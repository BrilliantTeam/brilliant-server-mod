package net.brilliantw.richpresence;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
//? if >=26.1 {
import net.minecraft.client.gui.GuiGraphicsExtractor;
//?} else {
/*import net.minecraft.client.gui.GuiGraphics;
*///?}
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

public final class ModSettingsScreen extends Screen {
	private record Rect(int x, int y, int w, int h) {
		boolean contains(double mx, double my) {
			return mx >= x && mx < x + w && my >= y && my < y + h;
		}
	}

	private record Row(String section, String label, String detail, BooleanSupplier value, Consumer<Boolean> set) {}

	private record Hit(Rect rect, Runnable action) {}

	private static final int HEADER = 26;
	private static final int FOOTER = 14;
	private static final int ROW = 30;
	private static final int SECTION = 18;
	private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm").withZone(ZoneId.systemDefault());

	private final Screen parent;
	private final List<Row> rows = new ArrayList<>();
	private final List<Hit> hits = new ArrayList<>();
	private double scroll;
	private String status = "";
	private long statusUntil;

	public ModSettingsScreen(Screen parent) {
		super(Component.literal("模組設定"));
		this.parent = parent;
		ModSettings.Data s = ModSettings.get();
		rows.add(new Row("一般", "Discord 遊玩狀態", "在 Discord 個人檔案顯示所在分流與遊玩時間", () -> s.discordPresence, v -> s.discordPresence = v));
		rows.add(new Row("一般", "新公告通知", "進入伺服器時提示有幾則未讀公告", () -> s.announcements, v -> s.announcements = v));
		rows.add(new Row("一般", "自動檢查更新", "有新版本時通知，並可在遊戲內一鍵更新", () -> s.updateCheck, v -> s.updateCheck = v));
		rows.add(new Row("聊天", "頻道分頁", "聊天欄上方依頻道篩選訊息的分頁", () -> s.channelTabs, v -> s.channelTabs = v));
		rows.add(new Row("聊天", "私訊視窗", "用獨立視窗顯示與管理私訊對話", () -> s.privateWindow, v -> s.privateWindow = v));
		rows.add(new Row("聊天", "表情符號選單", "聊天欄右下角的表情符號按鈕", () -> s.emoji, v -> s.emoji = v));
		rows.add(new Row("聊天", "高清看圖", "點擊 Discord 圖片連結時在遊戲內開啟", () -> s.imageLinks, v -> s.imageLinks = v));
		rows.add(new Row("聊天", "指令別名", "使用自訂的指令別名與對應的 TAB 補全", () -> s.aliases, v -> s.aliases = v));
		rows.add(new Row("遊戲", "自動腳本", "執行自動腳本；關閉時會停止所有執行中的腳本", () -> Scripts.data().enabled, v -> {
			Scripts.data().enabled = v;
			if (!v) {
				ScriptEngine.stopAll();
			}
			Scripts.save();
		}));
		rows.add(new Row("遊戲", "腳本懸浮視窗", "遊玩時在畫面上顯示腳本執行狀態", () -> Scripts.data().monitor, v -> {
			Scripts.data().monitor = v;
			Scripts.save();
		}));
		rows.add(new Row("遊戲", "配方書修正", "讓配方書正確辨識伺服器的自訂材料", () -> s.recipeFix, v -> s.recipeFix = v));
		rows.add(new Row("遊戲", "格子編號", "按住 Alt 時在介面格子上顯示編號", () -> s.slotNumbers, v -> s.slotNumbers = v));
		rows.add(new Row("遊戲", "記分板位置", "套用自訂的記分板位置與縮放", () -> s.scoreboardLayout, v -> s.scoreboardLayout = v));
		rows.add(new Row("遊戲", "鐵砧完整花費", "超限附魔超過 40 級時顯示實際花費，取代「太貴了！」", () -> s.anvilCost, v -> s.anvilCost = v));
	}

	private Rect panel() {
		int w = Math.min(width - 16, 460);
		return new Rect((width - w) / 2, 8, w, height - 16);
	}

	private Rect content() {
		Rect p = panel();
		return new Rect(p.x() + 10, p.y() + HEADER + 6, p.w() - 20, p.h() - HEADER - FOOTER - 10);
	}

	private void setStatus(String text) {
		status = text;
		statusUntil = System.currentTimeMillis() + 3000;
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

	private void toggleSwitch(Gfx gfx, int x, int y, boolean on, int onColor) {
		gfx.fill(x, y, x + 26, y + 12, on ? onColor : Theme.CHIP);
		int knob = on ? x + 15 : x + 1;
		gfx.fill(knob, y + 1, knob + 10, y + 11, on ? 0xFFFFFFFF : Theme.TEXT_SECONDARY);
	}

	private void draw(Gfx gfx, int mouseX, int mouseY) {
		hits.clear();
		Rect p = panel();
		gfx.fill(p.x(), p.y(), p.x() + p.w(), p.y() + p.h(), Theme.PANEL);
		Theme.watermark(gfx, p.x(), p.y(), p.w(), p.h());
		gfx.fill(p.x(), p.y(), p.x() + p.w(), p.y() + HEADER, Theme.HEADER);
		gfx.fill(p.x(), p.y() + HEADER - 1, p.x() + p.w(), p.y() + HEADER, Theme.ACCENT);
		Theme.drawBack(gfx, font, p.x(), p.y(), mouseX, mouseY);
		gfx.text(font, title.getString(), Theme.titleX(font, p.x()), p.y() + 9, Theme.TEXT);
		String version = "v" + RPConfig.MOD_VERSION;
		gfx.text(font, version, p.x() + p.w() - font.width(version) - 8, p.y() + 9, Theme.TEXT_DIM);

		Rect c = content();
		int height = layout(null, c, 0, -1, -1, false);
		scroll = Math.clamp(scroll, 0, Math.max(0, height - c.h()));
		boolean inside = c.contains(mouseX, mouseY);
		gfx.scissor(c.x(), c.y(), c.x() + c.w(), c.y() + c.h());
		layout(gfx, c, c.y() - (int) scroll, inside ? mouseX : -1, inside ? mouseY : -1, true);
		gfx.endScissor();
		hits.removeIf(hit -> hit.rect().y() + hit.rect().h() <= c.y() || hit.rect().y() >= c.y() + c.h());
		if (height > c.h()) {
			int bar = Math.max(16, c.h() * c.h() / height);
			int barY = c.y() + (int) ((c.h() - bar) * (scroll / (height - c.h())));
			gfx.fill(c.x() + c.w() + 3, barY, c.x() + c.w() + 5, barY + bar, Theme.CHIP_HOVER);
		}
		gfx.fill(p.x(), p.y() + p.h() - FOOTER, p.x() + p.w(), p.y() + p.h() - FOOTER + 1, Theme.HEADER);
		String footer = System.currentTimeMillis() < statusUntil ? status : "設定會自動儲存 · Esc 返回";
		gfx.text(font, footer, p.x() + 8, p.y() + p.h() - FOOTER + 4, Theme.TEXT_DIM);
	}

	private int layout(Gfx gfx, Rect c, int top, int mouseX, int mouseY, boolean draw) {
		int y = top;
		if (minecraft != null && minecraft.player != null && !RichPresenceModClient.isOnBrilliantServer()) {
			if (draw) {
				gfx.text(font, "目前不在輝煌伺服器，除非開啟危險設定，大部分功能只會在輝煌生效。", c.x(), y + 2, Theme.WARNING);
			}
			y += 16;
		}
		String section = "";
		for (Row row : rows) {
			if (!row.section().equals(section)) {
				section = row.section();
				if (draw) {
					gfx.text(font, section, c.x(), y + 6, Theme.ACCENT);
				}
				y += SECTION;
			}
			if (draw) {
				Rect r = new Rect(c.x(), y, c.w() - 4, ROW - 2);
				boolean on = row.value().getAsBoolean();
				gfx.fill(r.x(), r.y(), r.x() + r.w(), r.y() + r.h(), r.contains(mouseX, mouseY) ? Theme.CARD_HOVER : Theme.CARD);
				gfx.text(font, row.label(), r.x() + 8, r.y() + 5, Theme.TEXT);
				gfx.text(font, font.plainSubstrByWidth(row.detail(), r.w() - 50), r.x() + 8, r.y() + 16, Theme.TEXT_DIM);
				toggleSwitch(gfx, r.x() + r.w() - 34, r.y() + 8, on, Theme.SUCCESS);
				hits.add(new Hit(r, () -> {
					row.set().accept(!on);
					ModSettings.save();
					setStatus((on ? "已關閉「" : "已開啟「") + row.label() + "」");
				}));
			}
			y += ROW;
		}
		if (draw) {
			gfx.text(font, "危險設定", c.x(), y + 6, Theme.DANGER);
		}
		y += SECTION;
		ModSettings.Data s = ModSettings.get();
		String detail = "讓聊天選單、自動腳本、懸浮視窗、格子編號與記分板位置在其他伺服器與單人世界也能使用。其他伺服器可能禁止這類模組，後果自負。";
		List<net.minecraft.util.FormattedCharSequence> lines = font.split(Component.literal(detail), c.w() - 60);
		int boxH = 22 + lines.size() * 10 + (s.anyServer ? 12 : 0);
		if (draw) {
			Rect r = new Rect(c.x(), y, c.w() - 4, boxH);
			gfx.fill(r.x(), r.y(), r.x() + r.w(), r.y() + r.h(), r.contains(mouseX, mouseY) ? 0x40FF7A7A : 0x24FF7A7A);
			gfx.fill(r.x(), r.y(), r.x() + 2, r.y() + r.h(), Theme.DANGER);
			gfx.text(font, "模組功能不再限於輝煌伺服器", r.x() + 8, r.y() + 6, Theme.DANGER);
			for (int i = 0; i < lines.size(); i++) {
				gfx.text(font, lines.get(i), r.x() + 8, r.y() + 18 + i * 10, Theme.TEXT_SECONDARY);
			}
			if (s.anyServer) {
				gfx.text(font, "已於 " + DATE.format(Instant.ofEpochMilli(s.anyServerAcceptedAt)) + " 同意免責聲明（模組 " + s.anyServerAcceptedVersion + "）",
						r.x() + 8, r.y() + 18 + lines.size() * 10 + 2, Theme.TEXT_DIM);
			}
			toggleSwitch(gfx, r.x() + r.w() - 34, r.y() + 5, s.anyServer, Theme.DANGER);
			hits.add(new Hit(r, () -> {
				if (s.anyServer) {
					s.anyServer = false;
					ModSettings.save();
					setStatus("已關閉危險設定，模組功能恢復為只在輝煌伺服器運作");
				} else {
					CommandMenuScreen.setScreen(new AnyServerConsentScreen(this));
				}
			}));
		}
		y += boxH + 6;
		return y - top;
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (super.mouseClicked(event, doubleClick)) {
			return true;
		}
		Rect p = panel();
		if (Theme.backHit(font, p.x(), p.y(), event.x(), event.y())) {
			onClose();
			return true;
		}
		for (Hit hit : List.copyOf(hits)) {
			if (hit.rect().contains(event.x(), event.y())) {
				hit.action().run();
				return true;
			}
		}
		return false;
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		scroll -= scrollY * 24;
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
