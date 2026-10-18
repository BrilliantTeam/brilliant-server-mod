package net.brilliantw.richpresence;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
//? if >=26.1 {
import net.minecraft.client.gui.GuiGraphicsExtractor;
//?} else {
/*import net.minecraft.client.gui.GuiGraphics;
*///?}
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class ScriptImportScreen extends Screen {
	private static final int HEADER = 22;
	private static final float SCALE = 0.75f;

	private final Screen parent;
	private final Consumer<String> onImported;
	private String code;
	private Scripts.Data parsed;
	private String error = "";
	private double scroll;
	private Button importButton;
	private EditBox input;
	private ScriptView view;

	public ScriptImportScreen(Screen parent, Consumer<String> onImported) {
		this(parent, onImported, clipboardCode(), "匯入腳本");
	}

	public ScriptImportScreen(Screen parent, Consumer<String> onImported, String code, String title) {
		super(Component.literal(title));
		this.parent = parent;
		this.onImported = onImported;
		this.code = code;
		update();
	}

	private static String clipboardCode() {
		String clipboard = net.minecraft.client.Minecraft.getInstance().keyboardHandler.getClipboard();
		return clipboard != null && clipboard.strip().startsWith(Scripts.EXPORT_PREFIX) ? clipboard.strip() : "";
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
		return top() + HEADER + 62;
	}

	private int previewBottom() {
		return top() + panelHeight() - 32;
	}

	private int incomingBlocks() {
		return parsed == null ? 0 : parsed.groups.stream().mapToInt(group -> group.stacks.stream().mapToInt(stack -> Scripts.count(stack.blocks)).sum()).sum();
	}

	private void update() {
		scroll = 0;
		try {
			parsed = code.isBlank() ? null : Scripts.parse(code);
			error = "";
		} catch (IllegalArgumentException e) {
			parsed = null;
			error = e.getMessage();
		}
		if (importButton != null) {
			importButton.active = canImport();
			importButton.setMessage(importLabel());
		}
	}

	private boolean canImport() {
		return parsed != null && Scripts.fitProblem(parsed) == null;
	}

	private Component importLabel() {
		if (parsed == null) {
			return Component.literal("沒有可匯入的腳本");
		}
		if (!canImport()) {
			return Component.literal("空間不足，無法匯入").withStyle(ChatFormatting.RED);
		}
		return Component.literal("匯入 " + parsed.groups.size() + " 個分組").withStyle(ChatFormatting.GREEN);
	}

	@Override
	protected void init() {
		view = new ScriptView(font);
		view.animate = false;
		int x = left() + 10;
		int w = panelWidth() - 20;
		int pasteWidth = 64;
		int fileWidth = 64;
		input = addRenderableWidget(new EditBox(font, x, top() + HEADER + 16, w - pasteWidth - fileWidth - 8, 18, Component.literal("腳本代碼")));
		input.setMaxLength(Integer.MAX_VALUE);
		Theme.hint(input, "在這裡按 Ctrl+V 貼上 " + Scripts.EXPORT_PREFIX + "… 代碼");
		input.setValue(code);
		input.setResponder(value -> {
			code = value;
			update();
		});
		addRenderableWidget(Button.builder(Component.literal("貼上剪貼簿"), b -> setCode(minecraft.keyboardHandler.getClipboard()))
				.bounds(x + w - pasteWidth - fileWidth - 4, top() + HEADER + 15, pasteWidth, 20).build());
		addRenderableWidget(Button.builder(Component.literal("選擇檔案"), b -> FilePicker.openTextFile("選擇腳本 .txt 檔", this::loadFile))
				.bounds(x + w - fileWidth, top() + HEADER + 15, fileWidth, 20)
				.tooltip(Tooltip.create(Component.literal("從 .txt 檔匯入，例如 Discord 自動轉成的 message.txt\n也可以直接把檔案拖進遊戲視窗")))
				.build());

		int buttonY = top() + panelHeight() - 26;
		int buttonWidth = (w - 6) / 2;
		addRenderableWidget(Button.builder(Component.literal("取消"), b -> onClose()).bounds(x, buttonY, buttonWidth, 20).build());
		importButton = addRenderableWidget(Button.builder(importLabel(), b -> confirm()).bounds(x + buttonWidth + 6, buttonY, buttonWidth, 20).build());
		importButton.active = canImport();
		setInitialFocus(input);
	}

	private void setCode(String text) {
		if (text == null) {
			return;
		}
		String trimmed = text.replace("﻿", "").strip();
		input.setValue(trimmed.startsWith(Scripts.EXPORT_PREFIX) ? trimmed.replaceAll("\\s", "") : trimmed.replaceAll("[\\r\\n]+", " "));
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

	private void confirm() {
		if (!canImport()) {
			return;
		}
		int added = Scripts.merge(parsed);
		Scripts.save();
		onImported.accept("已匯入 " + added + " 塊積木，放在新的分組");
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
		gfx.fill(x, y, x + w, y + panelHeight(), Theme.PANEL);
		Theme.watermark(gfx, x, y, w, panelHeight());
		gfx.fill(x, y, x + w, y + HEADER, Theme.HEADER);
		gfx.fill(x, y + HEADER - 1, x + w, y + HEADER, Theme.ACCENT_DIM);
		gfx.text(font, title.getString(), x + 10, y + 7, Theme.ACCENT);

		gfx.text(font, "腳本代碼", x + 10, y + HEADER + 5, Theme.TEXT_SECONDARY);
		String dropHint = "也可以把 .txt 檔拖進視窗";
		gfx.text(font, dropHint, x + w - 10 - font.width(dropHint), y + HEADER + 5, Theme.TEXT_DIM);

		int summaryY = previewTop() - 14;
		if (!error.isEmpty()) {
			gfx.text(font, font.plainSubstrByWidth(error, w - 20), x + 10, summaryY, Theme.DANGER);
		} else if (parsed == null) {
			gfx.text(font, "貼上代碼或選擇檔案後，會在這裡預覽積木", x + 10, summaryY, Theme.TEXT_DIM);
		} else {
			gfx.text(font, font.plainSubstrByWidth(summary(), w - 20), x + 10, summaryY, canImport() ? Theme.TEXT : Theme.DANGER);
		}

		int top = previewTop();
		int bottom = previewBottom();
		gfx.fill(x + 10, top, x + w - 10, bottom, Theme.SIDEBAR);
		if (parsed == null) {
			return;
		}
		scroll = Math.clamp(scroll, 0, Math.max(0, preview(gfx, x, 0, false) - (bottom - top - 8)));
		gfx.scissor(x + 10, top, x + w - 10, bottom);
		preview(gfx, x, top + 4 - (int) scroll, true);
		gfx.endScissor();
	}

	private int preview(Gfx gfx, int x, int start, boolean draw) {
		int ly = start;
		for (Scripts.Group group : parsed.groups) {
			int blocks = group.stacks.stream().mapToInt(stack -> Scripts.count(stack.blocks)).sum();
			Map<Scripts.Block, String> issues = ScriptLint.check(group.stacks);
			if (draw) {
				gfx.text(font, group.name, x + 16, ly, Theme.ACCENT);
				String info = "  " + group.stacks.size() + " 個腳本 · " + blocks + " 塊積木" + (issues.isEmpty() ? "" : " · ⚠ " + issues.size() + " 個可能的問題");
				gfx.text(font, info, x + 16 + font.width(group.name), ly, issues.isEmpty() ? Theme.TEXT_DIM : ScriptView.WARNING);
			}
			ly += 14;
			view.issues = issues;
			for (Scripts.Stack stack : group.stacks) {
				ScriptView.Laid laid = view.layoutList(stack.blocks, 0, 0);
				if (draw) {
					gfx.push(x + 20, ly, SCALE);
					for (ScriptView.Node node : laid.nodes()) {
						view.drawNode(gfx, node);
					}
					gfx.pop();
				}
				ly += (int) Math.ceil((laid.height() + ScriptView.NOTCH_H) * SCALE) + 6;
			}
			ly += 6;
		}
		if (!parsed.variables.isEmpty()) {
			if (draw) {
				gfx.text(font, font.plainSubstrByWidth("變數：" + String.join("、", parsed.variables), panelWidth() - 32), x + 16, ly, 0xFFFF8C1A);
			}
			ly += 12;
		}
		return ly - start;
	}

	private String summary() {
		int blocks = incomingBlocks();
		long newVariables = parsed.variables.stream().filter(name -> !Scripts.data().variables.contains(name)).count();
		String text = "共 " + parsed.groups.size() + " 個分組、" + blocks + " 塊積木" + (newVariables > 0 ? "、新增 " + newVariables + " 個變數" : "") + "，會加在新的分組";
		String problem = Scripts.fitProblem(parsed);
		return problem != null ? problem : text;
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		if (mouseY >= previewTop() && mouseY < previewBottom()) {
			scroll -= scrollY * 30;
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
