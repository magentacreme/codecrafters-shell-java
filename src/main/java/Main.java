
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.jline.builtins.Completers;
import org.jline.reader.Candidate;
import org.jline.reader.Completer;
import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.reader.ParsedLine;
import org.jline.reader.Parser;
import org.jline.reader.Reference;
import org.jline.reader.impl.DefaultParser;
import org.jline.reader.impl.completer.StringsCompleter;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;

import autocomplete.AutoCompleteRegistry;
import command.CdCommand;
import command.Command;
import command.CommandRegistry;
import command.CompleteCommand;
import command.EchoCommand;
import command.ExitCommand;
import command.JobsCommand;
import command.PathResolver;
import command.PwdCommand;
import command.TypeCommand;
import env.Environment;
import file.StandardStream;
import parser.ArgumentParser;
import parser.Redirection;
import parser.RedirectionType;
import process.Job;
import process.JobRegistry;
import process.JobStatus;
import process.JobsPrinter;

public class Main
{
    private static CommandRegistry commandRegistry;
    private static JobRegistry jobRegistry;
    private static JobsPrinter jobsPrinter;
    private static AutoCompleteRegistry autoCompleteRegistry;
    private static PathResolver pathResolver;
    private static Environment environment;

    public static void main(String[] args) throws Exception
    {
        pathResolver = new PathResolver();
        commandRegistry = new CommandRegistry();
        jobRegistry = new JobRegistry();
        jobsPrinter = new JobsPrinter(jobRegistry);
        environment = new Environment();
        autoCompleteRegistry = new AutoCompleteRegistry();

        List<Command> commands = List.of(
                new EchoCommand(),
                new ExitCommand(),
                new PwdCommand(environment),
                new CdCommand(environment),
                new TypeCommand(commandRegistry, pathResolver),
                new CompleteCommand(environment, autoCompleteRegistry),
                new JobsCommand(jobsPrinter)
        );

        commands.forEach(commandRegistry::registerBuiltIn);

        Terminal terminal = TerminalBuilder.builder()
                .system(true)
                .build();

        Completer commandCompleter = new StringsCompleter(getAutocompleteCommands());
        Completer fileNameCompleter = new Completers.FileNameCompleter();
        Completer completer = (reader, line, candidates) ->
        {
            if (line.wordIndex() == 0)
            {
                commandCompleter.complete(reader, line, candidates);
                return;
            }

            String command = line.words().getFirst();

            Optional<Completer> registered =
                    autoCompleteRegistry.get(command);

            if (registered.isPresent())
            {
                registered.get().complete(reader, line, candidates);
            }
            else
            {
                fileNameCompleter.complete(reader, line, candidates);
            }
        };

        DefaultParser parser = new DefaultParser();

        // We do shell escaping ourselves later in ArgumentParser.
        parser.setEscapeChars(null);

        LineReader reader = LineReaderBuilder.builder()
                .terminal(terminal)
                .completer(completer)
                .parser(parser)
                .build();

        /*
         * Stores the contents of the line when TAB was last pressed on an
         * ambiguous completion.
         *
         * If TAB is pressed again and the line hasn't changed, we know this
         * is the second TAB and display the candidates.
         */

        AtomicReference<String> lastAmbiguousBuffer = new AtomicReference<>();

        reader.getWidgets().put("shell-complete", () ->
        {
            try
            {
                String line = reader.getBuffer().toString();
                int cursor = reader.getBuffer().cursor();

                ParsedLine parsedLine = parser.parse(
                        line,
                        cursor,
                        Parser.ParseContext.COMPLETE
                );

                String prefix = parsedLine.word();

                /*
                 * Get candidates from the appropriate completer.
                 */
                List<Candidate> candidates = new ArrayList<>();

                completer.complete(
                        reader,
                        parsedLine,
                        candidates
                );

                /*
                 * JLine's completer supplies possible candidates; because we're
                 * implementing the matching behavior ourselves, filter them against
                 * the word currently being typed.
                 *
                 * Also deduplicate candidates such as a builtin and an executable
                 * with the same name.
                 */
                List<Candidate> matches = candidates.stream()
                        .filter(candidate ->
                                candidate.value().startsWith(prefix))
                        .collect(Collectors.toMap(
                                Candidate::value,
                                Function.identity(),
                                (first, ignored) -> first
                        ))
                        .values()
                        .stream()
                        .sorted(Comparator.comparing(Candidate::value))
                        .toList();

                /*
                 * No matches.
                 */
                if (matches.isEmpty())
                {
                    lastAmbiguousBuffer.set(null);
                    reader.callWidget(LineReader.BEEP);
                    return true;
                }

                /*
                 * Exactly one match.
                 */
                if (matches.size() == 1)
                {
                    lastAmbiguousBuffer.set(null);

                    Candidate match = matches.getFirst();

                    String suffix =
                            match.value().substring(prefix.length());

                    reader.getBuffer().write(suffix);

                    /*
                     * Commands/files are complete tokens and receive a trailing
                     * space. Directories have complete=false and don't.
                     */
                    if (match.complete())
                    {
                        reader.getBuffer().write(" ");
                    }

                    return true;
                }

                /*
                 * Multiple matches.
                 *
                 * Before treating them as truly ambiguous, see whether they share
                 * additional characters beyond what the user has already typed.
                 */
                String commonPrefix = longestCommonPrefix(matches);

                if (commonPrefix.length() > prefix.length())
                {
                    String suffix =
                            commonPrefix.substring(prefix.length());

                    reader.getBuffer().write(suffix);

                    lastAmbiguousBuffer.set(null);

                    return true;
                }

                /*
                 * Multiple matches with no additional common prefix.
                 *
                 * First TAB -> beep.
                 */
                if (!line.equals(lastAmbiguousBuffer.get()))
                {
                    lastAmbiguousBuffer.set(line);
                    reader.callWidget(LineReader.BEEP);
                    return true;
                }

                /*
                 * Second TAB on unchanged input -> display choices.
                 */
                lastAmbiguousBuffer.set(null);

                PrintWriter writer = terminal.writer();

                writer.println();

                writer.println(
                        matches.stream()
                                .map(Candidate::value)
                                .collect(Collectors.joining("  "))
                );

                /*
                 * Print this explicitly rather than relying on JLine's REDISPLAY,
                 * because the CodeCrafters test terminal doesn't interpret JLine's
                 * redraw in quite the same way as a real terminal.
                 */
                writer.print("$ " + line);
                writer.flush();

                return true;
            }
            catch (Exception e)
            {
                lastAmbiguousBuffer.set(null);
                return false;
            }
        });

        reader.getKeyMaps()
                .get(LineReader.MAIN)
                .bind(
                        new Reference("shell-complete"),
                        "\t"
                );

        /*
         * Replace JLine's normal TAB action with ours.
         */
        reader.getKeyMaps()
                .get(LineReader.MAIN)
                .bind(
                        new Reference("shell-complete"),
                        "\t");

        while (true)
        {
            jobsPrinter.print(System.out, job -> job.getStatus() == JobStatus.DONE);
            String line = reader.readLine("$ ");

            if (!line.trim().isEmpty())
            {
                parse(line);
            }
        }
    }

    private static Set<String> getAutocompleteCommands()
    {
        List<String> builtInCommands = commandRegistry.getCommands().stream().map(Command::name).toList();
        Set<String> externalCommands = pathResolver.getAllAvailableCommands();
        return Stream.concat(builtInCommands.stream(), externalCommands.stream()).collect(Collectors.toSet());
    }

    private static void parse(String input) throws IOException
    {
        List<String> args = ArgumentParser.parseArgs(input);

        Redirection redirection = resolveRedirection(args);
        if (redirection != null)
        {
            args = args.subList(0, redirection.getIndex());
        }

        String commandName = args.getFirst();
        List<String> commandArgs = args.subList(1, args.size());

        Command command = commandRegistry.get(commandName);
        if (command != null)
        {
            executeBuiltin(command, commandArgs, redirection);
        }
        else if (pathResolver.resolve(commandName).isPresent())
        {
            executeProcess(args, input, redirection);
        }
        else
        {
            System.out.println(commandName + ": command not found");
        }
    }

    private static Redirection resolveRedirection(List<String> args)
    {
        int redirectIndex = findRedirect(args);
        if (redirectIndex != -1)
        {
            String redirect = args.get(redirectIndex);
            Path outputFile = Path.of(args.get(redirectIndex + 1));
            int fileDescriptor = 1;
            if (redirect.length() > 1 && !redirect.startsWith(">"))
            {
                fileDescriptor = Character.getNumericValue(redirect.charAt(0));
            }

            RedirectionType redirectionType = redirect.contains(">>") ?
                    RedirectionType.APPEND :
                    RedirectionType.OVERWRITE;
            return new Redirection(outputFile, StandardStream.valueOf(fileDescriptor), redirectIndex, redirectionType);
        }

        return null;
    }

    private static void executeBuiltin(Command command, List<String> args, Redirection redirection) throws IOException
    {
        if (redirection == null)
        {
            command.execute(args, System.out, System.err);
            return;
        }

        try (PrintStream redirected = openRedirection(redirection))
        {
            PrintStream stdout = System.out;
            PrintStream stderr = System.err;

            if (redirection.getStandardStream() == StandardStream.STDOUT)
            {
                stdout = redirected;
            }
            else if (redirection.getStandardStream() == StandardStream.STDERR)
            {
                stderr = redirected;
            }

            command.execute(args, stdout, stderr);
        }
    }

    private static PrintStream openRedirection(Redirection redirection) throws IOException
    {
        OpenOption mode = redirection.getRedirectionType() == RedirectionType.APPEND ?
                StandardOpenOption.APPEND :
                StandardOpenOption.TRUNCATE_EXISTING;

        return new PrintStream(Files.newOutputStream(redirection.getTargetPath(), StandardOpenOption.CREATE, mode));
    }

    private static int findRedirect(List<String> args)
    {
        Pattern redirectRegex = Pattern.compile(">+|[0-9](>+)");
        for (int i = 0; i < args.size(); i++)
        {
            String arg = args.get(i);
            if (redirectRegex.matcher(arg).matches())
            {
                return i;
            }
        }

        return -1;
    }

    private static void executeProcess(List<String> args, String input, Redirection redirection)
    {
        try
        {
            boolean background = args.getLast().equals("&");
            if (background)
            {
                args.removeLast();
            }

            ProcessBuilder processBuilder = new ProcessBuilder(args).inheritIO();
            if (redirection != null)
            {
                File outputFile = redirection.getTargetPath().toFile();
                StandardStream stream = redirection.getStandardStream();
                RedirectionType redirectionType = redirection.getRedirectionType();
                if (stream == StandardStream.STDOUT)
                {
                    if (redirectionType == RedirectionType.APPEND)
                    {
                        processBuilder.redirectOutput(ProcessBuilder.Redirect.appendTo(outputFile));
                    }
                    else
                    {
                        processBuilder.redirectOutput(outputFile);
                    }
                }
                else if (stream == StandardStream.STDERR)
                {
                    if (redirectionType == RedirectionType.APPEND)
                    {
                        processBuilder.redirectError(ProcessBuilder.Redirect.appendTo(outputFile));
                    }
                    else
                    {
                        processBuilder.redirectError(outputFile);
                    }
                }
            }

            Process process = processBuilder.start();

            process.onExit().thenAccept(completedProcess ->
            {
                jobRegistry.markAsDone(completedProcess.pid());
            });

            if (background)
            {
                int jobNumber = jobRegistry.nextJobNumber();
                System.out.printf("[%s] %s\n", jobNumber, process.pid());
                jobRegistry.register(new Job(jobNumber, process.pid(), input, JobStatus.RUNNING, Instant.now()));
            }
            else
            {
                process.waitFor();
            }
        }
        catch (IOException | InterruptedException e)
        {
            throw new RuntimeException(e);
        }
    }

    private static String longestCommonPrefix(List<Candidate> candidates)
    {
        if (candidates.isEmpty())
        {
            return "";
        }

        String prefix = candidates.getFirst().value();

        for (int i = 1; i < candidates.size(); i++)
        {
            String value = candidates.get(i).value();

            int length = Math.min(prefix.length(), value.length());
            int j = 0;

            while (j < length && prefix.charAt(j) == value.charAt(j))
            {
                j++;
            }

            prefix = prefix.substring(0, j);

            if (prefix.isEmpty())
            {
                break;
            }
        }

        return prefix;
    }
}


