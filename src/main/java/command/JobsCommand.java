package command;

import java.io.PrintStream;
import java.util.List;

import process.JobsPrinter;

public class JobsCommand implements Command
{
    private final JobsPrinter jobsPrinter;

    public JobsCommand(JobsPrinter jobsPrinter)
    {
        this.jobsPrinter = jobsPrinter;
    }

    @Override
    public void execute(List<String> args, PrintStream stdout, PrintStream stderr)
    {
        jobsPrinter.print(stdout, job -> true);
    }

    @Override
    public String name()
    {
        return "jobs";
    }
}
