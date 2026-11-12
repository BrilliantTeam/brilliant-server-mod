package net.brilliantw.richpresence;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.mojang.blaze3d.platform.InputConstants;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.network.chat.Component;

public final class QuickMessages {
	private record Rect(int x, int y, int w, int h) {
		boolean contains(double mouseX, double mouseY) {
			return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
		}
	}

	public static final int MAX_MESSAGES = 30;
	public static final int MAX_LENGTH = 256;
	private static final int ROW = 14;
	private static final int GAP = 2;
	private static final int PADDING = 3;
	private static final int WIDTH = 230;
	private static final Gson GSON = new Gson();

	private static List<String> messages;
	private static boolean open;
	private static int scroll;

	private QuickMessages() {}

	public static boolean isOpen() {
		return open;
	}

	public static void toggle() {
		open = !open;
		scroll = 0;
	}

	public static void close() {
		open = false;
	}

	private static Path file() {
		return FabricLoader.getInstance().getConfigDir().resolve("brilliant-server-mod-quick-messages.json");
	}

	static List<String> messages() {
		if (messages == null) {
			messages = new ArrayList<>();
			try {
				Path file = file();
				if (Files.exists(file)) {
					List<String> loaded = GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8), new TypeToken<List<String>>() {}.getType());
					if (loaded != null) {
						loaded.stream().filter(text -> text != null && !text.isBlank()).map(text -> text.length() > MAX_LENGTH ? text.substring(0, MAX_LENGTH) : text)
								.distinct().limit(MAX_MESSAGES).forEach(messages::add);
					}
				}
			} catch (Exception ignored) {
			}
		}
		return messages;
	}

	private static void save() {
		try {
			Path file = file();
			Path temp = file.resolveSibling(file.getFileName() + ".tmp");
			Files.writeString(temp, GSON.toJson(messages()), StandardCharsets.UTF_8);
			Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
		} catch (IOException e) {
			System.err.println("[brilliant-server-mod] Failed to save quick messages: " + e.getMessage());
		}
	}

	static String saveReason(String input) {
		String text = input == null ? "" : input.strip();
		if (text.isEmpty()) {
			return "先在聊天欄輸入要儲存的文字";
		}
		if (messages().contains(text)) {
			return "這則訊息已經儲存過了";
		}
		if (messages().size() >= MAX_MESSAGES) {
			return "最多儲存 " + MAX_MESSAGES + " 則，請先刪除不用的訊息";
		}
		return null;
	}

	static boolean add(String input) {
		if (saveReason(input) != null) {
			return false;
		}
		messages().add(input.strip().length() > MAX_LENGTH ? input.strip().substring(0, MAX_LENGTH) : input.strip());
		save();
		return true;
	}

	private static void remove(int index) {
		messages().remove(index);
		save();
	}

	public static void send(String text) {
		Minecraft client = Minecraft.getInstance();
		if (client.player == null) {
			return;
		}
		//? if >=26.2 {
		client.gui.hud.getChat().addRecentChat(text);
		//?} else {
		/*client.gui.getChat().addRecentChat(text);
		*///?}
		if (text.startsWith("/")) {
			client.player.connection.sendCommand(text.substring(1));
		} else {
			client.player.connection.sendChat(ChannelBar.applyPrefix(text));
		}
	}

	private static int visibleRows(ChatScreen screen) {
		int available = ChannelBar.barY(screen) - 4 - PADDING * 2 - (ROW + GAP) * 2;
		return Math.max(1, Math.min(messages().size(), available / (ROW + GAP)));
	}

	private static Rect panel(ChatScreen screen) {
		int rows = Math.max(1, visibleRows(screen)) + 1;
		int height = PADDING * 2 + rows * (ROW + GAP) - GAP;
		return new Rect(screen.width - 2 - WIDTH, ChannelBar.barY(screen) - 2 - height, WIDTH, height);
	}

	private static int backWidth() {
		return Gfx.iconLabelWidth(Minecraft.getInstance().font, Icons.BACK, Theme.BACK) + 10;
	}

	private static Rect backButton(Rect panel) {
		return new Rect(panel.x() + PADDING, panel.y() + PADDING, backWidth(), ROW);
	}

	private static Rect saveButton(Rect panel) {
		return new Rect(panel.x() + PADDING + backWidth() + GAP, panel.y() + PADDING, panel.w() - PADDING * 2 - backWidth() - GAP, ROW);
	}

	private static Rect row(Rect panel, int slot) {
		return new Rect(panel.x() + PADDING, panel.y() + PADDING + (slot + 1) * (ROW + GAP), panel.w() - PADDING * 2 - ROW - GAP, ROW);
	}

	private static Rect delete(Rect row) {
		return new Rect(row.x() + row.w() + GAP, row.y(), ROW, ROW);
	}

	public static void render(ChatScreen screen, Gfx gfx, String input, int mouseX, int mouseY) {
		if (!open) {
			return;
		}
		Font font = Minecraft.getInstance().font;
		List<Component> tooltip = new ArrayList<>();
		Rect panel = panel(screen);
		gfx.fill(panel.x(), panel.y(), panel.x() + panel.w(), panel.y() + panel.h(), 0xD0000000);

		Rect back = backButton(panel);
		gfx.fill(back.x(), back.y(), back.x() + back.w(), back.y() + back.h(), back.contains(mouseX, mouseY) ? 0x60FFFFFF : 0x30FFFFFF);
		gfx.iconLabel(font, Icons.BACK, Theme.BACK, back.x(), back.y(), back.w(), back.h(), 0xFFFFFFFF);

		Rect save = saveButton(panel);
		String reason = saveReason(input);
		boolean saveHovered = save.contains(mouseX, mouseY);
		gfx.fill(save.x(), save.y(), save.x() + save.w(), save.y() + save.h(), reason == null && saveHovered ? 0x6000C060 : 0x30FFFFFF);
		gfx.iconLabel(font, Icons.PLUS, "儲存目前輸入", save.x(), save.y(), save.w(), save.h(), reason == null ? 0xFFCFFFC0 : Theme.TEXT_DIM);
		if (saveHovered) {
			tooltip.add(Component.literal(reason == null ? "儲存：" + input.strip() : reason));
		}

		List<String> list = messages();
		if (list.isEmpty()) {
			Rect hint = row(panel, 0);
			gfx.label(font, "在聊天欄輸入文字，再按上方按鈕儲存", hint.x() + 2, hint.y(), hint.h(), Theme.TEXT_DIM);
		}
		int rows = visibleRows(screen);
		scroll = Math.clamp(scroll, 0, Math.max(0, list.size() - rows));
		for (int slot = 0; slot < rows && scroll + slot < list.size(); slot++) {
			String text = list.get(scroll + slot);
			Rect row = row(panel, slot);
			Rect delete = delete(row);
			boolean hovered = row.contains(mouseX, mouseY);
			gfx.fill(row.x(), row.y(), row.x() + row.w(), row.y() + row.h(), hovered ? 0x60FFFFFF : 0x30FFFFFF);
			String label = Gfx.fit(font, text, row.w() - 8);
			gfx.label(font, label, row.x() + 4, row.y(), row.h(), text.startsWith("/") ? 0xFFCFFFC0 : 0xFFFFFFFF);
			boolean deleteHovered = delete.contains(mouseX, mouseY);
			gfx.fill(delete.x(), delete.y(), delete.x() + delete.w(), delete.y() + delete.h(), deleteHovered ? 0x80FF5050 : 0x30FFFFFF);
			Icons.CLOSE.drawCentered(gfx, delete.x(), delete.y(), delete.w(), delete.h(), deleteHovered ? 0xFFFFFFFF : Theme.TEXT_DIM);
			if (hovered) {
				tooltip.add(Component.literal(text));
				tooltip.add(Component.literal("點擊送出 · Shift+點擊插入到輸入框").withStyle(ChatFormatting.GRAY));
			} else if (deleteHovered) {
				tooltip.add(Component.literal("刪除這則常用訊息"));
			}
		}
		if (!tooltip.isEmpty()) {
			gfx.tooltip(font, tooltip, mouseX, mouseY);
		}
	}

	public static boolean mouseClicked(ChatScreen screen, String input, double mouseX, double mouseY, int button) {
		if (!open || button != InputConstants.MOUSE_BUTTON_LEFT) {
			return false;
		}
		Rect panel = panel(screen);
		if (!panel.contains(mouseX, mouseY)) {
			return false;
		}
		if (backButton(panel).contains(mouseX, mouseY)) {
			Theme.click();
			ChatMenu.backToMenu(screen);
			return true;
		}
		if (saveButton(panel).contains(mouseX, mouseY)) {
			Theme.click();
			add(input);
			return true;
		}
		List<String> list = messages();
		int rows = visibleRows(screen);
		for (int slot = 0; slot < rows && scroll + slot < list.size(); slot++) {
			Rect row = row(panel, slot);
			if (delete(row).contains(mouseX, mouseY)) {
				Theme.click();
				remove(scroll + slot);
				return true;
			}
			if (row.contains(mouseX, mouseY)) {
				String text = list.get(scroll + slot);
				Theme.click();
				if (Minecraft.getInstance().hasShiftDown()) {
					PrivateWindow.insert(screen, text);
				} else {
					send(text);
					close();
					CommandMenuScreen.setScreen(null);
				}
				return true;
			}
		}
		return true;
	}

	public static boolean mouseScrolled(ChatScreen screen, double mouseX, double mouseY, double amount) {
		if (!open || !panel(screen).contains(mouseX, mouseY)) {
			return false;
		}
		scroll = Math.max(0, scroll - (int) Math.signum(amount));
		return true;
	}
}
