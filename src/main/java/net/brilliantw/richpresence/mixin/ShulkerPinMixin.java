package net.brilliantw.richpresence.mixin;

import java.util.List;
import net.brilliantw.richpresence.ShulkerPreview;
import net.minecraft.client.Minecraft;
//? if >=26.1 {
import net.minecraft.client.gui.GuiGraphicsExtractor;
//?} else {
/*import net.minecraft.client.gui.GuiGraphics;
*///?}
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractContainerScreen.class)
public abstract class ShulkerPinMixin {
	@Shadow protected Slot hoveredSlot;

	@Shadow
	protected abstract List<Component> getTooltipFromContainerItem(ItemStack stack);

	//? if >=26.1 {
	@Inject(method = "extractTooltip", at = @At("HEAD"), cancellable = true)
	private void brilliant$pinnedPreview(GuiGraphicsExtractor graphics, int mouseX, int mouseY, CallbackInfo ci) {
	//?} else {
	/*@Inject(method = "renderTooltip", at = @At("HEAD"), cancellable = true)
	private void brilliant$pinnedPreview(GuiGraphics graphics, int mouseX, int mouseY, CallbackInfo ci) {
	*///?}
		AbstractContainerScreen<?> screen = (AbstractContainerScreen<?>) (Object) this;
		ItemStack hovered = hoveredSlot != null && screen.getMenu().getCarried().isEmpty() ? hoveredSlot.getItem() : ItemStack.EMPTY;
		ItemStack stack = ShulkerPreview.pinned(screen, hovered, mouseX, mouseY);
		if (stack == null) {
			return;
		}
		graphics.setTooltipForNextFrame(Minecraft.getInstance().font, getTooltipFromContainerItem(stack), stack.getTooltipImage(),
				ShulkerPreview.pinX(), ShulkerPreview.pinY(), stack.get(DataComponents.TOOLTIP_STYLE));
		ci.cancel();
	}

	@Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
	private void brilliant$blockClicksWhilePinned(MouseButtonEvent event, boolean doubleClick, CallbackInfoReturnable<Boolean> cir) {
		if (ShulkerPreview.isPinned(this)) {
			cir.setReturnValue(true);
		}
	}
}
