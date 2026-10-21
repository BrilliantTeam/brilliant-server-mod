package net.brilliantw.richpresence;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
//? if >=26.1 {
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
//?} else {
/*import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
*///?}
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

public final class NightVision {
	private static KeyMapping toggle;

	private NightVision() {}

	public static boolean active() {
		return ModSettings.get().nightVision && RichPresenceModClient.featuresActive();
	}

	public static void register() {
		KeyMapping.Category category = KeyMapping.Category.register(Identifier.fromNamespaceAndPath("brilliant-server-mod", "tools"));
		KeyMapping key = new KeyMapping("key.brilliant-server-mod.night_vision", InputConstants.UNKNOWN.getValue(), category);
		//? if >=26.1 {
		toggle = KeyMappingHelper.registerKeyMapping(key);
		//?} else {
		/*toggle = KeyBindingHelper.registerKeyBinding(key);
		*///?}
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (toggle.consumeClick()) {
				if (client.player == null || !RichPresenceModClient.featuresActive() || !KeyCombos.matches(toggle)) {
					continue;
				}
				ModSettings.Data s = ModSettings.get();
				s.nightVision = !s.nightVision;
				ModSettings.save();
				RichPresenceModClient.showMessage(Component.literal("夜視：" + (s.nightVision ? "開啟" : "關閉")).withStyle(s.nightVision ? ChatFormatting.GREEN : ChatFormatting.GRAY));
			}
		});
	}
}
