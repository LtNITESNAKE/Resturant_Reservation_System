package UI;

import Database.*;
import Models.*;
import Security.*;
import Exceptions.*;
import Utils.ValidationUtils;
import java.util.*;
import java.time.*;
import Core.*;
import static Utils.ValidationUtils.*;

public class ConsoleInterface {
    private final Scanner scanner;
    private final DatabaseManager dbManager;
    private final AuthenticationManager authManager;
    private final ReservationManager reservationManager;
    private Customer currentCustomer;
    private Manager currentManager;

    // Constructor
    public ConsoleInterface(Scanner scanner, DatabaseManager dbManager, AuthenticationManager authManager) {
        this.scanner = scanner;
        this.dbManager = dbManager;
        this.authManager = authManager;
        this.reservationManager = new ReservationManager();
    }


    // Customer Operation

    public void handleCustomerLogin() throws AuthenticationException {
        System.out.print("Username: ");
        String username = scanner.nextLine();
        System.out.print("Password: ");
        String password = scanner.nextLine();

        currentCustomer = authManager.authenticateCustomer(username, password);
        if (currentCustomer != null) {
            System.out.println("Welcome back, " + currentCustomer.getFullName() + "!");
            handleCustomerMenu();
        }
    }
    private void handleCustomerMenu() {
        boolean running = true;
        while (running && currentCustomer != null) {
            displayCustomerMenu();
            int choice = getValidIntInput("Enter your choice: ", 1, 6);

            try {
                switch (choice) {
                    case 1 -> makeReservation();
                    case 2 -> viewMyReservations(currentCustomer.getCustomerID());
                    case 3 -> joinWaitlist();
                    case 4 -> viewWaitlistStatus();
                    case 5 -> updateProfile();
                    case 6 -> {
                        currentCustomer = null;
                        running = false;
                    }
                }
            } catch (Exception e) {
                System.out.println("Error: " + e.getMessage());
            }
        }
    }
    private void updateProfile() throws DatabaseConnectionException, AuthenticationException {
        System.out.println("\n=== Update Profile ===");

        if (currentCustomer == null) {
            System.out.println("No customer found. Please log in first.");
            return;
        }

        System.out.print("New email (press Enter to keep current: " + currentCustomer.getEmail() + "): ");
        String email = scanner.nextLine().trim();

        System.out.print("New phone (press Enter to keep current: " + currentCustomer.getPhoneNumber() + "): ");
        String phone = scanner.nextLine().trim();

        System.out.print("New password (press Enter to keep current): ");
        String password = scanner.nextLine().trim();

        String oldPassword = ""; // Assuming you will ask for the old password before updating
        if (!password.isEmpty()) {
            System.out.print("Enter current password to update: ");
            oldPassword = scanner.nextLine().trim();
        }

        // Update email if valid
        if (!email.isEmpty()) {
            if (ValidationUtils.isValidEmail(email)) {
                currentCustomer.setEmail(email);
            } else {
                System.out.println("Invalid email format");
                return;
            }
        }

        // Update phone if valid
        if (!phone.isEmpty()) {
            if (ValidationUtils.isValidPhone(phone)) {
                currentCustomer.setPhoneNumber(phone);
            } else {
                System.out.println("Invalid phone format");
                return;
            }
        }

        // Update profile in the database
        dbManager.updateCustomer(currentCustomer);

        // Update password if not empty
        if (!password.isEmpty()) {
            // Pass oldPassword and newPassword to the method
            authManager.updateCustomerPassword(currentCustomer.getCustomerID(), oldPassword, password);
        }

        System.out.println("Profile updated successfully!");
    }
    private void displayCustomerMenu() {
        System.out.println("\n=== Customer Menu ===");
        System.out.println("1. Make Reservation");
        System.out.println("2. View My Reservations");
        System.out.println("3. Join Waitlist");
        System.out.println("4. View Waitlist Status");
        System.out.println("5. Update Profile");
        System.out.println("6. Logout");
    }
    public void handleCustomerRegistration() {
        try {
            System.out.println("\n=== Customer Registration ===");
            System.out.print("First Name: ");
            String firstName = scanner.nextLine();
            System.out.print("Last Name: ");
            String lastName = scanner.nextLine();
            if (!ValidationUtils.isValidName(firstName) || !ValidationUtils.isValidName(lastName)) {
                throw new IllegalArgumentException("First name and last name must only contain letters and spaces.");
            }

            System.out.print("Email: ");
            String email = scanner.nextLine();
            System.out.print("Phone: ");
            String phone = scanner.nextLine();
            if (!ValidationUtils.isValidEmail(email) || !ValidationUtils.isValidPhone(phone)) {
                throw new IllegalArgumentException("Invalid email or phone format");
            }
            System.out.print("Username: ");
            String username = scanner.nextLine();
            System.out.print("Password: ");
            String password = scanner.nextLine();

            if(!isValidPassword(password)) {
                throw new IllegalArgumentException("Password must be at least 8 characters long," +
                        "include upper lowercase letters, " +
                        "numbers, and special characters");
            }

            Customer customer = new Customer(firstName, lastName, email, phone);
            authManager.registerCustomer(customer, username, password);
            System.out.println("Registration successful! Please login.");
        } catch (Exception e) {
            System.out.println("Registration failed: " + e.getMessage());
        }
    }

    // Customer Menu
    private void makeReservation() throws DatabaseConnectionException, ReservationException {
        System.out.println("\n=== Make Reservation ===");

        // Get party size
        int partySize = getValidIntInput("Enter party size (1-20): ", 1, 20);

        // Get date and time
        LocalDate date = getValidDate();
        LocalTime time = getValidTime();

        // Get available tables
        List<Table> availableTables = reservationManager.getAvailableTables(partySize, date, time);
        if (availableTables.isEmpty()) {
            System.out.println("No tables available. Would you like to join the waitlist? (y/n)");
            if (scanner.nextLine().toLowerCase().startsWith("y")) {
                joinWaitlist();
            }
            return;
        }

        // Display available tables
        System.out.println("\nAvailable Tables:");
        for (int i = 0; i < availableTables.size(); i++) {
            Table table = availableTables.get(i);
            System.out.printf("%d. Table %s (Capacity: %d)%n",
                    i + 1, table.getTableNumber(), table.getCapacity());
        }

        // Get table selection
        int tableChoice = getValidIntInput("Select table number: ", 1, availableTables.size());
        Table selectedTable = availableTables.get(tableChoice - 1);

        // Get special requests
        System.out.print("Special requests (press Enter if none): ");
        String specialRequests = scanner.nextLine();

        // Create reservation
        Reservation reservation = new Reservation(
                currentCustomer.getCustomerID(),
                selectedTable.getTableID(),
                date,
                time,
                partySize
        );
        reservation.setSpecialRequests(specialRequests);

        reservationManager.createReservation(reservation);
        System.out.println("Reservation created successfully!");
    }
    public void viewMyReservations(int customerid) throws DatabaseConnectionException {
        System.out.println("\n=== My Reservations ===");
        List<Reservation> reservations = reservationManager.getCustomerReservations(customerid);

        if (reservations.isEmpty()) {
            System.out.println("No reservations found.");
            return;
        }

        for (Reservation reservation : reservations) {
            System.out.printf("\nID: %d | Date: %s | Time: %s | Party: %d | Status: %s%n",
                    reservation.getReservationID(), reservation.getReservationDate(),
                    reservation.getReservationTime(), reservation.getPartySize(),
                    reservation.getStatus());
        }
    }
    private void joinWaitlist() throws DatabaseConnectionException {
        System.out.println("\n=== Join Waitlist ===");

        int partySize = getValidIntInput("Enter party size (1-20): ", 1, 20);
        LocalDate date = getValidDate();
        LocalTime time = getValidTime();

        // Get the current highest queuePosition for the requested date and time from the database
        int queuePosition = getNextQueuePosition(date, time);



        // Create the Waitlist object with the calculated queuePosition
        Waitlist waitlist = new Waitlist(
                currentCustomer.getCustomerID(),
                date,
                time,
                partySize,
                queuePosition
        );

        // Add the new waitlist entry to the database
        reservationManager.addToWaitlist(waitlist);
        System.out.println("Added to waitlist successfully!");
    }
    private int getNextQueuePosition(LocalDate date, LocalTime time) throws DatabaseConnectionException {
        return  dbManager.getHighestQueuePosition(date, time) + 1;
    }
    private void viewWaitlistStatus() throws DatabaseConnectionException {
        System.out.println("\n=== Waitlist Status ===");
        List<Waitlist> waitlistEntries = reservationManager
                .getCustomerWaitlistEntries(currentCustomer.getCustomerID());

        if (waitlistEntries.isEmpty()) {
            System.out.println("You are not on any waitlist.");
            return;
        }

        for (Waitlist entry : waitlistEntries) {
            System.out.printf("\nWaitlist ID: %d%n", entry.getWaitlistID());
            System.out.printf("Position: %d%n", entry.getQueuePosition());
            System.out.printf("Date: %s%n", entry.getRequestedDate());
            System.out.printf("Time: %s%n", entry.getRequestedTime());
            System.out.printf("Party Size: %d%n", entry.getPartySize());
            System.out.printf("Status: %s%n", entry.getStatus());
            System.out.printf("Estimated Wait Time: %d minutes%n", entry.getWaitTime());
            System.out.println("------------------------");
        }
    }



    // Manager Operation

    public void handleManagerLogin() throws AuthenticationException {
        System.out.print("Username: ");
        String username = scanner.nextLine();
        System.out.print("Password: ");
        String password = scanner.nextLine();

        currentManager = authManager.authenticateManager(username, password);
        if (currentManager != null) {
            System.out.println("Welcome, Manager " + currentManager.getFullName() + "!");
            handleManagerMenu();
        }
    }
    private void handleManagerMenu() {
        boolean running = true;
        while (running && currentManager != null) {
            displayManagerMenu();
            int choice = getValidIntInput("Enter your choice: ", 1, 7);

            try {
                switch (choice) {
                    case 1 -> manageReservations();
                    case 2 -> manageTables();
                    case 3 -> manageWaitlist();
                    case 4 -> viewReports();
                    case 5 -> addNewTable();
                    case 6 -> updateTableStatus();
                    case 7 -> {
                        currentManager = null;
                        running = false;
                    }
                }
            } catch (Exception e) {
                System.out.println("Error: " + e.getMessage());
            }
        }
    }
    private void displayManagerMenu() {
        System.out.println("\n=== Manager Menu ===");
        System.out.println("1. Manage Reservations");
        System.out.println("2. Manage Tables");
        System.out.println("3. Manage Waitlist");
        System.out.println("4. View Reports");
        System.out.println("5. Add New Table");
        System.out.println("6. Update Table Status");
        System.out.println("7. Logout");
    }
    public void handleManagerRegistration() {
        try {
            System.out.println("\n=== Manager Registration ===");

            System.out.print("First Name: ");
            String firstName = scanner.nextLine();
            System.out.print("Last Name: ");
            String lastName = scanner.nextLine();

            if (!ValidationUtils.isValidName(firstName) || !ValidationUtils.isValidName(lastName)) {
                throw new IllegalArgumentException("First name and last name must only contain letters and spaces.");
            }

            System.out.print("Email : ");
            String email = scanner.nextLine().trim();
            email = email.isEmpty() ? null : email;
            System.out.print("Phone Number : ");
            String phoneNumber = scanner.nextLine().trim();
            phoneNumber = phoneNumber.isEmpty() ? null : phoneNumber;

            if (!ValidationUtils.isValidEmail(email) || !ValidationUtils.isValidPhone(phoneNumber) ) {
                throw new IllegalArgumentException("Invalid email or phone format");
            }


            System.out.print("Username: ");
            String username = scanner.nextLine();
            System.out.print("Password: ");
            String password = scanner.nextLine();
            if(!isValidPassword(password)) {
                throw new IllegalArgumentException("Password must be at least 8 characters long," +
                        "include upper lowercase letters, " +
                        "numbers, and special characters");
            }


            Manager manager = new Manager(firstName, lastName, email, phoneNumber);
            authManager.registerManager(manager, username, password);
            System.out.println("Manager registration successful! Please login.");
        } catch (Exception e) {
            System.out.println("Registration failed: " + e.getMessage());
        }
    }

    // Manager Menu Methods
    private void manageReservations() throws DatabaseConnectionException {
        System.out.println("\n=== Manage Reservations ===");
        List<Reservation> activeReservations = reservationManager.getActiveReservations();

        if (activeReservations.isEmpty()) {
            System.out.println("No active reservations.");
            return;
        }

        for (Reservation res : activeReservations) {
            System.out.printf("\nID: %d | Date: %s | Time: %s | Party: %d | Status: %s%n",
                    res.getReservationID(), res.getReservationDate(),
                    res.getReservationTime(), res.getPartySize(), res.getStatus());
        }

        int resId = getValidIntInput("Enter reservation ID to update (0 to exit): ", 0,
                Integer.MAX_VALUE);
        if (resId > 0) {
            System.out.println("1. Confirm\n2. Complete\n3. Cancel");
            int action = getValidIntInput("Choose action: ", 1, 3);

            switch (action) {
                case 1 -> reservationManager.updateReservationStatus(resId,
                        Reservation.ReservationStatus.CONFIRMED);
                case 2 -> reservationManager.updateReservationStatus(resId,
                        Reservation.ReservationStatus.COMPLETED);
                case 3 -> reservationManager.updateReservationStatus(resId,
                        Reservation.ReservationStatus.CANCELLED);
            }
        }
    }
    private void manageTables() throws DatabaseConnectionException {
        System.out.println("\n=== Manage Tables ===");
        List<Table> tables = dbManager.getAllTables();

        for (Table table : tables) {
            System.out.printf("\nTable %s | Capacity: %d | Status: %s%n",
                    table.getTableNumber(), table.getCapacity(), table.getStatus());
        }
    }
    private void manageWaitlist() throws DatabaseConnectionException {
        System.out.println("\n=== Manage Waitlist ===");
        List<Waitlist> waitlist = dbManager.getActiveWaitlist();

        if (waitlist.isEmpty()) {
            System.out.println("No active waitlist entries.");
            return;
        }

        for (Waitlist entry : waitlist) {
            System.out.printf("\nID: %d | Position: %d | Party: %d | Wait Time: %d mins%n",
                    entry.getWaitlistID(), entry.getQueuePosition(),
                    entry.getPartySize(), entry.getWaitTime());
        }

        int id = getValidIntInput("Enter ID to update (0 to exit): ", 0, Integer.MAX_VALUE);
        if (id > 0) {
            System.out.println("1. Seat\n2. Remove");
            int action = getValidIntInput("Choose action: ", 1, 2);

            if (action == 1) {
                reservationManager.seatFromWaitlist(id);
            } else {
                reservationManager.removeFromWaitlist(id);
            }
        }
    }
    private void viewReports() throws DatabaseConnectionException, InvalidStatusException {
        System.out.println("\n=== Reports ===");
        System.out.println("1. Daily Reservations");
        System.out.println("2. Table Utilization");
        System.out.println("3. Waitlist Statistics");

        int choice = getValidIntInput("Select report: ", 1, 3);
        LocalDate date = getValidDate();

        switch (choice) {
            case 1 -> reservationManager.generateDailyReservationReport(date);
            case 2 -> reservationManager.generateTableUtilizationReport(date);
            case 3 -> reservationManager.generateWaitlistReport(date);
        }
    }
    private void addNewTable() throws DatabaseConnectionException {
        System.out.println("\n=== Add New Table ===");

        System.out.print("Table Number: ");
        String tableNumber = scanner.nextLine();

        int capacity = getValidIntInput("Capacity (1-20): ", 1, 20);

        System.out.print("Location: ");
        String location = scanner.nextLine();

        boolean hasWindow = getYesNoInput("Has window? (y/n): ");
        boolean isPrivate = getYesNoInput("Is private? (y/n): ");

        Table table = new Table(tableNumber, capacity, 1); // Default category ID
        table.setLocation(location);
        table.setHasWindow(hasWindow);
        table.setPrivate(isPrivate);
        table.setLastModifiedBy(currentManager.getManagerID());

        dbManager.createTable(table);
        System.out.println("Table added successfully!");
    }
    private void updateTableStatus() throws DatabaseConnectionException {
        System.out.println("\n=== Update Table Status ===");
        List<Table> tables = dbManager.getAllTables();

        for (Table table : tables) {
            System.out.printf("Table %s: %s%n", table.getTableNumber(), table.getStatus());
        }

        System.out.print("Enter table number: ");
        String tableNumber = scanner.nextLine();

        System.out.println("1. Available");
        System.out.println("2. Occupied");
        System.out.println("3. Reserved");
        System.out.println("4. Maintenance");

        int statusChoice = getValidIntInput("Choose new status: ", 1, 4);
        Table.TableStatus newStatus = Table.TableStatus.values()[statusChoice - 1];

        for (Table table : tables) {
            if (table.getTableNumber().equals(tableNumber)) {
                dbManager.updateTableStatus(table.getTableID(), newStatus,
                        currentManager.getManagerID());
                System.out.println("Status updated successfully!");
                return;
            }
        }
        System.out.println("Table not found!");
    }




}