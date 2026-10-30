# Minecraft Jogger

An independent Android launcher for Minecraft: Java Edition. Jogger adds a native white-and-black interface, isolated game instances, Microsoft device-code sign-in, an editable mobile control layout with a real Java input agent, content downloads and per-instance home-screen shortcuts.

This is a **1.0 preview** based on the actual Amethyst / Pojav JVM and graphics backend. It includes native launch and graphics libraries and Android OpenJDK 8. It downloads Minecraft and additional Java runtimes when needed.

## APK

The build produces **Minecraft-Jogger-1.0.0-preview.apk**.

- Android 10 (API 29) or later.
- 64-bit ARM phones/tablets; x86_64 is also included for compatible devices and testing.
- Independent Android package: com.wiktor.minecraftjogger.
- Minecraft game data is downloaded from the configured official Minecraft sources. The APK does not contain Minecraft's proprietary client or an account.
- A Microsoft account that owns Minecraft: Java Edition is needed for the full game. Account and multiplayer restrictions remain enforced by Microsoft.
- Minecraft, mods and renderers have different hardware requirements. APK installation alone does not establish that every game/mod version works on every phone.

## First use

1. Install the APK and open **Minecraft Jogger**.
2. Choose **Sign in with a code**. The code and its expiry come from Microsoft.
3. Open the displayed Microsoft page on this phone or another device, enter the code, and sign in with your Microsoft email. Jogger does not ask for your password.
4. Start the default **My first world** instance (Minecraft 1.21.4), or create another instance and choose a version.
5. Tap **Play Minecraft**. Let the first download and runtime extraction finish.
6. Use the left joystick to move, swipe the world to look, and use the right-side Mine, Use, Jump, Sneak and Sprint buttons. Bag, Chat, Type, Pause and Menu are available across the top.

The first download needs internet access and additional free storage. Shader packs, desktop native libraries and graphics overhaul mods may require a compatible renderer or may not work on Android.

## Included features

| Area | Function |
| --- | --- |
| Instances | Separate game directories for worlds, options, mods and resource packs; version selection, rename, renderer, Java settings and instance removal |
| Authentication | Microsoft device-code dialog, countdown, copy/open actions, cancellation, expiry and throttling handling, Xbox/Minecraft token exchange, account refresh, browser sign-in fallback |
| Touch | Editable familiar mobile layout, native camera movement, native keyboard/gamepad paths, and a bundled Java 8-compatible input agent |
| Java touch agent | Authenticated loopback bridge; input ordering, duplicate suppression, held-key tracking, focus/pause releases, stale-event rejection, heartbeat cleanup and native fallback |
| Mod loaders | Upstream Fabric, Quilt, Forge, NeoForge, OptiFine, LWJGL3ify and modpack installation interfaces |
| Mods | Version/loader-filtered Modrinth browsing, required dependency resolution, SHA-512 verification, JAR import, HTTPS download, enable/disable and delete |
| Resource packs | Version-filtered Modrinth browsing, ZIP import/download and archive validation; enable the pack from Minecraft's own Resource Packs menu |
| JARs | Minecraft downloads through the backend, mod JAR download/import, and the upstream Java installer launcher |
| Settings | Graphics renderer, resolution, RAM, Java runtime, controls, JVM arguments, device input and other backend settings |
| Shortcuts | Pin an instance to the Android home screen and launch it through the ordinary account/download checks |
| Diagnostics | Share launcher log, inspect instance files, runtime/download errors |

The mobile controls operate Java Edition. Java Edition's inventories, crafting, menus, mechanics and server protocol remain Java Edition's. The agent does not depend on obfuscated Minecraft class names or Mixins.

Home-screen pinning is confirmed by the Android home-screen app. Removing an instance disables its shortcut while leaving the world's files available.

## Source and license

The repository stores a reproducible source overlay rather than duplicating the upstream binary dependency history:

- **UPSTREAM.md**: exact upstream revision and component provenance.
- **overlay/**: complete replacement/new source files, native/build changes and tests.
- **build.sh**: reconstructs the pinned source and applies the overlay.
- **LICENSE**: upstream LGPL-3.0 license.
- **VALIDATION.md**: recorded tests and limitations.
- **.github/workflows/build-minecraft-jogger.yml** at repository root: GitHub Actions build.

Jogger's modifications are distributed under LGPL-3.0-or-later, consistent with the Amethyst/Pojav basis. Upstream and third-party notices/licenses remain in their original files and packaged components. OpenJDK, renderer and native libraries retain their own licenses.

**Not an official Minecraft product. Not approved by or associated with Mojang or Microsoft.**

## Build

Use Linux with JDK 21, Gradle 9.6.1, the Android SDK and NDK 27.3.13750724. Gradle files specify exact Android plugins and SDK requirements. The preparation script downloads pinned Android JRE 8 archives and verifies their SHA-256 values.

Run ./build.sh from this directory. It reconstructs the upstream checkout in a new directory, overlays Jogger's changes, prepares runtimes, and runs the release build plus Java agent tests.

Release signing uses these environment variables:

- **JOGGER_KEYSTORE**: absolute path to a private PKCS12 keystore.
- **JOGGER_KEYSTORE_PASSWORD**: its password.
- Key alias: **jogger**.

Keep the signing identity private and stable for update-compatible APKs. The delivered APK uses a separately preserved Jogger signing identity. The CI workflow can reuse repository secrets JOGGER_KEYSTORE_BASE64 and JOGGER_KEYSTORE_PASSWORD. Without them, CI makes a temporary preview identity; that build cannot install as an update over a differently signed APK.

Output: jogger-source/app_pojavlauncher/build/outputs/apk/release/app_pojavlauncher-release.apk.

## Privacy

Accounts are stored in the app's private directory. Android backup is disabled. Credential-bearing diagnostic output has been removed. The device-code page opens at Microsoft's validated HTTPS address. The touch bridge uses loopback and a fresh per-launch authentication token.

Minecraft/Microsoft, selected download services and upstream runtime/asset hosts receive requests necessary for sign-in, downloads and game operation. Modrinth downloads use URLs returned by Modrinth; user-entered download URLs must use HTTPS. Importing a JAR does not automatically execute it.
