package net.brilliantw.richpresence;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.List;
import java.util.Optional;
import net.brilliantw.richpresence.mixin.KeyMappingAccessor;
//? if >=26.1 {
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.ClientTooltipComponentCallback;
import net.minecraft.client.gui.GuiGraphicsExtractor;
//?} else {
/*import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.TooltipComponentCallback;
import net.minecraft.client.gui.GuiGraphics;
*///?}
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;

public final class ShulkerPreview implements TooltipComponent, ClientTooltipComponent {
	public interface Slots {
		List<ItemStack> brilliant$slots();
	}

	private static final String PLUGIN_MARKER = "ShulkerPreviewerLore";
	private static final Identifier TEXTURE = Identifier.withDefaultNamespace("textures/gui/container/shulker_box.png");
	private static final int PANEL_W = 176;
	private static final int BORDER = 7;
	private static final int SLOT = 18;
	private static final int COLUMNS = 9;

	private static ItemContainerContents lastContents;
	private static Optional<ShulkerPreview> last = Optional.empty();
	private static KeyMapping pinKey;
	private static Object pinnedScreen;
	private static ItemStack pinned;
	private static int pinX;
	private static int pinY;
	private static int mouseX;
	private static int mouseY;
	private static boolean nested;

	private final List<ItemStack> slots;

	private ShulkerPreview(List<ItemStack> slots) {
		this.slots = slots;
	}

	public static void register() {
		//? if >=26.1 {
		ClientTooltipComponentCallback.EVENT.register(data -> data instanceof ShulkerPreview preview ? preview : null);
		//?} else {
		/*TooltipComponentCallback.EVENT.register(data -> data instanceof ShulkerPreview preview ? preview : null);
		*///?}
		ItemTooltipCallback.EVENT.register((stack, context, flag, lines) -> {
			if (of(stack).isPresent()) {
				lines.removeIf(ShulkerPreview::fromPlugin);
			}
		});
	}

	static void registerKey(KeyMapping.Category category) {
		KeyMapping key = new KeyMapping("key.brilliant-server-mod.shulker_pin", InputConstants.KEY_LSHIFT, category);
		//? if >=26.1 {
		pinKey = KeyMappingHelper.registerKeyMapping(key);
		//?} else {
		/*pinKey = KeyBindingHelper.registerKeyBinding(key);
		*///?}
	}

	public static Optional<ShulkerPreview> of(ItemStack stack) {
		ItemContainerContents contents = stack.get(DataComponents.CONTAINER);
		return contents == null ? Optional.empty() : of(contents);
	}

	public static Optional<ShulkerPreview> of(ItemContainerContents contents) {
		if (!ModSettings.get().shulkerPreview) {
			return Optional.empty();
		}
		if (contents != lastContents) {
			List<ItemStack> slots = ((Slots) (Object) contents).brilliant$slots();
			last = slots.stream().allMatch(ItemStack::isEmpty) ? Optional.empty() : Optional.of(new ShulkerPreview(slots));
			lastContents = contents;
		}
		return last;
	}

	static boolean fromPlugin(Component line) {
		if (line.getContents() instanceof TranslatableContents translatable && translatable.getKey().isEmpty()) {
			for (Object arg : translatable.getArgs()) {
				if (PLUGIN_MARKER.equals(arg instanceof Component component ? component.getString() : String.valueOf(arg))) {
					return true;
				}
			}
		}
		return line.getSiblings().stream().anyMatch(ShulkerPreview::fromPlugin);
	}

	private static boolean pinHeld() {
		if (pinKey == null || pinKey.isUnbound()) {
			return false;
		}
		InputConstants.Key key = ((KeyMappingAccessor) pinKey).brilliant$key();
		return key.getType() != InputConstants.Type.MOUSE
				&& CommandKeys.keyDown(Minecraft.getInstance(), key.getValue())
				&& KeyCombos.matches(pinKey);
	}

	public static ItemStack pinned(Object screen, ItemStack hovered, int x, int y) {
		mouseX = x;
		mouseY = y;
		if (!pinHeld()) {
			pinned = null;
			pinnedScreen = null;
			return null;
		}
		if (pinned == null || pinnedScreen != screen) {
			if (hovered.isEmpty() || of(hovered).isEmpty()) {
				pinned = null;
				return null;
			}
			pinned = hovered.copy();
			pinnedScreen = screen;
			pinX = x;
			pinY = y;
		}
		return pinned;
	}

	public static boolean isPinned(Object screen) {
		return pinned != null && pinnedScreen == screen && pinHeld();
	}

	public static int pinX() {
		return pinX;
	}

	public static int pinY() {
		return pinY;
	}

	private int rows() {
		return (slots.size() + COLUMNS - 1) / COLUMNS;
	}

	@Override
	public int getHeight(Font font) {
		return rows() * SLOT + BORDER * 2 + 2;
	}

	@Override
	public int getWidth(Font font) {
		return PANEL_W;
	}

	//? if >=26.1 {
	@Override
	public void extractImage(Font font, int x, int y, int width, int height, GuiGraphicsExtractor graphics) {
		draw(new Gfx(graphics), font, x, y);
	}
	//?} else {
	/*@Override
	public void renderImage(Font font, int x, int y, int width, int height, GuiGraphics graphics) {
		draw(new Gfx(graphics), font, x, y);
	}
	*///?}

	private void draw(Gfx gfx, Font font, int x, int y) {
		int rows = rows();
		gfx.region(TEXTURE, x, y, PANEL_W, BORDER, 0, 0, PANEL_W, BORDER, 256, 256);
		for (int r = 0; r < rows; r++) {
			gfx.region(TEXTURE, x, y + BORDER + r * SLOT, PANEL_W, SLOT, 0, 17 + Math.min(r, 2) * SLOT, PANEL_W, SLOT, 256, 256);
		}
		gfx.region(TEXTURE, x, y + BORDER + rows * SLOT, PANEL_W, BORDER, 0, 159, PANEL_W, BORDER, 256, 256);
		int gridX = x + BORDER + 1;
		int gridY = y + BORDER + 1;
		boolean interactive = !nested && pinned != null && of(pinned).orElse(null) == this;
		int hovered = -1;
		if (interactive && mouseX >= gridX - 1 && mouseY >= gridY - 1) {
			int col = (mouseX - gridX + 1) / SLOT;
			int index = (mouseY - gridY + 1) / SLOT * COLUMNS + col;
			if (col < COLUMNS && index < slots.size()) {
				hovered = index;
			}
		}
		for (int i = 0; i < slots.size(); i++) {
			int sx = gridX + i % COLUMNS * SLOT;
			int sy = gridY + i / COLUMNS * SLOT;
			ItemStack stack = slots.get(i);
			if (!stack.isEmpty()) {
				gfx.itemWithCount(font, stack, sx, sy);
			}
			if (i == hovered) {
				gfx.fill(sx, sy, sx + 16, sy + 16, 0x80FFFFFF);
			}
		}
		if (hovered >= 0 && !slots.get(hovered).isEmpty()) {
			nested = true;
			try {
				gfx.itemTooltip(font, slots.get(hovered), mouseX, mouseY);
			} finally {
				nested = false;
			}
		}
	}
}
