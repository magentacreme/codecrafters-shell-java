import java.io.File;
import java.util.Scanner;
import java.util.Set;

public class Main {
    static final Set<String> builtins = Set.of("exit", "echo", "type", "pwd", "cd");

    public static void main(String[] args) throws Exception {
        Scanner scanner = new Scanner(System.in);
        File currentDirectory = new File(System.getProperty("user.dir"));

        while (true) {
            System.out.print("$ ");
            String input = scanner.nextLine();

            if (input.isEmpty()) {
                continue;
            }

            String[] parts = CommandParser.parse(input);
            if (parts.length == 0) {
                continue;
            }

            String command = parts[0];
            switch (command) {
                case "exit" -> {
                    scanner.close();
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
                        ProcessExecutor.executeCommand(parts, currentDirectory);
                    } else {
                        System.out.println(command + ": command not found");
                    }
                }
            }
        }
    }
}
