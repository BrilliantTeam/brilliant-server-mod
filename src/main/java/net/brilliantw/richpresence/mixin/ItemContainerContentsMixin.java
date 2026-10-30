package net.brilliantw.richpresence.mixin;

import java.util.List;
import java.util.function.Consumer;
import net.brilliantw.richpresence.ShulkerPreview;
import net.minecraft.core.component.DataComponentGetter;
//? if <26.1 {
/*import net.minecraft.core.NonNullList;
*///?}
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
//? if >=26.1 {
import java.util.Optional;
import net.minecraft.world.item.ItemStackTemplate;
//?}
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.ItemContainerContents;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemContainerContents.class)
public abstract class ItemContainerContentsMixin implements ShulkerPreview.Slots {
	//? if >=26.1 {
	@Shadow @Final private List<Optional<ItemStackTemplate>> items;

	@Override
	public List<ItemStack> brilliant$slots() {
		return items.stream().map(slot -> slot.map(t -> new ItemStack(t.item(), t.count(), t.components())).orElse(ItemStack.EMPTY)).toList();
	}
	//?} else {
	/*@Shadow @Final private NonNullList<ItemStack> items;

	@Override
	public List<ItemStack> brilliant$slots() {
		return items;
	}
	*///?}

	@Inject(method = "addToTooltip", at = @At("HEAD"), cancellable = true)
	private void brilliant$replaceWithPreview(Item.TooltipContext context, Consumer<Component> consumer, TooltipFlag flag, DataComponentGetter components, CallbackInfo ci) {
		if (ShulkerPreview.of((ItemContainerContents) (Object) this).isPresent()) {
			ci.cancel();
		}
	}
}
