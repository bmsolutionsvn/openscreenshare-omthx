package io.github.bachtam2001.breadomtscreen;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioPlaybackCaptureConfiguration;
import android.media.AudioRecord;
import android.media.AudioTimestamp;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Display;
import android.view.WindowManager;

import java.io.IOException;

import io.github.bachtam2001.breadomtscreen.omt.OMTHXEncoder;
import io.github.bachtam2001.breadomtscreen.omt.OMTHXFormat;
import io.github.bachtam2001.breadomtscreen.omt.OMTSender;

/**
 * Owns the one MediaProjection session: a VirtualDisplay whose surface is attached only while a
 * viewer is connected, a hardware encoder fed by that display, and playback-capture audio on the
 * same monotonic clock.
 */
public class CaptureService extends Service {

    private static final String TAG = "CaptureService";

    public static final String ACTION_START =
            "io.github.bachtam2001.breadomtscreen.action.START";
    public static final String ACTION_STOP =
            "io.github.bachtam2001.breadomtscreen.action.STOP";

    public static final String EXTRA_RESULT_CODE = "resultCode";
    public static final String EXTRA_DATA = "data";
    public static final String EXTRA_NAME = "name";
    public static final String EXTRA_QUALITY = "quality";
    public static final String EXTRA_FPS = "fps";

    /** Shown while playback capture has no permission: the session still goes live, silently. */
    public static final String SOUND_OFF_COPY =
            "Sound is off: Android needs the Record audio permission to capture game sound.";

    private static final String CHANNEL_ID = "bread-omt-screen";
    private static final int NOTIFICATION_ID = 1;
    private static final int ANNOUNCE_PORT = 6400;
    private static final int SAMPLE_RATE = 48000;
    private static final int CHANNELS = 2;
    private static final int AUDIO_FRAMES = 960;          // 20 ms
    private static final long POLL_MS = 250;
    private static final long VIEWER_HOLD_MS = 5000;

    /** One activity; it survives the service, which only pushes status. */
    public interface StatusListener {
        void onStatus(String statusLine, float level, boolean soundOff);
    }

    private static volatile StatusListener statusListener;
    private static volatile boolean running;

    public static void setStatusListener(StatusListener listener) {
        statusListener = listener;
    }

    /** True between a successful start and teardown; the activity uses it to label the button. */
    public static boolean isRunning() {
        return running;
    }

    /** The exact copy the phone and its notification show. Pure, so the JVM test can pin it. */
    public static String statusLine(boolean live, int width, int height, int fps, int viewers) {
        if (!live) {
            return "Idle: waiting for a viewer";
        }
        return "Live: " + width + "×" + height + " " + fps + " fps · " + viewers + " viewer(s)";
    }

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ViewerGate viewerGate = new ViewerGate(VIEWER_HOLD_MS);

    private OMTSender sender;
    private Announcer announcer;
    private MediaProjection projection;
    private VirtualDisplay virtualDisplay;
    private OMTHXEncoder encoder;
    private DisplayManager displayManager;

    private String codecMime;
    private int codecTag;
    private boolean hevc;
    private int quality = 50;
    private int fps = 60;
    private int liveWidth;
    private int liveHeight;
    private int densityDpi;

    private AudioRecord audioRecord;
    private Thread audioThread;
    private volatile boolean audioRunning;
    private volatile float level;
    private volatile boolean soundOff;
    private boolean stopped;
    private String lastNotificationLine;

    private final Runnable poll = new Runnable() {
        @Override
        public void run() {
            if (stopped) return;
            if (sender != null) {
                // getViewerCount() counts but does not reap a peer that hung up; this does, so an
                // idle announcement does not keep a dead viewer's socket for the whole session.
                sender.pollStatsForReaping();
            }
            final int viewers = sender == null ? 0 : sender.getViewerCount();
            final boolean capture = viewerGate.update(viewers, SystemClock.elapsedRealtime());
            if (capture && encoder == null) {
                beginCapture();
            } else if (!capture && encoder != null) {
                endCapture();
            }
            publishStatus(viewers);
            handler.postDelayed(this, POLL_MS);
        }
    };

    private final MediaProjection.Callback projectionCallback = new MediaProjection.Callback() {
        @Override
        public void onStop() {
            // The user ended sharing from Android's own UI; that is Stop.
            Log.i(TAG, "MediaProjection stopped by the system");
            stopSession();
        }

        @Override
        public void onCapturedContentResize(int width, int height) {
            handleSizeChange(width, height);
        }
    };

    private final DisplayManager.DisplayListener displayListener =
            new DisplayManager.DisplayListener() {
                @Override
                public void onDisplayAdded(int displayId) {
                }

                @Override
                public void onDisplayRemoved(int displayId) {
                }

                @Override
                public void onDisplayChanged(int displayId) {
                    // Before Android 14 there is no onCapturedContentResize; rotation shows up
                    // here as a changed default display.
                    if (Build.VERSION.SDK_INT < 34 && displayId == Display.DEFAULT_DISPLAY) {
                        handleDisplaySize();
                    }
                }
            };

    @Override
    public void onCreate() {
        super.onCreate();
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager != null) {
            manager.createNotificationChannel(new NotificationChannel(
                    CHANNEL_ID, getString(R.string.app_name), NotificationManager.IMPORTANCE_LOW));
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) {
            stopSelf();
            return START_NOT_STICKY;
        }
        if (ACTION_STOP.equals(intent.getAction())) {
            stopSession();
            return START_NOT_STICKY;
        }
        if (ACTION_START.equals(intent.getAction())) {
            startSession(intent);
        }
        return START_NOT_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        stopSession();
        super.onDestroy();
    }

    private void startSession(Intent intent) {
        if (stopped || sender != null) return;

        final int resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, 0);
        final Intent data;
        if (Build.VERSION.SDK_INT >= 33) {
            data = intent.getParcelableExtra(EXTRA_DATA, Intent.class);
        } else {
            data = intent.getParcelableExtra(EXTRA_DATA);
        }
        if (resultCode == 0 || data == null) {
            Log.e(TAG, "No MediaProjection consent; nothing to start");
            stopSelf();
            return;
        }

        String name = intent.getStringExtra(EXTRA_NAME);
        quality = intent.getIntExtra(EXTRA_QUALITY, 50);
        fps = intent.getIntExtra(EXTRA_FPS, 60);
        soundOff = checkSelfPermission(android.Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED;

        // startForeground must come first: from Android 14 getMediaProjection requires a running
        // mediaProjection foreground service.
        startForeground(NOTIFICATION_ID, buildNotification(statusLine(false, 0, 0, 0, 0)),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION);

        MediaProjectionManager manager =
                (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        projection = manager == null ? null : manager.getMediaProjection(resultCode, data);
        if (projection == null) {
            Log.e(TAG, "Could not obtain MediaProjection");
            stopSession();
            return;
        }

        chooseCodec();
        int[] display = readDisplayMetrics();
        liveWidth = display[0];
        liveHeight = display[1];
        densityDpi = display[2];
        int[] fit = fitSize(liveWidth, liveHeight);
        if (fit != null) {
            liveWidth = fit[0];
            liveHeight = fit[1];
        }

        sender = new OMTSender();
        if (!sender.init(name, liveWidth, liveHeight, fps, quality)) {
            Log.e(TAG, "OMT sender failed to start");
            stopSession();
            return;
        }
        running = true;

        announcer = new Announcer(this);
        announcer.start(name, ANNOUNCE_PORT);

        projection.registerCallback(projectionCallback, handler);

        virtualDisplay = projection.createVirtualDisplay("Bread OMT Screen",
                liveWidth, liveHeight, densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                null, null, handler);

        if (Build.VERSION.SDK_INT < 34) {
            displayManager = (DisplayManager) getSystemService(Context.DISPLAY_SERVICE);
            if (displayManager != null) {
                displayManager.registerDisplayListener(displayListener, handler);
            }
        }

        publishStatus(0);
        handler.postDelayed(poll, POLL_MS);
    }

    private void stopSession() {
        if (stopped) return;
        stopped = true;
        running = false;

        handler.removeCallbacks(poll);

        if (displayManager != null) {
            displayManager.unregisterDisplayListener(displayListener);
            displayManager = null;
        }

        endCapture();

        if (virtualDisplay != null) {
            virtualDisplay.release();
            virtualDisplay = null;
        }

        if (projection != null) {
            try {
                projection.unregisterCallback(projectionCallback);
            } catch (Exception e) {
                Log.w(TAG, "projection.unregisterCallback failed", e);
            }
            projection.stop();
            projection = null;
        }

        if (announcer != null) {
            announcer.stop();
            announcer = null;
        }

        if (sender != null) {
            sender.cleanup();
            sender = null;
        }

        // The activity may still be visible: give it the idle line so its button is not left on
        // Stop (which would otherwise look like Go Live is unavailable until the app restarts).
        StatusListener listener = statusListener;
        if (listener != null) {
            listener.onStatus(statusLine(false, 0, 0, 0, 0), 0f, false);
        }

        stopForeground(STOP_FOREGROUND_REMOVE);
        stopSelf();
    }

    // ===== Encode only while someone watches =====

    private void beginCapture() {
        startEncoder();
        startAudio();
    }

    private void endCapture() {
        stopAudio();
        stopEncoder();
    }

    private void startEncoder() {
        if (encoder != null || virtualDisplay == null) return;
        try {
            encoder = new OMTHXEncoder(codecMime, codecTag, liveWidth, liveHeight, fps,
                    OMTHXFormat.bitrate(liveWidth, liveHeight, fps, quality, hevc));
            encoder.start((data, offset, length, tag, width, height, fpsN, fpsD, ptsNs, keyframe) ->
                    sender.sendCoded(data, offset, length, tag, width, height, fpsN, fpsD, ptsNs));
            virtualDisplay.setSurface(encoder.getInputSurface());
        } catch (IOException | RuntimeException e) {
            Log.e(TAG, "Encoder did not start at " + liveWidth + "x" + liveHeight, e);
            if (encoder != null) {
                encoder.stop();
                encoder = null;
            }
            virtualDisplay.setSurface(null);
        }
    }

    private void stopEncoder() {
        if (virtualDisplay != null) {
            virtualDisplay.setSurface(null);
        }
        OMTHXEncoder victim = encoder;
        encoder = null;
        if (victim != null) {
            victim.stop();
        }
    }

    // ===== Size changes =====

    private void handleDisplaySize() {
        int[] display = readDisplayMetrics();
        handleSizeChange(display[0], display[1]);
    }

    private void handleSizeChange(int width, int height) {
        if (virtualDisplay == null) return;
        int[] fit = fitSize(width, height);
        if (fit == null) return;
        if (fit[0] == liveWidth && fit[1] == liveHeight) return;

        boolean encoding = encoder != null;
        if (encoding) stopEncoder();
        liveWidth = fit[0];
        liveHeight = fit[1];
        virtualDisplay.resize(liveWidth, liveHeight, densityDpi);
        if (encoding) startEncoder();
    }

    private int[] readDisplayMetrics() {
        WindowManager windowManager = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
        Display display = windowManager == null ? null : windowManager.getDefaultDisplay();
        DisplayMetrics metrics = new DisplayMetrics();
        if (display != null) {
            display.getRealMetrics(metrics);
        } else {
            metrics = getResources().getDisplayMetrics();
        }
        return new int[]{metrics.widthPixels, metrics.heightPixels, metrics.densityDpi};
    }

    private int[] fitSize(int width, int height) {
        MediaCodecInfo.CodecCapabilities capabilities = encoderCapabilities(codecMime);
        if (capabilities != null) {
            int[] fit = SizeFit.fit(width, height, fps, limits(capabilities));
            if (fit != null) return fit;
        }
        // No capability table (or nothing fits): send the request with even sides.
        int evenWidth = width & ~1;
        int evenHeight = height & ~1;
        if (evenWidth < 2 || evenHeight < 2) return null;
        return new int[]{evenWidth, evenHeight, fps};
    }

    private void chooseCodec() {
        hevc = encoderCapabilities("video/hevc") != null;
        codecMime = hevc ? "video/hevc" : "video/avc";
        codecTag = hevc ? OMTSender.CODEC_HEVC : OMTSender.CODEC_H264;
    }

    private static MediaCodecInfo.CodecCapabilities encoderCapabilities(String mime) {
        if (mime == null) return null;
        MediaCodecList list = new MediaCodecList(MediaCodecList.REGULAR_CODECS);
        for (MediaCodecInfo info : list.getCodecInfos()) {
            if (!info.isEncoder()) continue;
            for (String type : info.getSupportedTypes()) {
                if (type.equalsIgnoreCase(mime)) {
                    try {
                        return info.getCapabilitiesForType(type);
                    } catch (IllegalArgumentException e) {
                        return null;
                    }
                }
            }
        }
        return null;
    }

    private OMTHXFormat.Limits limits(final MediaCodecInfo.CodecCapabilities capabilities) {
        final MediaCodecInfo.VideoCapabilities video = capabilities.getVideoCapabilities();
        return (width, height) -> {
            try {
                if (!video.isSizeSupported(width, height)) return 0;
                return (int) Math.floor(video.getSupportedFrameRatesFor(width, height).getUpper());
            } catch (RuntimeException e) {
                return 0;
            }
        };
    }

    // ===== Audio =====

    private void startAudio() {
        if (audioRunning || projection == null) return;
        if (checkSelfPermission(android.Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            soundOff = true;
            return;
        }
        try {
            AudioPlaybackCaptureConfiguration capture =
                    new AudioPlaybackCaptureConfiguration.Builder(projection)
                            .addMatchingUsage(AudioAttributes.USAGE_GAME)
                            .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
                            .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
                            .build();

            final int minBytes = AudioRecord.getMinBufferSize(SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_STEREO, AudioFormat.ENCODING_PCM_FLOAT);
            final int bufferBytes = Math.max(minBytes, AUDIO_FRAMES * CHANNELS * 4 * 4);

            audioRecord = new AudioRecord.Builder()
                    .setAudioFormat(new AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                            .setSampleRate(SAMPLE_RATE)
                            .setChannelMask(AudioFormat.CHANNEL_IN_STEREO)
                            .build())
                    .setBufferSizeInBytes(bufferBytes)
                    .setAudioPlaybackCaptureConfig(capture)
                    .build();

            audioRecord.startRecording();
            soundOff = false;
            audioRunning = true;
            audioThread = new Thread(this::audioLoop, "OMTAudio");
            audioThread.setDaemon(true);
            audioThread.start();
        } catch (RuntimeException e) {
            Log.e(TAG, "Playback capture did not start", e);
            soundOff = true;
            releaseAudio();
        }
    }

    private void stopAudio() {
        audioRunning = false;
        if (audioRecord != null) {
            try {
                audioRecord.stop();
            } catch (IllegalStateException e) {
                Log.w(TAG, "AudioRecord.stop failed", e);
            }
        }
        if (audioThread != null) {
            try {
                audioThread.join(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            audioThread = null;
        }
        releaseAudio();
        level = 0f;
    }

    private void releaseAudio() {
        if (audioRecord != null) {
            try {
                audioRecord.release();
            } catch (Exception e) {
                Log.w(TAG, "AudioRecord.release failed", e);
            }
            audioRecord = null;
        }
    }

    private void audioLoop() {
        final float[] interleaved = new float[AUDIO_FRAMES * CHANNELS];
        long firstFrameNs = -1;
        long framesSoFar = 0;
        while (audioRunning) {
            final AudioRecord record = audioRecord;
            if (record == null) return;
            final int read;
            try {
                read = record.read(interleaved, 0, interleaved.length, AudioRecord.READ_BLOCKING);
            } catch (IllegalStateException e) {
                Log.w(TAG, "AudioRecord.read after stop", e);
                return;
            }
            if (read <= 0) continue;

            final int framesRead = read / CHANNELS;
            if (firstFrameNs < 0) {
                firstFrameNs = firstFrameTimeNs(record);
            }
            final float[] planar = AudioPack.toPlanar(interleaved, framesRead, CHANNELS);
            final long ptsNs = AudioPack.ptsNs(firstFrameNs, framesSoFar, SAMPLE_RATE);
            if (sender != null) {
                sender.sendAudio(planar, CHANNELS, framesRead, SAMPLE_RATE, ptsNs);
            }
            framesSoFar += framesRead;
            level = AudioPack.level(interleaved, read);
        }
    }

    private static long firstFrameTimeNs(AudioRecord record) {
        AudioTimestamp timestamp = new AudioTimestamp();
        if (record.getTimestamp(timestamp, AudioTimestamp.TIMEBASE_MONOTONIC) == AudioRecord.SUCCESS) {
            return timestamp.nanoTime;
        }
        return System.nanoTime();
    }

    // ===== Status and notification =====

    private void publishStatus(int viewers) {
        final String line = statusLine(encoder != null, liveWidth, liveHeight, fps, viewers);
        final float currentLevel = audioRunning ? level : 0f;
        StatusListener listener = statusListener;
        if (listener != null) {
            listener.onStatus(line, currentLevel, soundOff);
        }
        if (!line.equals(lastNotificationLine)) {
            lastNotificationLine = line;
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.notify(NOTIFICATION_ID, buildNotification(line));
            }
        }
    }

    private Notification buildNotification(String text) {
        Intent stop = new Intent(this, CaptureService.class).setAction(ACTION_STOP);
        PendingIntent stopIntent = PendingIntent.getService(this, 0, stop,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        return new Notification.Builder(this, CHANNEL_ID)
                .setContentTitle(getString(R.string.app_name))
                .setContentText(text)
                .setSmallIcon(android.R.drawable.presence_video_online)
                .setOngoing(true)
                .addAction(new Notification.Action.Builder(
                        android.R.drawable.ic_delete, "Stop", stopIntent).build())
                .build();
    }
}