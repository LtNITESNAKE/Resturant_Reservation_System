package Exceptions;

public class InvalidStatusException extends Exception {
    public InvalidStatusException(String message) {
        super(message);
    }
}