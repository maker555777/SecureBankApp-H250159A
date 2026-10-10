package bank.storage;

import bank.exception.BankException;
import bank.exception.InvalidRecordException;
import bank.exception.StorageException;
import bank.model.Account;
import bank.model.Storable;
import bank.model.Transaction;
import bank.model.User;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reads and writes the three text files that hold all the bank's data:
 *
 *   users.txt         one line per login
 *   accounts.txt      one line per account
 *   transactions.txt  one line per deposit or withdrawal (append-only)
 *
 * Two safety ideas run through this class:
 *
 *  1. SAFE SAVING. users.txt and accounts.txt are never edited in place.
 *     We write a complete new copy to a temporary file and then rename it over
 *     the old one. A rename is all-or-nothing, so if the power dies half way
 *     the old file is still intact instead of a half-written mess.
 *
 *  2. FAIL LOUDLY ON DAMAGE. If a line is corrupted or has been edited by hand,
 *     we refuse to start and say which file and line. Quietly skipping it
 *     would mean the next save silently deletes that record for good.
 */
public final class DataStore {

    private static final String USERS_HEADER =
            "# users.txt  username|salt|passwordHash|failedAttempts|locked";
    private static final String ACCOUNTS_HEADER =
            "# accounts.txt  type|accountNumber|owner|balance|openedOn";
    private static final String TRANSACTIONS_HEADER =
            "# transactions.txt  id|accountNumber|type|amount|balanceAfter|timestamp";

    private final Path directory;
    private final Path usersFile;
    private final Path accountsFile;
    private final Path transactionsFile;

    public DataStore(Path directory) {
        this.directory = directory;
        this.usersFile = directory.resolve("users.txt");
        this.accountsFile = directory.resolve("accounts.txt");
        this.transactionsFile = directory.resolve("transactions.txt");
    }

    // ---------------- users ----------------

    public Map<String, User> loadUsers() throws BankException {
        Map<String, User> users = new LinkedHashMap<>();
        int line = 0;
        for (User u : readAll(usersFile, User::fromRecord)) {
            line++;
            if (users.put(u.getUsername(), u) != null) {
                throw new InvalidRecordException("users.txt: duplicate username near entry " + line);
            }
        }
        return users;
    }

    public void saveUsers(Collection<User> users) throws BankException {
        writeAtomically(usersFile, USERS_HEADER, users);
    }

    // ---------------- accounts ----------------

    public Map<String, Account> loadAccounts() throws BankException {
        Map<String, Account> accounts = new LinkedHashMap<>();
        int line = 0;
        for (Account a : readAll(accountsFile, Account::fromRecord)) {
            line++;
            if (accounts.put(a.getAccountNumber(), a) != null) {
                throw new InvalidRecordException("accounts.txt: duplicate account near entry " + line);
            }
        }
        return accounts;
    }

    public void saveAccounts(Collection<Account> accounts) throws BankException {
        writeAtomically(accountsFile, ACCOUNTS_HEADER, accounts);
    }

    // ---------------- transactions ----------------

    public List<Transaction> loadTransactions() throws BankException {
        return readAll(transactionsFile, Transaction::fromRecord);
    }

    /** History only ever grows, so we add one line to the end instead of rewriting the file. */
    public void appendTransaction(Transaction transaction) throws BankException {
        try {
            Files.createDirectories(directory);
            String text = "";
            if (!Files.exists(transactionsFile)) {
                text = TRANSACTIONS_HEADER + System.lineSeparator();
            }
            text += transaction.toRecord() + System.lineSeparator();
            Files.writeString(transactionsFile, text, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            restrictToOwner(transactionsFile);
        } catch (IOException e) {
            throw new StorageException("Could not save the transaction.");
        }
    }

    // ---------------- shared helpers ----------------

    /** Turns one line of text into an object; each model class supplies its own. */
    @FunctionalInterface
    private interface RecordParser<T> {
        T parse(String line) throws InvalidRecordException;
    }

    /** A missing file just means "first run": there is nothing to load yet. */
    private <T> List<T> readAll(Path file, RecordParser<T> parser) throws BankException {
        List<T> items = new ArrayList<>();
        if (!Files.exists(file)) {
            return items;
        }
        List<String> lines;
        try {
            lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new StorageException("Could not read " + file.getFileName() + ".");
        }
        for (int i = 0; i < lines.size(); i++) {
            String text = lines.get(i).trim();
            if (text.isEmpty() || text.startsWith("#")) {
                continue; // blank lines and the header comment
            }
            try {
                items.add(parser.parse(text));
            } catch (InvalidRecordException e) {
                // Say where the damage is, but never echo the line itself (it may hold a hash).
                throw new InvalidRecordException(
                        file.getFileName() + " line " + (i + 1) + ": " + e.getMessage());
            }
        }
        return items;
    }

    private void writeAtomically(Path target, String header,
                                 Collection<? extends Storable> items) throws BankException {
        Path temp = null;
        try {
            Files.createDirectories(directory);
            temp = Files.createTempFile(directory, target.getFileName().toString(), ".tmp");

            List<String> lines = new ArrayList<>();
            lines.add(header);
            for (Storable item : items) {
                lines.add(item.toRecord());
            }
            Files.write(temp, lines, StandardCharsets.UTF_8);
            restrictToOwner(temp);

            try {
                Files.move(temp, target,
                        StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            if (temp != null) {
                try { Files.deleteIfExists(temp); } catch (IOException ignored) { }
            }
            throw new StorageException("Could not save " + target.getFileName() + ".");
        }
    }

    /**
     * On Linux/Mac make the file readable and writable by its owner only
     * (like chmod 600), so other users on the machine can't read the hashes.
     * On Windows this call isn't supported and is simply skipped.
     */
    private static void restrictToOwner(Path file) {
        try {
            Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("rw-------"));
        } catch (UnsupportedOperationException | IOException ignored) {
            // not a POSIX file system, nothing to do
        }
    }
}
