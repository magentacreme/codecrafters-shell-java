package command;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;

public class HistoryCommand implements Command
{
    private final List<String> history;

    public HistoryCommand(List<String> history)
    {
        this.history = history;
    }

    @Override
    public void execute(List<String> args, PrintStream stdout, PrintStream stderr)
    {
        if (!args.isEmpty() && args.getFirst().equals("-r"))
        {
            readHistoryFile(args, stderr);
            return;
        }
        if (!args.isEmpty() && args.getFirst().equals("-w"))
        {
            writeHistoryFile(args, stderr);
            return;
        }

        int limit = history.size();
        if (!args.isEmpty())
        {
            limit = Integer.parseInt(args.getFirst());
        }

        int start = Math.max(0, history.size() - limit);
        for (int i = start; i < history.size(); i++)
        {
            stdout.printf("%5d  %s%n", i + 1, history.get(i));
        }
    }

    private void readHistoryFile(List<String> args, PrintStream stderr)
    {
        if (args.size() < 2)
        {
            stderr.println("history: -r: option requires an argument");
            return;
        }

        try
        {
            Files.readAllLines(Path.of(args.get(1))).stream()
                    .filter(line -> !line.isEmpty())
                    .forEach(history::add);
        }
        catch (IOException e)
        {
            stderr.println("history: " + e.getMessage());
        }
    }

    private void writeHistoryFile(List<String> args, PrintStream stderr)
    {
        if (args.size() < 2)
        {
            stderr.println("history: -w: option requires an argument");
            return;
        }

        try
        {
            Files.write(
                    Path.of(args.get(1)),
                    history,
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING
            );
        }
        catch (IOException e)
        {
            stderr.println("history: " + e.getMessage());
        }
    }

    @Override
    public String name()
    {
        return "history";
    }
}
