package net.kdt.pojavlaunch.jogger.classic;

import java.util.*;

/** Platform-independent gesture and input ownership rules. */
public final class ClassicLogic {
    private ClassicLogic() {}
    public interface Output { void key(int key, boolean down); void mouse(int button, boolean down); }
    public static final class Inputs {
        private final Output output;
        private final Map<Integer, Set<Integer>> owners = new HashMap<>();
        private final Map<Integer, Integer> counts = new HashMap<>();
        public Inputs(Output output) { this.output = output; }
        public void set(int owner, int... codes) {
            Set<Integer> next = new HashSet<>();
            for (int code : codes) if (code != 0) next.add(code);
            Set<Integer> old = owners.get(owner);
            if (old == null) old = Collections.emptySet();
            for (int code : old) if (!next.contains(code)) {
                int n = counts.get(code) - 1;
                if (n == 0) { counts.remove(code); emit(code, false); }
                else counts.put(code, n);
            }
            for (int code : next) if (!old.contains(code)) {
                int n = counts.containsKey(code) ? counts.get(code) : 0;
                counts.put(code, n + 1); if (n == 0) emit(code, true);
            }
            if (next.isEmpty()) owners.remove(owner); else owners.put(owner, next);
        }
        public boolean held(int code) { return counts.containsKey(code); }
        public void clear() {
            for (int code : new HashSet<>(counts.keySet())) emit(code, false);
            owners.clear(); counts.clear();
        }
        private void emit(int code, boolean down) {
            if (code < 0) output.mouse(-code - 1, down); else output.key(code, down);
        }
    }
    public static final class Gesture {
        public float startX, startY, lastX, lastY;
        public long started;
        public boolean active, moved, mining;
        private float slopSquared;
        public void begin(float x, float y, long time, float slop) {
            startX = lastX = x; startY = lastY = y; started = time;
            slopSquared = slop * slop; active = true; moved = mining = false;
        }
        public void move(float x, float y) {
            float dx = x - startX, dy = y - startY;
            if (dx * dx + dy * dy > slopSquared) moved = true;
        }
        public boolean hold(long time, long threshold) {
            if (!active || moved || mining || time - started < threshold) return false;
            mining = true; return true;
        }
        public boolean end(long time, long threshold, boolean cancelled) {
            boolean place = active && !cancelled && !moved && !mining && time - started < threshold;
            active = false; return place;
        }
        public void cancel() { active = false; moved = true; mining = false; }
    }
    public static final class Sprint {
        private long downAt = -1, shortTapReleased = -1;
        public boolean down(long time) {
            boolean sprint = shortTapReleased >= 0 && time >= shortTapReleased && time - shortTapReleased <= 300;
            downAt = time; shortTapReleased = -1; return sprint;
        }
        public void up(long time) {
            shortTapReleased = downAt >= 0 && time - downAt < 230 ? time : -1;
            downAt = -1;
        }
        public void reset() { downAt = shortTapReleased = -1; }
    }
}
