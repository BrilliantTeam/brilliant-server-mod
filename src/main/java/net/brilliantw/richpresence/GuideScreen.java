package net.brilliantw.richpresence;

import com.mojang.blaze3d.platform.InputConstants;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.components.EditBox;
//? if >=26.1 {
import net.minecraft.client.gui.GuiGraphicsExtractor;
//?} else {
/*import net.minecraft.client.gui.GuiGraphics;
*///?}
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

public final class GuideScreen extends Screen {
	private record Rect(int x, int y, int w, int h) {
		boolean contains(double mouseX, double mouseY) {
			return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
		}
	}

	private record Entry(int y, int h, String slug, String title, String sub) {}

	private static final int HEADER = 26;
	private static final int FOOTER = 16;
	private static final int PAD = 7;
	private static final int CHIP_HEIGHT = 14;
	private static final int HEADING = 16;
	private static final int ITEM = 26;
	private static final int TITLE_SPACE = 22;

	private final Screen parent;
	private final Deque<String> history = new ArrayDeque<>();
	private EditBox search;
	private String typed = "";
	private long searchAt;
	private int searchToken;
	private String query = "";
	private List<GuideStore.Post> results;
	private boolean searching;
	private String listError = "";
	private boolean indexLoading;
	private String articleSlug;
	private GuideStore.Article article;
	private String articleError = "";
	private RichText.Layout articleLayout;
	private int layoutWidth = -1;
	private int layoutImages = -1;
	private double scroll;
	private double listScroll;
	private boolean draggingBar;
	private List<Entry> entries = List.of();

	public GuideScreen(Screen parent) {
		super(Component.literal("官網"));
		this.parent = parent;
		if (GuideStore.index() == null) {
			loadIndex();
		}
	}

	private void loadIndex() {
		indexLoading = true;
		listError = "";
		GuideStore.loadIndex(() -> indexLoading = false, error -> {
			indexLoading = false;
			listError = error;
		});
	}

	@Override
	protected void init() {
		Rect p = panel();
		int x = Theme.titleX(font, p.x()) + font.width(title) + 10;
		int w = Math.max(80, Math.min(220, browserChip().x() - 6 - x));
		search = new EditBox(font, x, p.y() + 5, w, 16, Component.literal("搜尋"));
		search.setMaxLength(60);
		Theme.hint(search, "搜尋官網文章…");
		search.setValue(typed);
		search.setResponder(value -> {
			typed = value;
			searchAt = System.currentTimeMillis() + 400;
		});
		addRenderableWidget(search);
		if (articleSlug == null) {
			setInitialFocus(search);
		}
	}

	private Rect panel() {
		int w = Math.min(width - 16, 560);
		return new Rect((width - w) / 2, 8, w, height - 16);
	}

	private Rect list() {
		Rect p = panel();
		return new Rect(p.x() + 8, p.y() + HEADER + 6, p.w() - 16, p.h() - HEADER - FOOTER - 10);
	}

	private Rect browserChip() {
		Rect p = panel();
		int w = font.width("瀏覽器開啟") + 12;
		return new Rect(p.x() + p.w() - 8 - w, p.y() + 6, w, CHIP_HEIGHT);
	}

	private Rect retryChip(int y) {
		Rect l = list();
		int w = font.width("重試") + 12;
		return new Rect(l.x() + (l.w() - w) / 2, y + 14, w, CHIP_HEIGHT);
	}

	private void runSearch() {
		searchAt = 0;
		String q = typed.trim();
		if (articleSlug != null) {
			closeArticle();
			history.clear();
		}
		if (q.equals(query) && (results != null || q.isEmpty())) {
			return;
		}
		query = q;
		scroll = 0;
		listError = "";
		int token = ++searchToken;
		if (q.isEmpty()) {
			results = null;
			searching = false;
			return;
		}
		searching = true;
		GuideStore.search(q, found -> {
			if (token == searchToken) {
				results = found;
				searching = false;
			}
		}, error -> {
			if (token == searchToken) {
				listError = error;
				searching = false;
			}
		});
	}

	private void openArticle(String slug, boolean push) {
		if (slug.equals(articleSlug)) {
			scroll = 0;
			return;
		}
		if (articleSlug == null) {
			listScroll = scroll;
		} else if (push) {
			history.push(articleSlug);
		}
		load(slug);
	}

	private void load(String slug) {
		articleSlug = slug;
		article = null;
		articleLayout = null;
		articleError = "";
		scroll = 0;
		setFocused(null);
		GuideStore.article(slug, loaded -> {
			if (slug.equals(articleSlug)) {
				article = loaded;
				layoutWidth = -1;
			}
		}, error -> {
			if (slug.equals(articleSlug)) {
				articleError = error;
			}
		});
	}

	private void closeArticle() {
		articleSlug = null;
		article = null;
		articleLayout = null;
		articleError = "";
		scroll = listScroll;
	}

	private void back() {
		if (!history.isEmpty()) {
			load(history.pop());
		} else {
			closeArticle();
		}
	}

	private List<Entry> buildEntries() {
		List<Entry> result = new ArrayList<>();
		int y = 0;
		if (!query.isEmpty()) {
			if (results == null) {
				return result;
			}
			result.add(new Entry(y, HEADING, null, "搜尋「" + query + "」：" + results.size() + " 筆結果", null));
			y += HEADING;
			for (GuideStore.Post post : results) {
				result.add(new Entry(y, ITEM, post.slug, post.title, post.snippet));
				y += ITEM + 3;
			}
			return result;
		}
		GuideStore.Index index = GuideStore.index();
		if (index == null) {
			return result;
		}
		for (GuideStore.Route route : index.routes) {
			result.add(new Entry(y, HEADING, null, route.title, null));
			y += HEADING;
			int n = 1;
			for (GuideStore.Step step : route.steps) {
				result.add(new Entry(y, ITEM, step.slug, n++ + ". " + step.title, step.action.isEmpty() ? null : step.action));
				y += ITEM + 3;
			}
			y += 4;
		}
		result.add(new Entry(y, HEADING, null, "全部文章（" + index.posts.size() + "）", null));
		y += HEADING;
		for (GuideStore.Post post : index.posts) {
			result.add(new Entry(y, ITEM, post.slug, post.title, post.excerpt));
			y += ITEM + 3;
		}
		return result;
	}

	private int contentHeight() {
		if (articleSlug != null) {
			return articleLayout == null ? 0 : articleLayout.height() + PAD;
		}
		return entries.isEmpty() ? 0 : entries.getLast().y() + entries.getLast().h();
	}

	private String oneLine(String text, int width) {
		String flat = text == null ? "" : text.replaceAll("\\s+", " ").trim();
		if (font.width(flat) <= width) {
			return flat;
		}
		return font.plainSubstrByWidth(flat, width - font.width("…")) + "…";
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

	private void drawChip(Gfx gfx, Rect chip, String label, int mouseX, int mouseY) {
		gfx.round(chip.x(), chip.y(), chip.x() + chip.w(), chip.y() + chip.h(), chip.contains(mouseX, mouseY) ? Theme.CHIP_HOVER : Theme.CHIP);
		gfx.label(font, label, chip.x() + 6, chip.y(), chip.h(), Theme.TEXT);
	}

	private void draw(Gfx gfx, int mouseX, int mouseY) {
		if (searchAt > 0 && System.currentTimeMillis() >= searchAt) {
			runSearch();
		}
		Rect p = panel();
		Rect l = list();
		Theme.panel(gfx, p.x(), p.y(), p.x() + p.w(), p.y() + p.h());
		Theme.watermark(gfx, p.x(), p.y(), p.w(), p.h());
		gfx.fill(p.x(), p.y(), p.x() + p.w(), p.y() + HEADER, Theme.HEADER);
		gfx.fill(p.x(), p.y() + HEADER - 1, p.x() + p.w(), p.y() + HEADER, Theme.ACCENT_DIM);
		gfx.text(font, title.getString(), Theme.titleX(font, p.x()), p.y() + 9, Theme.ACCENT);
		Theme.drawBack(gfx, font, p.x(), p.y(), mouseX, mouseY);
		drawChip(gfx, browserChip(), "瀏覽器開啟", mouseX, mouseY);

		List<Component> tooltip = new ArrayList<>();
		int innerWidth = l.w() - 4 - PAD * 2;
		if (articleSlug != null && article != null && (articleLayout == null || layoutWidth != innerWidth || layoutImages != RemoteImages.version())) {
			layoutWidth = innerWidth;
			layoutImages = RemoteImages.version();
			articleLayout = RichText.layout(font, article.lines, innerWidth, true, TITLE_SPACE);
		}
		if (articleSlug == null) {
			entries = buildEntries();
		}
		scroll = Math.clamp(scroll, 0, Math.max(0, contentHeight() + 20 - l.h()));

		gfx.scissor(l.x(), l.y(), l.x() + l.w(), l.y() + l.h());
		int top = l.y() - (int) scroll;
		String status = null;
		int statusColor = Theme.TEXT_DIM;
		boolean retry = false;
		if (articleSlug != null) {
			if (articleLayout != null) {
				gfx.text(font, Component.literal(article.title).withStyle(ChatFormatting.BOLD), l.x() + PAD, top + 6, Theme.ACCENT);
				RichText.draw(gfx, font, articleLayout, l.x() + PAD, top, innerWidth, true, mouseX, mouseY, l.contains(mouseX, mouseY), tooltip);
			} else if (!articleError.isEmpty()) {
				status = articleError;
				statusColor = Theme.DANGER;
				retry = true;
			} else {
				status = "載入文章中…";
			}
		} else {
			for (Entry entry : entries) {
				int ey = top + entry.y();
				if (ey + entry.h() < l.y() || ey > l.y() + l.h()) {
					continue;
				}
				if (entry.slug() == null) {
					gfx.text(font, Component.literal(entry.title()).withStyle(ChatFormatting.BOLD), l.x() + 2, ey + 4, Theme.ACCENT);
					continue;
				}
				boolean hovered = l.contains(mouseX, mouseY) && mouseY >= ey && mouseY < ey + entry.h() && mouseX < l.x() + l.w() - 4;
				gfx.fill(l.x(), ey, l.x() + l.w() - 4, ey + entry.h(), hovered ? Theme.CARD_HOVER : Theme.CARD);
				gfx.text(font, oneLine(entry.title(), innerWidth), l.x() + PAD, ey + 4, Theme.TEXT);
				if (entry.sub() != null) {
					gfx.text(font, oneLine(entry.sub(), innerWidth), l.x() + PAD, ey + 15, Theme.TEXT_SECONDARY);
				}
			}
			if (!listError.isEmpty()) {
				status = listError;
				statusColor = Theme.DANGER;
				retry = true;
			} else if (searching || (query.isEmpty() && indexLoading)) {
				status = searching ? "搜尋中…" : "載入中…";
			} else if (results != null && results.isEmpty()) {
				status = "找不到相關文章，換個關鍵字試試";
			}
		}
		if (status != null) {
			int sy = l.y() + l.h() / 3;
			gfx.text(font, status, l.x() + (l.w() - font.width(status)) / 2, sy, statusColor);
			if (retry) {
				drawChip(gfx, retryChip(sy), "重試", mouseX, mouseY);
			}
		}
		gfx.endScissor();

		int content = contentHeight() + 20;
		if (content > l.h()) {
			int barHeight = Math.max(16, l.h() * l.h() / content);
			int barY = l.y() + (int) ((l.h() - barHeight) * (scroll / (content - l.h())));
			gfx.fill(l.x() + l.w() - 2, barY, l.x() + l.w(), barY + barHeight, Theme.CHIP_HOVER);
		}

		gfx.fill(p.x(), p.y() + p.h() - FOOTER, p.x() + p.w(), p.y() + p.h() - FOOTER + 1, Theme.HEADER);
		gfx.text(font, "資料來源：輝煌官網 · 官網連結會在遊戲內開啟 · Esc 返回", p.x() + 8, p.y() + p.h() - FOOTER + 4, Theme.TEXT_DIM);

		if (!tooltip.isEmpty()) {
			gfx.tooltip(font, tooltip, mouseX, mouseY);
		}
	}

	private String browserUrl() {
		if (article != null) {
			return article.url;
		}
		if (articleSlug != null) {
			return RPConfig.WEBSITE_URL + "/" + URLEncoder.encode(articleSlug, StandardCharsets.UTF_8) + "/";
		}
		if (!query.isEmpty()) {
			return RPConfig.WEBSITE_URL + "/search?q=" + URLEncoder.encode(query, StandardCharsets.UTF_8);
		}
		return RPConfig.WEBSITE_URL;
	}

	private void openHref(String href) {
		String slug = GuideStore.slugOf(href);
		if (slug != null) {
			openArticle(slug, true);
		} else {
			RichText.openLink(this, href);
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
		setFocused(null);
		if (!panel().contains(mouseX, mouseY)) {
			CommandMenuScreen.setScreen(parent);
			return true;
		}
		if (Theme.backHit(font, panel().x(), panel().y(), mouseX, mouseY)) {
			onClose();
			return true;
		}
		if (browserChip().contains(mouseX, mouseY)) {
			ConfirmLinkScreen.confirmLinkNow(this, URI.create(browserUrl()));
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
		if (retryChip(l.y() + l.h() / 3).contains(mouseX, mouseY)) {
			if (articleSlug != null && !articleError.isEmpty()) {
				load(articleSlug);
			} else if (!listError.isEmpty()) {
				if (query.isEmpty()) {
					loadIndex();
				} else {
					query = "";
					runSearch();
				}
			}
			return true;
		}
		int top = l.y() - (int) scroll;
		if (articleSlug != null) {
			if (articleLayout != null) {
				RichText.Hit hit = RichText.hit(articleLayout, l.x() + PAD, top, mouseX, mouseY, true);
				if (hit != null && hit.href() != null) {
					openHref(hit.href());
				}
			}
			return true;
		}
		for (Entry entry : entries) {
			int ey = top + entry.y();
			if (entry.slug() != null && mouseY >= ey && mouseY < ey + entry.h()) {
				openArticle(entry.slug(), false);
				return true;
			}
		}
		return true;
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (getFocused() == search && event.isConfirmation()) {
			runSearch();
			return true;
		}
		return super.keyPressed(event);
	}

	private boolean onScrollBar(Rect l, double mouseX) {
		return mouseX >= l.x() + l.w() - 6 && contentHeight() + 20 > l.h();
	}

	private void dragScrollBar(double mouseY) {
		Rect l = list();
		int content = contentHeight() + 20;
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
		scroll -= scrollY * RichText.LINE * 3;
		return true;
	}

	@Override
	public void onClose() {
		if (articleSlug != null) {
			back();
		} else {
			CommandMenuScreen.setScreen(parent);
		}
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
