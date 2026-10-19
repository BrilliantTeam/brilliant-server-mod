package net.brilliantw.richpresence.mixin;

import net.brilliantw.richpresence.NightVision;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(GameRenderer.class)
public class NightVisionScaleMixin {
	//? if >=26.2 {
	@Inject(method = "nightVisionScale", at = @At("HEAD"), cancellable = true)
	//?} else {
	/*@Inject(method = "getNightVisionScale", at = @At("HEAD"), cancellable = true)
	*///?}
	private static void brilliant$fullbright(LivingEntity entity, float partialTick, CallbackInfoReturnable<Float> cir) {
		if (entity instanceof LocalPlayer && NightVision.active() && !entity.hasEffect(MobEffects.NIGHT_VISION)) {
			cir.setReturnValue(1.0f);
		}
	}
}
