package net.brilliantw.richpresence.mixin;

import net.brilliantw.richpresence.FakeChunks;
import net.minecraft.client.multiplayer.ClientChunkCache;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(ClientChunkCache.class)
public abstract class ClientChunkCacheMixin {
	@ModifyVariable(method = "calculateStorageRange", at = @At("HEAD"), argsOnly = true)
	private static int brilliant$keepFarChunks(int radius) {
		return FakeChunks.storageRadius(radius);
	}
}
