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

    /*
     * A completed job must remember the marker it had
     * BEFORE it was removed from the active job table.
     */
    private static final class CompletedJob {
        private final BackgroundJob job;
        private final char marker;

        private CompletedJob(
                BackgroundJob job,
                char marker) {

            this.job = job;
            this.marker = marker;
        }
    }

    /*
     * Result of reaping.
     */
    private static final class ReapResult {
        private final List<CompletedJob> completedJobs;

        private ReapResult(
                List<CompletedJob> completedJobs) {

            this.completedJobs = completedJobs;
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
             * -------------------------------------------------
             * AUTOMATIC REAPING
             * -------------------------------------------------
             *
             * This happens before every prompt.
             *
             * If the current '+' job finishes here,
             * the newest remaining job becomes '+'.
             */
            ReapResult reapResult =
                    reapCompletedJobs(
                            backgroundJobs,
                            false
                    );

            printCompletedJobs(
                    reapResult.completedJobs
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
                     * -------------------------------------------------
                     * JOBS BUILTIN
                     * -------------------------------------------------
                     *
                     * Here + completion behaves differently from
                     * automatic reaping.
                     *
                     * Example:
                     *
                     * [1]   Running
                     * [2]-  Running
                     * [3]+  Running
                     *
                     * if job 3 completes:
                     *
                     * [3]+  Done
                     *
                     * remaining job 1 becomes '-'.
                     */
                    ReapResult jobsResult =
                            reapCompletedJobs(
                                    backgroundJobs,
                                    true
                            );

                    printCompletedJobs(
                            jobsResult.completedJobs
                    );

                    printRunningJobs(
                            backgroundJobs
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

    /*
     * ============================================================
     * REAP COMPLETED JOBS
     * ============================================================
     *
     * fromJobsBuiltin == false:
     *
     *     Automatic reaping before a prompt.
     *
     *     If '+' completes:
     *
     *         newest remaining -> '+'
     *
     *
     * fromJobsBuiltin == true:
     *
     *     Reaping caused by `jobs`.
     *
     *     If '+' completes:
     *
     *         newest remaining -> '-'
     *
     *
     * The important part is that completed jobs save their
     * ORIGINAL marker before being removed.
     */
    private static ReapResult reapCompletedJobs(
            List<BackgroundJob> backgroundJobs,
            boolean fromJobsBuiltin)
            throws InterruptedException {

        List<BackgroundJob> sortedJobs =
                new ArrayList<>(backgroundJobs);

        sortedJobs.sort(
                (a, b) ->
                        Integer.compare(
                                a.number,
                                b.number
                        )
        );

        /*
         * Calculate markers BEFORE removing completed jobs.
         */
        Map<Integer, Character> markers =
                calculateMarkers(sortedJobs);

        List<CompletedJob> completedJobs =
                new ArrayList<>();

        boolean plusCompleted = false;

        /*
         * Find every completed process.
         */
        for (BackgroundJob job : sortedJobs) {

            if (!job.process.isAlive()) {

                job.process.waitFor();

                char marker =
                        markers.getOrDefault(
                                job.number,
                                ' '
                        );

                completedJobs.add(
                        new CompletedJob(
                                job,
                                marker
                        )
                );

                if (marker == '+') {
                    plusCompleted = true;
                }
            }
        }

        /*
         * Nothing finished.
         */
        if (completedJobs.isEmpty()) {
            return new ReapResult(completedJobs);
        }

        /*
         * Remove all completed jobs from the active table.
         */
        for (CompletedJob completed :
                completedJobs) {

            backgroundJobs.remove(
                    completed.job
            );
        }

        /*
         * Re-sort the remaining active jobs.
         */
        backgroundJobs.sort(
                (a, b) ->
                        Integer.compare(
                                a.number,
                                b.number
                        )
        );

        /*
         * --------------------------------------------------------
         * CASE 1: '+' DID NOT COMPLETE
         * --------------------------------------------------------
         *
         * Example:
         *
         * [1]   Running
         * [2]-  Done
         * [3]+  Running
         *
         * After removing job 2:
         *
         * [1]   Running
         * [3]+  Running
         *
         * Therefore simply recalculate normal markers.
         */
        if (!plusCompleted) {
            return new ReapResult(completedJobs);
        }

        /*
         * --------------------------------------------------------
         * CASE 2: '+' COMPLETED
         * --------------------------------------------------------
         */
        if (backgroundJobs.isEmpty()) {
            return new ReapResult(completedJobs);
        }

        /*
         * AUTOMATIC REAPING
         *
         * Example:
         *
         * [1]-  Running
         * [2]+  Done
         *
         * becomes:
         *
         * [1]+  Running
         */
        if (!fromJobsBuiltin) {

            /*
             * Markers are calculated dynamically, so nothing
             * actually needs to be stored here.
             *
             * The newest active job will be '+' when
             * printRunningJobs() is called.
             */
            return new ReapResult(completedJobs);
        }

        /*
         * JOBS BUILTIN
         *
         * Example from RQ2:
         *
         * [1]   Running
         * [2]-  Done
         * [3]+  Done
         *
         * After job 3 is reaped:
         *
         * [1]-  Running
         *
         * This means that when '+' is reaped through `jobs`,
         * the newest remaining job is '-' rather than '+'.
         *
         * We don't store that marker permanently.
         *
         * Instead, set a temporary marker state for the
         * currently active jobs.
         *
         * This state is handled by the special
         * jobsMarkerOverride below.
         */
        jobsMarkerOverride = true;

        return new ReapResult(completedJobs);
    }

    /*
     * This flag is used only for the special case:
     *
     * `jobs` reaps the current '+' job.
     *
     * In that situation the newest remaining job must be '-'.
     */
    private static boolean jobsMarkerOverride = false;

    /*
     * Calculate the normal markers:
     *
     * oldest jobs       -> ' '
     * second newest     -> '-'
     * newest            -> '+'
     */
    private static Map<Integer, Character> calculateMarkers(
            List<BackgroundJob> jobs) {

        Map<Integer, Character> markers =
                new HashMap<>();

        int size = jobs.size();

        for (int i = 0; i < size; i++) {

            BackgroundJob job =
                    jobs.get(i);

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

    /*
     * Print completed jobs.
     *
     * The marker stored in CompletedJob is the marker
     * from BEFORE the job was removed.
     */
    private static void printCompletedJobs(
            List<CompletedJob> completedJobs) {

        for (CompletedJob completed :
                completedJobs) {

            BackgroundJob job =
                    completed.job;

            String command =
                    job.command.replaceAll(
                            "\\s*&$",
                            ""
                    );

            System.out.printf(
                    "[%d]%c  %-24s%s%n",
                    job.number,
                    completed.marker,
                    "Done",
                    command
            );
        }
    }

    /*
     * Print all currently running jobs.
     */
    private static void printRunningJobs(
            List<BackgroundJob> runningJobs) {

        if (runningJobs.isEmpty()) {
            jobsMarkerOverride = false;
            return;
        }

        List<BackgroundJob> sortedJobs =
                new ArrayList<>(runningJobs);

        sortedJobs.sort(
                (a, b) ->
                        Integer.compare(
                                a.number,
                                b.number
                        )
        );

        Map<Integer, Character> markers =
                calculateMarkers(sortedJobs);

        /*
         * Special RQ2 behavior:
         *
         * When `jobs` itself reaped '+', the newest
         * remaining job must be '-'.
         */
        if (jobsMarkerOverride) {

            for (BackgroundJob job :
                    sortedJobs) {

                markers.put(
                        job.number,
                        ' '
                );
            }

            /*
             * Newest remaining job -> '-'
             */
            BackgroundJob newest =
                    sortedJobs.get(
                            sortedJobs.size() - 1
                    );

            markers.put(
                    newest.number,
                    '-'
            );

            /*
             * If there are two or more jobs, the
             * second newest remains blank in this
             * special state.
             */
        }

        for (BackgroundJob job :
                sortedJobs) {

            char marker =
                    markers.getOrDefault(
                            job.number,
                            ' '
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
         * The special state only applies to this one
         * `jobs` command.
         */
        jobsMarkerOverride = false;
    }
}
