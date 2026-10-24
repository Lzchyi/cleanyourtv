# Changelog — Lzchyi's Premium Minimal Home fork

Based on CleanYourTV v2.6.0, upstream commit 0bb7f7e. Personal follow-up build 16, 24 October 2026. Credit and MIT license remain with Gathin23 and the original contributors. This is not an upstream release or a stock v2.7.0 performance comparison.

## Added — Home, Library and settings

- Four-app translucent dock, compact horizontal clock/greeting/date and matching Inputs, Network and Settings controls.
- Full-screen alphabetical App Library with landscape banners, installed-app artwork, accessible labels and a hidden-app recovery view.
- Optional app-sized Library shortcut with text-free grouped-grid artwork. It defaults to the first pinned position and can be reordered with other Home apps.
- Launcher settings with a category sidebar, numeric segments and native switches, retaining existing preference keys.
- Bounded cached wallpaper softening and dimming; no per-frame blur generation, new runtime dependency, Internet permission or new Android background service.

## Fixed — navigation and layout

- Down navigates Home and stops at the final row; it never opens Library accidentally.
- Closing Library restores Home focus after layout with bounded retries, including the empty-grid case.
- Focused Library frames and settings selections fit inside their gutters.
- Enabling the shortcut with zero visible rows preserves the four-app dock and a small next-row preview.
- Dock-to-pinned navigation scrolls through the same viewport rather than abruptly jumping between layers; the header fades as lower rows use the viewport.
- Icon-only Network retains a compact green VPN connection badge. Live VPN-state changes were not revalidated for this publication.

## Fixed — Sony Apps and held Volume

- Separate optional Sony layout companion remaps Apps before Android's key-filter path; native held-volume repeat remains active.
- Direct Library alias removes the intermediate Home flash. The accessibility key helper stays disabled.
- Setup switches explicitly check WRITE_SECURE_SETTINGS. A bounded trial and explicit reverse layout support rollback; no resident watcher is needed after confirmation.
- Owner confirmed Apps from Home and another app, Back, continuous held Volume/stop-on-release and disappearance of the Home flash.
- A physical keyboard's Calculator key shares this Library route. Restart/sleep/reconnect persistence and other TV models remain untested. See [companion setup and rollback](companions/sony-apps-layout/README.md).

## Removed

- F1 countdown/calendar panel, parser, scheduling logic and host calendar-fetch tool. Existing external theme files and the separately installed F1 TV app remain unchanged.
- Unsuccessful software volume-repeat experiments and their audio permission.

## Validation

The launcher release build and companion compilation/signature verification passed. Historical build 9 had 188 passing JVM tests and zero lint errors; focused build-10 navigation/order checks had 41 passing tests. Publication checks are recorded in [the verification note](docs/publication-checks.md). Screenshots come from the real Sony, not an emulator.

Current installed APK archive sizes: launcher 446,941 bytes; companion 12,896 bytes. These are not installed-storage or RAM measurements. No fresh performance comparison with stock v2.7.0 was made. Existing Home ownership, theme and saved UI choices were preserved; no simulator was used.

## Upstream routing

The full visual redesign belongs in this fork with a SHOWCASE listing. It is not a configuration-only theme or a module-card add-on. Firmware evidence can be contributed separately as device/troubleshooting documentation. Small upstream launcher fixes require reproduction on the current reference launcher before extraction; problems found only in this restyle are not claimed as upstream bugs.
