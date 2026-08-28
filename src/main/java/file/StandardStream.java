package file;

public enum StandardStream
{
    STDOUT(1), STDERR(2);

    private final int fileDescriptor;

    StandardStream(int fileDescriptor)
    {
        this.fileDescriptor = fileDescriptor;
    }

    public static StandardStream valueOf(int fileDescriptor)
    {
        for (StandardStream descriptor : values())
        {
            if (descriptor.fileDescriptor == fileDescriptor)
            {
                return descriptor;
            }
        }

        throw new IllegalArgumentException(
                "Unknown file descriptor: " + fileDescriptor
        );
    }
}