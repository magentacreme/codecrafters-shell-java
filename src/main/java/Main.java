import java.io.File;
import java.util.Scanner;
import java.util.Set;

public class Main {

    public static void main(String[] args) throws Exception {

        Scanner sc = new Scanner(System.in);
        Set<String> builtins = Set.of("exit","echo","type","pwd","cd");

        File currentDirectory = new File(System.getProperty("user.dir"));

        while (true) {

            System.out.print("$ ");

            String input = sc.nextLine();

            String[] parts = input.split(" ");
            String command = parts[0];

            switch (command) {

                case "exit" -> System.exit(0);

                case "echo" -> {
                    for (int i = 1; i < parts.length; i++) {
                        if (i > 1) {
                            System.out.print(" ");
                        }
                        System.out.print(parts[i]);
                    }
                    System.out.println();
                }
                case "type" -> {
                    String argument = "";
                    if (parts.length > 1) {
                        argument = parts[1];
                    }

                    if (builtins.contains(argument)) {
                        System.out.println(argument + " is a shell builtin");
                    }
                    else {
                    String path = System.getenv("PATH");
                    String[] dir = path.split(":");
                    boolean found = false;

                    for (String d : dir) {
                        File file = new File(d, argument);
                            if (file.exists() && file.canExecute()) {
                                System.out.println(argument + " is " +file.getAbsolutePath());
                                found = true;
                                break;
                                }
                            }
                            if (!found) {
                                System.out.println(argument + ": not found");
                            }
                        }
                }

                case "pwd" -> System.out.println(currentDirectory.getAbsolutePath());

                case "cd" -> {
                    if(parts.length < 2) {
                        break;
                    }

                    String path = parts[1];
                    
                    File dir;
                    if(path.equals("~")) {
                        String Home = System.getenv("HOME");
                        dir = new File(Home);
                    } 
                    else if(path.startsWith("/")){
                        dir = new File(path);
                    }else {
                        dir = new File(currentDirectory, path);
                    }

                    if(dir.exists() && dir.isDirectory()) {
                        currentDirectory = dir.getCanonicalFile();
                    }else {
                        System.out.println("cd: " + path + ": No such file or directory");
                    }
                }

                default -> {
                    String path = System.getenv("PATH");
                    String[] dir = path.split(":");
                    String executablePath = null;

                    for (String d : dir) {
                        File file = new File(d, command);
                        if (file.exists() && file.canExecute()) {
                            executablePath = file.getAbsolutePath();
                            break;
                        }
                    }

                    if (executablePath != null) {
                        ProcessBuilder pb = new ProcessBuilder(parts);
                        pb.directory(currentDirectory);
                        pb.inheritIO();
                        Process process = pb.start();
                        process.waitFor();
                    } else {
                        System.out.println(input + ": command not found");
                    }
                }
            }
        }
    }
}