package net.brilliantw.richpresence.mixin;

import com.mojang.blaze3d.font.UnbakedGlyph;
import net.brilliantw.richpresence.StickerAnimator;
import net.minecraft.client.gui.font.glyphs.BakedGlyph;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "net.minecraft.client.gui.font.providers.BitmapProvider$Glyph")
public class BitmapGlyphMixin {
	@Inject(method = "bake", at = @At("RETURN"))
	private void brilliant$trackSticker(UnbakedGlyph.Stitcher stitcher, CallbackInfoReturnable<BakedGlyph> cir) {
		StickerAnimator.baked(this, cir.getReturnValue());
	}
}
