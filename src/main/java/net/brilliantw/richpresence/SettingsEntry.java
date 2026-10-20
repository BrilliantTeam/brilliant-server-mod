package net.brilliantw.richpresence;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
//? if >=26.1 {
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
//?} else {
/*import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
*///?}
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

public final class SettingsEntry {
	private static KeyMapping open;

	private SettingsEntry() {}

	public static void register() {
		KeyMapping.Category category = KeyMapping.Category.register(Identifier.fromNamespaceAndPath("brilliant-server-mod", "general"));
		KeyMapping key = new KeyMapping("key.brilliant-server-mod.settings", InputConstants.UNKNOWN.getValue(), category);
		//? if >=26.1 {
		open = KeyMappingHelper.registerKeyMapping(key);
		//?} else {
		/*open = KeyBindingHelper.registerKeyBinding(key);
		*///?}
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (open.consumeClick()) {
				if (ScriptEngine.screen() == null) {
					CommandMenuScreen.setScreen(new ModSettingsScreen(null));
				}
			}
		});
		ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
			if (screen instanceof PauseScreen pause && pause.showsPauseMenu() || screen instanceof TitleScreen) {
				attach(screen);
			}
		});
	}

	private static void attach(Screen screen) {
		Button button = Button.builder(Component.literal("輝煌模組設定"), b -> CommandMenuScreen.setScreen(new ModSettingsScreen(screen)))
				.bounds(6, 6, 96, 20)
				.tooltip(Tooltip.create(Component.literal("小地圖、聊天、介面色彩等所有輝煌模組的設定。\n也可以到「選項 → 控制 → 按鍵綁定」設定快捷鍵直接開啟。")))
				.build();
		//? if >=26.1 {
		Screens.getWidgets(screen).add(button);
		//?} else {
		/*Screens.getButtons(screen).add(button);
		*///?}
	}
}
