package eu.amadeos.system.base.sound;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * {@link SoundService} on top of {@code pactl}, which reaches PipeWire through pipewire-pulse. Volume and
 * mute act on the default output ({@code @DEFAULT_SINK@}), so they follow a change of device at once.
 */
public final class PactlSoundService implements SoundService {
    private static final Logger LOGGER = Logger.getLogger(PactlSoundService.class.getName());
    private static final long TIMEOUT_SECONDS = 10;

    @Override
    public SoundStatus status() {
        try {
            String info = run(Pactl.info());
            List<AudioDevice> outputs = Pactl.parseDevices(run(Pactl.sinks()));
            List<AudioDevice> inputs = Pactl.parseDevices(run(Pactl.sources()));
            return new SoundStatus(true, outputs, Pactl.parseDefault(info, "Default Sink"),
                    inputs, Pactl.parseDefault(info, "Default Source"));
        } catch (IOException | SoundException e) {
            LOGGER.log(Level.FINE, "Sound server not available", e);
            return SoundStatus.UNAVAILABLE;
        }
    }

    @Override
    public void setVolume(int percent) throws SoundException {
        runOrThrow(Pactl.setVolume(percent));
    }

    @Override
    public void setMuted(boolean muted) throws SoundException {
        runOrThrow(Pactl.setMuted(muted));
    }

    @Override
    public void setOutput(AudioDevice device) throws SoundException {
        runOrThrow(Pactl.setDefaultSink(device.name()));
        // WirePlumber moves streams that follow the default, but not those a program pinned to a device
        for (String id : ids(Pactl.sinkInputs())) tryRun(Pactl.moveSinkInput(id, device.name()));
    }

    @Override
    public void setInput(AudioDevice device) throws SoundException {
        runOrThrow(Pactl.setDefaultSource(device.name()));
        for (String id : ids(Pactl.sourceOutputs())) tryRun(Pactl.moveSourceOutput(id, device.name()));
    }

    @Override
    public void setInputMuted(boolean muted) throws SoundException {
        runOrThrow(Pactl.setInputMuted(muted));
    }

    @Override
    public void setOutputVolume(AudioDevice output, int percent) throws SoundException {
        runOrThrow(Pactl.setSinkVolume(output.name(), percent));
    }

    @Override
    public void setOutputMuted(AudioDevice output, boolean muted) throws SoundException {
        runOrThrow(Pactl.setSinkMuted(output.name(), muted));
    }

    @Override
    public void setInputVolume(AudioDevice input, int percent) throws SoundException {
        runOrThrow(Pactl.setSourceVolume(input.name(), percent));
    }

    @Override
    public void setInputMuted(AudioDevice input, boolean muted) throws SoundException {
        runOrThrow(Pactl.setSourceMuted(input.name(), muted));
    }

    @Override
    public List<SoundCard> cards() {
        try {
            return Pactl.parseCards(run(Pactl.cards()));
        } catch (IOException | SoundException e) {
            LOGGER.log(Level.FINE, "Could not list sound cards", e);
            return List.of();
        }
    }

    @Override
    public void setProfile(SoundCard card, String profile) throws SoundException {
        runOrThrow(Pactl.setCardProfile(card.name(), profile));
    }

    @Override
    public AutoCloseable watch(Runnable onChange) {
        Watcher watcher = new Watcher(onChange);
        Thread thread = new Thread(watcher, "pactl-subscribe");
        thread.setDaemon(true);
        thread.start();
        return watcher;
    }

    /** Follows {@code pactl subscribe} and starts it again if it ends, e.g. when PipeWire restarts. */
    private static final class Watcher implements Runnable, AutoCloseable {
        private final Runnable onChange;
        private volatile boolean closed;
        private volatile Process process;

        Watcher(Runnable onChange) {
            this.onChange = onChange;
        }

        @Override
        public void run() {
            while (!closed) {
                try {
                    process = start(Pactl.subscribe());
                    process.getOutputStream().close();
                    try (BufferedReader out = new BufferedReader(
                            new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                        String line;
                        while (!closed && (line = out.readLine()) != null) {
                            if (Pactl.relevantEvent(line)) onChange.run();
                        }
                    }
                } catch (IOException e) {
                    LOGGER.log(Level.FINE, "pactl subscribe failed", e);
                }
                if (closed) return;
                try {
                    Thread.sleep(5_000);
                } catch (InterruptedException e) {
                    return;
                }
                onChange.run();
            }
        }

        @Override
        public void close() {
            closed = true;
            Process p = process;
            if (p != null) p.destroy();
        }
    }

    private static List<String> ids(List<String> command) {
        try {
            return Pactl.parseIds(run(command));
        } catch (IOException | SoundException e) {
            LOGGER.log(Level.FINE, "Could not list streams", e);
            return List.of();
        }
    }

    private static void runOrThrow(List<String> command) throws SoundException {
        try {
            run(command);
        } catch (IOException e) {
            throw new SoundException(e.getMessage());
        }
    }

    private static void tryRun(List<String> command) {
        try {
            run(command);
        } catch (IOException | SoundException e) {
            LOGGER.log(Level.FINE, "Ignored: " + command.get(1), e);
        }
    }

    /** Runs pactl and returns its standard output; a non-zero exit becomes a SoundException. */
    private static String run(List<String> command) throws IOException, SoundException {
        Process process = start(command);
        process.getOutputStream().close();
        CompletableFuture<String> stderr = CompletableFuture.supplyAsync(() -> read(process.getErrorStream()));
        String stdout = read(process.getInputStream());
        try {
            if (!process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                throw new SoundException("pactl did not answer");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
            throw new SoundException("interrupted");
        }
        if (process.exitValue() != 0) {
            throw new SoundException(Pactl.errorMessage(stderr.join()));
        }
        return stdout;
    }

    private static Process start(List<String> command) throws IOException {
        ProcessBuilder builder = new ProcessBuilder(command);
        // English field names; descriptions keep their UTF-8
        builder.environment().put("LC_ALL", "C.UTF-8");
        return builder.start();
    }

    private static String read(java.io.InputStream in) {
        try (in) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "";
        }
    }
}
