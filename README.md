# Bread OMT Screen

Sends an Android phone's screen and game sound to the production as an OMT-HX source. The phone
renders straight into the hardware encoder, so the game is not slowed down by screen mirroring.

**Path to vMix:** phone (OMT-HX) → **BreadCamera or OMT-Bridge** on the PC → vMix. vMix cannot
decode OMT-HX itself; BreadCamera or OMT-Bridge converts it to plain OMT or NDI. This is the same
path Bread OMT Camera's HX mode uses.

## Setup

- Use a **5 GHz or Wi-Fi 6 router**, or a **USB-C Ethernet adapter with charging pass-through**.
- Budget about **15 Mbps per phone** at Medium, 2400×1080, 60 fps. Ten phones need roughly
  **150–300 Mbps** in total.
- Install the app, tap **Go Live**, accept Android's "Start sharing?" prompt, then switch to the
  game. On Android 14+ choose the game app so the status bar and notifications never reach the
  stream. On Android 10–13 the whole screen is shared and the app says so.

## Build

```powershell
./gradlew.bat assembleDebug
```

`ANDROID_HOME` must point at the Android SDK; NDK 27.3.13750724 and CMake 3.22.1 are used. The APK
is `app/build/outputs/apk/debug/app-debug.apk`.

## License and credits

GPL-3.0 (see `LICENSE`). The streaming core comes from **Open Camera OMT by ysdede**.
**libomt** and **libvmx** are MIT, from [Open Media Transport](https://github.com/Open-Media-Transport).

## Owner checklist on a real phone

1. The source appears in BreadCamera / OMT-Bridge after Go Live.
2. The phone shows `Idle: waiting for a viewer` until something connects.
3. The picture keeps the phone's native shape at 60 fps.
4. A real game shows no felt lag compared with not streaming.
5. Sound is in sync.
6. A game that blocks sound shows a flat meter with video still fine.
7. Rotation changes the size cleanly.
8. On Android 14+, choosing the game app only keeps notifications off stream.
9. Ending sharing from Android's own UI stops the app.
10. Refusing Record audio still goes live, with the "Sound is off" copy.
11. Stop, then Go Live again, asks consent and works.