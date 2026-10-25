package io.github.itswiktoragain.cursedcraft;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class Config {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    public static Config INSTANCE = new Config();

    public int walkBlocksUntilLegsGone = 320;
    public int mannequinRollIntervalTicks = 140;
    public double mannequinSpawnChance = 0.18;
    public int maxMannequinsNearby = 5;
    public int corruptionRadiusBlocks = 500;
    public int corruptionPatchesPerTick = 14;
    public int fallingBlocksPerTick = 128;
    public int giantCheckIntervalTicks = 900;
    public double giantSpawnChance = 0.65;
    public double randomBorderTeleportChance = 0.00022;
    public boolean deleteCurrentSingleplayerWorld = true;
    public boolean downloadPlayerSkins = true;

    private Config() {}

    public static void load() {
        Path path = FabricLoader.getInstance().getConfigDir().resolve("cursedcraft.json");
        try {
            Files.createDirectories(path.getParent());
            if (Files.isRegularFile(path)) {
                Config parsed = GSON.fromJson(Files.readString(path, StandardCharsets.UTF_8), Config.class);
                if (parsed != null) INSTANCE = parsed;
            }
            INSTANCE.walkBlocksUntilLegsGone = Math.max(10, INSTANCE.walkBlocksUntilLegsGone);
            INSTANCE.mannequinRollIntervalTicks = Math.max(20, INSTANCE.mannequinRollIntervalTicks);
            INSTANCE.mannequinSpawnChance = Math.max(0, Math.min(1, INSTANCE.mannequinSpawnChance));
            INSTANCE.maxMannequinsNearby = Math.max(0, Math.min(20, INSTANCE.maxMannequinsNearby));
            INSTANCE.corruptionRadiusBlocks = Math.max(16, Math.min(500, INSTANCE.corruptionRadiusBlocks));
            INSTANCE.corruptionPatchesPerTick = Math.max(1, Math.min(40, INSTANCE.corruptionPatchesPerTick));
            INSTANCE.fallingBlocksPerTick = Math.max(16, Math.min(256, INSTANCE.fallingBlocksPerTick));
            INSTANCE.giantCheckIntervalTicks = Math.max(80, INSTANCE.giantCheckIntervalTicks);
            INSTANCE.giantSpawnChance = Math.max(0, Math.min(1, INSTANCE.giantSpawnChance));
            INSTANCE.randomBorderTeleportChance = Math.max(0, Math.min(0.01, INSTANCE.randomBorderTeleportChance));
            Files.writeString(path, GSON.toJson(INSTANCE) + "\n", StandardCharsets.UTF_8);
        } catch (Exception e) {
            CursedCraft.LOGGER.warn("Could not load CursedCraft configuration", e);
        }
    }
}
