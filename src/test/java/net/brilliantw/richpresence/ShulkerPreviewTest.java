package net.brilliantw.richpresence;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

class ShulkerPreviewTest {
	@Test
	void recognizesShulkerPreviewerLore() {
		Component marker = Component.translatable("", Component.literal("ShulkerPreviewerLore"));
		assertTrue(ShulkerPreview.fromPlugin(marker));
		assertTrue(ShulkerPreview.fromPlugin(marker.copy().append("石頭 x64 , 鐵錠 x3")));
		assertTrue(ShulkerPreview.fromPlugin(Component.empty().append(marker.copy().append("鑽石"))));
		assertTrue(ShulkerPreview.fromPlugin(Component.translatable("", "ShulkerPreviewerLore")));
		assertFalse(ShulkerPreview.fromPlugin(Component.literal("ShulkerPreviewerLore")));
		assertFalse(ShulkerPreview.fromPlugin(Component.translatable("item.minecraft.stone")));
		assertFalse(ShulkerPreview.fromPlugin(Component.literal("玩家自己寫的說明")));
	}
}
