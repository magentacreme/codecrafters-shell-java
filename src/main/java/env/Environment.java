package env;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class Environment
{
    private Path currentDirectory = Paths.get(System.getProperty("user.dir"));

    public void changeDirectory(Path directory) throws IOException
    {
        Path nextDir = resolveDirectory(directory);
        if (!Files.exists(nextDir))
        {
            throw new FileNotFoundException();
        }

        this.currentDirectory = nextDir.toRealPath();
    }

    public Path getCurrentDirectory()
    {
        return currentDirectory;
    }

    private Path resolveDirectory(Path path)
    {
        String pathStr = path.toString();

        if (pathStr.startsWith("~"))
        {
            String home = getEnvironmentVariable("HOME");
            return Paths.get(home, pathStr.substring(1));
        }

        return path.isAbsolute()
                ? path
                : currentDirectory.resolve(path);
    }

    private String getEnvironmentVariable(String var)
    {
        return System.getenv(var);
    }
}
