package net.brilliantw.richpresence.mixin;

import net.brilliantw.richpresence.FakeChunks;
import net.minecraft.client.Options;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Options.class)
public abstract class OptionsMixin {
	@Inject(method = "getEffectiveRenderDistance", at = @At("HEAD"), cancellable = true)
	private void brilliant$clientRenderDistance(CallbackInfoReturnable<Integer> cir) {
		if (FakeChunks.enabled()) {
			cir.setReturnValue(FakeChunks.viewDistance(((Options) (Object) this).renderDistance().get()));
		}
	}
}
