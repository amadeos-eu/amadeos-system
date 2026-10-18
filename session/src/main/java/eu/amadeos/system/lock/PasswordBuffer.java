package eu.amadeos.system.lock;

import java.util.Arrays;

/**
 * Password typed into the lock screen. Kept as a char array that is wiped after use instead
 * of an immutable String.
 */
final class PasswordBuffer {
    static final int MAX_LENGTH = 256;
    private char[] chars = new char[32];
    private int length;

    void append(String text) {
        for (int i = 0; i < text.length() && length < MAX_LENGTH; i++) {
            char c = text.charAt(i);
            if (Character.isISOControl(c)) {
                continue;
            }
            if (length == chars.length) {
                char[] bigger = Arrays.copyOf(chars, chars.length * 2);
                Arrays.fill(chars, '\0');
                chars = bigger;
            }
            chars[length++] = c;
        }
    }

    void backspace() {
        if (length > 0) {
            chars[--length] = '\0';
        }
    }

    int length() {
        return length;
    }

    /** Returns the password and wipes the buffer. */
    String take() {
        String password = new String(chars, 0, length);
        clear();
        return password;
    }

    void clear() {
        Arrays.fill(chars, '\0');
        length = 0;
    }
}
