package command;

import java.io.PrintStream;
import java.util.List;

public interface Command
{
    void execute(List<String> args, PrintStream stdout, PrintStream stderr);

    String name();
}
