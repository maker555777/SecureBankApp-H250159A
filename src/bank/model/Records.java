package bank.model;

import bank.exception.InvalidRecordException;

/** Helpers for parsing pipe-separated records. */
final class Records {
    static final String SEPARATOR = "|";

    private Records() { }

    static String[] split(String line, int expectedFields) throws InvalidRecordException {
        if (line == null || line.isBlank()) {
            throw new InvalidRecordException("Empty record");
        }
        // Keep trailing empty fields so malformed records fail the field-count check.
        String[] parts = line.split("\\|", -1);
        if (parts.length != expectedFields) {
            throw new InvalidRecordException("Record has " + parts.length
                    + " fields, expected " + expectedFields);
        }
        return parts;
    }
}
