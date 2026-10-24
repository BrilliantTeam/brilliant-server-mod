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

	private static final int[] LETTER_CODES = {InputConstants.KEY_A, InputConstants.KEY_B, InputConstants.KEY_C, InputConstants.KEY_D, InputConstants.KEY_E, InputConstants.KEY_F, InputConstants.KEY_G, InputConstants.KEY_H, InputConstants.KEY_I, InputConstants.KEY_J, InputConstants.KEY_K, InputConstants.KEY_L, InputConstants.KEY_M, InputConstants.KEY_N, InputConstants.KEY_O, InputConstants.KEY_P, InputConstants.KEY_Q, InputConstants.KEY_R, InputConstants.KEY_S, InputConstants.KEY_T, InputConstants.KEY_U, InputConstants.KEY_V, InputConstants.KEY_W, InputConstants.KEY_X, InputConstants.KEY_Y, InputConstants.KEY_Z};
	private static final int[] DIGIT_CODES = {InputConstants.KEY_0, InputConstants.KEY_1, InputConstants.KEY_2, InputConstants.KEY_3, InputConstants.KEY_4, InputConstants.KEY_5, InputConstants.KEY_6, InputConstants.KEY_7, InputConstants.KEY_8, InputConstants.KEY_9};
	private static final int[] FUNCTION_CODES = {InputConstants.KEY_F1, InputConstants.KEY_F2, InputConstants.KEY_F3, InputConstants.KEY_F4, InputConstants.KEY_F5, InputConstants.KEY_F6, InputConstants.KEY_F7, InputConstants.KEY_F8, InputConstants.KEY_F9, InputConstants.KEY_F10, InputConstants.KEY_F11, InputConstants.KEY_F12};

	static int code(String name) {
		if (name.length() == 1 && name.charAt(0) >= 'A' && name.charAt(0) <= 'Z') {
			return LETTER_CODES[name.charAt(0) - 'A'];
		}
		if (name.length() == 1 && name.charAt(0) >= '0' && name.charAt(0) <= '9') {
			return DIGIT_CODES[name.charAt(0) - '0'];
		}
		if (name.matches("F([1-9]|1[0-2])")) {
			return FUNCTION_CODES[Integer.parseInt(name.substring(1)) - 1];
		}
		return switch (name) {
			case "Enter" -> InputConstants.KEY_RETURN;
			case "Tab" -> InputConstants.KEY_TAB;
			case "空白鍵" -> InputConstants.KEY_SPACE;
			case "Backspace" -> InputConstants.KEY_BACKSPACE;
			case "Delete" -> InputConstants.KEY_DELETE;
			case "Insert" -> InputConstants.KEY_INSERT;
			case "Home" -> InputConstants.KEY_HOME;
			case "End" -> InputConstants.KEY_END;
			case "PageUp" -> InputConstants.KEY_PAGEUP;
			case "PageDown" -> InputConstants.KEY_PAGEDOWN;
			case "上" -> InputConstants.KEY_UP;
			case "下" -> InputConstants.KEY_DOWN;
			case "左" -> InputConstants.KEY_LEFT;
			case "右" -> InputConstants.KEY_RIGHT;
			default -> InputConstants.KEY_ESCAPE;
		};
	}

	static int parse(String name) {
		return (name.contains("Ctrl") ? CTRL : 0) | (name.contains("Shift") ? SHIFT : 0) | (name.contains("Alt") ? ALT : 0);
	}

	static int eventMods(int combo) {
		return ((combo & SHIFT) != 0 ? InputConstants.MOD_SHIFT : 0) | ((combo & CTRL) != 0 ? InputConstants.MOD_CONTROL : 0) | ((combo & ALT) != 0 ? InputConstants.MOD_ALT : 0);
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
		check(code("Q") == InputConstants.KEY_Q && code("7") == InputConstants.KEY_7, "字母與數字鍵");
		check(code("F1") == InputConstants.KEY_F1 && code("F12") == InputConstants.KEY_F12, "功能鍵");
		check(code("空白鍵") == InputConstants.KEY_SPACE && code("左") == InputConstants.KEY_LEFT && code("ESC") == InputConstants.KEY_ESCAPE, "特殊鍵");
		for (String key : ScriptBlocks.KEYBOARD) {
			check(key.equals("ESC") || code(key) != InputConstants.KEY_ESCAPE, "每個按鍵都有對應：" + key);
		}
		check(ScriptBlocks.KEYBOARD.getFirst().equals("ESC") && ScriptBlocks.KEYBOARD.containsAll(List.of("ESC", "Enter", "Tab", "F1", "F2", "F3", "F5", "E")), "舊腳本的「按下鍵盤」選項仍然有效");
		check(parse("Ctrl+Shift") == (CTRL | SHIFT) && parse("不按") == 0, "組合鍵解析");
		check(eventMods(CTRL | SHIFT) == (InputConstants.MOD_CONTROL | InputConstants.MOD_SHIFT) && eventMods(ALT) == InputConstants.MOD_ALT, "組合鍵轉成按鍵事件旗標");
		check(java.util.Arrays.stream(LETTER_CODES).distinct().count() + java.util.Arrays.stream(DIGIT_CODES).distinct().count() + java.util.Arrays.stream(FUNCTION_CODES).distinct().count() == 48, "字母、數字與功能鍵的代碼不重複");
		System.out.println("OK：組合鍵判斷正常。");
	}

	private static void check(boolean ok, String what) {
		if (!ok) {
			throw new AssertionError(what);
		}
	}
}
