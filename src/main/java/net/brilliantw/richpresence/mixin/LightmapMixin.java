package net.brilliantw.richpresence.mixin;

import net.brilliantw.richpresence.NightVision;
import net.minecraft.client.player.LocalPlayer;
//? if >=26.1 {
import net.minecraft.client.renderer.LightmapRenderStateExtractor;
//?} else {
/*import net.minecraft.client.renderer.LightTexture;
*///?}
import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffects;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

//? if >=26.1 {
@Mixin(LightmapRenderStateExtractor.class)
//?} else {
/*@Mixin(LightTexture.class)
*///?}
public class LightmapMixin {
	//? if >=26.1 {
	@Redirect(method = "extract", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;hasEffect(Lnet/minecraft/core/Holder;)Z"), require = 0)
	//?} else {
	/*@Redirect(method = "updateLightTexture", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;hasEffect(Lnet/minecraft/core/Holder;)Z"), require = 0)
	*///?}
	private boolean brilliant$nightVision(LocalPlayer player, Holder<MobEffect> effect) {
		return effect == MobEffects.NIGHT_VISION && NightVision.active() || player.hasEffect(effect);
	}
}
