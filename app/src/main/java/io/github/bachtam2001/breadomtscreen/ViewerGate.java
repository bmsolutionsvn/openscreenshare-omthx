package io.github.bachtam2001.breadomtscreen;

/**
 * Whether capture should be running. A viewer starts it at once; the last viewer starts a hold so
 * a receiver that reconnects within it (vMix retrying its connection) does not restart the codec.
 */
public final class ViewerGate {

    private final long holdMs;
    private boolean seenViewer;
    private long zeroSinceMs = -1;

    public ViewerGate(long holdMs) {
        this.holdMs = holdMs;
    }

    public boolean update(int viewers, long nowMs) {
        if (viewers > 0) {
            seenViewer = true;
            zeroSinceMs = -1;
            return true;
        }
        if (!seenViewer) {
            return false;
        }
        if (zeroSinceMs < 0) {
            zeroSinceMs = nowMs;
        }
        return nowMs - zeroSinceMs < holdMs;
    }
}