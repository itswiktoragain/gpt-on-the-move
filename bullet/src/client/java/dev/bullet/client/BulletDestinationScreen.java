package dev.bullet.client;

import dev.bullet.BulletEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
import java.util.Locale;

public final class BulletDestinationScreen extends Screen {
    private TextFieldWidget xField;
    private TextFieldWidget yField;
    private TextFieldWidget zField;
    private Text error = Text.empty();

    public BulletDestinationScreen() {
        super(Text.literal("BULLET / DESTINATION"));
    }

    @Override
    protected void init() {
        int cx = this.width / 2;
        int top = this.height / 2 - 76;
        double px = client.player == null ? 0 : client.player.getX();
        double py = client.player == null ? 80 : client.player.getY();
        double pz = client.player == null ? 0 : client.player.getZ();

        xField = new TextFieldWidget(textRenderer, cx - 104, top + 24, 208, 20, Text.literal("X"));
        yField = new TextFieldWidget(textRenderer, cx - 104, top + 58, 208, 20, Text.literal("Y"));
        zField = new TextFieldWidget(textRenderer, cx - 104, top + 92, 208, 20, Text.literal("Z"));
        xField.setMaxLength(30); yField.setMaxLength(30); zField.setMaxLength(30);
        xField.setText(String.format(Locale.ROOT, "%.2f", px));
        yField.setText(String.format(Locale.ROOT, "%.2f", py));
        zField.setText(String.format(Locale.ROOT, "%.2f", pz));
        addDrawableChild(xField);
        addDrawableChild(yField);
        addDrawableChild(zField);
        addDrawableChild(ButtonWidget.builder(Text.literal("DONE — LAUNCH"), button -> submit())
            .dimensions(cx - 104, top + 123, 208, 20).build());
        setInitialFocus(xField);
    }

    private void submit() {
        try {
            double x = Double.parseDouble(xField.getText().trim());
            double y = Double.parseDouble(yField.getText().trim());
            double z = Double.parseDouble(zField.getText().trim());
            if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)
                || Math.abs(x) > 29999984 || Math.abs(z) > 29999984
                || y < client.world.getBottomY() + 2 || y > client.world.getTopYInclusive() - 2) {
                error = Text.literal("Coordinates are outside the world limits.");
                return;
            }
            if (!(client.player.getVehicle() instanceof BulletEntity)) {
                error = Text.literal("Sit in the Bullet before launching.");
                return;
            }
            // Native server command; server checks that the player really is mounted.
            client.player.networkHandler.sendChatCommand(String.format(Locale.ROOT,
                "bullet_go %.4f %.4f %.4f", x, y, z));
            BulletClient.beginLaunch();
            close();
        } catch (NumberFormatException ex) {
            error = Text.literal("Enter valid numeric X, Y and Z coordinates.");
        }
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        renderBackground(ctx, mouseX, mouseY, delta);
        int cx = width / 2;
        int top = height / 2 - 76;
        ctx.drawCenteredTextWithShadow(textRenderer, title, cx, top - 9, 0xffcf6c);
        ctx.drawTextWithShadow(textRenderer, "X  /  EAST-WEST", cx - 104, top + 12, 0xeeeeee);
        ctx.drawTextWithShadow(textRenderer, "Y  /  ALTITUDE", cx - 104, top + 46, 0xeeeeee);
        ctx.drawTextWithShadow(textRenderer, "Z  /  NORTH-SOUTH", cx - 104, top + 80, 0xeeeeee);
        super.render(ctx, mouseX, mouseY, delta);
        ctx.drawCenteredTextWithShadow(textRenderer, error, cx, top + 154, 0xff7777);
        ctx.drawCenteredTextWithShadow(textRenderer, "Your Bullet travels with you.", cx, top + 168, 0xaaaaaa);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
