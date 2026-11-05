# Open ScreenShare OMT-HX

Share an Android phone's screen **and game sound** over the network as an **OMT-HX** source (Open Media Transport, High Efficiency). It's built for capturing mobile games into a live production without the frame drops and in-game lag that phone casting causes.

The app is **Bread OMT Screen**. It's the sister of [Open Camera OMT-HX](https://github.com/bmsolutionsvn/opencamera-omthx), which does the same for the phone's camera.

## Features

- **Hardware encoding.** The screen renders straight into the phone's H.265 (HEVC) encoder, or H.264 when the phone has no HEVC encoder. The app never touches the pixels, so the game keeps its frame rate.
- **The phone's own shape.** A 21:9 phone sends 21:9 at its native resolution. When the encoder can't keep up, the size is scaled down evenly, with the shape kept.
- **60 fps** by default, or 30. A still screen keeps sending frames.
- **Game sound** as standard OMT audio (32-bit float, 48 kHz stereo), in sync with the picture.
- **Encodes only while someone watches.** With no viewer connected, nothing is captured or encoded. Encoding stops 5 s after the last viewer leaves.
- **Game-only sharing on Android 14+.** Choose the game app in Android's prompt, and notifications, the status bar and other apps never reach the stream.
- **Automatic discovery.** The phone appears on the network by DNS-SD (`_omt._tcp`) under its name, default `<phone model> (Screen)`.
- **Sound meter** on the phone and a live status line: sent size and rate, viewer count, network.

## How the feed reaches vMix

```
Phone (OMT-HX) ──► BreadCamera or OMT-Bridge on the PC ──► vMix (plain OMT or NDI)
```

vMix can't decode OMT-HX itself. BreadCamera or OMT-Bridge converts it, picture and sound. Any OMT-HX receiver works, for example the OBS `omtplugin` with OMT-HX.

## Requirements

- Android 10 (API 29) or later, arm64-v8a. Game-only sharing needs Android 14.
- A **5 GHz or Wi-Fi 6 router**, or a **USB-C Ethernet adapter with charging pass-through**.
- Bandwidth: about **15 Mbps per phone** at Medium quality, 2400×1080 60 fps. Ten phones need roughly **150–300 Mbps** in total.

## Usage

1. Install the APK and open **Bread OMT Screen**.
2. Set the name, Quality (Low / Medium / High) and Rate (60 / 30).
3. Tap **Go Live** and allow Record audio. Android needs that permission to capture game sound; the microphone is never used.
4. Accept Android's **Start sharing?** prompt. On Android 14+, choose the game app.
5. Switch to the game. The phone shows `Idle: waiting for a viewer` until a receiver connects.
6. Add the source in BreadCamera or OMT-Bridge.

**No sound?** Some games block sound capture. Android then gives silence and no error, and the meter stays flat. The picture is unaffected.

To stop, tap **Stop** in the app or in its notification, or end sharing from Android's own controls.

## Build

```powershell
./gradlew.bat assembleDebug
```

`ANDROID_HOME` must point at the Android SDK. The build uses NDK 27.3.13750724 and CMake 3.22.1, and the APK is `app/build/outputs/apk/debug/app-debug.apk`.

Checks without a phone:

```powershell
./gradlew.bat testDebugUnitTest lintDebug
powershell -NoProfile -ExecutionPolicy Bypass -File qa/run-audio-frame.ps1   # OMT audio bytes, needs MSYS2 g++
```

## Testing on a real phone

1. The source appears in BreadCamera / OMT-Bridge after Go Live.
2. The phone shows `Idle: waiting for a viewer` until something connects.
3. The picture keeps the phone's native shape at 60 fps.
4. A real game shows no felt lag compared with not streaming.
5. Sound is in sync.
6. A game that blocks sound shows a flat meter, with video still fine.
7. Rotation changes the size cleanly.
8. On Android 14+, sharing only the game app keeps notifications off stream.
9. Ending sharing from Android's own controls stops the app.
10. Refusing Record audio still goes live, with the "Sound is off" message.
11. Stop, then Go Live again, asks consent again and works.

## License and credits

GPL-3.0 (see [LICENSE](LICENSE)).

- The streaming core comes from **Open Camera OMT** by [ysdede](https://github.com/ysdede/opencamera-omt), through [Open Camera OMT-HX](https://github.com/bmsolutionsvn/opencamera-omthx).
- **libomt** and **libvmx** are MIT-licensed, from [Open Media Transport](https://github.com/Open-Media-Transport).
