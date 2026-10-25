# Bullet — Fabric 1.21.4

A tiny rideable, craftable teleport machine with vanilla-feeling coordinate-entry UI.

## Install
Use Minecraft Java Edition 1.21.4 with Fabric Loader and Fabric API. Drop bullet-1.0.5.jar into the mods folder.

## Crafting
Iron | Redstone | Iron
Copper | Ender Pearl | Copper
Iron | Redstone | Iron

Place the crafted Bullet item by right-clicking the top of a block, then right-click the vehicle to sit in it.
A native coordinate screen will open. Enter X, Y, Z, then press DONE — LAUNCH.
The FOV grows exponentially over five seconds to a logical 720 degrees with increasing camera shake.
The vehicle and player teleport together at the peak, then the camera returns to its original
FOV over four seconds. There is no FOV display or on-screen overlay.
The actual perspective matrix stays below 180 degrees (maximum 170) to avoid the
invalid projections, flipped geometry, and glitches that literal 720-degree perspective causes. Sneak-right-click the empty vehicle to recover the item.

Server-side travel is only allowed from the vehicle's seat.
