package bank.security;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.LocalDateTime;

/**
 * Append-only record of security-relevant events, so you can answer
 * "who did what, and when" after the fact.
 *
 * What it must never contain: passwords, hashes, or full account numbers.
 * Callers pass the masked number (Account.getMaskedNumber()).
 */
public final class AuditLogger {

    public enum Event {
        USER_REGISTERED,
        LOGIN_SUCCESS,
        LOGIN_FAILURE,
        ACCOUNT_LOCKED,
        LOGOUT,
        SESSION_TIMEOUT,
        ACCOUNT_CREATED,
        DEPOSIT,
        WITHDRAWAL,
        TRANSACTION_REJECTED
    }

    private final Path logFile;

    public AuditLogger(Path logFile) {
        this.logFile = logFile;
    }

    public void log(Event event, String username, String details) {
        String line = LocalDateTime.now().withNano(0)
                + " | " + event
                + " | user=" + clean(username)
                + " | " + clean(details)
                + System.lineSeparator();
        try {
            Path parent = logFile.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(logFile, line, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            restrictToOwner(logFile);
        } catch (IOException e) {
            // A broken log must not crash the bank, but it must not be silent either.
            System.err.println("Warning: could not write to the audit log.");
        }
    }

    /** Owner-only access (like chmod 600) where the file system supports it; skipped on Windows. */
    private static void restrictToOwner(Path file) {
        try {
            Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("rw-------"));
        } catch (UnsupportedOperationException | IOException ignored) {
            // not a POSIX file system, nothing to do
        }
    }

    /**
     * Stops "log injection": if someone typed a username containing a line
     * break, they could forge an extra fake log entry. We flatten anything
     * that could start a new line or fake a column.
     */
    private static String clean(String text) {
        if (text == null || text.isEmpty()) {
            return "-";
        }
        String flat = text.replaceAll("[\\r\\n|]", "_");
        return flat.length() > 80 ? flat.substring(0, 80) : flat;
    }
}
