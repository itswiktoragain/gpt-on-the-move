# AlphaModern v0.8.1 — fix Alpha 1.0.15 lighting stack overflow

The user's October 24, 2026 live Jartex run confirmed **terrain now loads**, but crashed with `java.lang.StackOverflowError` repeating `cj.e -> kb.a -> cj.a -> cj.e`.

Alpha's light scheduler `cj.a(Lbu;IIIIIIZ)V` enqueues legacy lighting work. The original method invokes `cj.e()` recursively when its pending list exceeds 100,000 entries; `cj.e` calls `kb.a` which schedules more work and can trigger recursion again. Modern chunks stimulate many more lighting updates than Alpha was designed to process.

**v0.8.1 fix**: Surgically patches the `java/util/List.add(Object)` call at bytecode offset 155 in the original Alpha 1.0.15 `cj.class` to call `alphamodern.WorldLightGuard.offer(List,Object)` instead. This replacement preserves bytecode code length, operand types, and return type. When modern bridge multiplayer is active, it caps the pending lighting queue at **8,192 tasks**, preventing the original 100,000-task recursion threshold from being reached. When not connected to the bridge, `List.add` is unchanged, keeping singleplayer behavior intact. The bridge still supplies block and skylight arrays from translated modern chunks. This may temporarily defer legacy relighting at some block edges, especially in complex lobbies.

The queue limit is configurable with `-Dalpha.modern.lightQueueLimit=8192` (range 256..32768). The agent logs how many legacy relight tasks it defers, and `/amdebug` reports this count. The session enables and disables the guard on connection and disconnection.

**This is not a confirmed live-server fix.** We verified the original Alpha class can be transformed and linked by the JVM and stress-tested 250,000+ queued lighting updates with no stack overflow, plus the prior offline mock protocol tests. No live Jartex multiplayer test has yet been run on v0.8.1.

## Download and installation

Download `AlphaModern-776-v0.8.1.jar`. In Prism Launcher 11.1.1, open the Alpha 1.0.15 instance's agent/mod settings, remove the old v0.8.0 AlphaModern agent, and add the new one. **Do not enable two agent versions simultaneously**. In custom JVM arguments, if set manually, use the absolute path:

```
-javaagent:/home/YOUR_USER/Downloads/AlphaModern-776-v0.8.1.jar
```

Keep `-Dalpha.modern.captureBadChunks=true` only if you need to debug rejected chunks. There is no need to reset the Microsoft login cache.

## Testing

```
sh build.sh
ALPHA_JAR=/path/to/minecraft-a1.0.15-client.jar sh test.sh
```

Inspect the original world-light bytecode transformation directly:

```
java -jar AlphaModern-776-v0.8.1.jar patch-world-dump /path/to/minecraft-a1.0.15-client.jar cj-fixed.class
javap -p -c cj-fixed.class
```

`cj.a(bu,int,int,int,int,int,int,boolean)` should contain `invokestatic alphamodern/WorldLightGuard.offer:(Ljava/util/List;Ljava/lang/Object;)Z` at offset 155, replacing the old 5-byte `invokeinterface List.add` with an equivalent static call and two NOPs.

## Remaining limitations

This is a best-effort Alpha/modern protocol bridge. Modern terrain above Alpha's 128-block projection, blocks, entity and inventory sync, and other gameplay features remain incomplete. The old Alpha lighting queue is capped intentionally: it can lead to stale local lighting until the modern server sends an updated chunk or light packet. Source and simulated tests are included. Do not distribute your saved Microsoft credentials.

---
