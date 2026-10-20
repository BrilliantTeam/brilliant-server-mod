package net.brilliantw.richpresence;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import java.util.List;

public final class Theme {
	public record Preset(String id, String name, int color) {}

	public static final List<Preset> ACCENTS = List.of(
			new Preset("sky", "天藍", 0xFF7AD3FF),
			new Preset("gold", "輝煌金", 0xFFF2C14E),
			new Preset("sakura", "櫻花粉", 0xFFFF9AC8),
			new Preset("mint", "薄荷綠", 0xFF7BE0B0),
			new Preset("lavender", "薰衣草", 0xFFB49CFF),
			new Preset("coral", "珊瑚橘", 0xFFFF9E6B),
			new Preset("ruby", "赤紅", 0xFFFF6B6B),
			new Preset("custom", "自訂", 0));
	public static final List<Preset> TONES = List.of(
			new Preset("graphite", "石墨", 0),
			new Preset("ocean", "深海", 0xFF0E2A48),
			new Preset("forest", "森林", 0xFF123222),
			new Preset("wine", "酒紅", 0xFF3C1422),
			new Preset("violet", "紫夜", 0xFF281A42),
			new Preset("midnight", "純黑", 0xFF000000));

	private static final int BASE_PANEL = 0xF0121619;
	private static final int BASE_HEADER = 0xFF1A2026;
	private static final int BASE_SIDEBAR = 0xFF161B20;
	private static final int BASE_CARD = 0xFF1E252C;
	private static final int BASE_CARD_HOVER = 0xFF2A333C;
	private static final int BASE_SELECTED = 0xFF253543;
	private static final int BASE_CHIP = 0xFF35404B;
	private static final int BASE_CHIP_HOVER = 0xFF4A5966;
	private static final int BASE_ACCENT_DIM = 0xFF2E5A70;

	public static int PANEL = BASE_PANEL;
	public static int HEADER = BASE_HEADER;
	public static int SIDEBAR = BASE_SIDEBAR;
	public static int CARD = BASE_CARD;
	public static int CARD_HOVER = BASE_CARD_HOVER;
	public static int SELECTED = BASE_SELECTED;
	public static int CHIP = BASE_CHIP;
	public static int CHIP_HOVER = BASE_CHIP_HOVER;
	public static int ACCENT = 0xFF7AD3FF;
	public static int ACCENT_DIM = BASE_ACCENT_DIM;
	public static final int TEXT = 0xFFE8EEF2;
	public static final int TEXT_SECONDARY = 0xFF8C98A4;
	public static final int TEXT_DIM = 0xFF5C6773;
	public static final int SUCCESS = 0xFF8FE3A0;
	public static final int WARNING = 0xFFF6E0A4;
	public static final int DANGER = 0xFFFF7A7A;

	public static final String BACK = "返回";
	private static final Identifier WATERMARK = Identifier.fromNamespaceAndPath("brilliant-server-mod", "textures/gui/watermark.png");
	private static final int WATERMARK_TEXTURE = 192;
	private static final int WATERMARK_MAX = 96;
	private static final int WATERMARK_TINT = 0x18FFFFFF;

	private Theme() {}

	static int mix(int a, int b, double t) {
		int r = (int) Math.round((a >> 16 & 255) * (1 - t) + (b >> 16 & 255) * t);
		int g = (int) Math.round((a >> 8 & 255) * (1 - t) + (b >> 8 & 255) * t);
		int bl = (int) Math.round((a & 255) * (1 - t) + (b & 255) * t);
		return a & 0xFF000000 | r << 16 | g << 8 | bl;
	}

	public static Preset preset(List<Preset> list, String id) {
		for (Preset preset : list) {
			if (preset.id().equals(id)) {
				return preset;
			}
		}
		return list.getFirst();
	}

	public static int accentColor(ModSettings.Data s) {
		Preset preset = preset(ACCENTS, s.accent);
		return preset.id().equals("custom") ? 0xFF000000 | s.customAccent : preset.color();
	}

	public static String hex(int color) {
		return String.format("#%06X", color & 0xFFFFFF);
	}

	public static Integer parseHex(String text) {
		String value = text.strip().replaceFirst("^#", "");
		if (!value.matches("[0-9a-fA-F]{6}")) {
			return null;
		}
		return 0xFF000000 | Integer.parseInt(value, 16);
	}

	public static void apply(ModSettings.Data s) {
		int accent = accentColor(s);
		Preset tone = preset(TONES, s.tone);
		double k = tone.id().equals("graphite") ? 0 : tone.id().equals("midnight") ? 0.55 : 0.32;
		int t = tone.color();
		PANEL = k == 0 ? BASE_PANEL : mix(BASE_PANEL, t, k);
		HEADER = k == 0 ? BASE_HEADER : mix(BASE_HEADER, t, k);
		SIDEBAR = k == 0 ? BASE_SIDEBAR : mix(BASE_SIDEBAR, t, k);
		CARD = k == 0 ? BASE_CARD : mix(BASE_CARD, t, k);
		CARD_HOVER = k == 0 ? BASE_CARD_HOVER : mix(BASE_CARD_HOVER, t, k);
		CHIP = k == 0 ? BASE_CHIP : mix(BASE_CHIP, t, k * 0.8);
		CHIP_HOVER = k == 0 ? BASE_CHIP_HOVER : mix(BASE_CHIP_HOVER, t, k * 0.8);
		ACCENT = accent;
		boolean defaultAccent = accent == 0xFF7AD3FF;
		ACCENT_DIM = defaultAccent ? BASE_ACCENT_DIM : mix(accent, 0xFF0E1114, 0.62);
		SELECTED = defaultAccent && k == 0 ? BASE_SELECTED : mix(CARD, accent, 0.18);
	}

	public static void watermark(Gfx gfx, int panelX, int panelY, int panelW, int panelH) {
		int size = Math.min(WATERMARK_MAX, Math.min(panelW, panelH - 60) / 2);
		if (size < 32) {
			return;
		}
		gfx.texture(WATERMARK, panelX + panelW - size - 10, panelY + panelH - 16 - size - 6, size, size, WATERMARK_TEXTURE, WATERMARK_TEXTURE, WATERMARK_TINT);
	}

	public static void hint(EditBox box, String text) {
		Font font = Minecraft.getInstance().font;
		int width = Math.max(0, box.getInnerWidth());
		String shown = font.width(text) <= width ? text : font.plainSubstrByWidth(text, Math.max(0, width - font.width("…"))) + "…";
		box.setHint(Component.literal(shown).withStyle(ChatFormatting.DARK_GRAY));
	}

	public static int backWidth(Font font) {
		return Gfx.iconLabelWidth(font, Icons.BACK, BACK) + 12;
	}

	public static int titleX(Font font, int panelX) {
		return panelX + 6 + backWidth(font) + 8;
	}

	public static boolean backHit(Font font, int panelX, int panelY, double mouseX, double mouseY) {
		return mouseX >= panelX + 6 && mouseX < panelX + 6 + backWidth(font) && mouseY >= panelY + 6 && mouseY < panelY + 20;
	}

	public static void drawBack(Gfx gfx, Font font, int panelX, int panelY, int mouseX, int mouseY) {
		boolean hovered = backHit(font, panelX, panelY, mouseX, mouseY);
		gfx.fill(panelX + 6, panelY + 6, panelX + 6 + backWidth(font), panelY + 20, hovered ? CHIP_HOVER : CHIP);
		gfx.iconLabel(font, Icons.BACK, BACK, panelX + 6, panelY + 6, backWidth(font), 14, TEXT);
	}
}
