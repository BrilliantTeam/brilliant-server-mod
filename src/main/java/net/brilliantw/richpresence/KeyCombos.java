package net.brilliantw.richpresence;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;

public final class KeyCombos {
	public static final int CTRL = 1;
	public static final int SHIFT = 2;
	public static final int ALT = 4;
	static final List<Integer> CHOICES = List.of(0, CTRL, ALT, SHIFT, CTRL | SHIFT, CTRL | ALT, ALT | SHIFT, CTRL | ALT | SHIFT);
	private static final String PREFIX = "key.brilliant-server-mod.";

	private KeyCombos() {}

	static List<KeyMapping> mine() {
		Minecraft client = Minecraft.getInstance();
		return client == null || client.options == null ? List.of()
				: Arrays.stream(client.options.keyMappings).filter(key -> key.getName().startsWith(PREFIX)).toList();
	}

	static int combo(KeyMapping key) {
		return ModSettings.get().keyCombos.getOrDefault(key.getName(), 0);
	}

	private static final Set<Integer> MODIFIER_KEYS = Set.of(InputConstants.KEY_LSHIFT, InputConstants.KEY_RSHIFT, InputConstants.KEY_LCONTROL,
			InputConstants.KEY_RCONTROL, InputConstants.KEY_LALT, InputConstants.KEY_RALT);

	static boolean isModifier(int keyCode) {
		return MODIFIER_KEYS.contains(keyCode);
	}

	static void bind(KeyMapping key, InputConstants.Key input, int combo) {
		key.setKey(input);
		KeyMapping.resetMapping();
		Minecraft.getInstance().options.save();
		if (combo == 0 || input == InputConstants.UNKNOWN) {
			ModSettings.get().keyCombos.remove(key.getName());
		} else {
			ModSettings.get().keyCombos.put(key.getName(), combo);
		}
		ModSettings.save();
	}

	static int held() {
		Minecraft client = Minecraft.getInstance();
		return (client.hasControlDown() ? CTRL : 0) | (client.hasShiftDown() ? SHIFT : 0) | (client.hasAltDown() ? ALT : 0);
	}

	static boolean fires(int want, int held, List<Integer> rivals) {
		return (held & want) == want && rivals.stream().noneMatch(other -> (held & other) == other && Integer.bitCount(other) > Integer.bitCount(want));
	}

	public static boolean matches(KeyMapping key) {
		List<Integer> rivals = mine().stream().filter(other -> other != key && !other.isUnbound() && other.same(key)).map(KeyCombos::combo).toList();
		return fires(combo(key), held(), rivals);
	}

	static String label(int combo) {
		if (combo == 0) {
			return "不需組合鍵";
		}
		StringBuilder out = new StringBuilder();
		if ((combo & CTRL) != 0) out.append("Ctrl + ");
		if ((combo & ALT) != 0) out.append("Alt + ");
		if ((combo & SHIFT) != 0) out.append("Shift + ");
		return out.toString();
	}

	static String describe(KeyMapping key) {
		String bound = key.isUnbound() ? "尚未綁定" : key.getTranslatedKeyMessage().getString();
		int combo = combo(key);
		return combo == 0 ? bound : label(combo) + bound;
	}

	public static void main(String[] args) {
		check(fires(0, 0, List.of()), "沒有組合鍵時直接觸發");
		check(fires(0, SHIFT, List.of()), "沒有對手時按著 Shift 也照常觸發（蹲下時開地圖）");
		check(!fires(CTRL, 0, List.of()), "設了 Ctrl 沒按就不觸發");
		check(fires(CTRL, CTRL, List.of()), "按著 Ctrl 觸發");
		check(!fires(0, CTRL, List.of(CTRL)), "同一顆鍵另有 Ctrl 組合時，按 Ctrl 只觸發組合那個");
		check(fires(CTRL, CTRL, List.of(0)), "組合那個照常觸發");
		check(!fires(CTRL, CTRL | SHIFT, List.of(CTRL | SHIFT)), "按 Ctrl + Shift 時只觸發最完整的組合");
		check(fires(CTRL | SHIFT, CTRL | SHIFT, List.of(CTRL)), "最完整的組合會觸發");
		check(fires(0, ALT, List.of(CTRL)), "按的不是對手的組合時照常觸發");
		check(label(CTRL | SHIFT).equals("Ctrl + Shift + "), "組合鍵名稱");
		System.out.println("OK：組合鍵判斷正常。");
	}

	private static void check(boolean ok, String what) {
		if (!ok) {
			throw new AssertionError(what);
		}
	}
}
