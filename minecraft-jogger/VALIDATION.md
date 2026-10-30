# Validation of Minecraft Jogger 1.0 preview

## Delivered binary

- File: Minecraft-Jogger-1.0.0-preview.apk
- Package: com.wiktor.minecraftjogger
- Version: 1.0.0-preview; version code 10000
- Minimum Android: 10 / API 29; target API 34
- Architectures: arm64-v8a and x86_64
- Size: 161,977,200 bytes
- SHA-256: 5abb7e3f913c8cf06f158061b54c9c61b4077a65560547776bcb1bc4b9298b8c
- APK Signature Scheme v2: verified
- Signer certificate SHA-256: 09d4a1e9a70927e1b027e5527e3fc71eda31ab92a81793c6cfe7b44ffe12e830

## Build and packaging checks

The complete Android release build passed using JDK 21, Gradle 9.6.1, Android Gradle Plugin 9.3.1 and NDK 27.3.13750724. Both native architectures compiled. The upstream release-lint errors for missing default strings were resolved with five English fallback strings; lint was not disabled to hide them.

APK inspection confirmed the independent package and application label, native JVM bridge libraries for both architectures, both verified Android OpenJDK 8 archives, LWJGL/graphics assets, and the real bundled jogger_touch_agent.jar. The agent JAR declares com.minecraftjogger.touch.JoggerTouchAgent as Premain-Class and is Java 8 compatible.

## Executed tests

- Touch input state tests: duplicate presses, pause/reset releases, stale epochs, native startup fallback, watchdog releases and malformed input.
- Actual packaged agent launched with -javaagent in a JVM: authenticated socket handshake, ordered key/mouse input, stale input rejection and heartbeat timeout cleanup.
- DeviceCodePolicyTest: 32 assertions for pending, expiry, denial, slowdown, server errors, success, and safe Microsoft verification URLs.
- RuntimeArchivePathsTest: 11 assertions including traversal, absolute paths, sibling-prefix escape and pre-existing symlink escape.
- test_downloads.py: 13 assertions against the production upstream downloader, including truncation, HTTP errors, midstream failure, unknown lengths, progress, atomic replacement and partial-file cleanup.
- JoggerDownloadsTest: production-class checks for filenames, real ZIP/JAR validation, the SHA-512 abc test vector, cancellation, preserving existing files, publication, version compatibility and conflicting dependencies.
- git diff --check: passed.

## Live authentication protocol checks

The inherited public client's compatible legacy Microsoft device-code endpoint issued a real code with HTTP 200. The corresponding token poll returned authorization_pending as expected. No user account was signed in, no entitlement was fabricated and no access/refresh token was supplied for testing.

## Practical limits

Full Microsoft account authorization, game entitlement verification, Minecraft world launch, multiplayer and physical-phone graphics performance require testing with the user's own account and device. No claim is made that every Minecraft version, mod, native library, shader or GPU combination works. Java Edition inventory and menus remain the Java Edition UI.

## Android emulator smoke test

The final delivered APK (SHA-256 above) installed and launched on Android 10 / API 29, x86_64, using the software CPU emulator. Its native Home screen rendered correctly and initial setup progress completed. Dark status/navigation icons were verified on the light launcher background. No application AndroidRuntime exception appeared in the final logcat capture.

The Instances list and default profile were also displayed on the immediately preceding build; the final code delta only restores system-bar contrast in LauncherActivity. Creating an instance and checking persistence, exercising Library/settings, pinning shortcuts and opening the device-code dialog were not completed in the emulator. The live device-code protocol and deterministic Java tests described above were checked separately.

## Independent GitHub build

GitHub Actions run [41005382621](https://github.com/itswiktoragain/gpt-on-the-move/actions/runs/41005382621) succeeded for commit 280b06924a75ab304b395a4015b6c86a2802b247. It reconstructed the pinned upstream source, built the release APK, ran the Java agent tests, verified its signature and uploaded its artifact. The delivered APK adds the final system-bar contrast fix, which passed another local release build and emulator install.

CI preview builds use a temporary signing identity unless repository signing secrets are configured. Use the delivered APK for this installation; a differently signed CI artifact cannot update it in place.
