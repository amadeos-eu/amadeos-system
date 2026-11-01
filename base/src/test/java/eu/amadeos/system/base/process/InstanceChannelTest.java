package eu.amadeos.system.base.process;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InstanceChannelTest {
    @TempDir
    Path dir;

    @Test
    void aSecondStartHandsItsArgumentsOver() throws Exception {
        Path socket = dir.resolve("tm.socket");
        assertFalse(InstanceChannel.send(socket, List.of()), "nobody listens yet");
        CompletableFuture<List<String>> received = new CompletableFuture<>();
        try (InstanceChannel channel = InstanceChannel.listen(socket, received::complete)) {
            assertTrue(InstanceChannel.send(socket, List.of("--not-responding", "0x400001")));
            assertEquals(List.of("--not-responding", "0x400001"), received.get(5, TimeUnit.SECONDS));
        }
        assertFalse(InstanceChannel.send(socket, List.of()), "the socket is removed on close");
    }

    @Test
    void thePathNamesTheProgram() {
        assertTrue(InstanceChannel.defaultPath("amadeos-search").getFileName().toString().contains("amadeos-search"));
    }
}
