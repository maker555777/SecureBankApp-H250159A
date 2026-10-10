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

/** Handles registration and sign-in. The caller clears password arrays after use. */
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

    /** Register a customer and save the password hash. */
    public User register(String usernameInput, char[] password) throws BankException {
        String username = InputValidator.username(usernameInput);
        InputValidator.newPassword(password, username);
        if (users.containsKey(username)) {
            throw new InvalidInputException("That username is already taken.");
        }

        HashedPassword hashed = PasswordHasher.hash(password);
        User user = new User(username, hashed.salt(), hashed.hash());

        // Update memory only after the file has been saved.
        List<User> updated = new ArrayList<>(users.values());
        updated.add(user);
        store.saveUsers(updated);
        users.put(username, user);

        audit.log(Event.USER_REGISTERED, username, "new user registered");
        return user;
    }

    /** Authenticate a customer and persist failed-attempt counts. */
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
