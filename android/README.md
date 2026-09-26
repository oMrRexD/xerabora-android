# xeRAbora for Android

*Português: [README.pt-BR.md](README.pt-BR.md).*

The xeRAbora client running entirely on the phone, no PC: the PS2 running OPL-RA (or a loader that
carries the same agent) finds the phone on the Wi-Fi, and the phone talks to RetroAchievements, unlocks
the achievements and shows the same page as the PC client.

## Install and use

1. Download the APK from the [latest release](https://github.com/oMrRexD/xerabora-android/releases/latest)
   and install it. The first time, Android asks you to allow installs from the browser.
2. Open the app. It asks for notifications and for running without battery optimization. Allow both:
   that is what keeps the client alive with the screen off in the middle of a game.
3. On the page's **SETTINGS** tab (**AJUSTES** in Portuguese and Spanish), sign in to RetroAchievements
   and paste the Web API key, as on the PC. The page opens in the phone's language (English, Portuguese
   or Spanish); the menu in its header changes it.
4. On the PS2, with the phone on the **same Wi-Fi** as the console: **RA: test PC connection** has to show
   the phone's address. Then **RA: check game support**, then the game.

The status notification shows what the console is doing: idle, found, the running game with its icon,
achievements, points and session time. Every unlock gets a notification of its own, and the link test,
"check game support" and a game starting show up as pop-ups. **Quit** in the notification, or
**QUIT** on the page, ends the client. The back button asks: **Minimize** keeps it running, **Quit** turns
it off, and **Send log** hands the end of `xerabora.log` to any app (for a bug report without a PC).

With the screen on during *test PC connection* and while the game starts, it works on any phone. The
console finds the client by broadcast, and the app holds a *multicast lock* to receive it with the
screen off, but some phones filter broadcasts all the same.

## Updates

- **The app offers them.** When it opens (at most every 6 h) it looks at the latest release and offers
  **Install**. The first time, Android asks to allow installs from xeRAbora. The login is kept.
- **This fork follows upstream by itself.** Every day the `sync-upstream` workflow merges
  `hacan359/xerabora`, and `android` builds and publishes the new APK. Tags are
  `android-<version>-r<build>`; the build number is the commit count, so it only goes up.

## How the port works

No upstream file is modified. Everything Android is a new file, so the daily merge never conflicts:

| File | What it does |
|---|---|
| `app/src/main/cpp/CMakeLists.txt` | Reads the `SRC` and `RC_SRC` lists from `client/Makefile`, so a file added upstream is built here too. Compiles `main.c` as `xerabora_main`. |
| `http_android.c` | `http.h` through `HttpURLConnection` (the system's TLS, instead of libcurl). |
| `sound_android.c` | `sound.h` through `SoundPool` (instead of paplay/aplay). WAVs in `sounds/` replace the defaults, as on the PC. |
| `hooks.c` | `-Wl,--wrap`: the client does not quit 15 s after its page closes (the service decides), and instead of opening a browser it tells the app the port. |
| `status.c` | `-Wl,--wrap` on the calls the client already makes to fill its page (console, game, status, unlock, push) and on `console_serve`: this feeds the notifications. It also reads the `RAA1` answers to "RA: check game support". |
| `net_guard.c` | A send to the page waits at most 250 ms. Android freezes the app's UI process in the background, and `webui.c`'s blocking `send()` then stalled the whole client (no telemetry, no discovery, no unlocks). |
| `jni_bridge.c` | `HOME` in the app's directory, stdout to logcat, then `main`. |
| `StatusNotifier.java` | The status notification (console, game, progress, time), one per unlock, and the pop-ups (PS2 connected, game recognized, game started). |
| `EngineService.java` | Foreground service in its own `:engine` process (the C code keeps static state, so every start is a new process), with the wake, Wi-Fi and multicast locks. |
| `MainActivity.java` | The client's page in a WebView on `127.0.0.1:18280`, which counts as "this machine", so login and QUIT work. |
| `UpdateChecker.java` | The in-app updater. |
| `tools/fake_ps2.py` | Plays the console's side of discovery, to test a phone without the PS2. |
| `tools/make_icons.py` | Rebuilds the launcher icon from `docs/icon.png`. |

**If the build breaks after a merge,** GitHub emails you about the failing `android` workflow. The
places where the port touches upstream are few:

- the `NAME := ...` assignments in `client/Makefile`;
- the `http.h` and `sound.h` APIs;
- the wrapped functions (`webui_page_gone`, `webui_open_browser`, `webui_set_console`, `webui_set_game`,
  `webui_set_game_title`, `webui_set_status`, `webui_note_unlock`, `webui_push`, `console_serve`), which
  must keep being called from other files than the one that defines them. They are declared with the
  header's own types, so a changed signature fails the build instead of miscalling;
- the `RAA1` reply format, which is part of `protocol/PROTOCOL.md`.

## Build

Needs JDK 21, the Android SDK (platform 36), NDK 28.2.13676358 and CMake 3.22.1.

```
git submodule update --init third_party/rcheevos
cd android
./gradlew assembleDebug
```

The APK lands in `android/app/build/outputs/apk/debug/`. The log: `adb logcat -s xerabora`. The full
`xerabora.log` is in `files/.config/xerabora/` in the app's storage.

Repository secrets the CI uses: `ANDROID_KEYSTORE_B64`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`
and `ANDROID_KEY_PASSWORD` (signing; without them no release is published) and `SYNC_TOKEN` (the daily
merge).
