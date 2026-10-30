# Minecraft Jogger - independent implementation

This branch replaces the previous Amethyst/Pojav-based experiment with original launcher, launch, native window/input bridge, and touch-control code. No Amethyst/Pojav launcher source, launcher APK, or launcher runtime glue is to be bundled.

Third-party dependencies such as OpenJDK, LWJGL, OpenAL and graphics translation libraries will be identified individually with their source and licenses. This project is not a Minecraft implementation; it launches the user's official Java Edition files.

Requested controls: classic directional pad with centre crouch, double-forward sprint, right action buttons, inventory ellipsis beside the hotbar, contextual chat/pause/leave actions, game-textured buttons, tap-to-use, hold-to-mine, and drag-to-look. Mobile performance settings must be reversible and must not overwrite unrelated instance settings.

Build and device/gameplay validation results will be recorded explicitly. No untested capability should be described as verified.
