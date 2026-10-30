# Minecraft Jogger

An independently written Android launcher for Minecraft: Java Edition. The launcher, installer, instance manager, Microsoft device-code client, touch HUD, Java agent, JVM host and Android window/input bridge are original Jogger code. This is not the older Amethyst/Pojav-based Jogger experiment.

## Installation and first run

Use the signed Minecraft-Jogger-0.3.0-independent.apk on 64-bit Android 10 or newer. ARM64 phones and x86_64 Android are included. An OpenGL ES 3 capable GPU is required. The APK contains OpenJDK 21 and native graphics dependencies; Minecraft's client and assets download from Mojang after installation.

Start with the default Minecraft 1.21.4 vanilla instance. The first download requires internet and additional storage. The launcher exposes the official, unmodified Minecraft demo for testing without account authorization. This does not unlock the full game or bypass Minecraft ownership.

The new Android package is com.wiktor.jogger, separate from the old com.wiktor.minecraftjogger fork. It does not replace the old app or automatically migrate its worlds. Keep existing worlds backed up. Preview CI builds may have different signing identities and therefore may not install as updates to each other.

## Controls

A classic directional pad sits on the left with a centre crouch toggle. Double-tap forward to sprint, then keep holding forward. Sliding to a different direction releases the old direction; pointer cancellation and focus loss release held controls. The right side has jump, mine and use buttons.

The inventory ... button sits after the nine hotbar slots. Tap a slot to select it. In the world, a quick screen tap uses/places, a stationary hold mines, and a drag turns the camera. The bundled Java agent uses the installed game's mappings to perform the interaction raycast at the touched screen location without turning the rendered camera. If the hook is unsupported, the native layer falls back to crosshair interaction and logs the missing hook; version compatibility is not universal.

The top controls respond to the game's screen state. Chat becomes Leave in the same position, with no pause button; other menus use back/type/exit. Exit asks Minecraft to close normally rather than immediately killing an active world. Widget textures are read from the installed Minecraft client, with original drawn icons and a bevel fallback before game textures exist.

The controls test in Settings is explicitly a test screen, not Minecraft gameplay. Settings also provides a real Java/OpenGL engine test with framebuffer readback.

## Instances and content

Instances have separate worlds, mods, resource packs and options. The launcher supports vanilla, Fabric and Quilt installation paths; it is not a universal installer for every loader or desktop Java application. Each instance can have a home-screen shortcut, subject to Android's confirmation.

Modrinth search filters for the selected game version and loader. Local JAR/ZIP import, direct HTTPS content downloading, enable/disable and deletion are included. Required dependencies are resolved by the content installer. Resource packs are activated through Minecraft's own Resource Packs menu. Only install trusted mods: they execute code with the game's permissions.

## Mobile performance

Balanced uses 6 render chunks, 5 simulation chunks, a 60 FPS cap and 70% resolution. Battery uses 4 render chunks, 5 simulation chunks, a 30 FPS cap and 55% resolution. Quality uses 10 render chunks, 6 simulation chunks, a 60 FPS cap and full resolution. Other effects are reduced in the lower presets. The options touched by presets are backed up and can be restored without overwriting unrelated options. RAM allocation and resolution are adjustable.

These are resource-budget settings, not measured promises of a particular frame rate. Actual performance depends on the device, graphics driver, Minecraft version and installed mods.

The bundled Java 21 / LWJGL 3 engine rejects versions requiring a newer Java runtime and does not support legacy LWJGL 2 versions. Begin with 1.21.4; other versions and loader combinations need their own validation.

## Microsoft authentication configuration

This independent build does not reuse another launcher's OAuth client identity. A Microsoft public-client application ID is required in Settings before device-code sign-in can work, unless the builder supplies it through JOGGER_MICROSOFT_CLIENT_ID. The registration must allow personal Microsoft accounts, device authorization and the necessary Xbox/Minecraft access. No client secret belongs in the APK.

Jogger requests a code, opens Microsoft's validated HTTPS sign-in page, polls with expiry/throttling/cancellation handling, exchanges Xbox/XSTS/Minecraft tokens and checks Java Edition entitlement. Account data is encrypted with Android Keystore and excluded from backup. A user account has not been authorized in automated testing. Registration approval and a successful build are different things; the official demo remains available while registration is being configured.

## Source, dependencies and build

Original Jogger code is MIT licensed. Separate dependencies retain their licenses: OpenJDK (GPLv2 with Classpath Exception), LWJGL, libffi, OpenAL Soft, MobileGlues, ASM and the Android NDK runtime. The GLFW public API header is used, not its desktop platform implementation. The OpenJDK Android runtime is built by AngelAuraMC; that attribution is not a claim that its launcher is included. MobileGlues contributes its renderer library only; no plugin APK is installed.

The GitHub Actions workflow at .github/workflows/build-jogger-independent.yml builds with JDK 21, Gradle 8.9, Android SDK 35 and NDK 27.3.13750724. dependencies.py builds/packages separately credited libraries. integrate.py applies asserted original-source changes. prepare.py compiles the original engine and agent, checks all required GLFW exports and runs deterministic tests. The workflow builds both release/debug APKs, checks signatures, installs the debug build in Android and preserves engine logs and screenshots.

Use the recorded test output rather than the overall workflow colour: the emulator step preserves diagnostics even on failure, so a green build alone does not establish gameplay success. The source and APK artifacts include the exact commit ID. Full account authorization, ARM64 physical-phone performance and broad mod compatibility are not established by emulator tests.

Not an official Minecraft product. Not associated with Mojang or Microsoft.
