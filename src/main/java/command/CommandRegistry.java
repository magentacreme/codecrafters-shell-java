package command;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public class CommandRegistry
{
    private final Map<String, Command> commandMap = new HashMap<>();

    public void registerBuiltIn(Command command)
    {
        commandMap.put(command.name(), command);
    }

    public Command get(String name)
    {
        return commandMap.get(name);
    }

    public boolean contains(String name)
    {
        return commandMap.containsKey(name);
    }

    public Collection<Command> getCommands()
    {
        return commandMap.values();
    }
}