package bank.security;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Turns a password into a salted PBKDF2 hash and checks passwords against one.
 *
 * Why not plain SHA-256? It is built to be fast, which helps an attacker
 * guess billions of passwords a second. PBKDF2 repeats the work hundreds of
 * thousands of times, so every guess is expensive. The random salt means two
 * users with the same password still get different hashes.
 *
 * Passwords are handled as char[] (not String) so they can be wiped from
 * memory straight after use.
 */
public final class PasswordHasher {

    private static final String ALGORITHM = "PBKDF2WithHmacSHA256";
    private static final int ITERATIONS = 600_000;   // OWASP guidance for PBKDF2-SHA256
    private static final int KEY_BITS = 256;
    private static final int SALT_BYTES = 16;

    private static final SecureRandom RANDOM = new SecureRandom();

    /** Both values are Base64 text so they fit on one line of users.txt. */
    public record HashedPassword(String salt, String hash) { }

    private PasswordHasher() { }

    /** New password: generates a fresh random salt. */
    public static HashedPassword hash(char[] password) {
        byte[] salt = new byte[SALT_BYTES];
        RANDOM.nextBytes(salt);
        byte[] hash = derive(password, salt);
        return new HashedPassword(encode(salt), encode(hash));
    }

    /**
     * Login attempt: re-hash with the stored salt and compare.
     * MessageDigest.isEqual takes the same time whether the first byte or the
     * last byte differs, so timing can't be used to guess the hash.
     */
    public static boolean matches(char[] password, String saltBase64, String expectedHashBase64) {
        try {
            byte[] salt = Base64.getDecoder().decode(saltBase64);
            byte[] expected = Base64.getDecoder().decode(expectedHashBase64);
            byte[] actual = derive(password, salt);
            return MessageDigest.isEqual(expected, actual);
        } catch (IllegalArgumentException e) {
            return false; // stored values were not valid Base64
        }
    }

    /**
     * Used when the username does not exist. Doing the same amount of work
     * means "unknown user" and "wrong password" take equally long to answer,
     * so an attacker can't use response time to find real usernames.
     */
    public static void spendSameTimeAsRealCheck(char[] password) {
        derive(password, new byte[SALT_BYTES]);
    }

    private static byte[] derive(char[] password, byte[] salt) {
        PBEKeySpec spec = new PBEKeySpec(password, salt, ITERATIONS, KEY_BITS);
        try {
            return SecretKeyFactory.getInstance(ALGORITHM).generateSecret(spec).getEncoded();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Password hashing is unavailable on this system");
        } finally {
            spec.clearPassword();
        }
    }

    private static String encode(byte[] data) {
        return Base64.getEncoder().encodeToString(data);
    }
}
