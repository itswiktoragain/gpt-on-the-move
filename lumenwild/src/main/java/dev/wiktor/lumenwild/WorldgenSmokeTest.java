package dev.wiktor.lumenwild;

import net.minecraft.block.BlockState;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

/**
 * CI-only test: actually creates chunks and checks for aetherstone islands,
 * empty sky and a deep void. Normal Minecraft launches never run this.
 */
public final class WorldgenSmokeTest {
    private WorldgenSmokeTest() {}

    public static void verify(MinecraftServer server) {
        ServerWorld sky = server.getWorld(LumenwildMod.LUMENWILD);
        if (sky == null) throw new IllegalStateException("Lumenwild dimension was not registered");

        int nativeStone = 0, openSky = 0, visibleVoid = 0, upperBlocks = 0, lowerBlocks = 0;
        for (int x = 0; x <= 96; x += 48) {
            for (int z = 0; z <= 96; z += 48) {
                for (int y = -40; y <= 300; y += 4) {
                    BlockState state = sky.getBlockState(new BlockPos(x, y, z));
                    if (state.isOf(LumenwildMod.AETHERSTONE)) {
                        nativeStone++;
                        if (y >= 115) upperBlocks++;
                        if (y <= 70) lowerBlocks++;
                    }
                    if (state.isAir()) {
                        openSky++;
                        if (y <= 5) visibleVoid++;
                    }
                }
            }
        }
        if (nativeStone < 8 || openSky < 40 || visibleVoid < 20
                || upperBlocks == 0 || lowerBlocks == 0) {
            throw new IllegalStateException("Lumenwild terrain test FAILED: stone="
                + nativeStone + " sky=" + openSky + " void=" + visibleVoid
                + " high=" + upperBlocks + " low=" + lowerBlocks);
        }
        System.out.println("LUMENWILD WORLDGEN SMOKE TEST PASSED: nativeStone=" + nativeStone
            + " air=" + openSky + " void=" + visibleVoid
            + " highLayer=" + upperBlocks + " lowLayer=" + lowerBlocks);
    }
}
