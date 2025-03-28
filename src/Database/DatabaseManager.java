package Database;

import Models.*;
import Exceptions.*;
import java.sql.*;
import java.sql.Date;
import java.util.*;
import java.time.*;
import static Utils.ValidationUtils.*;

public class DatabaseManager {
    private static ThreadLocal<Connection> connectionHolder = new ThreadLocal<>();
    private static DatabaseManager instance;


    //            Database Instance


    // Connection Management
    public static Connection getConnection() throws DatabaseConnectionException {
        try {
            Connection conn = connectionHolder.get();
            if (conn == null || conn.isClosed()) {
                conn = DatabaseConfig.getConnection();
                connectionHolder.set(conn);
            }
            return conn;
        } catch (SQLException e) {
            throw new DatabaseConnectionException("Connection failed", e);
        }
    }
    public static DatabaseManager getInstance() {
        if (instance == null) {
            synchronized (DatabaseManager.class) {
                if (instance == null) {
                    instance = new DatabaseManager();
                }
            }
        }
        return instance;
    }

    // Transaction Management
    public void beginTransaction() throws DatabaseConnectionException {
        try {
            getConnection().setAutoCommit(false);
        } catch (SQLException e) {
            throw new DatabaseConnectionException("Failed to start transaction", e);
        }
    }
    public void commitTransaction() throws DatabaseConnectionException {
        Connection conn = null;
        try {
            conn = getConnection();
            conn.commit();
            conn.setAutoCommit(true);
        } catch (SQLException e) {
            throw new DatabaseConnectionException("Commit failed", e);
        }
    }
    public void rollbackTransaction() throws DatabaseConnectionException {
        Connection conn = null;
        try {
            conn = getConnection();
            conn.rollback();
            conn.setAutoCommit(true);
        } catch (SQLException e) {
            throw new DatabaseConnectionException("Rollback failed", e);
        }
    }
    private void rollbackTransaction(Connection conn) {
        if (conn != null) {
            try {
                conn.rollback();
            } catch (SQLException ex) {
                // Log this critical error
                System.err.println("Transaction rollback failed: " + ex.getMessage());
            }
        }
    }

    // Generic Execute Methods
    public static PreparedStatement prepareStatement(String sql) throws DatabaseConnectionException {
        try {
            return getConnection().prepareStatement(sql);
        } catch (SQLException e) {
            throw new DatabaseConnectionException("Failed to prepare statement", e);
        }
    }
    public  static PreparedStatement prepareStatementWithKeys(String sql) throws DatabaseConnectionException {
        try {
            return getConnection().prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
        } catch (SQLException e) {
            throw new DatabaseConnectionException("Failed to prepare statement with keys", e);
        }
    }
    public boolean executeUpdate(String sql, Object... params) throws DatabaseConnectionException {
        try (PreparedStatement pstmt = prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) {
                pstmt.setObject(i + 1, params[i]);
            }
            int rowsAffected = pstmt.executeUpdate();
            return rowsAffected > 0;  // Returns true if at least one row was updated
        } catch (SQLException e) {
            throw new DatabaseConnectionException("Failed to execute update", e);
        }
    }

    // Resource Cleanup
    public void closeResources(AutoCloseable... resources) {
        for (AutoCloseable resource : resources) {
            try {
                if (resource != null) {
                    resource.close();
                }
            } catch (Exception e) {
                // Log error
            }
        }
    }

    // Connection Cleanup
    public void closeConnection() {
        Connection conn = connectionHolder.get();
        if (conn != null) {
            try {
                conn.close();
                connectionHolder.remove();
            } catch (SQLException e) {
                // Log error
            }
        }
    }
    private void closeConnection(Connection conn) {
        if (conn != null) {
            try {
                if (!conn.getAutoCommit()) {
                    conn.setAutoCommit(true);
                }
                conn.close();
            } catch (SQLException e) {
                System.err.println("Connection close failed: " + e.getMessage());
            }
        }
    }
    private void logDatabaseError(SQLException e) {
        System.err.println("Database Error Details:");
        System.err.println("Error Code: " + e.getErrorCode());
        System.err.println("SQL State: " + e.getSQLState());
        System.err.println("Error Message: " + e.getMessage());
    }
    private String getDetailedErrorMessage(SQLException e) {
        // Could be expanded to map specific error codes to more meaningful messages
        return "An unexpected database error occurred. " +
                "Please contact system administrator. " +
                "Error: " + e.getMessage();
    }





    //                                  Customer Operations

    // CURD Operations
    public int createCustomer(Customer customer, String username, String passwordHash, String salt) throws DatabaseConnectionException {
        Connection conn = null;
        try {
            // 1. Validate input
            validateCustomerInput(customer, username, passwordHash, salt);

            // 2. Get connection and start transaction
            conn = getConnection();
            conn.setAutoCommit(false);

            // 3. Insert customer
            String customerSql = "INSERT INTO Customers " +
                    "(FirstName, LastName, Email, PhoneNumber, PreferredCuisine, Allergies) " +
                    "VALUES (?, ?, ?, ?, ?, ?)";
            int customerId;

            try (PreparedStatement pstmt = conn.prepareStatement(customerSql, Statement.RETURN_GENERATED_KEYS)) {
                pstmt.setString(1, trimToMaxLength(customer.getFirstName(), MAX_NAME_LENGTH));
                pstmt.setString(2, trimToMaxLength(customer.getLastName(), MAX_NAME_LENGTH));
                pstmt.setString(3, trimToMaxLength(customer.getEmail(), MAX_EMAIL_LENGTH));
                pstmt.setString(4, trimToMaxLength(customer.getPhoneNumber(), MAX_PHONE_LENGTH));
                pstmt.setString(5, trimToMaxLength(customer.getPreferredCuisine(), MAX_CUISINE_LENGTH));
                pstmt.setString(6, customer.getAllergies());

                int rowsAffected = pstmt.executeUpdate();
                if (rowsAffected == 0) {
                    throw new DatabaseConnectionException("No rows inserted for customer");
                }

                try (ResultSet rs = pstmt.getGeneratedKeys()) {
                    if (!rs.next()) {
                        throw new DatabaseConnectionException("Failed to retrieve generated customer ID");
                    }
                    customerId = rs.getInt(1);
                }
            }

            // 4. Insert credentials with all fields properly initialized
            String credentialsSql = "INSERT INTO UserCredentials " +
                    "(UserID, Username, PasswordHash, Salt, LastLoginDate, LoginAttempts, AccountLocked) " +
                    "VALUES (?, ?, ?, ?, NULL, 0, 0)";
            try (PreparedStatement pstmt = conn.prepareStatement(credentialsSql)) {
                pstmt.setInt(1, customerId);
                pstmt.setString(2, username);
                pstmt.setString(3, passwordHash);
                pstmt.setString(4, salt);

                int credentialRows = pstmt.executeUpdate();
                if (credentialRows == 0) {
                    throw new DatabaseConnectionException("Failed to insert customer credentials");
                }
            }

            // 5. Commit transaction
            conn.commit();
            return customerId;

        } catch (SQLException e) {
            // Rollback transaction on any SQL error
            rollbackTransaction(conn);

            // Log detailed error
            logDatabaseError(e);

            throw new DatabaseConnectionException(
                    "Customer creation failed: " + getDetailedErrorMessage(e),
                    e
            );
        } finally {
            // Ensure connection is always closed and reset to auto-commit
            closeConnection(conn);
        }
    }
    public void updateCustomerPassword(int customerId, String newPasswordHash, String newSalt) throws DatabaseConnectionException {
        String sql = "UPDATE UserCredentials SET PasswordHash = ?, LastLoginDate = CURRENT_TIMESTAMP " +
                "WHERE UserID = ?";
        try (PreparedStatement pstmt = prepareStatement(sql)) {
            pstmt.setString(1, newPasswordHash);
            pstmt.setInt(2, customerId);
            if (pstmt.executeUpdate() == 0) {
                throw new DatabaseConnectionException("Password update failed");
            }
        } catch (SQLException e) {
            throw new DatabaseConnectionException("Failed to update password", e);
        }
    }
    public void updateCustomer(Customer customer) throws DatabaseConnectionException {
        String sql = "UPDATE Customers SET FirstName = ?, LastName = ?, Email = ?, " +
                "PhoneNumber = ?, PreferredCuisine = ?, Allergies = ? WHERE CustomerID = ?";
        try (PreparedStatement pstmt = prepareStatement(sql)) {
            pstmt.setString(1, customer.getFirstName());
            pstmt.setString(2, customer.getLastName());
            pstmt.setString(3, customer.getEmail());
            pstmt.setString(4, customer.getPhoneNumber());
            pstmt.setString(5, customer.getPreferredCuisine());
            pstmt.setString(6, customer.getAllergies());
            pstmt.setInt(7, customer.getCustomerID());

            if (pstmt.executeUpdate() == 0) {
                throw new DatabaseConnectionException("Customer update failed");
            }
        } catch (SQLException e) {
            throw new DatabaseConnectionException("Failed to update customer", e);
        }
    }
    public void createCustomer(Customer customer) throws DatabaseConnectionException {
        String sql = "INSERT INTO Customers (FirstName, LastName, Email, PhoneNumber, " +
                "PreferredCuisine, Allergies) VALUES (?, ?, ?, ?, ?, ?)";

        PreparedStatement pstmt = null;
        ResultSet rs = null;

        try {
            pstmt = prepareStatementWithKeys(sql);
            pstmt.setString(1, customer.getFirstName());
            pstmt.setString(2, customer.getLastName());
            pstmt.setString(3, customer.getEmail());
            pstmt.setString(4, customer.getPhoneNumber());
            pstmt.setString(5, customer.getPreferredCuisine());
            pstmt.setString(6, customer.getAllergies());

            int affectedRows = pstmt.executeUpdate();
            if (affectedRows == 0) {
                throw new DatabaseConnectionException("Creating customer failed");
            }

            rs = pstmt.getGeneratedKeys();
            if (rs.next()) {
                customer.setCustomerID(rs.getInt(1));
            }
        } catch (SQLException e) {
            throw new DatabaseConnectionException("Error creating customer", e);
        } finally {
            closeResources(rs, pstmt);
        }
    }
    public void deleteCustomer(int customerId) throws DatabaseConnectionException {
        Connection conn = getConnection();
        try {
            conn.setAutoCommit(false);

            // Delete credentials first due to foreign key constraint
            String credentialsSql = "DELETE FROM UserCredentials WHERE UserID = ?";
            try (PreparedStatement pstmt = conn.prepareStatement(credentialsSql)) {
                pstmt.setInt(1, customerId);
                pstmt.executeUpdate();
            }

            // Delete customer record
            String customerSql = "DELETE FROM Customers WHERE CustomerID = ?";
            try (PreparedStatement pstmt = conn.prepareStatement(customerSql)) {
                pstmt.setInt(1, customerId);
                if (pstmt.executeUpdate() == 0) {
                    throw new DatabaseConnectionException("Customer not found");
                }
            }

            conn.commit();
        } catch (SQLException e) {
            try {
                conn.rollback();
            } catch (SQLException ex) {
                throw new DatabaseConnectionException("Rollback failed", ex);
            }
            throw new DatabaseConnectionException("Failed to delete customer", e);
        } finally {
            try {
                conn.setAutoCommit(true);
            } catch (SQLException e) {
                throw new DatabaseConnectionException("Failed to reset auto-commit", e);
            }
        }
    }
    public void updateCustomerLoginAttempts(String username, int attempts) throws DatabaseConnectionException {
        String sql = "UPDATE UserCredentials SET LoginAttempts = ? WHERE Username = ? AND UserType = 'CUSTOMER'";
        executeUpdate(sql, attempts, username);
    }

    // Search Operations
    public List<Customer> getAllCustomers() throws DatabaseConnectionException {
        String sql = "SELECT * FROM Customers ORDER BY LastName, FirstName";
        List<Customer> customers = new ArrayList<>();

        try (PreparedStatement pstmt = prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) {
            while (rs.next()) {
                customers.add(mapResultSetToCustomer(rs));
            }
            return customers;
        } catch (SQLException e) {
            throw new DatabaseConnectionException("Failed to retrieve customers", e);
        }
    }
    public int getCustomerIdByUsername(String username) throws DatabaseConnectionException {
        String sql = "SELECT UserID FROM UserCredentials WHERE Username = ?";
        try (PreparedStatement pstmt = prepareStatement(sql)) {
            pstmt.setString(1, username);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("UserID");
                }
                throw new DatabaseConnectionException("Failed to retrieve customer ID");
            }
        } catch (SQLException e) {
            throw new DatabaseConnectionException("Failed to retrieve customer ID", e);
        }
    }
    public String getCustomerSalt(String username) throws DatabaseConnectionException {
        String sql = "SELECT Salt FROM UserCredentials WHERE Username = ?";
        try (PreparedStatement pstmt = prepareStatement(sql)) {
            pstmt.setString(1, username);
            try (ResultSet rs = pstmt.executeQuery()) {
                return rs.next() ? rs.getString("Salt") : null;
            }
        } catch (SQLException e) {
            throw new DatabaseConnectionException("Failed to retrieve customer salt", e);
        }
    }
    public Customer getCustomerById(int customerId) throws DatabaseConnectionException {
        String sql = "SELECT * FROM Customers WHERE CustomerID = ?";
        try (PreparedStatement pstmt = prepareStatement(sql)) {
            pstmt.setInt(1, customerId);
            // Execute query after setting parameters
            try (ResultSet rs = pstmt.executeQuery()) {
                return rs.next() ? mapResultSetToCustomer(rs) : null;
            }
        } catch (SQLException e) {
            throw new DatabaseConnectionException("Failed to retrieve customer", e);
        }
    }
    public Customer getCustomerByEmail(String email) throws DatabaseConnectionException {
        String sql = "SELECT * FROM Customers WHERE Email = ?";
        try (PreparedStatement pstmt = prepareStatement(sql)) {
            pstmt.setString(1, email);
            try (ResultSet rs = pstmt.executeQuery()) {
                return rs.next() ? mapResultSetToCustomer(rs) : null;
            }
        } catch (SQLException e) {
            throw new DatabaseConnectionException("Failed to retrieve customer by email", e);
        }
    }
    public List<Customer> searchCustomersByName(String searchTerm) throws DatabaseConnectionException {
        String sql = "SELECT * FROM Customers WHERE FirstName LIKE ? OR LastName LIKE ?";
        List<Customer> customers = new ArrayList<>();

        try (PreparedStatement pstmt = prepareStatement(sql)) {
            String searchPattern = "%" + searchTerm + "%";
            pstmt.setString(1, searchPattern);
            pstmt.setString(2, searchPattern);

            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    customers.add(mapResultSetToCustomer(rs));
                }
            }
            return customers;
        } catch (SQLException e) {
            throw new DatabaseConnectionException("Failed to search customers", e);
        }
    }
    public int getCustomerLoginAttempts(String username) throws DatabaseConnectionException {
        String sql = "SELECT LoginAttempts FROM UserCredentials WHERE Username = ? AND UserType = 'CUSTOMER'";
        try (PreparedStatement pstmt = prepareStatement(sql)) {
            pstmt.setString(1, username);
            try (ResultSet rs = pstmt.executeQuery()) {
                return rs.next() ? rs.getInt("LoginAttempts") : 0;
            }
        } catch (SQLException e) {
            throw new DatabaseConnectionException("Failed to get login attempts", e);
        }
    }
    public void lockCustomerAccount(String username) throws DatabaseConnectionException {
        String sql = "UPDATE UserCredentials SET AccountLocked = 1 WHERE Username = ? AND UserType = 'CUSTOMER'";
        executeUpdate(sql, username);
    }
    public String getCustomerPasswordHash(String username) throws DatabaseConnectionException {
        String sql = "SELECT PasswordHash FROM UserCredentials WHERE Username = ?";
        try (PreparedStatement pstmt = prepareStatement(sql)) {
            pstmt.setString(1, username);
            try (ResultSet rs = pstmt.executeQuery()) {
                return rs.next() ? rs.getString("PasswordHash") : null;
            }
        } catch (SQLException e) {
            throw new DatabaseConnectionException("Failed to get password hash", e);
        }
    }
    public boolean isCustomerAccountLocked(String username) throws DatabaseConnectionException {
        String sql = "SELECT AccountLocked FROM UserCredentials WHERE Username = ?";
        try (PreparedStatement pstmt = prepareStatement(sql)) {
            pstmt.setString(1, username);
            try (ResultSet rs = pstmt.executeQuery()) {
                return rs.next() && rs.getBoolean("AccountLocked");
            }
        } catch (SQLException e) {
            throw new DatabaseConnectionException("Failed to check account lock status", e);
        }
    }
    public String getCustomerUsername(int customerId) throws DatabaseConnectionException {
        String sql = "SELECT Username FROM UserCredentials WHERE UserID = ?";
        try (PreparedStatement pstmt = prepareStatement(sql)) {
            pstmt.setInt(1, customerId);
            try (ResultSet rs = pstmt.executeQuery()) {
                return rs.next() ? rs.getString("Username") : null;
            }
        } catch (SQLException e) {
            throw new DatabaseConnectionException("Failed to get username", e);
        }
    }

    // Statistics Operations
    public void updateCustomerVisits(int customerId) throws DatabaseConnectionException {
        String sql = "UPDATE Customers SET TotalVisits = TotalVisits + 1, " +
                "LastVisitDate = CURRENT_TIMESTAMP WHERE CustomerID = ?";
        try (PreparedStatement pstmt = prepareStatement(sql)) {
            pstmt.setInt(1, customerId);
            if (pstmt.executeUpdate() == 0) {
                throw new DatabaseConnectionException("Failed to update visit count");
            }
        } catch (SQLException e) {
            throw new DatabaseConnectionException("Failed to update customer visits", e);
        }
    }
    private Customer mapResultSetToCustomer(ResultSet rs) throws SQLException {
        Customer customer = new Customer();
        customer.setCustomerID(rs.getInt("CustomerID"));
        customer.setFirstName(rs.getString("FirstName"));
        customer.setLastName(rs.getString("LastName"));
        customer.setEmail(rs.getString("Email"));
        customer.setPhoneNumber(rs.getString("PhoneNumber"));
        customer.setRegistrationDate(rs.getTimestamp("RegistrationDate").toLocalDateTime());

        Timestamp lastVisit = rs.getTimestamp("LastVisitDate");
        if (lastVisit != null) {
            customer.setLastVisitDate(lastVisit.toLocalDateTime());
        }

        customer.setTotalVisits(rs.getInt("TotalVisits"));
        customer.setPreferredCuisine(rs.getString("PreferredCuisine"));
        customer.setAllergies(rs.getString("Allergies"));
        return customer;
    }





    //                                      Manager Operations

    // CURD Operations
    public int createManager(Manager manager, String username, String passwordHash, String salt) throws DatabaseConnectionException {
        Connection conn = null;
        try {
            // 1. Validate input before any database operation
            validateManagerInput(manager, username, passwordHash, salt);

            // 2. Establish database connection
            conn = getConnection();
            conn.setAutoCommit(false); // Begin transaction

            // 3. Insert manager record
            int managerId = insertManagerRecord(conn, manager);

            // 4. Insert manager credentials
            insertManagerCredentials(conn, managerId, username, passwordHash, salt);

            // 5. Commit transaction
            conn.commit();
            return managerId;

        } catch (SQLException e) {
            // Rollback transaction on any SQL error
            rollbackTransaction(conn);

            // Log detailed error for system administrators
            logDatabaseError(e);

            // Throw a meaningful exception to caller
            throw new DatabaseConnectionException(
                    "Manager creation failed: " + getDetailedErrorMessage(e),
                    e
            );
        } finally {
            // Ensure connection is always closed and reset to auto-commit
            closeConnection(conn);
        }
    }
    private int insertManagerRecord(Connection conn, Manager manager) throws SQLException, DatabaseConnectionException {
        String managerSql = "INSERT INTO Managers " +
                "(FirstName, LastName, Email, PhoneNumber, IsActive, CreatedDate) " +
                "VALUES (?, ?, ?, ?, ?, SYSDATETIME())";

        try (PreparedStatement pstmt = conn.prepareStatement(
                managerSql, Statement.RETURN_GENERATED_KEYS)) {

            // Safely set parameters with length trimming
            pstmt.setString(1, trimToMaxLength(manager.getFirstName(), MAX_NAME_LENGTH));
            pstmt.setString(2, trimToMaxLength(manager.getLastName(), MAX_NAME_LENGTH));
            pstmt.setString(3, trimToMaxLength(manager.getEmail(), MAX_EMAIL_LENGTH));
            pstmt.setString(4, trimToMaxLength(manager.getPhoneNumber(), MAX_PHONE_LENGTH));
            pstmt.setBoolean(5, manager.isActive());

            int rowsAffected = pstmt.executeUpdate();
            if (rowsAffected == 0) {
                throw new DatabaseConnectionException("No rows inserted for manager");
            }

            // Retrieve and return generated manager ID
            try (ResultSet rs = pstmt.getGeneratedKeys()) {
                if (!rs.next()) {
                    throw new DatabaseConnectionException("Failed to retrieve generated manager ID");
                }
                return rs.getInt(1);
            }
        }
    }
    private void insertManagerCredentials(Connection conn, int managerId, String username, String passwordHash, String salt) throws SQLException, DatabaseConnectionException {
        String credentialsSql = "INSERT INTO ManagerCredentials " +
                "(ManagerID, Username, PasswordHash, Salt, CreatedDate) " +
                "VALUES (?, ?, ?, ?, SYSDATETIME())";

        try (PreparedStatement pstmt = conn.prepareStatement(credentialsSql)) {
            pstmt.setInt(1, managerId);
            pstmt.setString(2, username);
            pstmt.setString(3, passwordHash);
            pstmt.setString(4, salt);

            int credentialRows = pstmt.executeUpdate();
            if (credentialRows == 0) {
                throw new DatabaseConnectionException("Failed to insert manager credentials");
            }
        }
    }
    public void updateManager(Manager manager) throws DatabaseConnectionException {
        String sql = "UPDATE Managers SET FirstName = ?, LastName = ?, Email = ?, " +
                "PhoneNumber = ?, IsActive = ? WHERE ManagerID = ?";
        try (PreparedStatement pstmt = prepareStatement(sql)) {
            pstmt.setString(1, manager.getFirstName());
            pstmt.setString(2, manager.getLastName());
            pstmt.setString(3, manager.getEmail());
            pstmt.setString(4, manager.getPhoneNumber());
            pstmt.setBoolean(5, manager.isActive());
            pstmt.setInt(6, manager.getManagerID());

            if (pstmt.executeUpdate() == 0) {
                throw new DatabaseConnectionException("Manager update failed");
            }
        } catch (SQLException e) {
            throw new DatabaseConnectionException("Failed to update manager", e);
        }
    }
    public void updateManagerLoginAttempts(String username, int attempts) throws DatabaseConnectionException {
        String sql = "UPDATE UserCredentials SET LoginAttempts = ? WHERE Username = ? AND UserType = 'MANAGER'";
        executeUpdate(sql, attempts, username);
    }
    public void updateManagerPassword(int managerId, String hashedPassword, String salt) throws DatabaseConnectionException {
        String sql = "UPDATE ManagerCredentials SET PasswordHash = ?, Salt = ?, " +
                "LastModified = CURRENT_TIMESTAMP WHERE ManagerID = ?";
        try (PreparedStatement pstmt = prepareStatement(sql)) {
            pstmt.setString(1, hashedPassword);
            pstmt.setString(2, salt);
            pstmt.setInt(3, managerId);

            if (pstmt.executeUpdate() == 0) {
                throw new DatabaseConnectionException("Failed to update manager password");
            }
        } catch (SQLException e) {
            throw new DatabaseConnectionException("Error updating manager password", e);
        }
    }

    // Search Operations
    public String getManagerSalt(String username) throws DatabaseConnectionException {
        String sql = "SELECT Salt FROM ManagerCredentials WHERE Username = ?";
        try (PreparedStatement pstmt = prepareStatement(sql)) {
            pstmt.setString(1, username);
            try (ResultSet rs = pstmt.executeQuery()) {
                return rs.next() ? rs.getString("Salt") : null;
            }
        } catch (SQLException e) {
            throw new DatabaseConnectionException("Failed to retrieve manager salt", e);
        }
    }
    public int getManagerIdByUsername(String username) throws DatabaseConnectionException {
        String sql = "SELECT ManagerID FROM ManagerCredentials WHERE Username = ?";
        try (PreparedStatement pstmt = prepareStatement(sql)) {
            pstmt.setString(1, username);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("ManagerID");
                }
                throw new DatabaseConnectionException("Failed to retrieve manager ID");
            }
        } catch (SQLException e) {
            throw new DatabaseConnectionException("Failed to retrieve manager ID", e);
        }
    }
    public Manager getManagerById(int managerId) throws DatabaseConnectionException {
        String sql = "SELECT * FROM Managers WHERE ManagerID = ?";
        try (PreparedStatement pstmt = prepareStatement(sql)) {
            pstmt.setInt(1, managerId);
            try (ResultSet rs = pstmt.executeQuery()) {
                return rs.next() ? mapResultSetToManager(rs) : null;
            }
        } catch (SQLException e) {
            throw new DatabaseConnectionException("Failed to retrieve manager", e);
        }
    }
    public List<Manager> getAllManagers() throws DatabaseConnectionException {
        String sql = "SELECT * FROM Managers ORDER BY LastName, FirstName";
        List<Manager> managers = new ArrayList<>();

        try (PreparedStatement pstmt = prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) {
            while (rs.next()) {
                managers.add(mapResultSetToManager(rs));
            }
            return managers;
        } catch (SQLException e) {
            throw new DatabaseConnectionException("Failed to retrieve managers", e);
        }
    }
    public Manager getManagerByEmail(String email) throws DatabaseConnectionException {
        String sql = "SELECT * FROM Managers WHERE Email = ?";
        try (PreparedStatement pstmt = prepareStatement(sql)) {
            pstmt.setString(1, email);
            try (ResultSet rs = pstmt.executeQuery()) {
                return rs.next() ? mapResultSetToManager(rs) : null;
            }
        } catch (SQLException e) {
            throw new DatabaseConnectionException("Failed to retrieve manager by email", e);
        }
    }
    public int getManagerLoginAttempts(String username) throws DatabaseConnectionException {
        String sql = "SELECT LoginAttempts FROM UserCredentials WHERE Username = ? AND UserType = 'MANAGER'";
        try (PreparedStatement pstmt = prepareStatement(sql)) {
            pstmt.setString(1, username);
            try (ResultSet rs = pstmt.executeQuery()) {
                return rs.next() ? rs.getInt("LoginAttempts") : 0;
            }
        } catch (SQLException e) {
            throw new DatabaseConnectionException("Failed to get login attempts", e);
        }
    }
    public void lockManagerAccount(String username) throws DatabaseConnectionException {
        String sql = "UPDATE UserCredentials SET AccountLocked = 1 WHERE Username = ? AND UserType = 'MANAGER'";
        executeUpdate(sql, username);
    }
    public String getManagerPasswordHash(String username) throws DatabaseConnectionException {
        String sql = "SELECT PasswordHash FROM ManagerCredentials WHERE Username = ?";
        try (PreparedStatement pstmt = prepareStatement(sql)) {
            pstmt.setString(1, username);
            try (ResultSet rs = pstmt.executeQuery()) {
                return rs.next() ? rs.getString("PasswordHash") : null;
            }
        } catch (SQLException e) {
            throw new DatabaseConnectionException("Failed to get manager password hash", e);
        }
    }
    public boolean isManagerAccountLocked(String username) throws DatabaseConnectionException {
        String sql = "SELECT AccountLocked FROM ManagerCredentials WHERE Username = ?";
        try (PreparedStatement pstmt = prepareStatement(sql)) {
            pstmt.setString(1, username);
            try (ResultSet rs = pstmt.executeQuery()) {
                return rs.next() && rs.getBoolean("AccountLocked");
            }
        } catch (SQLException e) {
            throw new DatabaseConnectionException("Failed to check manager account lock status", e);
        }
    }
    public String getManagerUsername(int managerId) throws DatabaseConnectionException {
        String sql = "SELECT Username FROM ManagerCredentials WHERE ManagerID = ?";
        try (PreparedStatement pstmt = prepareStatement(sql)) {
            pstmt.setInt(1, managerId);
            try (ResultSet rs = pstmt.executeQuery()) {
                return rs.next() ? rs.getString("Username") : null;
            }
        } catch (SQLException e) {
            throw new DatabaseConnectionException("Failed to get manager username", e);
        }
    }

    // Statistics Operations
    private Manager mapResultSetToManager(ResultSet rs) throws SQLException {
        Manager manager = new Manager();
        manager.setManagerID(rs.getInt("ManagerID"));
        manager.setFirstName(rs.getString("FirstName"));
        manager.setLastName(rs.getString("LastName"));
        manager.setEmail(rs.getString("Email"));
        manager.setPhoneNumber(rs.getString("PhoneNumber"));
        manager.setActive(rs.getBoolean("IsActive"));
        manager.setCreatedDate(rs.getTimestamp("CreatedDate").toLocalDateTime());

        Timestamp lastModified = rs.getTimestamp("LastModifiedDate");
        if (lastModified != null) {
            manager.setLastModifiedDate(lastModified.toLocalDateTime());
        }

        return manager;
    }




    //                                  Table Operations

    // CURD Operations
    public int createTable(Table table) throws DatabaseConnectionException {
        // First check if table number already exists
        if (isTableNumberExists(table.getTableNumber())) {
            throw new DatabaseConnectionException("Table number already exists");
        }

        String sql = "INSERT INTO RestaurantTables (CategoryID, TableNumber, Capacity, Status, " +
                "Location, HasWindow, IsPrivate, LastModifiedBy, LastModifiedDate) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, SYSDATETIME())";

        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            pstmt.setInt(1, table.getCategoryID());
            pstmt.setString(2, table.getTableNumber());
            pstmt.setInt(3, table.getCapacity());
            pstmt.setString(4, String.valueOf(table.getStatus()));
            pstmt.setString(5, table.getLocation());
            pstmt.setBoolean(6, table.isHasWindow());
            pstmt.setBoolean(7, table.isPrivate());
            pstmt.setInt(8, table.getLastModifiedBy());

            int affectedRows = pstmt.executeUpdate();

            if (affectedRows == 0) {
                throw new DatabaseConnectionException("Creating table failed, no rows affected.");
            }

            try (ResultSet generatedKeys = pstmt.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    return generatedKeys.getInt(1);
                } else {
                    throw new DatabaseConnectionException("Creating table failed, no ID obtained.");
                }
            }
        } catch (SQLException e) {
            throw new DatabaseConnectionException("Table creation failed: " + e.getMessage());
        }
    }

    // Validations Methods

    // Search Operations
    private boolean isTableNumberExists(String tableNumber) throws DatabaseConnectionException {
        String sql = "SELECT COUNT(*) FROM RestaurantTables WHERE TableNumber = ?";
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, tableNumber);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1) > 0;
                }
            }
            return false;
        } catch (SQLException e) {
            throw new DatabaseConnectionException("Error checking table number existence: " + e.getMessage());
        }
    }
    public Table getTableById(int tableId) throws DatabaseConnectionException {
        String sql = "SELECT t.*, tc.CategoryName, tc.MinCapacity, tc.MaxCapacity, tc.Description " +
                "FROM RestaurantTables t " +
                "JOIN TableCategories tc ON t.CategoryID = tc.CategoryID " +
                "WHERE t.TableID = ?";

        try (PreparedStatement pstmt = prepareStatement(sql)) {
            pstmt.setInt(1, tableId);
            try (ResultSet rs = pstmt.executeQuery()) {
                return rs.next() ? mapResultSetToTable(rs) : null;
            }
        } catch (SQLException e) {
            throw new DatabaseConnectionException("Failed to retrieve table", e);
        }
    }
    public List<Table> getAvailableTables(int partySize, LocalDateTime dateTime) throws DatabaseConnectionException {
        String sql = """
        SELECT t.*, tc.CategoryName, tc.MinCapacity, tc.MaxCapacity, tc.Description 
        FROM RestaurantTables t 
        JOIN TableCategories tc ON t.CategoryID = tc.CategoryID 
        WHERE t.Capacity >= ? 
        AND t.Status = 'Available' 
        AND t.TableID NOT IN (
            SELECT TableID 
            FROM Reservations 
            WHERE CAST(ReservationDate AS DATE) = CAST(? AS DATE) 
            AND CAST(ReservationTime AS TIME) = CAST(? AS TIME)
            AND Status IN ('Pending', 'Confirmed')
        )
        ORDER BY t.Capacity ASC""";

        List<Table> tables = new ArrayList<>();

        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            // Set parameters with explicit casting
            pstmt.setInt(1, partySize);
            pstmt.setTimestamp(2, Timestamp.valueOf(dateTime)); // Full datetime value for consistent casting
            pstmt.setTimestamp(3, Timestamp.valueOf(dateTime)); // Full datetime value for consistent casting

            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    tables.add(mapResultSetToTable(rs));
                }
            }
            return tables;
        } catch (SQLException e) {
            throw new DatabaseConnectionException("Failed to retrieve available tables: " + e.getMessage());
        }
    }
    public void updateTableStatus(int tableId, Table.TableStatus status, int modifiedBy) throws DatabaseConnectionException {
        String sql = "UPDATE RestaurantTables SET Status = ?, LastModifiedBy = ?, " +
                "LastModifiedDate = CURRENT_TIMESTAMP WHERE TableID = ?";

        try (PreparedStatement pstmt = prepareStatement(sql)) {
            pstmt.setString(1, status.toString());
            pstmt.setInt(2, modifiedBy);
            pstmt.setInt(3, tableId);

            if (pstmt.executeUpdate() == 0) {
                throw new DatabaseConnectionException("Table status update failed");
            }
        } catch (SQLException e) {
            throw new DatabaseConnectionException("Failed to update table status", e);
        }
    }
    public List<Table> getAvailableTables(int partySize, LocalDate date, LocalTime time) throws DatabaseConnectionException {
        String sql = """
        SELECT t.*, tc.CategoryName, tc.MinCapacity, tc.MaxCapacity, tc.Description 
        FROM RestaurantTables t 
        JOIN TableCategories tc ON t.CategoryID = tc.CategoryID 
        WHERE t.Capacity >= ? 
        AND t.Status = 'Available' 
        AND t.TableID NOT IN (
            SELECT TableID 
            FROM Reservations 
            WHERE ReservationDate = ?
            AND ReservationTime = ?
            AND Status IN ('Pending', 'Confirmed')
        )
        ORDER BY t.Capacity ASC""";

        List<Table> tables = new ArrayList<>();

        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, partySize);
            pstmt.setDate(2, Date.valueOf(date));
            pstmt.setTime(3, Time.valueOf(time));

            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    tables.add(mapResultSetToTable(rs));
                }
            }
            return tables;
        } catch (SQLException e) {
            throw new DatabaseConnectionException("Failed to retrieve available tables: " + e.getMessage());
        }
    }
    public List<Table> getAllTables() throws DatabaseConnectionException {
        String sql = "SELECT t.*, tc.CategoryName, tc.MinCapacity, tc.MaxCapacity, tc.Description " +
                "FROM RestaurantTables t " +
                "JOIN TableCategories tc ON t.CategoryID = tc.CategoryID " +
                "ORDER BY t.TableNumber";

        List<Table> tables = new ArrayList<>();
        try (PreparedStatement pstmt = prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) {
            while (rs.next()) {
                tables.add(mapResultSetToTable(rs));
            }
            return tables;
        } catch (SQLException e) {
            throw new DatabaseConnectionException("Failed to retrieve tables", e);
        }
    }
    public boolean checkTableAvailability(int tableId, LocalDate date, LocalTime time) throws DatabaseConnectionException {
        String sql = "SELECT COUNT(*) FROM Reservations WHERE TableID = ? " +
                "AND ReservationDate = ? " +
                "AND CAST(CAST(ReservationDate AS DATETIME) + ' ' + CAST(ReservationTime AS VARCHAR(8)) AS DATETIME) = ? " +  // Combine DATE and TIME into DATETIME
                "AND Status IN ('PENDING', 'CONFIRMED', 'RESERVED')";

        try (PreparedStatement pstmt = prepareStatement(sql)) {
            pstmt.setInt(1, tableId);
            pstmt.setDate(2, Date.valueOf(date));  // Pass the date properly
            pstmt.setTimestamp(3, Timestamp.valueOf(date.atTime(time)));  // Pass LocalDateTime as a Timestamp

            System.out.println("Executing SQL Query: " + sql); // Debugging log

            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    int count = rs.getInt(1);
                    System.out.println("Table availability check result: " + count); // Debugging log
                    return count == 0;  // If no reservations are found, table is available
                } else {
                    System.out.println("No results from query."); // Debugging log
                    return false;
                }
            }
        } catch (SQLException e) {
            System.err.println("SQL Error: " + e.getMessage());
            e.printStackTrace();
            throw new DatabaseConnectionException("Failed to check table availability", e);
        }
    }

    // Statistics Operations
    private Table mapResultSetToTable(ResultSet rs) throws SQLException {
        Table table = new Table();
        table.setTableID(rs.getInt("TableID"));
        table.setCategoryID(rs.getInt("CategoryID"));
        table.setTableNumber(rs.getString("TableNumber"));
        table.setCapacity(rs.getInt("Capacity"));

        String statusStr = rs.getString("Status");
        if (statusStr != null) {
            try {
                table.setStatus(Table.TableStatus.valueOf(statusStr.toUpperCase()));
            } catch (IllegalArgumentException e) {
                table.setStatus(Table.TableStatus.AVAILABLE); // Default status
            }
        }

        table.setLocation(rs.getString("Location"));
        table.setHasWindow(rs.getBoolean("HasWindow"));
        table.setPrivate(rs.getBoolean("IsPrivate"));
        table.setLastModifiedBy(rs.getInt("LastModifiedBy"));

        Timestamp modifiedDate = rs.getTimestamp("LastModifiedDate");
        if (modifiedDate != null) {
            table.setLastModifiedDate(modifiedDate.toLocalDateTime());
        }

        // Category details with null checks
        table.setCategoryName(rs.getString("CategoryName"));
        table.setMinCapacity(rs.getInt("MinCapacity"));
        table.setMaxCapacity(rs.getInt("MaxCapacity"));
        table.setDescription(rs.getString("Description"));

        return table;
    }




    //                                 Reservation Operations

    // CURD Operations
    public int createReservation(Reservation reservation) throws DatabaseConnectionException {
        // Validate reservation time is in the future
        if (LocalDateTime.of(reservation.getReservationDate(), reservation.getReservationTime())
                .isBefore(LocalDateTime.now())) {
            throw new DatabaseConnectionException("Cannot create reservation for past date/time");
        }

        String sql = """
        INSERT INTO Reservations 
        (CustomerID, TableID, ReservationDate, ReservationTime, PartySize, 
         Status, SpecialRequests, EstimatedDuration) 
        VALUES (?, ?, ?, ?, ?, ?, ?, ?)""";

        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            pstmt.setInt(1, reservation.getCustomerID());
            pstmt.setInt(2, reservation.getTableID());
            pstmt.setDate(3, Date.valueOf(reservation.getReservationDate()));
            pstmt.setTime(4, Time.valueOf(reservation.getReservationTime()));
            pstmt.setInt(5, reservation.getPartySize());
            pstmt.setString(6, "Confirmed"); // Default status for new reservations
            pstmt.setString(7, reservation.getSpecialRequests());
            pstmt.setInt(8, reservation.getEstimatedDuration());

            int affectedRows = pstmt.executeUpdate();
            if (affectedRows == 0) {
                throw new DatabaseConnectionException("Failed to create reservation");
            }

            try (ResultSet rs = pstmt.getGeneratedKeys()) {
                if (rs.next()) {
                    // Update table status after successful reservation
                    updateTableStatus(reservation.getTableID(), Table.TableStatus.RESERVED,
                            reservation.getCustomerID());
                    return rs.getInt(1);
                }
                throw new DatabaseConnectionException("Failed to get reservation ID");
            }
        } catch (SQLException e) {
            throw new DatabaseConnectionException("Reservation creation failed: " + e.getMessage());
        }
    }
    public void updateReservationStatus(int reservationId, Reservation.ReservationStatus status) throws DatabaseConnectionException {
        String sql = "UPDATE Reservations SET Status = ? WHERE ReservationID = ?";
        try (PreparedStatement pstmt = prepareStatement(sql)) {
            pstmt.setString(1, status.toString());
            pstmt.setInt(2, reservationId);

            if (pstmt.executeUpdate() == 0) {
                throw new DatabaseConnectionException("Reservation status update failed");
            }

            // Update table status if reservation is cancelled or completed
            if (status == Reservation.ReservationStatus.CANCELLED ||
                    status == Reservation.ReservationStatus.COMPLETED) {
                Reservation reservation = getReservationById(reservationId);
                updateTableStatus(reservation.getTableID(), Table.TableStatus.AVAILABLE, 0);
            }
        } catch (SQLException e) {
            throw new DatabaseConnectionException("Failed to update reservation status", e);
        }
    }

    // Validations Methods

    // Search Operations
    private boolean hasOverlappingReservations(int tableId, LocalDate date, LocalTime time, int estimatedDuration) throws DatabaseConnectionException {
        String sql = """
        SELECT COUNT(*) FROM Reservations 
        WHERE TableID = ? 
        AND ReservationDate = ? 
        AND Status IN ('Pending', 'Confirmed')
        AND (
            (ReservationTime BETWEEN ? AND DATEADD(MINUTE, ?, ?))
            OR (DATEADD(MINUTE, EstimatedDuration, ReservationTime) 
                BETWEEN ? AND DATEADD(MINUTE, ?, ?))
        )""";

        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            Time startTime = Time.valueOf(time);
            pstmt.setInt(1, tableId);
            pstmt.setDate(2, Date.valueOf(date));
            pstmt.setTime(3, startTime);
            pstmt.setInt(4, estimatedDuration);
            pstmt.setTime(5, startTime);
            pstmt.setTime(6, startTime);
            pstmt.setInt(7, estimatedDuration);
            pstmt.setTime(8, startTime);

            try (ResultSet rs = pstmt.executeQuery()) {
                return rs.next() && rs.getInt(1) > 0;
            }
        } catch (SQLException e) {
            throw new DatabaseConnectionException("Failed to check overlapping reservations");
        }
    }
    public Reservation getReservationById(int reservationId) throws DatabaseConnectionException {
        String sql = "SELECT * FROM Reservations WHERE ReservationID = ?";
        try (PreparedStatement pstmt = prepareStatement(sql)) {
            pstmt.setInt(1, reservationId);
            try (ResultSet rs = pstmt.executeQuery()) {
                return rs.next() ? mapResultSetToReservation(rs) : null;
            }
        } catch (SQLException e) {
            throw new DatabaseConnectionException("Failed to retrieve reservation", e);
        }
    }
    public List<Reservation> getReservationsByCustomer(int customerId) throws DatabaseConnectionException {
        String sql = "SELECT * FROM Reservations WHERE CustomerID = ? ORDER BY ReservationDate, ReservationTime";
        List<Reservation> reservations = new ArrayList<>();

        try (PreparedStatement pstmt = prepareStatement(sql)) {
            pstmt.setInt(1, customerId);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    reservations.add(mapResultSetToReservation(rs));
                }
            }
            return reservations;
        } catch (SQLException e) {
            throw new DatabaseConnectionException("Failed to retrieve customer reservations", e);
        }
    }
    public List<Reservation> getActiveReservations() throws DatabaseConnectionException {
        String sql = "SELECT * FROM Reservations WHERE Status IN ('PENDING', 'CONFIRMED') " +
                "AND ReservationDate >= CAST(GETDATE() AS DATE) ORDER BY ReservationDate, ReservationTime";
        List<Reservation> reservations = new ArrayList<>();

        try (PreparedStatement pstmt = prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) {
            while (rs.next()) {
                reservations.add(mapResultSetToReservation(rs));
            }
            return reservations;
        } catch (SQLException e) {
            System.err.println("SQL Exception: " + e.getMessage());  // Debugging print
            throw new DatabaseConnectionException("Failed to retrieve active reservations", e);
        }
    }
    public List<Reservation> getReservationsByDate(LocalDate date) throws InvalidStatusException {
        // Input validation
        if (date == null) {
            throw new IllegalArgumentException("Reservation date cannot be null");
        }

        // List to store reservations for the specified date
        List<Reservation> reservationsOnDate = new ArrayList<>();

        try (Connection conn = getConnection()) {
            // SQL query matching the ActiveReservations view structure
            String sql = "SELECT " +
                    "ReservationID, CustomerID, TableID, " +
                    "ReservationDate, ReservationTime, PartySize, " +
                    "Status, SpecialRequests, EstimatedDuration " +
                    "FROM Reservations " +
                    "WHERE ReservationDate = ?";

            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                // Set the date parameter
                pstmt.setDate(1, Date.valueOf(date));

                // Execute the query
                try (ResultSet rs = pstmt.executeQuery()) {
                    // Process each row in the result set
                    while (rs.next()) {
                        Reservation reservation = new Reservation();

                        // Populate reservation object from database result
                        reservation.setReservationID(rs.getInt("ReservationID"));
                        reservation.setCustomerID(rs.getInt("CustomerID"));
                        reservation.setTableID(rs.getInt("TableID"));

                        // Convert java.sql.Date to LocalDate
                        reservation.setReservationDate(rs.getDate("ReservationDate").toLocalDate());

                        // Convert java.sql.Time to LocalTime
                        reservation.setReservationTime(rs.getTime("ReservationTime").toLocalTime());

                        reservation.setPartySize(rs.getInt("PartySize"));

                        // Get and normalize status
                        String statusStr = rs.getString("Status").trim().toUpperCase();  // Normalize the status string
                        try {
                            reservation.setStatus(Reservation.ReservationStatus.valueOf(statusStr));
                        } catch (IllegalArgumentException e) {
                            // Throw exception instead of logging
                            throw new InvalidStatusException("Invalid status value found in the database: " + statusStr);
                        }

                        // Handle potential null values
                        String specialRequests = rs.getString("SpecialRequests");
                        if (specialRequests != null) {
                            reservation.setSpecialRequests(specialRequests);
                        }

                        reservation.setEstimatedDuration(rs.getInt("EstimatedDuration"));

                        // Add to the list of reservations
                        reservationsOnDate.add(reservation);
                    }
                }
            }
        } catch (SQLException | DatabaseConnectionException e) {
            // Rethrow the exception to be handled by the calling code
            throw new RuntimeException("Error retrieving reservations for date: " + date, e);
        }

        return reservationsOnDate;
    }
    public int getTableReservationCount(int tableId, LocalDate date) throws DatabaseConnectionException {
        String sql = "SELECT COUNT(*) AS ReservationCount " +
                "FROM Reservations " +
                "WHERE TableID = ? AND ReservationDate = ? " +
                "AND Status NOT IN ('Cancelled', 'Expired')";

        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            // Set parameters
            pstmt.setInt(1, tableId);
            pstmt.setDate(2, Date.valueOf(date));

            // Execute query
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("ReservationCount");
                }
                return 0;
            }
        } catch (SQLException e) {
            // Log the error and throw a custom exception
            throw new DatabaseConnectionException(
                    "Failed to retrieve reservation count for table " + tableId, e
            );
        }
    }

    // Statistics Operations
    private Reservation mapResultSetToReservation(ResultSet rs) throws SQLException {
        Reservation reservation = new Reservation();
        reservation.setReservationID(rs.getInt("ReservationID"));
        reservation.setCustomerID(rs.getInt("CustomerID"));
        reservation.setTableID(rs.getInt("TableID"));
        reservation.setReservationDate(rs.getDate("ReservationDate").toLocalDate());
        reservation.setReservationTime(rs.getTime("ReservationTime").toLocalTime());
        reservation.setPartySize(rs.getInt("PartySize"));

        // Normalize status to avoid issues with casing and spaces
        String statusStr = rs.getString("Status").trim().toUpperCase();  // Trim and normalize
        try {
            reservation.setStatus(Reservation.ReservationStatus.valueOf(statusStr));
        } catch (IllegalArgumentException e) {
            System.err.println("Invalid status value found in the database: " + statusStr);
            reservation.setStatus(Reservation.ReservationStatus.PENDING);  // Default status
        }

        reservation.setSpecialRequests(rs.getString("SpecialRequests"));
        reservation.setEstimatedDuration(rs.getInt("EstimatedDuration"));
        return reservation;
    }




    //                                    Waitlist Operations

    //  CURD Operations
    public int createWaitlistEntry(Waitlist waitlist) throws DatabaseConnectionException {
        String sql = "INSERT INTO Waitlist (CustomerID, RequestedDate, RequestedTime, " +
                "PartySize, Status, QueuePosition, WaitTime) VALUES (?, ?, ?, ?, ?, ?, ?)";

        try (PreparedStatement pstmt = prepareStatementWithKeys(sql)) {
            pstmt.setInt(1, waitlist.getCustomerID());
            pstmt.setDate(2, Date.valueOf(waitlist.getRequestedDate()));
            pstmt.setTime(3, Time.valueOf(waitlist.getRequestedTime()));
            pstmt.setInt(4, waitlist.getPartySize());
            pstmt.setString(5, waitlist.getStatus().toString());
            pstmt.setInt(6, getNextQueuePosition());
            pstmt.setInt(7, waitlist.getWaitTime());

            if (pstmt.executeUpdate() == 0) {
                throw new DatabaseConnectionException("Failed to create waitlist entry");
            }

            try (ResultSet rs = pstmt.getGeneratedKeys()) {
                return rs.next() ? rs.getInt(1) : -1;
            }
        } catch (SQLException e) {
            throw new DatabaseConnectionException("Waitlist entry creation failed", e);
        }
    }
    public void updateWaitlistStatus(int waitlistId, Waitlist.WaitlistStatus status) throws DatabaseConnectionException {
        String sql = "UPDATE Waitlist SET Status = ? WHERE WaitlistID = ?";

        try (PreparedStatement pstmt = prepareStatement(sql)) {
            pstmt.setString(1, status.toString());
            pstmt.setInt(2, waitlistId);

            if (pstmt.executeUpdate() == 0) {
                throw new DatabaseConnectionException("Waitlist status update failed");
            }

            if (status != Waitlist.WaitlistStatus.ACTIVE) {
                reorderQueuePositions();
            }
        } catch (SQLException e) {
            throw new DatabaseConnectionException("Failed to update waitlist status", e);
        }
    }
    public void updateWaitTime(int waitlistId, int newWaitTime) throws DatabaseConnectionException {
        String sql = "UPDATE Waitlist SET WaitTime = ? WHERE WaitlistID = ?";
        try (PreparedStatement pstmt = prepareStatement(sql)) {
            pstmt.setInt(1, newWaitTime);
            pstmt.setInt(2, waitlistId);

            if (pstmt.executeUpdate() == 0) {
                throw new DatabaseConnectionException("Wait time update failed");
            }
        } catch (SQLException e) {
            throw new DatabaseConnectionException("Failed to update wait time", e);
        }
    }
    public void removeFromWaitlist(int waitlistId) throws DatabaseConnectionException {
        String sql = "DELETE FROM Waitlist WHERE WaitlistID = ?";
        try (PreparedStatement pstmt = prepareStatement(sql)) {
            pstmt.setInt(1, waitlistId);

            if (pstmt.executeUpdate() == 0) {
                throw new DatabaseConnectionException("Failed to remove from waitlist");
            }
            reorderQueuePositions();
        } catch (SQLException e) {
            throw new DatabaseConnectionException("Failed to remove from waitlist", e);
        }
    }

    // Validations Methods

    // Search Operations
    private int getNextQueuePosition() throws SQLException, DatabaseConnectionException {
        String sql = "SELECT MAX(QueuePosition) + 1 FROM Waitlist WHERE Status = 'ACTIVE'";
        try (PreparedStatement pstmt = prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) {
            return rs.next() ? Math.max(1, rs.getInt(1)) : 1;
        }
    }
    public List<Waitlist> getActiveWaitlist() throws DatabaseConnectionException {
        String sql = "SELECT w.*, c.FirstName, c.LastName FROM Waitlist w " +
                "JOIN Customers c ON w.CustomerID = c.CustomerID " +
                "WHERE w.Status = 'ACTIVE' ORDER BY w.QueuePosition";

        List<Waitlist> waitlist = new ArrayList<>();
        try (PreparedStatement pstmt = prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) {
            while (rs.next()) {
                waitlist.add(mapResultSetToWaitlist(rs));
            }
            return waitlist;
        } catch (SQLException e) {
            throw new DatabaseConnectionException("Failed to retrieve active waitlist", e);
        }
    }
    private void reorderQueuePositions() throws SQLException {
        String sql = "UPDATE Waitlist SET QueuePosition = newPosition.rownum " +
                "FROM (SELECT WaitlistID, ROW_NUMBER() OVER (ORDER BY QueuePosition) AS rownum " +
                "FROM Waitlist WHERE Status = 'ACTIVE') AS newPosition " +
                "WHERE Waitlist.WaitlistID = newPosition.WaitlistID";

        try (PreparedStatement pstmt = prepareStatement(sql)) {
            pstmt.executeUpdate();
        } catch (DatabaseConnectionException e) {
            throw new RuntimeException(e);
        }
    }
    public int getHighestQueuePosition(LocalDate date, LocalTime time) throws DatabaseConnectionException {
        String query = "SELECT ISNULL(MAX(QueuePosition), 1) " +
                "FROM Waitlist " +
                "WHERE RequestedDate = ? " +
                "AND DATEPART(HOUR, RequestedTime) = DATEPART(HOUR, ?) " +
                "AND DATEPART(MINUTE, RequestedTime) = DATEPART(MINUTE, ?)";

        try (Connection connection = getConnection();
             PreparedStatement stmt = connection.prepareStatement(query)) {

            // Set parameters for PreparedStatement
            stmt.setDate(1, java.sql.Date.valueOf(date));
            stmt.setTime(2, java.sql.Time.valueOf(time));
            stmt.setTime(3, java.sql.Time.valueOf(time));

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);  // Returns the next available QueuePosition
                }
                return 1;  // Default to 1 if no rows are found
            }
        } catch (SQLException e) {
            throw new DatabaseConnectionException("Database error while fetching highest queue position", e);
        }
    }

    // Statistics Operations
    private Waitlist mapResultSetToWaitlist(ResultSet rs) throws SQLException {
        Waitlist waitlist = new Waitlist();
        waitlist.setWaitlistID(rs.getInt("WaitlistID"));
        waitlist.setCustomerID(rs.getInt("CustomerID"));
        waitlist.setRequestedDate(rs.getDate("RequestedDate").toLocalDate());
        waitlist.setRequestedTime(rs.getTime("RequestedTime").toLocalTime());
        waitlist.setPartySize(rs.getInt("PartySize"));
        waitlist.setStatus(Waitlist.WaitlistStatus.valueOf(rs.getString("Status")));
        waitlist.setQueuePosition(rs.getInt("QueuePosition"));
        waitlist.setWaitTime(rs.getInt("WaitTime"));
        return waitlist;
    }


}

