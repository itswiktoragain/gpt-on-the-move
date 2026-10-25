package io.github.itswiktoragain.cursedcraft.client;

import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.yggdrasil.ProfileResult;
import io.github.itswiktoragain.cursedcraft.CursedCraft;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.entity.state.ZombieEntityRenderState;
import net.minecraft.client.util.SkinTextures;
import net.minecraft.util.Identifier;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/** Resolves real online UUIDs instead of caching the default skin for an offline UUID. */
public final class SkinCache {
    private record Identity(String name, String uuid, boolean giant) {}
    private static final Map<ZombieEntityRenderState, Identity> RENDER_IDENTITIES = new IdentityHashMap<>();
    private static final Map<String, CompletableFuture<UUID>> ONLINE_UUIDS = new ConcurrentHashMap<>();
    private static final Map<UUID, CompletableFuture<GameProfile>> SIGNED_PROFILES = new ConcurrentHashMap<>();
    private static final Map<UUID, Supplier<SkinTextures>> SKIN_SUPPLIERS = new ConcurrentHashMap<>();
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(4)).build();

    private SkinCache() {}

    public static void remember(ZombieEntityRenderState state, String name, String uuid, boolean giant) {
        RENDER_IDENTITIES.put(state, new Identity(name, uuid, giant));
    }

    public static boolean isMannequin(ZombieEntityRenderState state) {
        return RENDER_IDENTITIES.containsKey(state);
    }

    public static boolean isGiant(ZombieEntityRenderState state) {
        Identity value = RENDER_IDENTITIES.get(state);
        return value != null && value.giant();
    }

    private static CompletableFuture<UUID> resolve(String name) {
        return ONLINE_UUIDS.computeIfAbsent(name.toLowerCase(java.util.Locale.ROOT), ignored -> {
            if (!name.matches("[a-zA-Z0-9_]{1,16}")) return CompletableFuture.completedFuture(null);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.mojang.com/users/profiles/minecraft/" + name))
                    .timeout(Duration.ofSeconds(7))
                    .header("Accept", "application/json")
                    .GET().build();
            return HTTP.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .thenApply(response -> {
                        if (response.statusCode() != 200) return null;
                        String id = JsonParser.parseString(response.body()).getAsJsonObject().get("id").getAsString();
                        if (!id.matches("[0-9a-fA-F]{32}")) return null;
                        String dashed = id.replaceFirst("(.{8})(.{4})(.{4})(.{4})(.{12})", "$1-$2-$3-$4-$5");
                        return UUID.fromString(dashed);
                    })
                    .exceptionally(ex -> {
                        CursedCraft.LOGGER.debug("Could not resolve Minecraft skin for {}", name, ex);
                        return null;
                    });
        });
    }

    public static Identifier get(ZombieEntityRenderState state, Identifier fallback) {
        Identity identity = RENDER_IDENTITIES.get(state);
        if (identity == null || !io.github.itswiktoragain.cursedcraft.Config.INSTANCE.downloadPlayerSkins) return fallback;
        UUID uuid = null;
        try {
            if (!identity.uuid().isEmpty()) {
                uuid = UUID.fromString(identity.uuid());
            } else {
                uuid = resolve(identity.name()).getNow(null);
            }
            if (uuid == null) return fallback; // Keeps fetching in the background.
            UUID finalId = uuid;
            // Vanilla's skin provider only reads the signed 'textures' property.
            // A GameProfile(UUID, username) alone always falls back to Steve/Alex.
            CompletableFuture<GameProfile> fullProfile = SIGNED_PROFILES.computeIfAbsent(
                    finalId, key -> CompletableFuture.supplyAsync(() -> {
                        try {
                            ProfileResult result = MinecraftClient.getInstance()
                                    .getSessionService().fetchProfile(key, true);
                            return result == null ? null : result.profile();
                        } catch (Exception ex) {
                            CursedCraft.LOGGER.warn("Failed to fetch signed skin for {}", identity.name(), ex);
                            return null;
                        }
                    }));
            GameProfile signed = fullProfile.getNow(null);
            if (signed == null) return fallback;
            Supplier<SkinTextures> supplier = SKIN_SUPPLIERS.computeIfAbsent(
                    finalId, key -> MinecraftClient.getInstance().getSkinProvider()
                            .getSkinTexturesSupplier(signed));
            SkinTextures textures = supplier.get();
            return textures == null ? fallback : textures.texture();
        } catch (Exception ex) {
            CursedCraft.LOGGER.debug("Player skin not ready yet for {}", identity.name(), ex);
            return fallback;
        }
    }
}
