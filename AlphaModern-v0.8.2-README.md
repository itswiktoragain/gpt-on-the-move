# AlphaModern 0.8.2 — stop legacy lighting storms

A Java `-javaagent` for Minecraft Alpha 1.0.15 that experimentally translates protocol 776 (Minecraft 26.2). Tested locally against the supplied Alpha client. Full compatibility with all modern servers is not guaranteed.

## Changes since 0.8.1

In a live Jartex run on October 24, 2026, 0.8.1 logged **113,250,000 deferred lighting jobs** while the client became unresponsive. The 8,192-job queue limit prevented recursion but still let Alpha perform millions of unnecessary eligibility checks and allocate `kb` lighting tasks. The log ended with `Stopping!` and an LWJGL X11 `BadWindow` error during cleanup; this is not sufficient to prove the underlying game logic triggered a new exception.

0.8.2 patches two instructions within Alpha's `cj.a(Lbu;IIIIIIZ)V` method at runtime:

1. At bytecode index 24, the early `cj.d(x,64,z)` eligibility check is replaced with `WorldLightGuard.allowOriginalLight(world,x,64,z)`. **During a modern bridge connection, it returns false immediately**, skipping the rest of the old relight scheduler. Outside bridged connections it executes the real `cj.d` via a cached method handle, preserving Alpha's original check and singleplayer behavior.
2. At bytecode index 155, the existing `List.add` failsafe rejects any remaining bridged relight work. This prevents recreation of the pathological 8,192-job queue. Old repeated 'Deferred 50000 legacy relighting jobs...' logging is removed.

Modern chunk lighting is still sent to Alpha by the protocol converter. Custom proxy packets or missing lighting data may cause an approximate daylight fallback, but this tradeoff is preferable to hanging the main thread.

## Installation in Prism Launcher

Use **one** AlphaModern agent, removing older 0.8.1 versions from the instance's agents/libraries. Add this JVM argument:

```
-javaagent:/home/YOUR_USER/Downloads/AlphaModern-776-v0.8.2.jar
```

The existing Microsoft sign-in token is left unchanged. Other features remain experimental, including blocks/entities above Alpha's capabilities and custom modern servers.

To see the exact patched `cj.class` from your original game file:

```
java -jar AlphaModern-776-v0.8.2.jar patch-world-dump minecraft-a1.0.15-client.jar cj-patched.class
```

## Verification

- Java 8-compatible source (`javac -source 8 -target 8`), executable agent manifest.
- Two million simulated lighting requests during bridge yielded zero jobs.
- Non-bridged world delegation (`cj.d` equivalent) and `List.add` restored after disconnect.
- Original Alpha `cj.class` patched and loaded by the JVM with unchanged method bytecode length.
- Terrain, light, chat, session, auth, and viewport regression tests passed using mocked server interactions.

**Live Jartex stability has not yet been tested with v0.8.2.** It should address the specific large-scale old-lighting loop shown in the supplied log, but other 26.2 protocol behavior may still fail. No credentials or auth secrets are included in the release.