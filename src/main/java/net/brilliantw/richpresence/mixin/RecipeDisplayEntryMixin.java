package net.brilliantw.richpresence.mixin;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import net.brilliantw.richpresence.ExactStackedContents;
import net.brilliantw.richpresence.RichPresenceModClient;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.StackedContents;
import net.minecraft.world.entity.player.StackedItemContents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.display.RecipeDisplay;
import net.minecraft.world.item.crafting.display.RecipeDisplayEntry;
import net.minecraft.world.item.crafting.display.ShapedCraftingRecipeDisplay;
import net.minecraft.world.item.crafting.display.ShapelessCraftingRecipeDisplay;
import net.minecraft.world.item.crafting.display.SlotDisplay;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(RecipeDisplayEntry.class)
public abstract class RecipeDisplayEntryMixin {
	@Shadow @Final private RecipeDisplay display;
	@Shadow @Final private Optional<List<Ingredient>> craftingRequirements;

	@Inject(method = "canCraft", at = @At("HEAD"), cancellable = true)
	private void brilliant$canCraftExact(StackedItemContents contents, CallbackInfoReturnable<Boolean> cir) {
		if (craftingRequirements.isEmpty() || !RichPresenceModClient.isOnBrilliantServer() || !net.brilliantw.richpresence.ModSettings.get().recipeFix) {
			return;
		}
		List<SlotDisplay> slots;
		if (display instanceof ShapedCraftingRecipeDisplay shaped) {
			slots = shaped.ingredients();
		} else if (display instanceof ShapelessCraftingRecipeDisplay shapeless) {
			slots = shapeless.ingredients();
		} else {
			return;
		}
		List<SlotDisplay> filled = slots.stream().filter(slot -> !(slot instanceof SlotDisplay.Empty)).toList();
		List<Ingredient> requirements = craftingRequirements.get();
		if (filled.size() != requirements.size()) {
			return;
		}

		List<StackedContents.IngredientInfo<ItemStack>> infos = new ArrayList<>();
		for (int i = 0; i < filled.size(); i++) {
			List<ItemStack> exact = brilliant$exactStacks(filled.get(i)).toList();
			Ingredient ingredient = requirements.get(i);
			infos.add(exact.isEmpty()
					? stack -> Inventory.isUsableForCrafting(stack) && ingredient.test(stack)
					: stack -> exact.stream().anyMatch(e -> ItemStack.isSameItemSameComponents(e, stack)));
		}
		cir.setReturnValue(((ExactStackedContents) contents).brilliant$exact().tryPick(infos, 1, null));
	}

	private static Stream<ItemStack> brilliant$exactStacks(SlotDisplay slot) {
		if (slot instanceof SlotDisplay.ItemStackSlotDisplay stackDisplay) {
			//? if >=26.1 {
			return Stream.of(stackDisplay.stack().create());
			//?} else {
			/*return Stream.of(stackDisplay.stack());
			*///?}
		}
		if (slot instanceof SlotDisplay.Composite composite) {
			return composite.contents().stream().flatMap(RecipeDisplayEntryMixin::brilliant$exactStacks);
		}
		return Stream.empty();
	}
}
