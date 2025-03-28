package Security;

import Exceptions.AuthenticationException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;

public class PasswordEncryptor {
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final MessageDigest digest;

    static {
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not found", e);
        }
    }

    private PasswordEncryptor() {}
    public static String generateSalt() {
        byte[] salt = new byte[16];
        RANDOM.nextBytes(salt);
        return bytesToHex(salt);
    }
    public static String hashPassword(String password, String salt) throws AuthenticationException {
        if (password == null || salt == null) {
            throw new AuthenticationException("Password or salt cannot be null");
        }

        String saltedPassword = salt + password;
        byte[] hashedBytes = digest.digest(saltedPassword.getBytes());
        return bytesToHex(hashedBytes);
    }
    public static boolean verifyPassword(String password, String salt, String hashedPassword) throws AuthenticationException {
        String computedHash = hashPassword(password, salt);
        return computedHash.equals(hashedPassword);
    }
    private static String bytesToHex(byte[] bytes) {
        StringBuilder hex = new StringBuilder();
        for (byte b : bytes) {
            hex.append(String.format("%02x", b));
        }
        return hex.toString();
    }


}