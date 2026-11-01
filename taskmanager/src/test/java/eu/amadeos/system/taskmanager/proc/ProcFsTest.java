package eu.amadeos.system.taskmanager.proc;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProcFsTest {
    @TempDir
    Path dir;

    @Test
    void readsTheCountersOfTheComputer() throws Exception {
        FakeProc fake = new FakeProc(dir);
        SystemCounters c = new ProcFs(dir).counters();
        assertArrayEquals(new long[]{1000, 4000}, c.cpuTotal());
        assertEquals(2, c.cpuCores().size());
        assertEquals(1000, c.netReceived());
        assertEquals(500, c.netSent());
        assertEquals(10 * 512, c.diskRead(), "sda only: no partition, no loop device");
        assertEquals(20 * 512, c.diskWritten());
        SystemCounters.Memory m = c.memory();
        assertEquals(16000000L * 1024, m.total());
        assertEquals(5000000L * 1024, m.used());
        assertEquals(3100000L * 1024, m.cache());
        assertEquals(0, m.swapUsed());
        assertEquals("Test CPU 9000", new ProcFs(dir).cpuModel());
        assertEquals(1.42, new ProcFs(dir).loadAverage()[0]);
        assertEquals(1761900000L, new ProcFs(dir).bootTimeSeconds());
        assertEquals("ralf", new ProcFs(dir).userNames().get(1000));
    }

    @Test
    void readsProcessesWithSpacesAndParenthesesInTheirNames() throws Exception {
        FakeProc fake = new FakeProc(dir);
        fake.process(2288, 2210, "Web Content", "/usr/lib/firefox-esr/firefox-esr -contentproc 12", 1000, 'S',
                1680, 844000, 4096, 0, 5000);
        fake.process(77, 1, "odd) name (x", "", 0, 'S', 5, 100, -1, -5, 10);
        fake.process(3, 2, "kworker/0:1", "", 0, 'I', 0, 0, -1, 0, 1);
        Map<Integer, ProcessInfo> byPid = new java.util.HashMap<>();
        for (ProcessInfo p : new ProcFs(dir).processes()) byPid.put(p.pid(), p);
        ProcessInfo web = byPid.get(2288);
        assertEquals("Web Content", web.name(), "the name the program gave itself");
        assertEquals(2210, web.parentPid());
        assertEquals(1000, web.uid());
        assertEquals(1680, web.cpuTicks());
        assertEquals(844000L * 1024, web.rssBytes());
        assertEquals(4096, web.ioBytes());
        assertEquals(5000, web.startTicks());
        assertFalse(web.kernelThread());
        ProcessInfo odd = byPid.get(77);
        assertEquals("odd) name (x", odd.name());
        assertEquals(-5, odd.nice());
        assertEquals(-1, odd.ioBytes());
        assertTrue(byPid.get(3).kernelThread());
    }

    @Test
    void namesFollowTheProgramFile() {
        assertEquals("firefox-esr", ProcessInfo.displayName("firefox-esr", List.of("/usr/lib/firefox-esr/firefox-esr")));
        assertEquals("amadeos-wm", ProcessInfo.displayName("java",
                List.of("/usr/bin/java", "--enable-native-access=ALL-UNNAMED", "-jar", "/opt/amadeos-wm/amadeos-wm.jar")));
        assertEquals("amadeos-wm", ProcessInfo.displayName("java",
                List.of("java", "-jar", "/build/amadeos-wm-0.0.0-dev.0.jar")));
        assertEquals("GradleWorkerMain", ProcessInfo.displayName("java",
                List.of("java", "@/tmp/args.txt", "-Xmx512m", "worker.org.gradle.GradleWorkerMain", "'Worker 5'")));
        assertEquals("MailApplication", ProcessInfo.displayName("java",
                List.of("java", "-Xmx512m", "-cp", "/opt/amade-mail/lib/*", "eu.amadeos.apps.mail.MailApplication")));
        assertEquals("Isolated Web Co", ProcessInfo.displayName("Isolated Web Co", List.of("/usr/lib/firefox/firefox")));
        assertEquals("bash", ProcessInfo.displayName("bash", List.of("-bash")), "a login shell's argv0 starts with -");
    }
}
