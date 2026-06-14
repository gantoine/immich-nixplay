# Immich Android TV

## Nixplay frame build

This build adds support for running on a [Nixplay](https://www.nixplay.com/) digital photo frame
(based on work from [smerschjohann/Immix](https://github.com/smerschjohann/Immix)). On top of the
standard Immich Android TV app it includes:

- A motion sensor that turns the display on/off. The idle timeout is configured under
  **Settings → View Settings → WakeLock** (default: 15 minutes, or "Always on" to never sleep).
  On non-Nixplay hardware the sensor reports "always active", so the display is never turned off.
- The remote's **POWER** button (or `F1`) toggles the screen on/off.
- The app registers as a HOME/launcher app so the frame can boot straight into it.
- Firebase (Crashlytics/Analytics) and Google Cast are removed, so the app builds without a
  `google-services.json` and does not depend on Google Play Services.

> Note: the motion sensor needs access to the frame's GPIO, so the app must be installed as a
> **system app**. It reads the native `gpio_jni` library and only activates when `/etc/nix.model`
> exists; otherwise it falls back gracefully.

### Building the release APK

The release build is signed with a keystore. The signing config reads these environment
variables (each falls back to an unusable dummy default, so set them explicitly):

```bash
# One-time: generate a self-signed keystore. Since the app is installed as a system app,
# the signing identity does not need to match anything — any key works.
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

The signed APK is written to `app/build/outputs/apk/release/ImmichTV-<version>.apk`.

> Keep `app/keystore` out of version control (it is gitignored). The keystore password must be
> at least 6 characters — the built-in `dummy` fallback is too short and will fail to sign.

### Installing on the frame (W10E model example)

> Disclaimer: improper handling or software errors may damage your device. You alone are
> responsible for any damage to hardware or software.

1. Disable the original software:
   ```bash
   adb shell
   $ su
   $ pm disable com.kitesystems.nix.prod & pm disable com.kitesystems.nix.frame
   ```
2. Enable ADB over the network so you can reach the device later without opening the frame:
   ```bash
   adb shell
   $ su
   $ setprop persist.adb.tcp.port 6666
   ```
3. Install the app as a system app:
   ```bash
   adb root && adb remount && \
   adb push app/build/outputs/apk/release/ImmichTV-*.apk /system/app/immich.apk && adb reboot
   ```
4. Set up your credentials. You can mirror the screen with [scrcpy](https://github.com/Genymobile/scrcpy),
   or send the host via adb (`adb shell input text https://demo.immich.app`).
5. Open Android Settings (`adb shell am start -a android.settings.SETTINGS`) and set the display to
   turn off after the shortest time, or use `adb shell settings put system screen_off_timeout 1`
   for the minimum.

---

Immich is a self hosted backup solution for photos and videos. Current features include:

- Upload and view videos and photos
- Auto backup when the app is opened
- Selective album(s) for backup
- Multi-user support
- Album and Shared albums

More info here: https://github.com/immich-app/immich

This Android TV app will allow you to view those uploaded photos and videos. Current features
include:

| Features                                                                       | Status |
|:-------------------------------------------------------------------------------|--------|
| Sign in by phone (https://github.com/giejay/Immich-Android-TV-Authentication)  | Done   |
| Sign in by entering API key                                                    | Done   |
| Demo environment                                                               | Done   |
| Album fetching + Lazy loading                                                  | Done   |
| Showing the photos inside an album                                             | Done   |
| Showing people, random, recent or seasonal photos                              | Done   |
| Slideshow of the photos and videos with a configured interval                  | Done   |
| Setting the app as the screensaver                                             | Done   |
| Setting the albums to show in the screensaver                                  | Done   |
| Configure the interval of the screensaver                                      | Done   |
| Add generic sorting of albums and photos                                       | Done   |
| Add sorting for specific album (select last item in row and press right again) | Done   |
| Showing the 4K thumbnail instead of the full image to speed up loading         | Done   |
| Showing the EXIF data and improving the slideshow view                         | Done   |
| Configure whether to play sound with videos                                    | Done   |
| Smarter merging of portrait photos (same people, same date, same city)         | Todo   |
| Add transitions to slideshow                                                   | Todo   |
| Add places/tags view                                                           | Todo   |
| Add background media playing info to screensaver                               | Todo   |
| Casting capabilities                                                           | Todo   |
| Searching in and for albums                                                    | Todo   |
| Dependency injection with Hilt/Dagger                                          | Todo   |

## Required API Permissions

When setting up your API key in Immich, make sure to grant the following permissions for the app to function properly:

- `album.read` - Read album information
- `activity.read` - Read activity data
- `asset.read` - Read asset metadata
- `asset.view` - View assets (photos/videos)
- `asset.download` - Download assets for viewing
- `album.download` - Download album content
- `archive.read` - Read archived items
- `face.read` - Read face detection data
- `folder.read` - Read folder view (required for the "Folders" screen)
- `library.read` - Read library information
- `timeline.read` - Read timeline data
- `memory.read` - Read memory/moment data
- `partner.read` - Read partner sharing data
- `person.read` - Read person/people data
- `session.read` - Read session information
- `tag.read` - Read tag information
- `tag.asset` - Read asset tag associations

## Screenshots

|                                                                                    |                                                                      |                                                                                    |
|:----------------------------------------------------------------------------------:|:--------------------------------------------------------------------:|:----------------------------------------------------------------------------------:|
|        ![Alt text](/screenshots/homescreen-1.png?raw=true "Album overview")        |      ![Alt text](/screenshots/photos.png?raw=true "All photos")      |      ![Alt text](/screenshots/sorting-options.png?raw=true "Sorting options")      |
|         ![Alt text](/screenshots/home-edit.png?raw=true "Edit homescreen")         | ![Alt text](/screenshots/settings-view.png?raw=true "View settings") | ![Alt text](/screenshots/settings-screensaver.png?raw=true "Screensaver settings") |
| ![Alt text](/screenshots/screensaver-portrait.png?raw=true "Screensaver portrait") |        ![Alt text](/screenshots/people.png?raw=true "People")        |             ![Alt text](/screenshots/seasonl.png?raw=true "Seasonal")              |

## Build steps

1. Clone project with `git clone --recurse git@github.com:giejay/Immich-Android-TV.git`
2. Create an account at firebase and create a google-services.json file, or
   `cp apps/google-services.example apps/google-services.json`
3. copy app/src/strings_other.xml.example to app/src/main/res/values/strings_other.xml and modify
   the address and API keys for your demo server.
4. Build apk with `./gradlew assembleRelease`

## Support the project

You can support the project in several ways. The first one is by creating nice descriptive bug
reports if you find any: https://github.com/giejay/Immich-Android-TV/issues/new/choose.
<br><br>Even better is creating a PR: https://github.com/giejay/Immich-Android-TV/pulls.
<br><br>
Lastly, if you feel this Android TV app is a useful addition to the already great Immich app, you
might consider buying me a coffee or a beer:

[!["Buy Me A Coffee"](https://www.buymeacoffee.com/assets/img/custom_images/orange_img.png)](https://www.buymeacoffee.com/giejay)

## FAQ

#### I'n not able to set the app as a screensaver

1. Enable development mode on the device (click the build number or "Android TV OS Build" 7 times in
   the System->About settings).
2. Go to System -> Developer Options and enable USB Debugging.
3. If you don't have ADB installed on your PC, follow these
   instructions: https://www.xda-developers.com/install-adb-windows-macos-linux/
4. After downloading/installing ADB on the PC, connect to the device using it's IP: adb connect
   192.168.xx.xx.
5. Once you are connected, execute the following command: 'adb shell settings put secure
   screensaver_components nl.giejay.android.tv.immich/.screensaver.ScreenSaverService'
6. Done!
