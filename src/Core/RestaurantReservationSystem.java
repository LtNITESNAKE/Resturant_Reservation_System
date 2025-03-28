package Core;

import Database.*;
import Security.*;
import UI.ConsoleInterface;
import java.util.Scanner;

public class RestaurantReservationSystem {
    private final DatabaseManager dbManager;
    private final AuthenticationManager authManager;
    private final ConsoleInterface consoleInterface;
    private final Scanner scanner;



    public RestaurantReservationSystem() {
        this.dbManager = DatabaseManager.getInstance();
        this.authManager = new AuthenticationManager();
        this.scanner = new Scanner(System.in);
        this.consoleInterface = new ConsoleInterface(scanner, dbManager, authManager);
    }
    public static void main(String[] args) {
        try {
            RestaurantReservationSystem system = new RestaurantReservationSystem();
            system.start();
        } catch (Exception e) {
            System.err.println("System Error: " + e.getMessage());
           throw e;
        }
    }
    private void start() {
        boolean running = true;
        while (running) {
            displayMainMenu();
            int choice = getValidIntInput("Enter your choice: ", 1, 5);

            try {
                switch (choice) {
                    case 1 -> consoleInterface.handleCustomerLogin();
                    case 2 -> consoleInterface.handleManagerLogin();
                    case 3 -> consoleInterface.handleCustomerRegistration();
                    case 4 -> consoleInterface.handleManagerRegistration(); // New option
                    case 5 -> running = false;
                    default -> System.out.println("Invalid choice. Please try again.");
                }
            } catch (Exception e) {
                System.out.println("Error: " + e.getMessage());
            }
        }
        System.out.println("Thank you for using the Restaurant Reservation System. Goodbye!");
    }
    private void displayMainMenu() {
        System.out.println("\n=== Restaurant Reservation System ===");
        System.out.println("1. Customer Login");
        System.out.println("2. Manager Login");
        System.out.println("3. Customer Registration");
        System.out.println("4. Manager Registration"); // New menu option
        System.out.println("5. Exit");
    }
    private int getValidIntInput(String prompt, int min, int max) {
        while (true) {
            try {
                System.out.print(prompt);
                int input = Integer.parseInt(scanner.nextLine());
                if (input >= min && input <= max) {
                    return input;
                }
                System.out.println("Please enter a number between " + min + " and " + max);
            } catch (NumberFormatException e) {
                System.out.println("Invalid input. Please enter a number.");
            }
        }
    }
}