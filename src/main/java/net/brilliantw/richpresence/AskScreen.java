package net.brilliantw.richpresence;

import java.util.List;
import java.util.function.Consumer;
//? if >=26.1 {
import net.minecraft.client.gui.GuiGraphicsExtractor;
//?} else {
/*import net.minecraft.client.gui.GuiGraphics;
*///?}
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;

public final class AskScreen extends Screen {
	private static final int WIDTH = 260;

	private final String question;
	private final List<String> options;
	private final Consumer<String> answer;
	private EditBox input;
	private boolean answered;

	public AskScreen(String question, List<String> options, Consumer<String> answer) {
		super(Component.literal("腳本詢問"));
		this.question = question;
		this.options = options;
		this.answer = answer;
	}

	private int panelHeight() {
		return 54 + (options == null ? 30 : ((options.size() + 1) / 2) * 24) + 4;
	}

	private int left() {
		return (width - WIDTH) / 2;
	}

	private int top() {
		return (height - panelHeight()) / 2;
	}

	@Override
	protected void init() {
		int x = left() + 10;
		int y = top() + 40;
		if (options == null) {
			input = addRenderableWidget(new EditBox(font, x, y, WIDTH - 20 - 56, 18, Component.literal("回答")));
			input.setMaxLength(Scripts.MAX_TEXT);
			Theme.hint(input, "輸入回答後按 Enter");
			addRenderableWidget(Button.builder(Component.literal("確定"), b -> submit(input.getValue())).bounds(x + WIDTH - 20 - 52, y - 1, 52, 20).build());
			setInitialFocus(input);
			return;
		}
		int buttonWidth = (WIDTH - 26) / 2;
		for (int i = 0; i < options.size(); i++) {
			String option = options.get(i);
			addRenderableWidget(Button.builder(Component.literal(option), b -> submit(option))
					.bounds(x + (i % 2) * (buttonWidth + 6), y + (i / 2) * 24, buttonWidth, 20).build());
		}
	}

	private void submit(String value) {
		if (answered) {
			return;
		}
		answered = true;
		answer.accept(value == null ? "" : value.strip());
		CommandMenuScreen.setScreen(null);
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (options == null && event.isConfirmation()) {
			submit(input.getValue());
			return true;
		}
		return super.keyPressed(event);
	}

	@Override
	public void removed() {
		if (!answered) {
			answered = true;
			answer.accept("");
		}
		super.removed();
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
		gfx.fill(x - 1, y - 1, x + WIDTH + 1, y + panelHeight() + 1, 0xFFFFAB19);
		gfx.fill(x, y, x + WIDTH, y + panelHeight(), Theme.PANEL);
		gfx.fill(x, y, x + WIDTH, y + 20, Theme.HEADER);
		gfx.label(font, options == null ? "腳本想問你" : "腳本請你選一個", x + 8, y, 20, Theme.ACCENT);
		gfx.text(font, font.plainSubstrByWidth(question, WIDTH - 16), x + 8, y + 26, Theme.TEXT);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
