package io.github.bachtam2001.breadomtscreen;

/**
 * Playback capture hands over interleaved floats; OMT FPA1 wants one channel after another. Also
 * the two pure numbers the service needs: the meter level and the frame timestamp.
 */
public final class AudioPack {

    private AudioPack() {
    }

    /** @return engine channel-major samples, `framesPerChannel` per channel. */
    public static float[] toPlanar(float[] interleaved, int framesPerChannel, int channels) {
        float[] planar = new float[framesPerChannel * channels];
        for (int ch = 0; ch < channels; ch++) {
            for (int frame = 0; frame < framesPerChannel; frame++) {
                planar[ch * framesPerChannel + frame] = interleaved[frame * channels + ch];
            }
        }
        return planar;
    }

    /** Peak absolute value, clamped to 0..1. */
    public static float level(float[] samples, int count) {
        float peak = 0f;
        for (int i = 0; i < count && i < samples.length; i++) {
            peak = Math.max(peak, Math.abs(samples[i]));
        }
        return Math.min(1f, peak);
    }

    /** @return the frame's time on the same monotonic clock as video, in nanoseconds. */
    public static long ptsNs(long firstFrameNs, long framesSoFar, int sampleRate) {
        return firstFrameNs + framesSoFar * 1_000_000_000L / sampleRate;
    }
}