package io.github.bachtam2001.breadomtscreen;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

/** The Go Live screen's Android-version copy, character for character. */
public class MainCopyTest {

    @Test
    public void android10To13SaysTheWholeScreenIsShared() {
        String copy = "This Android version shares the whole screen: notifications will show on stream.";
        for (int sdk = 29; sdk <= 33; sdk++) {
            assertEquals("sdk " + sdk, copy, MainActivity.wholeScreenNote(sdk));
        }
    }

    @Test
    public void android14AndLaterHasNoWholeScreenNote() {
        assertNull(MainActivity.wholeScreenNote(34));
        assertNull(MainActivity.wholeScreenNote(35));
        assertNull(MainActivity.wholeScreenNote(36));
    }
}