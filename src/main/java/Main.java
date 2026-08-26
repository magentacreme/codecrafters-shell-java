import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Scanner;
import java.util.Set;

public class Main {

static final Set<String> builtins =Set.of("exit", "echo", "type", "pwd", "cd");

static File resolveFile(File currentDirectory, String path) {
    File file = new File(path);
    return file.isAbsolute() ? file : new File(currentDirectory, path);
}
static String[] parseInput(String input) {
        ArrayList<String> arguments = new ArrayList<>();
        StringBuilder current = new StringBuilder();

        boolean inSingleQuote = false;
        boolean inDoubleQuote = false;
        boolean argumentStarted = false;

        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);

            if (inSingleQuote) {
                argumentStarted = true;

                if (c == '\'') {
                    inSingleQuote = false;
                } else {
                    current.append(c);
                }
            }

            else if (inDoubleQuote) {
                argumentStarted = true;

                if (c == '\\') {
                    if (i + 1 < input.length()) {
                        char next = input.charAt(i + 1);

                        // Inside double quotes only
                        // \" and \\ are special
                        if (next == '"' || next == '\\') {
                            current.append(next);
                            i++;
                        } else {
                            current.append('\\');
                        }
                    } else {
                        current.append('\\');
                    }
                } else if (c == '"') {
                    inDoubleQuote = false;
                } else {
                    current.append(c);
                }
            }

            else {
                // Backslash escapes next character
                if (c == '\\') {
                    argumentStarted = true;

                    if (i + 1 < input.length()) {
                        i++;
                        current.append(input.charAt(i));
                    }
                }
                // Start single quote
                else if (c == '\'') {
                    argumentStarted = true;
                    inSingleQuote = true;
                }
                // Start double quote
                else if (c == '"') {
                    argumentStarted = true;
                    inDoubleQuote = true;
                }
                // Whitespace separates arguments
                else if (Character.isWhitespace(c)) {
                    if (argumentStarted) {
                        arguments.add(current.toString());
                        current.setLength(0);
                        argumentStarted = false;
                    }
                }
                // Normal character
                else {
                    argumentStarted = true;
                    current.append(c);
                }
            }
        }

        // Add last argument
        if (argumentStarted) {
            arguments.add(current.toString());
        }

        return arguments.toArray(new String[0]);
    }

static int findOutputRedirect(String[] parts) {
        for (int i = 0; i < parts.length; i++) {
            if (parts[i].equals(">") || parts[i].equals("1>")) {
                return i;
            }
        }
        return -1;
    }

static void executeEcho(String[] parts,File currentDirectory)throws Exception{
        
    int redirectIndex = findOutputRedirect(parts);

        if (redirectIndex == -1) {
            for (int i = 1; i < parts.length; i++) {
                if (i > 1) {
                    System.out.print(" ");
                }
                System.out.print(parts[i]);
            }

            System.out.println();
            return;
        }

        // Make sure filename exists
        if (redirectIndex + 1 >= parts.length) {
            System.out.println("Missing output file");
            return;
        }

        String outputFile = parts[redirectIndex + 1];
        StringBuilder output = new StringBuilder();

        for (int i = 1; i < redirectIndex; i++) {
            if (i > 1) {
                output.append(" ");
            }
            output.append(parts[i]);
        }
        output.append(System.lineSeparator());

        File file = resolveFile(currentDirectory, outputFile);
        // > overwrites existing file
        Files.writeString(file.toPath(), output.toString());
    }

static void executeCommand(String[] parts,File currentDirectory)throws Exception{
        
    int redirectIndex = findOutputRedirect(parts);

    if (redirectIndex == -1) {
        //parts[0] = executablePath;
        ProcessBuilder pb = new ProcessBuilder(parts);
        pb.directory(currentDirectory);
        pb.inheritIO();
        
        Process process = pb.start();
        process.waitFor();
        return;
    }
    if (redirectIndex + 1 >= parts.length) {
        System.out.println("Missing output file");
        return;
    }
    String outputFile = parts[redirectIndex + 1];
        // Keep only command + arguments before >
    String[] commandParts = Arrays.copyOf(parts, redirectIndex);

        // Use actual executable path
    //commandParts[0] = executablePath;

    ProcessBuilder pb = new ProcessBuilder(commandParts);
    pb.directory(currentDirectory);
    pb.redirectOutput(resolveFile(currentDirectory, outputFile));
    pb.redirectError(ProcessBuilder.Redirect.INHERIT);

    Process process = pb.start();
    process.waitFor();
}

static String findExecutable(String command) {
        String path = System.getenv("PATH");

        if (path == null) {
            return null;
        }

        String[] directories = path.split(":");

        for (String directory : directories) {
            File file = new File(directory, command);

            if (file.exists() && file.isFile() && file.canExecute()) {
                return file.getAbsolutePath();
            }
        }

        return null;
    }

public static void main(String[] args) throws Exception {
        Scanner sc = new Scanner(System.in);
        File currentDirectory = new File(System.getProperty("user.dir"));

        while (true) {
            System.out.print("$ ");

            String input = sc.nextLine();

            if (input.isEmpty()) {
                continue;
            }

            String[] parts = parseInput(input);

            if (parts.length == 0) {
                continue;
            }
            String command = parts[0];

            switch (command) {
                case "exit" -> {
                    sc.close();
                    System.exit(0);
                }

                case "echo" -> {
                    executeEcho(parts, currentDirectory);
                }

                case "type" -> {
                    if (parts.length < 2) {
                        continue;
                    }

                    String argument = parts[1];

                    // Builtin
                    if (builtins.contains(argument)) {
                        System.out.println(argument + " is a shell builtin");
                    }
                    // External executable
                    else {
                        String executable = findExecutable(argument);

                        if (executable != null) {
                            System.out.println(argument + " is " + executable);
                        } else {
                            System.out.println(argument + ": not found");
                        }
                    }
                }

                case "pwd" -> {
                    System.out.println(currentDirectory.getAbsolutePath());
                }

                case "cd" -> {
                    if (parts.length < 2) {
                        continue;
                    }

                    String path = parts[1];
                    File directory;

                    // cd ~
                    if (path.equals("~")) {
                        String home = System.getenv("HOME");
                        directory = new File(home);
                    }
                    // Absolute path
                    else if (path.startsWith("/")) {
                        directory = new File(path);
                    }
                    // Relative path
                    else {
                        directory = new File(currentDirectory, path);
                    }

                    if (directory.exists() && directory.isDirectory()) {
                        currentDirectory = directory.getCanonicalFile();
                    } else {
                        System.out.println("cd: " + path + ": No such file or directory");
                    }
                }

                default -> {
                    String executablePath = findExecutable(command);
                    if (executablePath != null) {
                        executeCommand(parts, currentDirectory);
                    } else {
                        System.out.println(command + ": command not found");
                    }
                }
            }
        }
    }}