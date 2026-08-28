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
             * Reap jobs that finished before displaying
             * the next prompt.
             */
            List<BackgroundJob> completedJobs =
                    reapCompletedJobs(backgroundJobs);

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

            /*
             * Check whether this is a background command.
             */
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
                     * Reap jobs that have completed since
                     * the previous prompt.
                     */
                    List<BackgroundJob> reapedJobs =
                            reapCompletedJobs(
                                    backgroundJobs
                            );

                    printJobStatuses(
                            reapedJobs,
                            backgroundJobs,
                            true
                    );
                }

                case "complete" -> {

                    /*
                     * complete -C <script> <command>
                     */
                    if (parts.length >= 4
                            && parts[1].equals("-C")) {

                        completionScripts.put(
                                parts[3],
                                parts[2]
                        );

                    /*
                     * complete -r <command>
                     */
                    } else if (parts.length >= 3
                            && parts[1].equals("-r")) {

                        completionScripts.remove(
                                parts[2]
                        );

                    /*
                     * complete -p <command>
                     */
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
                             * Add the new job.
                             *
                             * Markers are recalculated afterward.
                             */
                            backgroundJobs.add(
                                    new BackgroundJob(
                                            nextJobNumber,
                                            process,
                                            input.trim(),
                                            ' '
                                    )
                            );

                            updateJobMarkers(
                                    backgroundJobs
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
     * Find all background jobs that have finished.
     */
    private static List<BackgroundJob> reapCompletedJobs(
            List<BackgroundJob> backgroundJobs)
            throws InterruptedException {

        List<BackgroundJob> completedJobs =
                new ArrayList<>();

        for (BackgroundJob job : backgroundJobs) {

            if (!job.process.isAlive()) {

                job.process.waitFor();

                completedJobs.add(job);
            }
        }

        /*
         * Remove completed jobs from the list of
         * active background jobs.
         */
        backgroundJobs.removeAll(
                completedJobs
        );

        /*
         * Recalculate + and - markers for the
         * remaining jobs.
         */
        updateJobMarkers(
                backgroundJobs
        );

        return completedJobs;
    }

    /*
     * Recalculate job markers.
     *
     * Highest-numbered active job = +
     * Second-highest-numbered active job = -
     * Everything else = blank
     */
    private static void updateJobMarkers(
            List<BackgroundJob> jobs) {

        /*
         * Clear all markers first.
         */
        for (BackgroundJob job : jobs) {
            job.marker = ' ';
        }

        if (jobs.isEmpty()) {
            return;
        }

        /*
         * Make sure jobs are ordered by job number.
         */
        jobs.sort(
                (a, b) ->
                        Integer.compare(
                                a.number,
                                b.number
                        )
        );

        /*
         * Highest job number gets +.
         */
        jobs.get(
                jobs.size() - 1
        ).marker = '+';

        /*
         * Second-highest job number gets -.
         */
        if (jobs.size() >= 2) {

            jobs.get(
                    jobs.size() - 2
            ).marker = '-';
        }
    }

    /*
     * Print background-job status lines.
     */
    private static void printJobStatuses(
            List<BackgroundJob> completedJobs,
            List<BackgroundJob> runningJobs,
            boolean includeRunning) {

        List<BackgroundJob> allJobs =
                new ArrayList<>();

        /*
         * Include completed jobs.
         */
        allJobs.addAll(
                completedJobs
        );

        /*
         * When executing "jobs", also include
         * currently running jobs.
         */
        if (includeRunning) {

            allJobs.addAll(
                    runningJobs
            );
        }

        /*
         * Display jobs in job-number order.
         */
        allJobs.sort(
                (a, b) ->
                        Integer.compare(
                                a.number,
                                b.number
                        )
        );

        for (BackgroundJob job : allJobs) {

            boolean running =
                    job.process.isAlive();

            String status;
            String command;

            if (running) {

                status = "Running";

                /*
                 * Running background commands keep
                 * the trailing "&".
                 */
                command = job.command;

            } else {

                status = "Done";

                /*
                 * Completed commands do NOT show "&".
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