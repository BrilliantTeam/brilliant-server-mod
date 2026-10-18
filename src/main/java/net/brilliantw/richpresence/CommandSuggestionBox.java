package net.brilliantw.richpresence;

import net.minecraft.client.Minecraft;
//? if >=26.1 {
import net.minecraft.client.gui.GuiGraphicsExtractor;
//?} else {
/*import net.minecraft.client.gui.GuiGraphics;
*///?}
import net.minecraft.client.gui.components.CommandSuggestions;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;

public final class CommandSuggestionBox {
	private static final int VANILLA_TOP = 72;
	private static final int LINES = 7;
	private static final int LIST_HEIGHT = LINES * 12 + 2;

	private final Screen screen;
	private final EditBox input;
	private final CommandSuggestions suggestions;
	private final int baseWidth;

	public CommandSuggestionBox(Screen screen, EditBox input) {
		this.screen = screen;
		this.input = input;
		this.baseWidth = input.getWidth();
		this.suggestions = new CommandSuggestions(Minecraft.getInstance(), screen, input, Minecraft.getInstance().font, true, false, 0, LINES, false, 0xD0000000);
		this.suggestions.setAllowSuggestions(true);
		this.suggestions.updateCommandInfo();
		clampHint();
	}

	public void update() {
		suggestions.updateCommandInfo();
		clampHint();
	}

	private void clampHint() {
		String hint = ((net.brilliantw.richpresence.mixin.EditBoxAccessor) input).brilliant$suggestion();
		int padding = input.getWidth() - input.getInnerWidth() + 2;
		int needed = Minecraft.getInstance().font.width(input.getValue() + (hint == null ? "" : hint)) + padding;
		int room = Math.max(baseWidth, screen.width - input.getX() - 4);
		int width = Math.clamp(needed, baseWidth, room);
		if (width != input.getWidth()) {
			input.setWidth(width);
			input.setCursorPosition(input.getCursorPosition());
		}
		if (hint != null && !hint.isEmpty() && needed > width) {
			input.setSuggestion(null);
		}
	}

	private int offset() {
		int below = input.getY() + input.getHeight() + 3;
		if (below + LIST_HEIGHT > screen.height && input.getY() - LIST_HEIGHT - 3 >= 0) {
			return input.getY() - LIST_HEIGHT - 3 - VANILLA_TOP;
		}
		return below - VANILLA_TOP;
	}

	//? if >=26.1 {
	public void render(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		int dy = offset();
		Gfx gfx = new Gfx(graphics);
		gfx.push(0, dy, 1);
		suggestions.extractRenderState(graphics, mouseX, mouseY - dy);
		gfx.pop();
		clampHint();
	}
	//?} else {
	/*public void render(GuiGraphics graphics, int mouseX, int mouseY) {
		int dy = offset();
		Gfx gfx = new Gfx(graphics);
		gfx.push(0, dy, 1);
		suggestions.render(graphics, mouseX, mouseY - dy);
		gfx.pop();
		clampHint();
	}
	*///?}

	public boolean keyPressed(KeyEvent event) {
		boolean handled = suggestions.keyPressed(event);
		clampHint();
		return handled;
	}

	public boolean mouseClicked(MouseButtonEvent event) {
		boolean handled = suggestions.mouseClicked(new MouseButtonEvent(event.x(), event.y() - offset(), event.buttonInfo()));
		clampHint();
		return handled;
	}

	public boolean mouseScrolled(double scrollY) {
		boolean handled = suggestions.mouseScrolled(scrollY);
		clampHint();
		return handled;
	}
}
