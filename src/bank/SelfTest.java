package bank;

import bank.exception.AuthenticationException;
import bank.exception.BankException;
import bank.exception.InsufficientFundsException;
import bank.exception.InvalidInputException;
import bank.exception.InvalidRecordException;
import bank.exception.StorageException;
import bank.model.Account;
import bank.model.AccountType;
import bank.model.CurrentAccount;
import bank.model.SavingsAccount;
import bank.model.Transaction;
import bank.model.TransactionType;
import bank.model.User;
import bank.security.AuditLogger;
import bank.security.InputValidator;
import bank.security.PasswordHasher;
import bank.service.AuthService;
import bank.service.BankService;
import bank.storage.DataStore;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/** Runs the project's checks without external test libraries. */
public final class SelfTest {

    private static int passed = 0;
    private static int failed = 0;

        /** Test group that may throw an exception. */
    private interface Action {
        void run() throws Exception;
    }

        /** Services backed by one data directory. */
    private record Env(DataStore store, AuditLogger audit, AuthService auth, BankService bank) {
        static Env open(Path dir) throws BankException {
            DataStore store = new DataStore(dir);
            AuditLogger audit = new AuditLogger(dir.resolve("audit.log"));
            return new Env(store, audit, new AuthService(store, audit), new BankService(store, audit));
        }
    }

    private SelfTest() { }

    public static void main(String[] args) throws Exception {
        Path root = Files.createTempDirectory("securebank-selftest");
        System.out.println("Secure Banking Application - self test");
        System.out.println("Working in a temporary folder; your real data is not touched.");
        try {
            run("Account rules", SelfTest::accountRules);
            run("File records", SelfTest::recordsAndCorruption);
            run("Password hashing", SelfTest::passwordHashing);
            run("Input validation", SelfTest::inputValidation);
            run("Audit log", () -> auditLog(root));
            run("Data files", () -> storage(root));
            run("Registration and login", () -> authentication(root));
            run("Banking operations", () -> banking(root));
        } finally {
            deleteFolder(root);
        }
        System.out.println();
        System.out.println("Result: " + passed + " passed, " + failed + " failed.");
        System.exit(failed == 0 ? 0 : 1);
    }

        // Account rules
    private static void accountRules() throws Exception {
        section("Account rules");

        Account savings = new SavingsAccount("1234567897", "tester");
        savings.deposit(money("100"));
        refuses("Savings cannot dip below its 10.00 minimum", InsufficientFundsException.class,
                () -> savings.withdraw(money("90.01")));
        savings.withdraw(money("90.00"));
        check("Savings allows a withdrawal that leaves exactly 10.00",
                savings.getBalance().compareTo(money("10.00")) == 0);

        Account current = new CurrentAccount("9876543210", "tester");
        current.withdraw(money("200"));
        check("Current account may use its 200.00 overdraft",
                current.getBalance().compareTo(money("-200.00")) == 0);
        refuses("Current account refuses to go past the overdraft", InsufficientFundsException.class,
                () -> current.withdraw(money("0.01")));

        refuses("Deposit of a negative amount is refused", InvalidInputException.class,
                () -> savings.deposit(money("-5")));
        refuses("Deposit of zero is refused", InvalidInputException.class,
                () -> savings.deposit(money("0")));
        refuses("Amount with 3 decimal places is refused", InvalidInputException.class,
                () -> savings.deposit(money("1.234")));
        refuses("Amount above the per-transaction limit is refused", InvalidInputException.class,
                () -> savings.deposit(money("1000000.01")));
        refuses("Missing (null) amount is refused", InvalidInputException.class,
                () -> savings.deposit(null));

        check("Account number is masked for display", savings.getMaskedNumber().equals("******7897"));
    }

        // File records
    private static void recordsAndCorruption() throws Exception {
        section("File records");

        Account account = new CurrentAccount("9876543210", "tester");
        Transaction t = account.deposit(money("42.50"));

        Account backAccount = Account.fromRecord(account.toRecord());
        check("Account survives a save/load round trip",
                backAccount.toRecord().equals(account.toRecord())
                        && backAccount.getType() == AccountType.CURRENT);

        Transaction backTxn = Transaction.fromRecord(t.toRecord());
        check("Transaction survives a save/load round trip",
                backTxn.toRecord().equals(t.toRecord()) && backTxn.getType() == TransactionType.DEPOSIT);

        User user = new User("tester", "salt", "hash");
        user.registerFailedAttempt();
        check("User (with failed-attempt count) survives a round trip",
                User.fromRecord(user.toRecord()).getFailedAttempts() == 1);

        refuses("Account line with the wrong number of fields is rejected", InvalidRecordException.class,
                () -> Account.fromRecord("SAVINGS|1234567897|tester"));
        refuses("Account line with an unknown type is rejected", InvalidRecordException.class,
                () -> Account.fromRecord("LOAN|1234567897|tester|1.00|2026-01-01"));
        refuses("Account line with a non-numeric balance is rejected", InvalidRecordException.class,
                () -> Account.fromRecord("SAVINGS|1234567897|tester|abc|2026-01-01"));
        refuses("Transaction line with a bad date is rejected", InvalidRecordException.class,
                () -> Transaction.fromRecord("TXN-1|1234567897|DEPOSIT|1.00|1.00|not-a-date"));
    }

        // Password hashing
    private static void passwordHashing() {
        section("Password hashing");

        PasswordHasher.HashedPassword first = PasswordHasher.hash("Secret123".toCharArray());
        PasswordHasher.HashedPassword second = PasswordHasher.hash("Secret123".toCharArray());

        check("Correct password verifies",
                PasswordHasher.matches("Secret123".toCharArray(), first.salt(), first.hash()));
        check("Wrong password does not verify",
                !PasswordHasher.matches("secret123".toCharArray(), first.salt(), first.hash()));
        check("Same password gets a different salt each time", !first.salt().equals(second.salt()));
        check("Same password gets a different hash each time", !first.hash().equals(second.hash()));
        check("The hash does not contain the password", !first.hash().contains("Secret123"));
        check("Damaged stored values just fail to match (no crash)",
                !PasswordHasher.matches("x".toCharArray(), "!!!", "???"));
    }

        // Input validation
    private static void inputValidation() throws Exception {
        section("Input validation");

        check("Username is trimmed and lower-cased", InputValidator.username("  Deon_01 ").equals("deon_01"));
        refuses("Username that is too short is refused", InvalidInputException.class,
                () -> InputValidator.username("abc"));
        refuses("Username containing the | separator is refused", InvalidInputException.class,
                () -> InputValidator.username("de|on1"));
        refuses("Username containing a line break is refused", InvalidInputException.class,
                () -> InputValidator.username("deon\nadmin"));
        refuses("Username containing a space is refused", InvalidInputException.class,
                () -> InputValidator.username("de on"));

        refuses("Password without a digit is refused", InvalidInputException.class,
                () -> InputValidator.newPassword("Passwordonly".toCharArray(), "deon"));
        refuses("Password that is too short is refused", InvalidInputException.class,
                () -> InputValidator.newPassword("Ab1".toCharArray(), "deon"));
        refuses("Password containing the username is refused", InvalidInputException.class,
                () -> InputValidator.newPassword("MyDeon1234".toCharArray(), "deon"));
        InputValidator.newPassword("Good1Pass".toCharArray(), "deon");
        check("A reasonable password is accepted", true);

        for (String bad : List.of("-5", "0", "1.234", "1e5", "1,000", "+5", "abc", "", " ")) {
            refuses("Amount '" + bad + "' is refused", InvalidInputException.class,
                    () -> InputValidator.amount(bad));
        }
        check("Amount '150.5' becomes 150.50", InputValidator.amount("150.5").toPlainString().equals("150.50"));

        String body = "123456789";
        String good = body + InputValidator.luhnCheckDigit(body);
        String wrong = body + ((InputValidator.luhnCheckDigit(body) + 1) % 10);
        check("Account number with a correct check digit is accepted",
                InputValidator.accountNumber(good).equals(good));
        refuses("Account number with a wrong check digit is refused", InvalidInputException.class,
                () -> InputValidator.accountNumber(wrong));
        refuses("Account number containing letters is refused", InvalidInputException.class,
                () -> InputValidator.accountNumber("12345abcde"));

        refuses("Menu choice outside the range is refused", InvalidInputException.class,
                () -> InputValidator.menuChoice("9", 5));
        refuses("Menu choice that is not a number is refused", InvalidInputException.class,
                () -> InputValidator.menuChoice("x", 5));
    }

        // Audit log
    private static void auditLog(Path root) throws Exception {
        section("Audit log");

        Path file = root.resolve("audit-test").resolve("audit.log");
        AuditLogger log = new AuditLogger(file);
        log.log(AuditLogger.Event.LOGIN_FAILURE, "deon\n2026-01-01 | LOGIN_SUCCESS | user=admin", "attempt");
        log.log(AuditLogger.Event.DEPOSIT, "deon", "account ******7391 amount 10.00");
        List<String> lines = Files.readAllLines(file);
        check("A fake line injected through a username stays on one line", lines.size() == 2);
        check("Each entry has exactly 4 columns (nothing forged)",
                lines.stream().allMatch(l -> l.split("\\|", -1).length == 4));
    }

        // Storage
    private static void storage(Path root) throws Exception {
        section("Data files");

        Path dir = root.resolve("storage");
        DataStore store = new DataStore(dir);
        check("First run with no files loads nothing instead of crashing",
                store.loadUsers().isEmpty() && store.loadAccounts().isEmpty()
                        && store.loadTransactions().isEmpty());

        PasswordHasher.HashedPassword h = PasswordHasher.hash("Secret123".toCharArray());
        store.saveUsers(List.of(new User("deon_01", h.salt(), h.hash())));
        Account account = new SavingsAccount("1234567897", "deon_01");
        Transaction t = account.deposit(money("250.50"));
        store.saveAccounts(List.of(account));
        store.appendTransaction(t);

        DataStore restarted = new DataStore(dir);
        Map<String, User> users = restarted.loadUsers();
        check("User and a still-valid password hash come back after a restart",
                users.containsKey("deon_01") && PasswordHasher.matches("Secret123".toCharArray(),
                        users.get("deon_01").getSalt(), users.get("deon_01").getPasswordHash()));
        check("Account balance comes back after a restart",
                restarted.loadAccounts().get("1234567897").getBalance().compareTo(money("250.50")) == 0);
        check("Transaction history comes back after a restart",
                restarted.loadTransactions().size() == 1);

        try (Stream<Path> files = Files.list(dir)) {
            check("No temporary files are left behind after saving",
                    files.noneMatch(p -> p.getFileName().toString().endsWith(".tmp")));
        }
        try {
            String perms = PosixFilePermissions.toString(
                    Files.getPosixFilePermissions(dir.resolve("users.txt")));
            check("users.txt can be read only by its owner (rw-------)", perms.equals("rw-------"));
        } catch (UnsupportedOperationException e) {
            System.out.println("  SKIP  permission check (this file system has no POSIX permissions)");
        }

        Files.writeString(dir.resolve("accounts.txt"), "SAVINGS|1234567897|deon_01|10.00|2026-10-10\nGARBAGE\n");
        refuses("A damaged data file stops the program from loading it", InvalidRecordException.class,
                restarted::loadAccounts);
        Files.writeString(dir.resolve("accounts.txt"),
                "SAVINGS|1234567897|a|10.00|2026-10-10\nSAVINGS|1234567897|b|20.00|2026-10-10\n");
        refuses("Two accounts with the same number are rejected", InvalidRecordException.class,
                restarted::loadAccounts);
    }

        // Registration and login
    private static void authentication(Path root) throws Exception {
        section("Registration and login");

        Path dir = root.resolve("auth");
        Env env = Env.open(dir);

        env.auth().register("Deon_01", "Secret123".toCharArray());
        check("Username is stored in lower case", env.store().loadUsers().containsKey("deon_01"));
        check("users.txt does not contain the plain password",
                !Files.readString(dir.resolve("users.txt")).contains("Secret123"));
        refuses("Registering the same username twice is refused", InvalidInputException.class,
                () -> env.auth().register("deon_01", "Another123".toCharArray()));
        refuses("Registering with a weak password is refused", InvalidInputException.class,
                () -> env.auth().register("sam_02", "weak".toCharArray()));
        env.auth().register("sam_02", "Hunter2pass".toCharArray());

        check("Login works and ignores the case of the username",
                env.auth().login("DEON_01", "Secret123".toCharArray()).getUsername().equals("deon_01"));

        String unknown = messageOf(() -> env.auth().login("nobody1", "Whatever1".toCharArray()));
        String wrong = messageOf(() -> env.auth().login("deon_01", "Wrongpass1".toCharArray()));
        check("Unknown user and wrong password give the same message", unknown != null && unknown.equals(wrong));
        refuses("Malformed login input is refused without a crash", AuthenticationException.class,
                () -> env.auth().login("a|b", "x".toCharArray()));

        messageOf(() -> env.auth().login("deon_01", "Wrongpass2".toCharArray()));
        String third = messageOf(() -> env.auth().login("deon_01", "Wrongpass3".toCharArray()));
        check("The third wrong password locks the account", third != null && third.contains("locked"));
        refuses("The correct password is refused while locked", AuthenticationException.class,
                () -> env.auth().login("deon_01", "Secret123".toCharArray()));

        Env restarted = Env.open(dir);
        refuses("The lock is still in place after a restart", AuthenticationException.class,
                () -> restarted.auth().login("deon_01", "Secret123".toCharArray()));
        check("Other users are not affected by someone else's lock",
                restarted.auth().login("sam_02", "Hunter2pass".toCharArray()) != null);
    }

        // Banking operations
    private static void banking(Path root) throws Exception {
        section("Banking operations");

        Path dir = root.resolve("bank");
        Env env = Env.open(dir);
        User sam = env.auth().register("sam_02", "Hunter2pass".toCharArray());
        User kim = env.auth().register("kim_03", "Zebra12345".toCharArray());

        Account savings = env.bank().openAccount(sam, AccountType.SAVINGS);
        String number = savings.getAccountNumber();
        check("New account number has 10 digits and a valid check digit",
                number.matches("\\d{10}") && InputValidator.accountNumber(number).equals(number));

        env.bank().deposit(sam, number, money("500"));
        Transaction w = env.bank().withdraw(sam, number, money("120.25"));
        check("Deposit then withdrawal leave the right balance",
                env.bank().balanceOf(sam, number).compareTo(money("379.75")) == 0);
        check("The receipt shows the balance after the transaction",
                w.getBalanceAfter().compareTo(money("379.75")) == 0);
        refuses("Withdrawing more than allowed is refused", InsufficientFundsException.class,
                () -> env.bank().withdraw(sam, number, money("400")));
        check("A refused withdrawal changes nothing",
                env.bank().balanceOf(sam, number).compareTo(money("379.75")) == 0);

        String notTheirs = messageOf(() -> env.bank().withdraw(kim, number, money("10")));
        String doesNotExist = messageOf(() -> env.bank().withdraw(kim, "1234567897", money("10")));
        check("Another user cannot touch your account", notTheirs != null && notTheirs.contains("not found"));
        check("'Not yours' and 'does not exist' give the same message",
                notTheirs != null && notTheirs.equals(doesNotExist));
        check("The owner's balance is untouched by the attempt",
                env.bank().balanceOf(sam, number).compareTo(money("379.75")) == 0);

        for (int i = 0; i < BankService.MAX_ACCOUNTS_PER_USER - 1; i++) {
            env.bank().openAccount(sam, AccountType.CURRENT);
        }
        refuses("Opening more than the allowed number of accounts is refused", InvalidInputException.class,
                () -> env.bank().openAccount(sam, AccountType.SAVINGS));

        List<Transaction> history = env.bank().recentTransactions(sam, number, 10);
        check("History lists newest first",
                history.size() == 2 && history.get(0).getType() == TransactionType.WITHDRAWAL);

        Env restarted = Env.open(dir);
        check("Balance is still correct after a restart",
                restarted.bank().balanceOf(sam, number).compareTo(money("379.75")) == 0);
        check("History is still there after a restart",
                restarted.bank().recentTransactions(sam, number, 10).size() == 2);

        // Replace the data folder with a file to simulate a failed write.
        System.out.println("  (next: the disk is broken on purpose, so a 'could not write' warning is expected)");
        Path backup = root.resolve("bank-backup");
        Files.move(dir, backup);
        Files.writeString(dir, "this is a file, not a folder");
        refuses("A disk error is reported as a StorageException", StorageException.class,
                () -> env.bank().deposit(sam, number, money("1000")));
        check("After a failed save the balance on screen did NOT change",
                env.bank().balanceOf(sam, number).compareTo(money("379.75")) == 0);
        Files.delete(dir);
        Files.move(backup, dir);
    }

        // Test helpers

        /** Run a test group and report unexpected exceptions as failures. */
    private static void run(String groupName, Action group) {
        try {
            group.run();
        } catch (Exception e) {
            check(groupName + " stopped early because of: " + e.getClass().getSimpleName()
                    + " - " + e.getMessage(), false);
        }
    }

    private static BigDecimal money(String text) {
        return new BigDecimal(text);
    }

    private static void section(String title) {
        System.out.println();
        System.out.println(title);
    }

    private static void check(String name, boolean ok) {
        if (ok) {
            passed++;
            System.out.println("  PASS  " + name);
        } else {
            failed++;
            System.out.println("  FAIL  " + name);
        }
    }

        /** Pass if the action throws the expected exception type. */
    private static void refuses(String name, Class<? extends Exception> expected, Action action) {
        try {
            action.run();
            check(name, false);
        } catch (Exception e) {
            check(name, expected.isInstance(e));
        }
    }

        /** Return the exception message, or null if the action succeeds. */
    private static String messageOf(Action action) {
        try {
            action.run();
            return null;
        } catch (Exception e) {
            return e.getMessage();
        }
    }

    private static void deleteFolder(Path folder) {
        try (Stream<Path> walk = Files.walk(folder)) {
            walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException ignored) {
                    // best effort: it is only a temp folder
                }
            });
        } catch (IOException ignored) {
            // best effort
        }
    }
}
