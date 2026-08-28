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

    /*
     * Represents one background job.
     *
     * No '+' / '-' marker is stored here.
     * Markers are calculated by JobRegistry when needed.
     */
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

    /*
     * Registry responsible only for storing and managing jobs.
     */
    private static final class JobRegistry {

        private final List<BackgroundJob> jobs =
                new ArrayList<>();

        private int nextJobNumber = 1;

        /*
         * Register a newly started background job.
         */
        public BackgroundJob register(
                Process process,
                String command) {

            BackgroundJob job =
                    new BackgroundJob(
                            nextJobNumber,
                            process,
                            command
                    );

            jobs.add(job);

            nextJobNumber++;

            return job;
        }

        /*
         * Return a copy of the current job list.
         */
        public List<BackgroundJob> list() {
            return new ArrayList<>(jobs);
        }

        /*
         * Remove a job from the registry.
         */
        public void remove(BackgroundJob job) {
            jobs.remove(job);
        }

        /*
         * Find all jobs whose processes have finished.
         */
        public List<BackgroundJob> findCompleted() {

            List<BackgroundJob> completed =
                    new ArrayList<>();

            for (BackgroundJob job : jobs) {

                if (!job.process.isAlive()) {
                    completed.add(job);
                }
            }

            return completed;
        }

        /*
         * Return the newest active job.
         *
         * The newest job has the highest job number.
         */
        public BackgroundJob newest() {

            BackgroundJob newest = null;

            for (BackgroundJob job : jobs) {

                if (newest == null
                        || job.number > newest.number) {

                    newest = job;
                }
            }

            return newest;
        }

        /*
         * Return the second newest active job.
         */
        public BackgroundJob secondNewest() {

            BackgroundJob newest = null;
            BackgroundJob secondNewest = null;

            for (BackgroundJob job : jobs) {

                if (newest == null
                        || job.number > newest.number) {

                    secondNewest = newest;
                    newest = job;

                } else if (
                        secondNewest == null
                        || job.number > secondNewest.number) {

                    secondNewest = job;
                }
            }

            return secondNewest;
        }

        /*
         * Determine the marker for a RUNNING job.
         *
         * The caller supplies which marker policy to use.
         */
        public char markerFor(
                BackgroundJob job,
                boolean jobsBuiltinMode) {

            BackgroundJob newest = newest();
            BackgroundJob secondNewest = secondNewest();

            if (newest == null) {
                return ' ';
            }

            /*
             * The newest active job is normally '+'.
             *
             * For the special state produced by `jobs` after
             * the previous '+' job was reaped, the newest job
             * may temporarily be '-'.
             */
            if (job == newest) {
                return jobsBuiltinMode ? '-' : '+';
            }

            if (job == secondNewest) {
                return ' ';
            }

            return ' ';
        }
    }

    /*
     * This flag represents the special marker state required
     * after a `jobs` command reaps the current '+' job.
     *
     * Example:
     *
     * Before:
     * [1]   Running
     * [2]-  Running
     * [3]+  Running
     *
     * Job 3 completes and `jobs` is executed:
     *
     * [1]   Running
     * [2]-  Running
     * [3]+  Done
     *
     * The next active job becomes '-' according to the stage
     * behavior.
     */
    private static boolean jobsMarkerState = false;


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

        JobRegistry jobRegistry =
                new JobRegistry();

        while (true) {

            /*
             * Automatic reaping happens BEFORE every prompt.
             *
             * This is required by BV8.
             */
            List<BackgroundJob> completedJobs =
                    reapJobs(
                            jobRegistry,
                            false
                    );

            /*
             * Automatically reaped jobs are printed
             * before the next prompt.
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
                     * jobs itself performs reaping.
                     */
                    List<BackgroundJob> completed =
                            reapJobs(
                                    jobRegistry,
                                    true
                            );

                    /*
                     * Print all active jobs first,
                     * then completed jobs.
                     *
                     * This ordering is required by RQ2.
                     */
                    printJobs(
                            jobRegistry,
                            completed
                    );
                }

                case "complete" -> {

                    if (parts.length >= 4
                            && parts[1].equals("-C")) {

                        completionScripts.put(
                                parts[3],
                                parts[2]
                        );

                    } else if (
                            parts.length >= 3
                            && parts[1].equals("-r")) {

                        completionScripts.remove(
                                parts[2]
                        );

                    } else if (
                            parts.length >= 3
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

                    if (executablePath == null) {

                        System.out.println(
                                command +
                                ": command not found"
                        );

                        continue;
                    }

                    if (isBackground) {

                        Process process =
                                ProcessExecutor.startCommand(
                                        parts,
                                        currentDirectory
                                );

                        BackgroundJob job =
                                jobRegistry.register(
                                        process,
                                        input.trim()
                                );

                        /*
                         * Starting a new job resets the marker
                         * calculation to the normal state.
                         */
                        jobsMarkerState = false;

                        System.out.println(
                                "[" +
                                job.number +
                                "] " +
                                process.pid()
                        );

                    } else {

                        ProcessExecutor.executeCommand(
                                parts,
                                currentDirectory
                        );
                    }
                }
            }
        }
    }


    /*
     * Reap all completed jobs.
     *
     * Automatic mode:
     *
     *     reapJobs(registry, false)
     *
     * jobs builtin:
     *
     *     reapJobs(registry, true)
     */
    private static List<BackgroundJob> reapJobs(
            JobRegistry registry,
            boolean fromJobsBuiltin)
            throws InterruptedException {

        List<BackgroundJob> completed =
                registry.findCompleted();

        if (completed.isEmpty()) {
            return completed;
        }

        /*
         * Save whether the newest job was completed.
         *
         * We determine this BEFORE removing it.
         */
        BackgroundJob newestBeforeReap =
                registry.newest();

        boolean newestCompleted = false;

        if (newestBeforeReap != null) {

            for (BackgroundJob job : completed) {

                if (job == newestBeforeReap) {
                    newestCompleted = true;
                    break;
                }
            }
        }

        /*
         * Wait for and remove every completed job.
         */
        for (BackgroundJob job : completed) {

            job.process.waitFor();

            registry.remove(job);
        }

        /*
         * If the newest job did not finish,
         * there is no need to change the active marker state.
         *
         * Example:
         *
         * [1]   Running
         * [2]-  Done
         * [3]+  Running
         *
         * remains:
         *
         * [1]   Running
         * [3]+  Running
         */
        if (!newestCompleted) {

            return completed;
        }

        /*
         * The '+' job completed.
         *
         * The remaining active jobs must now be recalculated.
         */
        BackgroundJob newNewest =
                registry.newest();

        if (newNewest == null) {

            jobsMarkerState = false;

        } else if (fromJobsBuiltin) {

            /*
             * Special state for:
             *
             * jobs
             *
             * after the current '+' job completes.
             *
             * The newest remaining job becomes '-'.
             */
            jobsMarkerState = true;

        } else {

            /*
             * Automatic reap before the next prompt:
             *
             * newest remaining job becomes '+'.
             */
            jobsMarkerState = false;
        }

        return completed;
    }


    /*
     * Print jobs builtin output.
     *
     * Running jobs MUST appear before completed jobs.
     */
    private static void printJobs(
            JobRegistry registry,
            List<BackgroundJob> completedJobs) {

        List<BackgroundJob> running =
                registry.list();

        /*
         * Sort by job number.
         */
        running.sort(
                (a, b) ->
                        Integer.compare(
                                a.number,
                                b.number
                        )
        );

        /*
         * Running jobs first.
         */
        for (BackgroundJob job : running) {

            char marker =
                    calculateRunningMarker(
                            registry,
                            job
                    );

            System.out.printf(
                    "[%d]%c  %-24s%s%n",
                    job.number,
                    marker,
                    "Running",
                    job.command
            );
        }

        /*
         * Completed jobs second.
         *
         * Their marker must be based on the marker
         * they had when they completed.
         */
        completedJobs.sort(
                (a, b) ->
                        Integer.compare(
                                a.number,
                                b.number
                        )
        );

        for (BackgroundJob job : completedJobs) {

            char marker =
                    calculateCompletedMarker(
                            registry,
                            job
                    );

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

        /*
         * After jobs has displayed the special '-' state,
         * return to the normal marker calculation.
         */
        jobsMarkerState = false;
    }


    /*
     * Calculate marker for a currently running job.
     */
    private static char calculateRunningMarker(
            JobRegistry registry,
            BackgroundJob job) {

        BackgroundJob newest =
                registry.newest();

        if (newest == null) {
            return ' ';
        }

        /*
         * Special state after a '+' job was reaped by `jobs`.
         */
        if (jobsMarkerState) {

            if (job == newest) {
                return '-';
            }

            return ' ';
        }

        /*
         * Normal state:
         * newest job is '+'.
         */
        if (job == newest) {
            return '+';
        }

        /*
         * Find second newest.
         */
        BackgroundJob secondNewest =
                registry.secondNewest();

        if (job == secondNewest) {
            return ' ';
        }

        return ' ';
    }


    /*
     * Calculate the marker for a completed job.
     *
     * We cannot use the current registry because the completed
     * job has already been removed.
     *
     * Therefore determine its marker from its job number
     * relative to the other jobs and the event that caused
     * reaping.
     */
    private static char calculateCompletedMarker(
            JobRegistry registry,
            BackgroundJob completedJob) {

        /*
         * A completed job can only retain '+' or '-'
         * if it was one of the two newest jobs before reaping.
         *
         * For RQ2:
         *
         * [1]   Running
         * [2]-  Done
         * [3]+  Running
         *
         * job 2 retains '-'.
         */
        BackgroundJob newest =
                registry.newest();

        if (newest != null) {

            /*
             * If completed job was immediately before
             * the current newest job, it was '-'.
             */
            if (completedJob.number
                    == newest.number - 1) {

                return '-';
            }
        }

        /*
         * If there are no active jobs, a completed job that
         * was the latest job was '+'.
         */
        if (newest == null) {

            return '+';
        }

        /*
         * If the completed job was newer than every
         * remaining job, it was the '+' job.
         */
        if (completedJob.number > newest.number) {
            return '+';
        }

        return ' ';
    }


    /*
     * Automatic reaping output.
     *
     * The job is printed exactly once.
     */
    private static void printCompletedJobs(
            List<BackgroundJob> completedJobs) {

        for (BackgroundJob job : completedJobs) {

            /*
             * Automatic reaping knows that a completed job
             * immediately preceding the newest remaining job
             * was '-'.
             *
             * Otherwise, the completed newest job was '+'.
             */
            char marker = '+';

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
}