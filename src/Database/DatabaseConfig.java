package Database;

import com.microsoft.sqlserver.jdbc.SQLServerDriver;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class DatabaseConfig {
    private static final String DB_URL = "jdbc:sqlserver://DESKTOP-C3O7KLG\\SQLEXPRESS;databaseName=RestaurantReservationSystem;integratedSecurity=true;encrypt=false;";

    public static Connection getConnection() throws SQLException {
        DriverManager.registerDriver(new SQLServerDriver());
        return DriverManager.getConnection(DB_URL);
    }

}