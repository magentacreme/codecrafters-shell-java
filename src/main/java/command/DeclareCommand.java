package command;

import java.io.PrintStream;
import java.util.List;

public class DeclareCommand implements Command
{
    @Override
    public void execute(List<String> args, PrintStream stdout, PrintStream stderr)
    {
        if (args.size() >= 2 && args.getFirst().equals("-p"))
        {
            stderr.printf("declare: %s: not found%n", args.get(1));
        }
    }

    @Override
    public String name()
    {
        return "declare";
    }
}
