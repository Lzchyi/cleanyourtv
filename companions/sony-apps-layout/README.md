# Optional Sony Apps layout companion

This companion is part of Lzchyi's personal launcher fork, not an upstream module-card add-on. Tested on Sony KD-55X9000H, Android 12, STT2.230505.001.S137 / 626241, with Sony TV VRC 001 remote vendor 054c/product 0c99. Other devices are untested. It has no Internet permission, accessibility service, Android background service or telemetry. It declares no requested permissions; its exported setup receiver requires the caller to hold WRITE_SECURE_SETTINGS.

## Why it exists

The accessibility key-filter path suppresses native held Volume on this firmware. The kernel reports Volume press/release while the helper receives neither. Keeping that helper disabled restores repeat. This companion supplies a saved keyboard-layout overlay mapping only Apps scan 580 / HID usage 0x000c0544 to CALCULATOR; other mappings are inherited. Android launches the fork's direct Library alias. A real keyboard's Calculator key would also open Library. No pre-existing Calculator handler was found on the tested TV; check your own device for conflicts.

The owner confirmed Apps from Home and another app, Back, native held Volume and removal of the Home flash. Saved layout selection needs no resident watcher or Mac connection. **Restart, sleep/wake and Bluetooth reconnect persistence remain untested.** Android can apply the selection to another remote with the same vendor/product identity.

## Build

Build the launcher first using JDK 17, SDK platform 34 and build-tools 34.0.0. Set JAVA_HOME and ANDROID_HOME, then run from the repository root:

```sh
python3 companions/sony-apps-layout/build.py
```

Outputs are build/SonyAppsLayoutTrial.apk and build/layoutctl.dex inside this folder. The APK uses the standard Android debug keystore from ANDROID_USER_HOME (or ~/.android). Preserve the signing key to update an existing installation. No key or compiled output is included here. The checked-in source is the tested provider/fallback and layout controller; this portable build wrapper only replaces the private workstation paths.

## Opt-in setup and rollback

This is an advanced manual setup, not part of the upstream cleanup prompt. Record the actual remote, current/enabled layout selection, default Home and accessibility configuration first. Do not overwrite unrelated accessibility services or layout choices. The source's LayoutCtl shell client uses normal InputManager service APIs with the shell's SET_KEYBOARD_LAYOUT permission, not root or direct system-file edits. Its inspect output contains a device descriptor; keep it private.

Install the companion only with the owner's authorization, then push the generated layoutctl.dex to /data/local/tmp/tvkeys-layoutctl.dex. With ADB already connected, inspect registration and the current selection:

```sh
adb shell 'CLASSPATH=/data/local/tmp/tvkeys-layoutctl.dex app_process /system/bin LayoutCtl inspect'
adb shell 'CLASSPATH=/data/local/tmp/tvkeys-layoutctl.dex app_process /system/bin LayoutCtl layouts'
```

The launcher has a default-disabled LibraryKeyActivity alias. This Sony rejects shell component enable/disable, so the app changes its own component through the explicitly permission-checked setup provider:

```sh
adb shell content call --uri content://com.gathin.tvhome.theme --method library_key_route --arg enable
adb shell am broadcast --receiver-foreground -n com.lzchyi.tvkeys/.Layouts -a com.lzchyi.tvkeys.SET_FORWARDING --ez enabled false
adb shell cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.APP_CALCULATOR
```

The final command must resolve to com.gathin.tvhome/.LibraryKeyActivity with no chooser. Keep the key-filter helper disabled and verify InputFilterEnabled=false. The controller supports a bounded 5–180 second trial and saves its baseline before applying a layout. Run it in a shell kept open for the whole trial; a detached watchdog requires its own startup handshake and disconnect-survival check before relying on it:

```sh
adb shell 'CLASSPATH=/data/local/tmp/tvkeys-layoutctl.dex app_process /system/bin LayoutCtl trial com.lzchyi.tvkeys/com.lzchyi.tvkeys.Layouts/apps_global /data/local/tmp/tvkeys-trial.json 180'
```

Use a fresh state path. Before keeping it, physically check held Volume Up/Down, stop-on-release, Apps from Home/another app and Back. An unconfirmed trial restores automatically while its process remains alive. A commit marker at the state path plus .commit retains the mapping; .cancel requests restoration. Never treat injected keys as physical remote proof. Our device session verified detached watchdog survival and automatic baseline restoration before accepting the mapping.

To undo a retained trial, retain the companion and baseline JSON while applying the reverse overlay:

```sh
adb shell 'CLASSPATH=/data/local/tmp/tvkeys-layoutctl.dex app_process /system/bin LayoutCtl restore /data/local/tmp/tvkeys-trial.json'
adb shell content call --uri content://com.gathin.tvhome.theme --method library_key_route --arg disable
```

Verify the original selection/list and button behavior before disabling the companion. Merely clearing the selection or removing the app is insufficient: Android combines the overlay into the live map, so the explicit apps_original reverse mapping is applied first. A neutral overlay filename may remain in memory until the remote reopens. Preserve unrelated layouts and keep private baseline files out of Git.

The earlier forwarding fallback retains both physically tested controls but can briefly show Home. To select it, disable the launcher's direct alias and set the companion receiver's enabled extra to true. No setup switch changes volume or accessibility. Disable debugging after device work.

[Launcher guide](../../docs/premium-redesign.md) · [Changelog](../../CHANGELOG.md). MIT, with the repository's existing license and upstream attribution.
