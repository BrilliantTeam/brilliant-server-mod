package net.brilliantw.richpresence;

import com.mojang.blaze3d.platform.InputConstants;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Stream;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.network.chat.Component;

public final class EmojiPicker {
	public record Emoji(String symbol, String insert, String name, String code) {}

	public record Section(String name, String icon, List<Emoji> emojis) {}

	private record Item(int section, Emoji emoji, int x, int y) {}

	private static final Map<String, String> ICONS = Map.of(
			"笑臉與情緒", "😀", "人物與身體", "👋", "動物與自然", "🐻", "食物與飲料", "🍔",
			"旅遊與地點", "🚗", "活動", "⚽", "物品", "💡", "符號", "🔣", "旗幟", "🏁");
	private static final Map<String, String> SEQUENCES = new HashMap<>();
	private static final Map<Character, Integer> SEQUENCE_LENGTHS = new HashMap<>();
	private static final ThreadLocal<Boolean> PLAIN = ThreadLocal.withInitial(() -> false);
	private static final List<Section> SECTIONS = load();
	private static final int CELL = 18;
	private static final int HEADER = 12;
	private static final int SIDEBAR = 20;
	private static final int FOOTER = 22;
	private static final int PAD = 4;
	private static final int GLYPH = 7;
	private static final int BUTTON = 14;
	private static final Panel CHAT = new Panel();

	private static boolean open;

	private EmojiPicker() {}

	public static List<Section> sections() {
		return SECTIONS;
	}

	private static List<Section> load() {
		List<Section> sections = new ArrayList<>();
		List<Emoji> brilliant = lines("/emojis.txt")
				.map(line -> line.split(":", 2))
				.filter(parts -> parts.length == 2)
				.map(parts -> {
					String code = ":" + parts[1].trim() + ":";
					return new Emoji(parts[0], code, code, "");
				})
				.toList();
		if (!brilliant.isEmpty()) {
			sections.add(new Section("輝煌", brilliant.getFirst().symbol(), brilliant));
		}
		Map<String, List<Emoji>> groups = new LinkedHashMap<>();
		lines("/default_emojis.txt").map(line -> line.split("\t", -1)).filter(parts -> parts.length == 5).forEach(parts ->
				groups.computeIfAbsent(parts[0], key -> new ArrayList<>()).add(new Emoji(fromHex(parts[1]), fromHex(parts[2]), parts[3], parts[4].isEmpty() ? "" : ":" + parts[4] + ":")));
		lines("/emoji_sequences.txt").map(line -> line.split("\t")).filter(parts -> parts.length == 2).forEach(parts -> {
			String sequence = fromHex(parts[0]);
			SEQUENCES.put(sequence, fromHex(parts[1]));
			SEQUENCE_LENGTHS.merge(sequence.charAt(0), sequence.length(), Math::max);
		});
		groups.forEach((name, emojis) -> sections.add(new Section(name, ICONS.getOrDefault(name, emojis.getFirst().symbol()), List.copyOf(emojis))));
		return List.copyOf(sections);
	}

	private static String fromHex(String hex) {
		StringBuilder out = new StringBuilder();
		for (String part : hex.trim().split(" ")) {
			out.appendCodePoint(Integer.parseInt(part, 16));
		}
		return out.toString();
	}

	private static boolean maySequence(String text) {
		for (int i = 0; i < text.length(); i++) {
			char c = text.charAt(i);
			if (c == '️' || c == '‍' || c == '⃣' || c == '\uDB40') {
				return true;
			}
			if (c == '\uD83C' && i + 1 < text.length()) {
				char next = text.charAt(i + 1);
				if (next >= '\uDDE6' && next <= '\uDDFF' || next >= '\uDFFB' && next <= '\uDFFF') {
					return true;
				}
			}
		}
		return false;
	}

	public static String display(String text) {
		if (text == null || SEQUENCES.isEmpty() || PLAIN.get() || !maySequence(text)) {
			return text;
		}
		StringBuilder out = null;
		int i = 0;
		while (i < text.length()) {
			String glyph = null;
			int length = Math.min(SEQUENCE_LENGTHS.getOrDefault(text.charAt(i), 0), text.length() - i);
			for (; length >= 2; length--) {
				glyph = SEQUENCES.get(text.substring(i, i + length));
				if (glyph != null) {
					break;
				}
			}
			if (glyph == null) {
				if (out != null) {
					out.append(text.charAt(i));
				}
				i++;
				continue;
			}
			if (out == null) {
				out = new StringBuilder(text.length()).append(text, 0, i);
			}
			out.append(glyph).append("️".repeat(length - glyph.length()));
			i += length;
		}
		return out == null ? text : out.toString();
	}

	public static <T> T plain(Supplier<T> action) {
		boolean previous = PLAIN.get();
		PLAIN.set(true);
		try {
			return action.get();
		} finally {
			PLAIN.set(previous);
		}
	}

	private static Stream<String> lines(String resource) {
		try (BufferedReader reader = new BufferedReader(new InputStreamReader(EmojiPicker.class.getResourceAsStream(resource), StandardCharsets.UTF_8))) {
			return reader.lines().toList().stream();
		} catch (Exception e) {
			return Stream.empty();
		}
	}

	private static void drawEmoji(Gfx gfx, Font font, String symbol, int x, int y, int size, float scale) {
		float width = Math.max(0, font.width(symbol) - 1) * scale;
		gfx.push(x + (size - width) / 2f, y + (size - GLYPH * scale) / 2f, scale);
		gfx.text(font, symbol, 0, 0, 0xFFFFFFFF);
		gfx.pop();
	}

	public static final class Panel {
		private double scroll;
		private int columns = -1;
		private List<Item> items = List.of();
		private int[] sectionTops = new int[0];
		private int contentHeight;

		public static int width(int columns) {
			return SIDEBAR + PAD * 2 + columns * CELL;
		}

		public static int height(int rows) {
			return PAD + rows * CELL + FOOTER;
		}

		private void layout(int w) {
			int cols = Math.max(1, (w - SIDEBAR - PAD * 2) / CELL);
			if (cols == columns) {
				return;
			}
			columns = cols;
			List<Item> list = new ArrayList<>();
			sectionTops = new int[SECTIONS.size()];
			int y = 0;
			for (int s = 0; s < SECTIONS.size(); s++) {
				sectionTops[s] = y;
				y += HEADER;
				List<Emoji> emojis = SECTIONS.get(s).emojis();
				for (int i = 0; i < emojis.size(); i++) {
					list.add(new Item(s, emojis.get(i), (i % cols) * CELL, y + (i / cols) * CELL));
				}
				y += (emojis.size() + cols - 1) / cols * CELL + 4;
			}
			items = list;
			contentHeight = y;
		}

		private static int gridHeight(int h) {
			return h - PAD - FOOTER;
		}

		private int maxScroll(int h) {
			return Math.max(0, contentHeight - gridHeight(h));
		}

		private int currentSection() {
			int current = 0;
			for (int s = 0; s < sectionTops.length; s++) {
				if (sectionTops[s] <= scroll + 1) {
					current = s;
				}
			}
			return current;
		}

		private static int iconY(int y, int section) {
			return y + PAD + section * CELL + (section > 0 ? 3 : 0);
		}

		private static int sectionAt(int x, int y, int h, double mouseX, double mouseY) {
			if (mouseX < x || mouseX >= x + SIDEBAR) {
				return -1;
			}
			for (int s = 0; s < SECTIONS.size(); s++) {
				int top = iconY(y, s);
				if (top + CELL > y + h) {
					break;
				}
				if (mouseY >= top && mouseY < top + CELL) {
					return s;
				}
			}
			return -1;
		}

		private Item itemAt(int x, int y, int w, int h, double mouseX, double mouseY) {
			layout(w);
			int gx = x + SIDEBAR + PAD;
			int gy = y + PAD;
			if (mouseX < gx || mouseX >= x + w - PAD || mouseY < gy || mouseY >= gy + gridHeight(h)) {
				return null;
			}
			double cx = mouseX - gx;
			double cy = mouseY - gy + scroll;
			for (Item item : items) {
				if (cx >= item.x() && cx < item.x() + CELL && cy >= item.y() && cy < item.y() + CELL) {
					return item;
				}
			}
			return null;
		}

		public void render(Gfx gfx, int x, int y, int w, int h, int mouseX, int mouseY) {
			Font font = Minecraft.getInstance().font;
			layout(w);
			scroll = Math.clamp(scroll, 0, maxScroll(h));
			int gx = x + SIDEBAR + PAD;
			int gy = y + PAD;
			int gh = gridHeight(h);
			gfx.fill(x - 1, y - 1, x + w + 1, y + h + 1, Theme.ACCENT_DIM);
			gfx.fill(x, y, x + w, y + h, Theme.PANEL);
			gfx.fill(x, y, x + SIDEBAR, y + h, Theme.SIDEBAR);

			int current = currentSection();
			int hoveredSection = sectionAt(x, y, h, mouseX, mouseY);
			for (int s = 0; s < SECTIONS.size(); s++) {
				int top = iconY(y, s);
				if (top + CELL > y + h) {
					break;
				}
				if (s == current || s == hoveredSection) {
					gfx.fill(x + 1, top, x + SIDEBAR - 1, top + CELL, s == current ? Theme.SELECTED : Theme.CARD_HOVER);
				}
				drawEmoji(gfx, font, SECTIONS.get(s).icon(), x + 1, top, SIDEBAR - 2, 1.5f);
				if (s == 0 && SECTIONS.size() > 1) {
					gfx.fill(x + 5, top + CELL + 1, x + SIDEBAR - 5, top + CELL + 2, Theme.CHIP);
				}
			}

			Item hovered = itemAt(x, y, w, h, mouseX, mouseY);
			int top = gy - (int) scroll;
			gfx.scissor(gx, gy, x + w - PAD, gy + gh);
			for (int s = 0; s < SECTIONS.size(); s++) {
				int headerY = top + sectionTops[s];
				if (headerY + HEADER >= gy && headerY <= gy + gh) {
					gfx.text(font, SECTIONS.get(s).name(), gx + 1, headerY + 2, Theme.TEXT_SECONDARY);
				}
			}
			for (Item item : items) {
				int iy = top + item.y();
				if (iy + CELL < gy || iy > gy + gh) {
					continue;
				}
				int ix = gx + item.x();
				if (item == hovered) {
					gfx.fill(ix, iy, ix + CELL, iy + CELL, Theme.CHIP_HOVER);
				}
				drawEmoji(gfx, font, item.emoji().symbol(), ix, iy, CELL, 2f);
			}
			gfx.endScissor();

			if (contentHeight > gh) {
				int bar = Math.max(12, gh * gh / contentHeight);
				int barY = gy + (int) ((gh - bar) * scroll / maxScroll(h));
				gfx.fill(x + w - 3, barY, x + w - 1, barY + bar, Theme.CHIP);
			}

			int footerY = y + h - FOOTER;
			gfx.fill(x + SIDEBAR, footerY, x + w, y + h, Theme.HEADER);
			int textX = x + SIDEBAR + CELL + 8;
			int textWidth = x + w - PAD - textX;
			if (hovered != null) {
				Emoji emoji = hovered.emoji();
				drawEmoji(gfx, font, emoji.symbol(), x + SIDEBAR + 4, footerY + 2, CELL, 2f);
				gfx.text(font, font.plainSubstrByWidth(emoji.name(), textWidth), textX, footerY + (emoji.code().isEmpty() ? 7 : 3), Theme.TEXT);
				if (!emoji.code().isEmpty()) {
					gfx.text(font, font.plainSubstrByWidth(emoji.code(), textWidth), textX, footerY + 12, Theme.TEXT_DIM);
				}
			} else {
				gfx.text(font, SECTIONS.get(current).name(), x + SIDEBAR + PAD, footerY + 7, Theme.TEXT_DIM);
			}
			if (hoveredSection >= 0) {
				gfx.tooltip(font, List.of(Component.literal(SECTIONS.get(hoveredSection).name())), mouseX, mouseY);
			}
		}

		public Emoji click(int x, int y, int w, int h, double mouseX, double mouseY) {
			layout(w);
			int section = sectionAt(x, y, h, mouseX, mouseY);
			if (section >= 0) {
				scroll = Math.min(sectionTops[section], maxScroll(h));
				return null;
			}
			Item item = itemAt(x, y, w, h, mouseX, mouseY);
			return item == null ? null : item.emoji();
		}

		public void scroll(double amount) {
			scroll -= Math.signum(amount) * CELL * 2;
		}
	}

	private static boolean active() {
		return !SECTIONS.isEmpty() && RichPresenceModClient.isOnBrilliantServer() && ModSettings.get().emoji;
	}

	private static int buttonX(ChatScreen screen) {
		return screen.width - 2 - BUTTON;
	}

	private static int buttonY(ChatScreen screen) {
		return screen.height - 16 - BUTTON;
	}

	private static int panelWidth() {
		return Panel.width(9);
	}

	private static int panelHeight(ChatScreen screen) {
		return Math.clamp(buttonY(screen) - 6, Panel.height(3), Panel.height(9));
	}

	private static int panelX(ChatScreen screen) {
		return screen.width - 2 - panelWidth();
	}

	private static int panelY(ChatScreen screen) {
		return buttonY(screen) - 3 - panelHeight(screen);
	}

	private static boolean inside(double mouseX, double mouseY, int x, int y, int width, int height) {
		return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
	}

	public static void render(ChatScreen screen, Gfx gfx, int mouseX, int mouseY) {
		if (!active()) {
			return;
		}
		Font font = Minecraft.getInstance().font;
		int bx = buttonX(screen);
		int by = buttonY(screen);
		boolean buttonHovered = inside(mouseX, mouseY, bx, by, BUTTON, BUTTON);
		gfx.fill(bx, by, bx + BUTTON, by + BUTTON, open || buttonHovered ? 0xC0505050 : 0x80000000);
		drawEmoji(gfx, font, SECTIONS.getFirst().icon(), bx, by, BUTTON, 1f);
		if (open) {
			CHAT.render(gfx, panelX(screen), panelY(screen), panelWidth(), panelHeight(screen), mouseX, mouseY);
		}
	}

	public static void close() {
		open = false;
	}

	public static boolean mouseClicked(ChatScreen screen, double mouseX, double mouseY, int button) {
		if (!active() || button != InputConstants.MOUSE_BUTTON_LEFT) {
			return false;
		}
		if (inside(mouseX, mouseY, buttonX(screen), buttonY(screen), BUTTON, BUTTON)) {
			open = !open;
			ChannelBar.close();
			return true;
		}
		if (!open || !inside(mouseX, mouseY, panelX(screen), panelY(screen), panelWidth(), panelHeight(screen))) {
			return false;
		}
		Emoji emoji = CHAT.click(panelX(screen), panelY(screen), panelWidth(), panelHeight(screen), mouseX, mouseY);
		if (emoji != null) {
			PrivateWindow.insert(screen, emoji.insert());
		}
		return true;
	}

	public static boolean mouseScrolled(ChatScreen screen, double mouseX, double mouseY, double amount) {
		if (!active() || !open || !inside(mouseX, mouseY, panelX(screen), panelY(screen), panelWidth(), panelHeight(screen))) {
			return false;
		}
		CHAT.scroll(amount);
		return true;
	}
}
