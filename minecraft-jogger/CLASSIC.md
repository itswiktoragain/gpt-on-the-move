# Minecraft Jogger 1.1 Classic

This is explicitly based on the original Amethyst/Pojav engine, at the user's request. It is not the unfinished independent engine. The original native JVM, graphics, downloads, mod-loader support, account handling and instance functionality remain in place.

The separate Android package `com.wiktor.minecraftjogger.classic` installs alongside the original APK. It does not replace or modify the original application's worlds. Accounts and instance content must be set up/imported in this separate installation. Android 10+, ARM64 and x86_64.

## Controls

Classic directional buttons on the left, with centre crouch toggle and double-forward-tap sprint. Right-side jump, mine and use buttons. Hotbar slots remain Minecraft's real slots; the appended `...` opens inventory. Short touch uses/places, a stationary hold mines, and dragging turns the view. Dragging away and back never accidentally places. Multiple fingers have independent input ownership so releasing one finger cannot release another finger's key.

Chat switches the top bar to Leave / Keyboard / Send and hides Pause. Other screens show Back / Keyboard / launcher menu. Back/Leave closes the current screen; it does not forcibly kill the JVM or discard a world. Save & Quit is still Minecraft's ordinary menu action.

The button backgrounds are read from the installed game JAR or enabled ZIP resource packs. Classic pixel icons are original Jogger artwork. A grey beveled fallback is used until the installed game texture is available. No proprietary Minecraft artwork is shipped in this update.

The Java agent observes actual Minecraft screen state and instruments the first-person picking ray. It uses official client mappings when available, plus named and Fabric/Quilt intermediary names. Touching an off-centre point changes the picking ray rather than spinning the camera or extending reach. The hook is conditional: unsupported Minecraft/render-mod combinations retain crosshair targeting. A small white touch-point marker appears only when the hook is active. Third-person or custom-camera mods are not guaranteed to map touch points correctly.

## Performance

New instances receive a reversible Balanced preset: six render chunks, five simulation chunks, a 60 FPS limit, reduced particles and entity distance, and no clouds/entity shadows. First-install launcher defaults use 75% resolution and conservative memory allocation. These are workload-reduction settings, not a measured FPS improvement claim.

Touch controls on the launcher opens the classic-control switch, touch targeting switch and Battery/Balanced/Quality/Restore presets. Existing unrelated options and resource-pack settings are preserved. Performance options are saved once, not overwritten on every launch. Original values are retained per instance for restoration. Renderer, resolution and RAM remain adjustable through normal Settings.

## Tests and limits

The workflow compiles the APK, verifies its Android signature, and runs gesture/input ownership tests, touch-ray projection/scope tests, the existing input-agent state tests and the live JVM socket test. A successful build is not a physical-phone gameplay test. Account authorization, renderer/device compatibility and actual in-game use still require testing on the user's phone.

The CI build uses a unique temporary signing key unless JOGGER_CLASSIC_KEYSTORE_BASE64 and JOGGER_CLASSIC_KEYSTORE_PASSWORD are configured. It is not update-compatible with the recovered original Jogger APK; the different package avoids that conflict.

## Build and source

The classic workflow reconstructs the pinned upstream engine, overlays `overlay/`, applies `classic-integrate.py`, and builds it. Original engine and bundled component licensing/provenance are documented in UPSTREAM.md. The touch-agent additionally bundles ASM 9.7.1 (BSD-3-Clause); its upstream license is preserved in the JAR.

Not an official Minecraft product. Not approved by or associated with Mojang or Microsoft.
