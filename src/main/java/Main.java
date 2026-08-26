import java.util.Scanner;
public class Main {
    public static void main(String[] args) throws Exception {
        Scanner sc = new Scanner(System.in);

        String command = sc.nextLine();

        while(command.equals("exit") == false){
            System.out.print("$ ");

            String input = sc.nextLine();
            System.out.println(input+": command not found");
        }
       
    }
}
