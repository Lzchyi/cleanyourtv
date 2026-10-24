# Publication checks — 24 October 2026

Personal v2.6.0 fork, build 16. These checks prepared the public source and documentation; no APK was installed on the TV during publication.

- Launcher: 189 JVM tests passed, zero failures/errors/skips.
- Release build, resource shrinking and lint passed; lint reports zero errors and 69 warnings.
- Repository: 301 tests passed, with one intentional skip.
- Optional companion: public portable build wrapper compiled the APK and shell DEX; signature verification passed. The shell DEX matches the device-trial tool. Generated output and signing keys are ignored.
- Three screenshots were visually inspected: actual Sony Home, Launcher settings and App Library captures from redesign validation. They show no account, address, Wi-Fi name or unique device identifier.
- Public source was checked for private workstation paths, device addresses, unique remote descriptor and private-key markers. Private resource overlays, ADB keys, raw dumps, trial baselines and compiled artifacts are excluded.
- No simulator/emulator was launched. No fresh performance comparison was run.

Physical results come from the earlier owner-authorized device session: Apps from Home and another app, Back, native held Volume and disappearance of the intermediate Home flash were confirmed. Default Home and external theme were preserved, the accessibility filter stayed off and no trial watchdog remained. Restart, sleep/wake and Bluetooth reconnect persistence remain untested.

Initial host runs were blocked by sandboxed loopback sockets used by Gradle and fake-ADB tests. Rerunning with local sockets allowed passed the fork checks above; these were host setup restrictions, not TV regressions.

The complete redesign stays on this fork's v2.6.0-redesign branch. Upstream contributions are separate documentation changes following CONTRIBUTING.md: a SHOWCASE row and a Sony firmware/troubleshooting follow-up. No claim is made that the fork-only layout bugs also exist on the current reference launcher.
