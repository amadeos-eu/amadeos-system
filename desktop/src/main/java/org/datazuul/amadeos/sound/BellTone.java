package org.datazuul.amadeos.sound;

import javax.sound.sampled.AudioFormat;

/**
 * The bell as sound: a soft square wave (the first three odd harmonics), like the PC speaker the X bell
 * was made for, with short fades so it does not click. Pure, so it can be tested.
 */
public final class BellTone {
    public static final float SAMPLE_RATE = 44_100f;
    /** 16-bit signed mono, little endian. */
    public static final AudioFormat FORMAT = new AudioFormat(SAMPLE_RATE, 16, 1, true, false);

    /** Loudest bell (100 %) as a fraction of full scale; the system volume still applies on top. */
    static final double MAX_AMPLITUDE = 0.5;
    private static final double FADE_SECONDS = 0.005;

    private BellTone() {
    }

    /**
     * @param percent    volume 0-100; 0 is silence
     * @param pitchHz    tone, kept between 20 Hz and 8 kHz
     * @param durationMs length, kept between 10 ms and 2 s
     * @return PCM samples in {@link #FORMAT}, empty for volume 0
     */
    public static byte[] samples(int percent, int pitchHz, int durationMs) {
        if (percent <= 0) return new byte[0];
        double amplitude = MAX_AMPLITUDE * Math.min(100, percent) / 100.0;
        double pitch = Math.max(20, Math.min(8_000, pitchHz));
        int frames = (int) (SAMPLE_RATE * Math.max(10, Math.min(2_000, durationMs)) / 1000);
        int fade = Math.min(frames / 2, (int) (SAMPLE_RATE * FADE_SECONDS));
        byte[] pcm = new byte[frames * 2];
        for (int i = 0; i < frames; i++) {
            double phase = 2 * Math.PI * pitch * i / SAMPLE_RATE;
            // Peaks at about 0.87
            double wave = Math.sin(phase) + Math.sin(3 * phase) / 3 + Math.sin(5 * phase) / 5;
            double envelope = Math.min(1, Math.min(i, frames - 1 - i) / (double) Math.max(1, fade));
            int value = (int) Math.round(Math.max(-1, Math.min(1, wave * amplitude * envelope)) * Short.MAX_VALUE);
            pcm[2 * i] = (byte) value;
            pcm[2 * i + 1] = (byte) (value >> 8);
        }
        return pcm;
    }
}
