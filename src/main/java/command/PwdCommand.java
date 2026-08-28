package command;

import java.io.PrintStream;
import java.util.List;

import env.Environment;

public class PwdCommand implements Command
{
    private final Environment environment;

    public PwdCommand(Environment environment)
    {
        this.environment = environment;
    }

    @Override
    public void execute(List<String> args, PrintStream stdout, PrintStream stderr)
    {
        System.out.println(environment.getCurrentDirectory());
    }

    @Override
    public String name()
    {
        return "pwd";
    }
}
