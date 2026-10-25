package io.github.itswiktoragain.cursedcraft.client;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

public final class ThrowawayWarningScreen extends Screen {
    private final Screen parent;

    public ThrowawayWarningScreen(Screen parent) {
        super(Text.literal("CursedCraft - destructive mod warning"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int buttonWidth = Math.min(310, Math.max(155, width - 30));
        int buttonY = height / 2 + 77;
        addDrawableChild(ButtonWidget.builder(
                        Text.literal("I UNDERSTAND - CONTINUE AT MY OWN RISK"),
                        button -> client.setScreen(parent))
                .dimensions(width / 2 - buttonWidth / 2, buttonY, buttonWidth, 22).build());
        addDrawableChild(ButtonWidget.builder(
                        Text.literal("QUIT MINECRAFT (RECOMMENDED)"),
                        button -> client.scheduleStop())
                .dimensions(width / 2 - buttonWidth / 2, buttonY + 29, buttonWidth, 22).build());
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        ctx.fill(0, 0, width, height, 0xF7050505);
        ctx.fill(8, 10, width - 8, height - 10, 0xE8380808);
        ctx.fill(14, 16, width - 14, height - 16, 0xF20D0D12);
        ctx.getMatrices().push();
        ctx.getMatrices().translate(width / 2.0, height / 2.0 - 100.0, 0);
        ctx.getMatrices().scale(2.6f, 2.6f, 1.0f);
        ctx.drawCenteredTextWithShadow(textRenderer, Text.literal("!! DANGER !!"), 0, 0, 0xFF3333);
        ctx.getMatrices().pop();
        int cx = width / 2;
        ctx.drawCenteredTextWithShadow(textRenderer,
                Text.literal("CURSEDCRAFT WILL PERMANENTLY DELETE THE WORLD YOU PLAY IN."), cx,
                height / 2 - 59, 0xFFDD55);
        ctx.drawCenteredTextWithShadow(textRenderer,
                Text.literal("USE A THROWAWAY MINECRAFT INSTANCE ONLY!"), cx,
                height / 2 - 41, 0xFF5555);
        ctx.drawCenteredTextWithShadow(textRenderer,
                Text.literal("DO NOT PUT THIS MOD IN AN INSTANCE WITH IMPORTANT WORLDS."), cx,
                height / 2 - 24, 0xFFFFFF);
        ctx.drawCenteredTextWithShadow(textRenderer,
                Text.literal("ANY DEATH or LOSING YOUR LEGS starts a 5-second corruption sequence."), cx,
                height / 2 - 5, 0xBBBBBB);
        ctx.drawCenteredTextWithShadow(textRenderer,
                Text.literal("Chunks from OTHER SEEDS are injected. Then the active world is erased."), cx,
                height / 2 + 10, 0xBBBBBB);
        ctx.drawCenteredTextWithShadow(textRenderer,
                Text.literal("THIS IS NOT A JOKE. MAKE AN EXTERNAL BACKUP BEFORE CONTINUING."), cx,
                height / 2 + 38, 0xFF7777);
        super.render(ctx, mouseX, mouseY, delta);
    }

    @Override
    public void close() {
        // Escape must not silently bypass this warning.
    }
}
