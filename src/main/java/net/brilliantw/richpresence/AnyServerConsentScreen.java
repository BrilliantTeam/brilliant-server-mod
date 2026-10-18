package net.brilliantw.richpresence;

import java.util.ArrayList;
import java.util.List;
//? if >=26.1 {
import net.minecraft.client.gui.GuiGraphicsExtractor;
//?} else {
/*import net.minecraft.client.gui.GuiGraphics;
*///?}
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

public final class AnyServerConsentScreen extends Screen {
	private record Rect(int x, int y, int w, int h) {
		boolean contains(double mx, double my) {
			return mx >= x && mx < x + w && my >= y && my < y + h;
		}
	}

	static final String PHRASE = "我了解風險並自行負責";
	static final long READ_MS = 10_000;
	static final String[] TERMS = {
			"輝煌模組是為輝煌伺服器（brilliantw.net）設計的用戶端模組。開啟此設定後，聊天選單、自動腳本、腳本懸浮視窗、格子編號與記分板位置等功能，將在所有伺服器與單人世界中運作。",
			"其他伺服器可能禁止使用自動腳本、巨集，或任何會代替玩家操作的用戶端模組。在這些伺服器上使用，可能被視為作弊，導致帳號遭到警告、停權或永久封鎖。開啟前請自行確認該伺服器的規則。",
			"自動腳本雖然遵守原版生存模式的操作與資訊限制，但其他伺服器的反作弊系統仍可能將其判定為違規。本模組無法保證不被偵測，也無法保證不受處罰。",
			"在輝煌以外的伺服器或單人世界使用本模組所造成的一切後果，包括但不限於帳號處罰、物品或存檔損失、與他人的糾紛，均由使用者自行承擔。輝煌伺服器、模組作者及團隊成員不負任何責任，也不提供相關客服或申訴協助。",
			"本模組依「現狀」提供，不保證在其他伺服器上的功能正確、相容或穩定。",
			"你可以隨時在「選單 → 模組設定」關閉此設定。開啟期間，每次進入非輝煌伺服器時都會顯示提醒。",
			"開啟即表示你已達可自行承擔法律責任的年齡，或已取得法定代理人同意，並同意以上所有條款。同意時間與模組版本會記錄在本機設定檔中。"
	};

	private static final int HEADER = 26;
	private static final int LINE = 10;

	private final Screen parent;
	private final long openedAt = System.currentTimeMillis();
	private final List<Runnable> clicks = new ArrayList<>();
	private final List<Rect> clickRects = new ArrayList<>();
	private EditBox phraseBox;
	private String phrase = "";
	private int step = 1;
	private boolean read;
	private boolean reachedEnd;
	private double scroll;

	public AnyServerConsentScreen(Screen parent) {
		super(Component.literal("危險設定：不限輝煌伺服器"));
		this.parent = parent;
	}

	static boolean canContinue(boolean read, boolean reachedEnd, long elapsed) {
		return read && reachedEnd && elapsed >= READ_MS;
	}

	static boolean phraseMatches(String text) {
		return PHRASE.equals(text.strip());
	}

	private Rect panel() {
		int w = Math.min(width - 16, 420);
		int h = Math.min(height - 16, 300);
		return new Rect((width - w) / 2, (height - h) / 2, w, h);
	}

	@Override
	protected void init() {
		Rect p = panel();
		phraseBox = new EditBox(font, p.x() + 16, p.y() + HEADER + 96, p.w() - 32, 18, Component.literal("確認文字"));
		phraseBox.setMaxLength(30);
		phraseBox.setValue(phrase);
		phraseBox.setResponder(value -> phrase = value);
		Theme.hint(phraseBox, "請手動輸入上方文字");
		phraseBox.visible = step == 2;
		addRenderableWidget(phraseBox);
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

	private void button(Gfx gfx, Rect r, String label, boolean enabled, boolean danger, int mouseX, int mouseY, Runnable action) {
		boolean hover = enabled && r.contains(mouseX, mouseY);
		int color = !enabled ? Theme.CHIP : danger ? (hover ? 0xFFFF9A9A : Theme.DANGER) : hover ? Theme.CHIP_HOVER : Theme.CHIP;
		gfx.fill(r.x(), r.y(), r.x() + r.w(), r.y() + r.h(), color);
		gfx.centered(font, label, r.x(), r.y(), r.w(), r.h(), !enabled ? Theme.TEXT_DIM : danger ? 0xFF1A1A1A : Theme.TEXT);
		if (enabled) {
			clickRects.add(r);
			clicks.add(action);
		}
	}

	private void draw(Gfx gfx, int mouseX, int mouseY) {
		clicks.clear();
		clickRects.clear();
		gfx.fill(0, 0, width, height, 0xA0000000);
		Rect p = panel();
		gfx.fill(p.x(), p.y(), p.x() + p.w(), p.y() + p.h(), Theme.PANEL);
		gfx.fill(p.x(), p.y(), p.x() + p.w(), p.y() + HEADER, Theme.HEADER);
		gfx.fill(p.x(), p.y() + HEADER - 1, p.x() + p.w(), p.y() + HEADER, Theme.DANGER);
		gfx.text(font, "⚠ " + title.getString(), p.x() + 10, p.y() + 9, Theme.DANGER);
		String stepLabel = step + " / 2";
		gfx.text(font, stepLabel, p.x() + p.w() - font.width(stepLabel) - 10, p.y() + 9, Theme.TEXT_DIM);
		int buttonY = p.y() + p.h() - 26;
		Rect cancel = new Rect(p.x() + 10, buttonY, 80, 18);
		if (step == 1) {
			drawTerms(gfx, p, mouseX, mouseY);
			long left = Math.max(0, READ_MS - (System.currentTimeMillis() - openedAt));
			boolean ready = canContinue(read, reachedEnd, System.currentTimeMillis() - openedAt);
			String next = left > 0 ? "下一步（" + (left + 999) / 1000 + "）" : !reachedEnd ? "請讀到最後" : "下一步";
			button(gfx, cancel, "取消", true, false, mouseX, mouseY, this::onClose);
			button(gfx, new Rect(p.x() + p.w() - 110, buttonY, 100, 18), next, ready, true, mouseX, mouseY, () -> {
				step = 2;
				phraseBox.visible = true;
				setFocused(phraseBox);
			});
		} else {
			int y = p.y() + HEADER + 12;
			for (FormattedCharSequence line : font.split(Component.literal("最後確認：開啟後，你需自行承擔在其他伺服器使用本模組的所有後果，輝煌伺服器與模組作者不負任何責任。"), p.w() - 32)) {
				gfx.text(font, line, p.x() + 16, y, Theme.TEXT);
				y += LINE;
			}
			y += 10;
			gfx.text(font, "請在下方輸入以下文字以開啟：", p.x() + 16, y, Theme.TEXT_SECONDARY);
			gfx.text(font, "「" + PHRASE + "」", p.x() + 16, y + 14, Theme.DANGER);
			phraseBox.setY(p.y() + HEADER + 96);
			button(gfx, cancel, "返回", true, false, mouseX, mouseY, () -> {
				step = 1;
				phraseBox.visible = false;
				setFocused(null);
			});
			button(gfx, new Rect(p.x() + p.w() - 130, buttonY, 120, 18), "開啟危險設定", phraseMatches(phrase), true, mouseX, mouseY, () -> {
				ModSettings.acceptAnyServer();
				CommandMenuScreen.setScreen(parent);
			});
		}
	}

	private void drawTerms(Gfx gfx, Rect p, int mouseX, int mouseY) {
		Rect area = new Rect(p.x() + 12, p.y() + HEADER + 8, p.w() - 24, p.h() - HEADER - 66);
		List<FormattedCharSequence> lines = new ArrayList<>();
		List<Integer> numbers = new ArrayList<>();
		for (int i = 0; i < TERMS.length; i++) {
			List<FormattedCharSequence> split = font.split(Component.literal(TERMS[i]), area.w() - 22);
			for (int j = 0; j < split.size(); j++) {
				lines.add(split.get(j));
				numbers.add(j == 0 ? i + 1 : 0);
			}
			lines.add(null);
			numbers.add(0);
		}
		int total = lines.size() * LINE;
		scroll = Math.clamp(scroll, 0, Math.max(0, total - area.h()));
		if (scroll >= total - area.h() - 2) {
			reachedEnd = true;
		}
		gfx.fill(area.x(), area.y(), area.x() + area.w(), area.y() + area.h(), Theme.SIDEBAR);
		gfx.scissor(area.x(), area.y(), area.x() + area.w(), area.y() + area.h());
		for (int i = 0; i < lines.size(); i++) {
			int y = area.y() + 4 + i * LINE - (int) scroll;
			if (numbers.get(i) > 0) {
				gfx.text(font, numbers.get(i) + ".", area.x() + 4, y, Theme.DANGER);
			}
			if (lines.get(i) != null) {
				gfx.text(font, lines.get(i), area.x() + 18, y, Theme.TEXT);
			}
		}
		gfx.endScissor();
		if (total > area.h()) {
			int bar = Math.max(16, area.h() * area.h() / total);
			int barY = area.y() + (int) ((area.h() - bar) * (scroll / (total - area.h())));
			gfx.fill(area.x() + area.w() - 3, barY, area.x() + area.w() - 1, barY + bar, Theme.CHIP_HOVER);
		}
		int y = area.y() + area.h() + 8;
		Rect check = new Rect(p.x() + 12, y, 14 + font.width("我已完整閱讀並了解以上免責聲明"), 10);
		gfx.fill(check.x(), y, check.x() + 10, y + 10, read ? Theme.DANGER : Theme.CHIP);
		if (read) {
			gfx.centered(font, "✓", check.x(), y, 10, 10, 0xFF1A1A1A);
		}
		gfx.text(font, "我已完整閱讀並了解以上免責聲明", check.x() + 14, y + 1, check.contains(mouseX, mouseY) ? Theme.TEXT : Theme.TEXT_SECONDARY);
		clickRects.add(check);
		clicks.add(() -> read = !read);
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (super.mouseClicked(event, doubleClick)) {
			return true;
		}
		for (int i = 0; i < clickRects.size(); i++) {
			if (clickRects.get(i).contains(event.x(), event.y())) {
				clicks.get(i).run();
				return true;
			}
		}
		return false;
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		scroll -= scrollY * 20;
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
