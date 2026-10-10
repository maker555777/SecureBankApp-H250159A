package bank.model;

import bank.exception.BankException;
import bank.exception.InsufficientFundsException;
import bank.exception.InvalidInputException;
import bank.exception.InvalidRecordException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.regex.Pattern;

/** Common account state and money movement rules. */
public abstract class Account implements Storable {

    private static final Pattern NUMBER_FORMAT = Pattern.compile("\\d{10}");
    private static final BigDecimal MAX_SINGLE_AMOUNT = new BigDecimal("1000000.00");

    private final String accountNumber;
    private final String ownerUsername;
    private final LocalDate openedOn;
    private BigDecimal balance;

    /** Create a new account with a zero balance. */
    protected Account(String accountNumber, String ownerUsername) {
        this(accountNumber, ownerUsername, BigDecimal.ZERO.setScale(2), LocalDate.now());
    }

    /** Restore an account from a stored record. */
    protected Account(String accountNumber, String ownerUsername,
                      BigDecimal balance, LocalDate openedOn) {
        if (accountNumber == null || !NUMBER_FORMAT.matcher(accountNumber).matches()) {
            throw new IllegalArgumentException("Account number must be 10 digits");
        }
        if (ownerUsername == null || ownerUsername.isBlank()) {
            throw new IllegalArgumentException("Account needs an owner");
        }
        this.accountNumber = accountNumber;
        this.ownerUsername = ownerUsername;
        this.balance = balance.setScale(2);
        this.openedOn = openedOn;
    }

    // Account-type specific rules

    public abstract AccountType getType();

    /** The most that can leave this account in a single withdrawal. */
    public abstract BigDecimal maxWithdrawable();

    // Money movement

    public Transaction deposit(BigDecimal amount) throws BankException {
        BigDecimal value = checkAmount(amount);
        balance = balance.add(value);
        return new Transaction(accountNumber, TransactionType.DEPOSIT, value, balance);
    }

    /** Subclasses cannot bypass the withdrawal checks. */
    public final Transaction withdraw(BigDecimal amount) throws BankException {
        BigDecimal value = checkAmount(amount);
        if (value.compareTo(maxWithdrawable()) > 0) {
            throw new InsufficientFundsException("Insufficient funds for this withdrawal.");
        }
        balance = balance.subtract(value);
        return new Transaction(accountNumber, TransactionType.WITHDRAWAL, value, balance);
    }

    /** Validate and normalize a transaction amount. */
    private static BigDecimal checkAmount(BigDecimal amount) throws InvalidInputException {
        if (amount == null) {
            throw new InvalidInputException("Amount is required.");
        }
        if (amount.signum() <= 0) {
            throw new InvalidInputException("Amount must be greater than zero.");
        }
        if (amount.stripTrailingZeros().scale() > 2) {
            throw new InvalidInputException("Amount can have at most 2 decimal places.");
        }
        BigDecimal value = amount.setScale(2);
        if (value.compareTo(MAX_SINGLE_AMOUNT) > 0) {
            throw new InvalidInputException("Amount is above the per-transaction limit.");
        }
        return value;
    }

    // Persistence

    @Override
    public String toRecord() {
        return String.join(Records.SEPARATOR,
                getType().name(), accountNumber, ownerUsername,
                balance.toPlainString(), openedOn.toString());
    }

    /** Create the appropriate account subtype from a stored record. */
    public static Account fromRecord(String line) throws InvalidRecordException {
        String[] p = Records.split(line, 5);
        try {
            AccountType type = AccountType.valueOf(p[0]);
            BigDecimal balance = new BigDecimal(p[3]);
            LocalDate opened = LocalDate.parse(p[4]);
            return switch (type) {
                case SAVINGS -> new SavingsAccount(p[1], p[2], balance, opened);
                case CURRENT -> new CurrentAccount(p[1], p[2], balance, opened);
            };
        } catch (IllegalArgumentException | DateTimeParseException | ArithmeticException e) {
            throw new InvalidRecordException("Bad account record");
        }
    }

    // Getters

    public String getAccountNumber() { return accountNumber; }
    public String getOwnerUsername() { return ownerUsername; }
    public LocalDate getOpenedOn() { return openedOn; }
    public BigDecimal getBalance() { return balance; }

    /** Return a masked number for display and logging. */
    public String getMaskedNumber() {
        return "******" + accountNumber.substring(6);
    }
}
