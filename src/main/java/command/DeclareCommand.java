package command;

import java.io.PrintStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DeclareCommand implements Command
{
    private final Map<String, String> variables = new HashMap<>();

    @Override
    public void execute(List<String> args, PrintStream stdout, PrintStream stderr)
    {
        if (!args.isEmpty() && args.getFirst().equals("-p"))
        {
            printVariable(args, stdout, stderr);
            return;
        }

        for (String argument : args)
        {
            int equalsIndex = argument.indexOf('=');
            if (equalsIndex > 0)
            {
                variables.put(
                        argument.substring(0, equalsIndex),
                        argument.substring(equalsIndex + 1)
                );
            }
        }
    }

    private void printVariable(List<String> args, PrintStream stdout, PrintStream stderr)
    {
        if (args.size() < 2 || !variables.containsKey(args.get(1)))
        {
            String name = args.size() < 2 ? "" : args.get(1);
            stderr.printf("declare: %s: not found%n", name);
            return;
        }

        String name = args.get(1);
        stdout.printf("declare -- %s=\"%s\"%n", name, variables.get(name));
    }

    @Override
    public String name()
    {
        return "declare";
    }
}
