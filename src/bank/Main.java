package bank;

import bank.exception.BankException;
import bank.security.AuditLogger;
import bank.service.AuthService;
import bank.service.BankService;
import bank.storage.DataStore;
import bank.ui.ConsoleUI;

import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;

/** Starts the console application. Use --ascii if box characters do not display correctly. */
public final class Main {

    private static final int DEFAULT_IDLE_SECONDS = 120;

    private Main() { }

    public static void main(String[] args) {
        boolean ascii = Arrays.asList(args).contains("--ascii");

        // Keep the terminal output consistent across system locales.
        PrintStream out = new PrintStream(new FileOutputStream(FileDescriptor.out), true, StandardCharsets.UTF_8);

        Path dataDir = Paths.get(System.getProperty("bank.data", "data")).toAbsolutePath();
        int idleSeconds = Integer.getInteger("bank.timeout.seconds", DEFAULT_IDLE_SECONDS);

        try {
            DataStore store = new DataStore(dataDir);
            AuditLogger audit = new AuditLogger(dataDir.resolve("audit.log"));
            AuthService auth = new AuthService(store, audit);
            BankService bank = new BankService(store, audit);
            new ConsoleUI(auth, bank, out, ascii, idleSeconds).run(dataDir.toString());
        } catch (BankException e) {
            // Do not start if existing data could not be read safely.
            out.println("The bank could not start: " + e.getMessage());
            out.println("Please check the files in " + dataDir);
            System.exit(1);
        }
    }
}
