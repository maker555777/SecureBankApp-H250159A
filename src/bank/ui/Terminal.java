package bank.ui;

import java.io.BufferedReader;
import java.io.Console;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

/** Reads console input, hiding passwords when supported by the terminal. */
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

    /** The caller is responsible for clearing the returned password array. */
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
