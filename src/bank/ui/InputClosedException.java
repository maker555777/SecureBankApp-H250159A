package bank.ui;

/** Raised when the input stream ends (the user pressed Ctrl+D) so the app can exit tidily. */
final class InputClosedException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    InputClosedException() {
        super("Input closed");
    }
}
