import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class Main {
    static final Set<String> builtins = Set.of("exit", "echo", "type", "pwd", "cd", "complete", "jobs");

    private static final class BackgroundJob {
        private final int number;
        private final Process process;
        private final String command;
        private char marker;

        private BackgroundJob(int number, Process process, String command, char marker) {
            this.number = number;
            this.process = process;
            this.command = command;
            this.marker = marker;
        }
    }

    public static void main(String[] args) throws Exception {
        
           ProcessExecutor.enableRawMode();

    Runtime.getRuntime().addShutdownHook(
        new Thread(() -> {
            try {
                ProcessExecutor.disableRawMode();
            } catch (Exception ignored) {
            }
        })
    );
        
        File currentDirectory = new File(System.getProperty("user.dir"));
        Map<String, String> completionScripts = new HashMap<>();
        List<BackgroundJob> backgroundJobs = new ArrayList<>();
        int nextJobNumber = 1;

        while (true) {
            List<BackgroundJob> completedJobs = reapCompletedJobs(backgroundJobs);
            printJobStatuses(completedJobs, backgroundJobs, false);
            System.out.print("$ ");

            String input = CommandParser.readCommand(builtins, completionScripts);

            if (input.isEmpty()) {
                continue;
            }

            String[] parts = CommandParser.parse(input);
            if (parts.length == 0) {
                continue;
            }

            boolean isBackground = parts[parts.length - 1].equals("&");
            if (isBackground) {
                parts = Arrays.copyOf(parts, parts.length - 1);
                if (parts.length == 0) {
                    continue;
                }
            }

            String command = parts[0];
            switch (command) {
                case "exit" -> {
                    System.exit(0);
                }
                case "echo" -> ProcessExecutor.executeEcho(parts, currentDirectory);
                case "type" -> {
                    if (parts.length < 2) {
                        continue;
                    }

                    String argument = parts[1];
                    if (builtins.contains(argument)) {
                        System.out.println(argument + " is a shell builtin");
                    } else {
                        String executable = ProcessExecutor.findExecutable(argument);
                        if (executable != null) {
                            System.out.println(argument + " is " + executable);
                        } else {
                            System.out.println(argument + ": not found");
                        }
                    }
                }
                case "pwd" -> System.out.println(currentDirectory.getAbsolutePath());
                case "jobs" -> {
                    List<BackgroundJob> reapedJobs = reapCompletedJobs(backgroundJobs);
                    printJobStatuses(reapedJobs, backgroundJobs, true);
                }
                case "complete" -> {
                    if (parts.length >= 4 && parts[1].equals("-C")) {
                        completionScripts.put(parts[3], parts[2]);
                    } else if (parts.length >= 3 && parts[1].equals("-r")) {
                        completionScripts.remove(parts[2]);
                    } else if (parts.length >= 3 && parts[1].equals("-p")) {
                        String script = completionScripts.get(parts[2]);
                        if (script == null) {
                            System.out.println("complete: " + parts[2] + ": no completion specification");
                        } else {
                            System.out.println("complete -C '" + script + "' " + parts[2]);
                        }
                    }
                }
                case "cd" -> {
                    if (parts.length < 2) {
                        continue;
                    }

                    String path = parts[1];
                    File directory;
                    if (path.equals("~")) {
                        directory = new File(System.getenv("HOME"));
                    } else if (path.startsWith("/")) {
                        directory = new File(path);
                    } else {
                        directory = new File(currentDirectory, path);
                    }

                    if (directory.exists() && directory.isDirectory()) {
                        currentDirectory = directory.getCanonicalFile();
                    } else {
                        System.out.println("cd: " + path + ": No such file or directory");
                    }
                }
                default -> {
                    String executablePath = ProcessExecutor.findExecutable(command);
                    if (executablePath != null) {
                        if (isBackground) {
                            Process process = ProcessExecutor.startCommand(parts, currentDirectory);
                            for (BackgroundJob job : backgroundJobs) {
                                if (job.marker == '+') {
                                    job.marker = '-';
                                }
                            }
                            backgroundJobs.add(new BackgroundJob(nextJobNumber, process, input.trim(), '+'));
                            System.out.println("[" + nextJobNumber + "] " + process.pid());
                            nextJobNumber++;
                        } else {
                            ProcessExecutor.executeCommand(parts, currentDirectory);
                        }
                    } else {
                        System.out.println(command + ": command not found");
                    }
                }
            }
        }
    }

    private static List<BackgroundJob> reapCompletedJobs(
        List<BackgroundJob> backgroundJobs) throws InterruptedException {

    List<BackgroundJob> completedJobs = new ArrayList<>();

    for (BackgroundJob job : backgroundJobs) {
        if (!job.process.isAlive()) {
            job.process.waitFor();
            completedJobs.add(job);
        }
    }

    // Remove completed jobs.
    backgroundJobs.removeAll(completedJobs);

    // Do NOT modify markers here.
    // Existing + and - markers belong to the remaining jobs.

    return completedJobs;
}
private static void printJobStatuses(
        List<BackgroundJob> completedJobs,
        List<BackgroundJob> runningJobs,
        boolean includeRunning) {

    for (BackgroundJob job : completedJobs) {
        String command = job.command.replaceAll("\\s*&$", "");

        System.out.printf(
                "[%d]%c  %-24s%s%n",
                job.number,
                job.marker,
                "Done",
                command
        );
    }

    if (includeRunning) {
        for (BackgroundJob job : runningJobs) {
            System.out.printf(
                    "[%d]%c  %-24s%s%n",
                    job.number,
                    job.marker,
                    "Running",
                    job.command
            );
        }
    }
}
}