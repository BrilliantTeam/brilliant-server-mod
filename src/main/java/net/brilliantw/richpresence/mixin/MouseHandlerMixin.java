package net.brilliantw.richpresence.mixin;

import net.brilliantw.richpresence.CommandKeys;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.input.MouseButtonInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
public class MouseHandlerMixin {
	@Inject(method = "onButton", at = @At("HEAD"))
	private void brilliant$trackButton(long window, MouseButtonInfo info, int action, CallbackInfo ci) {
		CommandKeys.mouse(info.button(), action != 0);
	}
}
