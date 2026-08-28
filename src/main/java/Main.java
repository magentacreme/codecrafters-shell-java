
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
        private char marker;

        private BackgroundJob(
                int number,
                Process process,
                String command,
                char marker) {

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

        File currentDirectory =
                new File(System.getProperty("user.dir"));

        Map<String, String> completionScripts =
                new HashMap<>();

        List<BackgroundJob> backgroundJobs =
                new ArrayList<>();

        int nextJobNumber = 1;

        while (true) {

            /*
             * Automatic reaping before every prompt.
             */
            List<BackgroundJob> completedJobs =
                    reapCompletedJobs(
                            backgroundJobs,
                            false
                    );

            printJobStatuses(
                    completedJobs,
                    backgroundJobs,
                    false
            );

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
                     * jobs builtin performs its own reaping.
                     */
                    List<BackgroundJob> reapedJobs =
                            reapCompletedJobs(
                                    backgroundJobs,
                                    true
                            );

                    printJobStatuses(
                            reapedJobs,
                            backgroundJobs,
                            true
                    );
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

                            /*
                             * When starting a new background job:
                             *
                             * old '+' -> '-'
                             * old '-' -> blank
                             * new job -> '+'
                             */
                            for (BackgroundJob job :
                                    backgroundJobs) {

                                if (job.marker == '+') {

                                    job.marker = '-';

                                } else if (job.marker == '-') {

                                    job.marker = ' ';
                                }
                            }

                            backgroundJobs.add(
                                    new BackgroundJob(
                                            nextJobNumber,
                                            process,
                                            input.trim(),
                                            '+'
                                    )
                            );

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

    /*
     * Reap all completed jobs.
     *
     * fromJobsBuiltin == false:
     *     Called automatically before a prompt.
     *
     * fromJobsBuiltin == true:
     *     Called by the jobs builtin.
     */
    private static List<BackgroundJob> reapCompletedJobs(
            List<BackgroundJob> backgroundJobs,
            boolean fromJobsBuiltin)
            throws InterruptedException {

        List<BackgroundJob> completedJobs =
                new ArrayList<>();

        /*
         * Find every completed process.
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
         * Determine whether the current '+' job
         * is one of the completed jobs.
         */
        boolean plusCompleted = false;

        for (BackgroundJob job : completedJobs) {

            if (job.marker == '+') {

                plusCompleted = true;
                break;
            }
        }

        /*
         * Remove completed jobs from the active table.
         *
         * Their marker remains unchanged in completedJobs,
         * allowing us to print:
         *
         * [2]+ Done
         *
         * or
         *
         * [2]- Done
         */
        backgroundJobs.removeAll(completedJobs);

        /*
         * Handle marker recalculation.
         */
        if (plusCompleted) {

            /*
             * The current '+' job completed.
             */
            if (fromJobsBuiltin) {

                /*
                 * When reaping from `jobs`, the newest remaining
                 * job gets '-'.
                 *
                 * Example:
                 *
                 * [1]  Running
                 * [2]-  Done
                 * [3]+  Running
                 *
                 * job 3 completes:
                 *
                 * [1]-  Running
                 * [3]+  Done
                 */
                for (BackgroundJob job :
                        backgroundJobs) {

                    job.marker = ' ';
                }

                backgroundJobs.sort(
                        (a, b) ->
                                Integer.compare(
                                        a.number,
                                        b.number
                                )
                );

                if (!backgroundJobs.isEmpty()) {

                    BackgroundJob newest =
                            backgroundJobs.get(
                                    backgroundJobs.size() - 1
                            );

                    newest.marker = '-';
                }

            } else {

                /*
                 * Automatic reaping happens before the next
                 * prompt.
                 *
                 * The newest remaining job becomes '+'.
                 *
                 * Example:
                 *
                 * [1]- Running
                 * [2]+ Done
                 *
                 * becomes:
                 *
                 * [1]+ Running
                 */
                for (BackgroundJob job :
                        backgroundJobs) {

                    job.marker = ' ';
                }

                backgroundJobs.sort(
                        (a, b) ->
                                Integer.compare(
                                        a.number,
                                        b.number
                                )
                );

                if (!backgroundJobs.isEmpty()) {

                    BackgroundJob newest =
                            backgroundJobs.get(
                                    backgroundJobs.size() - 1
                            );

                    newest.marker = '+';
                }
            }

        } else {

            /*
             * A non-'+' job completed.
             *
             * Keep the current '+' job.
             *
             * Clear any old '-' marker.
             */
            for (BackgroundJob job :
                    backgroundJobs) {

                if (job.marker == '-') {

                    job.marker = ' ';
                }
            }
        }

        return completedJobs;
    }

    private static void printJobStatuses(
            List<BackgroundJob> completedJobs,
            List<BackgroundJob> runningJobs,
            boolean includeRunning) {

        List<BackgroundJob> allJobs =
                new ArrayList<>();

        /*
         * Add completed jobs.
         */
        allJobs.addAll(completedJobs);

        /*
         * jobs builtin also displays running jobs.
         */
        if (includeRunning) {

            allJobs.addAll(runningJobs);
        }

        /*
         * Always print in job-number order.
         */
        allJobs.sort(
                (a, b) ->
                        Integer.compare(
                                a.number,
                                b.number
                        )
        );

        for (BackgroundJob job :
                allJobs) {

            boolean running =
                    job.process.isAlive();

            String status;
            String command;

            if (running) {

                status = "Running";

                /*
                 * Running jobs include '&'.
                 */
                command = job.command;

            } else {

                status = "Done";

                /*
                 * Done jobs do not include '&'.
                 */
                command =
                        job.command.replaceAll(
                                "\\s*&$",
                                ""
                        );
            }

            System.out.printf(
                    "[%d]%c  %-24s%s%n",
                    job.number,
                    job.marker,
                    status,
                    command
            );
        }
    }
}
