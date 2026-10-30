package net.brilliantw.richpresence.mixin;

import java.util.Optional;
import net.brilliantw.richpresence.ShulkerPreview;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Item.class)
public abstract class ItemTooltipImageMixin {
	@Inject(method = "getTooltipImage", at = @At("HEAD"), cancellable = true)
	private void brilliant$shulkerPreview(ItemStack stack, CallbackInfoReturnable<Optional<TooltipComponent>> cir) {
		ShulkerPreview.of(stack).ifPresent(preview -> cir.setReturnValue(Optional.of(preview)));
	}
}
