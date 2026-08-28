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
                String name = argument.substring(0, equalsIndex);
                if (!isValidIdentifier(name))
                {
                    stderr.printf("declare: `%s': not a valid identifier%n", argument);
                    continue;
                }

                variables.put(
                        name,
                        argument.substring(equalsIndex + 1)
                );
            }
        }
    }

    private boolean isValidIdentifier(String name)
    {
        if (name.isEmpty() || (!Character.isLetter(name.charAt(0)) && name.charAt(0) != '_'))
        {
            return false;
        }

        for (int i = 1; i < name.length(); i++)
        {
            char character = name.charAt(i);
            if (!Character.isLetterOrDigit(character) && character != '_')
            {
                return false;
            }
        }

        return true;
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

    public String getVariable(String name)
    {
        return variables.get(name);
    }
}
