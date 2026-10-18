package net.brilliantw.richpresence;

import net.brilliantw.richpresence.mixin.AbstractContainerScreenAccessor;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;

public final class SlotNumbers {
	private SlotNumbers() {}

	public static void register() {
		ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
			if (screen instanceof AbstractContainerScreen<?> container) {
				//? if >=26.1 {
				ScreenEvents.afterExtract(screen).register((current, graphics, mouseX, mouseY, delta) -> draw(new Gfx(graphics), container));
				//?} else {
				/*ScreenEvents.afterRender(screen).register((current, graphics, mouseX, mouseY, delta) -> draw(new Gfx(graphics), container));
				*///?}
			}
		});
	}

	private static void draw(Gfx gfx, AbstractContainerScreen<?> screen) {
		Minecraft client = Minecraft.getInstance();
		if (!client.hasAltDown() || !RichPresenceModClient.featuresActive() || !ModSettings.get().slotNumbers) {
			return;
		}
		AbstractContainerScreenAccessor accessor = (AbstractContainerScreenAccessor) screen;
		int left = accessor.brilliant$leftPos();
		int top = accessor.brilliant$topPos();
		var slots = screen.getMenu().slots;
		for (int i = 0; i < slots.size(); i++) {
			Slot slot = slots.get(i);
			if (!slot.isActive()) {
				continue;
			}
			String label = Integer.toString(i + 1);
			int x = left + slot.x;
			int y = top + slot.y;
			int w = client.font.width(label) + 2;
			gfx.fill(x, y, x + w, y + 9, 0xD0000000);
			gfx.text(client.font, label, x + 1, y + 1, 0xFFFFE066);
		}
		String hint = "腳本用的格子編號（放開 Alt 隱藏）";
		gfx.fill(left, top - 12, left + client.font.width(hint) + 6, top - 1, 0xD0000000);
		gfx.text(client.font, hint, left + 3, top - 10, 0xFFFFE066);
	}
}
