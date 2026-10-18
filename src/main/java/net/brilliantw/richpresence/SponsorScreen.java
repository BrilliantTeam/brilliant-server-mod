package net.brilliantw.richpresence;

import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import net.minecraft.client.Minecraft;
//? if >=26.1 {
import net.minecraft.client.gui.GuiGraphicsExtractor;
//?} else {
/*import net.minecraft.client.gui.GuiGraphics;
*///?}
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

public final class SponsorScreen extends Screen {
	private record Rect(int x, int y, int w, int h) {
		boolean contains(double mx, double my) {
			return mx >= x && mx < x + w && my >= y && my < y + h;
		}
	}

	private record Hit(Rect rect, Runnable action) {}

	private record Status(String text, int color) {}

	private static final class Remote {
		String value = "";
		SponsorStore.Check result;
		boolean done;
		long at;
		int seq;

		void schedule(String text) {
			value = text;
			result = null;
			done = false;
			at = text.isBlank() ? 0 : System.currentTimeMillis() + 600;
		}
	}

	private static final int HEADER = 26;
	private static final int TABS = 20;
	private static final int FOOTER = 14;
	private static final int LINE = 10;
	private static final int GOLD = 0xFFFFBB06;
	private static final int GOLD_HOVER = 0xFFFFCB3A;
	private static final int ORANGE = 0xFFFFAB19;
	private static final int DARK = 0xFF1A1A1A;
	private static final int COMMAND = 0xFF6FD3FF;
	private static final String[] TAB_LABELS = { "回饋內容", "立即贊助", "付款方式", "贊助須知" };
	private static final int[] QUICK = { 170, 470, 970, 1170, 2970, 3470, 5000 };
	private static final Pattern NAME = Pattern.compile("^\\.?[A-Za-z0-9_]+$");
	private static final Pattern DISCORD = Pattern.compile("^[A-Za-z0-9_.]+(#[0-9]{1,4})?$");
	private static final Pattern EMAIL = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
	private static final Pattern PHONE = Pattern.compile("^\\+?\\d{8,13}$");
	private static final String TERMS_URL = RPConfig.WEBSITE_URL + "/服務條款";

	private final Screen parent;
	private final List<Hit> hits = new ArrayList<>();
	private final Remote nameCheck = new Remote();
	private final Remote discordCheck = new Remote();
	private int tab;
	private double scroll;
	private int contentHeight;
	private int rewardColumn = -1;
	private int methodDetail = -1;
	private EditBox nameBox;
	private EditBox discordBox;
	private EditBox amountBox;
	private EditBox cumulativeBox;
	private EditBox commentBox;
	private EditBox emailBox;
	private EditBox phoneBox;
	private String nameText;
	private String discordText;
	private String amountText = "";
	private String cumulativeText = "";
	private String commentText = "";
	private String emailText = "";
	private String phoneText = "";
	private int methodId;
	private boolean publishGame = true;
	private boolean anonymous;
	private boolean hideAmount;
	private boolean termsAccepted;
	private boolean submitting;
	private String submitError = "";
	private long estimateAt;
	private int estimateSeq;
	private boolean estimating;
	private SponsorStore.Estimate estimate;

	public SponsorScreen(Screen parent) {
		super(Component.literal("贊助輝煌"));
		this.parent = parent;
		nameText = Minecraft.getInstance().getUser().getName();
		discordText = SponsorStore.savedDiscord();
		nameCheck.schedule(nameText);
		discordCheck.schedule(discordText);
		SponsorStore.load(false);
	}

	static String nameError(String text) {
		if (text.isEmpty()) {
			return "請填寫遊戲 ID";
		}
		if (text.chars().anyMatch(Character::isWhitespace)) {
			return "遊戲 ID 不可包含空格";
		}
		if (!NAME.matcher(text).matches()) {
			return "遊戲 ID 只能包含英文字母、數字與底線";
		}
		return text.length() < 3 || text.length() > 22 ? "遊戲 ID 長度需介於 3～22 個字元" : null;
	}

	static String discordError(String text) {
		if (text.isEmpty()) {
			return "請填寫 Discord 使用者名稱";
		}
		if (text.chars().anyMatch(Character::isWhitespace)) {
			return "Discord 使用者名稱不可包含空格";
		}
		return DISCORD.matcher(text).matches() ? null : "只能包含英文字母、數字、底線與英文句點";
	}

	static String amountError(String text) {
		if (text.isBlank()) {
			return "請填寫贊助金額";
		}
		int amount = parse(text);
		return amount < 30 || amount > 10000 ? "贊助金額需為 30～10000 的整數" : null;
	}

	static String emailError(String text) {
		return text.isBlank() || EMAIL.matcher(text).matches() ? null : "電子郵件格式不正確，或留空";
	}

	static String phoneError(String text) {
		return text.isBlank() || PHONE.matcher(text.replaceAll("[\\s-]", "")).matches() ? null : "聯絡電話需為 8～13 位數字，或留空";
	}

	static String commentError(String text) {
		return text.contains("@") ? "贊助訊息不得含有「@」符號" : null;
	}

	static int parse(String text) {
		try {
			return text.isBlank() ? 0 : Integer.parseInt(text.strip());
		} catch (NumberFormatException e) {
			return -1;
		}
	}

	private Rect panel() {
		int w = Math.min(width - 16, 560);
		return new Rect((width - w) / 2, 8, w, height - 16);
	}

	private Rect content() {
		Rect p = panel();
		return new Rect(p.x() + 10, p.y() + HEADER + TABS + 6, p.w() - 20, p.h() - HEADER - TABS - FOOTER - 10);
	}

	private Rect siteChip() {
		Rect p = panel();
		int w = font.width("官網贊助頁") + 12;
		return new Rect(p.x() + p.w() - w - 6, p.y() + 5, w, 16);
	}

	private Rect tabRect(int index) {
		Rect p = panel();
		int x = p.x() + 10;
		for (int i = 0; i < index; i++) {
			x += font.width(TAB_LABELS[i]) + 20;
		}
		return new Rect(x, p.y() + HEADER + 2, font.width(TAB_LABELS[index]) + 16, TABS - 4);
	}

	private EditBox box(String label, String hint, int maxLength, String value, Consumer<String> responder) {
		EditBox box = new EditBox(font, 0, 0, 120, 16, Component.literal(label));
		box.setMaxLength(maxLength);
		box.setValue(value);
		box.setResponder(responder);
		box.visible = false;
		addRenderableWidget(box);
		Theme.hint(box, hint);
		return box;
	}

	@Override
	protected void init() {
		nameBox = box("遊戲 ID", "範例：RiceChen_", 22, nameText, value -> {
			nameText = value.strip();
			nameCheck.schedule(nameText);
		});
		discordBox = box("Discord 使用者名稱", "範例：ricechen_", 37, discordText, value -> {
			discordText = value.strip();
			discordCheck.schedule(discordText);
		});
		amountBox = box("贊助金額", "30～10000 元", 5, amountText, value -> {
			amountText = value.strip();
			scheduleEstimate();
		});
		cumulativeBox = box("已累積金額", "沒有可留空", 8, cumulativeText, value -> {
			cumulativeText = value.strip();
			scheduleEstimate();
		});
		commentBox = box("贊助訊息", "範例：我愛輝煌", 100, commentText, value -> commentText = value);
		emailBox = box("電子郵件", "example@gmail.com", 254, emailText, value -> emailText = value.strip());
		phoneBox = box("聯絡電話", "0912345678", 20, phoneText, value -> phoneText = value.strip());
	}

	private List<EditBox> formBoxes() {
		return List.of(nameBox, discordBox, amountBox, cumulativeBox, commentBox, emailBox, phoneBox);
	}

	private void selectTab(int index) {
		tab = index;
		scroll = 0;
		if (index != 1) {
			formBoxes().forEach(box -> box.visible = false);
			if (formBoxes().contains(getFocused())) {
				setFocused(null);
			}
		}
	}

	private void scheduleEstimate() {
		estimate = null;
		estimateAt = System.currentTimeMillis() + 300;
	}

	private void runCheck(Remote remote, String field) {
		if (remote.at == 0 || System.currentTimeMillis() < remote.at) {
			return;
		}
		remote.at = 0;
		String value = remote.value;
		if ((field.equals("name") ? nameError(value) : discordError(value)) != null) {
			return;
		}
		int seq = ++remote.seq;
		SponsorStore.check(field, value, result -> {
			if (seq == remote.seq && value.equals(remote.value)) {
				remote.result = result;
				remote.done = true;
			}
		});
	}

	@Override
	public void tick() {
		runCheck(nameCheck, "name");
		runCheck(discordCheck, "discordTag");
		if (estimateAt > 0 && System.currentTimeMillis() >= estimateAt) {
			estimateAt = 0;
			int cumulative = parse(cumulativeText);
			if (cumulative < 0 || amountError(amountText) != null && !amountText.isBlank()) {
				return;
			}
			int seq = ++estimateSeq;
			estimating = true;
			SponsorStore.estimate(amountError(amountText) == null ? parse(amountText) : 0, cumulative, result -> {
				if (seq == estimateSeq) {
					estimating = false;
					estimate = result;
				}
			});
		}
	}

	private void open(String url) {
		if (url != null && !url.isBlank()) {
			ConfirmLinkScreen.confirmLinkNow(this, URI.create(url));
		}
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
		hits.clear();
		Rect p = panel();
		gfx.fill(p.x(), p.y(), p.x() + p.w(), p.y() + p.h(), Theme.PANEL);
		Theme.watermark(gfx, p.x(), p.y(), p.w(), p.h());
		gfx.fill(p.x(), p.y(), p.x() + p.w(), p.y() + HEADER, Theme.HEADER);
		gfx.fill(p.x(), p.y() + HEADER - 1, p.x() + p.w(), p.y() + HEADER, GOLD);
		Theme.drawBack(gfx, font, p.x(), p.y(), mouseX, mouseY);
		gfx.text(font, title.getString(), Theme.titleX(font, p.x()), p.y() + 9, GOLD);
		SponsorStore.Data data = SponsorStore.data();
		if (data != null && !data.excerpt.isBlank()) {
			int x = Theme.titleX(font, p.x()) + font.width(title) + 10;
			gfx.text(font, font.plainSubstrByWidth(data.excerpt, siteChip().x() - x - 6), x, p.y() + 9, Theme.TEXT_DIM);
		}
		Rect site = siteChip();
		chip(gfx, site, "官網贊助頁", site.contains(mouseX, mouseY), GOLD);
		hits.add(new Hit(site, () -> open(data != null ? data.links.page : RPConfig.WEBSITE_URL)));

		for (int i = 0; i < TAB_LABELS.length; i++) {
			Rect r = tabRect(i);
			boolean selected = i == tab;
			gfx.fill(r.x(), r.y(), r.x() + r.w(), r.y() + r.h(), selected ? Theme.SELECTED : r.contains(mouseX, mouseY) ? Theme.CHIP_HOVER : Theme.CHIP);
			if (selected) {
				gfx.fill(r.x(), r.y() + r.h() - 2, r.x() + r.w(), r.y() + r.h(), GOLD);
			}
			gfx.centered(font, TAB_LABELS[i], r.x(), r.y(), r.w(), r.h(), selected ? Theme.TEXT : Theme.TEXT_SECONDARY);
			int index = i;
			hits.add(new Hit(r, () -> selectTab(index)));
		}

		Rect c = content();
		if (data == null) {
			formBoxes().forEach(box -> box.visible = false);
			boolean failed = !SponsorStore.loading() && !SponsorStore.error().isEmpty();
			gfx.centered(font, failed ? SponsorStore.error() + "（點此重試）" : "正在讀取官網的贊助資訊…", c.x(), c.y(), c.w(), c.h(), failed ? Theme.DANGER : Theme.TEXT_DIM);
			if (failed) {
				hits.add(new Hit(c, () -> SponsorStore.load(true)));
			}
		} else {
			if (methodId == 0) {
				methodId = data.methods.stream().filter(m -> m.orderable).mapToInt(m -> m.id).findFirst().orElse(0);
			}
			int before = hits.size();
			contentHeight = section(null, data, c, 0, -1, -1, false);
			hits.subList(before, hits.size()).clear();
			scroll = Math.clamp(scroll, 0, Math.max(0, contentHeight - c.h()));
			int top = c.y() + 2 - (int) scroll;
			boolean inside = c.contains(mouseX, mouseY);
			gfx.scissor(c.x(), c.y(), c.x() + c.w(), c.y() + c.h());
			section(gfx, data, c, top, inside ? mouseX : -1, inside ? mouseY : -1, true);
			gfx.endScissor();
			for (int i = hits.size() - 1; i >= before; i--) {
				Rect r = hits.get(i).rect();
				int y1 = Math.max(r.y(), c.y());
				int y2 = Math.min(r.y() + r.h(), c.y() + c.h());
				if (y2 <= y1) {
					hits.remove(i);
				} else {
					hits.set(i, new Hit(new Rect(r.x(), y1, r.w(), y2 - y1), hits.get(i).action()));
				}
			}
			if (contentHeight > c.h()) {
				int bar = Math.max(16, c.h() * c.h() / contentHeight);
				int barY = c.y() + (int) ((c.h() - bar) * (scroll / (contentHeight - c.h())));
				gfx.fill(c.x() + c.w() + 3, barY, c.x() + c.w() + 5, barY + bar, Theme.CHIP_HOVER);
			}
		}
		gfx.fill(p.x(), p.y() + p.h() - FOOTER, p.x() + p.w(), p.y() + p.h() - FOOTER + 1, Theme.HEADER);
		gfx.text(font, "Patreon、轉帳與海外付款需在官網完成 · 資料來源：輝煌官網 · Esc 返回", p.x() + 8, p.y() + p.h() - FOOTER + 4, Theme.TEXT_DIM);
	}

	private int section(Gfx gfx, SponsorStore.Data data, Rect c, int top, int mouseX, int mouseY, boolean draw) {
		return switch (tab) {
			case 0 -> data.rewards != null && !data.rewards.columns.isEmpty() ? drawRewards(gfx, data, c, top, mouseX, mouseY, draw) : drawTiers(gfx, data, c, top, mouseX, mouseY, draw);
			case 1 -> drawForm(gfx, data, c, top, mouseX, mouseY, draw);
			case 2 -> drawMethods(gfx, data, c, top, mouseX, mouseY, draw);
			default -> drawNotes(gfx, data, c, top, mouseX, mouseY, draw);
		};
	}

	private void chip(Gfx gfx, Rect r, String label, boolean hovered, int color) {
		gfx.fill(r.x(), r.y(), r.x() + r.w(), r.y() + r.h(), hovered ? Theme.CHIP_HOVER : Theme.CHIP);
		gfx.centered(font, label, r.x(), r.y(), r.w(), r.h(), color);
	}

	private void goldButton(Gfx gfx, Rect r, String label, int mouseX, int mouseY, Runnable action) {
		gfx.fill(r.x(), r.y(), r.x() + r.w(), r.y() + r.h(), action == null ? Theme.CHIP : r.contains(mouseX, mouseY) ? GOLD_HOVER : GOLD);
		gfx.centered(font, label, r.x(), r.y(), r.w(), r.h(), action == null ? Theme.TEXT_SECONDARY : DARK);
		if (action != null) {
			hits.add(new Hit(r, action));
		}
	}

	private void link(Gfx gfx, String label, int x, int y, int mouseX, int mouseY, Runnable action) {
		Rect r = new Rect(x, y, font.width(label), LINE);
		gfx.text(font, label, x, y, r.contains(mouseX, mouseY) ? Theme.TEXT : Theme.ACCENT);
		hits.add(new Hit(r, action));
	}

	private List<FormattedCharSequence> wrap(String text, int width) {
		return font.split(Component.literal(text), Math.max(20, width));
	}

	private int paragraph(Gfx gfx, String text, int x, int y, int width, int color, boolean draw) {
		for (FormattedCharSequence line : wrap(text, width)) {
			if (draw) {
				gfx.text(font, line, x, y, color);
			}
			y += LINE;
		}
		return y;
	}

	private static int defaultColumn(SponsorStore.Rewards rewards) {
		for (int i = 0; i < rewards.columns.size(); i++) {
			if (!rewards.columns.get(i).single && rewards.columns.get(i).amount >= 970) {
				return i;
			}
		}
		return 0;
	}

	static int previousColumn(SponsorStore.Rewards rewards, int index) {
		SponsorStore.Column column = rewards.columns.get(index);
		if (column.single) {
			return -1;
		}
		for (int i = index - 1; i >= 0; i--) {
			SponsorStore.Column other = rewards.columns.get(i);
			if (!other.single && other.amount > 0 && other.amount < column.amount) {
				return i;
			}
		}
		return -1;
	}

	private static String columnChip(SponsorStore.Column column) {
		if (column.single) {
			return "單筆 " + column.amount + "+";
		}
		return column.amount > 1 ? Integer.toString(column.amount) : column.label.replace("元", " 元");
	}

	private static String value(SponsorStore.Row row, int index) {
		return index >= 0 && index < row.values.size() ? row.values.get(index) : "";
	}

	private int drawRewards(Gfx gfx, SponsorStore.Data data, Rect c, int top, int mouseX, int mouseY, boolean draw) {
		SponsorStore.Rewards rewards = data.rewards;
		if (rewardColumn < 0 || rewardColumn >= rewards.columns.size()) {
			rewardColumn = defaultColumn(rewards);
		}
		int y = paragraph(gfx, "回饋依「累積」贊助金額發放，多次贊助會合併計算；「單筆」是一次贊助達到金額時額外加贈。選擇階級查看可享回饋：", c.x(), top, c.w() - 4, Theme.TEXT_SECONDARY, draw) + 4;
		int x = c.x();
		for (int i = 0; i < rewards.columns.size(); i++) {
			SponsorStore.Column column = rewards.columns.get(i);
			String label = columnChip(column);
			int w = font.width(label) + 10;
			if (x + w > c.x() + c.w()) {
				x = c.x();
				y += 17;
			}
			if (draw) {
				Rect r = new Rect(x, y, w, 14);
				boolean selected = i == rewardColumn;
				gfx.fill(r.x(), r.y(), r.x() + r.w(), r.y() + r.h(), selected ? 0x60FFBB06 : r.contains(mouseX, mouseY) ? Theme.CHIP_HOVER : column.single ? Theme.SIDEBAR : Theme.CHIP);
				gfx.centered(font, label, r.x(), r.y(), r.w(), r.h(), selected ? GOLD : column.single ? Theme.TEXT_SECONDARY : Theme.TEXT);
				int index = i;
				hits.add(new Hit(r, () -> rewardColumn = index));
			}
			x += w + 3;
		}
		y += 22;
		SponsorStore.Column column = rewards.columns.get(rewardColumn);
		String heading = column.single ? "單筆贊助 " + column.amount + " 元以上，額外加贈"
				: column.amount > 1 ? "累積贊助 " + column.amount + " 元以上可享" : column.label + " 可享";
		int previous = previousColumn(rewards, rewardColumn);
		if (draw) {
			gfx.text(font, heading, c.x(), y, GOLD);
			if (previous >= 0) {
				String legend = "金色＝比 " + rewards.columns.get(previous).amount + " 元階級提升";
				gfx.text(font, legend, c.x() + c.w() - font.width(legend) - 4, y, Theme.TEXT_DIM);
			}
		}
		y += 14;
		int selectedColumn = rewardColumn;
		List<SponsorStore.Row> rows = rewards.rows.stream().filter(row -> !value(row, selectedColumn).isBlank()).toList();
		int columns = c.w() >= 420 ? 2 : 1;
		int cellW = (c.w() - 4 - (columns - 1) * 10) / columns;
		if (draw) {
			for (int i = 0; i < rows.size(); i++) {
				SponsorStore.Row row = rows.get(i);
				int cx = c.x() + (i % columns) * (cellW + 10);
				int cy = y + (i / columns) * 13;
				String shown = value(row, rewardColumn);
				boolean improved = previous >= 0 && !shown.equals(value(row, previous));
				int valueW = font.width(shown);
				if ((i / columns) % 2 == 0) {
					gfx.fill(cx - 2, cy - 2, cx + cellW, cy + 11, 0x18FFFFFF);
				}
				gfx.text(font, font.plainSubstrByWidth(row.name, cellW - valueW - 12), cx, cy, Theme.TEXT_SECONDARY);
				gfx.text(font, shown, cx + cellW - valueW - 4, cy, improved ? GOLD : shown.equals("✓") ? Theme.SUCCESS : Theme.TEXT);
			}
		}
		y += (rows.size() + columns - 1) / columns * 13 + 8;
		if (!column.single) {
			for (int i = rewardColumn + 1; i < rewards.columns.size() && rewards.columns.get(i).single && rewards.columns.get(i).amount == column.amount; i++) {
				int index = i;
				String bonus = String.join("、", rewards.rows.stream().filter(row -> !value(row, index).isBlank()).map(row -> row.name + " " + value(row, index)).toList());
				if (!bonus.isEmpty()) {
					y = paragraph(gfx, "單筆贊助 " + column.amount + " 元以上另外加贈：" + bonus, c.x(), y, c.w() - 4, GOLD, draw) + 6;
				}
			}
			List<SponsorStore.Command> commands = new ArrayList<>();
			List<SponsorStore.Special> specials = new ArrayList<>();
			if (rewardColumn == 0) {
				commands.addAll(data.free);
			}
			for (SponsorStore.Tier tier : data.tiers) {
				if (tier.amount == column.amount) {
					commands.addAll(tier.commands);
					specials.addAll(tier.specials);
				}
			}
			for (SponsorStore.Special special : specials) {
				String title = "★ " + special.title;
				if (draw) {
					Rect r = new Rect(c.x(), y, font.width(title), LINE);
					gfx.text(font, title, c.x(), y, r.contains(mouseX, mouseY) && !special.url.isBlank() ? Theme.TEXT : GOLD);
					if (!special.url.isBlank()) {
						hits.add(new Hit(r, () -> open(special.url)));
					}
				}
				y += LINE + 1;
				for (String detail : special.detail) {
					y = paragraph(gfx, detail, c.x() + 10, y, c.w() - 14, Theme.TEXT_DIM, draw);
				}
				y += 4;
			}
			if (!commands.isEmpty()) {
				if (draw) {
					gfx.text(font, rewardColumn == 0 ? "所有玩家可用指令" : "解鎖指令", c.x(), y, Theme.TEXT);
				}
				y += 12;
				for (SponsorStore.Command command : commands) {
					y = commandLine(gfx, command, c.x() + 10, y, c.w() - 14, draw);
				}
				y += 4;
			}
		}
		if (draw) {
			link(gfx, "在 Google 文件查看完整回饋表 ↗", c.x(), y, mouseX, mouseY, () -> open(data.links.rewards));
		}
		return y + LINE + 4 - top;
	}

	private int drawTiers(Gfx gfx, SponsorStore.Data data, Rect c, int top, int mouseX, int mouseY, boolean draw) {
		int y = top;
		int badge = 56;
		int textX = c.x() + badge + 12;
		int textW = c.w() - badge - 16;
		y = paragraph(gfx, "回饋依「累積」贊助金額發放，多次贊助會合併計算。達到金額後即可使用該階級與以下所有回饋。", c.x(), y, c.w() - 4, Theme.TEXT_SECONDARY, draw) + 6;
		if (!data.free.isEmpty()) {
			if (draw) {
				gfx.label(font, "所有玩家", c.x(), y, 12, Theme.TEXT);
			}
			for (SponsorStore.Command command : data.free) {
				y = commandLine(gfx, command, textX, y, textW, draw);
			}
			y += 6;
		}
		for (int i = 0; i < data.tiers.size(); i++) {
			SponsorStore.Tier tier = data.tiers.get(i);
			int start = y;
			for (SponsorStore.Special special : tier.specials) {
				String heading = "★ " + special.title;
				if (draw) {
					Rect r = new Rect(textX, y, font.width(heading), LINE);
					gfx.text(font, heading, textX, y, r.contains(mouseX, mouseY) ? Theme.TEXT : GOLD);
					if (!special.url.isBlank()) {
						hits.add(new Hit(r, () -> open(special.url)));
					}
				}
				y += LINE + 1;
				for (String detail : special.detail) {
					y = paragraph(gfx, detail, textX + 8, y, textW - 8, Theme.TEXT_DIM, draw);
				}
				y += 3;
			}
			for (SponsorStore.Command command : tier.commands) {
				y = commandLine(gfx, command, textX, y, textW, draw);
			}
			int end = Math.max(y, start + 18);
			if (draw) {
				gfx.fill(c.x(), start - 1, c.x() + badge, start + 13, 0x40FFBB06);
				gfx.fill(c.x(), start - 1, c.x() + 2, start + 13, GOLD);
				gfx.centered(font, tier.amount + "+", c.x() + 2, start - 1, badge - 2, 14, GOLD);
				gfx.text(font, "新台幣", c.x() + (badge - font.width("新台幣")) / 2, start + 15, Theme.TEXT_DIM);
				if (i < data.tiers.size() - 1) {
					gfx.fill(c.x() + badge / 2, start + 26, c.x() + badge / 2 + 1, end + 8, 0x60FFBB06);
				}
			}
			y = end + 10;
		}
		if (draw) {
			link(gfx, "完整回饋表（Google 文件） ↗", c.x(), y, mouseX, mouseY, () -> open(data.links.rewards));
		}
		return y + LINE + 4 - top;
	}

	private int commandLine(Gfx gfx, SponsorStore.Command command, int x, int y, int width, boolean draw) {
		int commandWidth = Math.min(font.width(command.command), width / 2);
		if (draw) {
			gfx.text(font, font.plainSubstrByWidth(command.command, commandWidth + 1), x, y, COMMAND);
		}
		int effectX = x + commandWidth + 8;
		return paragraph(gfx, command.effect, effectX, y, x + width - effectX, Theme.TEXT_SECONDARY, draw);
	}

	private Status remoteStatus(String text, String error, Remote remote, String help) {
		if (text.isEmpty()) {
			return new Status(help, Theme.TEXT_DIM);
		}
		if (error != null) {
			return new Status(error, Theme.DANGER);
		}
		if (remote.done && remote.value.equals(text)) {
			return remote.result == null ? new Status("暫時無法檢查，送出時會再確認", Theme.TEXT_DIM)
					: new Status(remote.result.message(), remote.result.ok() ? Theme.SUCCESS : Theme.DANGER);
		}
		return new Status("檢查中…", Theme.TEXT_DIM);
	}

	private Status amountStatus() {
		if (amountText.isBlank()) {
			return new Status("可直接點下方快捷金額", Theme.TEXT_DIM);
		}
		String error = amountError(amountText);
		if (error != null) {
			return new Status(error, Theme.DANGER);
		}
		if (estimating) {
			return new Status("試算中…", Theme.TEXT_DIM);
		}
		return estimate != null && estimate.watercress() != null ? new Status("可獲得 " + estimate.watercress() + " 個水芥", GOLD) : new Status("", 0);
	}

	private int field(Gfx gfx, Rect c, String label, EditBox box, Status status, int x, int y, int w, boolean draw) {
		if (draw) {
			gfx.text(font, label, x, y, Theme.TEXT_SECONDARY);
			box.setX(x);
			box.setY(y + 11);
			box.setWidth(w);
			box.visible = y + 11 >= c.y() && y + 27 <= c.y() + c.h();
			if (!status.text().isEmpty()) {
				gfx.text(font, font.plainSubstrByWidth(status.text(), w), x, y + 30, status.color());
			}
		}
		return y + 43;
	}

	private SponsorStore.Method method(SponsorStore.Data data, int id) {
		return data.methods.stream().filter(m -> m.id == id).findFirst().orElse(null);
	}

	private int drawForm(Gfx gfx, SponsorStore.Data data, Rect c, int top, int mouseX, int mouseY, boolean draw) {
		boolean wide = c.w() >= 420;
		int colW = wide ? (c.w() - 16) / 2 : c.w() - 6;
		int leftX = c.x() + 2;
		int rightX = wide ? leftX + colW + 12 : leftX;
		int y = top;
		SponsorStore.Order last = SponsorStore.lastOrder();
		if (last != null) {
			if (draw) {
				link(gfx, "查看剛才建立的訂單 →", leftX, y, mouseX, mouseY, () -> CommandMenuScreen.setScreen(new SponsorOrderScreen(this, last)));
			}
			y += 14;
		}
		int left = field(gfx, c, "遊戲 ID", nameBox, remoteStatus(nameText, nameError(nameText), nameCheck, "要接收回饋的 Minecraft 遊戲 ID"), leftX, y, colW, draw);
		left = field(gfx, c, "Discord 使用者名稱", discordBox, remoteStatus(discordText, discordError(discordText), discordCheck, "需先加入輝煌 Discord 群組"), leftX, left, colW, draw);
		left = field(gfx, c, "贊助金額（新台幣）", amountBox, amountStatus(), leftX, left, colW, draw);
		int qx = leftX;
		for (int amount : QUICK) {
			String label = Integer.toString(amount);
			int w = font.width(label) + 8;
			if (qx + w > leftX + colW) {
				break;
			}
			if (draw) {
				Rect r = new Rect(qx, left - 2, w, 13);
				chip(gfx, r, label, r.contains(mouseX, mouseY), label.equals(amountText) ? GOLD : Theme.TEXT);
				hits.add(new Hit(r, () -> amountBox.setValue(label)));
			}
			qx += w + 3;
		}
		left += 16;
		int cumulative = parse(cumulativeText);
		left = field(gfx, c, "已累積贊助金額（選填，用於試算）", cumulativeBox,
				cumulative < 0 ? new Status("需為整數，沒有可留空", Theme.DANGER) : new Status("", 0), leftX, left, colW, draw);

		int right = wide ? y : left;
		if (draw) {
			gfx.text(font, "付款方式", rightX, right, Theme.TEXT_SECONDARY);
		}
		int mx = rightX;
		int my = right + 11;
		for (SponsorStore.Method method : data.methods) {
			if (!method.orderable) {
				continue;
			}
			int w = font.width(method.name) + 12;
			if (mx > rightX && mx + w > rightX + colW) {
				mx = rightX;
				my += 19;
			}
			if (draw) {
				Rect r = new Rect(mx, my, w, 16);
				boolean selected = method.id == methodId;
				gfx.fill(r.x(), r.y(), r.x() + r.w(), r.y() + r.h(), selected ? 0x60FFBB06 : r.contains(mouseX, mouseY) ? Theme.CHIP_HOVER : Theme.CHIP);
				gfx.centered(font, method.name, r.x(), r.y(), r.w(), r.h(), selected ? GOLD : Theme.TEXT);
				hits.add(new Hit(r, () -> methodId = method.id));
			}
			mx += w + 4;
		}
		right = my + 20;
		SponsorStore.Method selected = method(data, methodId);
		if (selected != null) {
			boolean slow = selected.kind.equals("slow");
			right = paragraph(gfx, selected.fee + (slow ? "・" + selected.tag : ""), rightX, right, colW, slow ? ORANGE : Theme.TEXT_DIM, draw);
		}
		right += 4;
		String commentWarn = commentError(commentText);
		right = field(gfx, c, "贊助訊息（選填）", commentBox, commentWarn != null ? new Status(commentWarn, Theme.DANGER) : new Status(commentText.length() + "/100・會顯示在贊助公告", Theme.TEXT_DIM), rightX, right, colW, draw);
		String emailWarn = emailError(emailText);
		right = field(gfx, c, "電子郵件（選填）", emailBox, emailWarn != null ? new Status(emailWarn, Theme.DANGER) : new Status("", 0), rightX, right, colW, draw);
		String phoneWarn = phoneError(phoneText);
		right = field(gfx, c, "聯絡電話（選填）", phoneBox, phoneWarn != null ? new Status(phoneWarn, Theme.DANGER) : new Status("", 0), rightX, right, colW, draw);

		y = Math.max(left, right) + 2;
		if (draw) {
			gfx.fill(c.x(), y, c.x() + c.w() - 4, y + 1, Theme.ACCENT_DIM);
			gfx.text(font, "贊助公告", leftX, y + 12, Theme.TEXT_SECONDARY);
		}
		y += 8;
		int startX = leftX + font.width("贊助公告") + 8;
		int tx = startX;
		String[] labels = { "遊戲內：" + (publishGame ? "發布於聊天頻道" : "不發布"), "Discord：" + (anonymous ? "匿名發布" : "公開發布"), "金額：" + (hideAmount ? "隱藏" : "顯示") };
		Runnable[] actions = { () -> publishGame = !publishGame, () -> anonymous = !anonymous, () -> hideAmount = !hideAmount };
		for (int i = 0; i < labels.length; i++) {
			int w = font.width(labels[i]) + 14;
			if (tx > startX && tx + w > c.x() + c.w()) {
				tx = startX;
				y += 19;
			}
			if (draw) {
				Rect r = new Rect(tx, y, w, 16);
				chip(gfx, r, labels[i], r.contains(mouseX, mouseY), Theme.TEXT);
				hits.add(new Hit(r, actions[i]));
			}
			tx += w + 6;
		}
		y += 24;

		int amount = parse(amountText);
		if (amountError(amountText) == null && cumulative >= 0 && estimate != null && !estimating) {
			int total = cumulative + amount;
			String next = estimate.nextTier() == null ? "贊助後將達到最高階級，感謝您的支持！" : "贊助後距離下一階級還需 " + Math.max(0, estimate.nextTier() - total) + " 元";
			if (draw) {
				gfx.text(font, next, leftX, y, Theme.TEXT);
			}
			y += 12;
			for (SponsorStore.Tier tier : data.tiers) {
				if (tier.amount > cumulative && tier.amount <= total) {
					String perk = !tier.specials.isEmpty() ? tier.specials.getFirst().title
							: tier.commands.isEmpty() ? "" : tier.commands.getFirst().command + (tier.commands.size() > 1 ? " 等 " + tier.commands.size() + " 個指令" : "");
					y = paragraph(gfx, "✦ 新解鎖 " + tier.amount + "+：" + perk, leftX + 6, y, c.w() - 12, GOLD, draw);
				}
			}
			y += 6;
		}

		if (draw) {
			gfx.fill(leftX, y, leftX + 10, y + 10, termsAccepted ? GOLD : Theme.CHIP);
			if (termsAccepted) {
				gfx.centered(font, "✓", leftX, y, 10, 10, DARK);
			}
			String label = "我已查閱並同意本服的服務條款";
			Rect labelRect = new Rect(leftX, y, 14 + font.width(label), 10);
			gfx.text(font, label, leftX + 14, y + 1, Theme.TEXT);
			hits.add(new Hit(labelRect, () -> termsAccepted = !termsAccepted));
			link(gfx, "服務條款 ↗", labelRect.x() + labelRect.w() + 8, y + 1, mouseX, mouseY, () -> open(TERMS_URL));
		}
		y += 16;
		if (!submitError.isEmpty()) {
			y = paragraph(gfx, submitError, leftX, y, c.w() - 8, Theme.DANGER, draw) + 4;
		}
		String submitLabel = submitting ? "正在建立訂單…" : "已確認填寫資訊無誤，建立訂單";
		if (draw) {
			goldButton(gfx, new Rect(leftX, y, Math.min(c.w() - 6, font.width(submitLabel) + 40), 20), submitLabel, mouseX, mouseY, submitting ? null : () -> confirmSubmit(data));
		}
		y += 26;
		y = paragraph(gfx, "建立訂單後會顯示繳費代碼，同時私訊到你的 Discord。繳費完成後系統會自動發放回饋，不需要回報。", leftX, y, c.w() - 8, Theme.TEXT_DIM, draw);
		return y + 4 - top;
	}

	private String formProblem() {
		String error = nameError(nameText);
		if (error != null) {
			return error;
		}
		if (nameCheck.done && nameCheck.result != null && !nameCheck.result.ok()) {
			return nameCheck.result.message();
		}
		if ((error = discordError(discordText)) != null) {
			return error;
		}
		if (discordCheck.done && discordCheck.result != null && !discordCheck.result.ok()) {
			return discordCheck.result.message();
		}
		if ((error = amountError(amountText)) != null) {
			return error;
		}
		if (methodId == 0) {
			return "目前沒有可在模組內使用的付款方式，請改到官網贊助";
		}
		if ((error = commentError(commentText)) != null || (error = emailError(emailText)) != null || (error = phoneError(phoneText)) != null) {
			return error;
		}
		if (!nameCheck.done || !discordCheck.done) {
			return "正在確認遊戲 ID 與 Discord 帳號，請稍候再按一次";
		}
		return termsAccepted ? null : "請先勾選「我已查閱並同意本服的服務條款」";
	}

	private void confirmSubmit(SponsorStore.Data data) {
		String problem = formProblem();
		if (problem != null) {
			submitError = problem;
			return;
		}
		submitError = "";
		SponsorStore.Method method = method(data, methodId);
		String summary = "遊戲 ID：" + nameText + "\nDiscord：" + discordText
				+ "\n付款方式：" + (method != null ? method.name : "#" + methodId)
				+ "\n贊助金額：" + parse(amountText) + " 元" + (method != null && !method.fee.isBlank() ? "（另計" + method.fee + "）" : "")
				+ "\n\n確認後會立即建立繳費訂單。";
		CommandMenuScreen.setScreen(new ConfirmScreen(confirmed -> {
			CommandMenuScreen.setScreen(this);
			if (confirmed) {
				submit();
			}
		}, Component.literal("確定要建立贊助訂單嗎？"), Component.literal(summary)));
	}

	static Map<String, String> orderForm(String name, String discord, int amount, int cumulative, int method, String comment,
			boolean hideAmount, boolean publishGame, boolean anonymous, String email, String phone) {
		Map<String, String> fields = new LinkedHashMap<>();
		fields.put("name", name);
		fields.put("discordTag", discord);
		fields.put("donateAmount", Integer.toString(amount));
		fields.put("cumulativeAmount", cumulative > 0 ? Integer.toString(cumulative) : "");
		fields.put("paymentMethod", Integer.toString(method));
		fields.put("comment", comment);
		fields.put("hideAmount", hideAmount ? "1" : "0");
		fields.put("publishWayMinecraft", publishGame ? "0" : "3");
		fields.put("publishWayDiscord", anonymous ? "1" : "0");
		fields.put("email", email);
		fields.put("mobileNumber", phone);
		return fields;
	}

	private void submit() {
		submitting = true;
		Map<String, String> fields = orderForm(nameText, discordText, parse(amountText), parse(cumulativeText), methodId, commentText, hideAmount, publishGame, anonymous, emailText, phoneText);
		SponsorStore.submit(fields, methodId, result -> {
			submitting = false;
			if (result.order() == null) {
				submitError = result.error();
				return;
			}
			SponsorStore.saveDiscord(discordText);
			termsAccepted = false;
			CommandMenuScreen.setScreen(new SponsorOrderScreen(this, result.order()));
		});
	}

	private int drawMethods(Gfx gfx, SponsorStore.Data data, Rect c, int top, int mouseX, int mouseY, boolean draw) {
		int y = paragraph(gfx, "超商代碼與條碼可以直接在模組內建立訂單，點卡片查看繳費步驟；標示 ↗ 的方式會開啟官網說明。", c.x(), top, c.w() - 4, Theme.TEXT_SECONDARY, draw) + 6;
		if (methodDetail < 0) {
			for (int i = 0; i < data.methods.size(); i++) {
				if (data.methods.get(i).orderable) {
					methodDetail = i;
					break;
				}
			}
		}
		int gap = 8;
		int cardW = (c.w() - gap - 4) / 2;
		int cardH = 46;
		if (draw) {
			for (int i = 0; i < data.methods.size(); i++) {
				SponsorStore.Method method = data.methods.get(i);
				Rect r = new Rect(c.x() + (i % 2) * (cardW + gap), y + (i / 2) * (cardH + gap), cardW, cardH);
				int tagColor = switch (method.kind) {
					case "good" -> Theme.SUCCESS;
					case "slow" -> ORANGE;
					default -> 0xFF9AA0A6;
				};
				gfx.fill(r.x(), r.y(), r.x() + r.w(), r.y() + r.h(), i == methodDetail ? Theme.SELECTED : r.contains(mouseX, mouseY) ? Theme.CHIP_HOVER : Theme.SIDEBAR);
				gfx.fill(r.x(), r.y(), r.x() + 2, r.y() + r.h(), tagColor);
				if (i == methodDetail) {
					gfx.fill(r.x(), r.y() + r.h() - 1, r.x() + r.w(), r.y() + r.h(), GOLD);
				}
				gfx.text(font, method.name + (method.orderable ? "" : " ↗"), r.x() + 8, r.y() + 5, Theme.TEXT);
				int tagW = font.width(method.tag) + 8;
				gfx.fill(r.x() + r.w() - tagW - 6, r.y() + 3, r.x() + r.w() - 6, r.y() + 15, (tagColor & 0xFFFFFF) | 0x40000000);
				gfx.centered(font, method.tag, r.x() + r.w() - tagW - 6, r.y() + 3, tagW, 12, tagColor);
				int ly = r.y() + 18;
				for (String line : method.desc) {
					gfx.text(font, font.plainSubstrByWidth(line, r.w() - 14), r.x() + 8, ly, Theme.TEXT_DIM);
					ly += LINE;
				}
				int index = i;
				hits.add(new Hit(r, () -> {
					if (method.orderable) {
						methodDetail = index;
					} else {
						open(method.url);
					}
				}));
			}
		}
		y += (data.methods.size() + 1) / 2 * (cardH + gap) + 4;
		if (methodDetail >= 0 && methodDetail < data.methods.size()) {
			SponsorStore.Method method = data.methods.get(methodDetail);
			if (draw) {
				gfx.text(font, method.name + " 繳費步驟", c.x(), y, GOLD);
			}
			y += 13;
			for (int i = 0; i < method.steps.size(); i++) {
				if (draw) {
					gfx.text(font, (i + 1) + ".", c.x() + 2, y, Theme.TEXT_DIM);
				}
				y = paragraph(gfx, method.steps.get(i), c.x() + 14, y, c.w() - 18, Theme.TEXT, draw) + 2;
			}
			if (!method.fee.isBlank()) {
				y = paragraph(gfx, method.fee, c.x(), y + 2, c.w() - 4, Theme.TEXT_SECONDARY, draw);
			}
			if (!method.settlement.isBlank()) {
				y = paragraph(gfx, method.settlement, c.x(), y, c.w() - 4, Theme.TEXT_SECONDARY, draw);
			}
			y += 6;
			String label = "用 " + method.name + " 贊助 →";
			if (draw) {
				goldButton(gfx, new Rect(c.x(), y, font.width(label) + 20, 18), label, mouseX, mouseY, () -> {
					methodId = method.id;
					selectTab(1);
				});
			}
			y += 22;
		}
		return y - top;
	}

	private int drawNotes(Gfx gfx, SponsorStore.Data data, Rect c, int top, int mouseX, int mouseY, boolean draw) {
		int y = top;
		if (draw) {
			gfx.text(font, "資金使用", c.x(), y, GOLD);
		}
		y = paragraph(gfx, data.usage, c.x(), y + 12, c.w() - 4, Theme.TEXT_SECONDARY, draw) + 8;
		if (draw) {
			gfx.text(font, "贊助須知", c.x(), y, GOLD);
		}
		y += 12;
		for (int i = 0; i < data.notes.size(); i++) {
			if (draw) {
				gfx.text(font, (i + 1) + ".", c.x(), y, Theme.TEXT_DIM);
			}
			y = paragraph(gfx, data.notes.get(i), c.x() + 14, y, c.w() - 18, Theme.TEXT, draw) + 3;
		}
		y += 8;
		String[][] buttons = { { "查詢我的贊助紀錄", data.links.records }, { "Discord 贊助處理工單", data.links.discord }, { "服務條款", TERMS_URL } };
		int x = c.x();
		for (String[] button : buttons) {
			int w = font.width(button[0]) + 16;
			if (draw) {
				Rect r = new Rect(x, y, w, 16);
				chip(gfx, r, button[0], r.contains(mouseX, mouseY), Theme.ACCENT);
				hits.add(new Hit(r, () -> open(button[1])));
			}
			x += w + 6;
		}
		return y + 20 - top;
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
		setFocused(null);
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
