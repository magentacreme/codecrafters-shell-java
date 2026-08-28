package command;


import java.io.PrintStream;
import java.util.List;

public class TypeCommand implements Command
{
    private final CommandRegistry commandRegistry;
    private final PathResolver pathResolver;

    public TypeCommand(CommandRegistry commandRegistry, PathResolver pathResolver)
    {
        this.commandRegistry = commandRegistry;
        this.pathResolver = pathResolver;
    }

    @Override
    public void execute(List<String> args, PrintStream stdout, PrintStream stderr)
    {
        String command = args.getFirst();
        if (commandRegistry.contains(command))
        {
            stdout.println(command + " is a shell builtin");
        }
        else
        {
            pathResolver.resolve(command).ifPresentOrElse(
                    path -> stdout.println(command + " is " + path),
                    () -> stdout.println(command + ": not found"));
        }
    }

    @Override
    public String name()
    {
        return "type";
    }
}
