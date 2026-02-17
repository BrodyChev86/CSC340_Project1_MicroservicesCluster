package CSC340_Project1_MicroservicesCluster;
import java.util.Scanner;

public class Test{
    
    public static void main(String[] args){
        
        // Step 1: Create Scanner object
        Scanner sc = new Scanner(System.in);

        // Step 2: Prompt user for input
        System.out.print("Enter an integer: ");

        // Step 3: Read integer input
        int number = sc.nextInt();

        // Step 4: Print the entered integer
        System.out.println("You entered: " + number);

        // Close the scanner
        sc.close();
    }
}