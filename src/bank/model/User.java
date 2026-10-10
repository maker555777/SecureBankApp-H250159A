package bank.model;

import bank.exception.InvalidRecordException;

/** User login details and failed-attempt state. */
public final class User implements Storable {

    public static final int MAX_FAILED_ATTEMPTS = 3;

    private final String username;
    private final String salt;          // Base64
    private final String passwordHash;  // Base64
    private int failedAttempts;
    private boolean locked;

    public User(String username, String salt, String passwordHash) {
        this(username, salt, passwordHash, 0, false);
    }

    private User(String username, String salt, String passwordHash,
                 int failedAttempts, boolean locked) {
        this.username = username;
        this.salt = salt;
        this.passwordHash = passwordHash;
        this.failedAttempts = failedAttempts;
        this.locked = locked;
    }

    /** Record a failed login and lock the user at the attempt limit. */
    public void registerFailedAttempt() {
        failedAttempts++;
        if (failedAttempts >= MAX_FAILED_ATTEMPTS) {
            locked = true;
        }
    }

    public void resetFailedAttempts() {
        failedAttempts = 0;
    }

    public static User fromRecord(String line) throws InvalidRecordException {
        String[] p = Records.split(line, 5);
        try {
            int attempts = Integer.parseInt(p[3]);
            if (attempts < 0) {
                throw new InvalidRecordException("Bad user record");
            }
            return new User(p[0], p[1], p[2], attempts, Boolean.parseBoolean(p[4]));
        } catch (NumberFormatException e) {
            throw new InvalidRecordException("Bad user record");
        }
    }

    @Override
    public String toRecord() {
        return String.join(Records.SEPARATOR,
                username, salt, passwordHash,
                Integer.toString(failedAttempts), Boolean.toString(locked));
    }

    public String getUsername() { return username; }
    public String getSalt() { return salt; }
    public String getPasswordHash() { return passwordHash; }
    public int getFailedAttempts() { return failedAttempts; }
    public boolean isLocked() { return locked; }
}
