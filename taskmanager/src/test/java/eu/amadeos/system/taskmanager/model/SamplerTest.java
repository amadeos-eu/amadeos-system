package eu.amadeos.system.taskmanager.model;

import eu.amadeos.system.taskmanager.proc.FakeProc;
import eu.amadeos.system.taskmanager.proc.ProcFs;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SamplerTest {
    @TempDir
    Path dir;

    @Test
    void ratesAndSharesComeFromTwoSamples() throws Exception {
        FakeProc fake = new FakeProc(dir);
        fake.process(100, 1, "busy", "/usr/bin/busy", 1000, 'R', 1000, 4000, 0, 0, 50);
        fake.process(101, 1, "other", "/usr/bin/other", 0, 'S', 10, 400, -1, 0, 60);
        AtomicLong clock = new AtomicLong(0);
        Sampler sampler = new Sampler(new ProcFs(dir), clock::get);
        Sample first = sampler.sample();
        assertEquals(0, first.processes().get(100).cpu(), "nothing to compare with yet");

        // One second later: 400 of 1000 ticks of the machine were busy, 200 of them in process 100.
        clock.set(1_000_000_000L);
        fake.cpu(1400, 5000, new long[]{900, 2500}, new long[]{500, 2500});
        fake.net(1000 + 2048, 500 + 100);
        fake.disk(10 + 8, 20);
        fake.process(100, 1, "busy", "/usr/bin/busy", 1000, 'R', 1200, 4000, 1_000_000, 0, 50);
        Sample second = sampler.sample();
        assertEquals(40, second.cpu(), 0.001);
        assertEquals(80, second.cores().get(0), 0.001);
        assertEquals(0, second.cores().get(1), 0.001);
        assertEquals(20, second.processes().get(100).cpu(), 0.001);
        assertEquals(1_000_000, second.processes().get(100).diskRate(), 0.001);
        assertEquals(-1, second.processes().get(101).diskRate(), "another user's io cannot be read");
        assertEquals("root", second.processes().get(101).user());
        assertEquals(2048, second.netDown(), 0.001);
        assertEquals(100, second.netUp(), 0.001);
        assertEquals(8 * 512, second.diskRead(), 0.001);
        assertEquals(40, sampler.cpu().latest(), 0.001);
        assertEquals(2, sampler.cpu().count());
    }

    @Test
    void aNewProcessWithAnOldIdStartsFromZero() throws Exception {
        FakeProc fake = new FakeProc(dir);
        fake.process(100, 1, "old", "/usr/bin/old", 1000, 'R', 1000, 4000, 0, 0, 50);
        AtomicLong clock = new AtomicLong(0);
        Sampler sampler = new Sampler(new ProcFs(dir), clock::get);
        sampler.sample();
        clock.set(1_000_000_000L);
        fake.cpu(1400, 5000, new long[]{900, 2500}, new long[]{500, 2500});
        fake.process(100, 1, "new", "/usr/bin/new", 1000, 'R', 5, 4000, 0, 0, 999);
        assertEquals(0, sampler.sample().processes().get(100).cpu());
    }
}
