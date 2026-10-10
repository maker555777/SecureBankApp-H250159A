package bank.service;

import bank.exception.AuthenticationException;
import bank.exception.BankException;
import bank.exception.InvalidInputException;
import bank.security.AuditLogger;
import bank.security.AuditLogger.Event;
import bank.security.InputValidator;
import bank.security.PasswordHasher;
import bank.security.PasswordHasher.HashedPassword;
import bank.model.User;
import bank.storage.DataStore;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Registration and login.
 *
 * Passwords arrive as char[] and are NOT wiped here: the caller (the console
 * UI) owns the array and clears it as soon as this method returns.
 */
public final class AuthService {

    private static final String GENERIC_FAILURE = "Invalid username or password.";
    private static final String LOCKED_MESSAGE =
            "This account is locked after too many failed attempts. Please contact the bank.";

    private final DataStore store;
    private final AuditLogger audit;
    private final Map<String, User> users;

    public AuthService(DataStore store, AuditLogger audit) throws BankException {
        this.store = store;
        this.audit = audit;
        this.users = new LinkedHashMap<>(store.loadUsers());
    }

    /** Creates a login. Only the salt and hash are stored, never the password. */
    public User register(String usernameInput, char[] password) throws BankException {
        String username = InputValidator.username(usernameInput);
        InputValidator.newPassword(password, username);
        if (users.containsKey(username)) {
            throw new InvalidInputException("That username is already taken.");
        }

        HashedPassword hashed = PasswordHasher.hash(password);
        User user = new User(username, hashed.salt(), hashed.hash());

        // Save first, remember second: if saving fails, memory and disk still agree.
        List<User> updated = new ArrayList<>(users.values());
        updated.add(user);
        store.saveUsers(updated);
        users.put(username, user);

        audit.log(Event.USER_REGISTERED, username, "new user registered");
        return user;
    }

    /**
     * Checks a login. Things done on purpose here:
     *  - an unknown user and a wrong password give the same message and take
     *    about the same time (so usernames can't be guessed from the response);
     *  - the failure count is saved to disk, so restarting the app can't reset it;
     *  - a locked account is rejected before the password is even looked at.
     *
     * Trade-off: telling a user "locked" does confirm that the username exists.
     * We accept that so a genuine customer knows why they can't get in.
     */
    public User login(String usernameInput, char[] password) throws BankException {
        String username;
        try {
            InputValidator.loginPassword(password);
            username = InputValidator.username(usernameInput);
        } catch (InvalidInputException e) {
            PasswordHasher.spendSameTimeAsRealCheck(password == null ? new char[0] : password);
            audit.log(Event.LOGIN_FAILURE, usernameInput, "malformed login input");
            throw new AuthenticationException(GENERIC_FAILURE);
        }

        User user = users.get(username);
        if (user == null) {
            PasswordHasher.spendSameTimeAsRealCheck(password);
            audit.log(Event.LOGIN_FAILURE, username, "unknown user");
            throw new AuthenticationException(GENERIC_FAILURE);
        }

        if (user.isLocked()) {
            PasswordHasher.spendSameTimeAsRealCheck(password);
            audit.log(Event.LOGIN_FAILURE, username, "attempt on locked account");
            throw new AuthenticationException(LOCKED_MESSAGE);
        }

        if (PasswordHasher.matches(password, user.getSalt(), user.getPasswordHash())) {
            if (user.getFailedAttempts() > 0) {
                user.resetFailedAttempts();
                store.saveUsers(users.values());
            }
            audit.log(Event.LOGIN_SUCCESS, username, "signed in");
            return user;
        }

        user.registerFailedAttempt();
        store.saveUsers(users.values());

        if (user.isLocked()) {
            audit.log(Event.ACCOUNT_LOCKED, username,
                    "locked after " + User.MAX_FAILED_ATTEMPTS + " failed attempts");
            throw new AuthenticationException(LOCKED_MESSAGE);
        }
        audit.log(Event.LOGIN_FAILURE, username,
                "wrong password, attempt " + user.getFailedAttempts()
                        + " of " + User.MAX_FAILED_ATTEMPTS);
        throw new AuthenticationException(GENERIC_FAILURE);
    }

    public void recordLogout(User user, boolean timedOut) {
        audit.log(timedOut ? Event.SESSION_TIMEOUT : Event.LOGOUT, user.getUsername(), "session ended");
    }
}
