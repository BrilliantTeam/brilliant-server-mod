package net.brilliantw.richpresence.mixin;

import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(targets = "net.minecraft.client.multiplayer.prediction.BlockStatePredictionHandler$ServerVerifiedState")
public interface ServerVerifiedStateAccessor {
	@Accessor("blockState")
	BlockState brilliant$getBlockState();
}
