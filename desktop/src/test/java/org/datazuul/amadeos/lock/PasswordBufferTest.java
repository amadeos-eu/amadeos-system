package org.datazuul.amadeos.lock;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PasswordBufferTest {
    @Test
    void collectsTypedCharactersAndWipesOnTake() {
        PasswordBuffer buffer = new PasswordBuffer();
        buffer.append("geh");
        buffer.append("eimä");
        buffer.backspace();
        assertEquals(6, buffer.length());
        assertEquals("geheim", buffer.take());
        assertEquals(0, buffer.length());
        assertEquals("", buffer.take());
    }

    @Test
    void ignoresControlCharactersAndCapsLength() {
        PasswordBuffer buffer = new PasswordBuffer();
        buffer.append("a\tb\u0008c\r");
        assertEquals("abc", buffer.take());
        buffer.append("x".repeat(PasswordBuffer.MAX_LENGTH + 50));
        assertEquals(PasswordBuffer.MAX_LENGTH, buffer.length());
        buffer.clear();
        buffer.backspace();
        assertEquals(0, buffer.length());
    }
}
