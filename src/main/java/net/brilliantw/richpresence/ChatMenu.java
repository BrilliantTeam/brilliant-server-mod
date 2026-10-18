package net.brilliantw.richpresence;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.Function;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;

public final class ChatMenu {
	record Item(String section, String label, String detail, int color, Consumer<ChatScreen> run) {
		String haystack() {
			return (section + " " + label + " " + detail).toLowerCase(Locale.ROOT);
		}
	}

	private record Rect(int x, int y, int w, int h) {
		boolean contains(double mouseX, double mouseY) {
			return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
		}
	}

	private record Placed(Item item, Rect rect) {}

	private static final int WIDTH = 250;
	private static final int PADDING = 4;
	private static final int ROW = 14;
	private static final int GAP = 2;
	private static final int SEARCH = 14;
	private static final int SECTION = 30;
	private static final int MAX_RESULTS = 8;
	private static final int INFO = 0xFF7AD3FF;
	private static final int CHAT = 0xFFF9C3F5;
	private static final int TOOL = 0xFFCFFFC0;
	private static final int SHOW = 0xFFCFFFC0;
	private static final int ACTION = 0xFFF6E0A4;

	private static boolean open;
	private static EditBox search;
	private static int selected;
	private static int scroll;
	private static ChatScreen reopenFor;

	private ChatMenu() {}

	public static EditBox createSearch() {
		search = new EditBox(Minecraft.getInstance().font, 0, 0, 100, 10, Component.literal("搜尋"));
		search.setBordered(false);
		search.setMaxLength(50);
		search.setResponder(value -> {
			selected = 0;
			scroll = 0;
		});
		search.visible = open;
		return search;
	}

	public static void onInit(ChatScreen screen) {
		if (reopenFor == screen) {
			open(screen);
		}
		reopenFor = null;
	}

	public static void backToMenu(ChatScreen screen) {
		ChannelBar.close();
		open(screen);
	}

	private static Consumer<ChatScreen> page(Function<ChatScreen, Screen> factory) {
		return screen -> {
			reopenFor = screen;
			CommandMenuScreen.setScreen(factory.apply(screen));
		};
	}

	public static EditBox search() {
		return search;
	}

	public static boolean isOpen() {
		return open;
	}

	public static void open(ChatScreen screen) {
		open = true;
		selected = 0;
		scroll = 0;
		if (search != null) {
			search.setValue("");
			search.visible = true;
			screen.setFocused(search);
		}
	}

	public static void close() {
		open = false;
		if (search != null) {
			search.visible = false;
			search.setFocused(false);
		}
	}

	private static Consumer<ChatScreen> insert(String text) {
		return screen -> PrivateWindow.insert(screen, text);
	}

	private static Consumer<ChatScreen> command(String command) {
		return screen -> {
			Minecraft client = Minecraft.getInstance();
			if (client.player != null) {
				client.player.connection.sendCommand(command);
			}
		};
	}

	static List<Item> items() {
		List<Item> items = new ArrayList<>();
		ModUpdate.Latest update = ModUpdate.available();
		if (update != null) {
			items.add(new Item("資訊", "更新至 " + ModUpdate.label(update), update.notes.isEmpty() ? "輝煌模組有新版本" : update.notes, Theme.WARNING, page(UpdateScreen::new)));
		}
		items.addAll(baseItems());
		return items;
	}

	private static List<Item> baseItems() {
		int unread = AnnouncementStore.totalUnread();
		return List.of(
				new Item("資訊", unread > 0 ? "公告 " + unread : "公告", unread > 0 ? "有 " + unread + " 則新公告" : "伺服器公告", unread > 0 ? Theme.WARNING : INFO,
						page(AnnouncementScreen::new)),
				new Item("資訊", "官網", "搜尋與閱讀官網文章", INFO, page(GuideScreen::new)),
				new Item("資訊", "聊天紀錄", "搜尋與複製聊天紀錄", INFO, page(ChatHistoryScreen::new)),
				new Item("資訊", "贊助輝煌", "查看贊助方案與回饋，支持伺服器營運", INFO, page(SponsorScreen::new)),
				new Item("聊天", "常用訊息", "儲存與快速送出常用訊息", CHAT, screen -> QuickMessages.toggle()),
				new Item("聊天", "頻道設定", "切換發言、讀取與關閉各頻道", CHAT, screen -> ChannelBar.openSettings()),
				new Item("工具", "自訂指令", "開啟自訂指令選單", TOOL, page(CommandMenuScreen::new)),
				new Item("工具", "分享座標", "插入目前座標與地獄換算", TOOL, screen -> insert(CoordTools.shareText()).accept(screen)),
				new Item("工具", "自動腳本", "用積木組合自動化腳本", TOOL, page(ScriptEditorScreen::new)),
				new Item("工具", "記分板位置", "拖曳移動、滾輪縮放記分板", TOOL, page(ScoreboardEditScreen::new)),
				new Item("伺服器", "伺服器選單", "/menu", INFO, command("menu")),
				new Item("伺服器", "切換分流", "/bs", INFO, command("bs")),
				new Item("資訊欄", "開關", "/sbt 顯示或隱藏記分板", INFO, command("sbt")),
				new Item("資訊欄", "壹頁", "/sbt1 個人資訊與在線人數", INFO, command("sbt1")),
				new Item("資訊欄", "貳頁", "/sbt2 領地與傳點", INFO, command("sbt2")),
				new Item("資訊欄", "參頁", "/sbt3 領地飛行與全地圖飛行", INFO, command("sbt3")),
				new Item("資訊欄", "肆頁", "/sbt4 連鎖功能與雜項", INFO, command("sbt4")),
				new Item("資訊欄", "伍頁", "/sbt5 防護與剝皮功能", INFO, command("sbt5")),
				new Item("展示", "手上物品", "插入 [i]", SHOW, insert("[i]")),
				new Item("展示", "背包", "插入 [inv]", SHOW, insert("[inv]")),
				new Item("展示", "終界箱", "插入 [ender]", SHOW, insert("[ender]")),
				new Item("動作", "坐下", "/sit", ACTION, command("sit")),
				new Item("動作", "躺下", "/lay", ACTION, command("lay")),
				new Item("動作", "旋轉", "/spin", ACTION, command("spin")),
				new Item("動作", "趴下", "/bellyflop", ACTION, command("bellyflop"))
		);
	}

	private static List<Item> searchable() {
		List<Item> all = new ArrayList<>(items());
		for (ChannelBar.Row row : ChannelBar.ROWS) {
			for (ChannelBar.Action action : row.actions()) {
				all.add(new Item("頻道", row.name() + "・" + action.label(), "/" + action.command(), row.color(), command(action.command())));
			}
		}
		for (String message : QuickMessages.messages()) {
			all.add(new Item("常用", message, "點擊送出", CHAT, screen -> {
				QuickMessages.send(message);
				CommandMenuScreen.setScreen(null);
			}));
		}
		for (CommandStore.Node node : CommandStore.flatten(CommandStore.get().groups, false)) {
			for (CommandStore.Entry entry : node.group().commands) {
				all.add(new Item("指令", "/" + entry.command, entry.note.isEmpty() ? node.path() : entry.note, TOOL, screen -> {
					command(entry.command).accept(screen);
					CommandMenuScreen.setScreen(null);
				}));
			}
		}
		return all;
	}

	static List<Item> filter(List<Item> items, String query) {
		String[] terms = query.toLowerCase(Locale.ROOT).trim().split("\\s+");
		return items.stream().filter(item -> {
			String haystack = item.haystack();
			for (String term : terms) {
				if (!haystack.contains(term)) {
					return false;
				}
			}
			return true;
		}).toList();
	}

	private static String query() {
		return search == null ? "" : search.getValue().trim();
	}

	private static List<Item> results() {
		return filter(searchable(), query());
	}

	private static int chipWidth(Font font, Item item) {
		return font.width(item.label()) + 10;
	}

	private static List<Placed> gridLayout(Font font, int x, int y) {
		List<Placed> placed = new ArrayList<>();
		int left = x + PADDING + SECTION;
		int right = x + WIDTH - PADDING;
		int cx = left;
		int cy = y;
		String section = null;
		for (Item item : items()) {
			int w = chipWidth(font, item);
			if (!item.section().equals(section)) {
				if (section != null) {
					cy += ROW + GAP;
				}
				section = item.section();
				cx = left;
			} else if (cx + w > right) {
				cx = left;
				cy += ROW + GAP;
			}
			placed.add(new Placed(item, new Rect(cx, cy, w, ROW)));
			cx += w + GAP;
		}
		return placed;
	}

	private static int contentHeight(Font font) {
		if (!query().isEmpty()) {
			return Math.max(1, Math.min(MAX_RESULTS, results().size())) * (ROW + GAP) + ROW;
		}
		List<Placed> grid = gridLayout(font, 0, 0);
		return grid.isEmpty() ? ROW : grid.getLast().rect().y() + ROW;
	}

	private static Rect panel(ChatScreen screen, Font font) {
		int height = PADDING * 3 + SEARCH + contentHeight(font);
		int right = ChannelBar.menuRight(screen);
		return new Rect(Math.max(2, right - WIDTH), ChannelBar.barY(screen) - 2 - height, WIDTH, height);
	}

	private static int contentTop(Rect panel) {
		return panel.y() + PADDING * 2 + SEARCH;
	}

	private static List<Placed> resultLayout(Rect panel, List<Item> results) {
		List<Placed> placed = new ArrayList<>();
		int top = contentTop(panel);
		for (int i = 0; i < MAX_RESULTS && scroll + i < results.size(); i++) {
			placed.add(new Placed(results.get(scroll + i), new Rect(panel.x() + PADDING, top + i * (ROW + GAP), panel.w() - PADDING * 2, ROW)));
		}
		return placed;
	}

	public static void renderBackground(ChatScreen screen, Gfx gfx) {
		if (!open) {
			return;
		}
		Font font = Minecraft.getInstance().font;
		Rect panel = panel(screen, font);
		gfx.fill(panel.x(), panel.y(), panel.x() + panel.w(), panel.y() + panel.h(), 0xE8101418);
		gfx.fill(panel.x(), panel.y(), panel.x() + panel.w(), panel.y() + 1, Theme.ACCENT_DIM);
		int sy = panel.y() + PADDING;
		gfx.fill(panel.x() + PADDING, sy, panel.x() + panel.w() - PADDING, sy + SEARCH, 0xFF1E252C);
		Icons.SEARCH.draw(gfx, panel.x() + PADDING + 4, sy + (SEARCH - Icons.SEARCH.height()) / 2, Theme.TEXT_DIM);
		if (search != null) {
			search.setX(panel.x() + PADDING + 14);
			search.setY(sy + 3);
			search.setWidth(panel.w() - PADDING * 2 - 18);
			Theme.hint(search, "搜尋功能、動作、常用訊息、指令…");
		}
	}

	public static void render(ChatScreen screen, Gfx gfx, int mouseX, int mouseY) {
		if (!open) {
			return;
		}
		Font font = Minecraft.getInstance().font;
		Rect panel = panel(screen, font);
		List<Component> tooltip = new ArrayList<>();
		if (query().isEmpty()) {
			String section = null;
			for (Placed placed : gridLayout(font, panel.x(), contentTop(panel))) {
				Item item = placed.item();
				Rect r = placed.rect();
				if (!item.section().equals(section)) {
					section = item.section();
					gfx.label(font, section, panel.x() + PADDING, r.y(), r.h(), Theme.TEXT_DIM);
				}
				boolean hovered = r.contains(mouseX, mouseY);
				gfx.fill(r.x(), r.y(), r.x() + r.w(), r.y() + r.h(), hovered ? 0x60FFFFFF : 0x28FFFFFF);
				gfx.centered(font, item.label(), r.x(), r.y(), r.w(), r.h(), item.color());
				if (hovered) {
					tooltip.add(Component.literal(item.detail()));
				}
			}
		} else {
			List<Item> results = results();
			selected = Math.clamp(selected, 0, Math.max(0, results.size() - 1));
			if (results.isEmpty()) {
				gfx.label(font, "找不到符合的功能", panel.x() + PADDING + 2, contentTop(panel), ROW, Theme.TEXT_DIM);
			}
			List<Placed> rows = resultLayout(panel, results);
			for (int i = 0; i < rows.size(); i++) {
				Item item = rows.get(i).item();
				Rect r = rows.get(i).rect();
				boolean active = scroll + i == selected;
				boolean hovered = r.contains(mouseX, mouseY);
				gfx.fill(r.x(), r.y(), r.x() + r.w(), r.y() + r.h(), active ? Theme.SELECTED : hovered ? 0x40FFFFFF : 0x18FFFFFF);
				if (active) {
					gfx.fill(r.x(), r.y(), r.x() + 2, r.y() + r.h(), Theme.ACCENT);
				}
				String tag = item.section();
				gfx.label(font, tag, r.x() + 5, r.y(), r.h(), Theme.TEXT_DIM);
				int lx = r.x() + 5 + font.width("伺服器") + 6;
				int detailWidth = Math.min(font.width(item.detail()), r.w() / 3);
				String label = font.plainSubstrByWidth(item.label(), r.x() + r.w() - lx - detailWidth - 10);
				gfx.label(font, label, lx, r.y(), r.h(), item.color());
				String detail = font.plainSubstrByWidth(item.detail(), detailWidth);
				gfx.label(font, detail, r.x() + r.w() - 4 - font.width(detail), r.y(), r.h(), Theme.TEXT_DIM);
				if (hovered && (font.width(item.label()) > font.width(label) || font.width(item.detail()) > detailWidth)) {
					tooltip.add(Component.literal(item.label()));
					tooltip.add(Component.literal(item.detail()).withStyle(ChatFormatting.GRAY));
				}
			}
			String hint = "方向鍵選擇 · Enter 執行 · Esc 關閉" + (results.size() > MAX_RESULTS ? " · 共 " + results.size() + " 項" : "");
			gfx.text(font, hint, panel.x() + PADDING + 2, panel.y() + panel.h() - PADDING - 9, Theme.TEXT_DIM);
		}
		if (!tooltip.isEmpty()) {
			gfx.tooltip(font, tooltip, mouseX, mouseY);
		}
	}

	private static void run(ChatScreen screen, Item item) {
		close();
		item.run().accept(screen);
	}

	public static boolean mouseClicked(ChatScreen screen, double mouseX, double mouseY, int button) {
		if (!open) {
			return false;
		}
		Font font = Minecraft.getInstance().font;
		Rect panel = panel(screen, font);
		if (!panel.contains(mouseX, mouseY)) {
			close();
			return false;
		}
		if (button != InputConstants.MOUSE_BUTTON_LEFT) {
			return true;
		}
		if (mouseY < contentTop(panel)) {
			screen.setFocused(search);
			return true;
		}
		List<Placed> placed = query().isEmpty() ? gridLayout(font, panel.x(), contentTop(panel)) : resultLayout(panel, results());
		for (Placed p : placed) {
			if (p.rect().contains(mouseX, mouseY)) {
				run(screen, p.item());
				return true;
			}
		}
		return true;
	}

	public static boolean mouseScrolled(ChatScreen screen, double mouseX, double mouseY, double amount) {
		if (!open || !panel(screen, Minecraft.getInstance().font).contains(mouseX, mouseY)) {
			return false;
		}
		int size = results().size();
		scroll = Math.clamp(scroll - (int) Math.signum(amount), 0, Math.max(0, size - MAX_RESULTS));
		return true;
	}

	public static boolean keyPressed(ChatScreen screen, KeyEvent event) {
		if (!open) {
			if (event.isEscape() && (QuickMessages.isOpen() || ChannelBar.settingsOpen())) {
				backToMenu(screen);
				return true;
			}
			return false;
		}
		if (event.isEscape()) {
			close();
			return true;
		}
		if (screen.getFocused() != search) {
			return false;
		}
		List<Item> results = query().isEmpty() ? List.of() : results();
		if (event.isUp() || event.isDown()) {
			if (!results.isEmpty()) {
				selected = Math.floorMod(selected + (event.isDown() ? 1 : -1), results.size());
				scroll = Math.clamp(scroll, Math.max(0, selected - MAX_RESULTS + 1), selected);
			}
			return true;
		}
		if (event.isConfirmation()) {
			if (!results.isEmpty()) {
				run(screen, results.get(Math.clamp(selected, 0, results.size() - 1)));
			}
			return true;
		}
		return false;
	}
}
