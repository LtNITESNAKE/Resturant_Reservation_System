package Utils;

import Exceptions.DatabaseConnectionException;
import Models.Customer;
import Models.Manager;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Scanner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static Database.DatabaseManager.prepareStatement;

public class ValidationUtils {
    private  static final Scanner scanner = new Scanner(System.in);
    public static final int MAX_PHONE_LENGTH = 13;
    public static final int MAX_NAME_LENGTH = 50;
    public static final int MAX_EMAIL_LENGTH = 100;
    public static final int MAX_CUISINE_LENGTH = 100;


    public static int getValidIntInput(String prompt, int min, int max) {
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

    public static boolean isValidEmail(String email) {
        // Basic email regex pattern
        String emailRegex = "^[a-zA-Z0-9_+&*-]+(?:\\.[a-zA-Z0-9_+&*-]+)*@(?:[a-zA-Z0-9-]+\\.)+[a-zA-Z]{2,7}$";
        Pattern pattern = Pattern.compile(emailRegex);
        Matcher matcher = pattern.matcher(email);
        return matcher.matches();
    }
    public static boolean isValidPhone(String phone) {
        // A basic phone number regex pattern (for example: +1-234-567-8901)
        String phoneRegex = "^\\+?[0-9]{1,4}?[-.\\s]?(\\(?[0-9]{1,3}?\\)?[-.\\s]?[0-9]{1,4}[-.\\s]?[0-9]{1,4})$";
        Pattern pattern = Pattern.compile(phoneRegex);
        Matcher matcher = pattern.matcher(phone);
        return matcher.matches();
    }
    public static boolean validateManagerCredentials(String username, String hashedPassword) throws DatabaseConnectionException {
        String sql = "SELECT 1 FROM ManagerCredentials WHERE Username = ? AND PasswordHash = ?";
        try (PreparedStatement pstmt = prepareStatement(sql)) {
            pstmt.setString(1, username);
            pstmt.setString(2, hashedPassword);
            try (ResultSet rs = pstmt.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            throw new DatabaseConnectionException("Failed to validate manager credentials", e);
        }
    }
    public static boolean validateCustomerCredentials(String username, String hashedPassword) throws DatabaseConnectionException {
        String sql = "SELECT 1 FROM UserCredentials WHERE Username = ? AND PasswordHash = ?";
        try (PreparedStatement pstmt = prepareStatement(sql)) {
            pstmt.setString(1, username);
            pstmt.setString(2, hashedPassword);
            try (ResultSet rs = pstmt.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            throw new DatabaseConnectionException("Failed to validate customer credentials", e);
        }
    }
    public static boolean isValidPassword(String password) {
        if (password == null || password.length() < 8) {
            return false; // Must be at least 8 characters long
        }

        // Check for at least one uppercase letter, one lowercase letter, one number, and one special character
        boolean hasUpperCase = false;
        boolean hasLowerCase = false;
        boolean hasDigit = false;
        boolean hasSpecialChar = false;

        for (char c : password.toCharArray()) {
            if (Character.isUpperCase(c)) {
                hasUpperCase = true;
            } else if (Character.isLowerCase(c)) {
                hasLowerCase = true;
            } else if (Character.isDigit(c)) {
                hasDigit = true;
            } else if (!Character.isLetterOrDigit(c)) {
                hasSpecialChar = true;
            }
        }

        // Check if all conditions are met
        return hasUpperCase && hasLowerCase && hasDigit && hasSpecialChar;
    }
    public  static boolean getYesNoInput(String prompt) {

        while (true) {
            System.out.print(prompt);
            String input = scanner.nextLine().toLowerCase();
            if (input.startsWith("y")) return true;
            if (input.startsWith("n")) return false;
            System.out.println("Please enter y or n");
        }
    }
    public static boolean isValidName(String name) {
            if (name == null || name.trim().isEmpty()) {
                return false; // Name should not be empty
            }

            // Name must contain only letters (a-z, A-Z) and possibly spaces between names
            String regex = "^[A-Za-z]+(?: [A-Za-z]+)*$"; // Allows single or multiple words
            return name.matches(regex);
        }


    public static void validateCustomerInput(Customer customer, String username, String passwordHash, String salt) throws DatabaseConnectionException {
        if (customer == null) {
            throw new DatabaseConnectionException("Customer object cannot be null");
        }

        // Name validations
        validateName(customer.getFirstName(), "First Name", MAX_NAME_LENGTH);
        validateName(customer.getLastName(), "Last Name", MAX_NAME_LENGTH);

        // Email validation (required for customers)
        validateEmail(customer.getEmail());

        // Phone validation (required for customers)
        validatePhoneNumber(customer.getPhoneNumber());

        // Credential validations
        if (username == null || username.trim().isEmpty()) {
            throw new DatabaseConnectionException("Username cannot be empty");
        }

        if (passwordHash == null || salt == null) {
            throw new DatabaseConnectionException("Password hash and salt are required");
        }
    }
    public static void validateManagerInput(Manager manager, String username, String passwordHash, String salt) throws DatabaseConnectionException {
        // Comprehensive input validation
        if (manager == null) {
            throw new DatabaseConnectionException("Manager object cannot be null");
        }

        // Name validations
        validateName(manager.getFirstName(), "First Name", MAX_NAME_LENGTH);
        validateName(manager.getLastName(), "Last Name", MAX_NAME_LENGTH);

        // Optional email validation
        if (manager.getEmail() != null) {
            validateEmail(manager.getEmail());
        }

        // Optional phone validation
        if (manager.getPhoneNumber() != null) {
            validatePhoneNumber(manager.getPhoneNumber());
        }

        // Credential validations
        if (username == null || username.trim().isEmpty()) {
            throw new DatabaseConnectionException("Username cannot be empty");
        }

        if (passwordHash == null || salt == null) {
            throw new DatabaseConnectionException("Password hash and salt are required");
        }
    }
    private static void validateEmail(String email) throws DatabaseConnectionException {
        if (!email.matches("^[A-Za-z0-9+_.-]+@(.+)$")) {
            throw new DatabaseConnectionException("Invalid email format");
        }
    }
    private static void validateName(String name, String fieldName, int maxLength) throws DatabaseConnectionException {
        if (name == null || name.trim().isEmpty()) {
            throw new DatabaseConnectionException(fieldName + " cannot be empty");
        }
        if (name.length() > maxLength) {
            throw new DatabaseConnectionException(
                    fieldName + " must be " + maxLength + " characters or less"
            );
        }
    }
    private static void validatePhoneNumber(String phone) throws DatabaseConnectionException {
        if (!phone.matches("^\\+?[0-9]{10,14}$")) {
            throw new DatabaseConnectionException("Invalid phone number format");
        }
    }

    public static String trimToMaxLength(String value, int maxLength) {
        return value != null
                ? (value.length() > maxLength ? value.substring(0, maxLength) : value)
                : null;
    }

    public static LocalTime getValidTime() {
        while (true) {
            try {
                System.out.print("Enter time (HH:mm): ");
                String input = scanner.nextLine();
                return LocalTime.parse(input); // Will throw an exception if the format is invalid
            } catch (Exception e) {
                System.out.println("Invalid time format. Please use HH:mm.");
            }
        }
    }
    public static LocalDate getValidDate() {
        while (true) {
            try {
                System.out.print("Enter date (YYYY-MM-DD): ");
                String input = scanner.nextLine();
                LocalDate date = LocalDate.parse(input);

                // Check if the date is in the future
                if (date.isAfter(LocalDate.now())) {
                    return date;
                } else {
                    throw new DateTimeException("The date must be in the future.");
                }
            } catch (DateTimeException e) {
                System.out.println(e.getMessage()); // Show custom error for invalid dates
            } catch (Exception e) {
                System.out.println("Invalid date format. Please use YYYY-MM-DD.");
            }
        }
    }

}
