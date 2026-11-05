package io.github.bachtam2001.breadomtscreen;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import org.junit.Test;

/** Interleaved playback-capture samples to OMT FPA1 planar audio, the meter, and timestamps. */
public class AudioPackTest {

    @Test
    public void interleavedBecomesPlanarByChannel() {
        assertArrayEquals(new float[]{1, 3, 5, 2, 4, 6},
                AudioPack.toPlanar(new float[]{1, 2, 3, 4, 5, 6}, 3, 2), 0f);
    }

    @Test
    public void meterIsTheClampedPeak() {
        assertEquals(0f, AudioPack.level(new float[]{0f, 0f}, 2), 0f);
        assertEquals(0.5f, AudioPack.level(new float[]{0.25f, -0.5f}, 2), 0f);
        assertEquals(1f, AudioPack.level(new float[]{2f}, 1), 0f);
    }

    @Test
    public void timestampsAdvanceOnTheMonotonicClock() {
        assertEquals(1_000_001_000L, AudioPack.ptsNs(1_000, 48_000, 48_000));
    }
}