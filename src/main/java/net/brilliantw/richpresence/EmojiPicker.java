package net.brilliantw.richpresence;

import com.mojang.blaze3d.platform.InputConstants;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
//? if >=26.1 {
import net.minecraft.client.gui.GuiGraphicsExtractor;
//?} else {
/*import net.minecraft.client.gui.GuiGraphics;
*///?}
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.network.chat.Component;

public final class EmojiPicker {
	public record Emoji(String symbol, String code) {}

	private static final List<Emoji> EMOJIS = load();
	private static final int CELL = 14;
	private static final int COLUMNS = 12;
	private static final int ROWS = 8;
	private static final int PADDING = 3;

	private static boolean open;
	private static int scrollRow;

	private EmojiPicker() {}

	public static List<Emoji> emojis() {
		return EMOJIS;
	}

	private static List<Emoji> load() {
		try (BufferedReader reader = new BufferedReader(new InputStreamReader(EmojiPicker.class.getResourceAsStream("/emojis.txt"), StandardCharsets.UTF_8))) {
			return reader.lines()
					.map(line -> line.split(":", 2))
					.filter(parts -> parts.length == 2)
					.map(parts -> new Emoji(parts[0], ":" + parts[1].trim() + ":"))
					.toList();
		} catch (Exception e) {
			return List.of();
		}
	}

	private static boolean active() {
		return !EMOJIS.isEmpty() && RichPresenceModClient.isOnBrilliantServer() && ModSettings.get().emoji;
	}

	private static int buttonX(ChatScreen screen) {
		return screen.width - 2 - CELL;
	}

	private static int buttonY(ChatScreen screen) {
		return screen.height - 16 - CELL;
	}

	private static int panelWidth() {
		return COLUMNS * CELL + PADDING * 2;
	}

	private static int panelHeight() {
		return ROWS * CELL + PADDING * 2;
	}

	private static int panelX(ChatScreen screen) {
		return screen.width - 2 - panelWidth();
	}

	private static int panelY(ChatScreen screen) {
		return buttonY(screen) - 2 - panelHeight();
	}

	private static int maxScrollRow() {
		return Math.max(0, (EMOJIS.size() + COLUMNS - 1) / COLUMNS - ROWS);
	}

	private static boolean inside(double mouseX, double mouseY, int x, int y, int width, int height) {
		return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
	}

	private static int hoveredIndex(ChatScreen screen, double mouseX, double mouseY) {
		int x = panelX(screen) + PADDING;
		int y = panelY(screen) + PADDING;
		if (!open || !inside(mouseX, mouseY, x, y, COLUMNS * CELL, ROWS * CELL)) {
			return -1;
		}
		int index = ((int) (mouseY - y) / CELL + scrollRow) * COLUMNS + (int) (mouseX - x) / CELL;
		return index < EMOJIS.size() ? index : -1;
	}

	//? if >=26.1 {
	public static void render(ChatScreen screen, GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
	//?} else {
	/*public static void render(ChatScreen screen, GuiGraphics graphics, int mouseX, int mouseY) {
	*///?}
		if (!active()) {
			return;
		}
		Font font = Minecraft.getInstance().font;
		int bx = buttonX(screen);
		int by = buttonY(screen);
		boolean buttonHovered = inside(mouseX, mouseY, bx, by, CELL, CELL);
		graphics.fill(bx, by, bx + CELL, by + CELL, open || buttonHovered ? 0xC0505050 : 0x80000000);
		drawSymbol(graphics, font, EMOJIS.getFirst().symbol(), bx, by);
		if (!open) {
			return;
		}

		int px = panelX(screen);
		int py = panelY(screen);
		graphics.fill(px, py, px + panelWidth(), py + panelHeight(), 0xD0000000);
		int hovered = hoveredIndex(screen, mouseX, mouseY);
		for (int row = 0; row < ROWS; row++) {
			for (int column = 0; column < COLUMNS; column++) {
				int index = (row + scrollRow) * COLUMNS + column;
				if (index >= EMOJIS.size()) {
					break;
				}
				int cx = px + PADDING + column * CELL;
				int cy = py + PADDING + row * CELL;
				if (index == hovered) {
					graphics.fill(cx, cy, cx + CELL, cy + CELL, 0x60FFFFFF);
				}
				drawSymbol(graphics, font, EMOJIS.get(index).symbol(), cx, cy);
			}
		}
		if (hovered >= 0) {
			Emoji emoji = EMOJIS.get(hovered);
			graphics.setTooltipForNextFrame(font, Component.literal(emoji.symbol() + " " + emoji.code()), mouseX, mouseY);
		}
	}

	//? if >=26.1 {
	private static void drawSymbol(GuiGraphicsExtractor graphics, Font font, String symbol, int x, int y) {
	//?} else {
	/*private static void drawSymbol(GuiGraphics graphics, Font font, String symbol, int x, int y) {
	*///?}
		text(graphics, font, symbol, x + (CELL - font.width(symbol)) / 2, y + (CELL - font.lineHeight) / 2 + 1, 0xFFFFFFFF);
	}

	//? if >=26.1 {
	static void text(GuiGraphicsExtractor graphics, Font font, String text, int x, int y, int color) {
		graphics.text(font, text, x, y, color, false);
	}
	//?} else {
	/*static void text(GuiGraphics graphics, Font font, String text, int x, int y, int color) {
		graphics.drawString(font, text, x, y, color, false);
	}
	*///?}

	public static void close() {
		open = false;
	}

	public static boolean mouseClicked(ChatScreen screen, double mouseX, double mouseY, int button) {
		if (!active() || button != InputConstants.MOUSE_BUTTON_LEFT) {
			return false;
		}
		if (inside(mouseX, mouseY, buttonX(screen), buttonY(screen), CELL, CELL)) {
			open = !open;
			ChannelBar.close();
			return true;
		}
		if (!open || !inside(mouseX, mouseY, panelX(screen), panelY(screen), panelWidth(), panelHeight())) {
			return false;
		}
		int index = hoveredIndex(screen, mouseX, mouseY);
		if (index >= 0) {
			PrivateWindow.insert(screen, EMOJIS.get(index).code());
		}
		return true;
	}

	public static boolean mouseScrolled(ChatScreen screen, double mouseX, double mouseY, double amount) {
		if (!active() || !open || !inside(mouseX, mouseY, panelX(screen), panelY(screen), panelWidth(), panelHeight())) {
			return false;
		}
		scrollRow = Math.clamp(scrollRow - (int) Math.signum(amount), 0, maxScrollRow());
		return true;
	}
}
