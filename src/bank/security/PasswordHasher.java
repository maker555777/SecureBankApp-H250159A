package bank.security;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/** Creates salted PBKDF2 hashes and verifies login attempts. Callers clear password arrays. */
public final class PasswordHasher {

    private static final String ALGORITHM = "PBKDF2WithHmacSHA256";
    private static final int ITERATIONS = 600_000;   // OWASP guidance for PBKDF2-SHA256
    private static final int KEY_BITS = 256;
    private static final int SALT_BYTES = 16;

    private static final SecureRandom RANDOM = new SecureRandom();

    /** Salt and hash encoded for storage in users.txt. */
    public record HashedPassword(String salt, String hash) { }

    private PasswordHasher() { }

    /** Hash a new password with a fresh salt. */
    public static HashedPassword hash(char[] password) {
        byte[] salt = new byte[SALT_BYTES];
        RANDOM.nextBytes(salt);
        byte[] hash = derive(password, salt);
        return new HashedPassword(encode(salt), encode(hash));
    }

    /** Verify a password using the stored salt. */
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

    /** Do the hashing work for a login attempt with an unknown username. */
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
