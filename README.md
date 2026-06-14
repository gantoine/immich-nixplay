# immich-nixplay

[Immich](https://github.com/immich-app/immich) Android TV for
[Nixplay](https://www.nixplay.com/) digital photo frames, **including older models still on
Android 4.4 (KitKat / API 19)**. A fork of
[Immich Android TV](https://github.com/giejay/Immich-Android-TV) plus the frame work from
[smerschjohann/Immix](https://github.com/smerschjohann/Immix).

If you have a newer frame or any Android TV device on **API 21+**, the upstream app works as-is and
most of this README's frame-specific caveats don't apply to you.

## What this fork adds

- **Runs on Android 4.4 (API 19).** Older frames (e.g. the `w10a6`, on Android 4.4.4) can't install
  the stock app, which requires API 24. This fork lowers `minSdk` to 19 and ports everything that
  required a newer OS: legacy ExoPlayer 2.x for video, OkHttp 3.12 + a TLS 1.2 socket factory,
  AndroidX downgrades, legacy multidex, and assorted KitKat runtime fixes.
- **Frame integration:**
  - A motion sensor turns the display on/off. Idle timeout is under
    **Settings → View Settings → WakeLock** (default 15 min, or "Always on"). On non-Nixplay
    hardware the sensor reports "always active" so the screen never sleeps.
  - The remote's **POWER** button (or `F1`) toggles the screen.
  - The app registers as a **HOME / launcher** app so the frame boots straight into it.
- **No Google dependencies.** Firebase (Crashlytics/Analytics), Google Cast, Play Billing
  (donations) and Play Services auth are removed, so it builds without `google-services.json` and
  runs on frames that have no Google Play Services.

> The motion sensor needs the frame's GPIO, so the app must be installed as a **system app**. It
> reads the native `gpio_jni` library and only activates when `/etc/nix.model` exists; otherwise it
> falls back gracefully.

## Know your frame first

Frame models run different Android versions, and that changes everything below. Check yours:

```bash
adb connect <frame-ip>:5555           # or :6666 once you've set the custom port (see below)
adb shell getprop ro.build.version.release   # e.g. 4.4.4
adb shell getprop ro.build.version.sdk       # e.g. 19  -> KitKat, all caveats apply
adb shell getprop ro.product.model           # e.g. w10a6
```

If `sdk` is **19 or 20**, read the [Networking & TLS](#networking--tls-on-android-44-frames)
section before anything else — it's the single most likely thing to block you.

## Building the release APK

The release build is signed with a keystore, read from these environment variables (each has an
unusable dummy default, so set them explicitly):

```bash
# One-time: generate a self-signed keystore. The app is a system app, so the signing identity
# doesn't need to match anything — any key works. Password must be at least 6 characters.
keytool -genkeypair -v -keystore app/keystore -alias immich -keyalg RSA -keysize 2048 \
  -validity 10000 -storepass immixframe -keypass immixframe \
  -dname "CN=Immix Frame, OU=Dev, O=Immix"

# Build the signed release APK
RELEASE_KEYSTORE_PATH="$(pwd)/app/keystore" \
RELEASE_KEYSTORE_PASSWORD=immixframe \
RELEASE_KEY_ALIAS=immich \
RELEASE_KEY_PASSWORD=immixframe \
  ./gradlew assembleRelease
```

The signed APK lands at `app/build/outputs/apk/release/ImmichTV-<version>.apk`. `app/keystore` is
gitignored — keep it out of version control.

No `google-services.json` or `strings_other.xml` is needed (this fork removed those dependencies).

## Installing on the frame

> Disclaimer: improper handling or software errors may damage your device. You alone are
> responsible for any damage to hardware or software.

### 1. Prepare the frame (one-time)

```bash
adb shell
$ su
# Stop the stock software so Immich can become the HOME app:
$ pm disable com.kitesystems.nix.prod & pm disable com.kitesystems.nix.frame
# Keep network adb available across reboots without opening the frame:
$ setprop persist.adb.tcp.port 6666
```

### 2. First install — push as a system app

A system-app install is required for the motion sensor's GPIO access and to act as HOME.

```bash
adb root                              # restarts adbd; the TCP connection drops...
adb connect <frame-ip>:6666           # ...so reconnect
adb remount
adb push app/build/outputs/apk/release/ImmichTV-*.apk /system/app/immich.apk
adb shell chmod 644 /system/app/immich.apk
adb reboot
```

> **Heads-up:** `adb root` always drops a TCP adb connection — reconnect after it. And re-pushing to
> `/system/app` then rebooting can **wipe the app's data** (your saved login). For that reason, use
> `install -r` for updates, not another `/system/app` push:

### 3. Updating — `adb install -r` (no reboot, keeps your login)

```bash
adb install -r app/build/outputs/apk/release/ImmichTV-*.apk
adb shell am start -n nl.giejay.android.tv.immich/.MainActivity   # relaunch
```

`install -r` updates the system app in place: it preserves app data, keeps system-app status, and
needs no reboot. This is the fast iteration loop.

### 4. Point the app at your Immich server

On first launch you'll get the sign-in screen. Enter your **server URL** and an **API key** (see
[permissions](#required-api-permissions)). On a KitKat frame the URL should almost always be a
**LAN HTTP** address — see the next section for why.

The on-screen keyboard often doesn't cooperate with frame remotes. You can type via adb instead:

```bash
adb shell input keyevent 123                          # cursor to end of the focused field
for i in $(seq 60); do adb shell input keyevent 67; done   # backspace to clear it
adb shell input text 'http://192.168.1.20:2283'       # type the URL (no spaces)
```

Or mirror the screen with [scrcpy](https://github.com/Genymobile/scrcpy) and use a real keyboard.

### 5. Display sleep

Set the panel to sleep quickly (the motion sensor / WakeLock handles waking):

```bash
adb shell settings put system screen_off_timeout 1
```

## Networking & TLS on Android 4.4 frames

**This is the gotcha that bites everyone with an API-19 frame.** KitKat's TLS stack only offers old
cipher suites (CBC-SHA1); it has none of the modern AEAD ciphers (AES-GCM, ChaCha20) or TLS 1.3.
Modern reverse proxies — Let's Encrypt with a Mozilla "intermediate"/"modern" config, Caddy, recent
nginx, Cloudflare — typically **only** accept those modern ciphers. The result: **the frame and a
modern HTTPS endpoint share no cipher, and the connection fails** with an SSL handshake error. No
client-side change can fix that, because API 19 simply doesn't implement the required crypto.

The app does enable TLS 1.2 and every cipher the device supports (it's off by default on KitKat), so
HTTPS works against servers that still accept a legacy cipher — but most modern setups don't.

**Recommended fix: point the frame at your Immich server's LAN HTTP endpoint**, e.g.
`http://192.168.1.20:2283` (2283 is Immich's default port). Cleartext HTTP has no TLS to negotiate,
works reliably on the LAN, and API 19 imposes no cleartext-traffic restriction. This is the simplest
and most robust option for a frame that lives on the same network as the server.

Check whether your server is reachable over HTTPS from a KitKat client (run from any machine):

```bash
# If every line says "REJECTED", an API-19 frame cannot reach this server over HTTPS — use LAN HTTP.
H=photos.example.com
for c in ECDHE-RSA-AES128-SHA ECDHE-RSA-AES256-SHA AES128-SHA AES256-SHA; do
  out=$(echo | openssl s_client -connect $H:443 -tls1_2 -cipher $c -servername $H 2>/dev/null \
        | grep -E 'Cipher *:' | head -1)
  printf '%-22s %s\n' "$c" "${out:-REJECTED}"
done
```

Alternatives if you must use HTTPS: serve a legacy-cipher endpoint for the frame on a separate
hostname, or front Immich with a LAN-only reverse proxy that offers a KitKat-compatible cipher.

## Required API permissions

Create an API key in Immich (Account Settings → API Keys) with these permissions:

- `album.read`
- `album.download`
- `activity.read`
- `asset.read`
- `asset.view`
- `asset.download`
- `archive.read`
- `face.read`
- `folder.read`
- `library.read`
- `timeline.read`
- `memory.read`
- `partner.read`
- `person.read`
- `session.read`
- `tag.read`
- `tag.asset`

You can edit an existing key to add a missing permission without recreating it. Note that
`asset.view` is what serves thumbnails/images and `folder.read` is required for the Folders screen.

## Known limitations on Android 4.4 frames

- **HTTPS to modern servers won't work** — use a LAN HTTP endpoint (see above).
- **Video playback is hardware-decode only.** The media3 FFmpeg software-decoder extension can't run
  on KitKat and was dropped; playback uses legacy ExoPlayer 2.x with the frame's hardware decoders,
  so exotic codecs may not play.
- **No donations / Play Billing** (removed; the frame has no Google Play).

## Debugging crashes on the frame

Release builds **do not plant Timber**, so the app's own logs are silent. To diagnose:

```bash
# Live crash stack:
adb logcat AndroidRuntime:E '*:S'

# Full trace (incl. the root "Caused by") survives in Android's DropBox even when the deep
# stack is truncated in logcat. Entries are gzipped:
adb shell 'ls /data/system/dropbox/' | grep crash | sort | tail -1     # newest entry
adb shell cp /data/system/dropbox/<entry>.txt.gz /data/local/tmp/c.gz
adb pull /data/local/tmp/c.gz /tmp/c.gz && gunzip -f /tmp/c.gz && less /tmp/c
```

> The KitKat shell is minimal — `pidof`, `sed`, `head`, `curl` and friends are absent. Pipe to your
> host machine for filtering, and `adb pull` (binary-safe) rather than `cat` for gzipped files.

---

## About Immich

Immich is a self-hosted backup solution for photos and videos: https://github.com/immich-app/immich

This Android TV app views those photos/videos. Features:

| Feature                                                                        | Status            |
|:-------------------------------------------------------------------------------|-------------------|
| Sign in by entering API key                                                    | Done              |
| Album fetching + lazy loading                                                  | Done              |
| Showing the photos inside an album                                             | Done              |
| Showing people, random, recent or seasonal photos                              | Done              |
| Folder view                                                                    | Done              |
| Slideshow of photos and videos with a configured interval                      | Done              |
| Setting the app as the screensaver + choosing albums/interval                  | Done              |
| Generic sorting of albums and photos                                           | Done              |
| Per-album sorting (select last item in row and press right again)              | Done              |
| HD-thumbnail mode to speed up loading                                          | Done              |
| EXIF metadata overlay in the slideshow                                         | Done              |
| Configure whether to play sound with videos                                    | Done              |
| Sign in by phone / demo environment                                            | Removed (fork)    |
| Casting                                                                        | Removed (fork)    |

## Screenshots

|                                                                                    |                                                                      |                                                                                    |
|:----------------------------------------------------------------------------------:|:--------------------------------------------------------------------:|:----------------------------------------------------------------------------------:|
|        ![Alt text](/screenshots/homescreen-1.png?raw=true "Album overview")        |      ![Alt text](/screenshots/photos.png?raw=true "All photos")      |      ![Alt text](/screenshots/sorting-options.png?raw=true "Sorting options")      |
|         ![Alt text](/screenshots/home-edit.png?raw=true "Edit homescreen")         | ![Alt text](/screenshots/settings-view.png?raw=true "View settings") | ![Alt text](/screenshots/settings-screensaver.png?raw=true "Screensaver settings") |
| ![Alt text](/screenshots/screensaver-portrait.png?raw=true "Screensaver portrait") |        ![Alt text](/screenshots/people.png?raw=true "People")        |             ![Alt text](/screenshots/seasonl.png?raw=true "Seasonal")              |

## FAQ

#### Setting the app as a screensaver

```bash
adb connect <frame-ip>:6666
adb shell settings put secure screensaver_components \
  nl.giejay.android.tv.immich/.screensaver.ScreenSaverService
```

On a regular Android TV device, first enable Developer Options (click the build number 7×) and USB
debugging, then run the same command.

## Credits & support

Built on [giejay/Immich-Android-TV](https://github.com/giejay/Immich-Android-TV) and the frame work
in [smerschjohann/Immix](https://github.com/smerschjohann/Immix). Please support the upstream author:

[!["Buy Me A Coffee"](https://www.buymeacoffee.com/assets/img/custom_images/orange_img.png)](https://www.buymeacoffee.com/giejay)
