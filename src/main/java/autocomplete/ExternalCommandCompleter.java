package autocomplete;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;

import org.jline.reader.Candidate;
import org.jline.reader.Completer;
import org.jline.reader.LineReader;
import org.jline.reader.ParsedLine;

import env.Environment;

public class ExternalCommandCompleter implements Completer
{
    private final String completionCommand;
    private final Environment environment;

    public ExternalCommandCompleter(String completionCommand, Environment environment)
    {
        this.completionCommand = completionCommand;
        this.environment = environment;
    }

    @Override
    public void complete(LineReader reader, ParsedLine line, List<Candidate> candidates)
    {
        String command = line.words().getFirst();
        String currentWord = line.word();

        String previousWord = line.wordIndex() > 0
                ? line.words().get(line.wordIndex() - 1)
                : "";

        List<String> args = List.of(
                completionCommand,
                command,
                currentWord,
                previousWord);

        ProcessBuilder processBuilder = new ProcessBuilder(args);
        processBuilder.environment().put("COMP_POINT", String.valueOf(line.cursor()));
        processBuilder.environment().put("COMP_LINE", line.line());

        processBuilder.directory(environment.getCurrentDirectory().toFile());

        try
        {
            Process process = processBuilder.start();

            try (BufferedReader stdout = process.inputReader())
            {
                stdout.lines()
                        .filter(s -> !s.isBlank())
                        .filter(s -> s.startsWith(args.get(2)))
                        .map(Candidate::new).forEach(candidates::add);
            }

            process.waitFor();
        }
        catch (IOException e)
        {
            throw new UncheckedIOException(e);
        }
        catch (InterruptedException e)
        {
            Thread.currentThread().interrupt();
        }
    }
}