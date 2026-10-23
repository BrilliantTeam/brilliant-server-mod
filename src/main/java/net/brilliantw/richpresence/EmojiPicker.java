package net.brilliantw.richpresence;

import com.mojang.blaze3d.platform.InputConstants;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.stream.Stream;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;

public final class EmojiPicker {
	public record Emoji(String symbol, String insert, String name, String code, List<Emoji> tones) {
		public Emoji toned(int tone) {
			return tone > 0 && tone <= tones.size() ? tones.get(tone - 1) : this;
		}
	}

	public record Section(String name, String icon, List<Emoji> emojis) {}

	private record Item(Emoji emoji, int x, int y) {}

	private static final Map<String, String> ICONS = Map.of(
			"笑臉與情緒", "😀", "人物與身體", "👋", "動物與自然", "🐻", "食物與飲料", "🍔",
			"旅遊與地點", "🚗", "活動", "⚽", "物品", "💡", "符號", "🔣", "旗幟", "🏁");
	private static final String BRILLIANT = "輝煌";
	private static final String RECENT = "經常使用";
	private static final String RESULTS = "搜尋結果";
	private static final Map<String, String> SEQUENCES = new HashMap<>();
	private static final Map<Character, Integer> SEQUENCE_LENGTHS = new HashMap<>();
	private static final Map<String, Emoji> BY_INSERT = new HashMap<>();
	private static final ThreadLocal<Boolean> PLAIN = ThreadLocal.withInitial(() -> false);
	private static final List<Section> SECTIONS = load();
	private static final List<Emoji> STICKERS = lines("/stickers.txt").map(line -> line.split("\t", -1)).filter(parts -> parts.length >= 3)
			.map(parts -> new Emoji(fromHex(parts[0]), ":" + parts[1] + ":", parts[2], ":" + parts[1] + ":", List.of()))
			.toList();
	private static final String STICKER_TAB = "貼圖";
	private static final int STICKER_CELL = 40;
	private static final int STICKER_GAP = 4;
	private static final int STICKER_GLYPH = 36;
	private static final int STICKER_ASCENT = 34;
	private static final int TAB = 12;
	private static final int CELL = 18;
	private static final int ICON = 16;
	private static final int HEADER = 12;
	private static final int SIDEBAR = 20;
	private static final int SEARCH = 14;
	private static final int FOOTER = 22;
	private static final int PAD = 4;
	private static final int GLYPH = 7;
	private static final int BUTTON = 14;
	private static final int RECENT_SHOWN = 18;
	private static final int RECENT_KEPT = 60;
	private static final List<Panel> PANELS = new ArrayList<>();
	private static final Panel CHAT = new Panel();

	private static boolean open;
	private static int frame;
	private static int recentVersion;

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
					return new Emoji(parts[0], code, code, "", List.of());
				})
				.toList();
		if (!brilliant.isEmpty()) {
			sections.add(new Section(BRILLIANT, brilliant.getFirst().symbol(), brilliant));
		}
		Map<String, List<Emoji>> groups = new LinkedHashMap<>();
		lines("/default_emojis.txt").map(line -> line.split("\t", -1)).filter(parts -> parts.length == 6).forEach(parts -> {
			String code = parts[4].isEmpty() ? "" : ":" + parts[4] + ":";
			List<Emoji> tones = parts[5].isEmpty() ? List.of() : Stream.of(parts[5].split(","))
					.map(tone -> tone.split("\\|"))
					.map(pair -> new Emoji(fromHex(pair[0]), fromHex(pair[1]), parts[3], code, List.of()))
					.toList();
			groups.computeIfAbsent(parts[0], key -> new ArrayList<>()).add(new Emoji(fromHex(parts[1]), fromHex(parts[2]), parts[3], code, tones));
		});
		lines("/emoji_sequences.txt").map(line -> line.split("\t")).filter(parts -> parts.length == 2).forEach(parts -> {
			String sequence = fromHex(parts[0]);
			SEQUENCES.put(sequence, fromHex(parts[1]));
			SEQUENCE_LENGTHS.merge(sequence.charAt(0), sequence.length(), Math::max);
		});
		groups.forEach((name, emojis) -> sections.add(new Section(name, ICONS.getOrDefault(name, emojis.getFirst().symbol()), List.copyOf(emojis))));
		sections.forEach(section -> section.emojis().forEach(emoji -> BY_INSERT.putIfAbsent(emoji.insert(), emoji)));
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
		boolean found = false;
		for (int i = 0; i < text.length(); i++) {
			char c = text.charAt(i);
			if (StickerAnimator.isSticker(c)) {
				StickerAnimator.seen(c);
			} else if (c == '️' || c == '‍' || c == '⃣' || c == '\uDB40') {
				found = true;
			} else if (c == '\uD83C' && i + 1 < text.length()) {
				char next = text.charAt(i + 1);
				found |= next >= '\uDDE6' && next <= '\uDDFF' || next >= '\uDFFB' && next <= '\uDFFF';
			}
		}
		return found;
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

	private static int tone() {
		return Math.clamp(ModSettings.get().emojiTone, 0, 5);
	}

	private static List<Emoji> recent() {
		Map<String, Integer> counts = ModSettings.get().emojiRecent;
		if (counts == null || counts.isEmpty()) {
			return List.of();
		}
		return counts.entrySet().stream()
				.sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
				.map(entry -> BY_INSERT.get(entry.getKey()))
				.filter(Objects::nonNull)
				.limit(RECENT_SHOWN)
				.toList();
	}

	private static void used(Emoji emoji) {
		ModSettings.Data settings = ModSettings.get();
		if (settings.emojiRecent == null) {
			settings.emojiRecent = new HashMap<>();
		}
		int count = settings.emojiRecent.merge(emoji.insert(), 1, Integer::sum);
		if (count > 100) {
			settings.emojiRecent.replaceAll((key, value) -> value / 2);
			settings.emojiRecent.values().removeIf(value -> value <= 0);
		}
		while (settings.emojiRecent.size() > RECENT_KEPT) {
			settings.emojiRecent.entrySet().stream().min(Map.Entry.comparingByValue()).ifPresent(entry -> settings.emojiRecent.remove(entry.getKey()));
		}
		recentVersion++;
		ModSettings.save();
	}

	private static void drawEmoji(Gfx gfx, Font font, String symbol, int x, int y, int size, float scale) {
		drawGlyph(gfx, font, symbol, x, y, size, scale, GLYPH);
	}

	private static void drawGlyph(Gfx gfx, Font font, String symbol, int x, int y, int size, float scale, int glyph) {
		float width = Math.max(0, font.width(symbol) - 1) * scale;
		gfx.push(x + (size - width) / 2f, y + (size - glyph * scale) / 2f, scale);
		gfx.text(font, symbol, 0, 0, 0xFFFFFFFF);
		gfx.pop();
	}

	private static boolean inside(double mouseX, double mouseY, int x, int y, int width, int height) {
		return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
	}

	public static boolean orphanedSearch(GuiEventListener focused) {
		frame++;
		boolean orphaned = false;
		for (Panel panel : PANELS) {
			if (panel.search != null && panel.drawnFrame < frame - 1) {
				panel.search.visible = false;
				orphaned |= focused == panel.search;
			}
		}
		return orphaned;
	}

	public static final class Panel {
		private EditBox search;
		private double scroll;
		private boolean tonePicker;
		private boolean stickerTab;
		private int cell = CELL;
		private int drawnFrame = -10;
		private int recentSeen;
		private String layoutKey = "";
		private List<Section> shown = List.of();
		private List<Section> sidebar = List.of();
		private List<Item> items = List.of();
		private int[] sectionTops = new int[0];
		private int contentHeight;

		public Panel() {
			PANELS.add(this);
		}

		public static int width(int columns) {
			return SIDEBAR + PAD * 2 + columns * CELL;
		}

		public static int height(int rows) {
			return PAD + TAB + 2 + SEARCH + 3 + rows * CELL + FOOTER;
		}

		public EditBox createSearch() {
			search = new EditBox(Minecraft.getInstance().font, 0, 0, 100, 10, Component.literal("搜尋表情符號"));
			search.setBordered(false);
			search.setMaxLength(40);
			search.setResponder(value -> scroll = 0);
			search.visible = false;
			return search;
		}

		public void open(Screen screen) {
			scroll = 0;
			tonePicker = false;
			recentSeen = recentVersion;
			drawnFrame = frame;
			if (search != null) {
				search.setValue("");
				search.visible = true;
				screen.setFocused(search);
			}
		}

		public boolean searchFocused(Screen screen) {
			return search != null && search.visible && screen.getFocused() == search;
		}

		private String query() {
			return search == null ? "" : search.getValue().trim().replace(":", "").toLowerCase(Locale.ROOT);
		}

		private static List<Section> browse() {
			List<Emoji> recent = recent();
			if (recent.isEmpty()) {
				return SECTIONS;
			}
			List<Section> all = new ArrayList<>();
			all.add(new Section(RECENT, "🕒", recent));
			all.addAll(SECTIONS);
			return all;
		}

		private static List<Emoji> matching(Stream<Emoji> emojis, String query) {
			return emojis.filter(emoji -> emoji.name().toLowerCase(Locale.ROOT).contains(query) || emoji.code().toLowerCase(Locale.ROOT).contains(query)).toList();
		}

		private void layout(int w) {
			int size = stickerTab ? STICKER_CELL : CELL;
			int cols = Math.max(1, (w - SIDEBAR - PAD * 2) / size);
			String query = query();
			String key = cols + "|" + recentSeen + "|" + query + "|" + stickerTab;
			if (key.equals(layoutKey)) {
				return;
			}
			layoutKey = key;
			cell = size;
			sidebar = stickerTab ? List.of() : browse();
			if (stickerTab) {
				shown = List.of(new Section(STICKER_TAB, "", matching(STICKERS.stream(), query)));
			} else {
				shown = query.isEmpty() ? sidebar : List.of(new Section(RESULTS, "🔍", matching(SECTIONS.stream().flatMap(section -> section.emojis().stream()), query)));
			}
			List<Item> list = new ArrayList<>();
			sectionTops = new int[shown.size()];
			int y = 0;
			for (int s = 0; s < shown.size(); s++) {
				sectionTops[s] = y;
				y += HEADER;
				List<Emoji> emojis = shown.get(s).emojis();
				for (int i = 0; i < emojis.size(); i++) {
					list.add(new Item(emojis.get(i), (i % cols) * size, y + (i / cols) * size));
				}
				y += (emojis.size() + cols - 1) / cols * size + 4;
			}
			items = list;
			contentHeight = y;
		}

		private static int searchTop(int y) {
			return y + PAD + TAB + 2;
		}

		private static int gridTop(int y) {
			return searchTop(y) + SEARCH + 3;
		}

		private static int gridHeight(int y, int h) {
			return y + h - FOOTER - gridTop(y);
		}

		private int maxScroll(int y, int h) {
			return Math.max(0, contentHeight - gridHeight(y, h));
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

		private int iconY(int y, int index) {
			int divider = -1;
			for (int s = 0; s < sidebar.size(); s++) {
				if (sidebar.get(s).name().equals(BRILLIANT)) {
					divider = s;
				}
			}
			return y + PAD + index * ICON + (divider >= 0 && index > divider ? 3 : 0);
		}

		private int sidebarAt(int x, int y, int h, double mouseX, double mouseY) {
			if (mouseX < x || mouseX >= x + SIDEBAR) {
				return -1;
			}
			for (int s = 0; s < sidebar.size(); s++) {
				int top = iconY(y, s);
				if (top + ICON > y + h) {
					break;
				}
				if (mouseY >= top && mouseY < top + ICON) {
					return s;
				}
			}
			return -1;
		}

		private static int toneX(int x, int w) {
			return x + w - PAD - SEARCH;
		}

		private static int toneMenuY(int y) {
			return searchTop(y) + SEARCH + 2;
		}

		private static int[] tabRect(Font font, int x, int y, int index) {
			int left = x + SIDEBAR + PAD;
			int emojiWidth = font.width("表情符號") + 10;
			return index == 0 ? new int[] { left, y + PAD, emojiWidth, TAB } : new int[] { left + emojiWidth + 2, y + PAD, font.width(STICKER_TAB) + 10, TAB };
		}

		private int tabAt(int x, int y, double mouseX, double mouseY) {
			Font font = Minecraft.getInstance().font;
			for (int i = 0; i < (STICKERS.isEmpty() ? 1 : 2); i++) {
				int[] r = tabRect(font, x, y, i);
				if (inside(mouseX, mouseY, r[0], r[1], r[2], r[3])) {
					return i;
				}
			}
			return -1;
		}

		private int searchRight(int x, int w) {
			return stickerTab ? x + w - PAD : toneX(x, w) - 2;
		}

		private int toneAt(int x, int y, int w, double mouseX, double mouseY) {
			if (!tonePicker || !inside(mouseX, mouseY, toneX(x, w) - 2, toneMenuY(y), CELL, CELL * 6)) {
				return -1;
			}
			return (int) (mouseY - toneMenuY(y)) / CELL;
		}

		private Item itemAt(int x, int y, int w, int h, double mouseX, double mouseY) {
			layout(w);
			int gx = x + SIDEBAR + PAD;
			int gy = gridTop(y);
			if (toneAt(x, y, w, mouseX, mouseY) >= 0 || mouseX < gx || mouseX >= x + w - PAD || mouseY < gy || mouseY >= gy + gridHeight(y, h)) {
				return null;
			}
			double cx = mouseX - gx;
			double cy = mouseY - gy + scroll;
			for (Item item : items) {
				if (cx >= item.x() && cx < item.x() + cell && cy >= item.y() && cy < item.y() + cell) {
					return item;
				}
			}
			return null;
		}

		private void drawItem(Gfx gfx, Font font, Emoji emoji, int x, int y, int size) {
			if (stickerTab) {
				int box = size - STICKER_GAP * 2;
				int glyphWidth = Math.max(1, font.width(emoji.symbol()) - 1);
				float scale = box / (float) Math.max(STICKER_GLYPH, glyphWidth);
				float width = glyphWidth * scale;
				float height = STICKER_GLYPH * scale;
				gfx.scissor(x + STICKER_GAP, y + STICKER_GAP, x + size - STICKER_GAP, y + size - STICKER_GAP);
				gfx.push(x + (size - width) / 2f, y + (size - height) / 2f + (STICKER_ASCENT - GLYPH) * scale, scale);
				gfx.text(font, emoji.symbol(), 0, 0, 0xFFFFFFFF);
				gfx.pop();
				gfx.endScissor();
			} else {
				drawEmoji(gfx, font, emoji.symbol(), x, y, size, 2f);
			}
		}

		private static Emoji toneSample() {
			Emoji hand = BY_INSERT.get("👏");
			return hand == null ? new Emoji("👏", "👏", "", "", List.of()) : hand;
		}

		public void render(Gfx gfx, int x, int y, int w, int h, int mouseX, int mouseY) {
			drawnFrame = frame;
			Font font = Minecraft.getInstance().font;
			layout(w);
			scroll = Math.clamp(scroll, 0, maxScroll(y, h));
			int tone = tone();
			int gx = x + SIDEBAR + PAD;
			int gy = gridTop(y);
			int gh = gridHeight(y, h);
			int right = x + w - PAD;
			gfx.fill(x - 1, y - 1, x + w + 1, y + h + 1, Theme.ACCENT_DIM);
			gfx.fill(x, y, x + w, y + h, Theme.PANEL);
			gfx.fill(x, y, x + SIDEBAR, y + h, Theme.SIDEBAR);

			boolean browsing = shown == sidebar;
			int current = browsing ? currentSection() : -1;
			int hoveredSection = sidebarAt(x, y, h, mouseX, mouseY);
			for (int s = 0; s < sidebar.size(); s++) {
				int top = iconY(y, s);
				if (top + ICON > y + h) {
					break;
				}
				if (s == current || s == hoveredSection) {
					gfx.fill(x + 2, top, x + SIDEBAR - 2, top + ICON, s == current ? Theme.SELECTED : Theme.CARD_HOVER);
				}
				drawEmoji(gfx, font, sidebar.get(s).icon(), x + 2, top, SIDEBAR - 4, 1.4f);
				if (sidebar.get(s).name().equals(BRILLIANT) && s + 1 < sidebar.size()) {
					gfx.fill(x + 5, top + ICON + 1, x + SIDEBAR - 5, top + ICON + 2, Theme.CHIP);
				}
			}

			int hoveredTab = tabAt(x, y, mouseX, mouseY);
			for (int i = 0; i < (STICKERS.isEmpty() ? 1 : 2); i++) {
				int[] r = tabRect(font, x, y, i);
				boolean active = (i == 1) == stickerTab;
				if (active || i == hoveredTab) {
					gfx.fill(r[0], r[1], r[0] + r[2], r[1] + r[3], active ? Theme.SELECTED : Theme.CARD_HOVER);
				}
				gfx.centered(font, i == 0 ? "表情符號" : STICKER_TAB, r[0], r[1], r[2], r[3], active ? Theme.TEXT : Theme.TEXT_SECONDARY);
			}

			int sy = searchTop(y);
			int tx = toneX(x, w);
			int searchRight = searchRight(x, w);
			gfx.fill(gx, sy, searchRight, sy + SEARCH, Theme.CARD);
			Icons.SEARCH.draw(gfx, gx + 3, sy + (SEARCH - Icons.SEARCH.height()) / 2, Theme.TEXT_DIM);
			if (search != null) {
				search.visible = true;
				search.setX(gx + 14);
				search.setY(sy + 3);
				search.setWidth(searchRight - gx - 16);
				Theme.hint(search, stickerTab ? "搜尋貼圖" : "搜尋表情符號");
				gfx.widget(search, mouseX, mouseY);
			}
			boolean toneHovered = !stickerTab && inside(mouseX, mouseY, tx, sy, SEARCH, SEARCH);
			if (!stickerTab) {
				gfx.fill(tx, sy, tx + SEARCH, sy + SEARCH, tonePicker || toneHovered ? Theme.CHIP_HOVER : Theme.CARD);
				drawEmoji(gfx, font, toneSample().toned(tone).symbol(), tx, sy, SEARCH, 1.4f);
			}

			Item hovered = itemAt(x, y, w, h, mouseX, mouseY);
			int top = gy - (int) scroll;
			gfx.scissor(gx, gy, right, gy + gh);
			for (int s = 0; s < shown.size(); s++) {
				int headerY = top + sectionTops[s];
				if (headerY + HEADER >= gy && headerY <= gy + gh) {
					gfx.text(font, shown.get(s).name(), gx + 1, headerY + 2, Theme.TEXT_SECONDARY);
				}
			}
			for (Item item : items) {
				int iy = top + item.y();
				if (iy + cell < gy || iy > gy + gh) {
					continue;
				}
				int ix = gx + item.x();
				if (item == hovered) {
					gfx.fill(ix, iy, ix + cell, iy + cell, Theme.CHIP_HOVER);
				}
				drawItem(gfx, font, item.emoji().toned(tone), ix, iy, cell);
			}
			if (!browsing && items.isEmpty()) {
				gfx.centered(font, "找不到表情符號", gx, gy + HEADER, right - gx, CELL, Theme.TEXT_DIM);
			}
			gfx.endScissor();

			if (contentHeight > gh) {
				int bar = Math.max(12, gh * gh / contentHeight);
				int barY = gy + (int) ((gh - bar) * scroll / maxScroll(y, h));
				gfx.fill(x + w - 3, barY, x + w - 1, barY + bar, Theme.CHIP);
			}

			int footerY = y + h - FOOTER;
			gfx.fill(x + SIDEBAR, footerY, x + w, y + h, Theme.HEADER);
			int textX = x + SIDEBAR + CELL + 8;
			int textWidth = right - textX;
			if (hovered != null) {
				Emoji emoji = hovered.emoji().toned(tone);
				drawItem(gfx, font, emoji, x + SIDEBAR + 4, footerY + 2, CELL);
				gfx.text(font, font.plainSubstrByWidth(emoji.name(), textWidth), textX, footerY + (emoji.code().isEmpty() ? 7 : 3), Theme.TEXT);
				if (!emoji.code().isEmpty()) {
					gfx.text(font, font.plainSubstrByWidth(emoji.code(), textWidth), textX, footerY + 12, Theme.TEXT_DIM);
				}
			} else if (!shown.isEmpty()) {
				gfx.text(font, shown.get(Math.max(0, current)).name(), x + SIDEBAR + PAD, footerY + 7, Theme.TEXT_DIM);
			}

			if (tonePicker) {
				int my = toneMenuY(y);
				int mx = tx - 2;
				gfx.fill(mx - 1, my - 1, mx + CELL + 1, my + CELL * 6 + 1, Theme.ACCENT_DIM);
				gfx.fill(mx, my, mx + CELL, my + CELL * 6, Theme.HEADER);
				int hoveredTone = toneAt(x, y, w, mouseX, mouseY);
				for (int t = 0; t < 6; t++) {
					if (t == tone || t == hoveredTone) {
						gfx.fill(mx, my + t * CELL, mx + CELL, my + (t + 1) * CELL, t == tone ? Theme.SELECTED : Theme.CHIP_HOVER);
					}
					drawEmoji(gfx, font, toneSample().toned(t).symbol(), mx, my + t * CELL, CELL, 1.6f);
				}
			}

			if (hoveredSection >= 0) {
				gfx.tooltip(font, List.of(Component.literal(sidebar.get(hoveredSection).name())), mouseX, mouseY);
			} else if (toneHovered && !tonePicker) {
				gfx.tooltip(font, List.of(Component.literal("膚色")), mouseX, mouseY);
			}
		}

		public Emoji click(Screen screen, int x, int y, int w, int h, double mouseX, double mouseY) {
			layout(w);
			if (tonePicker) {
				int tone = toneAt(x, y, w, mouseX, mouseY);
				tonePicker = false;
				if (tone >= 0) {
					ModSettings.get().emojiTone = tone;
					ModSettings.save();
				}
				return null;
			}
			int tab = tabAt(x, y, mouseX, mouseY);
			if (tab >= 0) {
				if ((tab == 1) != stickerTab) {
					stickerTab = tab == 1;
					scroll = 0;
					if (search != null) {
						search.setValue("");
					}
				}
				return null;
			}
			int sy = searchTop(y);
			if (!stickerTab && inside(mouseX, mouseY, toneX(x, w), sy, SEARCH, SEARCH)) {
				tonePicker = true;
				return null;
			}
			if (inside(mouseX, mouseY, x + SIDEBAR + PAD, sy, searchRight(x, w) - x - SIDEBAR - PAD, SEARCH)) {
				if (search != null) {
					screen.setFocused(search);
				}
				return null;
			}
			int section = sidebarAt(x, y, h, mouseX, mouseY);
			if (section >= 0) {
				if (search != null && !search.getValue().isEmpty()) {
					search.setValue("");
					layout(w);
				}
				scroll = Math.min(sectionTops[section], maxScroll(y, h));
				return null;
			}
			Item item = itemAt(x, y, w, h, mouseX, mouseY);
			return item == null ? null : pick(item.emoji());
		}

		private Emoji pick(Emoji emoji) {
			if (stickerTab) {
				return emoji;
			}
			used(emoji);
			return emoji.toned(tone());
		}

		public Emoji first() {
			if (query().isEmpty() || shown.isEmpty() || shown.getFirst().emojis().isEmpty()) {
				return null;
			}
			return pick(shown.getFirst().emojis().getFirst());
		}

		public void scroll(double amount) {
			tonePicker = false;
			scroll -= Math.signum(amount) * CELL * 2;
		}
	}

	private static boolean active() {
		return !SECTIONS.isEmpty() && RichPresenceModClient.isOnBrilliantServer() && ModSettings.get().emoji;
	}

	public static boolean isOpen() {
		return open;
	}

	public static EditBox createSearch() {
		return CHAT.createSearch();
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
			if (open) {
				CHAT.open(screen);
			}
			ChannelBar.close();
			return true;
		}
		if (!open || !inside(mouseX, mouseY, panelX(screen), panelY(screen), panelWidth(), panelHeight(screen))) {
			return false;
		}
		Emoji emoji = CHAT.click(screen, panelX(screen), panelY(screen), panelWidth(), panelHeight(screen), mouseX, mouseY);
		if (emoji != null) {
			PrivateWindow.insert(screen, emoji.insert());
		}
		return true;
	}

	public static boolean keyPressed(ChatScreen screen, KeyEvent event) {
		if (!open || !CHAT.searchFocused(screen)) {
			return false;
		}
		if (event.isEscape()) {
			close();
			return true;
		}
		if (event.isConfirmation()) {
			Emoji emoji = CHAT.first();
			if (emoji == null) {
				return false;
			}
			PrivateWindow.insert(screen, emoji.insert());
			return true;
		}
		return event.isUp() || event.isDown();
	}

	public static boolean mouseScrolled(ChatScreen screen, double mouseX, double mouseY, double amount) {
		if (!active() || !open || !inside(mouseX, mouseY, panelX(screen), panelY(screen), panelWidth(), panelHeight(screen))) {
			return false;
		}
		CHAT.scroll(amount);
		return true;
	}
}
