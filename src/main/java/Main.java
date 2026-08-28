import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Scanner;
import java.util.Set;

public class Main {

    private static final Set<String> BUILTINS = Set.of("exit", "echo", "type", "pwd", "cd", "jobs");

    private static String currentDir = System.getProperty("user.dir");

    private static final List<Job> backgroundJobs = new ArrayList<>();

    private static int nextJobId = 1;

    public static void main(String[] args) throws Exception {

        Scanner sc = new Scanner(System.in);

        while (true) {

            reapCompletedJobs(false);

            System.out.print("$ ");

            String input = sc.nextLine().trim();

            if (input.isEmpty()) {

                continue;

            }

            ArrayList<String> tokens = parseArguments(input);

            if (tokens.isEmpty()) {

                continue;

            }

            String command = tokens.get(0);

            List<String> argsList = tokens.subList(1, tokens.size());

            String redirectFile = null;

            String redirectErrFile = null;

            boolean isAppend = false;

            boolean isErrAppend = false;

            ArrayList<String> cleanArgs = new ArrayList<>();

            for (int i = 0; i < argsList.size(); i++) {

                String arg = argsList.get(i);

                if (arg.equals("2>>") && i + 1 < argsList.size()) {

                    redirectErrFile = argsList.get(i + 1);

                    isErrAppend = true;

                    i++;

                } else if ((arg.equals(">>") || arg.equals("1>>")) && i + 1 < argsList.size()) {

                    redirectFile = argsList.get(i + 1);

                    isAppend = true;

                    i++;

                } else if ((arg.equals(">") || arg.equals("1>")) && i + 1 < argsList.size()) {

                    redirectFile = argsList.get(i + 1);

                    isAppend = false;

                    i++;

                } else if (arg.equals("2>") && i + 1 < argsList.size()) {

                    redirectErrFile = argsList.get(i + 1);

                    isErrAppend = false;

                    i++;

                } else {

                    cleanArgs.add(arg);

                }

            }

            boolean isBackground = false;

            if (!cleanArgs.isEmpty() && cleanArgs.get(cleanArgs.size() - 1).equals("&")) {

                isBackground = true;

                cleanArgs.remove(cleanArgs.size() - 1);

            }

            try {

                if (redirectFile != null) {

                    File file =

                            redirectFile.startsWith("/")

                                    ? new File(redirectFile)

                                    : new File(currentDir, redirectFile);

                    if (file.getParentFile() != null) {

                        file.getParentFile().mkdirs();

                    }

                    file.createNewFile();

                }

                if (redirectErrFile != null) {

                    File file =

                            redirectErrFile.startsWith("/")

                                    ? new File(redirectErrFile)

                                    : new File(currentDir, redirectErrFile);

                    if (file.getParentFile() != null) {

                        file.getParentFile().mkdirs();

                    }

                    file.createNewFile();

                }

            } catch (Exception e) {

            }

            String[] commandArgs = cleanArgs.toArray(new String[0]);

            ArrayList<String> fullPartsList = new ArrayList<>();

            fullPartsList.add(command);

            fullPartsList.addAll(cleanArgs);

            String[] parts = fullPartsList.toArray(new String[0]);

            switch (command) {

                case "exit":

                    return;

                case "echo":

                    String echoOutput = String.join(" ", commandArgs);

                    writeOutput(echoOutput, redirectFile, isAppend);

                    break;

                case "pwd":

                    writeOutput(currentDir, redirectFile, isAppend);

                    break;

                case "cd":

                    handleCdCommand(commandArgs);

                    break;

                case "type":

                    handleTypeCommand(commandArgs, redirectFile, isAppend);

                    break;

                case "jobs":

                    reapCompletedJobs(true);

                    break;

                default:

                    handleExternalProgram(

                            command,

                            parts,

                            redirectFile,

                            redirectErrFile,

                            isAppend,

                            isErrAppend,

                            isBackground,

                            input);

                    break;

            }

        }

    }

    static class Job {

        int id;

        Process process;

        String command;

        public Job(int id, Process process, String command) {

            this.id = id;

            this.process = process;

            this.command = command;

        }

    }

    private static void reapCompletedJobs(boolean printRunning) {

        int totalDisplayedJobs = backgroundJobs.size();

        Iterator<Job> iterator = backgroundJobs.iterator();

        int index = 0;

        while (iterator.hasNext()) {

            Job job = iterator.next();

            boolean isAlive = job.process.isAlive();

            String marker = " ";

            if (index == totalDisplayedJobs - 1) {

                marker = "+";

            } else if (index == totalDisplayedJobs - 2) {

                marker = "-";

            }

            if (isAlive) {

                if (printRunning) {

                    String paddedStatus = String.format("%-24s", "Running");

                    System.out.println(

                            "[" + job.id + "]" + marker + "  " + paddedStatus + job.command);

                }

                index++;

            } else {

                String paddedStatus = String.format("%-24s", "Done");

                String displayCmd = job.command;

                if (displayCmd.endsWith("&")) {

                    displayCmd = displayCmd.substring(0, displayCmd.length() - 1).trim();

                }

                System.out.println("[" + job.id + "]" + marker + "  " + paddedStatus + displayCmd);

                iterator.remove();

                index++;

            }

        }

    }

    private static void writeOutput(String text, String redirectFile, boolean append) {

        if (redirectFile != null) {

            try {

                File file =

                        redirectFile.startsWith("/")

                                ? new File(redirectFile)

                                : new File(currentDir, redirectFile);

                if (file.getParentFile() != null) {

                    file.getParentFile().mkdirs();

                }

                try (PrintWriter writer = new PrintWriter(new FileWriter(file, append))) {

                    writer.println(text);

                }

            } catch (Exception e) {

                System.err.println("Error writing to redirect file: " + e.getMessage());

            }

        } else {

            System.out.println(text);

        }

    }

    private static ArrayList<String> parseArguments(String input) {

        ArrayList<String> tokens = new ArrayList<>();

        StringBuilder currentToken = new StringBuilder();

        boolean insideSingleQuotes = false;

        boolean insideDoubleQuotes = false;

        boolean isEscaped = false;

        boolean hasContent = false;

        for (int i = 0; i < input.length(); i++) {

            char c = input.charAt(i);

            if (isEscaped) {

                currentToken.append(c);

                hasContent = true;

                isEscaped = false;

            } else if (c == '\\' && !insideSingleQuotes && !insideDoubleQuotes) {

                isEscaped = true;

            } else if (c == '\\' && insideDoubleQuotes) {

                if (i + 1 < input.length()) {

                    char nextChar = input.charAt(i + 1);

                    if (nextChar == '"' || nextChar == '\\') {

                        currentToken.append(nextChar);

                        hasContent = true;

                        i++;

                    } else {

                        currentToken.append(c);

                        hasContent = true;

                    }

                } else {

                    currentToken.append(c);

                    hasContent = true;

                }

            } else if (c == '\'' && !insideDoubleQuotes) {

                insideSingleQuotes = !insideSingleQuotes;

                hasContent = true;

            } else if (c == '"' && !insideSingleQuotes) {

                insideDoubleQuotes = !insideDoubleQuotes;

                hasContent = true;

            } else if (c == ' ' && !insideSingleQuotes && !insideDoubleQuotes) {

                if (currentToken.length() > 0 || hasContent) {

                    tokens.add(currentToken.toString());

                    currentToken.setLength(0);

                    hasContent = false;

                }

            } else {

                currentToken.append(c);

            }

        }

        if (currentToken.length() > 0 || hasContent) {

            tokens.add(currentToken.toString());

        }

        return tokens;

    }

    private static void handleCdCommand(String[] args) {

        if (args.length == 0) {

            return;

        }

        String targetPathStr = args[0];

        if (targetPathStr.equals("~")) {

            String homeDir = System.getenv("HOME");

            if (homeDir != null) {

                targetPathStr = homeDir;

            }

        }

        File targetDir =

                targetPathStr.startsWith("/")

                        ? new File(targetPathStr)

                        : new File(currentDir, targetPathStr);

        if (targetDir.exists() && targetDir.isDirectory()) {

            try {

                currentDir = targetDir.getCanonicalPath();

            } catch (Exception e) {

                System.out.println("cd: " + targetPathStr + ": No such file or directory");

            }

        } else {

            System.out.println("cd: " + targetPathStr + ": No such file or directory");

        }

    }

    private static void handleTypeCommand(String[] args, String redirectFile, boolean append) {

        if (args.length == 0) {

            System.out.println("type: missing operand");

            return;

        }

        String targetCommand = args[0];

        String outputMsg;

        if (BUILTINS.contains(targetCommand)) {

            outputMsg = targetCommand + " is a shell builtin";

        } else {

            File executable = findInPath(targetCommand);

            if (executable != null) {

                outputMsg = targetCommand + " is " + executable.getAbsolutePath();

            } else {

                outputMsg = targetCommand + ": not found";

            }

        }

        writeOutput(outputMsg, redirectFile, append);

    }

    private static void handleExternalProgram(

            String command,

            String[] fullInputParts,

            String redirectFile,

            String redirectErrFile,

            boolean append,

            boolean errAppend,

            boolean isBackground,

            String rawInput) {

        File executable = findInPath(command);

        if (executable != null) {

            try {

                String[] executionParts = fullInputParts;

                if (isBackground

                        && fullInputParts.length > 0

                        && fullInputParts[fullInputParts.length - 1].equals("&")) {

                    executionParts = Arrays.copyOf(fullInputParts, fullInputParts.length - 1);

                }

                ProcessBuilder pb = new ProcessBuilder(fullInputParts);

                pb.directory(new File(currentDir));

                if (redirectFile != null) {

                    File file =

                            redirectFile.startsWith("/")

                                    ? new File(redirectFile)

                                    : new File(currentDir, redirectFile);

                    if (file.getParentFile() != null) {

                        file.getParentFile().mkdirs();

                    }

                    pb.redirectOutput(

                            append

                                    ? ProcessBuilder.Redirect.appendTo(file)

                                    : ProcessBuilder.Redirect.to(file));

                } else {

                    pb.redirectOutput(ProcessBuilder.Redirect.INHERIT);

                }

                if (redirectErrFile != null) {

                    File file =

                            redirectErrFile.startsWith("/")

                                    ? new File(redirectErrFile)

                                    : new File(currentDir, redirectErrFile);

                    if (file.getParentFile() != null) {

                        file.getParentFile().mkdirs();

                    }

                    pb.redirectError(

                            errAppend

                                    ? ProcessBuilder.Redirect.appendTo(file)

                                    : ProcessBuilder.Redirect.to(file));

                } else {

                    pb.redirectError(ProcessBuilder.Redirect.INHERIT);

                }

                Process process = pb.start();

                if (isBackground) {

                    int jobId = nextJobId++;

                    backgroundJobs.add(new Job(jobId, process, rawInput));

                    System.out.println("[" + jobId + "] " + process.pid());

                } else {

                    process.waitFor();

                }

            } catch (Exception e) {

                System.out.println(command + ": command not found");

            }

        } else {

            System.out.println(command + ": command not found");

        }

    }

    private static File findInPath(String command) {

        String pathEnv = System.getenv("PATH");

        if (pathEnv == null) return null;

        String[] directories = pathEnv.split(File.pathSeparator);

        for (String dir : directories) {

            File file = new File(dir, command);

            if (file.isFile() && file.canExecute()) {

                return file;

            }

        }

        return null;

    }

}

