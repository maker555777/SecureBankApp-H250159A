package bank.ui;

import bank.exception.BankException;
import bank.exception.InvalidInputException;
import bank.model.Account;
import bank.model.AccountType;
import bank.model.CurrentAccount;
import bank.model.SavingsAccount;
import bank.model.Transaction;
import bank.model.User;
import bank.security.InputValidator;
import bank.service.AuthService;
import bank.service.BankService;

import java.io.PrintStream;
import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/** Console screens and menus. Banking rules are handled by the service classes. */
public final class ConsoleUI {

    private static final String BANK_NAME = "H I T   T R U S T   B A N K";
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /** Signals that the current session has expired. */
    private static final class SessionExpiredException extends RuntimeException {
        private static final long serialVersionUID = 1L;
    }

    /** Tracks the signed-in user and session timeout. */
    private static final class Session {
        final User user;
        private final long timeoutNanos;
        private long lastActivity = System.nanoTime();

        Session(User user, int timeoutSeconds) {
            this.user = user;
            this.timeoutNanos = timeoutSeconds * 1_000_000_000L;
        }

        boolean expired() { return System.nanoTime() - lastActivity > timeoutNanos; }
        void touch() { lastActivity = System.nanoTime(); }
    }

    private final AuthService auth;
    private final BankService bank;
    private final Terminal term;
    private final Style style;
    private final PrintStream out;
    private final int timeoutSeconds;

    public ConsoleUI(AuthService auth, BankService bank, PrintStream out,
                     boolean ascii, int timeoutSeconds) {
        this.auth = auth;
        this.bank = bank;
        this.out = out;
        this.style = new Style(ascii, Style.colorSupported());
        this.term = new Terminal(out);
        this.timeoutSeconds = timeoutSeconds;
    }

    // =============================================================
    //  Start screen
    // =============================================================

    public void run(String dataFolder) {
        out.println();
        Panel.banner(out, style, BANK_NAME, "Secure Banking  -  HIT Student Project");
        out.println(style.dim("  Data folder: " + dataFolder));
        try {
            while (true) {
                out.println();
                out.println("  1) Sign in");
                out.println("  2) Register");
                out.println("  3) Exit");
                int choice = choose(null, 3);
                switch (choice) {
                    case 1 -> signIn();
                    case 2 -> register();
                    default -> { goodbye(); return; }
                }
            }
        } catch (InputClosedException e) {
            out.println();
            goodbye();
        }
    }

    private void goodbye() {
        out.println();
        out.println(style.dim("  Thank you for banking with HIT Trust Bank. Goodbye."));
    }

    // =============================================================
    //  Register and sign in
    // =============================================================

    private void register() {
        out.println();
        out.println(style.bold("  Create your login"));
        out.println(style.dim("  Username: 4-20 letters, digits or underscore."));
        out.println(style.dim("  Password: 8-64 characters with at least one letter and one digit."));
        String username = term.readLine("  Choose a username: ");
        char[] password = term.readPassword("  Choose a password: ");
        char[] confirm = term.readPassword("  Repeat the password: ");
        try {
            if (!Arrays.equals(password, confirm)) {
                throw new InvalidInputException("The two passwords do not match.");
            }
            auth.register(username, password);
            success("Registration complete. You can now sign in.");
        } catch (BankException e) {
            error(e.getMessage());
        } finally {
            wipe(password);
            wipe(confirm);
        }
    }

    private void signIn() {
        out.println();
        String username = term.readLine("  Username: ");
        char[] password = term.readPassword("  Password: ");
        User user;
        try {
            user = auth.login(username, password);
        } catch (BankException e) {
            error(e.getMessage());
            return;
        } finally {
            wipe(password);
        }
        success("Welcome, " + user.getUsername() + ".");
        out.println(style.dim("  You will be signed out after " + timeoutSeconds
                + " seconds without activity."));
        sessionMenu(new Session(user, timeoutSeconds));
    }

    // =============================================================
    //  Signed-in menu
    // =============================================================

    private void sessionMenu(Session s) {
        try {
            while (true) {
                out.println();
                out.println(style.bold("  Main menu") + style.dim("   signed in as " + s.user.getUsername()));
                out.println("  1) Open a new account");
                out.println("  2) My accounts");
                out.println("  3) Check balance");
                out.println("  4) Deposit");
                out.println("  5) Withdraw");
                out.println("  6) Recent transactions");
                out.println("  7) Sign out");
                int choice = choose(s, 7);
                try {
                    switch (choice) {
                        case 1 -> openAccount(s);
                        case 2 -> listAccounts(s);
                        case 3 -> showBalance(s);
                        case 4 -> deposit(s);
                        case 5 -> withdraw(s);
                        case 6 -> history(s);
                        default -> {
                            auth.recordLogout(s.user, false);
                            out.println(style.dim("  You have been signed out."));
                            return;
                        }
                    }
                } catch (BankException e) {
                    error(e.getMessage());
                }
            }
        } catch (SessionExpiredException e) {
            auth.recordLogout(s.user, true);
            out.println();
            warn("Signed out: no activity for " + timeoutSeconds + " seconds.");
        }
    }

    private void openAccount(Session s) throws BankException {
        out.println();
        out.println(style.bold("  Which type of account?"));
        out.println("  1) Savings  - keeps a minimum balance of " + money(SavingsAccount.MINIMUM_BALANCE));
        out.println("  2) Current  - overdraft of up to " + money(CurrentAccount.OVERDRAFT_LIMIT));
        AccountType type = choose(s, 2) == 1 ? AccountType.SAVINGS : AccountType.CURRENT;

        Account account = bank.openAccount(s.user, type);
        out.println();
        Panel.details(out, style, "New account opened", new String[][] {
                {"Type", label(account)},
                {"Account number", account.getAccountNumber()},
                {"Opened", account.getOpenedOn().toString()},
                {"Balance", money(account.getBalance())}
        });
        out.println(style.yellow("  Note down your account number now. For your safety it is"));
        out.println(style.yellow("  shown in full only here; elsewhere it appears masked."));
    }

    private void listAccounts(Session s) {
        List<Account> accounts = bank.accountsOf(s.user);
        if (accounts.isEmpty()) {
            info("You have no accounts yet. Choose option 1 to open one.");
            return;
        }
        List<String[]> rows = new ArrayList<>();
        int n = 1;
        for (Account a : accounts) {
            rows.add(new String[] {Integer.toString(n++), label(a), a.getMaskedNumber(), money(a.getBalance())});
        }
        out.println();
        Panel.table(out, style, "My accounts", new String[] {"#", "Type", "Account", "Balance"}, "LLLR", rows);
    }

    private void showBalance(Session s) throws BankException {
        Account account = pickAccount(s, "Check the balance of which account?");
        if (account == null) return;
        BigDecimal balance = bank.balanceOf(s.user, account.getAccountNumber());
        out.println();
        Panel.details(out, style, "Account balance", new String[][] {
                {"Account", account.getMaskedNumber() + "  (" + label(account) + ")"},
                {"Balance", money(balance)},
                {"Available to withdraw", money(account.maxWithdrawable())}
        });
    }

    private void deposit(Session s) throws BankException {
        Account account = pickAccount(s, "Deposit into which account?");
        if (account == null) return;
        BigDecimal amount = InputValidator.amount(ask(s, "  Amount to deposit: $"));
        Transaction t = bank.deposit(s.user, account.getAccountNumber(), amount);
        success("Deposit successful.");
        receipt(t, account);
    }

    private void withdraw(Session s) throws BankException {
        Account account = pickAccount(s, "Withdraw from which account?");
        if (account == null) return;
        BigDecimal amount = InputValidator.amount(ask(s, "  Amount to withdraw: $"));
        String answer = ask(s, "  Withdraw " + money(amount) + " from " + account.getMaskedNumber()
                + "? Type yes to confirm: ").trim();
        if (!answer.equalsIgnoreCase("yes")) {
            info("Withdrawal cancelled. Nothing was changed.");
            return;
        }
        Transaction t = bank.withdraw(s.user, account.getAccountNumber(), amount);
        success("Withdrawal successful.");
        receipt(t, account);
    }

    private void history(Session s) throws BankException {
        Account account = pickAccount(s, "Show transactions for which account?");
        if (account == null) return;
        List<Transaction> list = bank.recentTransactions(s.user, account.getAccountNumber(), 10);
        if (list.isEmpty()) {
            info("No transactions on this account yet.");
            return;
        }
        List<String[]> rows = new ArrayList<>();
        for (Transaction t : list) {
            rows.add(new String[] {
                    t.getTimestamp().format(STAMP),
                    t.getType().name(),
                    money(t.getAmount()),
                    money(t.getBalanceAfter()),
                    t.getId()});
        }
        out.println();
        Panel.table(out, style, "Last " + list.size() + " transactions  -  "
                        + account.getMaskedNumber(),
                new String[] {"Date and time", "Type", "Amount", "Balance", "Reference"},
                "LLRRL", rows);
    }

    // =============================================================
    //  Helpers
    // =============================================================

    /** Lists the user's accounts and asks which one. Returns null if they have none. */
    private Account pickAccount(Session s, String question) {
        List<Account> accounts = bank.accountsOf(s.user);
        if (accounts.isEmpty()) {
            info("You have no accounts yet. Choose option 1 to open one.");
            return null;
        }
        if (accounts.size() == 1) {
            Account only = accounts.get(0);
            out.println(style.dim("  Using your " + label(only) + " account " + only.getMaskedNumber()));
            return only;
        }
        out.println();
        out.println(style.bold("  " + question));
        int n = 1;
        for (Account a : accounts) {
            out.println("  " + n++ + ") " + label(a) + "  " + a.getMaskedNumber()
                    + style.dim("   " + money(a.getBalance())));
        }
        return accounts.get(choose(s, accounts.size()) - 1);
    }

    private void receipt(Transaction t, Account account) {
        out.println();
        Panel.details(out, style, "HIT Trust Bank  -  Receipt", new String[][] {
                {"Reference", t.getId()},
                {"Type", t.getType().name()},
                {"Account", account.getMaskedNumber() + "  (" + label(account) + ")"},
                {"Amount", money(t.getAmount())},
                {"New balance", money(t.getBalanceAfter())},
                {"Date and time", t.getTimestamp().format(STAMP)}
        });
    }

    /** Reads a menu number, asking again until it is valid. s is null before sign-in. */
    private int choose(Session s, int max) {
        while (true) {
            String line = s == null ? term.readLine("  > ") : ask(s, "  > ");
            try {
                return InputValidator.menuChoice(line, max);
            } catch (InvalidInputException e) {
                error(e.getMessage());
            }
        }
    }

    /**
     * Reads a line inside a session. If the user sat idle for too long the
     * session ends instead of acting on whatever they typed. (The check happens
     * when Enter is pressed, because a console cannot interrupt a waiting read.)
     */
    private String ask(Session s, String prompt) {
        String line = term.readLine(prompt);
        if (s.expired()) {
            throw new SessionExpiredException();
        }
        s.touch();
        return line;
    }

    private static String label(Account a) {
        return a.getType() == AccountType.SAVINGS ? "Savings" : "Current";
    }

    private static String money(BigDecimal value) {
        String text = String.format(Locale.US, "$%,.2f", value.abs());
        return value.signum() < 0 ? "-" + text : text;
    }

    private static void wipe(char[] secret) {
        if (secret != null) {
            Arrays.fill(secret, '\0');
        }
    }

    private void success(String m) { out.println(style.green("  [OK] " + m)); }
    private void error(String m)   { out.println(style.red("  [!] " + m)); }
    private void warn(String m)    { out.println(style.yellow("  [!] " + m)); }
    private void info(String m)    { out.println(style.dim("  " + m)); }
}
