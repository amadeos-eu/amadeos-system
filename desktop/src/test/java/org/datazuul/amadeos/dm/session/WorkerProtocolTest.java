package org.datazuul.amadeos.dm.session;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WorkerProtocolTest {
    @Test
    void requestRoundTripsIncludingUnicodeAndSpaces() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        WorkerProtocol.Request request = new WorkerProtocol.Request("jürgen", "pass wört ß", SessionType.FAILSAFE);
        WorkerProtocol.writeRequest(out, request);
        assertEquals(request, WorkerProtocol.readRequest(new ByteArrayInputStream(out.toByteArray())));
    }

    @Test
    void lineBreaksInCredentialsAreRejected() {
        assertThrows(IOException.class, () -> WorkerProtocol.writeRequest(new ByteArrayOutputStream(),
                new WorkerProtocol.Request("user", "pass\nroot", SessionType.DESKTOP)));
    }

    @Test
    void incompleteOrOversizedRequestsFail() {
        assertThrows(IOException.class, () -> WorkerProtocol.readRequest(
                new ByteArrayInputStream("onlyuser\n".getBytes(StandardCharsets.UTF_8))));
        byte[] huge = new byte[WorkerProtocol.MAX_LINE_BYTES + 10];
        java.util.Arrays.fill(huge, (byte) 'a');
        assertThrows(IOException.class, () -> WorkerProtocol.readRequest(new ByteArrayInputStream(huge)));
    }

    @Test
    void readLineDoesNotConsumeBytesAfterTheLine() throws IOException {
        ByteArrayInputStream in = new ByteArrayInputStream("first\nsecond\n".getBytes(StandardCharsets.UTF_8));
        assertEquals("first", WorkerProtocol.readLine(in));
        assertEquals("second", WorkerProtocol.readLine(in));
        assertNull(WorkerProtocol.readLine(in));
    }

    @Test
    void resultsAreParsed() throws IOException {
        ByteArrayOutputStream ok = new ByteArrayOutputStream();
        WorkerProtocol.writeResult(ok, true, null);
        assertNull(WorkerProtocol.parseResult(ok.toString(StandardCharsets.UTF_8).trim()));

        ByteArrayOutputStream fail = new ByteArrayOutputStream();
        WorkerProtocol.writeResult(fail, false, "Login incorrect");
        assertEquals("Login incorrect", WorkerProtocol.parseResult(fail.toString(StandardCharsets.UTF_8).trim()));

        assertNotNull(WorkerProtocol.parseResult(null));
        assertNotNull(WorkerProtocol.parseResult("garbage"));
    }
}
