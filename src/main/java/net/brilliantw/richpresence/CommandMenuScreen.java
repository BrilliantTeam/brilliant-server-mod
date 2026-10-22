package net.brilliantw.richpresence;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
//? if >=26.1 {
import net.minecraft.client.gui.GuiGraphicsExtractor;
//?} else {
/*import net.minecraft.client.gui.GuiGraphics;
*///?}
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

public final class CommandMenuScreen extends Screen {
	private record Row(CommandStore.Node node, int index, CommandStore.Entry entry) {}

	private record Deleted(CommandStore.Group group, int index, CommandStore.Entry entry) {}

	private record Rect(int x, int y, int w, int h) {
		boolean contains(double mouseX, double mouseY) {
			return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
		}
	}

	private enum Action {
		KEY("按鍵", "綁定按鍵：在遊戲中按下就直接執行這條指令，可加 Ctrl／Alt／Shift"),
		FILL("填入", "放進聊天欄，可補上參數再送出"),
		EDIT("編輯", "修改指令、備註或分組"),
		DELETE("刪除", "刪除後可在下方按「復原」");

		final String label;
		final String description;

		Action(String label, String description) {
			this.label = label;
			this.description = description;
		}
	}

	private enum GroupIcon {
		DELETE(Icons.CLOSE, "刪除分組"),
		RENAME(Icons.EDIT, "重新命名分組"),
		ADD_CHILD(Icons.PLUS, "新增子分組");

		final Icons symbol;
		final String description;

		GroupIcon(Icons symbol, String description) {
			this.symbol = symbol;
			this.description = description;
		}
	}

	private static final int HEADER = 26;
	private static final int FOOTER = 16;
	private static final int SIDEBAR = 132;
	private static final int GROUP_ROW = 18;
	private static final int INDENT = 8;
	private static final int LIST_HEADER = 20;
	private static final int CARD = 26;
	private static final int CARD_GAP = 3;
	private static final int CHIP_HEIGHT = 14;
	private static final String ADD_COMMAND = "＋ 新增指令";
	private static final String ADD_GROUP = "新增分組";
	private static final String UNDO = "復原";

	private final Screen parent;
	private final CommandStore store = CommandStore.get();
	private CommandStore.Entry listening;
	private CommandStore.Group selected;
	private String search = "";
	private double listScroll;
	private double groupScroll;
	private String status = "";
	private int statusColor = Theme.TEXT_SECONDARY;
	private long statusUntil;
	private Deleted lastDeleted;
	private EditBox searchBox;

	public CommandMenuScreen(Screen parent) {
		super(Component.literal("自訂指令"));
		this.parent = parent;
	}

	static void setScreen(Screen screen) {
		//? if >=26.2 {
		Minecraft.getInstance().gui.setScreen(screen);
		//?} else {
		/*Minecraft.getInstance().setScreen(screen);
		*///?}
	}

	private List<CommandStore.Node> allNodes() {
		return CommandStore.flatten(store.groups, false);
	}

	private List<CommandStore.Node> visibleNodes() {
		return CommandStore.flatten(store.groups, true);
	}

	private CommandStore.Node nodeOf(CommandStore.Group group) {
		return allNodes().stream().filter(node -> node.group() == group).findFirst().orElse(null);
	}

	private int panelWidth() {
		return Math.min(width - 16, 540);
	}

	private Rect panel() {
		return new Rect((width - panelWidth()) / 2, 8, panelWidth(), height - 16);
	}

	private Rect sidebar() {
		Rect p = panel();
		return new Rect(p.x(), p.y() + HEADER, SIDEBAR, p.h() - HEADER - FOOTER);
	}

	private Rect groupArea() {
		Rect s = sidebar();
		return new Rect(s.x(), s.y() + 4, s.w(), s.h() - GROUP_ROW - 12);
	}

	private Rect addGroupRow() {
		Rect s = sidebar();
		return new Rect(s.x() + 4, s.y() + s.h() - GROUP_ROW - 4, s.w() - 8, GROUP_ROW);
	}

	private Rect main() {
		Rect p = panel();
		int x = p.x() + SIDEBAR + 8;
		return new Rect(x, p.y() + HEADER + 4, p.x() + p.w() - 8 - x, p.h() - HEADER - FOOTER - 8);
	}

	private Rect list() {
		Rect m = main();
		return new Rect(m.x(), m.y() + LIST_HEADER, m.w(), m.h() - LIST_HEADER);
	}

	private Rect addCommandChip() {
		Rect m = main();
		int w = font.width(ADD_COMMAND) + 12;
		return new Rect(m.x() + m.w() - w, m.y() + 1, w, CHIP_HEIGHT);
	}

	private Rect footer() {
		Rect p = panel();
		return new Rect(p.x(), p.y() + p.h() - FOOTER, p.w(), FOOTER);
	}

	private Rect undoChip() {
		Rect f = footer();
		int w = font.width(UNDO) + 10;
		return new Rect(f.x() + 8 + font.width(status) + 6, f.y() + 1, w, CHIP_HEIGHT);
	}

	private Rect groupRow(int position) {
		Rect area = groupArea();
		return new Rect(area.x() + 4, area.y() + position * GROUP_ROW - (int) groupScroll, area.w() - 8, GROUP_ROW);
	}

	private Rect arrow(Rect row, int depth) {
		return new Rect(row.x() + 2 + depth * INDENT, row.y() + 2, 9, GROUP_ROW - 4);
	}

	private List<GroupIcon> icons(CommandStore.Node node) {
		return node.depth() < CommandStore.MAX_DEPTH
				? List.of(GroupIcon.DELETE, GroupIcon.RENAME, GroupIcon.ADD_CHILD)
				: List.of(GroupIcon.DELETE, GroupIcon.RENAME);
	}

	private Rect groupIcon(Rect row, int slot) {
		return new Rect(row.x() + row.w() - 13 * (slot + 1), row.y() + 2, 12, GROUP_ROW - 4);
	}

	private Rect card(int position) {
		Rect l = list();
		return new Rect(l.x(), l.y() + position * (CARD + CARD_GAP) - (int) listScroll, l.w() - 4, CARD);
	}

	private Rect actionChip(Rect card, Action action) {
		int right = card.x() + card.w() - 6;
		for (int i = Action.values().length - 1; i >= 0; i--) {
			Action current = Action.values()[i];
			int w = font.width(current.label) + 10;
			right -= w;
			if (current == action) {
				return new Rect(right, card.y() + (CARD - CHIP_HEIGHT) / 2, w, CHIP_HEIGHT);
			}
			right -= 3;
		}
		throw new IllegalStateException();
	}

	private String groupTag(Row row) {
		if (row.node().group() == selected && search.isBlank()) {
			return "";
		}
		CommandStore.Node selectedNode = selected != null && search.isBlank() ? nodeOf(selected) : null;
		String path = row.node().path();
		return selectedNode != null && path.startsWith(selectedNode.path() + CommandStore.PATH_SEPARATOR)
				? path.substring(selectedNode.path().length() + CommandStore.PATH_SEPARATOR.length())
				: path;
	}

	private List<Row> rows() {
		String query = search.trim().toLowerCase(Locale.ROOT);
		List<CommandStore.Node> nodes = query.isEmpty() && selected != null
				? CommandStore.flatten(List.of(selected), false).stream().map(node -> nodeOf(node.group())).toList()
				: allNodes();
		List<Row> rows = new ArrayList<>();
		for (CommandStore.Node node : nodes) {
			List<CommandStore.Entry> commands = node.group().commands;
			for (int i = 0; i < commands.size(); i++) {
				CommandStore.Entry entry = commands.get(i);
				if (query.isEmpty()
						|| entry.command.toLowerCase(Locale.ROOT).contains(query)
						|| entry.note.toLowerCase(Locale.ROOT).contains(query)
						|| entry.alias.contains(query)
						|| node.path().toLowerCase(Locale.ROOT).contains(query)) {
					rows.add(new Row(node, i, entry));
				}
			}
		}
		return rows;
	}

	@Override
	protected void init() {
		if (selected != null && nodeOf(selected) == null) {
			selected = null;
		}
		Rect p = panel();
		int buttonWidth = 44;
		int titleWidth = Theme.titleX(font, p.x()) - p.x() + font.width(title) + 10;
		searchBox = addRenderableWidget(new EditBox(font, p.x() + titleWidth, p.y() + 4, p.w() - titleWidth - (buttonWidth + 4) * 2 - 8, 18, Component.literal("搜尋")));
		searchBox.setMaxLength(64);
		Theme.hint(searchBox, "搜尋指令、備註或分組…  按 Enter 執行第一個");
		searchBox.setValue(search);
		searchBox.setResponder(value -> {
			search = value;
			listScroll = 0;
		});
		addRenderableWidget(Button.builder(Component.literal("匯入"), b -> setScreen(new ImportScreen(this, message -> setStatus(message, Theme.SUCCESS))))
				.bounds(p.x() + p.w() - 8 - buttonWidth * 2 - 4, p.y() + 3, buttonWidth, 20)
				.tooltip(Tooltip.create(Component.literal("貼上其他玩家分享的指令集代碼\n可預覽內容，選擇合併或覆蓋")))
				.build());
		addRenderableWidget(Button.builder(Component.literal("匯出"), b -> exportAll())
				.bounds(p.x() + p.w() - 8 - buttonWidth, p.y() + 3, buttonWidth, 20)
				.tooltip(Tooltip.create(Component.literal("把全部分組複製到剪貼簿\n貼給其他玩家即可匯入")))
				.build());
		setInitialFocus(searchBox);
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
		Rect p = panel();
		gfx.fill(p.x(), p.y(), p.x() + p.w(), p.y() + p.h(), Theme.PANEL);
		Theme.watermark(gfx, p.x(), p.y(), p.w(), p.h());
		gfx.fill(p.x(), p.y(), p.x() + p.w(), p.y() + HEADER, Theme.HEADER);
		gfx.fill(p.x(), p.y() + HEADER - 1, p.x() + p.w(), p.y() + HEADER, Theme.ACCENT_DIM);
		gfx.text(font, title.getString(), Theme.titleX(font, p.x()), p.y() + 9, Theme.ACCENT);
		Theme.drawBack(gfx, font, p.x(), p.y(), mouseX, mouseY);

		List<Component> tooltip = new ArrayList<>();
		drawSidebar(gfx, mouseX, mouseY, tooltip);
		drawList(gfx, mouseX, mouseY, tooltip);
		drawFooter(gfx, mouseX, mouseY);

		if (!tooltip.isEmpty()) {
			gfx.tooltip(font, tooltip, mouseX, mouseY);
		}
	}

	private void drawSidebar(Gfx gfx, int mouseX, int mouseY, List<Component> tooltip) {
		Rect s = sidebar();
		gfx.fill(s.x(), s.y(), s.x() + s.w(), s.y() + s.h(), Theme.SIDEBAR);
		Rect area = groupArea();
		List<CommandStore.Node> nodes = visibleNodes();
		groupScroll = Math.clamp(groupScroll, 0, Math.max(0, (nodes.size() + 1) * GROUP_ROW - area.h()));
		gfx.scissor(area.x(), area.y(), area.x() + area.w(), area.y() + area.h());
		for (int position = 0; position <= nodes.size(); position++) {
			Rect row = groupRow(position);
			if (row.y() + row.h() < area.y() || row.y() > area.y() + area.h()) {
				continue;
			}
			CommandStore.Node node = position == 0 ? null : nodes.get(position - 1);
			CommandStore.Group group = node == null ? null : node.group();
			int depth = node == null ? 0 : node.depth();
			boolean hovered = row.contains(mouseX, mouseY) && area.contains(mouseX, mouseY);
			boolean isSelected = group == selected;
			if (isSelected) {
				gfx.fill(row.x(), row.y(), row.x() + row.w(), row.y() + row.h(), Theme.SELECTED);
				gfx.fill(row.x(), row.y(), row.x() + 2, row.y() + row.h(), Theme.ACCENT);
			} else if (hovered) {
				gfx.fill(row.x(), row.y(), row.x() + row.w(), row.y() + row.h(), Theme.CARD);
			}

			if (group != null && !group.children.isEmpty()) {
				Rect arrow = arrow(row, depth);
				(group.collapsed ? Icons.CARET_RIGHT : Icons.CARET_DOWN).drawCentered(gfx, arrow.x(), arrow.y(), arrow.w(), arrow.h(), arrow.contains(mouseX, mouseY) ? Theme.TEXT : Theme.TEXT_DIM);
				if (arrow.contains(mouseX, mouseY)) {
					tooltip.add(Component.literal(group.collapsed ? "展開子分組" : "收合子分組"));
				}
			}

			int nameX = row.x() + 12 + depth * INDENT;
			String name = group == null ? "全部指令" : group.name;
			String count = String.valueOf(group == null ? store.groups.stream().mapToInt(CommandStore.Group::totalCommands).sum() : group.totalCommands());
			List<GroupIcon> icons = node == null ? List.of() : icons(node);
			boolean showIcons = hovered && node != null;
			int reserved = showIcons ? icons.size() * 13 + 2 : font.width(count) + 6;
			gfx.text(font, font.plainSubstrByWidth(name, row.x() + row.w() - reserved - nameX), nameX, row.y() + 5, isSelected ? Theme.TEXT : Theme.TEXT_SECONDARY);
			if (showIcons) {
				for (int slot = 0; slot < icons.size(); slot++) {
					GroupIcon icon = icons.get(slot);
					Rect rect = groupIcon(row, slot);
					drawIcon(gfx, rect, icon.symbol, icon == GroupIcon.DELETE ? Theme.DANGER : icon == GroupIcon.ADD_CHILD ? Theme.ACCENT : Theme.TEXT, mouseX, mouseY);
					if (rect.contains(mouseX, mouseY)) {
						tooltip.add(Component.literal(icon.description));
					}
				}
			} else {
				gfx.text(font, count, row.x() + row.w() - 5 - font.width(count), row.y() + 5, Theme.TEXT_DIM);
			}
		}
		gfx.endScissor();

		Rect add = addGroupRow();
		if (add.contains(mouseX, mouseY)) {
			gfx.fill(add.x(), add.y(), add.x() + add.w(), add.y() + add.h(), Theme.CARD);
		}
		Icons.PLUS.draw(gfx, add.x() + 7, add.y() + (add.h() - Icons.PLUS.height()) / 2, Theme.ACCENT);
		gfx.label(font, ADD_GROUP, add.x() + 7 + Icons.PLUS.width() + 4, add.y(), add.h(), Theme.ACCENT);
	}

	private void drawIcon(Gfx gfx, Rect icon, Icons symbol, int color, int mouseX, int mouseY) {
		if (icon.contains(mouseX, mouseY)) {
			gfx.fill(icon.x(), icon.y(), icon.x() + icon.w(), icon.y() + icon.h(), Theme.CHIP_HOVER);
		}
		symbol.drawCentered(gfx, icon.x(), icon.y(), icon.w(), icon.h(), color);
	}

	private void drawChip(Gfx gfx, Rect chip, String label, int color, int mouseX, int mouseY) {
		gfx.fill(chip.x(), chip.y(), chip.x() + chip.w(), chip.y() + chip.h(), chip.contains(mouseX, mouseY) ? Theme.CHIP_HOVER : Theme.CHIP);
		gfx.label(font, label, chip.x() + 5, chip.y(), chip.h(), color);
	}

	private void drawList(Gfx gfx, int mouseX, int mouseY, List<Component> tooltip) {
		Rect m = main();
		List<Row> rows = rows();
		CommandStore.Node selectedNode = selected == null ? null : nodeOf(selected);
		String heading = !search.isBlank() ? "搜尋結果" : selectedNode == null ? "全部指令" : selectedNode.path();
		Rect add = addCommandChip();
		String countText = rows.size() + " 個";
		int headingWidth = add.x() - m.x() - 14 - font.width(countText);
		String shownHeading = font.plainSubstrByWidth(heading, headingWidth);
		gfx.text(font, shownHeading, m.x() + 2, m.y() + 4, Theme.TEXT);
		gfx.text(font, countText, m.x() + 8 + font.width(shownHeading), m.y() + 4, Theme.TEXT_DIM);
		drawChip(gfx, add, ADD_COMMAND, Theme.ACCENT, mouseX, mouseY);

		Rect l = list();
		int contentHeight = rows.size() * (CARD + CARD_GAP) - CARD_GAP;
		listScroll = Math.clamp(listScroll, 0, Math.max(0, contentHeight - l.h()));

		if (rows.isEmpty()) {
			drawEmptyState(gfx, l);
			return;
		}

		gfx.scissor(l.x(), l.y(), l.x() + l.w(), l.y() + l.h());
		for (int i = 0; i < rows.size(); i++) {
			Rect card = card(i);
			if (card.y() + card.h() < l.y() || card.y() > l.y() + l.h()) {
				continue;
			}
			Row row = rows.get(i);
			boolean hovered = card.contains(mouseX, mouseY) && l.contains(mouseX, mouseY);
			gfx.fill(card.x(), card.y(), card.x() + card.w(), card.y() + card.h(), hovered ? Theme.CARD_HOVER : Theme.CARD);
			if (hovered) {
				gfx.fill(card.x(), card.y(), card.x() + 2, card.y() + card.h(), Theme.ACCENT);
			}

			int textRight = hovered ? actionChip(card, Action.KEY).x() - 6 : card.x() + card.w() - 8;
			String keyLabel = CommandKeys.label(row.entry());
			String tag = (row.entry() == listening ? "[請按下按鍵⋯] " : keyLabel.isEmpty() ? "" : "[" + keyLabel + "] ") + groupTag(row);
			if (!hovered && !tag.isEmpty()) {
				int tagWidth = Math.min(font.width(tag), card.w() / 3);
				gfx.text(font, font.plainSubstrByWidth(tag, tagWidth), textRight - tagWidth, card.y() + 4, Theme.TEXT_DIM);
				textRight -= tagWidth + 8;
			}
			int textWidth = textRight - card.x() - 8;
			String aliasTag = row.entry().alias.isEmpty() ? "" : "/" + row.entry().alias + " → ";
			if (!aliasTag.isEmpty()) {
				gfx.text(font, aliasTag, card.x() + 8, card.y() + 4, Theme.ACCENT);
			}
			int aliasWidth = font.width(aliasTag);
			gfx.text(font, font.plainSubstrByWidth("/" + row.entry().command, Math.max(0, textWidth - aliasWidth)), card.x() + 8 + aliasWidth, card.y() + 4, Theme.TEXT);
			String note = row.entry().note.isEmpty() ? "沒有備註" : row.entry().note;
			gfx.text(font, font.plainSubstrByWidth(note, textWidth), card.x() + 8, card.y() + 15, row.entry().note.isEmpty() ? Theme.TEXT_DIM : Theme.TEXT_SECONDARY);

			if (hovered) {
				Action hoveredAction = null;
				for (Action action : Action.values()) {
					Rect chip = actionChip(card, action);
					drawChip(gfx, chip, action.label, action == Action.DELETE ? Theme.DANGER : Theme.TEXT, mouseX, mouseY);
					if (chip.contains(mouseX, mouseY)) {
						hoveredAction = action;
					}
				}
				if (hoveredAction != null) {
					tooltip.add(Component.literal(hoveredAction.description));
				} else {
					tooltip.add(Component.literal("/" + row.entry().command));
					if (!row.entry().note.isEmpty()) {
						tooltip.add(Component.literal(row.entry().note).withStyle(ChatFormatting.GRAY));
					}
					tooltip.add(Component.literal(row.node().path()).withStyle(ChatFormatting.DARK_GRAY));
					tooltip.add(Component.literal("點擊執行").withStyle(ChatFormatting.AQUA));
				}
			}
		}
		gfx.endScissor();

		if (contentHeight > l.h()) {
			int barHeight = Math.max(16, l.h() * l.h() / contentHeight);
			int barY = l.y() + (int) ((l.h() - barHeight) * (listScroll / (contentHeight - l.h())));
			gfx.fill(l.x() + l.w() - 2, barY, l.x() + l.w(), barY + barHeight, Theme.CHIP_HOVER);
		}
	}

	private void drawEmptyState(Gfx gfx, Rect l) {
		List<String> lines;
		if (store.groups.stream().allMatch(group -> group.totalCommands() == 0) && search.isBlank()) {
			lines = List.of("還沒有任何指令", "按右上角「＋ 新增指令」開始建立", "或用「匯入」載入其他玩家分享的指令集");
		} else if (!search.isBlank()) {
			lines = List.of("找不到符合「" + search.trim() + "」的指令");
		} else {
			lines = List.of("這個分組還沒有指令", "按右上角「＋ 新增指令」加入");
		}
		int y = l.y() + Math.max(10, (l.h() - lines.size() * 12) / 3);
		for (int i = 0; i < lines.size(); i++) {
			String line = lines.get(i);
			gfx.text(font, line, l.x() + (l.w() - font.width(line)) / 2, y + i * 12, i == 0 ? Theme.TEXT_SECONDARY : Theme.TEXT_DIM);
		}
	}

	private void drawFooter(Gfx gfx, int mouseX, int mouseY) {
		Rect f = footer();
		gfx.fill(f.x(), f.y(), f.x() + f.w(), f.y() + 1, Theme.HEADER);
		boolean showStatus = System.currentTimeMillis() < statusUntil;
		if (showStatus) {
			gfx.text(font, status, f.x() + 8, f.y() + 4, statusColor);
			if (lastDeleted != null) {
				drawChip(gfx, undoChip(), UNDO, Theme.ACCENT, mouseX, mouseY);
			}
		}
		String hint = "點擊卡片執行 · 滾輪捲動 · Esc 返回";
		int hintX = f.x() + f.w() - 8 - font.width(hint);
		if (!showStatus || hintX > undoChip().x() + undoChip().w() + 8) {
			gfx.text(font, hint, hintX, f.y() + 4, Theme.TEXT_DIM);
		}
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (listening != null && event.button() >= InputConstants.MOUSE_BUTTON_MIDDLE) {
			CommandStore.Entry entry = listening;
			listening = null;
			InputConstants.Key key = InputConstants.Type.MOUSE.getOrCreate(event.button());
			String clash = Arrays.stream(minecraft.options.keyMappings).filter(mapping -> mapping.saveString().equals(key.getName())).findFirst()
					.map(mapping -> "（這個按鍵也用於「" + Component.translatable(mapping.getName()).getString() + "」，兩個會一起觸發）").orElse("");
			finishBinding(entry, key, clash);
			return true;
		}
		listening = null;
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
			onClose();
			return true;
		}
		if (lastDeleted != null && System.currentTimeMillis() < statusUntil && undoChip().contains(mouseX, mouseY)) {
			undo();
			return true;
		}
		if (addGroupRow().contains(mouseX, mouseY)) {
			openGroupDialog(null, null);
			return true;
		}
		if (addCommandChip().contains(mouseX, mouseY)) {
			openCommandDialog(null);
			return true;
		}
		if (groupArea().contains(mouseX, mouseY)) {
			clickGroup(mouseX, mouseY);
			return true;
		}
		if (list().contains(mouseX, mouseY)) {
			List<Row> rows = rows();
			for (int i = 0; i < rows.size(); i++) {
				Rect card = card(i);
				if (!card.contains(mouseX, mouseY)) {
					continue;
				}
				Row row = rows.get(i);
				if (actionChip(card, Action.KEY).contains(mouseX, mouseY)) {
					listening = row.entry();
					searchBox.setFocused(false);
					setFocused(null);
					setStatus("按下要綁定的按鍵或滑鼠中鍵、側鍵，可同時按住 Ctrl／Alt／Shift；Esc 取消、Backspace 清除", Theme.ACCENT);
				} else if (actionChip(card, Action.FILL).contains(mouseX, mouseY)) {
					fill(row.entry().command);
				} else if (actionChip(card, Action.EDIT).contains(mouseX, mouseY)) {
					openCommandDialog(row);
				} else if (actionChip(card, Action.DELETE).contains(mouseX, mouseY)) {
					deleteCommand(row);
				} else {
					run(row.entry().command);
				}
				return true;
			}
		}
		return true;
	}

	private void clickGroup(double mouseX, double mouseY) {
		List<CommandStore.Node> nodes = visibleNodes();
		for (int position = 0; position <= nodes.size(); position++) {
			Rect row = groupRow(position);
			if (!row.contains(mouseX, mouseY)) {
				continue;
			}
			if (position == 0) {
				select(null);
				return;
			}
			CommandStore.Node node = nodes.get(position - 1);
			if (!node.group().children.isEmpty() && arrow(row, node.depth()).contains(mouseX, mouseY)) {
				node.group().collapsed = !node.group().collapsed;
				return;
			}
			List<GroupIcon> icons = icons(node);
			for (int slot = 0; slot < icons.size(); slot++) {
				if (groupIcon(row, slot).contains(mouseX, mouseY)) {
					switch (icons.get(slot)) {
						case DELETE -> deleteGroup(node);
						case RENAME -> openGroupDialog(node.group(), null);
						case ADD_CHILD -> openGroupDialog(null, node.group());
					}
					return;
				}
			}
			select(node.group());
			return;
		}
	}

	private void select(CommandStore.Group group) {
		selected = group;
		listScroll = 0;
		searchBox.setValue("");
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		if (list().contains(mouseX, mouseY)) {
			listScroll -= scrollY * (CARD + CARD_GAP);
			return true;
		}
		if (groupArea().contains(mouseX, mouseY)) {
			groupScroll -= scrollY * GROUP_ROW;
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (listening != null) {
			bindListening(event);
			return true;
		}
		if (event.isConfirmation() && searchBox.isFocused() && !search.isBlank()) {
			List<Row> rows = rows();
			if (!rows.isEmpty()) {
				run(rows.getFirst().entry().command);
			}
			return true;
		}
		return super.keyPressed(event);
	}

	private void bindListening(KeyEvent event) {
		if (event.isEscape()) {
			listening = null;
			setStatus("已取消綁定", Theme.TEXT_DIM);
			return;
		}
		if (KeyCombos.isModifier(event.key())) {
			return;
		}
		CommandStore.Entry entry = listening;
		listening = null;
		if (event.key() == InputConstants.KEY_BACKSPACE || event.key() == InputConstants.KEY_DELETE) {
			entry.key = "";
			entry.mods = 0;
			store.save();
			setStatus("已清除「/" + entry.command + "」的按鍵", Theme.SUCCESS);
			return;
		}
		String clash = Arrays.stream(minecraft.options.keyMappings).filter(mapping -> mapping.matches(event)).findFirst()
				.map(mapping -> "（這個按鍵也用於「" + Component.translatable(mapping.getName()).getString() + "」，兩個會一起觸發）").orElse("");
		finishBinding(entry, InputConstants.getKey(event), clash);
	}

	private void finishBinding(CommandStore.Entry entry, InputConstants.Key key, String clash) {
		entry.key = key.getName();
		entry.mods = KeyCombos.held();
		store.save();
		setStatus("「/" + entry.command + "」綁定為 " + CommandKeys.label(entry) + clash, clash.isEmpty() ? Theme.SUCCESS : Theme.WARNING);
	}

	private void setStatus(String message, int color) {
		status = message;
		statusColor = color;
		statusUntil = System.currentTimeMillis() + 6000;
		lastDeleted = null;
	}

	private void run(String command) {
		if (minecraft.player != null) {
			minecraft.player.connection.sendCommand(command);
		}
		setScreen(null);
	}

	private void fill(String command) {
		setScreen(new ChatScreen("/" + command + " ", false));
	}

	private void openCommandDialog(Row row) {
		List<CommandStore.Node> nodes = allNodes();
		List<String> paths = nodes.isEmpty() ? List.of("未分組") : nodes.stream().map(CommandStore.Node::path).toList();
		CommandStore.Group preferred = row != null ? row.node().group() : selected;
		int groupIndex = Math.max(0, nodes.stream().map(CommandStore.Node::group).toList().indexOf(preferred));
		List<EditDialog.Field> fields = List.of(
				new EditDialog.Field("指令", "例如 home 或 /warp shop", row != null ? row.entry().command : "", CommandStore.MAX_COMMAND),
				new EditDialog.Field("備註", "說明這個指令的用途（選填）", row != null ? row.entry().note : "", CommandStore.MAX_NOTE),
				new EditDialog.Field("別名（選填）", "例如 h：在聊天欄輸入 /h 就會執行這個指令", row != null ? row.entry().alias : "", CommandAliases.MAX_ALIAS));
		setScreen(new EditDialog(this, row != null ? "編輯指令" : "新增指令", fields, paths, groupIndex, (values, index) -> {
			String command = CommandStore.cleanCommand(values.get(0));
			if (command.isEmpty()) {
				return "請輸入指令";
			}
			CommandStore.Group target;
			if (nodes.isEmpty()) {
				target = new CommandStore.Group("未分組");
				store.groups.add(target);
			} else {
				target = nodes.get(index).group();
			}
			String alias = CommandAliases.clean(values.get(2));
			String previousAlias = row != null ? row.entry().alias : "";
			if (!alias.isEmpty() && !alias.equals(previousAlias) && CommandAliases.all().containsKey(alias)) {
				return "別名 /" + alias + " 已經被其他指令使用了";
			}
			if (!alias.isEmpty() && CommandAliases.serverCommand(alias) != null) {
				return "伺服器已經有 /" + alias + " 指令，請換一個別名";
			}
			if (alias.equalsIgnoreCase(command.split(" ", 2)[0])) {
				return "別名不能跟指令本身一樣";
			}
			CommandStore.Entry entry = new CommandStore.Entry(command, CommandStore.cleanText(values.get(1), CommandStore.MAX_NOTE));
			entry.alias = alias;
			if (row == null) {
				target.commands.add(entry);
			} else if (row.node().group() == target) {
				target.commands.set(row.index(), entry);
			} else {
				row.node().group().commands.remove(row.index());
				target.commands.add(entry);
			}
			store.save();
			CommandAliases.install();
			setStatus((row == null ? "已新增 /" : "已更新 /") + command + (alias.isEmpty() ? "" : "，別名 /" + alias), Theme.SUCCESS);
			return null;
		}).command(0));
	}

	private void openGroupDialog(CommandStore.Group editing, CommandStore.Group parentGroup) {
		String title = editing != null ? "重新命名分組" : parentGroup != null ? "在「" + parentGroup.name + "」下新增子分組" : "新增分組";
		List<EditDialog.Field> fields = List.of(new EditDialog.Field("分組名稱", "例如 傳送、商店、常用", editing != null ? editing.name : "", CommandStore.MAX_NAME));
		setScreen(new EditDialog(this, title, fields, null, 0, (values, index) -> {
			String name = CommandStore.cleanText(values.get(0), CommandStore.MAX_NAME);
			if (name.isEmpty()) {
				return "請輸入分組名稱";
			}
			if (editing != null) {
				editing.name = name;
				setStatus("已重新命名為「" + name + "」", Theme.SUCCESS);
			} else {
				CommandStore.Group group = new CommandStore.Group(name);
				if (parentGroup != null) {
					parentGroup.children.add(group);
					parentGroup.collapsed = false;
				} else {
					store.groups.add(group);
				}
				selected = group;
				listScroll = 0;
				search = "";
				setStatus("已新增分組「" + name + "」", Theme.SUCCESS);
			}
			store.save();
			return null;
		}));
	}

	private void deleteGroup(CommandStore.Node node) {
		CommandStore.Group group = node.group();
		int children = group.totalChildren();
		String detail = (children > 0 ? children + " 個子分組、" : "") + group.totalCommands() + " 個指令";
		setScreen(new ConfirmScreen(confirmed -> {
			if (confirmed) {
				(node.parent() == null ? store.groups : node.parent().children).remove(group);
				store.save();
				if (selected != null && nodeOf(selected) == null) {
					selected = null;
				}
				listScroll = 0;
				setStatus("已刪除分組「" + group.name + "」", Theme.WARNING);
			}
			setScreen(this);
		}, Component.literal("刪除分組「" + node.path() + "」"), Component.literal("其中的 " + detail + "會一併刪除，且無法復原。")));
	}

	private void deleteCommand(Row row) {
		row.node().group().commands.remove(row.index());
		store.save();
		setStatus("已刪除 /" + row.entry().command, Theme.WARNING);
		lastDeleted = new Deleted(row.node().group(), row.index(), row.entry());
	}

	private void undo() {
		Deleted deleted = lastDeleted;
		if (nodeOf(deleted.group()) != null) {
			deleted.group().commands.add(Math.min(deleted.index(), deleted.group().commands.size()), deleted.entry());
			store.save();
			setStatus("已復原 /" + deleted.entry().command, Theme.SUCCESS);
		} else {
			setStatus("原分組已不存在，無法復原", Theme.DANGER);
		}
	}

	private void exportAll() {
		int count = store.groups.stream().mapToInt(CommandStore.Group::totalCommands).sum();
		if (count == 0) {
			setStatus("沒有可匯出的指令", Theme.DANGER);
			return;
		}
		minecraft.keyboardHandler.setClipboard(CommandStore.export(store.groups));
		setStatus("已複製 " + allNodes().size() + " 個分組、" + count + " 個指令到剪貼簿", Theme.SUCCESS);
	}

	@Override
	public void onClose() {
		setScreen(parent);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
