package command;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class PathResolver
{
    private final String[] directories;

    public PathResolver()
    {
        String path = System.getenv("PATH");
        path = path.replace("/usr/local/sbin", ""); // workaround for local machine
        directories = path == null ? new String[0] : path.split(File.pathSeparator);
    }

    public Optional<Path> resolve(String commandName)
    {
        for (String directory : directories)
        {
            Path candidate = Path.of(directory, commandName);
            if (Files.isRegularFile(candidate) && Files.isExecutable(candidate))
            {
                return Optional.of(candidate);
            }
        }

        return Optional.empty();
    }

    public Set<String> getAllAvailableCommands()
    {
        Set<String> allCommands = new HashSet<>();
        try
        {
            for (String directory : directories)
            {
                allCommands.addAll(listFilesInDirectory(directory));
            }
        }
        catch (IOException e)
        {
            throw new RuntimeException(e);
        }

        return allCommands;
    }

    public Set<String> listFilesInDirectory(String dir) throws IOException
    {
        try (Stream<Path> stream = Files.list(Paths.get(dir))) {
            return stream
                    .filter(file -> !Files.isDirectory(file))
                    .map(Path::getFileName)
                    .map(Path::toString)
                    .collect(Collectors.toSet());
        }
    }
}