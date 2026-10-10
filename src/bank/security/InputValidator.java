package bank.security;

import bank.exception.InvalidInputException;

import java.math.BigDecimal;
import java.util.regex.Pattern;

/**
 * Every rule about what the user may type lives here, in one place.
 * The golden rule is "allow-list": describe what IS acceptable and reject
 * the rest, instead of trying to list every bad thing.
 */
public final class InputValidator {

    private static final Pattern USERNAME = Pattern.compile("[A-Za-z0-9_]{4,20}");
    private static final Pattern AMOUNT = Pattern.compile("\\d{1,7}(\\.\\d{1,2})?");
    private static final Pattern ACCOUNT_NUMBER = Pattern.compile("\\d{10}");

    public static final int MIN_PASSWORD_LENGTH = 8;
    public static final int MAX_PASSWORD_LENGTH = 64;

    private InputValidator() { }

    /**
     * 4-20 letters, digits or underscore. Because the pipe character is not in
     * that list, a username can never break the '|' separated data files.
     * Returned in lower case so "Deon" and "deon" are the same user.
     */
    public static String username(String input) throws InvalidInputException {
        String value = input == null ? "" : input.trim();
        if (!USERNAME.matcher(value).matches()) {
            throw new InvalidInputException(
                    "Username must be 4-20 characters: letters, digits or underscore only.");
        }
        return value.toLowerCase();
    }

    /** For creating a password: enforces the strength rules. */
    public static void newPassword(char[] password, String username) throws InvalidInputException {
        if (password == null || password.length < MIN_PASSWORD_LENGTH
                || password.length > MAX_PASSWORD_LENGTH) {
            throw new InvalidInputException("Password must be " + MIN_PASSWORD_LENGTH
                    + " to " + MAX_PASSWORD_LENGTH + " characters long.");
        }
        boolean hasLetter = false;
        boolean hasDigit = false;
        for (char c : password) {
            if (Character.isLetter(c)) hasLetter = true;
            if (Character.isDigit(c)) hasDigit = true;
        }
        if (!hasLetter || !hasDigit) {
            throw new InvalidInputException("Password needs at least one letter and one digit.");
        }
        if (username != null && new String(password).toLowerCase().contains(username.toLowerCase())) {
            throw new InvalidInputException("Password must not contain your username.");
        }
    }

    /**
     * For logging in: only a sanity check on length. Strength rules are NOT
     * applied here, otherwise a failed login would reveal what the rules are.
     */
    public static void loginPassword(char[] password) throws InvalidInputException {
        if (password == null || password.length == 0 || password.length > MAX_PASSWORD_LENGTH) {
            throw new InvalidInputException("Invalid username or password.");
        }
    }

    /** Typed money: digits with an optional dot and at most 2 decimals, no signs, no letters. */
    public static BigDecimal amount(String input) throws InvalidInputException {
        String value = input == null ? "" : input.trim();
        if (!AMOUNT.matcher(value).matches()) {
            throw new InvalidInputException(
                    "Enter an amount like 150 or 150.75 (digits only, max 2 decimals).");
        }
        BigDecimal result = new BigDecimal(value).setScale(2);
        if (result.signum() <= 0) {
            throw new InvalidInputException("Amount must be greater than zero.");
        }
        return result;
    }

    /** 10 digits and a correct check digit, so a mistyped number is caught early. */
    public static String accountNumber(String input) throws InvalidInputException {
        String value = input == null ? "" : input.trim();
        if (!ACCOUNT_NUMBER.matcher(value).matches()
                || luhnCheckDigit(value.substring(0, 9)) != value.charAt(9) - '0') {
            throw new InvalidInputException("That is not a valid account number.");
        }
        return value;
    }

    /** A menu choice from 1 to max. Anything else (letters, blanks, huge numbers) is rejected. */
    public static int menuChoice(String input, int max) throws InvalidInputException {
        String value = input == null ? "" : input.trim();
        if (value.length() == 0 || value.length() > 2 || !value.chars().allMatch(Character::isDigit)) {
            throw new InvalidInputException("Please choose a number from 1 to " + max + ".");
        }
        int choice = Integer.parseInt(value);
        if (choice < 1 || choice > max) {
            throw new InvalidInputException("Please choose a number from 1 to " + max + ".");
        }
        return choice;
    }

    /**
     * Luhn check digit (the same idea used on bank cards). The account generator
     * in Step 5 uses this to make the 10th digit.
     */
    public static int luhnCheckDigit(String nineDigits) {
        int sum = 0;
        boolean doubleIt = true; // the digit next to the check digit gets doubled
        for (int i = nineDigits.length() - 1; i >= 0; i--) {
            int d = nineDigits.charAt(i) - '0';
            if (doubleIt) {
                d *= 2;
                if (d > 9) d -= 9;
            }
            sum += d;
            doubleIt = !doubleIt;
        }
        return (10 - (sum % 10)) % 10;
    }
}
