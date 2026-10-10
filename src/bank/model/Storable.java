package bank.model;

/**
 * Anything that can be written to one line of a text file.
 * Fields are separated with the '|' character, so the validator
 * (Step 3) must never allow '|' inside user-supplied text.
 */
public interface Storable {
    String toRecord();
}
