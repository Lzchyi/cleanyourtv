# Premium v2.6.0

This personal fork is based on upstream's `v2.6.0` tag (`0bb7f7e`), with application version **2.6.0 / code 16**. It keeps the native Java views, RecyclerView, existing input/vendor handling and fast-return lifecycle. It adds no runtime dependency, Internet permission or new background service. Android 8/API 26 and later remain supported; hardware validation for this redesign is on one Sony KD-55X9000H running Android 12.

## Home and navigation

The clock sits beside a compact greeting/date block. Inputs, Network and Settings are matching icon controls with accessible names; an active VPN keeps a compact green badge. The dock has at most four apps, independently of ordinary grid columns. Existing dock overflow stays among the regular Home apps. A translucent gradient and thin edge simulate glass without live blur.

Focus uses a restrained 1.05× scale. Header focus keeps the wallpaper sharp; dock and lower-row focus soften and dim it progressively. One worker generates a cache of at most 320×320 pixels per wallpaper; decoding that preview is capped at 204,800 pixels. Focus changes animate opacity rather than recompute blur. Allocation/decode failure falls back to dimming. Disabled animations settle immediately; reduced motion shortens or removes effects. Original wallpaper files remain local and unchanged.

The optional **App Library tile** defaults to the first pinned position below the dock and participates in saved app reordering. Its grouped-grid artwork contains no embedded text. It appears at the same size as other Home apps, with only a small next-row preview when visible rows is zero. Select it to open the full-screen, alphabetical Library: six landscape banner columns at Sony 1080p, adapting to narrower screens. Original app banners and icons remain unchanged. Down only moves focus; it never opens the Library. OK opens an app. Hold OK, or press Menu, for relevant Open, Add/Remove Home, Hide/Unhide and App info actions. The Hidden apps view makes recovery available. Back restores the prior Home app and scroll position. On empty Home, use the enabled Library tile or a supported Apps button. Layout settings control the Library tile; it is hidden by default.

The launcher accepts Android's standard All Apps request, but the legacy accessibility Apps-key helper remains **disabled on this Sony** because its platform key-filter path breaks native held-volume repeat. Kernel capture records Volume press/release while the helper receives neither; a software timer cannot repair those missing callbacks.

The separately installed [Sony Apps layout companion](../companions/sony-apps-layout/README.md) maps only the observed Apps scan/usage to an application-launch category. A default-off launcher alias opens Library directly, hides Home chrome immediately and suppresses the entry animation. The owner confirmed Apps from Home and another app, Back, held Volume and disappearance of the intermediate Home flash. The saved layout needs no resident watcher or accessibility filter. Restart/sleep/Bluetooth reconnect persistence remains untested; a keyboard's Calculator key shares the Library route while enabled. The on-screen tile works independently.

## Settings and preferences

Hold OK on the Settings icon, then select Launcher settings:

- **Layout:** ordinary columns 4–7, visible rows 0–3, optional Library tile.
- **Appearance:** existing local wallpaper picker.
- **Motion:** animations and opt-in advanced effects, with the existing warning.
- **System:** theme defaults, Android's default-Home picker and TV settings.

Left returns to the sidebar; Right enters the selected category. Numeric segments and native switches retain the existing preference keys. Home additions/removals, ordering and new hidden choices use their existing or separate atomic stores. An APK update does not rewrite the external theme or revision and does not clear saved UI choices.

The Formula 1 countdown panel, calendar parser/scheduling class, resource strings, drawable, tests and host calendar-fetch tool are removed. The launcher had no F1 network service or F1-specific runtime dependency. A legacy `race_calendar` field in an existing user theme is inert unknown data; leaving the file untouched avoids treating an app update as a theme reset. The separately installed F1 TV app is unchanged.

## Build and upgrade

Use JDK 17, SDK/build tools 34 and the checked-in Gradle wrapper:

```sh
cd launcher
./gradlew --no-daemon testReleaseUnitTest lintRelease assembleRelease
```

Release builds remain minified/resource-shrunk. The existing sideload signing setup uses a local debug keystore; **preserve the key that signed the installed app**. A new machine or CI runner generates a different key and its APK cannot update an existing differently signed installation.

For a TV whose compiled defaults differ from this source, put only those resource overrides in a private directory and pass `-PlauncherResourceOverlay=/absolute/res/directory` to the release build. Theme and saved UI choices still take priority. Keep local paths and keys out of Git. The Sony artifact uses an overlay preserving the owner's existing dock and Sony input-player defaults; these are configuration, not hardcoded UI examples.

Install an APK only after comparing its certificate to the installed app. Use an in-place `install -r`, without uninstalling or clearing app data, then verify Home ownership, settings, wallpaper and dock order. Keep a same-signer recovery artifact and the original Home component available during testing. Turn debugging off after the upgrade checks.

Performance measurements, actual device checks and remaining limitations are recorded in the release report. A release is ready only when required host/CI checks pass and any unresolved device or performance limits are explicitly stated.

## Screenshots and changes

[Real-TV gallery](screenshots/README.md) · [Categorized changelog](../CHANGELOG.md). Screenshots were captured on the actual Sony during redesign validation; build 16 changes the input entry route without another visual redesign. No fresh runtime-performance claim is made.
