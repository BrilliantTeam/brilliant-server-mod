package net.brilliantw.richpresence;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;

public final class CommandKeys {
	private static final Set<CommandStore.Entry> DOWN = Collections.newSetFromMap(new IdentityHashMap<>());

	private static final boolean[] MOUSE = new boolean[16];

	private CommandKeys() {}

	public static void mouse(int button, boolean down) {
		if (button >= 0 && button < MOUSE.length) {
			MOUSE[button] = down;
		}
	}

	public static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(CommandKeys::tick);
	}

	static InputConstants.Key key(CommandStore.Entry entry) {
		if (entry.key == null || entry.key.isEmpty()) {
			return null;
		}
		try {
			InputConstants.Key key = InputConstants.getKey(entry.key);
			boolean primaryMouse = key.getType() == InputConstants.Type.MOUSE && key.getValue() < InputConstants.MOUSE_BUTTON_MIDDLE;
			return key == InputConstants.UNKNOWN || primaryMouse ? null : key;
		} catch (RuntimeException e) {
			return null;
		}
	}

	static String label(CommandStore.Entry entry) {
		InputConstants.Key key = key(entry);
		return key == null ? "" : KeyCombos.label(entry.mods).replace("不需組合鍵", "") + key.getDisplayName().getString();
	}

	private static boolean isDown(Minecraft client, InputConstants.Key key) {
		if (key.getType() == InputConstants.Type.MOUSE) {
			return key.getValue() >= 0 && key.getValue() < MOUSE.length && MOUSE[key.getValue()];
		}
		return keyDown(client, key.getValue());
	}

	static boolean keyDown(Minecraft client, int keyCode) {
		//? if >=26.3 {
		/*return InputConstants.isKeyDown(keyCode);
		*///?} else {
		return InputConstants.isKeyDown(client.getWindow(), keyCode);
		//?}
	}

	private static void tick(Minecraft client) {
		List<CommandStore.Entry> bound = new ArrayList<>();
		for (CommandStore.Node node : CommandStore.flatten(CommandStore.get().groups, false)) {
			for (CommandStore.Entry entry : node.group().commands) {
				if (key(entry) != null) {
					bound.add(entry);
				}
			}
		}
		if (bound.isEmpty()) {
			DOWN.clear();
			return;
		}
		boolean ready = client.player != null && ScriptEngine.screen() == null;
		int held = KeyCombos.held();
		for (CommandStore.Entry entry : bound) {
			InputConstants.Key key = key(entry);
			if (!isDown(client, key)) {
				DOWN.remove(entry);
				continue;
			}
			if (!DOWN.add(entry) || !ready) {
				continue;
			}
			List<Integer> rivals = bound.stream().filter(other -> other != entry && key.equals(key(other))).map(other -> other.mods).toList();
			if (KeyCombos.fires(entry.mods, held, rivals)) {
				client.player.connection.sendCommand(entry.command);
			}
		}
	}
}
