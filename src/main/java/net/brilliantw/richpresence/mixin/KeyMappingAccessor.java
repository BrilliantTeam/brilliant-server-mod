package net.brilliantw.richpresence.mixin;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(KeyMapping.class)
public interface KeyMappingAccessor {
	@Accessor("clickCount")
	int brilliant$getClickCount();

	@Accessor("clickCount")
	void brilliant$setClickCount(int count);

	@Accessor("key")
	InputConstants.Key brilliant$key();
}
