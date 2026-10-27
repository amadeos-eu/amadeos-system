package eu.amadeos.system.settings;

import eu.amadeos.system.base.sound.AudioDevice;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.IntConsumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * {@link SoundCheck} with the PulseAudio tools of pipewire-pulse: {@code paplay} plays a generated tone on the
 * chosen output, {@code parec} records the chosen input, whose loudest sample of each tenth of a second becomes
 * the level.
 */
final class PulseSoundCheck implements SoundCheck {
    private static final Logger LOGGER = Logger.getLogger(PulseSoundCheck.class.getName());
    static final int RATE = 44_100;

    @Override
    public void playTest(AudioDevice output, Channel channel) {
        Thread.ofVirtual().start(() -> {
            try {
                Path wav = Files.createTempFile("amadeos-sound-test", ".wav");
                try {
                    Files.write(wav, tone(channel));
                    Process p = new ProcessBuilder("paplay", "--device=" + output.name(), wav.toString())
                            .redirectErrorStream(true).start();
                    p.getInputStream().transferTo(java.io.OutputStream.nullOutputStream());
                    p.waitFor();
                } finally {
                    Files.deleteIfExists(wav);
                }
            } catch (IOException e) {
                LOGGER.log(Level.WARNING, "Cannot play the test sound", e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
    }

    /** A stereo WAV of two soft rising tones (0.7 s), on the left, right or both channels. */
    static byte[] tone(Channel channel) {
        int frames = RATE * 7 / 10;
        ByteBuffer data = ByteBuffer.allocate(frames * 4).order(ByteOrder.LITTLE_ENDIAN);
        for (int i = 0; i < frames; i++) {
            double t = (double) i / RATE;
            double freq = t < 0.35 ? 660 : 880;
            double local = t < 0.35 ? t : t - 0.35;
            // Short fades at both ends of each tone, so it does not click.
            double envelope = Math.min(1, Math.min(local / 0.02, (0.35 - local) / 0.05));
            short sample = (short) (Math.sin(2 * Math.PI * freq * t) * envelope * 0.4 * Short.MAX_VALUE);
            data.putShort(channel == Channel.RIGHT ? 0 : sample);
            data.putShort(channel == Channel.LEFT ? 0 : sample);
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteBuffer header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN);
        header.put("RIFF".getBytes()).putInt(36 + data.capacity()).put("WAVE".getBytes());
        header.put("fmt ".getBytes()).putInt(16).putShort((short) 1).putShort((short) 2).putInt(RATE)
                .putInt(RATE * 4).putShort((short) 4).putShort((short) 16);
        header.put("data".getBytes()).putInt(data.capacity());
        out.writeBytes(header.array());
        out.writeBytes(data.array());
        return out.toByteArray();
    }

    @Override
    public AutoCloseable meter(AudioDevice input, IntConsumer level) {
        Process process;
        try {
            process = new ProcessBuilder(List.of("parec", "--device=" + input.name(), "--raw", "--format=s16le",
                    "--channels=1", "--rate=8000", "--latency-msec=50")).start();
        } catch (IOException e) {
            LOGGER.log(Level.FINE, "Cannot record " + input.name(), e);
            return () -> { };
        }
        Thread.ofVirtual().start(() -> {
            byte[] buffer = new byte[1600]; // a tenth of a second
            try (InputStream in = process.getInputStream()) {
                while (true) {
                    int n = in.readNBytes(buffer, 0, buffer.length);
                    if (n <= 0) break;
                    level.accept(peak(buffer, n));
                }
            } catch (IOException e) {
                LOGGER.log(Level.FINE, "Recording ended", e);
            }
            level.accept(0);
        });
        return process::destroy;
    }

    /** The loudest sample of 16-bit little-endian audio, 0-100 on a scale that follows loudness (dB) roughly. */
    static int peak(byte[] samples, int length) {
        int max = 0;
        for (int i = 0; i + 1 < length; i += 2) {
            int s = Math.abs((short) ((samples[i] & 0xff) | (samples[i + 1] << 8)));
            if (s > max) max = s;
        }
        if (max == 0) return 0;
        double db = 20 * Math.log10(max / (double) Short.MAX_VALUE); // -90 .. 0
        return (int) Math.max(0, Math.min(100, Math.round((db + 60) / 60 * 100)));
    }
}
