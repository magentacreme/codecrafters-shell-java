package command;

import java.io.PrintStream;
import java.util.List;

public class ExitCommand implements Command
{
    @Override
    public void execute(List<String> args,  PrintStream stdout, PrintStream stderr)
    {
        System.exit(0);
    }

    @Override
    public String name()
    {
        return "exit";
    }
}
