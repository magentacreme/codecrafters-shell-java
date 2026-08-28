package command;

import java.io.PrintStream;
import java.util.List;

public class DeclareCommand implements Command
{
    @Override
    public void execute(List<String> args, PrintStream stdout, PrintStream stderr)
    {
    }

    @Override
    public String name()
    {
        return "declare";
    }
}
