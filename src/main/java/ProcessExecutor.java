import java.io.File;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;

public final class ProcessExecutor {
    private ProcessExecutor() {
    }

    public static void executeEcho(String[] parts, File currentDirectory) throws Exception {
        int stdoutIndex = findOutputRedirect(parts);
        int stderrIndex = findErrorRedirect(parts);

        if (stderrIndex != -1 && stderrIndex + 1 < parts.length) {
            File errFile = resolveFile(currentDirectory, parts[stderrIndex + 1]);
            if (isAppendRedirect(parts[stderrIndex])) {
                Files.writeString(errFile.toPath(), "", StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            } else {
                Files.writeString(errFile.toPath(), "");
            }
        }

        int echoEnd = parts.length;
        if (stdoutIndex != -1) echoEnd = Math.min(echoEnd, stdoutIndex);
        if (stderrIndex != -1) echoEnd = Math.min(echoEnd, stderrIndex);

        StringBuilder output = new StringBuilder();
        for (int i = 1; i < echoEnd; i++) {
            if (i > 1) output.append(" ");
            output.append(parts[i]);
        }

        if (stdoutIndex != -1 && stdoutIndex + 1 < parts.length) {
            output.append(System.lineSeparator());
            File outFile = resolveFile(currentDirectory, parts[stdoutIndex + 1]);
            if (isAppendRedirect(parts[stdoutIndex])) {
                Files.writeString(outFile.toPath(), output.toString(), StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            } else {
                Files.writeString(outFile.toPath(), output.toString());
            }
        } else {
            System.out.println(output.toString());
        }
    }

    public static void executeCommand(String[] parts, File currentDirectory) throws Exception {
        int stdoutIndex = findOutputRedirect(parts);
        int stderrIndex = findErrorRedirect(parts);
        int endOfArgs = parts.length;
        if (stdoutIndex != -1) endOfArgs = Math.min(endOfArgs, stdoutIndex);
        if (stderrIndex != -1) endOfArgs = Math.min(endOfArgs, stderrIndex);

        ProcessBuilder processBuilder = new ProcessBuilder(Arrays.copyOf(parts, endOfArgs));
        processBuilder.directory(currentDirectory);

        if (stdoutIndex != -1 && stdoutIndex + 1 < parts.length) {
            File outputFile = resolveFile(currentDirectory, parts[stdoutIndex + 1]);
            if (isAppendRedirect(parts[stdoutIndex])) {
                processBuilder.redirectOutput(ProcessBuilder.Redirect.appendTo(outputFile));
            } else {
                processBuilder.redirectOutput(outputFile);
            }
        } else {
            processBuilder.redirectOutput(ProcessBuilder.Redirect.INHERIT);
        }

        if (stderrIndex != -1 && stderrIndex + 1 < parts.length) {
            File errorFile = resolveFile(currentDirectory, parts[stderrIndex + 1]);
            if (isAppendRedirect(parts[stderrIndex])) {
                processBuilder.redirectError(ProcessBuilder.Redirect.appendTo(errorFile));
            } else {
                processBuilder.redirectError(errorFile);
            }
        } else {
            processBuilder.redirectError(ProcessBuilder.Redirect.INHERIT);
        }

        Process process = processBuilder.start();
        process.waitFor();
    }

    public static String findExecutable(String command) {
        String path = System.getenv("PATH");
        if (path == null) return null;

        for (String directory : path.split(":")) {
            File file = new File(directory, command);
            if (file.exists() && file.isFile() && file.canExecute()) {
                return file.getAbsolutePath();
            }
        }
        return null;
    }

    private static int findOutputRedirect(String[] parts) {
        for (int i = 0; i < parts.length; i++) {
            if (parts[i].equals(">") || parts[i].equals("1>")
                    || parts[i].equals(">>") || parts[i].equals("1>>")) return i;
        }
        return -1;
    }

    private static int findErrorRedirect(String[] parts) {
        for (int i = 0; i < parts.length; i++) {
            if (parts[i].equals("2>") || parts[i].equals("2>>")) return i;
        }
        return -1;
    }

    private static boolean isAppendRedirect(String redirect) {
        return redirect.equals(">>") || redirect.equals("1>>") || redirect.equals("2>>");
    }

    private static File resolveFile(File currentDirectory, String path) {
        File file = new File(path);
        return file.isAbsolute() ? file : new File(currentDirectory, path);
    }
}
