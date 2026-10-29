package dev.wiktor.unboundhorizons.mixin;

import java.util.Locale;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.option.VideoOptionsScreen;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.resource.language.I18n;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(VideoOptionsScreen.class)
public abstract class VideoOptionsScreenMixin {
    @Inject(method = "init", at = @At("TAIL"), require = 0)
    private void unboundhorizons$hideViewDistanceOption(CallbackInfo ci) {
        String label = I18n.translate("options.renderDistance");
        int marker = label.indexOf('%');
        if (marker >= 0) label = label.substring(0, marker);
        label = label.trim().toLowerCase(Locale.ROOT);
        if (label.isEmpty()) return;

        for (Element element : ((Screen) (Object) this).children()) {
            if (element instanceof ClickableWidget widget) {
                String title = widget.getMessage().getString().trim().toLowerCase(Locale.ROOT);
                if (title.startsWith(label)) widget.visible = false;
            }
        }
    }
}
