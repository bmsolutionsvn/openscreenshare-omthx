package io.github.bachtam2001.breadomtscreen;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** The two status lines the phone and its notification show, character for character. */
public class ServiceStatusTest {

    @Test
    public void idleLine() {
        assertEquals("Idle: waiting for a viewer", CaptureService.statusLine(false, 0, 0, 0, 0));
    }

    @Test
    public void liveLineKeepsTheSentSizeRateAndViewers() {
        assertEquals("Live: 2400×1080 60 fps · 2 viewer(s)",
                CaptureService.statusLine(true, 2400, 1080, 60, 2));
    }

    @Test
    public void soundOffCopyIsExact() {
        assertEquals("Sound is off: Android needs the Record audio permission to capture game sound.",
                CaptureService.SOUND_OFF_COPY);
    }

    @Test
    public void failedEncoderIsNotRetriedWhileViewersStay() {
        assertTrue(CaptureService.shouldStartEncoder(true, false, false));
        assertFalse(CaptureService.shouldStartEncoder(true, false, true));
        assertFalse(CaptureService.shouldStartEncoder(true, true, false));
        assertFalse(CaptureService.shouldStartEncoder(false, false, false));
    }

    @Test
    public void encoderFailureLineNamesTheSize() {
        assertEquals("Could not start the video encoder at 2400×1080",
                CaptureService.encoderFailedLine(2400, 1080));
    }
}