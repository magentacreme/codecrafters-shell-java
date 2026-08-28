package process;

import java.io.PrintStream;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;

public class JobsPrinter
{
    private final JobRegistry jobRegistry;

    public JobsPrinter(JobRegistry jobRegistry)
    {
        this.jobRegistry = jobRegistry;
    }

    public void print(PrintStream printStream, Predicate<Job> jobFilter)
    {
        List<Job> jobs = jobRegistry.listJobs().stream()
                .filter(jobFilter)
                .sorted(Comparator.comparing(Job::getSpawnTime))
                .toList();
        for (int i = 0; i < jobs.size(); i++)
        {
            String marker = " ";
            if (jobs.size() == 1)
            {
                marker = "+";
            }
            else
            {
                if (i == jobs.size() - 1)
                {
                    marker = "+";
                }
                else if (i == jobs.size() - 2)
                {
                    marker = "-";
                }
            }

            Job job = jobs.get(i);
            String status = createStatusColumn(job.getStatus());
            String command = job.getCommand();

            int displayIndex = i + 1;
            if (job.getPreviousDisplayIndex() == null)
            {
                job.setPreviousDisplayIndex(displayIndex);
            }
            else
            {
                displayIndex = job.getPreviousDisplayIndex();
            }

            if (job.getStatus() == JobStatus.DONE)
            {
                jobRegistry.remove(job.getPid());
                command = command.substring(0, command.length() - 1); // Remove & from Done jobs
            }
            else
            {
                job.setPreviousDisplayIndex(i + 1);
            }

            printStream.printf("[%d]%s  %s%s\n", displayIndex, marker, status, command);
        }
    }

    private String createStatusColumn(JobStatus status)
    {
        String displayName = status.getDisplayName();
        StringBuilder sb = new StringBuilder(displayName);
        while (sb.length() <= 24)
        {
            sb.append(" ");
        }

        return sb.toString();
    }
}
