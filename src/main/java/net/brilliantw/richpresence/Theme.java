package net.brilliantw.richpresence;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
//? if >=26.1 {
import net.minecraft.client.gui.GuiGraphicsExtractor;
//?} else {
/*import net.minecraft.client.gui.GuiGraphics;
*///?}
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;
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
	private static final int BASE_CARD_HOVER = 0xFF323C46;
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
	public static final int TEXT_SECONDARY = 0xFFB0BAC4;
	public static final int TEXT_DIM = 0xFF838E9A;
	public static final int SUCCESS = 0xFF8FE3A0;
	public static final int WARNING = 0xFFF6E0A4;
	public static final int DANGER = 0xFFFF8C8C;
	public static final int KNOB_ON = 0xFF121619;
	public static final int SCROLLBAR = 0xFF6B7B8A;
	public static final int SCROLLBAR_HOVER = 0xFF95A3B0;

	public static final int HEADER_H = 26;
	public static final int FOOTER_H = 16;
	public static final int SCROLLBAR_W = 4;
	public static final String BACK = "返回";
	private static final Identifier WATERMARK = Identifier.fromNamespaceAndPath("brilliant-server-mod", "textures/gui/watermark.png");
	private static final int WATERMARK_TEXTURE = 192;
	private static final int WATERMARK_MAX = 96;
	private static final int WATERMARK_TINT = 0x18FFFFFF;
	private static final int[] SHADOW = { 0x48000000, 0x28000000, 0x10000000 };

	private Theme() {}

	static int mix(int a, int b, double t) {
		int r = (int) Math.round((a >> 16 & 255) * (1 - t) + (b >> 16 & 255) * t);
		int g = (int) Math.round((a >> 8 & 255) * (1 - t) + (b >> 8 & 255) * t);
		int bl = (int) Math.round((a & 255) * (1 - t) + (b & 255) * t);
		return a & 0xFF000000 | r << 16 | g << 8 | bl;
	}

	static double luminance(int color) {
		double r = channel(color >> 16 & 255), g = channel(color >> 8 & 255), b = channel(color & 255);
		return 0.2126 * r + 0.7152 * g + 0.0722 * b;
	}

	private static double channel(int value) {
		double v = value / 255.0;
		return v <= 0.03928 ? v / 12.92 : Math.pow((v + 0.055) / 1.055, 2.4);
	}

	static double contrast(int a, int b) {
		double la = luminance(a), lb = luminance(b);
		return (Math.max(la, lb) + 0.05) / (Math.min(la, lb) + 0.05);
	}

	static int readable(int color, int background) {
		int result = color;
		for (int i = 1; i <= 20 && contrast(result, background) < 4.5; i++) {
			result = mix(color, 0xFFFFFFFF, i * 0.05);
		}
		return result;
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
		accent = readable(accent, CARD_HOVER);
		ACCENT = accent;
		boolean defaultAccent = accent == 0xFF7AD3FF;
		ACCENT_DIM = defaultAccent ? BASE_ACCENT_DIM : mix(accent, 0xFF0E1114, 0.62);
		SELECTED = defaultAccent && k == 0 ? BASE_SELECTED : mix(CARD, accent, 0.18);
	}

	public static void frame(Gfx gfx, int x1, int y1, int x2, int y2, int fill) {
		for (int i = 0; i < SHADOW.length; i++) {
			gfx.outline(x1 - 2 - i, y1 - 2 - i, x2 + 2 + i, y2 + 2 + i, SHADOW[i]);
		}
		gfx.outline(x1 - 1, y1 - 1, x2 + 1, y2 + 1, CHIP);
		gfx.fill(x1, y1, x2, y2, fill);
	}

	public static void panel(Gfx gfx, int x1, int y1, int x2, int y2) {
		frame(gfx, x1, y1, x2, y2, PANEL);
	}

	public static void header(Gfx gfx, int x, int y, int w) {
		header(gfx, x, y, w, ACCENT_DIM);
	}

	public static void header(Gfx gfx, int x, int y, int w, int line) {
		gfx.fill(x, y, x + w, y + HEADER_H, HEADER);
		gfx.fill(x, y + HEADER_H - 1, x + w, y + HEADER_H, line);
	}

	public static void footer(Gfx gfx, Font font, int x, int bottom, int w, String text, int color) {
		gfx.fill(x, bottom - FOOTER_H, x + w, bottom - FOOTER_H + 1, HEADER);
		gfx.text(font, Gfx.fit(font, text, w - 16), x + 8, bottom - FOOTER_H + 4, color);
	}

	public static void scrollbar(Gfx gfx, int x, int y, int h, Scroller scroller, boolean hot) {
		if (!scroller.overflow()) {
			return;
		}
		int start = y + scroller.thumbStart(h);
		gfx.round(x, start, x + SCROLLBAR_W, start + scroller.thumbLength(h), hot || scroller.dragging() ? SCROLLBAR_HOVER : SCROLLBAR);
	}

	public static void scrollbar(Gfx gfx, int x, int y, int h, int content, double offset, boolean hot) {
		if (content <= h) {
			return;
		}
		int length = Math.max(16, h * h / content);
		int start = y + (int) ((h - length) * Math.clamp(offset / (content - h), 0, 1));
		gfx.round(x, start, x + SCROLLBAR_W, start + length, hot ? SCROLLBAR_HOVER : SCROLLBAR);
	}

	public static ButtonWidget button(Component message, Button.OnPress onPress, int x, int y, int w, int h) {
		return new ButtonWidget(x, y, w, h, message, onPress);
	}

	public static final class ButtonWidget extends Button {
		private boolean primary;

		private ButtonWidget(int x, int y, int w, int h, Component message, OnPress onPress) {
			super(x, y, w, h, message, onPress, DEFAULT_NARRATION);
		}

		public ButtonWidget primary() {
			primary = true;
			return this;
		}

		public ButtonWidget tip(String text) {
			setTooltip(Tooltip.create(Component.literal(text)));
			return this;
		}

		//? if >=26.1 {
		@Override
		protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
			draw(new Gfx(graphics));
		}
		//?} else {
		/*@Override
		protected void renderContents(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
			draw(new Gfx(graphics));
		}
		*///?}

		private void draw(Gfx gfx) {
			int x = getX(), y = getY(), w = getWidth(), h = getHeight();
			boolean hot = active && isHoveredOrFocused();
			int fill = !active ? CARD : primary ? (hot ? mix(ACCENT_DIM, ACCENT, 0.25) : ACCENT_DIM) : hot ? CHIP_HOVER : CHIP;
			gfx.round(x, y, x + w, y + h, fill);
			if (active && isFocused()) {
				gfx.outline(x, y, x + w, y + h, ACCENT);
			}
			Font font = Minecraft.getInstance().font;
			int color = active ? TEXT : TEXT_DIM;
			Component message = getMessage();
			if (font.width(message) <= w - 8) {
				gfx.text(font, message, x + (w - font.width(message)) / 2, Gfx.textTop(font, y, h), color);
			} else {
				gfx.text(font, Gfx.fit(font, message.getString(), w - 8), x + 4, Gfx.textTop(font, y, h), color);
			}
		}
	}

	public static void click() {
		Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
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
		box.setHint(Component.literal(Gfx.fit(font, text, width)).withStyle(ChatFormatting.DARK_GRAY));
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
		gfx.round(panelX + 6, panelY + 6, panelX + 6 + backWidth(font), panelY + 20, hovered ? CHIP_HOVER : CHIP);
		gfx.iconLabel(font, Icons.BACK, BACK, panelX + 6, panelY + 6, backWidth(font), 14, TEXT);
	}
}
