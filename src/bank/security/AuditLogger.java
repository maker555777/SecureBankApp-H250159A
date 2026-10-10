package bank.security;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.LocalDateTime;

/** Writes security and transaction events to an append-only log. */
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
            // Logging failures should not interrupt a banking operation.
            System.err.println("Warning: could not write to the audit log.");
        }
    }

    /** Restrict access to the owner where POSIX permissions are available. */
    private static void restrictToOwner(Path file) {
        try {
            Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("rw-------"));
        } catch (UnsupportedOperationException | IOException ignored) {
            // not a POSIX file system, nothing to do
        }
    }

    /** Keep user-provided values on one line and within the expected columns. */
    private static String clean(String text) {
        if (text == null || text.isEmpty()) {
            return "-";
        }
        String flat = text.replaceAll("[\\r\\n|]", "_");
        return flat.length() > 80 ? flat.substring(0, 80) : flat;
    }
}
