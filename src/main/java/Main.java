import java.io.File;
import java.util.Scanner;

public class Main {
    public static void main(String[] args) throws Exception {
        
        Scanner sc = new Scanner(System.in);

        while(true){

            System.out.print("$ ");

            String input = sc.nextLine();

            String parts[] = input.split(" ",2);
            String command = parts[0];
            String argument = "";
            if(parts.length >1){
                argument = parts[1];
            }

            switch (command) {
                case "exit"->System.exit(0);
                case "echo"->System.out.println(argument);
                case "type" -> {
                    switch (argument) {
                        case "exit", "echo", "type" ->
                                System.out.println(argument + " is a shell builtin");
                        default ->{
                            String path = System.getenv("PATH");
                            String[] dir = path.split(":");
                            boolean found = false;
                            for(String d:dir){
                                File file = new File(d, argument);
                                if(file.exists() && file.canExecute()){
                                    System.out.println(argument + " is " + file.getAbsolutePath());
                                    found = true;
                                    break;
                                }
                            }
                            if(!found){
                                System.out.println(argument + " not found");
                            }
                        }
                    }
                }
                default->System.out.println(input + ": command not found");
            }
        }
    }
}
