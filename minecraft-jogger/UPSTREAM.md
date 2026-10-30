# Source provenance

## Launcher foundation

- Project: Amethyst-Android, derived from PojavLauncher / Boardwalk
- Repository: https://github.com/AngelAuraMC/Amethyst-Android
- Pinned commit: 28d6e12d68121b7dd75f2e841058a6b7cdfe4a29
- Source: https://github.com/AngelAuraMC/Amethyst-Android/tree/28d6e12d68121b7dd75f2e841058a6b7cdfe4a29
- License: LGPL-3.0, retained in LICENSE
- Parent: https://github.com/PojavLauncherTeam/PojavLauncher
- Pinned submodule revisions are retained. androidnsbypass is fetched over HTTPS rather than SSH.

Jogger retains the upstream Java namespace for native/JNI ABI compatibility while using the separate Android application ID com.wiktor.minecraftjogger.

## Runtime and graphics components

The pinned upstream checkout retains native libraries, LWJGL assets and original notices. Source locations and dependency attributions are in its README:

https://github.com/AngelAuraMC/Amethyst-Android/blob/28d6e12d68121b7dd75f2e841058a6b7cdfe4a29/README.md

These include OpenJDK, LWJGL/LWJGLX, MobileGlues, Mesa/Zink, ANGLE, OpenAL, SDL, libepoxy, ByteHook, Cacio and the Android namespace shim. Their distinct licenses apply; the Jogger overlay does not relabel them as original work.

Android OpenJDK source/build repository:

https://github.com/AngelAuraMC/angelauramc-openjdk-build

Bundled Java 8 architecture archives are verified by overlay/scripts/jogger-prepare.py:

| Architecture | SHA-256 |
| --- | --- |
| ARM64 | 9a59124d9791957d55c68be664ab76831f336cf2e1e1cd4414220c6fdbf0e06d |
| x86_64 | b1fbcef4965c17925894febe8216d089c6dd47b37950b5f945a89616443c1d0e |

Their full contents and license notices are preserved in the runtime binpacks. Additional runtimes download through the backend when a Minecraft version requires them.

## Jogger additions and corrections

- Native Home, Instances and Library views.
- Isolated instance directories and Android pinned shortcuts.
- Microsoft legacy device-code authorization compatible with the inherited public client, retaining browser fallback.
- Account refresh ordering and expiry corrections; credential-log removal.
- Real jogger_touch_agent premain module and companion Android input bridge.
- Editable mobile touch layout and focus-loss cleanup.
- Modrinth browsing, checked downloads, imports, dependency handling and content management.
- Atomic profile storage and download/extraction correctness fixes.
- Separate application identity, monochrome theme, icon, signing and reproducible build.
- Portable tests for device-code policy, touch protocol/socket operation, archive handling and downloads.

No Mojang client JAR, user credentials, authentication tokens or private signing key are published in this source directory.
