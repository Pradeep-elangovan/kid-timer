# Kid Timer

A tiny Android app that locks the phone after a set amount of screen-on time, so a kid can't overuse it.

## What it does

- Choose **Off, 15, 30, 45 or 60 minutes**.
- Counts time the screen is on **and the phone is unlocked**.
- Shows a toast **one minute before** locking.
- When time is up, it **locks the phone**. The count restarts at the next unlock.
- Your choice (including Off) is **remembered** across locks and reboots until you change it.
- Includes a **Quick Settings tile** (pull-down drawer) that cycles Off, 15, 30, 45, 60. Changing it from the lock screen asks for your unlock first.

## Get the APK

1. Open the **Actions** tab on GitHub and pick the latest **Build APK** run.
2. Download the **kid-timer-apk** artifact (a zip) and unzip it.
3. Copy `app-debug.apk` to your phone and install it (allow "install unknown apps" if asked).

You can also start a build any time with **Run workflow** on the Actions tab.

## One-time setup on the phone

1. Open **Kid Timer** and allow notifications.
2. Tap **Allow phone lock** and activate the device admin. This is what lets the app lock the screen. It also makes the app harder to uninstall without deactivating it first.
3. Tap **Add to Quick Settings** to put the tile in the drawer (or edit the tiles with the pencil icon and drag in "Screen timer").
4. Tap **Battery: set to Unrestricted** and choose Unrestricted, so Android doesn't stop the timer in the background.
5. Pick a time.

The default on first install is **Off**.

## Notes

- A persistent low-priority notification is shown while a timer is active. Android requires this for any background service.
- A lock only helps while the phone stays locked: if your kid knows your PIN, they can unlock it and the next count starts.

## Build locally

Requires JDK 17, the Android SDK (API 35) and Gradle 8.9:

```
gradle assembleDebug
```

The APK is written to `app/build/outputs/apk/debug/app-debug.apk`.
