package org.datazuul.amadeos.base.sound;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BellToneTest {

    @Test
    void lengthFollowsTheDuration() {
        assertEquals(44_100 / 10 * 2, BellTone.samples(50, 400, 100).length);
        assertEquals(0, BellTone.samples(0, 400, 100).length, "volume 0 is xset b off");
    }

    @Test
    void durationAndPitchAreKeptInRange() {
        assertEquals((int) (BellTone.SAMPLE_RATE * 2) * 2, BellTone.samples(50, 400, 60_000).length);
        assertEquals((int) (BellTone.SAMPLE_RATE / 100) * 2, BellTone.samples(50, 400, 1).length);
        assertTrue(peak(BellTone.samples(50, 40_000, 100)) > 0, "pitch above Nyquist is lowered, not silent");
    }

    @Test
    void louderBellHasHigherPeakAndStartsAndEndsSilent() {
        byte[] quiet = BellTone.samples(20, 440, 200);
        byte[] loud = BellTone.samples(100, 440, 200);
        assertTrue(peak(loud) > 4 * peak(quiet) * 0.9);
        assertTrue(peak(loud) <= Short.MAX_VALUE * BellTone.MAX_AMPLITUDE);
        assertEquals(0, sample(loud, 0), "fade in");
        assertEquals(0, sample(loud, loud.length / 2 - 1), "fade out");
    }

    private static int sample(byte[] pcm, int frame) {
        return (short) ((pcm[2 * frame] & 0xFF) | (pcm[2 * frame + 1] << 8));
    }

    private static int peak(byte[] pcm) {
        int peak = 0;
        for (int i = 0; i < pcm.length / 2; i++) peak = Math.max(peak, Math.abs(sample(pcm, i)));
        return peak;
    }
}
