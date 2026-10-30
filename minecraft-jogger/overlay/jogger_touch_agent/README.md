# Jogger touch agent

This Java 8 compatible `premain` agent runs inside Minecraft's JVM. It receives virtual
button and joystick transitions over an authenticated loopback socket, validates and
orders them, removes duplicate presses, tracks held inputs, and emits releases when the
launcher pauses, the session changes, or heartbeats stop. Android forwards the resulting
events into Amethyst's existing native GLFW/SDL queue. This avoids Minecraft name mappings
and does not transform game classes, use Mixins, or depend on a mod loader.

The Android endpoint binds only to `127.0.0.1` on a random port and generates a fresh
256-bit token for each launch. Nothing is sent to an external server. The agent never
reads the player's account. Old queued presses are rejected after an epoch change,
including when the game returns from the background. Before the agent connects, and if
it disconnects, touch controls use the native path. Camera swipes, keyboard typing,
physical peripherals, and existing hotbar gestures remain on that native path throughout.

`JoggerControls` installs an editable layout with a large left joystick and right-side
jump, mine, use, and sneak buttons, plus sprint, inventory, chat, pause, and keyboard.
The layout resembles familiar mobile controls; it does not replace Minecraft Java's
inventory UI with the Bedrock inventory UI.

## Build and checks

The launcher build includes `:jogger_touch_agent:jar` and packages it as
`assets/components/jogger_touch_agent/jogger_touch_agent.jar`. `Tools.launchMinecraft`
adds the component's `-javaagent` option. `MainActivity` cancels control state on pause.

Run `./gradlew :jogger_touch_agent:verifyTouchProtocol` for the dependency-free state tests.
The integration test starts the actual packaged Java agent and verifies its handshake,
event ordering, stale-input rejection, and the heartbeat watchdog:

```sh
python3 jogger_touch_agent/tests/agent_socket_test.py "$JAVA_HOME/bin/java" \
  app_pojavlauncher/src/main/assets/components/jogger_touch_agent/jogger_touch_agent.jar \
  jogger_touch_agent/build/classes/java/test
```

The Android setting `jogger_touch_agent` defaults to true and can disable the agent while
keeping the editable mobile layout and native touch path available.
