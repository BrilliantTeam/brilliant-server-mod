package net.brilliantw.richpresence;

import com.mojang.blaze3d.platform.InputConstants;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.components.EditBox;
//? if >=26.1 {
import net.minecraft.client.gui.GuiGraphicsExtractor;
//?} else {
/*import net.minecraft.client.gui.GuiGraphics;
*///?}
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

public final class ChatHistoryScreen extends Screen {
	private record Message(long time, Component text, String plain) {}

	private record Row(Message message, int y, int height, List<FormattedCharSequence> lines) {}

	private record Rect(int x, int y, int w, int h) {
		boolean contains(double mouseX, double mouseY) {
			return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
		}
	}

	private static final int MAX = 1000;
	private static final int HEADER = 26;
	private static final int FOOTER = 16;
	private static final int LINE = 10;
	private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault());
	private static final Deque<Message> HISTORY = new ArrayDeque<>();
	private static int version;

	private final Screen parent;
	private EditBox search;
	private String query = "";
	private List<Row> rows = List.of();
	private int layoutKey = Integer.MIN_VALUE;
	private double scroll;
	private boolean draggingBar;
	private String status = "";
	private long statusUntil;

	public ChatHistoryScreen(Screen parent) {
		super(Component.literal("聊天紀錄"));
		this.parent = parent;
	}

	public static void record(Component message) {
		String plain = ChatTabs.plain(message);
		if (plain.isBlank()) {
			return;
		}
		HISTORY.addFirst(new Message(System.currentTimeMillis(), message.copy(), plain));
		while (HISTORY.size() > MAX) {
			HISTORY.removeLast();
		}
		version++;
	}

	@Override
	protected void init() {
		Rect p = panel();
		int x = Theme.titleX(font, p.x()) + font.width(title) + 10;
		search = new EditBox(font, x, p.y() + 5, Math.max(80, Math.min(240, p.x() + p.w() - 10 - x)), 16, Component.literal("搜尋"));
		search.setMaxLength(100);
		Theme.hint(search, "搜尋訊息…");
		search.setValue(query);
		search.setResponder(value -> {
			query = value;
			scroll = 0;
		});
		addRenderableWidget(search);
		setInitialFocus(search);
	}

	private Rect panel() {
		int w = Math.min(width - 16, 560);
		return new Rect((width - w) / 2, 8, w, height - 16);
	}

	private Rect list() {
		Rect p = panel();
		return new Rect(p.x() + 8, p.y() + HEADER + 6, p.w() - 16, p.h() - HEADER - FOOTER - 10);
	}

	private int timeWidth() {
		return font.width("00:00:00") + 6;
	}

	private void layout() {
		Rect l = list();
		int key = (l.w() * 31 + version) * 31 + query.hashCode();
		if (key == layoutKey) {
			return;
		}
		layoutKey = key;
		String needle = query.trim().toLowerCase(Locale.ROOT);
		int textWidth = Math.max(40, l.w() - 4 - 8 - timeWidth());
		List<Row> result = new ArrayList<>();
		int y = 0;
		for (Message message : HISTORY) {
			if (!needle.isEmpty() && !message.plain().toLowerCase(Locale.ROOT).contains(needle)) {
				continue;
			}
			List<FormattedCharSequence> lines = font.split(message.text(), textWidth);
			int height = Math.max(1, lines.size()) * LINE + 4;
			result.add(new Row(message, y, height, lines));
			y += height + 2;
		}
		rows = result;
	}

	private int contentHeight() {
		return rows.isEmpty() ? 0 : rows.getLast().y() + rows.getLast().height();
	}

	//? if >=26.1 {
	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		draw(new Gfx(graphics), mouseX, mouseY);
		super.extractRenderState(graphics, mouseX, mouseY, partialTick);
	}
	//?} else {
	/*@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		draw(new Gfx(graphics), mouseX, mouseY);
		super.render(graphics, mouseX, mouseY, partialTick);
	}
	*///?}

	private void draw(Gfx gfx, int mouseX, int mouseY) {
		layout();
		Rect p = panel();
		Rect l = list();
		gfx.fill(p.x(), p.y(), p.x() + p.w(), p.y() + p.h(), Theme.PANEL);
		Theme.watermark(gfx, p.x(), p.y(), p.w(), p.h());
		gfx.fill(p.x(), p.y(), p.x() + p.w(), p.y() + HEADER, Theme.HEADER);
		gfx.fill(p.x(), p.y() + HEADER - 1, p.x() + p.w(), p.y() + HEADER, Theme.ACCENT_DIM);
		gfx.text(font, title.getString(), Theme.titleX(font, p.x()), p.y() + 9, Theme.ACCENT);
		Theme.drawBack(gfx, font, p.x(), p.y(), mouseX, mouseY);

		scroll = Math.clamp(scroll, 0, Math.max(0, contentHeight() - l.h()));
		gfx.scissor(l.x(), l.y(), l.x() + l.w(), l.y() + l.h());
		int top = l.y() - (int) scroll;
		for (Row row : rows) {
			int ry = top + row.y();
			if (ry + row.height() < l.y() || ry > l.y() + l.h()) {
				continue;
			}
			boolean hovered = l.contains(mouseX, mouseY) && mouseX < l.x() + l.w() - 6 && mouseY >= ry && mouseY < ry + row.height();
			gfx.fill(l.x(), ry, l.x() + l.w() - 4, ry + row.height(), hovered ? Theme.CARD_HOVER : Theme.CARD);
			gfx.text(font, TIME.format(Instant.ofEpochMilli(row.message().time())), l.x() + 4, ry + 3, Theme.TEXT_DIM);
			for (int i = 0; i < row.lines().size(); i++) {
				gfx.text(font, row.lines().get(i), l.x() + 4 + timeWidth(), ry + 3 + i * LINE, Theme.TEXT);
			}
		}
		if (rows.isEmpty()) {
			String empty = HISTORY.isEmpty() ? "還沒有聊天紀錄" : "找不到符合的訊息";
			gfx.text(font, empty, l.x() + (l.w() - font.width(empty)) / 2, l.y() + l.h() / 3, Theme.TEXT_DIM);
		}
		gfx.endScissor();

		int content = contentHeight();
		if (content > l.h()) {
			int barHeight = Math.max(16, l.h() * l.h() / content);
			int barY = l.y() + (int) ((l.h() - barHeight) * (scroll / (content - l.h())));
			gfx.fill(l.x() + l.w() - 2, barY, l.x() + l.w(), barY + barHeight, Theme.CHIP_HOVER);
		}

		gfx.fill(p.x(), p.y() + p.h() - FOOTER, p.x() + p.w(), p.y() + p.h() - FOOTER + 1, Theme.HEADER);
		boolean showStatus = System.currentTimeMillis() < statusUntil;
		String footer = showStatus ? status : "點擊訊息即可複製 · 最多保留 " + MAX + " 則，關閉遊戲後清除 · Esc 返回";
		gfx.text(font, font.plainSubstrByWidth(footer, p.w() - 16), p.x() + 8, p.y() + p.h() - FOOTER + 4, showStatus ? Theme.SUCCESS : Theme.TEXT_DIM);
	}

	private void dragScrollBar(double mouseY) {
		Rect l = list();
		int content = contentHeight();
		int barHeight = Math.max(16, l.h() * l.h() / Math.max(1, content));
		double fraction = (mouseY - l.y() - barHeight / 2.0) / Math.max(1, l.h() - barHeight);
		scroll = Math.clamp(fraction, 0, 1) * Math.max(0, content - l.h());
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (super.mouseClicked(event, doubleClick)) {
			return true;
		}
		if (event.button() != InputConstants.MOUSE_BUTTON_LEFT) {
			return false;
		}
		double mouseX = event.x();
		double mouseY = event.y();
		setFocused(null);
		if (!panel().contains(mouseX, mouseY)) {
			onClose();
			return true;
		}
		if (Theme.backHit(font, panel().x(), panel().y(), mouseX, mouseY)) {
			onClose();
			return true;
		}
		Rect l = list();
		if (!l.contains(mouseX, mouseY)) {
			return true;
		}
		if (mouseX >= l.x() + l.w() - 6 && contentHeight() > l.h()) {
			draggingBar = true;
			dragScrollBar(mouseY);
			return true;
		}
		int top = l.y() - (int) scroll;
		for (Row row : rows) {
			int ry = top + row.y();
			if (mouseY >= ry && mouseY < ry + row.height()) {
				minecraft.keyboardHandler.setClipboard(row.message().plain());
				status = "已複製：" + row.message().plain();
				statusUntil = System.currentTimeMillis() + 3000;
				return true;
			}
		}
		return true;
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
		if (draggingBar) {
			dragScrollBar(event.y());
			return true;
		}
		return super.mouseDragged(event, dragX, dragY);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		draggingBar = false;
		return super.mouseReleased(event);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		scroll -= scrollY * LINE * 3;
		return true;
	}

	@Override
	public void onClose() {
		CommandMenuScreen.setScreen(parent);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
