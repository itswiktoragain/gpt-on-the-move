# Bezel Motion
Native Java Android side-button animation app. Target Android 8+; designed for modern devices.

- Enable **Bezel Motion button detection** in Android **Settings > Accessibility > Installed apps**.
- Volume Up and Volume Down can trigger visual effects over other apps without blocking the volume changes.
- Drag the colored markers in the in-app phone preview to match your real Power, Volume Up, and Volume Down buttons. Markers snap to either screen edge.
- Change appearance, size, spread, animation duration, and color. Preferences survive restarts.
- Use the three test buttons to preview an effect in place on your real screen.
- A power-button key event is generally withheld by Android; the app optionally shows its Power-position animation after the display wakes. It cannot animate while the screen is off.

This app only requests Android's key-filtering Accessibility permission; it does not inspect window contents or transmit data.

Build: \`gradle assembleDebug\` from this folder, using Android SDK 35 and JDK 17.
