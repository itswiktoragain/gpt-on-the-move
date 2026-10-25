package io.github.itswiktoragain.cursedcraft.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.entity.state.ZombieEntityRenderState;
import net.minecraft.util.Identifier;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class SkinCache {
    private static final Map<ZombieEntityRenderState, String> IDENTITIES = new IdentityHashMap<>();
    private static final Map<String, Identifier> CACHE = new ConcurrentHashMap<>();
    private SkinCache() {}

    public static void remember(ZombieEntityRenderState state, String name) {
        IDENTITIES.put(state, name);
    }

    public static Identifier get(ZombieEntityRenderState state, Identifier fallback) {
        String name = IDENTITIES.get(state);
        if (name == null) return fallback;
        // The client skin provider uses authenticated profile textures when available.
        return CACHE.computeIfAbsent(name, key -> {
            try {
                var session = MinecraftClient.getInstance().getSessionService();
                var profile = new com.mojang.authlib.GameProfile(java.util.UUID.nameUUIDFromBytes(
                        ("OfflinePlayer:" + key).getBytes(java.nio.charset.StandardCharsets.UTF_8)), key);
                var textures = MinecraftClient.getInstance().getSkinProvider().getSkinTextures(profile);
                return textures.texture();
            } catch (Exception ignored) {
                return fallback;
            }
        });
    }
}
