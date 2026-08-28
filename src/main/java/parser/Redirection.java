package parser;

import java.nio.file.Path;

import file.StandardStream;

public class Redirection
{
    private final Path targetPath;
    private final StandardStream standardStream;
    private final RedirectionType redirectionType;
    private final int index;

    public Redirection(Path targetPath, StandardStream standardStream, int index, RedirectionType redirectionType)
    {
        this.targetPath = targetPath;
        this.standardStream = standardStream;
        this.index = index;
        this.redirectionType = redirectionType;
    }

    public Path getTargetPath()
    {
        return targetPath;
    }

    public StandardStream getStandardStream()
    {
        return standardStream;
    }

    public RedirectionType getRedirectionType()
    {
        return redirectionType;
    }

    public int getIndex()
    {
        return index;
    }
}
