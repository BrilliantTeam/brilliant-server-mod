package net.brilliantw.richpresence.mixin;

import net.brilliantw.richpresence.CommandKeys;
import net.brilliantw.richpresence.Freecam;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
public class MouseHandlerMixin {
	@Inject(method = "onButton", at = @At("HEAD"))
	private void brilliant$trackButton(long window, MouseButtonInfo info, int action, CallbackInfo ci) {
		CommandKeys.mouse(info.button(), action != 0);
	}

	@Redirect(method = "turnPlayer", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;turn(DD)V"))
	private void brilliant$freecamTurn(LocalPlayer player, double dx, double dy) {
		if (Freecam.active()) {
			Freecam.turn(dx, dy);
		} else {
			player.turn(dx, dy);
		}
	}

	@Inject(method = "onScroll", at = @At("HEAD"), cancellable = true)
	private void brilliant$freecamScroll(long window, double xOffset, double yOffset, CallbackInfo ci) {
		if (Freecam.scroll(yOffset)) {
			ci.cancel();
		}
	}
}
