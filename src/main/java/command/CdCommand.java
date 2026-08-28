package command;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import env.Environment;

public class CdCommand implements Command
{
    private final Environment environment;

    public CdCommand(Environment environment)
    {
        this.environment = environment;
    }

    @Override
    public void execute(List<String> args, PrintStream stdout, PrintStream stderr)
    {
        if (args.size() == 1)
        {
            Path targetDir = Paths.get(args.getFirst());
            try
            {
                environment.changeDirectory(targetDir);
            }
            catch (IOException e)
            {
                if (e instanceof FileNotFoundException)
                {
                    System.out.printf("%s: %s: No such file or directory%n", name(), targetDir);
                }
                else
                {
                    throw new RuntimeException(e);
                }
            }
        }
        // else add too many args error
    }

    @Override
    public String name()
    {
        return "cd";
    }
}
