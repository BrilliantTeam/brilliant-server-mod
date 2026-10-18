package net.brilliantw.richpresence;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
//? if >=26.1 {
import net.minecraft.client.gui.GuiGraphicsExtractor;
//?} else {
/*import net.minecraft.client.gui.GuiGraphics;
*///?}
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.network.chat.Component;

public final class ChannelBar {
	private record Channel(String name, String prefix, int color) {}

	record Action(String label, String command) {}

	record Row(String name, int color, List<Action> actions) {}

	private record Button<T>(int x, int y, int width, int height, T value) {
		boolean contains(double mouseX, double mouseY) {
			return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
		}
	}

	private static final int CHAT = 0xFFBBDDF6;
	private static final int TRADE = 0xFFF9C3F5;
	private static final int AREA = 0xFFF7E0A4;
	private static final int GUILD = 0xFFF6C236;
	private static final int RESIDENCE = 0xFFF6E0A4;
	private static final int SYSTEM = 0xFF7AD3FF;

	private static final List<Channel> CHANNELS = List.of(
			new Channel("一般", "", 0xFFFFFFFF),
			new Channel("聊天", "!", CHAT),
			new Channel("交易", "?", TRADE),
			new Channel("區域", "-", AREA),
			new Channel("公會", "+", GUILD),
			new Channel("領地", "*", RESIDENCE)
	);

	static final List<Row> ROWS = List.of(
			new Row("聊天頻道", CHAT, List.of(new Action("切換發言", "joinchat"), new Action("讀取", "listenchat"), new Action("關閉讀取", "leavechat"))),
			new Row("交易頻道", TRADE, List.of(new Action("切換發言", "jointrade"), new Action("讀取", "listentrade"), new Action("關閉讀取", "leavetrade"))),
			new Row("區域頻道", AREA, List.of(new Action("切換發言", "joinarea"), new Action("讀取", "listenarea"), new Action("關閉讀取", "leavearea"))),
			new Row("領地頻道", RESIDENCE, List.of(new Action("進入／離開", "rc"))),
			new Row("公會頻道", GUILD, List.of(new Action("進入／離開", "gd gc"))),
			new Row("私訊", 0xFFAAAAAA, List.of(new Action("全體私訊開關", "msgtoggle"), new Action("封鎖列表", "ignore list"), new Action("私訊音效", "notify"))),
			new Row("顯示", SYSTEM, List.of(new Action("遊戲廣播", "bla"), new Action("系統訊息", "tsm")))
	);

	private static final String MENU_LABEL = "選單";
	private static final int HEIGHT = 14;
	private static final int GAP = 2;
	private static final int PADDING = 3;
	private static final int EMOJI_BUTTON = 14;

	private static int selected;
	private static boolean settingsOpen;

	private ChannelBar() {}

	private static boolean active() {
		return RichPresenceModClient.featuresActive();
	}

	public static void close() {
		settingsOpen = false;
		QuickMessages.close();
		ChatMenu.close();
	}

	public static boolean settingsOpen() {
		return settingsOpen;
	}

	public static void openSettings() {
		close();
		settingsOpen = true;
	}

	public static String applyPrefix(String message) {
		String prefix = CHANNELS.get(selected).prefix();
		if (prefix.isEmpty() || message.isBlank() || message.startsWith("/") || !RichPresenceModClient.isOnBrilliantServer()) {
			return message;
		}
		return prefix + message;
	}

	public static int barY(ChatScreen screen) {
		return screen.height - 16 - HEIGHT;
	}

	private static int buttonWidth(Font font, String label) {
		return font.width(label) + 8;
	}

	private static List<Button<Integer>> chips(Font font, ChatScreen screen) {
		List<Button<Integer>> buttons = new ArrayList<>();
		if (!RichPresenceModClient.isOnBrilliantServer()) {
			return buttons;
		}
		int x = 2;
		for (int i = 0; i < CHANNELS.size(); i++) {
			int width = buttonWidth(font, CHANNELS.get(i).name());
			buttons.add(new Button<>(x, barY(screen), width, HEIGHT, i));
			x += width + GAP;
		}
		return buttons;
	}

	private static Button<Void> menuButton(Font font, ChatScreen screen) {
		int width = Gfx.iconLabelWidth(font, Icons.MENU, MENU_LABEL) + 10 + (badgeCount() > 0 ? 12 : 0);
		return new Button<>(screen.width - 2 - EMOJI_BUTTON - GAP - width, barY(screen), width, HEIGHT, null);
	}

	private static int labelWidth(Font font) {
		return ROWS.stream().mapToInt(row -> font.width(row.name())).max().orElse(0) + 8;
	}

	private static Button<Void> panel(Font font, ChatScreen screen) {
		int actionsWidth = ROWS.stream()
				.mapToInt(row -> row.actions().stream().mapToInt(action -> buttonWidth(font, action.label()) + GAP).sum() - GAP)
				.max().orElse(0);
		int width = PADDING * 2 + labelWidth(font) + actionsWidth;
		int height = PADDING * 2 + (ROWS.size() + 1) * (HEIGHT + GAP) - GAP;
		return new Button<>(screen.width - 2 - width, barY(screen) - 2 - height, width, height, null);
	}

	private static List<Button<Action>> actions(Font font, Button<Void> panel) {
		List<Button<Action>> buttons = new ArrayList<>();
		for (int i = 0; i < ROWS.size(); i++) {
			int x = panel.x() + PADDING + labelWidth(font);
			int y = panel.y() + PADDING + (i + 1) * (HEIGHT + GAP);
			for (Action action : ROWS.get(i).actions()) {
				int width = buttonWidth(font, action.label());
				buttons.add(new Button<>(x, y, width, HEIGHT, action));
				x += width + GAP;
			}
		}
		return buttons;
	}

	private static Button<Void> backButton(Font font, Button<Void> panel) {
		return new Button<>(panel.x() + PADDING, panel.y() + PADDING, Gfx.iconLabelWidth(font, Icons.BACK, Theme.BACK) + 10, HEIGHT, null);
	}

	private static int badgeCount() {
		return AnnouncementStore.totalUnread() + (ModUpdate.available() != null ? 1 : 0);
	}

	public static int menuRight(ChatScreen screen) {
		Font font = Minecraft.getInstance().font;
		Button<Void> menu = menuButton(font, screen);
		return menu.x() + menu.width();
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
		Gfx gfx = new Gfx(graphics);
		Component tooltip = null;

		for (Button<Integer> chip : chips(font, screen)) {
			Channel channel = CHANNELS.get(chip.value());
			boolean hovered = chip.contains(mouseX, mouseY);
			int background = chip.value() == selected ? 0xD0505050 : hovered ? 0xA0303030 : 0x80000000;
			gfx.fill(chip.x(), chip.y(), chip.x() + chip.width(), chip.y() + chip.height(), background);
			if (chip.value() == selected) {
				gfx.fill(chip.x(), chip.y() + chip.height() - 1, chip.x() + chip.width(), chip.y() + chip.height(), channel.color());
			}
			gfx.centered(font, channel.name(), chip.x(), chip.y(), chip.width(), chip.height(), channel.color());
			if (hovered) {
				tooltip = Component.literal(channel.prefix().isEmpty()
						? "照目前所在頻道發言"
						: "快速發言：送出時自動在訊息前加上 " + channel.prefix());
			}
		}

		Button<Void> menu = menuButton(font, screen);
		boolean menuHovered = menu.contains(mouseX, mouseY);
		gfx.fill(menu.x(), menu.y(), menu.x() + menu.width(), menu.y() + menu.height(), ChatMenu.isOpen() || menuHovered ? 0xC0505050 : 0x80000000);
		gfx.iconLabel(font, Icons.MENU, MENU_LABEL, menu.x(), menu.y(), menu.width() - (badgeCount() > 0 ? 12 : 0), menu.height(), 0xFFFFFFFF);
		int unread = badgeCount();
		if (unread > 0) {
			String badge = unread > 9 ? "9+" : String.valueOf(unread);
			int bw = Math.max(9, font.width(badge) + 4);
			int bx = menu.x() + menu.width() - bw - 2;
			gfx.fill(bx, menu.y() + 2, bx + bw, menu.y() + HEIGHT - 2, 0xFFE04848);
			gfx.centered(font, badge, bx, menu.y() + 2, bw, HEIGHT - 4, 0xFFFFFFFF);
		}
		if (menuHovered) {
			int announcements = AnnouncementStore.totalUnread();
			String update = ModUpdate.available() != null ? "模組有新版本" : "";
			String news = announcements > 0 ? announcements + " 則新公告" : "";
			String extra = String.join("、", Stream.of(update, news).filter(s -> !s.isEmpty()).toList());
			tooltip = Component.literal(extra.isEmpty() ? "功能選單：公告、官網、常用訊息、互動與更多" : "功能選單（" + extra + "）");
		}

		if (settingsOpen && RichPresenceModClient.isOnBrilliantServer()) {
			Button<Void> panel = panel(font, screen);
			gfx.fill(panel.x(), panel.y(), panel.x() + panel.width(), panel.y() + panel.height(), 0xD0000000);
			for (int i = 0; i < ROWS.size(); i++) {
				Row row = ROWS.get(i);
				gfx.label(font, row.name(), panel.x() + PADDING + 2, panel.y() + PADDING + (i + 1) * (HEIGHT + GAP), HEIGHT, row.color());
			}
			Button<Void> back = backButton(font, panel);
			gfx.fill(back.x(), back.y(), back.x() + back.width(), back.y() + back.height(), back.contains(mouseX, mouseY) ? 0x60FFFFFF : 0x30FFFFFF);
			gfx.iconLabel(font, Icons.BACK, Theme.BACK, back.x(), back.y(), back.width(), back.height(), 0xFFFFFFFF);
			gfx.label(font, "頻道設定", back.x() + back.width() + 6, back.y(), back.height(), SYSTEM);
			for (Button<Action> button : actions(font, panel)) {
				boolean hovered = button.contains(mouseX, mouseY);
				gfx.fill(button.x(), button.y(), button.x() + button.width(), button.y() + button.height(), hovered ? 0x60FFFFFF : 0x30FFFFFF);
				gfx.centered(font, button.value().label(), button.x(), button.y(), button.width(), button.height(), 0xFFFFFFFF);
				if (hovered) {
					tooltip = Component.literal("/" + button.value().command());
				}
			}
		}

		if (tooltip != null) {
			gfx.tooltip(font, List.of(tooltip), mouseX, mouseY);
		}
	}

	public static boolean mouseClicked(ChatScreen screen, double mouseX, double mouseY, int button) {
		if (!active() || button != InputConstants.MOUSE_BUTTON_LEFT) {
			return false;
		}
		Minecraft minecraft = Minecraft.getInstance();
		Font font = minecraft.font;
		for (Button<Integer> chip : chips(font, screen)) {
			if (chip.contains(mouseX, mouseY)) {
				selected = chip.value();
				return true;
			}
		}
		if (menuButton(font, screen).contains(mouseX, mouseY)) {
			boolean wasOpen = ChatMenu.isOpen();
			close();
			EmojiPicker.close();
			if (!wasOpen) {
				ChatMenu.open(screen);
			}
			return true;
		}
		if (!settingsOpen || !RichPresenceModClient.isOnBrilliantServer()) {
			return false;
		}
		Button<Void> panel = panel(font, screen);
		if (!panel.contains(mouseX, mouseY)) {
			settingsOpen = false;
			return false;
		}
		if (backButton(font, panel).contains(mouseX, mouseY)) {
			ChatMenu.backToMenu(screen);
			return true;
		}
		for (Button<Action> action : actions(font, panel)) {
			if (action.contains(mouseX, mouseY) && minecraft.player != null) {
				minecraft.player.connection.sendCommand(action.value().command());
			}
		}
		return true;
	}
}
