package bank.model;

import bank.exception.InvalidRecordException;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/** An immutable account transaction. */
public final class Transaction implements Storable {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final String id;
    private final String accountNumber;
    private final TransactionType type;
    private final BigDecimal amount;
    private final BigDecimal balanceAfter;
    private final LocalDateTime timestamp;

    /** Create a transaction with the current timestamp. */
    public Transaction(String accountNumber, TransactionType type,
                       BigDecimal amount, BigDecimal balanceAfter) {
        this(newId(), accountNumber, type, amount, balanceAfter,
                LocalDateTime.now().withNano(0));
    }

    private Transaction(String id, String accountNumber, TransactionType type,
                        BigDecimal amount, BigDecimal balanceAfter, LocalDateTime timestamp) {
        this.id = id;
        this.accountNumber = accountNumber;
        this.type = type;
        this.amount = amount;
        this.balanceAfter = balanceAfter;
        this.timestamp = timestamp;
    }

    /** Generate a reference using the date and a random suffix. */
    private static String newId() {
        String date = LocalDateTime.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        return String.format("TXN-%s-%06X", date, RANDOM.nextInt(0x1000000));
    }

    public static Transaction fromRecord(String line) throws InvalidRecordException {
        String[] p = Records.split(line, 6);
        try {
            return new Transaction(
                    p[0],
                    p[1],
                    TransactionType.valueOf(p[2]),
                    new BigDecimal(p[3]),
                    new BigDecimal(p[4]),
                    LocalDateTime.parse(p[5]));
        } catch (IllegalArgumentException | DateTimeParseException e) {
            // NumberFormatException is a subclass of IllegalArgumentException.
            throw new InvalidRecordException("Bad transaction record");
        }
    }

    @Override
    public String toRecord() {
        return String.join(Records.SEPARATOR,
                id, accountNumber, type.name(),
                amount.toPlainString(), balanceAfter.toPlainString(),
                timestamp.toString());
    }

    public String getId() { return id; }
    public String getAccountNumber() { return accountNumber; }
    public TransactionType getType() { return type; }
    public BigDecimal getAmount() { return amount; }
    public BigDecimal getBalanceAfter() { return balanceAfter; }
    public LocalDateTime getTimestamp() { return timestamp; }
}
