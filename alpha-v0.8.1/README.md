# AlphaModern 0.8.1 - Stack overflow fix

The October 24 live Jartex log confirms terrain now loads, but Alpha 1.0.15 crashes in a recursive lighting work queue (cj.e -> kb.a -> cj.a -> cj.e).

The 0.8.1 agent is one full Java agent JAR based on 0.8.0 plus an in-process safety transformer. It caps legacy lighting work at 8192 entries while the modern multiplayer BridgeSocket is open. It keeps original behavior outside bridged multiplayer. The safety hook replaces one 5-byte invokeinterface List.add with a methodref to WorldLightGuard.offer plus two NOPs, preserving Alpha's bytecode length. It does not patch the game jar on disk.

To install in Prism Launcher, remove 0.8.0 from the instance agent list; add AlphaModern-776-v0.8.1.jar. Enable only one AlphaModern agent. Old cached Microsoft credentials are left untouched.

To inspect the patch: java -jar AlphaModern-776-v0.8.1.jar patch-world-dump path/to/minecraft-a1.0.15-client.jar cj-fixed.class

Build: sh alpha-v0.8.1/build.sh (requires JDK 8+, original 0.8.0 jar and source ZIP alongside this directory).

Local testing against the provided original Alpha 1.0.15 JAR confirmed the modified class loads and a 250,000-job stress test remains bounded. The earlier mock authentication and terrain tests still pass. No live server verification of 0.8.1 is claimed. Capped lighting can sometimes remain stale until the server updates a chunk; full modern inventory and entity translation are still incomplete.
