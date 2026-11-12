package net.brilliantw.richpresence;

import com.mojang.blaze3d.platform.InputConstants;
import java.net.URI;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.ChatFormatting;
//? if >=26.1 {
import net.minecraft.client.gui.GuiGraphicsExtractor;
//?} else {
/*import net.minecraft.client.gui.GuiGraphics;
*///?}
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

public final class AnnouncementScreen extends Screen {
	private record Rect(int x, int y, int w, int h) {
		boolean contains(double mouseX, double mouseY) {
			return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
		}
	}

	private record Card(AnnouncementStore.Message message, int y, int height, RichText.Layout content) {}

	private static final int HEADER = Theme.HEADER_H;
	private static final int FOOTER = Theme.FOOTER_H;
	private static final int PAD = 7;
	private static final int CARD_GAP = 6;
	private static final int LINE = RichText.LINE;
	private static final int CHIP_HEIGHT = 14;
	private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm").withZone(ZoneId.systemDefault());

	private final Screen parent;
	private final Map<String, String> seenAtOpen = new HashMap<>();
	private final Set<String> revealed = new HashSet<>();
	private String board;
	private double scroll;
	private boolean draggingBar;
	private List<Card> cards = List.of();
	private int layoutWidth = -1;
	private int layoutVersion = -1;
	private int layoutReveals = -1;
	private int layoutImages = -1;
	private String layoutBoard;

	public AnnouncementScreen(Screen parent) {
		super(Component.literal("公告"));
		this.parent = parent;
		if (AnnouncementStore.BOARDS.isEmpty()) {
			AnnouncementStore.loadBoards(this::selectDefaultBoard);
		} else {
			selectDefaultBoard();
		}
	}

	private void selectDefaultBoard() {
		if (AnnouncementStore.BOARDS.isEmpty()) {
			return;
		}
		String target = AnnouncementStore.BOARDS.stream()
				.filter(b -> AnnouncementStore.unread(b.key) > 0)
				.map(b -> b.key)
				.findFirst()
				.orElse(AnnouncementStore.BOARDS.getFirst().key);
		selectBoard(target);
	}

	private void selectBoard(String key) {
		if (board != null) {
			AnnouncementStore.markSeen(board);
		}
		board = key;
		scroll = 0;
		seenAtOpen.putIfAbsent(key, AnnouncementStore.seenId(key) == null ? "" : AnnouncementStore.seenId(key));
		if (AnnouncementStore.isStale(key)) {
			AnnouncementStore.refresh(key, () -> AnnouncementStore.markSeen(key));
		} else {
			AnnouncementStore.markSeen(key);
		}
	}

	private Rect panel() {
		int w = Math.min(width - 16, 520);
		return new Rect((width - w) / 2, 8, w, height - 16);
	}

	private Rect list() {
		Rect p = panel();
		return new Rect(p.x() + 8, p.y() + HEADER + 6, p.w() - 16, p.h() - HEADER - FOOTER - 10);
	}

	private List<Rect> tabs() {
		Rect p = panel();
		List<Rect> rects = new ArrayList<>();
		int x = Theme.titleX(font, p.x()) + font.width(title) + 12;
		for (AnnouncementStore.Board b : AnnouncementStore.BOARDS) {
			int w = font.width(tabLabel(b)) + 12;
			rects.add(new Rect(x, p.y() + 6, w, CHIP_HEIGHT));
			x += w + 3;
		}
		return rects;
	}

	private String tabLabel(AnnouncementStore.Board b) {
		int unread = AnnouncementStore.unread(b.key);
		return unread > 0 && !b.key.equals(board) ? b.label + " " + unread : b.label;
	}

	private Rect refreshChip() {
		Rect p = panel();
		int w = font.width("重新整理") + 12;
		return new Rect(p.x() + p.w() - 8 - w, p.y() + 6, w, CHIP_HEIGHT);
	}

	private Rect websiteChip() {
		Rect refresh = refreshChip();
		int w = font.width("網站") + 12;
		return new Rect(refresh.x() - 3 - w, refresh.y(), w, CHIP_HEIGHT);
	}

	private Rect retryChip() {
		Rect l = list();
		int w = font.width("重試") + 12;
		return new Rect(l.x() + (l.w() - w) / 2, contentBottom() + 14, w, CHIP_HEIGHT);
	}

	private int contentHeight() {
		return cards.isEmpty() ? 0 : cards.getLast().y() + cards.getLast().height();
	}

	private int contentBottom() {
		return list().y() + contentHeight() - (int) scroll + 4;
	}

	private void layout() {
		AnnouncementStore.BoardState state = board == null ? null : AnnouncementStore.state(board);
		int width = list().w() - 4 - PAD * 2;
		if (state == null) {
			cards = List.of();
			return;
		}
		if (width == layoutWidth && state.version == layoutVersion && revealed.size() == layoutReveals && board.equals(layoutBoard)
				&& RemoteImages.version() == layoutImages) {
			return;
		}
		layoutImages = RemoteImages.version();
		layoutWidth = width;
		layoutVersion = state.version;
		layoutReveals = revealed.size();
		layoutBoard = board;

		List<Card> result = new ArrayList<>();
		int y = 0;
		for (AnnouncementStore.Message message : state.messages) {
			RichText.Layout content = RichText.layout(font, message.lines, width, revealed.contains(message.id), PAD + LINE + 4);
			int height = content.height() + PAD - 2;
			result.add(new Card(message, y, height, content));
			y += height + CARD_GAP;
		}
		cards = result;
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

	private void drawChip(Gfx gfx, Rect chip, String label, int color, boolean active, int mouseX, int mouseY) {
		int background = active ? Theme.SELECTED : chip.contains(mouseX, mouseY) ? Theme.CHIP_HOVER : Theme.CHIP;
		gfx.round(chip.x(), chip.y(), chip.x() + chip.w(), chip.y() + chip.h(), background);
		if (active) {
			gfx.fill(chip.x(), chip.y() + chip.h() - 1, chip.x() + chip.w(), chip.y() + chip.h(), Theme.ACCENT);
		}
		gfx.label(font, label, chip.x() + 6, chip.y(), chip.h(), color);
	}

	private void draw(Gfx gfx, int mouseX, int mouseY) {
		if (board == null && !AnnouncementStore.BOARDS.isEmpty()) {
			selectDefaultBoard();
		}
		layout();
		Rect p = panel();
		Theme.panel(gfx, p.x(), p.y(), p.x() + p.w(), p.y() + p.h());
		Theme.watermark(gfx, p.x(), p.y(), p.w(), p.h());
		Theme.header(gfx, p.x(), p.y(), p.w());
		gfx.text(font, title.getString(), Theme.titleX(font, p.x()), p.y() + 9, Theme.ACCENT);
		Theme.drawBack(gfx, font, p.x(), p.y(), mouseX, mouseY);

		List<Component> tooltip = new ArrayList<>();
		List<Rect> tabs = tabs();
		for (int i = 0; i < tabs.size(); i++) {
			AnnouncementStore.Board b = AnnouncementStore.BOARDS.get(i);
			boolean unread = AnnouncementStore.unread(b.key) > 0 && !b.key.equals(board);
			drawChip(gfx, tabs.get(i), tabLabel(b), unread ? Theme.WARNING : Theme.TEXT, b.key.equals(board), mouseX, mouseY);
		}
		drawChip(gfx, refreshChip(), "重新整理", Theme.TEXT, false, mouseX, mouseY);
		drawChip(gfx, websiteChip(), "網站", Theme.TEXT, false, mouseX, mouseY);
		if (websiteChip().contains(mouseX, mouseY)) {
			tooltip.add(Component.literal("在瀏覽器開啟輝煌官網，可看圖片與影片"));
		}

		Rect l = list();
		AnnouncementStore.BoardState state = board == null ? null : AnnouncementStore.state(board);
		scroll = Math.clamp(scroll, 0, Math.max(0, contentHeight() + 30 - l.h()));
		if (state != null && state.more && !state.loading && state.error.isEmpty() && scroll >= contentHeight() - l.h() - 40) {
			AnnouncementStore.loadMore(board, () -> {});
		}

		gfx.scissor(l.x(), l.y(), l.x() + l.w(), l.y() + l.h());
		String seenBefore = board == null ? "" : seenAtOpen.getOrDefault(board, "");
		for (Card card : cards) {
			int cy = l.y() + card.y() - (int) scroll;
			if (cy + card.height() < l.y() || cy > l.y() + l.h()) {
				continue;
			}
			drawCard(gfx, card, l.x(), cy, l.w() - 4, seenBefore, mouseX, mouseY, l.contains(mouseX, mouseY), tooltip);
		}
		drawListStatus(gfx, state, l, mouseX, mouseY);
		gfx.endScissor();

		int content = contentHeight() + 30;
		Theme.scrollbar(gfx, l.x() + l.w() - Theme.SCROLLBAR_W, l.y(), l.h(), content, scroll, draggingBar || l.contains(mouseX, mouseY) && mouseX >= l.x() + l.w() - 6);

		Theme.footer(gfx, font, p.x(), p.y() + p.h(), p.w(), "資料來源：輝煌 Discord 公告頻道 · 滾輪捲動 · Esc 返回", Theme.TEXT_DIM);

		if (!tooltip.isEmpty()) {
			gfx.tooltip(font, tooltip, mouseX, mouseY);
		}
	}

	private void drawCard(Gfx gfx, Card card, int x, int y, int w, String seenBefore, int mouseX, int mouseY, boolean mouseInList, List<Component> tooltip) {
		AnnouncementStore.Message message = card.message();
		gfx.fill(x, y, x + w, y + card.height(), Theme.CARD);
		boolean isNew = !seenBefore.isEmpty() && AnnouncementStore.isNewer(message.id, seenBefore);
		if (isNew) {
			gfx.fill(x, y, x + 2, y + card.height(), Theme.ACCENT);
		}
		int hx = x + PAD;
		gfx.text(font, Component.literal(message.author).withStyle(ChatFormatting.BOLD), hx, y + PAD, Theme.TEXT);
		hx += font.width(Component.literal(message.author).withStyle(ChatFormatting.BOLD)) + 6;
		String time = TIME.format(Instant.ofEpochMilli(message.time)) + (message.edited ? "（已編輯）" : "");
		gfx.text(font, time, hx, y + PAD, Theme.TEXT_DIM);
		if (isNew) {
			String label = "NEW";
			int lw = font.width(label) + 8;
			int lx = x + w - PAD - lw;
			gfx.fill(lx, y + PAD - 2, lx + lw, y + PAD + 10, Theme.ACCENT_DIM);
			gfx.text(font, label, lx + 4, y + PAD, Theme.ACCENT);
		}

		RichText.draw(gfx, font, card.content(), x + PAD, y, w - PAD * 2, revealed.contains(message.id), mouseX, mouseY, mouseInList, tooltip);
	}

	private void drawListStatus(Gfx gfx, AnnouncementStore.BoardState state, Rect l, int mouseX, int mouseY) {
		int y = contentBottom() + 2;
		String text;
		int color = Theme.TEXT_DIM;
		boolean retry = false;
		if (board == null) {
			if (AnnouncementStore.boardsLoading()) {
				text = "載入中…";
			} else if (!AnnouncementStore.boardsError().isEmpty()) {
				text = AnnouncementStore.boardsError();
				color = Theme.DANGER;
				retry = true;
			} else {
				text = "目前沒有公告頻道";
			}
		} else if (state.loading) {
			text = state.messages.isEmpty() ? "載入公告中…" : "載入更早的公告…";
		} else if (!state.error.isEmpty()) {
			text = state.error;
			color = Theme.DANGER;
			retry = true;
		} else if (state.loaded && state.messages.isEmpty()) {
			text = "這個頻道目前沒有公告";
		} else if (state.loaded && !state.more) {
			text = "已經是最早的公告了";
		} else {
			return;
		}
		if (cards.isEmpty()) {
			y = l.y() + l.h() / 3;
		}
		gfx.text(font, text, l.x() + (l.w() - font.width(text)) / 2, y, color);
		if (retry) {
			Rect chip = cards.isEmpty() ? new Rect(retryChip().x(), y + 14, retryChip().w(), CHIP_HEIGHT) : retryChip();
			drawChip(gfx, chip, "重試", Theme.TEXT, false, mouseX, mouseY);
		}
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
		if (!panel().contains(mouseX, mouseY)) {
			onClose();
			return true;
		}
		if (Theme.backHit(font, panel().x(), panel().y(), mouseX, mouseY)) {
			Theme.click();
			onClose();
			return true;
		}
		List<Rect> tabs = tabs();
		for (int i = 0; i < tabs.size(); i++) {
			if (tabs.get(i).contains(mouseX, mouseY)) {
				Theme.click();
				selectBoard(AnnouncementStore.BOARDS.get(i).key);
				return true;
			}
		}
		if (refreshChip().contains(mouseX, mouseY)) {
			Theme.click();
			if (board == null) {
				AnnouncementStore.loadBoards(this::selectDefaultBoard);
			} else {
				String key = board;
				AnnouncementStore.refresh(key, () -> AnnouncementStore.markSeen(key));
			}
			return true;
		}
		if (websiteChip().contains(mouseX, mouseY)) {
			Theme.click();
			ConfirmLinkScreen.confirmLinkNow(this, URI.create(RPConfig.WEBSITE_URL));
			return true;
		}
		Rect l = list();
		if (!l.contains(mouseX, mouseY)) {
			return true;
		}
		if (onScrollBar(l, mouseX)) {
			draggingBar = true;
			dragScrollBar(mouseY);
			return true;
		}
		Rect retry = cards.isEmpty() ? new Rect(retryChip().x(), l.y() + l.h() / 3 + 14, retryChip().w(), CHIP_HEIGHT) : retryChip();
		if (retry.contains(mouseX, mouseY)) {
			Theme.click();
			if (board == null) {
				AnnouncementStore.loadBoards(this::selectDefaultBoard);
			} else if (AnnouncementStore.state(board).messages.isEmpty()) {
				String key = board;
				AnnouncementStore.refresh(key, () -> AnnouncementStore.markSeen(key));
			} else {
				AnnouncementStore.loadMore(board, () -> {});
			}
			return true;
		}
		for (Card card : cards) {
			int cy = l.y() + card.y() - (int) scroll;
			if (mouseY < cy || mouseY >= cy + card.height()) {
				continue;
			}
			RichText.Hit hit = RichText.hit(card.content(), l.x() + PAD, cy, mouseX, mouseY, revealed.contains(card.message().id));
			if (hit != null && hit.href() != null) {
				RichText.openLink(this, hit.href());
			} else if (hit != null && hit.spoiler()) {
				revealed.add(card.message().id);
			}
			return true;
		}
		return true;
	}

	private boolean onScrollBar(Rect l, double mouseX) {
		return mouseX >= l.x() + l.w() - 6 && contentHeight() + 30 > l.h();
	}

	private void dragScrollBar(double mouseY) {
		Rect l = list();
		int content = contentHeight() + 30;
		int barHeight = Math.max(16, l.h() * l.h() / content);
		double fraction = (mouseY - l.y() - barHeight / 2.0) / Math.max(1, l.h() - barHeight);
		scroll = Math.clamp(fraction, 0, 1) * (content - l.h());
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
		if (board != null) {
			AnnouncementStore.markSeen(board);
		}
		CommandMenuScreen.setScreen(parent);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
