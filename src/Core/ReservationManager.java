package Core;

import Database.*;
import Models.*;
import Exceptions.*;
import java.util.*;
import java.time.*;

public class ReservationManager {
    private final DatabaseManager dbManager;

    public ReservationManager() {
        this.dbManager = DatabaseManager.getInstance();
    }

    // Reservation Management
    public void createReservation(Reservation reservation) throws DatabaseConnectionException, ReservationException {
        if (!isTableAvailable(reservation.getTableID(),
                reservation.getReservationDate(),
                reservation.getReservationTime())) {
            throw new ReservationException("Table not available for selected time");
        }
        dbManager.createReservation(reservation);
    }
    public List<Reservation> getCustomerReservations(int customerId) throws DatabaseConnectionException {
        return dbManager.getReservationsByCustomer(customerId);
    }
    public List<Reservation> getActiveReservations() throws DatabaseConnectionException {
        return dbManager.getActiveReservations();
    }
    public void cancelReservation(int reservationId) throws DatabaseConnectionException {
        dbManager.updateReservationStatus(reservationId, Reservation.ReservationStatus.CANCELLED);
    }

    // Waitlist Management
    public void addToWaitlist(Waitlist entry) throws DatabaseConnectionException {
        dbManager.createWaitlistEntry(entry);
    }
    public void removeFromWaitlist(int waitlistId) throws DatabaseConnectionException {
        dbManager.updateWaitlistStatus(waitlistId, Waitlist.WaitlistStatus.EXPIRED);
    }
    public void seatFromWaitlist(int waitlistId) throws DatabaseConnectionException {
        dbManager.updateWaitlistStatus(waitlistId, Waitlist.WaitlistStatus.SEATED);
    }
    public List<Waitlist> getCustomerWaitlistEntries(int customerId) throws DatabaseConnectionException {
        return dbManager.getActiveWaitlist();
    }

    // Table Availability
    public List<Table> getAvailableTables(int partySize, LocalDate date, LocalTime time) throws DatabaseConnectionException {
        LocalDateTime dateTime = LocalDateTime.of(date, time);
        return dbManager.getAvailableTables(partySize, dateTime);
    }
    private boolean isTableAvailable(int tableId, LocalDate date, LocalTime time) throws DatabaseConnectionException {
        return dbManager.checkTableAvailability(tableId, date, time);
    }

    // Report Generation
    public void generateDailyReservationReport(LocalDate date) throws DatabaseConnectionException, InvalidStatusException {
        List<Reservation> reservations = dbManager.getReservationsByDate(date);
        System.out.println("\n=== Daily Reservation Report ===");
        System.out.println("Date: " + date);
        System.out.println("Total Reservations: " + reservations.size());

        for (Reservation res : reservations) {
            System.out.printf("Time: %s | Table: %d | Party: %d | Status: %s%n",
                    res.getReservationTime(), res.getTableID(),
                    res.getPartySize(), res.getStatus());
        }
    }
    public void generateTableUtilizationReport(LocalDate date) throws DatabaseConnectionException {
        List<Table> tables = dbManager.getAllTables();
        System.out.println("\n=== Table Utilization Report ===");
        System.out.println("Date: " + date);

        for (Table table : tables) {
            int reservationCount = dbManager.getTableReservationCount(table.getTableID(), date);
            System.out.printf("Table %s: %d reservations%n",
                    table.getTableNumber(), reservationCount);
        }
    }
    public void updateReservationStatus(int reservationId, Reservation.ReservationStatus newStatus) throws DatabaseConnectionException {
        try {
            if (reservationId <= 0) {
                throw new IllegalArgumentException("Invalid reservation ID");
            }
            if (newStatus == null) {
                throw new IllegalArgumentException("Reservation status cannot be null");
            }
            dbManager.updateReservationStatus(reservationId, newStatus);
        } catch (DatabaseConnectionException e) {
            System.err.println("Failed to update reservation status: " + e.getMessage());
            throw e;
        }
    }
    public void generateWaitlistReport(LocalDate date) throws DatabaseConnectionException {
        List<Waitlist> waitlist = dbManager.getActiveWaitlist();
        System.out.println("\n=== Waitlist Report ===");
        System.out.println("Date: " + date);
        System.out.println("Total Waiting: " + waitlist.size());

        int totalWaitTime = 0;
        for (Waitlist entry : waitlist) {
            totalWaitTime += entry.getWaitTime();
            System.out.printf("Position: %d | Party Size: %d | Wait Time: %d mins%n",
                    entry.getQueuePosition(), entry.getPartySize(), entry.getWaitTime());
        }

        if (!waitlist.isEmpty()) {
            System.out.printf("Average Wait Time: %d minutes%n", totalWaitTime / waitlist.size());
        }
    }


}