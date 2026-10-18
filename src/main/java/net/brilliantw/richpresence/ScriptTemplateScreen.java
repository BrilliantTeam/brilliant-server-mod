package net.brilliantw.richpresence;

import java.util.function.Consumer;
//? if >=26.1 {
import net.minecraft.client.gui.GuiGraphicsExtractor;
//?} else {
/*import net.minecraft.client.gui.GuiGraphics;
*///?}
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

public final class ScriptTemplateScreen extends Screen {
	private static final int HEADER = 22;
	private static final int ROW = 30;

	private final Screen parent;
	private final Consumer<String> onImported;
	private double scroll;

	public ScriptTemplateScreen(Screen parent, Consumer<String> onImported) {
		super(Component.literal("範本庫"));
		this.parent = parent;
		this.onImported = onImported;
	}

	private int panelWidth() {
		return Math.min(width - 16, 360);
	}

	private int left() {
		return (width - panelWidth()) / 2;
	}

	private int listTop() {
		return 8 + HEADER + 18;
	}

	private int listBottom() {
		return height - 12;
	}

	private int rowAt(double mx, double my) {
		if (mx < left() + 8 || mx >= left() + panelWidth() - 8 || my < listTop() || my >= listBottom()) {
			return -1;
		}
		int index = (int) ((my - listTop() + scroll) / ROW);
		return index >= 0 && index < ScriptTemplates.ALL.size() ? index : -1;
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
		int x = left();
		int w = panelWidth();
		gfx.fill(x, 8, x + w, height - 8, Theme.PANEL);
		Theme.watermark(gfx, x, 8, w, height - 16);
		gfx.fill(x, 8, x + w, 8 + HEADER, Theme.HEADER);
		gfx.fill(x, 8 + HEADER - 1, x + w, 8 + HEADER, Theme.ACCENT_DIM);
		Theme.drawBack(gfx, font, x, 8, mouseX, mouseY);
		gfx.text(font, title.getString(), Theme.titleX(font, x), 15, Theme.ACCENT);
		gfx.text(font, "選一個範本預覽，匯入後會放在新的分組，可以再自由修改", x + 10, 8 + HEADER + 5, Theme.TEXT_DIM);
		int contentHeight = ScriptTemplates.ALL.size() * ROW;
		scroll = Math.clamp(scroll, 0, Math.max(0, contentHeight - (listBottom() - listTop())));
		int hovered = rowAt(mouseX, mouseY);
		gfx.scissor(x, listTop(), x + w, listBottom());
		for (int i = 0; i < ScriptTemplates.ALL.size(); i++) {
			ScriptTemplates.Template template = ScriptTemplates.ALL.get(i);
			int y = listTop() + i * ROW - (int) scroll;
			gfx.fill(x + 8, y, x + w - 8, y + ROW - 3, i == hovered ? Theme.CHIP_HOVER : Theme.SIDEBAR);
			gfx.fill(x + 8, y, x + 10, y + ROW - 3, 0xFFFFAB19);
			gfx.text(font, template.name(), x + 16, y + 4, Theme.TEXT);
			gfx.text(font, font.plainSubstrByWidth(template.description(), w - 32), x + 16, y + 15, Theme.TEXT_DIM);
		}
		gfx.endScissor();
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (Theme.backHit(font, left(), 8, event.x(), event.y())) {
			onClose();
			return true;
		}
		int index = rowAt(event.x(), event.y());
		if (index >= 0) {
			ScriptTemplates.Template template = ScriptTemplates.ALL.get(index);
			CommandMenuScreen.setScreen(new ScriptImportScreen(parent, onImported, template.code(), "範本：" + template.name()));
			return true;
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		scroll -= scrollY * ROW;
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
