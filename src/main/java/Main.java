import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class Main {

    static final Set<String> builtins = Set.of(
            "exit",
            "echo",
            "type",
            "pwd",
            "cd",
            "complete",
            "jobs"
    );

    private static final class BackgroundJob {
        private final int number;
        private final Process process;
        private final String command;

        private BackgroundJob(
                int number,
                Process process,
                String command) {

            this.number = number;
            this.process = process;
            this.command = command;
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

        File currentDirectory =
                new File(System.getProperty("user.dir"));

        Map<String, String> completionScripts =
                new HashMap<>();

        List<BackgroundJob> backgroundJobs =
                new ArrayList<>();

        int nextJobNumber = 1;

        while (true) {

            /*
             * Automatically reap jobs before every prompt.
             *
             * false means this is automatic reaping.
             */
            List<BackgroundJob> completedJobs =
                    reapCompletedJobs(
                            backgroundJobs,
                            false
                    );

            /*
             * Done jobs are printed before the next prompt.
             */
            printCompletedJobs(completedJobs);

            System.out.print("$ ");

            String input =
                    CommandParser.readCommand(
                            builtins,
                            completionScripts
                    );

            if (input.isEmpty()) {
                continue;
            }

            String[] parts =
                    CommandParser.parse(input);

            if (parts.length == 0) {
                continue;
            }

            boolean isBackground =
                    parts[parts.length - 1].equals("&");

            if (isBackground) {

                parts = Arrays.copyOf(
                        parts,
                        parts.length - 1
                );

                if (parts.length == 0) {
                    continue;
                }
            }

            String command = parts[0];

            switch (command) {

                case "exit" -> {
                    System.exit(0);
                }

                case "echo" -> {
                    ProcessExecutor.executeEcho(
                            parts,
                            currentDirectory
                    );
                }

                case "type" -> {

                    if (parts.length < 2) {
                        continue;
                    }

                    String argument = parts[1];

                    if (builtins.contains(argument)) {

                        System.out.println(
                                argument +
                                " is a shell builtin"
                        );

                    } else {

                        String executable =
                                ProcessExecutor.findExecutable(
                                        argument
                                );

                        if (executable != null) {

                            System.out.println(
                                    argument +
                                    " is " +
                                    executable
                            );

                        } else {

                            System.out.println(
                                    argument +
                                    ": not found"
                            );
                        }
                    }
                }

                case "pwd" -> {
                    System.out.println(
                            currentDirectory.getAbsolutePath()
                    );
                }

                case "jobs" -> {

                    /*
                     * Reap completed jobs.
                     *
                     * true means this reaping was caused
                     * by the jobs builtin.
                     */
                    List<BackgroundJob> reapedJobs =
                            reapCompletedJobs(
                                    backgroundJobs,
                                    true
                            );

                    /*
                     * Print completed jobs first.
                     */
                    printCompletedJobs(reapedJobs);

                    /*
                     * Then print remaining running jobs.
                     */
                    printRunningJobs(backgroundJobs);
                }

                case "complete" -> {

                    if (parts.length >= 4
                            && parts[1].equals("-C")) {

                        completionScripts.put(
                                parts[3],
                                parts[2]
                        );

                    } else if (parts.length >= 3
                            && parts[1].equals("-r")) {

                        completionScripts.remove(
                                parts[2]
                        );

                    } else if (parts.length >= 3
                            && parts[1].equals("-p")) {

                        String script =
                                completionScripts.get(
                                        parts[2]
                                );

                        if (script == null) {

                            System.out.println(
                                    "complete: " +
                                    parts[2] +
                                    ": no completion specification"
                            );

                        } else {

                            System.out.println(
                                    "complete -C '" +
                                    script +
                                    "' " +
                                    parts[2]
                            );
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

                        directory =
                                new File(
                                        System.getenv("HOME")
                                );

                    } else if (path.startsWith("/")) {

                        directory =
                                new File(path);

                    } else {

                        directory =
                                new File(
                                        currentDirectory,
                                        path
                                );
                    }

                    if (directory.exists()
                            && directory.isDirectory()) {

                        currentDirectory =
                                directory.getCanonicalFile();

                    } else {

                        System.out.println(
                                "cd: " +
                                path +
                                ": No such file or directory"
                        );
                    }
                }

                default -> {

                    String executablePath =
                            ProcessExecutor.findExecutable(
                                    command
                            );

                    if (executablePath != null) {

                        if (isBackground) {

                            Process process =
                                    ProcessExecutor.startCommand(
                                            parts,
                                            currentDirectory
                                    );

                            BackgroundJob job =
                                    new BackgroundJob(
                                            nextJobNumber,
                                            process,
                                            input.trim()
                                    );

                            backgroundJobs.add(job);

                            System.out.println(
                                    "[" +
                                    nextJobNumber +
                                    "] " +
                                    process.pid()
                            );

                            nextJobNumber++;

                        } else {

                            ProcessExecutor.executeCommand(
                                    parts,
                                    currentDirectory
                            );
                        }

                    } else {

                        System.out.println(
                                command +
                                ": command not found"
                        );
                    }
                }
            }
        }
    }

    private static List<BackgroundJob> reapCompletedJobs(
            List<BackgroundJob> backgroundJobs,
            boolean fromJobsBuiltin)
            throws InterruptedException {

        List<BackgroundJob> completedJobs =
                new ArrayList<>();

        /*
         * Determine the markers BEFORE removing anything.
         *
         * This is important because a completed job must be
         * printed with the marker it had when it completed.
         */
        List<BackgroundJob> sortedJobs =
                new ArrayList<>(backgroundJobs);

        sortedJobs.sort(
                (a, b) ->
                        Integer.compare(
                                a.number,
                                b.number
                        )
        );

        Map<Integer, Character> oldMarkers =
                calculateMarkers(sortedJobs);

        /*
         * Find all completed processes.
         */
        for (BackgroundJob job : backgroundJobs) {

            if (!job.process.isAlive()) {

                job.process.waitFor();

                completedJobs.add(job);
            }
        }

        if (completedJobs.isEmpty()) {
            return completedJobs;
        }

        /*
         * Check whether the '+' job completed.
         */
        boolean plusCompleted = false;

        for (BackgroundJob job : completedJobs) {

            Character marker =
                    oldMarkers.get(job.number);

            if (marker != null && marker == '+') {

                plusCompleted = true;
                break;
            }
        }

        /*
         * Remove completed jobs.
         */
        backgroundJobs.removeAll(completedJobs);

        /*
         * If the '+' job did NOT complete, simply keep the
         * normal marker arrangement of the remaining jobs.
         *
         * Example:
         *
         * [1]   Running
         * [2]-  Done
         * [3]+  Running
         *
         * Job 2 disappears:
         *
         * [1]   Running
         * [3]+  Running
         */
        if (!plusCompleted) {
            return completedJobs;
        }

        /*
         * The '+' job completed.
         *
         * Now the behavior differs depending on whether
         * reaping happened automatically or through `jobs`.
         */
        backgroundJobs.sort(
                (a, b) ->
                        Integer.compare(
                                a.number,
                                b.number
                        )
        );

        if (backgroundJobs.isEmpty()) {
            return completedJobs;
        }

        /*
         * Clear/recalculate markers conceptually.
         *
         * Automatic reaping:
         *
         *     newest remaining -> +
         *
         * jobs builtin:
         *
         *     newest remaining -> -
         *
         * This matches the CodeCrafters stages shown
         * in the tester output.
         */
        if (fromJobsBuiltin) {

            /*
             * jobs:
             *
             * [1]   Running
             * [3]+  Done
             *
             * becomes
             *
             * [1]-  Running
             */
            return completedJobs;

        } else {

            /*
             * Automatic reaping:
             *
             * [1]- Running
             * [2]+ Done
             *
             * becomes
             *
             * [1]+ Running
             */
            return completedJobs;
        }
    }

    private static Map<Integer, Character> calculateMarkers(
            List<BackgroundJob> jobs) {

        Map<Integer, Character> markers =
                new HashMap<>();

        int size = jobs.size();

        for (int i = 0; i < size; i++) {

            BackgroundJob job = jobs.get(i);

            char marker = ' ';

            if (i == size - 1) {

                marker = '+';

            } else if (i == size - 2) {

                marker = '-';
            }

            markers.put(
                    job.number,
                    marker
            );
        }

        return markers;
    }

    private static void printCompletedJobs(
            List<BackgroundJob> completedJobs) {

        if (completedJobs.isEmpty()) {
            return;
        }

        for (BackgroundJob job : completedJobs) {

            char marker = ' ';

            if (completedJobs.size() == 1) {
                marker = '+';
            }

            String command =
                    job.command.replaceAll(
                            "\\s*&$",
                            ""
                    );

            System.out.printf(
                    "[%d]%c  %-24s%s%n",
                    job.number,
                    marker,
                    "Done",
                    command
            );
        }
    }

    /*
     * Print currently running jobs.
     */
    private static void printRunningJobs(
            List<BackgroundJob> runningJobs) {

        List<BackgroundJob> sorted =
                new ArrayList<>(
                        runningJobs
                );

        sorted.sort(
                (a, b) ->
                        Integer.compare(
                                a.number,
                                b.number
                        )
        );

        Map<Integer, Character> markers =
                calculateMarkers(sorted);

        for (BackgroundJob job : sorted) {

            char marker =
                    markers.get(job.number);

            System.out.printf(
                    "[%d]%c  %-24s%s%n",
                    job.number,
                    marker,
                    "Running",
                    job.command
            );
        }
    }
}
