package bank.ui;

/**
 * Everything about how the screen looks: box-drawing characters and colours.
 *
 * Colours are switched off automatically when the output is not a real
 * terminal (an IDE console would print the raw escape codes as junk), and
 * the box characters fall back to plain ASCII with the --ascii flag.
 */
final class Style {

    private static final String RESET = "\u001B[0m";

    private final boolean color;

    // light box (panels)
    final String tl, tr, bl, br, h, v, ml, mr;
    // heavy box (banner)
    final String dtl, dtr, dbl, dbr, dh, dv;

    Style(boolean ascii, boolean color) {
        this.color = color;
        if (ascii) {
            tl = "+"; tr = "+"; bl = "+"; br = "+"; h = "-"; v = "|"; ml = "+"; mr = "+";
            dtl = "+"; dtr = "+"; dbl = "+"; dbr = "+"; dh = "="; dv = "|";
        } else {
            tl = "┌"; tr = "┐"; bl = "└"; br = "┘"; h = "─"; v = "│"; ml = "├"; mr = "┤";
            dtl = "╔"; dtr = "╗"; dbl = "╚"; dbr = "╝"; dh = "═"; dv = "║";
        }
    }

    /** Colour only when we are attached to a real terminal and the user hasn't opted out. */
    static boolean colorSupported() {
        String term = System.getenv("TERM");
        return System.console() != null
                && System.getenv("NO_COLOR") == null
                && term != null && !term.equals("dumb");
    }

    String cyan(String s)   { return wrap("36", s); }
    String green(String s)  { return wrap("32", s); }
    String red(String s)    { return wrap("31", s); }
    String yellow(String s) { return wrap("33", s); }
    String dim(String s)    { return wrap("2", s); }
    String bold(String s)   { return wrap("1", s); }

    private String wrap(String code, String s) {
        return color ? "\u001B[" + code + "m" + s + RESET : s;
    }
}
