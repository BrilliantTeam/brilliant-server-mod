package net.brilliantw.richpresence;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
//? if >=26.1 {
import net.minecraft.client.gui.GuiGraphicsExtractor;
//?} else {
/*import net.minecraft.client.gui.GuiGraphics;
*///?}
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class ImportScreen extends Screen {
	private static final int HEADER = Theme.HEADER_H;
	private static final int LINE = 11;

	private final Screen parent;
	private final CommandStore store = CommandStore.get();
	private final Consumer<String> onImported;
	private String code;
	private boolean replace;
	private List<CommandStore.Group> parsed = List.of();
	private List<CommandStore.PreviewLine> preview = List.of();
	private String error = "";
	private double scroll;
	private Button importButton;
	private EditBox input;

	public ImportScreen(Screen parent, Consumer<String> onImported) {
		super(Component.literal("匯入指令集"));
		this.parent = parent;
		this.onImported = onImported;
		String clipboard = net.minecraft.client.Minecraft.getInstance().keyboardHandler.getClipboard();
		this.code = CommandStore.looksLikeCode(clipboard) ? clipboard.trim() : "";
		update();
	}

	private int panelWidth() {
		return Math.min(width - 16, 440);
	}

	private int left() {
		return (width - panelWidth()) / 2;
	}

	private int top() {
		return 8;
	}

	private int panelHeight() {
		return height - 16;
	}

	private int previewTop() {
		return top() + HEADER + 82;
	}

	private int previewBottom() {
		return top() + panelHeight() - 32;
	}

	private long addedCount() {
		return preview.stream().filter(CommandStore.PreviewLine::added).count();
	}

	private int existingCount() {
		return store.groups.stream().mapToInt(CommandStore.Group::totalCommands).sum();
	}

	private void update() {
		scroll = 0;
		try {
			parsed = CommandStore.parse(code);
			preview = CommandStore.preview(replace ? List.of() : store.groups, parsed);
			error = "";
		} catch (IllegalArgumentException e) {
			parsed = List.of();
			preview = List.of();
			error = code.isBlank() ? "" : e.getMessage();
		}
		if (importButton != null) {
			importButton.active = addedCount() > 0;
			importButton.setMessage(importLabel());
		}
	}

	private Component importLabel() {
		long count = addedCount();
		if (count == 0) {
			return Component.literal("沒有可匯入的指令");
		}
		return Component.literal((replace ? "覆蓋並匯入 " : "合併匯入 ") + count + " 個指令").withStyle(replace ? ChatFormatting.RED : ChatFormatting.GREEN);
	}

	private Component modeLabel(boolean replaceMode) {
		String label = replaceMode ? "覆蓋" : "合併";
		return replace == replaceMode ? Component.literal("【" + label + "】").withStyle(ChatFormatting.AQUA) : Component.literal(label).withStyle(ChatFormatting.GRAY);
	}

	@Override
	protected void init() {
		int x = left() + 10;
		int w = panelWidth() - 20;
		int pasteWidth = 64;
		int fileWidth = 64;
		input = addRenderableWidget(new EditBox(font, x, top() + HEADER + 16, w - pasteWidth - fileWidth - 8, 18, Component.literal("指令集代碼")));
		input.setMaxLength(Integer.MAX_VALUE);
		Theme.hint(input, "在這裡按 Ctrl+V 貼上 BSM-CMD:… 代碼");
		input.setValue(code);
		input.setResponder(value -> {
			code = value;
			update();
		});
		addRenderableWidget(Theme.button(Component.literal("貼上剪貼簿"), b -> setCode(minecraft.keyboardHandler.getClipboard()), x + w - pasteWidth - fileWidth - 4, top() + HEADER + 15, pasteWidth, 20));
		addRenderableWidget(Theme.button(Component.literal("選擇檔案"), b -> FilePicker.openTextFile("選擇指令集 .txt 檔", this::loadFile), x + w - fileWidth, top() + HEADER + 15, fileWidth, 20).tip("從 .txt 檔匯入，例如 Discord 自動轉成的 message.txt\n也可以直接把檔案拖進遊戲視窗"));

		int modeY = top() + HEADER + 40;
		int modeWidth = 60;
		addRenderableWidget(Theme.button(modeLabel(false), b -> setReplace(false), x + 34, modeY, modeWidth, 20).tip("保留現有指令\n同名分組會合併，重複的指令會略過"));
		addRenderableWidget(Theme.button(modeLabel(true), b -> setReplace(true), x + 34 + modeWidth + 4, modeY, modeWidth, 20).tip("刪除現有的全部指令，換成匯入的內容\n覆蓋前會自動備份舊資料"));

		int buttonY = top() + panelHeight() - 26;
		int buttonWidth = (w - 6) / 2;
		addRenderableWidget(Theme.button(Component.literal("取消"), b -> onClose(), x, buttonY, buttonWidth, 20));
		importButton = addRenderableWidget(Theme.button(importLabel(), b -> confirm(), x + buttonWidth + 6, buttonY, buttonWidth, 20).primary());
		importButton.active = addedCount() > 0;
		setInitialFocus(input);
	}

	private void setCode(String text) {
		if (text == null) {
			return;
		}
		String trimmed = text.replace("﻿", "").trim();
		input.setValue(CommandStore.looksLikeCode(trimmed) ? trimmed.replaceAll("\\s", "") : trimmed.replaceAll("[\\r\\n]+", " "));
	}

	private void loadFile(Path path) {
		try {
			setCode(FilePicker.readText(path));
			if (code.isBlank()) {
				error = "檔案是空的：" + path.getFileName();
			}
		} catch (IOException e) {
			error = "無法讀取 " + path.getFileName() + "：" + e.getMessage();
		}
	}

	@Override
	public void onFilesDrop(List<Path> paths) {
		if (!paths.isEmpty()) {
			loadFile(paths.getFirst());
		}
	}

	private void setReplace(boolean value) {
		replace = value;
		update();
		rebuildWidgets();
	}

	private void confirm() {
		if (!replace) {
			int added = CommandStore.merge(store.groups, parsed);
			store.save();
			finish("已匯入 " + added + " 個指令");
			return;
		}
		CommandMenuScreen.setScreen(new ConfirmScreen(confirmed -> {
			if (!confirmed) {
				CommandMenuScreen.setScreen(this);
				return;
			}
			try {
				String backup = store.replaceAll(parsed);
				finish("已覆蓋為 " + addedCount() + " 個指令" + (backup != null ? "，舊資料備份為 " + backup : ""));
			} catch (IllegalStateException e) {
				error = e.getMessage();
				CommandMenuScreen.setScreen(this);
			}
		}, Component.literal("確定要覆蓋全部指令？"),
				Component.literal("你現有的 " + CommandStore.flatten(store.groups, false).size() + " 個分組、" + existingCount() + " 個指令會被刪除，換成匯入的 " + addedCount() + " 個指令。\n舊資料會先自動備份到 config 資料夾。")));
	}

	private void finish(String message) {
		onImported.accept(message);
		CommandMenuScreen.setScreen(parent);
	}

	//? if >=26.1 {
	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		draw(new Gfx(graphics));
		super.extractRenderState(graphics, mouseX, mouseY, partialTick);
	}
	//?} else {
	/*@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		draw(new Gfx(graphics));
		super.render(graphics, mouseX, mouseY, partialTick);
	}
	*///?}

	private void draw(Gfx gfx) {
		int x = left();
		int y = top();
		int w = panelWidth();
		Theme.panel(gfx, x, y, x + w, y + panelHeight());
		Theme.header(gfx, x, y, w);
		gfx.text(font, title.getString(), x + 10, y + 9, Theme.ACCENT);

		gfx.text(font, "指令集代碼", x + 10, y + HEADER + 5, Theme.TEXT_SECONDARY);
		String dropHint = "也可以把 .txt 檔拖進視窗";
		gfx.text(font, dropHint, x + w - 10 - font.width(dropHint), y + HEADER + 5, Theme.TEXT_DIM);
		gfx.text(font, "方式", x + 10, y + HEADER + 46, Theme.TEXT_SECONDARY);

		int summaryY = previewTop() - 14;
		if (!error.isEmpty()) {
			gfx.text(font, error, x + 10, summaryY, Theme.DANGER);
		} else if (preview.isEmpty()) {
			gfx.text(font, "貼上代碼後會在這裡預覽內容", x + 10, summaryY, Theme.TEXT_DIM);
		} else {
			gfx.text(font, Gfx.fit(font, summary(), w - 20), x + 10, summaryY, replace ? Theme.DANGER : Theme.TEXT);
		}

		int top = previewTop();
		int bottom = previewBottom();
		gfx.fill(x + 10, top, x + w - 10, bottom, Theme.SIDEBAR);
		int contentHeight = previewHeight();
		scroll = Math.clamp(scroll, 0, Math.max(0, contentHeight - (bottom - top - 8)));
		gfx.scissor(x + 10, top, x + w - 10, bottom);
		int ly = top + 4 - (int) scroll;
		String currentGroup = null;
		for (CommandStore.PreviewLine line : preview) {
			if (!line.group().equals(currentGroup)) {
				currentGroup = line.group();
				if (ly != top + 4 - (int) scroll) {
					ly += 4;
				}
				String tag = line.newGroup() ? "  新分組" : "  合併到現有分組";
				gfx.text(font, line.group(), x + 16, ly, Theme.ACCENT);
				gfx.text(font, tag, x + 16 + font.width(line.group()), ly, Theme.TEXT_DIM);
				ly += LINE + 1;
			}
			boolean added = line.added();
			String mark = added ? "＋ " : "－ ";
			String command = "/" + line.entry().command;
			int textX = x + 24;
			int maxWidth = w - 48;
			gfx.text(font, mark + Gfx.fit(font, command, maxWidth / 2), textX, ly, added ? Theme.SUCCESS : Theme.TEXT_DIM);
			int noteX = textX + font.width(mark) + Math.min(font.width(command), maxWidth / 2) + 8;
			String suffix = added ? line.entry().note : "已存在，略過";
			if (!suffix.isEmpty()) {
				gfx.text(font, Gfx.fit(font, suffix, x + w - 16 - noteX), noteX, ly, Theme.TEXT_DIM);
			}
			ly += LINE;
		}
		gfx.endScissor();
	}

	private String summary() {
		long skipped = preview.size() - addedCount();
		long groups = preview.stream().map(CommandStore.PreviewLine::group).distinct().count();
		if (replace) {
			return "將刪除現有 " + existingCount() + " 個指令，換成 " + groups + " 個分組、" + addedCount() + " 個指令";
		}
		return "共 " + groups + " 個分組：新增 " + addedCount() + " 個指令" + (skipped > 0 ? "，略過 " + skipped + " 個重複" : "");
	}

	private int previewHeight() {
		long groups = preview.stream().map(CommandStore.PreviewLine::group).distinct().count();
		return (int) (preview.size() * LINE + groups * (LINE + 5));
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		if (mouseY >= previewTop() && mouseY < previewBottom()) {
			scroll -= scrollY * LINE * 3;
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
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
