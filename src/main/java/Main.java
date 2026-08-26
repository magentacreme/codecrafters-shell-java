import java.util.Scanner;
public class Main {
    public static void main(String[] args) throws Exception {
        System.out.print("$ ");
        Scanner sc = new Scanner(System.in);
        for (int i = 0; i < 1; i+=0) {
            String input = sc.nextLine();
            System.out.println(input+": command not found");
            System.out.print("$ ");
        }
    }
}
