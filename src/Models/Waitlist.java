package Models;

import java.time.LocalDate;
import java.time.LocalTime;

public class Waitlist {


    public enum WaitlistStatus {
        ACTIVE,
        SEATED,
        EXPIRED
    }

    private int waitlistID;
    private int customerID;
    private LocalDate requestedDate;
    private LocalTime requestedTime;
    private int partySize;
    private WaitlistStatus status;
    private int queuePosition;
    private int waitTime; // in minutes

    // Default constructor
    public Waitlist() {
        this.status = WaitlistStatus.ACTIVE;
    }

    // Main constructor
    public Waitlist(int customerID, LocalDate requestedDate, 
                   LocalTime requestedTime, int partySize, int queuePosition) {
        this();
        this.customerID = customerID;
        this.requestedDate = requestedDate;
        this.requestedTime = requestedTime;
        setPartySize(partySize);
        this.queuePosition = queuePosition;
        waitTime = 120;
    }

    // Getters and Setters
    public int getWaitlistID() {
        return waitlistID;
    }

    public void setWaitlistID(int waitlistID) {
        this.waitlistID = waitlistID;
    }

    public int getCustomerID() {
        return customerID;
    }

    public void setCustomerID(int customerID) {
        this.customerID = customerID;
    }

    public LocalDate getRequestedDate() {
        return requestedDate;
    }

    public void setRequestedDate(LocalDate requestedDate) {
        this.requestedDate = requestedDate;
    }

    public LocalTime getRequestedTime() {
        return requestedTime;
    }

    public void setRequestedTime(LocalTime requestedTime) {
        this.requestedTime = requestedTime;
    }

    public int getPartySize() {
        return partySize;
    }

    public void setPartySize(int partySize) {
        if (partySize <= 0) {
            throw new IllegalArgumentException("Party size must be greater than 0");
        }
        this.partySize = partySize;
    }

    public WaitlistStatus getStatus() {
        return status;
    }

    public void setStatus(WaitlistStatus status) {
        this.status = status;
    }

    public int getQueuePosition() {
        return queuePosition;
    }

    public void setQueuePosition(int queuePosition) {
        if (queuePosition <= 0) {
            throw new IllegalArgumentException("Queue position must be greater than 0");
        }
        this.queuePosition = queuePosition;
    }

    public int getWaitTime() {
        return waitTime;
    }

    public void setWaitTime(int waitTime) {
        if (waitTime < 0) {
            throw new IllegalArgumentException("Wait time cannot be negative");
        }
        this.waitTime = waitTime;
    }

    // Utility methods
    public boolean isActive() {
        return status == WaitlistStatus.ACTIVE;
    }

    public void markAsSeated() {
        this.status = WaitlistStatus.SEATED;
    }

    public void markAsExpired() {
        this.status = WaitlistStatus.EXPIRED;
    }

    @Override
    public String toString() {
        return "Waitlist{" +
                "waitlistID=" + waitlistID +
                ", customerID=" + customerID +
                ", requestedDate=" + requestedDate +
                ", requestedTime=" + requestedTime +
                ", partySize=" + partySize +
                ", status=" + status +
                ", queuePosition=" + queuePosition +
                ", waitTime=" + waitTime +
                '}';
    }
}