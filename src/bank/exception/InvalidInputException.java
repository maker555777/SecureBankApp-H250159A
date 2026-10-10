package bank.exception;

/** Thrown when something the user typed (or an amount passed in) fails validation. */
public class InvalidInputException extends BankException {
    private static final long serialVersionUID = 1L;

    public InvalidInputException(String message) {
        super(message);
    }
}
