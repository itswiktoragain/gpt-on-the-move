# Minecraft Jogger Mobile 1.2.0

Amethyst/Pojav-based Android launcher update, explicitly retaining and crediting that engine.

## Reconstruct source

Reconstruct upstream commit 28d6e12d68121b7dd75f2e841058a6b7cdfe4a29, copy ../overlay into it, run ../classic-integrate.py, then run apply.py with the checkout path. Do NOT run fov-hotfix.py afterwards; the mobile diff includes that frame-safe repair. apply.py also produces a plain, reviewable mobile-update.patch. The base64/LZMA transport files contain source only; SHA-256 is verified before application. The full plain diff is included in build artifacts.

## Changes

- Opt-in password-encrypted, portable account backups in a user-selected Documents-provider folder. Two rotating files preserve the previous verified backup. Restore after reinstall requires choosing a vault file and supplying the password. Private local credentials never become public files. Microsoft can still require reauthentication for revoked or expired sessions.
- An AWT account-transfer utility exports a vault from the older Jogger installation, without changing its credentials or worlds.
- Corner Back/Menu controls outside play, no dedicated keyboard/mine/place buttons, square inventory button, combined hotbar/inventory centering on supported HUD adapters, raised chat and chat hit testing.
- Tap entity to attack, tap block to use/place, hold to mine, drag to look. Touch-first inventories use the game's touchscreen path when available and a pickup/drop gesture fallback otherwise. Java Edition rules and interface remain Java Edition.
- Version metadata, verified official mapping downloads, named/intermediary aliases, and conservative legacy bytecode discovery replace a single-version assumption. Unsupported/ambiguous features are disabled individually. Crosshair remains available as a fallback when touch-point picking cannot be established. No promise of every version/mod combination is made.
- Java 8-compatible agent, isolated ASM tooling, original FOV verification repair retained, bounded initial heap instead of eagerly reserving the entire configured heap.

## Validation and limits

Local tests: 15 encrypted-vault checks, 15 gesture/input checks, 9 touch projection checks, 71 original frame regressions, 26 HUD wrapper/fallback checks. Five matching adapter passes and data-flow analysis of 887 methods were exercised against the actual official Minecraft 1.21.4 client. Packaged Java-agent socket tests passed locally. These are NOT physical-phone gameplay tests. Alpha a1.0.4 and the user's requested 26.4 snapshot 6 have not been gameplay-verified. The selected version's installed metadata drives adaptation; the adapter does not add sprinting/crouching mechanics absent from early game versions.

## Installation identity

Package com.wiktor.minecraftjogger.mobile installs alongside earlier Classic builds because their signing keys were not retained. Do not uninstall old installations until account and world migration is complete. This build encrypts its new signing-key backup to a private recipient before artifact publication, allowing the signing identity to be retained without exposing it publicly.

Backups contain accounts only, NOT worlds. Keep the password and verify the latest successful-backup status before uninstalling. Cannot recover account data already deleted without a prior backup.

Not an official Minecraft product. Not approved by or associated with Mojang or Microsoft. Engine and modification licensing remains LGPL-3.0-or-later; ASM and other third-party notices remain applicable.
