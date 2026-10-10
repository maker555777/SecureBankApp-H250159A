package bank.exception;

/** Thrown when a line in a data file is damaged or has been edited by hand. */
public class InvalidRecordException extends BankException {
    private static final long serialVersionUID = 1L;

    public InvalidRecordException(String message) {
        super(message);
    }
}
