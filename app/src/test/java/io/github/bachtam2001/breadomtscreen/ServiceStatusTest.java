package io.github.bachtam2001.breadomtscreen;

import static org.junit.Assert.assertEquals;

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
}