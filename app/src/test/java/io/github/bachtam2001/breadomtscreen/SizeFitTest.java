package io.github.bachtam2001.breadomtscreen;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import io.github.bachtam2001.breadomtscreen.omt.OMTHXFormat;
import org.junit.Test;

/**
 * The size the phone's own shape is sent at: never letterboxed, never larger than the screen, and
 * shrunk evenly only when the encoder cannot do the native size at the chosen rate.
 */
public class SizeFitTest {

    @Test
    public void nativeSizeIsSupported() {
        assertArrayEquals(new int[]{2400, 1080, 60},
                SizeFit.fit(2400, 1080, 60, (w, h) -> 60));
    }

    @Test
    public void widthIsCappedWithoutLosingTheShape() {
        int[] fit = SizeFit.fit(2400, 1080, 60, (w, h) -> w <= 1920 ? 60 : 0);
        assertNotNull(fit);
        assertTrue("width " + fit[0] + " must be capped at 1920", fit[0] <= 1920);
        assertEquals(60, fit[2]);
        double requested = 2400.0 / 1080.0;
        double actual = (double) fit[0] / fit[1];
        assertTrue("aspect changed by more than 1%: " + fit[0] + "x" + fit[1],
                Math.abs(actual - requested) / requested < 0.01);
        assertEquals(0, fit[0] % 2);
        assertEquals(0, fit[1] % 2);
    }

    @Test
    public void oddInputGivesEvenSides() {
        int[] fit = SizeFit.fit(2401, 1081, 60, (w, h) -> 60);
        assertNotNull(fit);
        assertEquals(0, fit[0] % 2);
        assertEquals(0, fit[1] % 2);
    }

    @Test
    public void portraitStaysPortrait() {
        int[] fit = SizeFit.fit(1080, 2400, 60, (w, h) -> h <= 1920 ? 60 : 0);
        assertNotNull(fit);
        assertTrue("portrait became landscape: " + fit[0] + "x" + fit[1], fit[1] > fit[0]);
    }

    @Test
    public void nothingFitsBelowTheShortSideFloor() {
        assertNull(SizeFit.fit(2400, 1080, 60, (w, h) -> 0));
    }
}