package bank.exception;

/** Thrown when a data file cannot be read or written (disk full, no permission, etc.). */
public class StorageException extends BankException {
    private static final long serialVersionUID = 1L;

    public StorageException(String message) {
        super(message);
    }
}
