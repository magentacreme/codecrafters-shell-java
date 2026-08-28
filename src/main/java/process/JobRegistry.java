package process;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class JobRegistry
{
    private final Map<Long, Job> jobByPid = new HashMap<>();

    public Job get(Long pid)
    {
        return jobByPid.get(pid);
    }

    public List<Job> listJobs()
    {
        return new ArrayList<>(jobByPid.values());
    }

    public void register(Job job)
    {
        jobByPid.put(job.getPid(), job);
    }

    public int nextJobNumber()
    {
        int nextJobNumber = 1;
        while (true)
        {
            boolean numberInUse = false;
            for (Job job : jobByPid.values())
            {
                if (job.getJobNumber() == nextJobNumber)
                {
                    numberInUse = true;
                    break;
                }
            }

            if (!numberInUse)
            {
                return nextJobNumber;
            }

            nextJobNumber++;
        }
    }

    public void remove(long pid)
    {
        jobByPid.remove(pid);
    }

    public void markAsDone(long pid)
    {
        Optional.ofNullable(jobByPid.get(pid)).ifPresent(job -> job.setStatus(JobStatus.DONE));
    }
}
