package io.github.bachtam2001.breadomtscreen;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Encode only while someone watches, with the 5 s hold: a vMix reconnect inside the hold must not
 * restart the encoder.
 */
public class ViewerGateTest {

    @Test
    public void noViewerEverMeansNoCapture() {
        ViewerGate gate = new ViewerGate(5000);
        assertFalse(gate.update(0, 0));
    }

    @Test
    public void captureFollowsViewersWithAHold() {
        ViewerGate gate = new ViewerGate(5000);
        assertTrue(gate.update(1, 100));
        assertTrue(gate.update(0, 200));
        assertTrue(gate.update(0, 5199));
        assertFalse(gate.update(0, 5200));
    }

    @Test
    public void reconnectInsideTheHoldRestartsTheHold() {
        ViewerGate gate = new ViewerGate(5000);
        assertTrue(gate.update(1, 6000));
        assertTrue(gate.update(0, 6100));
        assertTrue(gate.update(1, 6900));
        assertTrue(gate.update(0, 7000));
        assertTrue(gate.update(0, 11999));
    }
}