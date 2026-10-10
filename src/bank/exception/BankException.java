package bank.exception;

/**
 * Parent of every error this application raises on purpose.
 * Catching BankException in the UI means we never leak a raw stack trace to the user.
 */
public class BankException extends Exception {
    private static final long serialVersionUID = 1L;

    public BankException(String message) {
        super(message);
    }
}
