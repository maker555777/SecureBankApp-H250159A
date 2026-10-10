package bank.model;

import bank.exception.InvalidRecordException;

/** Small helper so every class parses its file line the same careful way. */
final class Records {
    static final String SEPARATOR = "|";

    private Records() { }

    static String[] split(String line, int expectedFields) throws InvalidRecordException {
        if (line == null || line.isBlank()) {
            throw new InvalidRecordException("Empty record");
        }
        // -1 keeps trailing empty fields so a damaged line is caught, not silently shortened
        String[] parts = line.split("\\|", -1);
        if (parts.length != expectedFields) {
            throw new InvalidRecordException("Record has " + parts.length
                    + " fields, expected " + expectedFields);
        }
        return parts;
    }
}
