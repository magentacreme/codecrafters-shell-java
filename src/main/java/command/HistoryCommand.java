package command;

import java.io.PrintStream;
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

    @Override
    public String name()
    {
        return "history";
    }
}
