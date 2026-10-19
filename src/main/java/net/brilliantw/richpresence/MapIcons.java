package net.brilliantw.richpresence;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SpawnEggItem;

public final class MapIcons {
	public static final int OUTLINE = 0xFF101317;
	private static final Map<String, ItemStack> ITEMS = new HashMap<>();
	private static final Map<EntityType<?>, ItemStack> EGGS = new HashMap<>();

	private MapIcons() {}

	public static ItemStack item(String id) {
		return ITEMS.computeIfAbsent(id, key -> {
			ItemStack stack = new ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.withDefaultNamespace(key)));
			return stack.isEmpty() ? new ItemStack(Items.COMPASS) : stack;
		});
	}

	public static ItemStack egg(EntityType<?> type) {
		return EGGS.computeIfAbsent(type, key -> {
			//? if >=26.1 {
			return SpawnEggItem.byId(key).map(ItemStack::new).orElse(ItemStack.EMPTY);
			//?} else {
			/*SpawnEggItem egg = SpawnEggItem.byId(key);
			return egg == null ? ItemStack.EMPTY : new ItemStack(egg);
			*///?}
		});
	}

	public static void disc(Gfx gfx, int cx, int cy, double radius, int color) {
		int r = (int) Math.ceil(radius);
		for (int dy = -r; dy < r; dy++) {
			double y = dy + 0.5;
			double half = radius * radius - y * y;
			if (half <= 0) {
				continue;
			}
			int w = (int) Math.round(Math.sqrt(half));
			if (w > 0) {
				gfx.fill(cx - w, cy + dy, cx + w, cy + dy + 1, color);
			}
		}
	}

	public static void badge(Gfx gfx, int cx, int cy, int radius, int ring, ItemStack stack) {
		disc(gfx, cx, cy, radius + 0.5, OUTLINE);
		disc(gfx, cx, cy, radius - 0.5, ring);
		disc(gfx, cx, cy, radius - 1.6, MapMath.mix(ring, 0xFF151A20, 0.62) | 0xFF000000);
		if (!stack.isEmpty()) {
			float scale = (radius * 2 - 3) / 16f;
			gfx.push(cx - 8 * scale, cy - 8 * scale, scale);
			gfx.item(stack, 0, 0);
			gfx.pop();
		}
	}
}
