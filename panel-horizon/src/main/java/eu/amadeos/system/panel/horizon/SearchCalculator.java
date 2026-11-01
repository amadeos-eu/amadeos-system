package eu.amadeos.system.panel.horizon;

import java.math.BigDecimal;
import java.math.MathContext;
import java.text.DecimalFormatSymbols;
import java.util.Locale;
import java.util.Optional;

/**
 * The sums of the search: {@code 12*7+3}, {@code (4+5)/2}, {@code 2^10}, {@code 80*15%} (fifteen percent of
 * eighty), {@code sqrt(2)}, {@code pi}. A decimal comma counts as a point. Only text that is clearly a
 * calculation, with at least one operator or function, gives a result, so that a search for {@code 2024}, a
 * date such as {@code 2026-11-01} or a file name does not.
 */
final class SearchCalculator {
    private final String text;
    private int pos;

    private SearchCalculator(String text) {
        this.text = text;
    }

    /** The value of the calculation, or empty if the text is not one or cannot be worked out. */
    static Optional<Double> evaluate(String query) {
        String q = query.strip().replace(',', '.').replace('×', '*').replace('÷', '/').replace("−", "-");
        if (q.startsWith("=")) q = q.substring(1).strip();
        if (q.isEmpty() || !q.matches("[0-9.+\\-*/^%() a-z]+") || !looksLikeCalculation(q)) return Optional.empty();
        try {
            SearchCalculator c = new SearchCalculator(q.replace(" ", ""));
            double v = c.expression();
            if (c.pos != c.text.length() || Double.isNaN(v) || Double.isInfinite(v)) return Optional.empty();
            return Optional.of(v);
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    private static boolean looksLikeCalculation(String q) {
        String words = q.replaceAll("sqrt|pi", "");
        if (words.matches(".*[a-z].*") || q.matches("\\d{4}-\\d{1,2}-\\d{1,2}")) return false;
        return q.matches(".*[0-9)a-z]\\s*[+\\-*/^%].*") || q.contains("sqrt") || q.equals("pi")
                || q.matches("-\\s*\\(.*");
    }

    // expression = term (('+' | '-') term)*
    private double expression() {
        double v = term();
        while (pos < text.length()) {
            char c = text.charAt(pos);
            if (c == '+') {
                pos++;
                v += term();
            } else if (c == '-') {
                pos++;
                v -= term();
            } else {
                break;
            }
        }
        return v;
    }

    // term = factor (('*' | '/') factor)*
    private double term() {
        double v = factor();
        while (pos < text.length()) {
            char c = text.charAt(pos);
            if (c == '*') {
                pos++;
                v *= factor();
            } else if (c == '/') {
                pos++;
                double d = factor();
                if (d == 0) throw new IllegalArgumentException("division by zero");
                v /= d;
            } else {
                break;
            }
        }
        return v;
    }

    // factor = unary ('^' factor)?   (right-associative)
    private double factor() {
        double base = unary();
        if (pos < text.length() && text.charAt(pos) == '^') {
            pos++;
            return Math.pow(base, factor());
        }
        return base;
    }

    // unary = '-' unary | '+' unary | primary '%'?
    private double unary() {
        if (pos < text.length() && text.charAt(pos) == '-') {
            pos++;
            return -unary();
        }
        if (pos < text.length() && text.charAt(pos) == '+') {
            pos++;
            return unary();
        }
        double v = primary();
        if (pos < text.length() && text.charAt(pos) == '%') {
            pos++;
            v /= 100;
        }
        return v;
    }

    private double primary() {
        if (pos >= text.length()) throw new IllegalArgumentException("end");
        char c = text.charAt(pos);
        if (c == '(') {
            pos++;
            double v = expression();
            expect(')');
            return v;
        }
        if (text.startsWith("sqrt", pos)) {
            pos += 4;
            expect('(');
            double v = expression();
            expect(')');
            if (v < 0) throw new IllegalArgumentException("negative root");
            return Math.sqrt(v);
        }
        if (text.startsWith("pi", pos)) {
            pos += 2;
            return Math.PI;
        }
        int start = pos;
        while (pos < text.length() && (Character.isDigit(text.charAt(pos)) || text.charAt(pos) == '.')) pos++;
        if (start == pos) throw new IllegalArgumentException("number expected at " + pos);
        return Double.parseDouble(text.substring(start, pos));
    }

    private void expect(char c) {
        if (pos >= text.length() || text.charAt(pos) != c) throw new IllegalArgumentException(c + " expected");
        pos++;
    }

    /** At most 12 significant digits, no trailing zeros, the decimal separator of the language: 3.5, 3,5. */
    static String format(double value, Locale locale) {
        BigDecimal d = new BigDecimal(value).round(new MathContext(12)).stripTrailingZeros();
        String s = d.abs().compareTo(new BigDecimal("1e15")) >= 0 || (d.signum() != 0
                && d.abs().compareTo(new BigDecimal("1e-9")) < 0) ? d.toString() : d.toPlainString();
        if (s.equals("-0")) s = "0";
        char sep = DecimalFormatSymbols.getInstance(locale).getDecimalSeparator();
        return sep == '.' ? s : s.replace('.', sep);
    }
}
