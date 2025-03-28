package Models;

import java.time.LocalDate;
import java.time.LocalTime;

public class Reservation {
    public enum ReservationStatus {
        PENDING,
        CONFIRMED,
        COMPLETED,
        CANCELLED
    }

    private int reservationID;
    private int customerID;
    private int tableID;
    private LocalDate reservationDate;
    private LocalTime reservationTime;
    private int partySize;
    private ReservationStatus status;
    private String specialRequests;
    private int estimatedDuration; // in minutes

    // Default constructor
    public Reservation() {
        this.status = ReservationStatus.PENDING;
        this.estimatedDuration = 120; // Default 2 hours
    }

    // Main constructor
    public Reservation(int customerID, int tableID, LocalDate date, 
                      LocalTime time, int partySize) {
        this();
        this.customerID = customerID;
        this.tableID = tableID;
        this.reservationDate = date;
        this.reservationTime = time;
        setPartySize(partySize);
    }

    // Getters and Setters
    public int getReservationID() {
        return reservationID;
    }

    public void setReservationID(int reservationID) {
        this.reservationID = reservationID;
    }

    public int getCustomerID() {
        return customerID;
    }

    public void setCustomerID(int customerID) {
        this.customerID = customerID;
    }

    public int getTableID() {
        return tableID;
    }

    public void setTableID(int tableID) {
        this.tableID = tableID;
    }

    public LocalDate getReservationDate() {
        return reservationDate;
    }

    public void setReservationDate(LocalDate reservationDate) {
        this.reservationDate = reservationDate;
    }

    public LocalTime getReservationTime() {
        return reservationTime;
    }

    public void setReservationTime(LocalTime reservationTime) {
        this.reservationTime = reservationTime;
    }

    public int getPartySize() {
        return partySize;
    }

    public void setPartySize(int partySize) {
        if (partySize <= 0 || partySize > 20) {
            throw new IllegalArgumentException("Party size must be between 1 and 20");
        }
        this.partySize = partySize;
    }

    public ReservationStatus getStatus() {
        return status;
    }

    public void setStatus(ReservationStatus status) {
        this.status = status;
    }

    public String getSpecialRequests() {
        return specialRequests;
    }

    public void setSpecialRequests(String specialRequests) {
        this.specialRequests = specialRequests;
    }

    public int getEstimatedDuration() {
        return estimatedDuration;
    }

    public void setEstimatedDuration(int estimatedDuration) {
        this.estimatedDuration = estimatedDuration;
    }

    // Utility methods
    public boolean isActive() {
        return status == ReservationStatus.PENDING || 
               status == ReservationStatus.CONFIRMED;
    }

    public boolean canBeCancelled() {
        return status != ReservationStatus.COMPLETED && 
               status != ReservationStatus.CANCELLED;
    }

    @Override
    public String toString() {
        return "Reservation{" +
                "reservationID=" + reservationID +
                ", customerID=" + customerID +
                ", tableID=" + tableID +
                ", date=" + reservationDate +
                ", time=" + reservationTime +
                ", partySize=" + partySize +
                ", status=" + status +
                '}';
    }
}