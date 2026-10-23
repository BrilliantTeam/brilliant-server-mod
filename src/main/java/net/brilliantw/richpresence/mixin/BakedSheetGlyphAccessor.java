package net.brilliantw.richpresence.mixin;

//? if >=26.3 {
/*import com.mojang.renderpearl.api.textures.GpuTextureView;
*///?} else {
import com.mojang.blaze3d.textures.GpuTextureView;
//?}
import net.minecraft.client.gui.font.glyphs.BakedSheetGlyph;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(BakedSheetGlyph.class)
public interface BakedSheetGlyphAccessor {
	@Accessor("textureView")
	GpuTextureView brilliant$textureView();

	@Accessor("u0")
	float brilliant$u0();

	@Accessor("v0")
	float brilliant$v0();

	@Accessor("u1")
	float brilliant$u1();

	@Accessor("v1")
	float brilliant$v1();
}
