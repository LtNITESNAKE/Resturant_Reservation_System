package Models;

import java.time.LocalDateTime;

public class Table {


    public enum TableStatus {
        AVAILABLE,
        OCCUPIED,
        RESERVED,
        MAINTENANCE
    }

    private int tableID;
    private int categoryID;
    private String tableNumber;
    private int capacity;
    private TableStatus status;
    private String location;
    private boolean hasWindow;
    private boolean isPrivate;
    private int lastModifiedBy;
    private LocalDateTime lastModifiedDate;
    private String categoryName;
    private int minCapacity;
    private int maxCapacity;
    private String description;

    // Default constructor
    public Table() {
        this.status = TableStatus.AVAILABLE;
        this.lastModifiedDate = LocalDateTime.now();
    }

    // Main constructor
    public Table(String tableNumber, int capacity, int categoryID) {
        this();
        this.tableNumber = tableNumber;
        this.capacity = capacity;
        this.categoryID = categoryID;
        status = TableStatus.AVAILABLE;
    }

    // Getters and Setters
    public int getTableID() {
        return tableID;
    }

    public void setTableID(int tableID) {
        this.tableID = tableID;
    }

    public int getCategoryID() {
        return categoryID;
    }

    public void setCategoryID(int categoryID) {
        this.categoryID = categoryID;
    }

    public String getTableNumber() {
        return tableNumber;
    }

    public void setTableNumber(String tableNumber) {
        this.tableNumber = tableNumber;
    }

    public int getCapacity() {
        return capacity;
    }

    public void setCapacity(int capacity) {
        if (capacity <= 0 || capacity > 20) {
            throw new IllegalArgumentException("Capacity must be between 1 and 20");
        }
        this.capacity = capacity;
    }

    public TableStatus getStatus() {
        return status;
    }

    public void setStatus(TableStatus status) {
        this.status = status;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public boolean isHasWindow() {
        return hasWindow;
    }

    public void setHasWindow(boolean hasWindow) {
        this.hasWindow = hasWindow;
    }

    public boolean isPrivate() {
        return isPrivate;
    }

    public void setPrivate(boolean isPrivate) {
        this.isPrivate = isPrivate;
    }

    public int getLastModifiedBy() {
        return lastModifiedBy;
    }

    public void setLastModifiedBy(int lastModifiedBy) {
        this.lastModifiedBy = lastModifiedBy;
    }

    public LocalDateTime getLastModifiedDate() {
        return lastModifiedDate;
    }

    public void setLastModifiedDate(LocalDateTime lastModifiedDate) {
        this.lastModifiedDate = lastModifiedDate;
    }

    public String getCategoryName() {
        return categoryName;
    }

    public void setCategoryName(String categoryName) {
        this.categoryName = categoryName;
    }

    // Category-related getters and setters
    public int getMinCapacity() {
        return minCapacity;
    }

    public void setMinCapacity(int minCapacity) {
        this.minCapacity = minCapacity;
    }

    public int getMaxCapacity() {
        return maxCapacity;
    }

    public void setMaxCapacity(int maxCapacity) {
        this.maxCapacity = maxCapacity;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    // Check if table can accommodate party size
    public boolean canAccommodate(int partySize) {
        return partySize <= capacity && partySize >= 1;
    }

    // Check if table is available
    public boolean isAvailable() {
        return status == TableStatus.AVAILABLE;
    }

    @Override
    public String toString() {
        return "Table{" +
                "tableID=" + tableID +
                ", tableNumber='" + tableNumber + '\'' +
                ", capacity=" + capacity +
                ", status=" + status +
                ", location='" + location + '\'' +
                ", category='" + categoryName + '\'' +
                '}';
    }
}