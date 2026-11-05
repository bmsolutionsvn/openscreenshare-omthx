package io.github.bachtam2001.breadomtscreen;

import io.github.bachtam2001.breadomtscreen.omt.OMTHXFormat;

/**
 * The size to ask the encoder for: the phone's own shape, shrunk evenly only when the encoder
 * cannot do it at the chosen rate. Free of Android imports so the rule is unit-tested on the JVM.
 */
public final class SizeFit {

    /** Below this on the short side the picture is no longer worth sending. */
    private static final int MIN_SHORT_SIDE = 320;

    private SizeFit() {
    }

    /**
     * @return {width, height, fps}, or null when the encoder supports nothing down to the floor.
     */
    public static int[] fit(int width, int height, int fps, OMTHXFormat.Limits limits) {
        if (width <= 0 || height <= 0 || fps <= 0 || limits == null) {
            return null;
        }
        final double ratio = (double) width / (double) height;
        int w = width & ~1;   // both sides even, never larger than the request
        if (w < 2 || (height & ~1) < 2) {
            return null;
        }
        while (Math.min(w, (int) Math.round(w / ratio) & ~1) >= MIN_SHORT_SIDE) {
            int h = ((int) Math.round(w / ratio)) & ~1;
            if (h >= 2
                    && Math.abs((double) w / (double) h - ratio) / ratio <= 0.01
                    && limits.maxFpsAt(w, h) >= fps) {
                return new int[]{w, h, fps};
            }
            w -= 2;
        }
        return null;
    }
}