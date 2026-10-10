package bank.exception;

/** Thrown when a withdrawal is more than the account is allowed to give out. */
public class InsufficientFundsException extends BankException {
    private static final long serialVersionUID = 1L;

    public InsufficientFundsException(String message) {
        super(message);
    }
}
