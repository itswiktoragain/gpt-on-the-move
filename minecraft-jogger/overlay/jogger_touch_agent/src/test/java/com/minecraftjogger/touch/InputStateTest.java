package com.minecraftjogger.touch;

import java.util.Arrays;
import java.util.List;

/** Dependency-free executable checks for the failure modes that can leave a player moving/mining. */
public final class InputStateTest {
    private static void expect(List<String> actual, String... expected) {
        if (!actual.equals(Arrays.asList(expected))) throw new AssertionError(actual + " != " + Arrays.toString(expected));
    }
    public static void main(String[] args) {
        InputState state = new InputState();
        expect(state.accept("K 1 87 119 0 0 1"), "K 1 87 119 0 0 1");
        expect(state.accept("K 1 87 119 0 0 1"));
        expect(state.accept("M 1 0 0 1"), "M 1 0 0 1");
        expect(state.accept("P 1"));
        expect(state.accept("R 2"), "K 2 87 119 0 0 0", "M 2 0 0 0");
        expect(state.accept("K 1 87 119 0 0 1")); // late press after pause cannot restart movement
        expect(state.accept("K 2 87 119 0 0 0"), "K 2 87 119 0 0 0"); // startup fallback release
        expect(state.accept("K 2 340 0 0 1 1"), "K 2 340 0 0 1 1");
        expect(state.release(), "K 2 340 0 0 0 0"); // watchdog release
        expect(state.release());
        expect(state.accept("M 2 8 0 1"));
        expect(state.accept("K 999 87 119 0 0 4")); // invalid future message must not advance epoch
        expect(state.accept("K 3 87 119 0 0 1"), "K 3 87 119 0 0 1");
        expect(state.accept("R -1"));
        expect(state.accept("K 3 33 0 0 128 1"));
        expect(state.accept("K three 87 0 0 0 1"));
        expect(state.accept("M 3 0 0 1 extra"));
        expect(state.accept("P 4"), "K 4 87 119 0 0 0");
        System.out.println("Touch protocol: ordered state, duplicates, epoch reset, startup fallback, watchdog and malformed-input checks passed.");
    }
}
