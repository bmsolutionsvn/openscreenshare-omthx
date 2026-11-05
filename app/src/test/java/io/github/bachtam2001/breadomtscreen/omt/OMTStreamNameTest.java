package io.github.bachtam2001.breadomtscreen.omt;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 * The rule that decides whether an OMT receiver lists this phone at all.
 *
 * A name without a matched '(' ')' pair is dropped by libomtnet's OMTAddress.IsValid on both the
 * Windows and Avahi browse paths, with no error surfaced anywhere.
 */
public class OMTStreamNameTest {

    private static void assertReceiverVisible(String name) {
        int open = name.indexOf('(');
        int close = name.indexOf(')');
        assertTrue("receiver would discard this name (no '(' ): " + name, open >= 0);
        assertTrue("receiver would discard this name (no ')' after '('): " + name, close > open);
    }

    @Test
    public void customNameIsMadeVisible() {
        String n = OMTStreamName.normalize("PhoneCam");
        assertEquals("PhoneCam (Screen)", n);
        assertReceiverVisible(n);
    }

    @Test
    public void alreadyQualifiedNamesAreLeftAlone() {
        assertEquals("Studio (Back Room)", OMTStreamName.normalize("Studio (Back Room)"));
        assertEquals("Cam (2)", OMTStreamName.normalize("Cam (2)"));
    }

    @Test
    public void whitespaceIsTrimmedButNameKept() {
        assertEquals("PhoneCam (Screen)", OMTStreamName.normalize("  PhoneCam  "));
    }

    @Test
    public void strayClosingParenDoesNotProduceGarbage() {
        String n = OMTStreamName.normalize("PhoneCam)");
        assertReceiverVisible(n);
        assertFalse("double parenthesis from a stray fragment: " + n, n.contains(") (Screen) ("));
    }

    @Test
    public void emptyAndNullFallBackToTheCaller() {
        assertNull(OMTStreamName.normalize(null));
        assertNull(OMTStreamName.normalize(""));
        assertNull(OMTStreamName.normalize("     "));
    }

    @Test
    public void onlyParenthesisCharactersAreRejectedAsUnusable() {
        String n = OMTStreamName.normalize("(");
        assertReceiverVisible(n);
    }

    @Test
    public void theDefaultNameAlreadyPasses() {
        assertReceiverVisible("Pixel 7_1a2b3c (Screen)");
        assertEquals("Phone (Screen)", OMTStreamName.normalize("Phone"));
    }
}