package net.brilliantw.richpresence.mixin;

import net.brilliantw.richpresence.Freecam;
import net.minecraft.client.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Camera.class)
public abstract class CameraMixin {
	@Shadow
	protected abstract void setRotation(float yRot, float xRot);

	@Shadow
	protected abstract void setPosition(double x, double y, double z);

	//? if >=26.1 {
	@Inject(method = "alignWithEntity", at = @At("TAIL"))
	//?} else {
	/*@Inject(method = "setup", at = @At("TAIL"))
	*///?}
	private void brilliant$freecam(CallbackInfo ci) {
		if (Freecam.active()) {
			Freecam.frame();
			setRotation(Freecam.yaw(), Freecam.pitch());
			setPosition(Freecam.x(), Freecam.y(), Freecam.z());
		}
	}
}
