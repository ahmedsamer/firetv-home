# firetv-home

A tvOS-style home screen for the Amazon Fire TV Cube (3rd gen, Fire OS 7.7 / Android 9): one row of your own apps
over a sky wallpaper, with no ads and no Amazon rows. Each app gets a logo tile in its brand colour and glows in
that colour when selected. There's a 12-hour clock top left and the weather from Open-Meteo top right.

Based on `firehome` from [debloat-fire-stick-claude-code](https://github.com/whyashthakker/debloat-fire-stick-claude-code)
and [CleanYourTV](https://github.com/Gathin23/cleanyourtv) (MIT, see `LICENSE`).

## How it works

Fire OS doesn't let another app take over the Home button, and Amazon's launcher can't be disabled. So:

- **`HomeWatcher`**, a foreground service, follows the system log (`READ_LOGS`, granted over ADB) and opens this
  screen whenever Amazon's home is started.
- **Overlay:** Android holds back app launches for 5 s after a Home press. A full-screen window showing a picture
  of this screen (`SYSTEM_ALERT_WINDOW`) covers Amazon's home within ~0.1 s, until the real screen arrives.
  Remote keys are swallowed while it shows, Back removes it, and it always goes after 8 s.
- **`BootReceiver`** starts the watcher at boot and after every update.

Buttons: **Appstore** (the store front), **Settings** (Fire TV settings) and **Fire TV home** (Amazon's home, with
the takeover paused for 10 minutes).

## Build

Needs only a JDK and two zips from Google's SDK repository (no Android Studio):

```bash
SDK=$HOME/android-sdk; mkdir -p $SDK/build-tools $SDK/platforms
curl -LO https://dl.google.com/android/repository/build-tools_r34-linux.zip
curl -LO https://dl.google.com/android/repository/platform-28_r06.zip
unzip -q build-tools_r34-linux.zip -d $SDK/build-tools
unzip -q platform-28_r06.zip -d $SDK/platforms
ANDROID_HOME=$SDK bash build.sh          # -> build/firehome.apk, signed with ~/.android/debug.keystore
```

## Install (ADB debugging on, TV at TV_IP)

```bash
adb connect TV_IP:5555
adb install -r build/firehome.apk
adb shell pm grant com.firehome android.permission.READ_LOGS
adb shell appops set com.firehome SYSTEM_ALERT_WINDOW allow
adb shell am start -n com.firehome/.HomeActivity
```

Remove: `adb shell pm uninstall com.firehome`. Amazon's home is back on the next Home press.

## Customise (`src/com/firehome/`)

- `HomeActivity.PINNED`: the order of apps in the row. Apps not listed are added after them, by name.
- `HomeActivity.ABOUT`: the caption and line shown above the row for each app.
- `HomeActivity.LOGO_TILES`: tile colours and style per app (`LOGO`, `WHITE`, `MASK`, `ICON`), and `LOGO_WEIGHT`
  for optical size tweaks.
- `Weather.URL`: your latitude and longitude.

## Known limits

- Fire OS never passes the Home key to accessibility services, so the 5 s hold can only be hidden, not removed.
- Settings opens inside Amazon's launcher, Amazon's banner included: on Fire OS 7 that's where Settings lives.
