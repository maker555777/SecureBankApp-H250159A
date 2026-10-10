package bank.service;

import bank.exception.BankException;
import bank.exception.InvalidInputException;
import bank.model.Account;
import bank.model.AccountType;
import bank.model.CurrentAccount;
import bank.model.SavingsAccount;
import bank.model.Transaction;
import bank.model.User;
import bank.security.AuditLogger;
import bank.security.AuditLogger.Event;
import bank.security.InputValidator;
import bank.storage.DataStore;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Everything a signed-in customer can do: open accounts, check a balance,
 * deposit, withdraw, and see recent transactions.
 *
 * Two rules protect the customer:
 *  1. OWNERSHIP. Every method that takes an account number first checks that
 *     the account belongs to the signed-in user. Without this, anyone could
 *     type another customer's number and move their money.
 *  2. SAVE BEFORE COMMIT. A deposit or withdrawal is applied to a COPY of the
 *     account and written to disk. Only if the write succeeds does the copy
 *     replace the real account in memory. A disk error therefore can never
 *     leave the screen showing a balance the file doesn't have.
 */
public final class BankService {

    public static final int MAX_ACCOUNTS_PER_USER = 5;

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String NOT_FOUND = "Account not found.";

    private final DataStore store;
    private final AuditLogger audit;
    private final Map<String, Account> accounts;
    private final List<Transaction> transactions;

    public BankService(DataStore store, AuditLogger audit) throws BankException {
        this.store = store;
        this.audit = audit;
        this.accounts = new LinkedHashMap<>(store.loadAccounts());
        this.transactions = new ArrayList<>(store.loadTransactions());
    }

    // ---------------- opening and listing ----------------

    public Account openAccount(User owner, AccountType type) throws BankException {
        if (accountsOf(owner).size() >= MAX_ACCOUNTS_PER_USER) {
            throw new InvalidInputException("You can hold at most "
                    + MAX_ACCOUNTS_PER_USER + " accounts.");
        }
        String number = newAccountNumber();
        Account account = switch (type) {
            case SAVINGS -> new SavingsAccount(number, owner.getUsername());
            case CURRENT -> new CurrentAccount(number, owner.getUsername());
        };

        List<Account> updated = new ArrayList<>(accounts.values());
        updated.add(account);
        store.saveAccounts(updated);
        accounts.put(number, account);

        audit.log(Event.ACCOUNT_CREATED, owner.getUsername(),
                type + " account " + account.getMaskedNumber());
        return account;
    }

    public List<Account> accountsOf(User owner) {
        List<Account> mine = new ArrayList<>();
        for (Account a : accounts.values()) {
            if (a.getOwnerUsername().equals(owner.getUsername())) {
                mine.add(a);
            }
        }
        return mine;
    }

    // ---------------- reading ----------------

    public BigDecimal balanceOf(User user, String accountNumberInput) throws BankException {
        return ownedAccount(user, accountNumberInput).getBalance();
    }

    /** The most recent transactions for one account, newest first. */
    public List<Transaction> recentTransactions(User user, String accountNumberInput, int limit)
            throws BankException {
        Account account = ownedAccount(user, accountNumberInput);
        List<Transaction> mine = new ArrayList<>();
        for (Transaction t : transactions) {
            if (t.getAccountNumber().equals(account.getAccountNumber())) {
                mine.add(t);
            }
        }
        Collections.reverse(mine);
        return mine.size() > limit ? new ArrayList<>(mine.subList(0, limit)) : mine;
    }

    // ---------------- money movement ----------------

    public Transaction deposit(User user, String accountNumberInput, BigDecimal amount)
            throws BankException {
        Account original = ownedAccount(user, accountNumberInput);
        Account working = copyOf(original);
        try {
            Transaction t = working.deposit(amount);
            commit(original, working, t);
            audit.log(Event.DEPOSIT, user.getUsername(),
                    original.getMaskedNumber() + " amount " + t.getAmount());
            return t;
        } catch (BankException e) {
            audit.log(Event.TRANSACTION_REJECTED, user.getUsername(),
                    "deposit on " + original.getMaskedNumber() + " refused");
            throw e;
        }
    }

    public Transaction withdraw(User user, String accountNumberInput, BigDecimal amount)
            throws BankException {
        Account original = ownedAccount(user, accountNumberInput);
        Account working = copyOf(original);
        try {
            Transaction t = working.withdraw(amount);
            commit(original, working, t);
            audit.log(Event.WITHDRAWAL, user.getUsername(),
                    original.getMaskedNumber() + " amount " + t.getAmount());
            return t;
        } catch (BankException e) {
            audit.log(Event.TRANSACTION_REJECTED, user.getUsername(),
                    "withdrawal on " + original.getMaskedNumber() + " refused");
            throw e;
        }
    }

    // ---------------- internals ----------------

    /**
     * Finds an account AND proves it belongs to this user. A number that does
     * not exist and a number that belongs to someone else give the identical
     * message, so nobody can use this to discover which numbers are real.
     */
    private Account ownedAccount(User user, String accountNumberInput) throws BankException {
        String number;
        try {
            number = InputValidator.accountNumber(accountNumberInput);
        } catch (InvalidInputException e) {
            throw new InvalidInputException(NOT_FOUND);
        }
        Account account = accounts.get(number);
        if (account == null || !account.getOwnerUsername().equals(user.getUsername())) {
            audit.log(Event.TRANSACTION_REJECTED, user.getUsername(), "access to an account that is not theirs");
            throw new InvalidInputException(NOT_FOUND);
        }
        return account;
    }

    private static Account copyOf(Account account) throws BankException {
        return Account.fromRecord(account.toRecord());
    }

    /** Writes the new balance and the transaction; only then updates memory. */
    private void commit(Account original, Account working, Transaction t) throws BankException {
        List<Account> before = new ArrayList<>(accounts.values());
        List<Account> after = new ArrayList<>();
        for (Account a : before) {
            after.add(a == original ? working : a);
        }

        store.saveAccounts(after);
        try {
            store.appendTransaction(t);
        } catch (BankException e) {
            // The balance file already moved on; put it back so the two files agree.
            try {
                store.saveAccounts(before);
            } catch (BankException ignored) {
                // nothing more we can do; the original error is the one to report
            }
            throw e;
        }
        accounts.put(original.getAccountNumber(), working);
        transactions.add(t);
    }

    /** 9 random digits plus a Luhn check digit; retried in the unlikely event of a clash. */
    private String newAccountNumber() {
        String number;
        do {
            String body = (1 + RANDOM.nextInt(9)) + String.format("%08d", RANDOM.nextInt(100_000_000));
            number = body + InputValidator.luhnCheckDigit(body);
        } while (accounts.containsKey(number));
        return number;
    }
}
