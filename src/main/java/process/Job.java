package process;

import java.time.Instant;

public final class Job
{
    private Integer previousDisplayIndex;
    private final int jobNumber;
    private final long pid;
    private final String command;
    private JobStatus status;
    private final Instant spawnTime;

    public Job(int jobNumber, long pid, String command, JobStatus status, Instant spawnTime)
    {
        this.jobNumber = jobNumber;
        this.pid = pid;
        this.command = command;
        this.status = status;
        this.spawnTime = spawnTime;
    }

    public int getJobNumber()
    {
        return jobNumber;
    }

    public long getPid()
    {
        return pid;
    }

    public String getCommand()
    {
        return command;
    }

    public JobStatus getStatus()
    {
        return status;
    }

    public Instant getSpawnTime()
    {
        return spawnTime;
    }

    public void setStatus(JobStatus status)
    {
        this.status = status;
    }

    public Integer getPreviousDisplayIndex()
    {
        return previousDisplayIndex;
    }

    public void setPreviousDisplayIndex(int previousDisplayIndex)
    {
        this.previousDisplayIndex = previousDisplayIndex;
    }

    @Override
    public String toString()
    {
        return "Job[" + "jobNumber=" + jobNumber + ", " + "pid=" + pid + ", " + "command=" + command + ", " + "status="
                + status + ", " + "spawnTime=" + spawnTime + ']';
    }

}
