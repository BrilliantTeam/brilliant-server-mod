package net.brilliantw.richpresence;

import com.mojang.blaze3d.platform.InputConstants;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.util.FormattedCharSequence;

public final class PrivateWindow {
	private record Rect(int x, int y, int w, int h) {
		boolean contains(double mouseX, double mouseY) {
			return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
		}
	}

	private record Tab(Rect rect, PrivateMessages.Conversation conversation) {}

	private record Line(FormattedCharSequence text, String face) {}

	private enum Popup { NONE, EMOJI, SHOWCASE }

	private static final int MIN_WIDTH = 170;
	private static final int MIN_BODY = 40;
	private static final int FACE = 8;
	private static final int GRIP = 7;
	private static final int HEADER = 14;
	private static final Scroller TAB_SCROLL = new Scroller();
	private static PrivateMessages.Conversation revealed;
	private static PrivateMessages.Conversation pendingTab;
	private static double pressX;
	private static double pressY;
	private static final int FOOTER = 20;
	private static final int PAD = 4;
	private static final int LINE = 10;
	private static final int BACKGROUND = 0xC0101418;
	private static final List<String[]> SHOWCASE = List.of(new String[] { "手上物品", "[i]" }, new String[] { "背包", "[inv]" }, new String[] { "終界箱", "[ender]" });

	private static EditBox input;
	private static String draft = "";
	private static Popup popup = Popup.NONE;
	private static boolean dragging;
	private static double dragX;
	private static double dragY;
	private static double scroll;
	private static final EmojiPicker.Panel EMOJI_PANEL = new EmojiPicker.Panel();
	private static boolean resizing;
	private static double resizeX;
	private static double resizeY;
	private static int resizeWidth;
	private static int resizeBody;

	private PrivateWindow() {}

	private static Font font() {
		return Minecraft.getInstance().font;
	}

	private static int bodyHeight() {
		int screenHeight = Minecraft.getInstance().getWindow().getGuiScaledHeight();
		return Math.clamp(PrivateMessages.bodyHeight(), MIN_BODY, Math.max(MIN_BODY, screenHeight - HEADER - FOOTER - 8));
	}

	private static void face(Gfx gfx, String name, int x, int y) {
		RemoteImages.Entry entry = RemoteImages.get(RemoteImages.HEAD_PREFIX + URLEncoder.encode(name, StandardCharsets.UTF_8), RemoteImages.requestWidth(FACE));
		if (entry.state == RemoteImages.State.READY) {
			RemoteImages.draw(gfx, entry, x, y, FACE, FACE);
		} else {
			gfx.fill(x, y, x + FACE, y + FACE, Theme.CHIP);
		}
	}

	private static Rect grip(Rect frame) {
		return new Rect(frame.x() + frame.w() - GRIP, frame.y() + frame.h() - GRIP, GRIP, GRIP);
	}

	private static boolean visible() {
		return RichPresenceModClient.isOnBrilliantServer() && ModSettings.get().privateWindow && !PrivateMessages.conversations().isEmpty();
	}

	public static EditBox createInput() {
		input = new EditBox(font(), 0, 0, 100, 16, Component.literal("私訊"));
		input.setMaxLength(256);
		input.setValue(draft);
		input.setResponder(value -> draft = value);
		input.visible = false;
		return input;
	}

	public static EditBox focusedInput(ChatScreen screen) {
		return inputFocused(screen) ? input : null;
	}

	public static boolean inputFocused(ChatScreen screen) {
		return input != null && input.visible && screen.getFocused() == input;
	}

	public static void insert(ChatScreen screen, String text) {
		if (inputFocused(screen)) {
			input.insertText(text);
		} else {
			screen.insertText(text, false);
		}
	}

	public static EditBox createEmojiSearch() {
		return EMOJI_PANEL.createSearch();
	}

	public static boolean keyPressed(ChatScreen screen, KeyEvent event) {
		if (popup == Popup.EMOJI && EMOJI_PANEL.searchFocused(screen)) {
			if (event.isEscape()) {
				popup = Popup.NONE;
				screen.setFocused(input);
				return true;
			}
			if (event.isConfirmation()) {
				EmojiPicker.Emoji emoji = EMOJI_PANEL.first();
				if (emoji != null && input != null) {
					input.insertText(emoji.insert());
				}
				return true;
			}
			return event.isUp() || event.isDown();
		}
		if (!inputFocused(screen) || event.isEscape()) {
			return false;
		}
		if (event.isConfirmation()) {
			send();
		} else {
			input.keyPressed(event);
		}
		return true;
	}

	public static void send() {
		PrivateMessages.Conversation selected = PrivateMessages.selected();
		if (input == null || selected == null || input.getValue().isBlank()) {
			return;
		}
		PrivateMessages.send(selected.name, input.getValue());
		input.setValue("");
		scroll = 0;
	}

	private static String pillLabel() {
		int unread = PrivateMessages.totalUnread();
		return "私訊" + (unread > 0 ? " " + unread : "");
	}

	private static boolean expandedInteractive(boolean interactive) {
		return interactive && !PrivateMessages.collapsed() && PrivateMessages.selected() != null;
	}

	private static Rect frame(int screenWidth, int screenHeight, boolean interactive) {
		int w = PrivateMessages.collapsed() ? Gfx.iconLabelWidth(font(), Icons.MAIL, pillLabel()) + 12 : Math.clamp(PrivateMessages.windowWidth(), MIN_WIDTH, Math.max(MIN_WIDTH, screenWidth - 8));
		int h = PrivateMessages.collapsed() ? HEADER : HEADER + bodyHeight() + (expandedInteractive(interactive) ? FOOTER : 0);
		int x = Math.clamp(PrivateMessages.windowX(), 0, Math.max(0, screenWidth - w));
		int y = Math.clamp(PrivateMessages.windowY(), 0, Math.max(0, screenHeight - h));
		return new Rect(x, y, w, h);
	}

	private static Rect headerButton(Rect frame, int slot) {
		return new Rect(frame.x() + frame.w() - 13 * (slot + 1), frame.y() + 1, 12, HEADER - 2);
	}

	private static Rect tabView(Rect frame, boolean interactive) {
		return new Rect(frame.x() + PAD, frame.y(), frame.w() - PAD - (interactive ? 42 : 16), HEADER);
	}

	private static Rect tabBar(Rect frame, boolean interactive) {
		Rect view = tabView(frame, interactive);
		return new Rect(view.x(), frame.y() + HEADER - 4, view.w(), 4);
	}

	private static List<Tab> tabs(Rect frame, boolean interactive) {
		List<Tab> tabs = new ArrayList<>();
		Rect view = tabView(frame, interactive);
		PrivateMessages.Conversation selected = PrivateMessages.selected();
		int x = 0;
		int selectedX = -1;
		int selectedW = 0;
		for (PrivateMessages.Conversation conversation : PrivateMessages.conversations()) {
			int w = font().width(tabLabel(conversation)) + 8 + FACE + 3;
			if (conversation == selected) {
				selectedX = x;
				selectedW = w;
			}
			tabs.add(new Tab(new Rect(x, frame.y() + 1, w, HEADER - 2), conversation));
			x += w + 2;
		}
		TAB_SCROLL.measure(Math.max(0, x - 2), view.w());
		if (selected != revealed) {
			revealed = selected;
			if (selectedX >= 0) {
				TAB_SCROLL.reveal(selectedX, selectedW);
			}
		}
		List<Tab> shifted = new ArrayList<>();
		for (Tab tab : tabs) {
			Rect r = tab.rect();
			Rect moved = new Rect(view.x() + r.x() - TAB_SCROLL.offset(), r.y(), r.w(), r.h());
			if (moved.x() + moved.w() > view.x() && moved.x() < view.x() + view.w()) {
				shifted.add(new Tab(moved, tab.conversation()));
			}
		}
		return shifted;
	}

	private static String tabLabel(PrivateMessages.Conversation conversation) {
		String name = font().plainSubstrByWidth(conversation.display, 70);
		return conversation.unread > 0 ? name + " " + conversation.unread : name;
	}

	private static Rect body(Rect frame) {
		return new Rect(frame.x() + PAD, frame.y() + HEADER + 2, frame.w() - PAD * 2, bodyHeight() - 4);
	}

	private static Rect footerChip(Rect frame, int slot) {
		String[] labels = { "傳送", "表情", "展示" };
		int right = frame.x() + frame.w() - PAD;
		for (int i = 0; i <= slot; i++) {
			int w = font().width(labels[i]) + 10;
			right -= w;
			if (i == slot) {
				return new Rect(right, frame.y() + HEADER + bodyHeight() + 2, w, FOOTER - 4);
			}
			right -= 2;
		}
		throw new IllegalStateException();
	}

	private static Rect inputRect(Rect frame) {
		int x = frame.x() + PAD;
		return new Rect(x, frame.y() + HEADER + bodyHeight() + 2, footerChip(frame, 2).x() - 3 - x, FOOTER - 4);
	}

	private static Rect emojiPanel(Rect frame) {
		int w = EmojiPicker.Panel.width(9);
		int h = EmojiPicker.Panel.height(9);
		Rect anchor = footerChip(frame, 1);
		int y = frame.y() + HEADER + bodyHeight() + FOOTER + 2;
		int screenHeight = Minecraft.getInstance().getWindow().getGuiScaledHeight();
		if (y + h > screenHeight) {
			y = frame.y() + HEADER + bodyHeight() - h;
		}
		return new Rect(Math.max(0, anchor.x() + anchor.w() - w), y, w, h);
	}

	private static List<Rect> showcaseItems(Rect frame) {
		Rect anchor = footerChip(frame, 2);
		int w = SHOWCASE.stream().mapToInt(item -> font().width(item[0] + " " + item[1])).max().orElse(0) + 12;
		int y = frame.y() + HEADER + bodyHeight() + FOOTER + 2;
		List<Rect> rects = new ArrayList<>();
		for (int i = 0; i < SHOWCASE.size(); i++) {
			rects.add(new Rect(Math.max(0, anchor.x() + anchor.w() - w), y + i * 17, w, 16));
		}
		return rects;
	}

	private static void chip(Gfx gfx, Rect rect, String label, int color, boolean hovered, boolean active) {
		gfx.fill(rect.x(), rect.y(), rect.x() + rect.w(), rect.y() + rect.h(), active ? Theme.SELECTED : hovered ? Theme.CHIP_HOVER : Theme.CHIP);
		if (active) {
			gfx.fill(rect.x(), rect.y() + rect.h() - 1, rect.x() + rect.w(), rect.y() + rect.h(), Theme.ACCENT);
		}
		gfx.centered(font(), label, rect.x(), rect.y(), rect.w(), rect.h(), color);
	}

	private static void iconChip(Gfx gfx, Rect rect, Icons icon, int color, boolean hovered) {
		gfx.fill(rect.x(), rect.y(), rect.x() + rect.w(), rect.y() + rect.h(), hovered ? Theme.CHIP_HOVER : Theme.CHIP);
		icon.drawCentered(gfx, rect.x(), rect.y(), rect.w(), rect.h(), color);
	}

	public static void renderHud(Gfx gfx) {
		Minecraft minecraft = Minecraft.getInstance();
		//? if >=26.2 {
		boolean hidden = minecraft.gui.screen() instanceof ChatScreen;
		//?} else {
		/*boolean hidden = minecraft.options.hideGui || minecraft.screen instanceof ChatScreen;
		*///?}
		if (hidden) {
			return;
		}
		render(gfx, minecraft.getWindow().getGuiScaledWidth(), minecraft.getWindow().getGuiScaledHeight(), -1, -1, false);
	}

	public static void render(Gfx gfx, int screenWidth, int screenHeight, int mouseX, int mouseY, boolean interactive) {
		boolean shown = visible();
		if (input != null) {
			input.visible = shown && expandedInteractive(interactive);
		}
		if (!shown) {
			popup = Popup.NONE;
			return;
		}
		Font font = font();
		Rect frame = frame(screenWidth, screenHeight, interactive);
		if (PrivateMessages.collapsed()) {
			boolean hovered = frame.contains(mouseX, mouseY);
			gfx.fill(frame.x(), frame.y(), frame.x() + frame.w(), frame.y() + frame.h(), hovered ? Theme.CHIP_HOVER : BACKGROUND);
			gfx.iconLabel(font, Icons.MAIL, pillLabel(), frame.x(), frame.y(), frame.w(), frame.h(), PrivateMessages.totalUnread() > 0 ? Theme.WARNING : Theme.ACCENT);
			return;
		}

		gfx.fill(frame.x(), frame.y(), frame.x() + frame.w(), frame.y() + frame.h(), BACKGROUND);
		gfx.fill(frame.x(), frame.y(), frame.x() + frame.w(), frame.y() + HEADER, Theme.HEADER);
		gfx.fill(frame.x(), frame.y() + HEADER - 1, frame.x() + frame.w(), frame.y() + HEADER, Theme.ACCENT_DIM);
		PrivateMessages.Conversation selected = PrivateMessages.selected();
		Rect view = tabView(frame, interactive);
		List<Tab> tabs = tabs(frame, interactive);
		gfx.scissor(view.x(), view.y(), view.x() + view.w(), view.y() + view.h());
		for (Tab tab : tabs) {
			boolean isSelected = tab.conversation() == selected;
			int color = tab.conversation().unread > 0 ? Theme.WARNING : isSelected ? Theme.TEXT : Theme.TEXT_SECONDARY;
			Rect r = tab.rect();
			chip(gfx, r, "", color, interactive && r.contains(mouseX, mouseY), isSelected);
			face(gfx, tab.conversation().name, r.x() + 4, r.y() + (r.h() - FACE) / 2);
			gfx.label(font, tabLabel(tab.conversation()), r.x() + 4 + FACE + 3, r.y(), r.h(), color);
		}
		gfx.endScissor();
		if (TAB_SCROLL.overflow()) {
			Rect bar = tabBar(frame, interactive);
			int start = bar.x() + TAB_SCROLL.thumbStart(bar.w());
			gfx.fill(bar.x(), bar.y() + 1, bar.x() + bar.w(), bar.y() + 3, 0x40FFFFFF);
			gfx.fill(start, bar.y() + 1, start + TAB_SCROLL.thumbLength(bar.w()), bar.y() + 3,
					TAB_SCROLL.dragging() || interactive && bar.contains(mouseX, mouseY) ? Theme.ACCENT : Theme.ACCENT_DIM);
		}
		iconChip(gfx, headerButton(frame, 0), Icons.MINUS, Theme.TEXT, interactive && headerButton(frame, 0).contains(mouseX, mouseY));
		if (interactive) {
			iconChip(gfx, headerButton(frame, 1), PrivateMessages.hideInChat() ? Icons.EYE_OFF : Icons.EYE, PrivateMessages.hideInChat() ? Theme.SUCCESS : Theme.TEXT_DIM, headerButton(frame, 1).contains(mouseX, mouseY));
			if (selected != null) {
				iconChip(gfx, headerButton(frame, 2), Icons.CLOSE, Theme.DANGER, headerButton(frame, 2).contains(mouseX, mouseY));
			}
		}

		drawMessages(gfx, frame, selected, interactive);

		if (expandedInteractive(interactive)) {
			Rect box = inputRect(frame);
			if (input != null) {
				input.setX(box.x());
				input.setY(box.y());
				input.setWidth(box.w());
				input.setMaxLength(PrivateMessages.maxMessageLength(selected.name));
				Theme.hint(input, "私訊 " + selected.display + "…");
			}
			chip(gfx, footerChip(frame, 0), "傳送", Theme.ACCENT, footerChip(frame, 0).contains(mouseX, mouseY), false);
			chip(gfx, footerChip(frame, 1), "表情", Theme.TEXT, footerChip(frame, 1).contains(mouseX, mouseY), popup == Popup.EMOJI);
			chip(gfx, footerChip(frame, 2), "展示", Theme.TEXT, footerChip(frame, 2).contains(mouseX, mouseY), popup == Popup.SHOWCASE);
			Rect g = grip(frame);
			int gripColor = resizing || g.contains(mouseX, mouseY) ? Theme.ACCENT : Theme.TEXT_DIM;
			for (int i = 0; i < 3; i++) {
				int d = i * 2 + 1;
				gfx.fill(g.x() + g.w() - d - 1, g.y() + g.h() - 2, g.x() + g.w() - d, g.y() + g.h() - 1, gripColor);
				gfx.fill(g.x() + g.w() - 2, g.y() + g.h() - d - 1, g.x() + g.w() - 1, g.y() + g.h() - d, gripColor);
			}
		} else {
			popup = Popup.NONE;
		}
	}

	public static void renderOverlay(Gfx gfx, int screenWidth, int screenHeight, int mouseX, int mouseY) {
		if (!visible()) {
			return;
		}
		Rect frame = frame(screenWidth, screenHeight, true);
		List<Component> tooltip = new ArrayList<>();
		if (popup == Popup.EMOJI) {
			Rect panel = emojiPanel(frame);
			EMOJI_PANEL.render(gfx, panel.x(), panel.y(), panel.w(), panel.h(), mouseX, mouseY);
		} else if (popup == Popup.SHOWCASE) {
			List<Rect> items = showcaseItems(frame);
			for (int i = 0; i < items.size(); i++) {
				Rect rect = items.get(i);
				gfx.fill(rect.x() - 1, rect.y() - 1, rect.x() + rect.w() + 1, rect.y() + rect.h() + 1, Theme.ACCENT_DIM);
				chip(gfx, rect, SHOWCASE.get(i)[0] + " " + SHOWCASE.get(i)[1], Theme.TEXT, rect.contains(mouseX, mouseY), false);
			}
		}
		if (PrivateMessages.collapsed()) {
			if (frame.contains(mouseX, mouseY)) {
				tooltip.add(Component.literal("點擊展開私訊視窗（可拖曳移動）"));
			}
		} else if (tooltip.isEmpty()) {
			for (Tab tab : tabs(frame, true)) {
				if (tab.rect().contains(mouseX, mouseY)) {
					tooltip.add(Component.literal(tab.conversation().label()));
				}
			}
			if (headerButton(frame, 0).contains(mouseX, mouseY)) {
				tooltip.add(Component.literal("縮小私訊視窗"));
			} else if (headerButton(frame, 1).contains(mouseX, mouseY)) {
				tooltip.add(Component.literal(PrivateMessages.hideInChat() ? "私訊目前只顯示在這個視窗，點擊改為也顯示在聊天欄" : "私訊目前也會顯示在聊天欄，點擊改為只顯示在這個視窗"));
			} else if (PrivateMessages.selected() != null && headerButton(frame, 2).contains(mouseX, mouseY)) {
				tooltip.add(Component.literal("關閉這個對話"));
			} else if (expandedInteractive(true) && grip(frame).contains(mouseX, mouseY)) {
				tooltip.add(Component.literal("拖曳以調整視窗大小"));
			} else if (mouseY >= frame.y() && mouseY < frame.y() + HEADER && frame.contains(mouseX, mouseY)) {
				tooltip.add(Component.literal("拖曳標題列可以移動視窗"));
			}
		}
		if (!tooltip.isEmpty()) {
			gfx.tooltip(font(), tooltip, mouseX, mouseY);
		}
	}

	private static void drawMessages(Gfx gfx, Rect frame, PrivateMessages.Conversation selected, boolean interactive) {
		Rect body = body(frame);
		if (selected == null) {
			return;
		}
		List<Line> lines = new ArrayList<>();
		String me = Minecraft.getInstance().getUser().getName();
		for (PrivateMessages.Message message : selected.messages) {
			String name = message.mine() ? "我" : selected.display;
			Component who = Component.literal((message.server().isEmpty() ? name : message.server() + "｜" + name) + "：")
					.withStyle(Style.EMPTY.withColor(TextColor.fromRgb((message.mine() ? Theme.ACCENT : Theme.WARNING) & 0xFFFFFF)));
			List<FormattedCharSequence> wrapped = font().split(who.copy().append(Component.literal(message.text()).withStyle(ChatFormatting.WHITE)), body.w() - FACE - 3);
			for (int i = 0; i < wrapped.size(); i++) {
				lines.add(new Line(wrapped.get(i), i == 0 ? (message.mine() ? me : selected.name) : null));
			}
		}
		int visible = body.h() / LINE;
		int maxScroll = Math.max(0, lines.size() - visible);
		scroll = Math.clamp(scroll, 0, maxScroll);
		int offset = interactive ? (int) scroll : 0;
		int start = Math.max(0, lines.size() - visible - offset);
		int end = Math.min(lines.size(), start + visible);
		int y = body.y() + body.h() - (end - start) * LINE;
		for (int i = start; i < end; i++) {
			Line line = lines.get(i);
			if (line.face() != null) {
				face(gfx, line.face(), body.x(), y);
			}
			gfx.text(font(), line.text(), body.x() + FACE + 3, y, 0xFFFFFFFF);
			y += LINE;
		}
		if (interactive && maxScroll > 0) {
			int barHeight = Math.max(8, body.h() * visible / lines.size());
			int barY = body.y() + (int) ((body.h() - barHeight) * (1 - scroll / maxScroll));
			gfx.fill(frame.x() + frame.w() - 2, barY, frame.x() + frame.w(), barY + barHeight, Theme.CHIP_HOVER);
		}
	}

	public static boolean mouseClicked(ChatScreen screen, MouseButtonEvent event, boolean doubleClick) {
		if (!visible() || event.button() != InputConstants.MOUSE_BUTTON_LEFT) {
			return false;
		}
		double mouseX = event.x();
		double mouseY = event.y();
		Rect frame = frame(screen.width, screen.height, true);
		if (popup == Popup.EMOJI && emojiPanel(frame).contains(mouseX, mouseY)) {
			Rect panel = emojiPanel(frame);
			EmojiPicker.Emoji emoji = EMOJI_PANEL.click(screen, panel.x(), panel.y(), panel.w(), panel.h(), mouseX, mouseY);
			if (emoji != null && input != null) {
				input.insertText(emoji.insert());
				screen.setFocused(input);
			}
			return true;
		}
		if (popup == Popup.SHOWCASE) {
			List<Rect> items = showcaseItems(frame);
			for (int i = 0; i < items.size(); i++) {
				if (items.get(i).contains(mouseX, mouseY) && input != null) {
					input.insertText(SHOWCASE.get(i)[1]);
					screen.setFocused(input);
					popup = Popup.NONE;
					return true;
				}
			}
		}
		if (!frame.contains(mouseX, mouseY)) {
			popup = Popup.NONE;
			return false;
		}
		if (PrivateMessages.collapsed()) {
			startDrag(frame, mouseX, mouseY);
			return true;
		}
		PrivateMessages.Conversation selected = PrivateMessages.selected();
		if (expandedInteractive(true) && grip(frame).contains(mouseX, mouseY)) {
			resizing = true;
			resizeX = mouseX;
			resizeY = mouseY;
			resizeWidth = frame.w();
			resizeBody = bodyHeight();
			popup = Popup.NONE;
			return true;
		}
		if (headerButton(frame, 0).contains(mouseX, mouseY)) {
			PrivateMessages.setCollapsed(true);
			return true;
		}
		if (headerButton(frame, 1).contains(mouseX, mouseY)) {
			PrivateMessages.setHideInChat(!PrivateMessages.hideInChat());
			return true;
		}
		if (selected != null && headerButton(frame, 2).contains(mouseX, mouseY)) {
			PrivateMessages.close(selected);
			return true;
		}
		if (TAB_SCROLL.overflow() && tabBar(frame, true).contains(mouseX, mouseY)) {
			Rect bar = tabBar(frame, true);
			TAB_SCROLL.press(mouseX, bar.x(), bar.w());
			return true;
		}
		for (Tab tab : tabs(frame, true)) {
			if (tab.rect().contains(mouseX, mouseY) && tabView(frame, true).contains(mouseX, mouseY)) {
				pendingTab = tab.conversation();
				pressX = mouseX;
				pressY = mouseY;
				startDrag(frame, mouseX, mouseY);
				return true;
			}
		}
		if (expandedInteractive(true)) {
			if (input != null && inputRect(frame).contains(mouseX, mouseY)) {
				screen.setFocused(input);
				input.mouseClicked(event, doubleClick);
				popup = Popup.NONE;
				return true;
			}
			if (footerChip(frame, 0).contains(mouseX, mouseY)) {
				send();
				return true;
			}
			if (footerChip(frame, 1).contains(mouseX, mouseY)) {
				popup = popup == Popup.EMOJI ? Popup.NONE : Popup.EMOJI;
				if (popup == Popup.EMOJI) {
					EMOJI_PANEL.open(screen);
				}
				return true;
			}
			if (footerChip(frame, 2).contains(mouseX, mouseY)) {
				popup = popup == Popup.SHOWCASE ? Popup.NONE : Popup.SHOWCASE;
				return true;
			}
		}
		popup = Popup.NONE;
		if (mouseY < frame.y() + HEADER) {
			startDrag(frame, mouseX, mouseY);
		}
		return true;
	}

	private static void startDrag(Rect frame, double mouseX, double mouseY) {
		dragging = true;
		dragX = mouseX - frame.x();
		dragY = mouseY - frame.y();
	}

	public static boolean mouseDragged(ChatScreen screen, double mouseX, double mouseY) {
		Rect bar = tabBar(frame(screen.width, screen.height, true), true);
		if (TAB_SCROLL.drag(mouseX, bar.x(), bar.w())) {
			return true;
		}
		if (resizing) {
			int width = Math.clamp(resizeWidth + (int) (mouseX - resizeX), MIN_WIDTH, Math.max(MIN_WIDTH, screen.width - 8));
			int body = Math.clamp(resizeBody + (int) (mouseY - resizeY), MIN_BODY, Math.max(MIN_BODY, screen.height - HEADER - FOOTER - 8));
			PrivateMessages.resizeWindow(width, body);
			return true;
		}
		if (!dragging) {
			return false;
		}
		if (pendingTab != null) {
			if (Math.abs(mouseX - pressX) < 3 && Math.abs(mouseY - pressY) < 3) {
				return true;
			}
			pendingTab = null;
		}
		PrivateMessages.moveWindow((int) (mouseX - dragX), (int) (mouseY - dragY));
		return true;
	}

	public static boolean mouseReleased(ChatScreen screen, double mouseX, double mouseY) {
		if (TAB_SCROLL.release()) {
			return true;
		}
		if (resizing) {
			resizing = false;
			PrivateMessages.saveSettings();
			return true;
		}
		if (!dragging) {
			return false;
		}
		dragging = false;
		if (pendingTab != null) {
			PrivateMessages.select(pendingTab.name);
			pendingTab = null;
			scroll = 0;
			return true;
		}
		Rect frame = frame(screen.width, screen.height, true);
		PrivateMessages.moveWindow(frame.x(), frame.y());
		if (PrivateMessages.collapsed() && Math.abs(mouseX - frame.x() - dragX) < 2 && Math.abs(mouseY - frame.y() - dragY) < 2) {
			PrivateMessages.setCollapsed(false);
		} else {
			PrivateMessages.saveSettings();
		}
		return true;
	}

	public static boolean mouseScrolled(ChatScreen screen, double mouseX, double mouseY, double amount) {
		if (!visible() || PrivateMessages.collapsed()) {
			return false;
		}
		Rect frame = frame(screen.width, screen.height, true);
		if (popup == Popup.EMOJI && emojiPanel(frame).contains(mouseX, mouseY)) {
			EMOJI_PANEL.scroll(amount);
			return true;
		}
		if (!frame.contains(mouseX, mouseY)) {
			return false;
		}
		if (mouseY < frame.y() + HEADER) {
			TAB_SCROLL.scrollBy((int) Math.round(-amount * 24));
			return true;
		}
		scroll += Math.signum(amount) * 2;
		return true;
	}
}
