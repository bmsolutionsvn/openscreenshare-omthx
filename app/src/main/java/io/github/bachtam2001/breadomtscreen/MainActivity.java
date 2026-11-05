package io.github.bachtam2001.breadomtscreen;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.media.projection.MediaProjectionManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

import io.github.bachtam2001.breadomtscreen.omt.OMTStreamName;

/**
 * The one screen: source name, quality, rate, Go Live / Stop, and what the phone is doing right now.
 */
public class MainActivity extends Activity {

    private static final int REQ_PERMISSIONS = 1;
    private static final int REQ_CAPTURE = 2;

    private static final String PREFS = "bread_omt_screen";
    private static final String PREF_NAME = "name";
    private static final String PREF_QUALITY = "quality";
    private static final String PREF_RATE = "rate";

    private static final int[] QUALITIES = {1, 50, 100};   // Low, Medium, High
    private static final int[] RATES = {60, 30};

    private static final String WHOLE_SCREEN_COPY =
            "This Android version shares the whole screen: notifications will show on stream.";

    /** API 29..33 share the whole screen; 34+ can share just the chosen app. */
    public static String wholeScreenNote(int sdk) {
        return sdk < 34 ? WHOLE_SCREEN_COPY : null;
    }

    private EditText nameField;
    private Spinner qualitySpinner;
    private Spinner rateSpinner;
    private Button liveButton;
    private TextView statusText;
    private ProgressBar meter;
    private TextView soundOffText;
    private TextView networkText;
    private TextView wholeScreenText;

    private boolean live;

    private final CaptureService.StatusListener statusListener = (line, level, soundOff) ->
            runOnUiThread(() -> {
                live = !line.startsWith("Idle");
                statusText.setText(line);
                liveButton.setText(live ? "Stop" : "Go Live");
                meter.setProgress(Math.round(level * 100f));
                soundOffText.setVisibility(soundOff ? View.VISIBLE : View.GONE);
                updateNetworkName();
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        nameField = findViewById(R.id.name);
        qualitySpinner = findViewById(R.id.quality);
        rateSpinner = findViewById(R.id.rate);
        liveButton = findViewById(R.id.live);
        statusText = findViewById(R.id.status);
        meter = findViewById(R.id.meter);
        soundOffText = findViewById(R.id.sound_off);
        networkText = findViewById(R.id.network);
        wholeScreenText = findViewById(R.id.whole_screen);

        qualitySpinner.setAdapter(spinnerAdapter("Low", "Medium", "High"));
        rateSpinner.setAdapter(spinnerAdapter("60 fps", "30 fps"));

        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        nameField.setText(prefs.getString(PREF_NAME, defaultName()));
        qualitySpinner.setSelection(clamp(prefs.getInt(PREF_QUALITY, 1), QUALITIES.length));
        rateSpinner.setSelection(clamp(prefs.getInt(PREF_RATE, 0), RATES.length));

        soundOffText.setText(CaptureService.SOUND_OFF_COPY);

        String note = wholeScreenNote(Build.VERSION.SDK_INT);
        wholeScreenText.setText(note);
        wholeScreenText.setVisibility(note == null ? View.GONE : View.VISIBLE);

        liveButton.setOnClickListener(v -> {
            if (live) {
                startService(new Intent(this, CaptureService.class)
                        .setAction(CaptureService.ACTION_STOP));
            } else {
                requestGoLive();
            }
        });

        updateNetworkName();
    }

    @Override
    protected void onResume() {
        super.onResume();
        CaptureService.setStatusListener(statusListener);
    }

    @Override
    protected void onPause() {
        CaptureService.setStatusListener(null);
        super.onPause();
    }

    private ArrayAdapter<String> spinnerAdapter(String... values) {
        ArrayAdapter<String> adapter =
                new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, values);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        return adapter;
    }

    private static int clamp(int value, int length) {
        return Math.max(0, Math.min(length - 1, value));
    }

    private String defaultName() {
        String name = OMTStreamName.normalize(Build.MODEL);
        return name == null ? "Bread OMT Screen (Screen)" : name;
    }

    /** The name the user typed, made visible to OMT receivers, falling back to the model. */
    private String normalizedName() {
        String name = OMTStreamName.normalize(nameField.getText().toString());
        if (name == null) {
            name = defaultName();
        }
        nameField.setText(name);
        return name;
    }

    private int selectedQuality() {
        int position = clamp(qualitySpinner.getSelectedItemPosition(), QUALITIES.length);
        return QUALITIES[position];
    }

    private int selectedRate() {
        int position = clamp(rateSpinner.getSelectedItemPosition(), RATES.length);
        return RATES[position];
    }

    private void persistSettings() {
        getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                .putString(PREF_NAME, normalizedName())
                .putInt(PREF_QUALITY, clamp(qualitySpinner.getSelectedItemPosition(),
                        QUALITIES.length))
                .putInt(PREF_RATE, clamp(rateSpinner.getSelectedItemPosition(), RATES.length))
                .apply();
    }

    private void requestGoLive() {
        List<String> needed = new ArrayList<>();
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            needed.add(Manifest.permission.RECORD_AUDIO);
        }
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            needed.add(Manifest.permission.POST_NOTIFICATIONS);
        }

        if (!needed.isEmpty()) {
            requestPermissions(needed.toArray(new String[0]), REQ_PERMISSIONS);
        } else {
            askForCapture();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions,
                                           int[] grantResults) {
        if (requestCode == REQ_PERMISSIONS) {
            // Whatever the answer: sound may be off, but the session still goes live.
            askForCapture();
        } else {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        }
    }

    private void askForCapture() {
        MediaProjectionManager manager =
                (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        if (manager == null) {
            return;
        }
        startActivityForResult(manager.createScreenCaptureIntent(), REQ_CAPTURE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode != REQ_CAPTURE) {
            super.onActivityResult(requestCode, resultCode, data);
            return;
        }
        if (resultCode != RESULT_OK || data == null) {
            return;   // a refusal leaves the screen exactly as it was
        }

        persistSettings();
        Intent start = new Intent(this, CaptureService.class)
                .setAction(CaptureService.ACTION_START)
                .putExtra(CaptureService.EXTRA_RESULT_CODE, resultCode)
                .putExtra(CaptureService.EXTRA_DATA, data)
                .putExtra(CaptureService.EXTRA_NAME, normalizedName())
                .putExtra(CaptureService.EXTRA_QUALITY, selectedQuality())
                .putExtra(CaptureService.EXTRA_FPS, selectedRate());
        startForegroundService(start);
    }

    private void updateNetworkName() {
        String name = "Wi-Fi";
        ConnectivityManager manager =
                (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (manager != null) {
            Network network = manager.getActiveNetwork();
            NetworkCapabilities capabilities =
                    network == null ? null : manager.getNetworkCapabilities(network);
            if (capabilities != null
                    && capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) {
                name = "Ethernet";
            } else if (capabilities != null
                    && capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                WifiManager wifi =
                        (WifiManager) getApplicationContext().getSystemService(WIFI_SERVICE);
                String ssid = wifi == null ? null : wifi.getConnectionInfo().getSSID();
                if (ssid != null && ssid.length() >= 2 && ssid.startsWith("\"")
                        && ssid.endsWith("\"")) {
                    ssid = ssid.substring(1, ssid.length() - 1);
                }
                if (ssid != null && !ssid.isEmpty() && !"<unknown ssid>".equals(ssid)) {
                    name = ssid;
                }
            }
        }
        networkText.setText(name);
    }
}