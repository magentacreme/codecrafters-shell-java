package command;

import java.io.PrintStream;
import java.util.List;

public class EchoCommand implements Command
{
    @Override
    public void execute(List<String> args, PrintStream stdout, PrintStream stderr)
    {
        stdout.println(String.join(" ", args));
    }

    @Override
    public String name()
    {
        return "echo";
    }
}
