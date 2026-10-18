package eu.amadeos.dm.session;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import eu.amadeos.commons.i18n.Language;
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
    void theLoginLanguageTravelsWithTheRequest() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        WorkerProtocol.Request request = new WorkerProtocol.Request("anna", "secret", SessionType.DESKTOP,
                Optional.of(Language.FRENCH));
        WorkerProtocol.writeRequest(out, request);
        assertEquals("anna\nsecret\ndesktop\nfr\n\n", out.toString(StandardCharsets.UTF_8));
        assertEquals(request, WorkerProtocol.readRequest(new ByteArrayInputStream(out.toByteArray())));
    }

    @Test
    void theTimeZoneTravelsWithTheRequest() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        WorkerProtocol.Request request = new WorkerProtocol.Request("anna", "secret", SessionType.DESKTOP,
                Optional.of(Language.GERMAN), Optional.of("Europe/Berlin"));
        WorkerProtocol.writeRequest(out, request);
        assertEquals("anna\nsecret\ndesktop\nde\nEurope/Berlin\n", out.toString(StandardCharsets.UTF_8));
        assertEquals(request, WorkerProtocol.readRequest(new ByteArrayInputStream(out.toByteArray())));
    }

    @Test
    void requestsWithoutOrWithUnknownLanguageKeepTheDefault() throws IOException {
        WorkerProtocol.Request threeLines = WorkerProtocol.readRequest(new ByteArrayInputStream(
                "anna\nsecret\ndesktop\n".getBytes(StandardCharsets.UTF_8)));
        assertEquals(Optional.empty(), threeLines.language());
        assertEquals(Optional.empty(), threeLines.timeZone());
        WorkerProtocol.Request fourLines = WorkerProtocol.readRequest(new ByteArrayInputStream(
                "anna\nsecret\ndesktop\nklingon\n".getBytes(StandardCharsets.UTF_8)));
        assertEquals(Optional.empty(), fourLines.language());
        assertEquals(Optional.empty(), fourLines.timeZone());
        assertEquals(Optional.empty(), WorkerProtocol.readRequest(new ByteArrayInputStream(
                "anna\nsecret\ndesktop\nde\n \n".getBytes(StandardCharsets.UTF_8))).timeZone(),
                "a blank time zone means: keep the system's");
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
