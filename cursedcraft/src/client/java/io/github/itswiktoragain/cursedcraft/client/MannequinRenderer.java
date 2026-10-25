package io.github.itswiktoragain.cursedcraft.client;

import io.github.itswiktoragain.cursedcraft.MannequinEntity;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.ZombieEntityRenderer;
import net.minecraft.client.render.entity.state.ZombieEntityRenderState;
import net.minecraft.util.Identifier;

public final class MannequinRenderer extends ZombieEntityRenderer {
    private static final Identifier FALLBACK = Identifier.ofVanilla("textures/entity/player/wide/steve.png");

    public MannequinRenderer(EntityRendererFactory.Context ctx) {
        super(ctx);
    }

    @Override
    public Identifier getTexture(ZombieEntityRenderState state) {
        return SkinCache.get(state, FALLBACK);
    }

    @Override
    public void updateRenderState(net.minecraft.entity.mob.ZombieEntity entity, ZombieEntityRenderState state, float tickDelta) {
        super.updateRenderState(entity, state, tickDelta);
        if (entity instanceof MannequinEntity mannequin) {
            SkinCache.remember(state, mannequin.getSkinName(), mannequin.getSkinUuid(), mannequin.isGiant());
        }
    }
}
