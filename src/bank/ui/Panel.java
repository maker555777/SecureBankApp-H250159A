package bank.ui;

import java.io.PrintStream;
import java.util.List;

/** Draws the boxed banner, receipts and tables. All sizing is worked out from the content. */
final class Panel {

    private Panel() { }

    static void banner(PrintStream out, Style s, String... lines) {
        int width = 0;
        for (String l : lines) width = Math.max(width, l.length() + 8);
        width = Math.max(width, 46);
        out.println(s.cyan(s.dtl + s.dh.repeat(width) + s.dtr));
        for (String l : lines) {
            out.println(s.cyan(s.dv) + s.bold(center(l, width)) + s.cyan(s.dv));
        }
        out.println(s.cyan(s.dbl + s.dh.repeat(width) + s.dbr));
    }

    /** A titled box of "label   value" rows, used for receipts and balances. */
    static void details(PrintStream out, Style s, String title, String[][] rows) {
        int labelW = 0;
        int valueW = 0;
        for (String[] r : rows) {
            labelW = Math.max(labelW, r[0].length());
            valueW = Math.max(valueW, r[1].length());
        }
        int inner = Math.max(Math.max(title.length() + 4, labelW + valueW + 5), 40);

        out.println(s.cyan(s.tl + s.h.repeat(inner) + s.tr));
        out.println(s.cyan(s.v) + " " + s.bold(padRight(title, inner - 1)) + s.cyan(s.v));
        out.println(s.cyan(s.ml + s.h.repeat(inner) + s.mr));
        for (String[] r : rows) {
            out.println(s.cyan(s.v) + " " + s.dim(padRight(r[0], labelW)) + "  "
                    + padRight(r[1], inner - labelW - 3) + s.cyan(s.v));
        }
        out.println(s.cyan(s.bl + s.h.repeat(inner) + s.br));
    }

    /**
     * A titled table. align is one letter per column: 'L' left, 'R' right.
     */
    static void table(PrintStream out, Style s, String title, String[] headers,
                      String align, List<String[]> rows) {
        int cols = headers.length;
        int[] w = new int[cols];
        for (int c = 0; c < cols; c++) w[c] = headers[c].length();
        for (String[] r : rows) {
            for (int c = 0; c < cols; c++) w[c] = Math.max(w[c], r[c].length());
        }
        int content = 0;
        for (int x : w) content += x;
        content += 2 * (cols - 1);
        int inner = Math.max(content + 2, title.length() + 4);

        out.println(s.cyan(s.tl + s.h.repeat(inner) + s.tr));
        out.println(s.cyan(s.v) + " " + s.bold(padRight(title, inner - 1)) + s.cyan(s.v));
        out.println(s.cyan(s.ml + s.h.repeat(inner) + s.mr));
        out.println(s.cyan(s.v) + " " + s.dim(padRight(line(headers, w, align), inner - 1)) + s.cyan(s.v));
        for (String[] r : rows) {
            out.println(s.cyan(s.v) + " " + padRight(line(r, w, align), inner - 1) + s.cyan(s.v));
        }
        out.println(s.cyan(s.bl + s.h.repeat(inner) + s.br));
    }

    private static String line(String[] cells, int[] w, String align) {
        StringBuilder sb = new StringBuilder();
        for (int c = 0; c < cells.length; c++) {
            if (c > 0) sb.append("  ");
            sb.append(align.charAt(c) == 'R' ? padLeft(cells[c], w[c]) : padRight(cells[c], w[c]));
        }
        return sb.toString();
    }

    static String padRight(String text, int width) {
        return text.length() >= width ? text : text + " ".repeat(width - text.length());
    }

    static String padLeft(String text, int width) {
        return text.length() >= width ? text : " ".repeat(width - text.length()) + text;
    }

    static String center(String text, int width) {
        int total = Math.max(0, width - text.length());
        int left = total / 2;
        return " ".repeat(left) + text + " ".repeat(total - left);
    }
}
