package eu.amadeos.base.sound;

import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.Mixer;
import javax.sound.sampled.SourceDataLine;

/**
 * Plays bells through ALSA's {@code default} device, which leads to PipeWire, so the system volume and
 * output selection apply. Playing happens on its own thread; a bell that rings while one is still
 * sounding is dropped instead of queued, so a burst of ^G in a terminal does not drone on.
 */
public final class BellPlayer implements AutoCloseable {
    private static final Logger LOGGER = Logger.getLogger(BellPlayer.class.getName());

    private final ExecutorService thread = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "bell");
        t.setDaemon(true);
        return t;
    });
    private final AtomicBoolean sounding = new AtomicBoolean();
    private final AtomicBoolean reportedFailure = new AtomicBoolean();

    public void ring(int percent, int pitchHz, int durationMs) {
        if (percent <= 0 || !sounding.compareAndSet(false, true)) return;
        try {
            thread.execute(() -> {
                try {
                    play(BellTone.samples(percent, pitchHz, durationMs));
                } finally {
                    sounding.set(false);
                }
            });
        } catch (java.util.concurrent.RejectedExecutionException closed) {
            sounding.set(false);
        }
    }

    private void play(byte[] pcm) {
        try (SourceDataLine line = defaultDeviceLine()) {
            line.open(BellTone.FORMAT);
            line.start();
            line.write(pcm, 0, pcm.length);
            line.drain();
        } catch (LineUnavailableException | IllegalArgumentException | SecurityException e) {
            if (reportedFailure.compareAndSet(false, true)) {
                LOGGER.log(Level.WARNING, "No audio output for the bell: " + e.getMessage(), e);
            }
        }
    }

    private static SourceDataLine defaultDeviceLine() throws LineUnavailableException {
        DataLine.Info info = new DataLine.Info(SourceDataLine.class, BellTone.FORMAT);
        for (Mixer.Info mixerInfo : AudioSystem.getMixerInfo()) {
            String name = mixerInfo.getName().toLowerCase(Locale.ROOT);
            if (!name.startsWith("default") && !name.contains("pipewire") && !name.contains("pulse")) continue;
            Mixer mixer = AudioSystem.getMixer(mixerInfo);
            if (mixer.isLineSupported(info)) return (SourceDataLine) mixer.getLine(info);
        }
        return AudioSystem.getSourceDataLine(BellTone.FORMAT);
    }

    @Override
    public void close() {
        thread.shutdownNow();
    }
}
