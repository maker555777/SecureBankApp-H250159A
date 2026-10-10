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

/**
 * Entry point. Run it from the project folder so it finds the data folder:
 *
 *     java -cp out bank.Main
 *
 * Options:  --ascii   draw boxes with plain characters (for terminals that show junk)
 */
public final class Main {

    private static final int DEFAULT_IDLE_SECONDS = 120;

    private Main() { }

    public static void main(String[] args) {
        boolean ascii = Arrays.asList(args).contains("--ascii");

        // Always write UTF-8 so the box characters look right regardless of the system locale.
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
            // Corrupt or unreadable data: refuse to start rather than risk overwriting it.
            out.println("The bank could not start: " + e.getMessage());
            out.println("Please check the files in " + dataDir);
            System.exit(1);
        }
    }
}
