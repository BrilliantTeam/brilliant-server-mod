package net.brilliantw.richpresence.mixin;

import net.brilliantw.richpresence.ModSettings;
import net.brilliantw.richpresence.RichPresenceModClient;
import net.minecraft.client.gui.screens.inventory.AnvilScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

@Mixin(AnvilScreen.class)
public abstract class AnvilScreenMixin {
	//? if >=26.1 {
	@ModifyConstant(method = "extractLabels", constant = @Constant(intValue = 40))
	//?} else {
	/*@ModifyConstant(method = "renderLabels", constant = @Constant(intValue = 40))
	*///?}
	private int brilliant$liftTooExpensive(int limit) {
		return RichPresenceModClient.isOnBrilliantServer() && ModSettings.get().anvilCost ? Integer.MAX_VALUE : limit;
	}
}
