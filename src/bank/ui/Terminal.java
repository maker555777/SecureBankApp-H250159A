package bank.ui;

import java.io.BufferedReader;
import java.io.Console;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

/** Reads what the user types. Passwords are read without echoing them back when the terminal allows it. */
final class Terminal {

    private final PrintStream out;
    private final BufferedReader in =
            new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
    private final Console console = System.console();
    private boolean warnedAboutVisiblePassword;

    Terminal(PrintStream out) {
        this.out = out;
    }

    String readLine(String prompt) {
        out.print(prompt);
        out.flush();
        try {
            String line = in.readLine();
            if (line == null) {
                throw new InputClosedException();
            }
            return line;
        } catch (IOException e) {
            throw new InputClosedException();
        }
    }

    /**
     * Real terminals: the typed characters are hidden. IDE consoles cannot hide
     * input, so there we warn once and read normally. The caller must wipe the
     * returned array when it is finished with it.
     */
    char[] readPassword(String prompt) {
        if (console != null) {
            char[] password = console.readPassword("%s", prompt);
            if (password == null) {
                throw new InputClosedException();
            }
            return password;
        }
        if (!warnedAboutVisiblePassword) {
            out.println("  (This console cannot hide typing - your password will be visible.)");
            warnedAboutVisiblePassword = true;
        }
        return readLine(prompt).toCharArray();
    }
}
