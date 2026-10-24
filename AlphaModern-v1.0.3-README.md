# AlphaModern v1.0.3 — Inventory Startup Crash Fix

Java 8 agent for **Minecraft Alpha a1.0.15** connecting experimentally to Minecraft 26.2 protocol 776.

## Fixed in this release

v1.0.2 could crash on opening the multiplayer connection UI before reaching a server:

```
java.lang.IllegalAccessError: tried to access class alphamodern.InventoryMirror
from class net.minecraft.client.Minecraft
  at net.minecraft.client.Minecraft.a(SourceFile:1118)
  at mb.<init>(SourceFile:17)
```

Cause: the agent transformed `net.minecraft.client.Minecraft` and `dy` to invoke static methods on `alphamodern.InventoryMirror`, but `InventoryMirror` was package-private and its `bind(Object)` method was also package-private. The JVM correctly refused the cross-package `invokestatic` call. This error happens at first use of the transformed method, so the old load-only test missed it.

Fix: `InventoryMirror` is now **public** and `InventoryMirror.bind(Object)` is **public static**. The existing `InventoryMirror.click(Object, int, int, int)` is still public static. Source-level access checks and real cross-package `invokestatic` executions are now part of the automated regression suite, using `java -Xverify:all`. This fix requires no flags, Java version upgrade, or different Minecraft jar.

## Retained from v1.0.2

- Server-mirrored hotbar, main inventory and armor; basic clicks sent to server.
- Fallback full-cube collisions for unknown solid modern blocks, no temporary spawn-collision bypass.
- Server-authoritative ghost-block updates and collision awareness.
- Spawn-chunk gating, respawn/world-switch support, 26.2 server keepalive/ping and player sync.
- Modern-to-Alpha terrain block state conversion, relighting storm prevention, formatted chat.
- Device-code Microsoft sign-in and saved credentials (unchanged).

## Prism Launcher

Replace the old agent with exactly one JVM agent:

```
-javaagent:/home/YOUR_USER/Downloads/AlphaModern-776-v1.0.3.jar
```

Use **Java 8** for Alpha. The agent was compiled targeting Java 8. The existing saved Microsoft account token will not be deleted. To run tests with a JDK and the original Alpha jar at `/mnt/data/minecraft-a1.0.15-client.jar`:

```
sh test.sh
```

## Limits

The previously reported **IllegalAccessError** is fixed and locally regression-tested, including actual cross-package calls. The agent is still experimental: full modern inventory components, arbitrary custom container menus, complete modern block shapes, and live Jartex compatibility remain unverified. The subsequent Linux `BadWindow` exception in the original crash log occurs during shutdown and is not the primary bug fixed here. Do not share locally stored OAuth credentials.