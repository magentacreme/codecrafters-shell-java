package process;

public enum JobStatus
{
    RUNNING("Running"), DONE("Done");

    private final String displayName;

    JobStatus(String displayName)
    {
        this.displayName = displayName;
    }

    public String getDisplayName()
    {
        return displayName;
    }
}
