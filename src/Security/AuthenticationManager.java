package Security;

import Database.*;
import Models.*;
import Exceptions.*;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import static Utils.ValidationUtils.validateCustomerCredentials;
import static Utils.ValidationUtils.validateManagerCredentials;

public class AuthenticationManager {
    private final DatabaseManager dbManager;
    private static final int MAX_LOGIN_ATTEMPTS = 3;

    public AuthenticationManager() {
        this.dbManager = DatabaseManager.getInstance();
    }

    // Authentication
    public Manager authenticateManager(String username, String password) throws AuthenticationException {
        try {
            if (isAccountLocked(username, "manager")) {
                throw new AuthenticationException("Account is locked. Please contact admin.");
            }

            String storedSalt = dbManager.getManagerSalt(username);
            if (storedSalt == null) {
                throw new AuthenticationException("Invalid username or password");
            }

            String hashedPassword = PasswordEncryptor.hashPassword(password, storedSalt);
            if (validateManagerCredentials(username, hashedPassword)) {
                resetLoginAttempts(username, "manager");
                int managerId = dbManager.getManagerIdByUsername(username);
                return dbManager.getManagerById(managerId);
            }

            incrementLoginAttempts(username, "manager");
            throw new AuthenticationException("Invalid username or password");
        } catch (DatabaseConnectionException e) {
            throw new AuthenticationException("Authentication failed: " + e.getMessage());
        }
    }
    public Customer authenticateCustomer(String username, String password) throws AuthenticationException {
        try {
            if (username == null || password == null || username.trim().isEmpty() || password.trim().isEmpty()) {
                throw new AuthenticationException("Username and password are required");
            }

            if (isAccountLocked(username, "customer")) {
                throw new AuthenticationException("Account is locked. Please contact support.");
            }

            String storedSalt = dbManager.getCustomerSalt(username);
            if (storedSalt == null) {
                incrementLoginAttempts(username, "customer");
                throw new AuthenticationException("Invalid username or password");
            }

            String hashedPassword = PasswordEncryptor.hashPassword(password, storedSalt);
            if (!validateCustomerCredentials(username, hashedPassword)) {
                incrementLoginAttempts(username, "customer");
                throw new AuthenticationException("Invalid username or password");
            }

            resetLoginAttempts(username, "customer");
            int customerId = dbManager.getCustomerIdByUsername(username);
            Customer customer = dbManager.getCustomerById(customerId);

            if (customer == null) {
                throw new AuthenticationException("Customer account not found");
            }

            return customer;

        } catch (DatabaseConnectionException e) {
            throw new AuthenticationException("Authentication failed: Database error");
        }
    }

    // Registration Methods
    public void registerCustomer(Customer customer, String username, String password) throws AuthenticationException {
        try {
            if (dbManager.getCustomerSalt(username) != null) {
                throw new AuthenticationException("Username already exists");
            }

            String salt = PasswordEncryptor.generateSalt();
            String hashedPassword = PasswordEncryptor.hashPassword(password, salt);
            dbManager.createCustomer(customer, username, hashedPassword, salt);
        } catch (DatabaseConnectionException e) {
            throw new AuthenticationException("Registration failed: " + e.getMessage());
        }
    }
    public void registerManager(Manager manager, String username, String password) throws AuthenticationException {

        try {
            if (dbManager.getManagerSalt(username) != null) {
                throw new AuthenticationException("Username already exists");
            }

            String salt = PasswordEncryptor.generateSalt();
            String hashedPassword = PasswordEncryptor.hashPassword(password, salt);
            dbManager.createManager(manager, username, hashedPassword, salt);
        } catch (DatabaseConnectionException e) {
            throw new AuthenticationException("Manager registration failed: " + e.getMessage());
        }
    }

    // Password Management
    public void updateCustomerPassword(int customerId, String oldPassword, String newPassword) throws AuthenticationException {

        try {
            String username = dbManager.getCustomerUsername(customerId);
            String storedSalt = dbManager.getCustomerSalt(username);

            if (!PasswordEncryptor.verifyPassword(oldPassword, storedSalt,
                    dbManager.getCustomerPasswordHash(username))) {
                throw new AuthenticationException("Current password is incorrect");
            }

            String newSalt = PasswordEncryptor.generateSalt();
            String newHashedPassword = PasswordEncryptor.hashPassword(newPassword, newSalt);
            dbManager.updateCustomerPassword(customerId, newHashedPassword, newSalt);
        } catch (DatabaseConnectionException e) {
            throw new AuthenticationException("Password update failed: " + e.getMessage());
        }
    }
    public void updateManagerPassword(int managerId, String oldPassword, String newPassword) throws AuthenticationException {

        try {
            String username = dbManager.getManagerUsername(managerId);
            String storedSalt = dbManager.getManagerSalt(username);

            if (!PasswordEncryptor.verifyPassword(oldPassword, storedSalt,
                    dbManager.getManagerPasswordHash(username))) {
                throw new AuthenticationException("Current password is incorrect");
            }

            String newSalt = PasswordEncryptor.generateSalt();
            String newHashedPassword = PasswordEncryptor.hashPassword(newPassword, newSalt);
            dbManager.updateManagerPassword(managerId, newHashedPassword, newSalt);
        } catch (DatabaseConnectionException e) {
            throw new AuthenticationException("Password update failed: " + e.getMessage());
        }
    }


    // Attempts
    private void incrementLoginAttempts(String username, String userType) throws DatabaseConnectionException {
        String sql;
        if (userType.equals("customer")) {
            sql = "UPDATE UserCredentials SET LoginAttempts = LoginAttempts + 1 " +
                    "WHERE Username = ?";
        } else {
            sql = "UPDATE ManagerCredentials SET LoginAttempts = LoginAttempts + 1 " +
                    "WHERE Username = ?";
        }

        if (!dbManager.executeUpdate(sql, username)) {
            throw new DatabaseConnectionException("Failed to update login attempts");
        }

        // Check if max attempts reached and lock if necessary
        int currentAttempts = userType.equals("customer") ?
                dbManager.getCustomerLoginAttempts(username) :
                dbManager.getManagerLoginAttempts(username);

        if (currentAttempts >= MAX_LOGIN_ATTEMPTS) {
            lockAccount(username, userType);
        }
    }
    private void resetLoginAttempts(String username, String userType) throws DatabaseConnectionException {
        String sql;
        if (userType.equals("customer")) {
            sql = "UPDATE UserCredentials SET LoginAttempts = 0 " +
                    "WHERE Username = ?";
        } else {
            sql = "UPDATE ManagerCredentials SET LoginAttempts = 0 " +
                    "WHERE Username = ?";
        }

        if (!dbManager.executeUpdate(sql, username)) {
            throw new DatabaseConnectionException("Failed to reset login attempts");
        }
    }
    private void lockAccount(String username, String userType) throws DatabaseConnectionException {
        String sql;
        if (userType.equals("customer")) {
            sql = "UPDATE UserCredentials SET AccountLocked = 1 " +
                    "WHERE Username = ?";
        } else {
            sql = "UPDATE ManagerCredentials SET AccountLocked = 1 " +
                    "WHERE Username = ?";
        }

        if (!dbManager.executeUpdate(sql, username)) {
            throw new DatabaseConnectionException("Failed to lock account");
        }
    }
    private boolean isAccountLocked(String username, String userType) throws DatabaseConnectionException {
        try {
            String sql;
            if (userType.equals("customer")) {
                sql = "SELECT AccountLocked FROM UserCredentials WHERE Username = ?";
            } else {
                sql = "SELECT AccountLocked FROM ManagerCredentials WHERE Username = ?";
            }

            try (PreparedStatement pstmt = dbManager.prepareStatement(sql)) {
                pstmt.setString(1, username);
                try (ResultSet rs = pstmt.executeQuery()) {
                    return rs.next() && rs.getBoolean("AccountLocked");
                }
            }
        } catch (SQLException e) {
            throw new DatabaseConnectionException("Failed to check account lock status", e);
        }
    }

}