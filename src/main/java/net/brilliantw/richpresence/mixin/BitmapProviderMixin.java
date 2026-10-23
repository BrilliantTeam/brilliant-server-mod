package net.brilliantw.richpresence.mixin;

import com.mojang.blaze3d.font.UnbakedGlyph;
import net.brilliantw.richpresence.StickerAnimator;
import net.minecraft.client.gui.font.providers.BitmapProvider;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BitmapProvider.class)
public class BitmapProviderMixin {
	@Inject(method = "getGlyph", at = @At("RETURN"))
	private void brilliant$rememberSticker(int codepoint, CallbackInfoReturnable<UnbakedGlyph> cir) {
		StickerAnimator.unbaked(codepoint, cir.getReturnValue());
	}
}
