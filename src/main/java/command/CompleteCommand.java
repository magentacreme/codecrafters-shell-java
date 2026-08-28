package command;

import java.io.PrintStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import autocomplete.AutoCompleteRegistry;
import autocomplete.ExternalCommandCompleter;
import env.Environment;

public class CompleteCommand implements Command
{
    private static final String PRINT_FLAG = "-p";
    private static final String CREATE_FLAG = "-C";

    private final Environment environment;
    private final AutoCompleteRegistry autoCompleteRegistry;

    private final Map<String, String> commandPerCompletionSpec = new HashMap<>();

    public CompleteCommand(Environment environment, AutoCompleteRegistry autoCompleteRegistry)
    {
        this.environment = environment;
        this.autoCompleteRegistry = autoCompleteRegistry;
    }

    @Override
    public void execute(List<String> args, PrintStream stdout, PrintStream stderr)
    {
        String flag = args.get(0);
        switch (flag)
        {
        case "-C" -> handleCreate(args);
        case "-p" -> handlePrint(args, stdout, stderr);
        case "-r" -> handleRemoval(args);
        default -> throw new UnsupportedOperationException();
        }
    }

    private void handleRemoval(List<String> args)
    {
        String commandToRemove = args.get(1);
        autoCompleteRegistry.remove(commandToRemove);
        commandPerCompletionSpec.remove(commandToRemove);
    }

    private void handleCreate(List<String> args)
    {
        int flagIndex = args.indexOf(CREATE_FLAG);
        String completerScriptPath = args.get(flagIndex + 1);
        String command = args.getLast();
        String cachedFullCommand = String.format("%s %s %s %s", name(), CREATE_FLAG,
                wrapInSingleQuotes(completerScriptPath), command);

        commandPerCompletionSpec.put(command, cachedFullCommand);
        autoCompleteRegistry.register(command, new ExternalCommandCompleter(completerScriptPath, environment));
    }

    private void handlePrint(List<String> args, PrintStream stdout, PrintStream stderr)
    {
        int flagIndex = args.indexOf(PRINT_FLAG);
        String command = args.get(flagIndex + 1);
        if (commandPerCompletionSpec.containsKey(command))
        {
            stdout.println(commandPerCompletionSpec.get(command));
        }
        else
        {
            stderr.printf("%s: %s: no completion specification%n", name(), command);
        }
    }

    private String wrapInSingleQuotes(String str)
    {
        if (!str.startsWith("'") && !str.endsWith("'"))
        {
            return "'" + str + "'";
        }

        return str;
    }

    @Override
    public String name()
    {
        return "complete";
    }
}
