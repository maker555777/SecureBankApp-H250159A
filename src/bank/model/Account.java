package bank.model;

import bank.exception.BankException;
import bank.exception.InsufficientFundsException;
import bank.exception.InvalidInputException;
import bank.exception.InvalidRecordException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.regex.Pattern;

/**
 * Base class for every kind of account.
 *
 * The balance is private and there is no setBalance(): the only way
 * money moves is through deposit() and withdraw(), and both of them
 * validate the amount first. Subclasses only decide HOW MUCH may be
 * taken out (see maxWithdrawable()).
 */
public abstract class Account implements Storable {

    private static final Pattern NUMBER_FORMAT = Pattern.compile("\\d{10}");
    private static final BigDecimal MAX_SINGLE_AMOUNT = new BigDecimal("1000000.00");

    private final String accountNumber;
    private final String ownerUsername;
    private final LocalDate openedOn;
    private BigDecimal balance;

    /** Opening a new account: starts at zero today. */
    protected Account(String accountNumber, String ownerUsername) {
        this(accountNumber, ownerUsername, BigDecimal.ZERO.setScale(2), LocalDate.now());
    }

    /** Restoring an account that was loaded from the data file. */
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

    // ---- behaviour that differs between account types ----

    public abstract AccountType getType();

    /** The most that can leave this account in a single withdrawal. */
    public abstract BigDecimal maxWithdrawable();

    // ---- money movement ----

    public Transaction deposit(BigDecimal amount) throws BankException {
        BigDecimal value = checkAmount(amount);
        balance = balance.add(value);
        return new Transaction(accountNumber, TransactionType.DEPOSIT, value, balance);
    }

    /** final: no subclass may skip the checks by overriding this. */
    public final Transaction withdraw(BigDecimal amount) throws BankException {
        BigDecimal value = checkAmount(amount);
        if (value.compareTo(maxWithdrawable()) > 0) {
            throw new InsufficientFundsException("Insufficient funds for this withdrawal.");
        }
        balance = balance.subtract(value);
        return new Transaction(accountNumber, TransactionType.WITHDRAWAL, value, balance);
    }

    /** Rejects null, zero, negative, over-precise and absurdly large amounts. */
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

    // ---- persistence ----

    @Override
    public String toRecord() {
        return String.join(Records.SEPARATOR,
                getType().name(), accountNumber, ownerUsername,
                balance.toPlainString(), openedOn.toString());
    }

    /** Factory: reads the type column and builds the right subclass. */
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

    // ---- getters (no setters on purpose) ----

    public String getAccountNumber() { return accountNumber; }
    public String getOwnerUsername() { return ownerUsername; }
    public LocalDate getOpenedOn() { return openedOn; }
    public BigDecimal getBalance() { return balance; }

    /** For screens and logs: ******7391 instead of the full number. */
    public String getMaskedNumber() {
        return "******" + accountNumber.substring(6);
    }
}
