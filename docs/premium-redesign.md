# Premium v2.6.0

This personal fork is based on upstream's `v2.6.0` tag (`0bb7f7e`), with application version **2.6.0 / code 8**. It keeps the native Java views, RecyclerView, existing input/vendor handling and fast-return lifecycle. It adds no runtime dependency, Internet permission or new background service. Android 8/API 26 and later remain supported; hardware validation for this redesign is on one Sony KD-55X9000H running Android 12.

## Home and navigation

The clock sits beside a compact greeting/date block. Inputs, Network and Settings are matching icon controls with accessible names; an active VPN keeps a compact green badge. The dock has at most four apps, independently of ordinary grid columns. Existing dock overflow stays among the regular Home apps. A translucent gradient and thin edge simulate glass without live blur.

Focus uses a restrained 1.05× scale. Header focus keeps the wallpaper sharp; dock and lower-row focus soften and dim it progressively. One worker generates a cache of at most 320×320 pixels per wallpaper; decoding that preview is capped at 204,800 pixels. Focus changes animate opacity rather than recompute blur. Allocation/decode failure falls back to dimming. Disabled animations settle immediately; reduced motion shortens or removes effects. Original wallpaper files remain local and unchanged.

The optional **App Library tile** appears directly below the dock at the same size as other Home apps, including when visible rows is zero. Select it to open the full-screen, alphabetical Library: six landscape banner columns at Sony 1080p, adapting to narrower screens. Original app banners and icons remain unchanged. **Down from the final actual Home row** remains an alternative. OK opens an app. Hold OK, or press Menu, for relevant Open, Add/Remove Home, Hide/Unhide and App info actions. The Hidden apps view makes recovery available. Back restores the prior Home app and scroll position. Empty Home can enter the Library with Down from its top controls. Layout settings control the Library tile; it is hidden by default.

The launcher accepts Android’s `ACTION_ALL_APPS` activity request as well as a foreground `KEYCODE_ALL_APPS`. Some Android TV firmware sends the request only to a system launcher. For those sets, the existing `HomeKeyService` has an opt-in `apps_key_library` resource mode: only Apps opens the Library; Home/Settings mappings are bypassed and unrelated keys pass through. The Sony build opts in and recognizes the observed VRC 001 Apps scan only with its matching vendor/product identity. This reuses the existing key-only service, requests no screen-content events and adds no polling. It requires enabling that service; other remotes and firmware still need device verification. The visible tile works independently of remote compatibility.

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
