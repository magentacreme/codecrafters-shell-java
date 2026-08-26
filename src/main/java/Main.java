import java.util.Scanner;
public class Main {
    public static void main(String[] args) throws Exception {
        Scanner sc = new Scanner(System.in);

        String input ="";

        while(input.equals("exit") == false){
            System.out.print("$ ");

            input = sc.nextLine();
            System.out.println(input+": command not found");
        }
       
    }
}
