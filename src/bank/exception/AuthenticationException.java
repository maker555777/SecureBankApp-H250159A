package bank.exception;

/**
 * Thrown on a failed login or a locked account.
 * The message is deliberately vague so an attacker can't tell
 * whether the username or the password was the wrong part.
 */
public class AuthenticationException extends BankException {
    private static final long serialVersionUID = 1L;

    public AuthenticationException(String message) {
        super(message);
    }
}
