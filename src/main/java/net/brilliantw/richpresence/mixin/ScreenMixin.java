package net.brilliantw.richpresence.mixin;

import java.net.URI;
import net.brilliantw.richpresence.DiscordImageLinks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Screen.class)
public abstract class ScreenMixin {
	@Inject(method = "clickUrlAction", at = @At("HEAD"), cancellable = true)
	private static void brilliant$openDiscordImage(Minecraft minecraft, Screen screen, URI uri, CallbackInfoReturnable<Boolean> cir) {
		if (DiscordImageLinks.openUrl(uri)) {
			cir.setReturnValue(true);
		}
	}
}
