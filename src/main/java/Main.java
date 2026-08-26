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
                        default -> System.out.println(argument + ": not found");
                    }
                }
                default->System.out.println(input + ": command not found");
            }
        }
    }
}
