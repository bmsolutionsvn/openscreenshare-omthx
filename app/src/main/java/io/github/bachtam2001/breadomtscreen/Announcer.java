package io.github.bachtam2001.breadomtscreen;

import android.content.Context;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;
import android.net.wifi.WifiManager;
import android.os.PowerManager;
import android.util.Log;

/**
 * The DNS-SD announcement and the locks that keep it alive: multicast (so mDNS is heard), a
 * low-latency Wi-Fi lock, and a partial wake lock so the phone does not sleep mid-stream.
 *
 * Every lock is acquired IDEMPOTENTLY and released in its own try/catch: the leaks this avoids are
 * the ones OMTStreamingManager.registerService/unregisterService document (a re-acquired
 * refcounted multicast lock, a wake lock lost behind an NsdManager exception).
 */
final class Announcer {

    private static final String TAG = "Announcer";
    private static final String SERVICE_TYPE = "_omt._tcp";

    private final Context context;

    private NsdManager nsdManager;
    private NsdManager.RegistrationListener registrationListener;
    private WifiManager.MulticastLock multicastLock;
    private WifiManager.WifiLock wifiLock;
    private PowerManager.WakeLock wakeLock;

    Announcer(Context context) {
        this.context = context.getApplicationContext();
    }

    /** @return true when NsdManager accepted the registration request. */
    boolean start(String name, int port) {
        try {
            WifiManager wifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
            if (wifiManager != null) {
                if (multicastLock == null) {
                    multicastLock = wifiManager.createMulticastLock("OMT_mDNS");
                    multicastLock.setReferenceCounted(true);
                }
                if (!multicastLock.isHeld()) {
                    multicastLock.acquire();
                }

                if (wifiLock == null) {
                    wifiLock = wifiManager.createWifiLock(
                            WifiManager.WIFI_MODE_FULL_LOW_LATENCY, "OMT_Streaming_Lock");
                    wifiLock.setReferenceCounted(false);
                }
                if (!wifiLock.isHeld()) {
                    wifiLock.acquire();
                }
            }

            PowerManager powerManager = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
            if (powerManager != null) {
                if (wakeLock == null) {
                    wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,
                            "OMT:StreamingWakeLock");
                }
                if (!wakeLock.isHeld()) {
                    wakeLock.acquire();
                }
            }

            nsdManager = (NsdManager) context.getSystemService(Context.NSD_SERVICE);
            if (nsdManager == null) {
                Log.e(TAG, "NsdManager unavailable - the device will not be discoverable");
                return false;
            }

            NsdServiceInfo info = new NsdServiceInfo();
            info.setServiceName(name);
            info.setServiceType(SERVICE_TYPE);
            info.setPort(port);

            registrationListener = new NsdManager.RegistrationListener() {
                @Override
                public void onServiceRegistered(NsdServiceInfo serviceInfo) {
                    Log.i(TAG, "NSD service registered: " + serviceInfo.getServiceName());
                }

                @Override
                public void onRegistrationFailed(NsdServiceInfo serviceInfo, int errorCode) {
                    // NsdManager accepted the call and mdnsd refused it. Give back what start()
                    // took rather than leaving the locks held for the life of the process.
                    Log.e(TAG, "NSD registration failed: " + errorCode);
                    stop();
                }

                @Override
                public void onServiceUnregistered(NsdServiceInfo serviceInfo) {
                    Log.i(TAG, "NSD service unregistered");
                }

                @Override
                public void onUnregistrationFailed(NsdServiceInfo serviceInfo, int errorCode) {
                    Log.e(TAG, "NSD unregistration failed: " + errorCode);
                }
            };

            nsdManager.registerService(info, NsdManager.PROTOCOL_DNS_SD, registrationListener);
            Log.i(TAG, "NSD registration initiated for " + name);
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Failed during service registration", e);
            return false;
        }
    }

    void stop() {
        if (nsdManager != null && registrationListener != null) {
            try {
                nsdManager.unregisterService(registrationListener);
            } catch (Exception e) {
                Log.e(TAG, "Failed to unregister NSD service", e);
            }
            registrationListener = null;
        }

        if (wifiLock != null) {
            try {
                if (wifiLock.isHeld()) wifiLock.release();
            } catch (Exception e) {
                Log.e(TAG, "Failed to release WiFi lock", e);
            }
            wifiLock = null;
        }

        if (wakeLock != null) {
            try {
                if (wakeLock.isHeld()) wakeLock.release();
            } catch (Exception e) {
                Log.e(TAG, "Failed to release WakeLock", e);
            }
            wakeLock = null;
        }

        if (multicastLock != null) {
            try {
                if (multicastLock.isHeld()) multicastLock.release();
            } catch (Exception e) {
                Log.e(TAG, "Failed to release multicast lock", e);
            }
            multicastLock = null;
        }
    }
}