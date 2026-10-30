package net.brilliantw.richpresence;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.AbstractWidget;
//? if >=26.1 {
import net.minecraft.client.gui.GuiGraphicsExtractor;
//?} else {
/*import net.minecraft.client.gui.GuiGraphics;
*///?}
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.gui.screens.inventory.tooltip.DefaultTooltipPositioner;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;

public final class Gfx {
	//? if >=26.1 {
	private final GuiGraphicsExtractor graphics;

	public Gfx(GuiGraphicsExtractor graphics) {
		this.graphics = graphics;
	}
	//?} else {
	/*private final GuiGraphics graphics;

	public Gfx(GuiGraphics graphics) {
		this.graphics = graphics;
	}
	*///?}

	public void fill(int x1, int y1, int x2, int y2, int color) {
		graphics.fill(x1, y1, x2, y2, color);
	}

	public static int textTop(Font font, int top, int height) {
		return top + (height - font.lineHeight + 2) / 2;
	}

	public void label(Font font, String text, int x, int top, int height, int color) {
		text(font, text, x, textTop(font, top, height), color);
	}

	public void centered(Font font, String text, int left, int top, int width, int height, int color) {
		text(font, text, left + (width - font.width(text)) / 2, textTop(font, top, height), color);
	}

	public void iconLabel(Font font, Icons icon, String text, int left, int top, int width, int height, int color) {
		int total = icon.width() + (text.isEmpty() ? 0 : 3 + font.width(text));
		int x = left + (width - total) / 2;
		icon.draw(this, x, top + (height - icon.height()) / 2, color);
		if (!text.isEmpty()) {
			text(font, text, x + icon.width() + 3, textTop(font, top, height), color);
		}
	}

	public static int iconLabelWidth(Font font, Icons icon, String text) {
		return icon.width() + (text.isEmpty() ? 0 : 3 + font.width(text));
	}

	public void text(Font font, String text, int x, int y, int color) {
		//? if >=26.1 {
		graphics.text(font, text, x, y, color, false);
		//?} else {
		/*graphics.drawString(font, text, x, y, color, false);
		*///?}
	}

	public void text(Font font, Component text, int x, int y, int color) {
		//? if >=26.1 {
		graphics.text(font, text, x, y, color, false);
		//?} else {
		/*graphics.drawString(font, text, x, y, color, false);
		*///?}
	}

	public void text(Font font, FormattedCharSequence text, int x, int y, int color) {
		//? if >=26.1 {
		graphics.text(font, text, x, y, color, false);
		//?} else {
		/*graphics.drawString(font, text, x, y, color, false);
		*///?}
	}

	public void image(Identifier texture, int x, int y, int width, int height) {
		image(texture, x, y, width, height, 0f, 1f, 0f, 1f);
	}

	public void image(Identifier texture, int x, int y, int width, int height, float u0, float u1, float v0, float v1) {
		graphics.blit(texture, x, y, x + width, y + height, u0, u1, v0, v1);
	}

	public void sprite(Identifier atlas, int x, int y, int u, int v, int size, int atlasWidth, int atlasHeight, int color) {
		graphics.blit(RenderPipelines.GUI_TEXTURED, atlas, x, y, u, v, size, size, atlasWidth, atlasHeight, color);
	}

	public void texture(Identifier texture, int x, int y, int width, int height, int textureWidth, int textureHeight, int color) {
		graphics.blit(RenderPipelines.GUI_TEXTURED, texture, x, y, 0, 0, width, height, textureWidth, textureHeight, textureWidth, textureHeight, color);
	}

	public void region(Identifier texture, int x, int y, int width, int height, float u, float v, int regionWidth, int regionHeight, int textureWidth, int textureHeight) {
		graphics.blit(RenderPipelines.GUI_TEXTURED, texture, x, y, u, v, width, height, regionWidth, regionHeight, textureWidth, textureHeight, 0xFFFFFFFF);
	}

	public void widget(AbstractWidget widget, int mouseX, int mouseY) {
		//? if >=26.1 {
		widget.extractRenderState(graphics, mouseX, mouseY, 0f);
		//?} else {
		/*widget.render(graphics, mouseX, mouseY, 0f);
		*///?}
	}

	public void tooltip(Font font, List<Component> lines, int x, int y) {
		graphics.setComponentTooltipForNextFrame(font, lines, x, y);
	}

	public void scissor(int x1, int y1, int x2, int y2) {
		graphics.enableScissor(x1, y1, x2, y2);
	}

	public void endScissor() {
		graphics.disableScissor();
	}

	public void item(ItemStack stack, int x, int y) {
		//? if >=26.1 {
		graphics.fakeItem(stack, x, y);
		//?} else {
		/*graphics.renderFakeItem(stack, x, y);
		*///?}
	}

	public void itemWithCount(Font font, ItemStack stack, int x, int y) {
		item(stack, x, y);
		//? if >=26.1 {
		graphics.itemDecorations(font, stack, x, y);
		//?} else {
		/*graphics.renderItemDecorations(font, stack, x, y);
		*///?}
	}

	public void itemTooltip(Font font, ItemStack stack, int x, int y) {
		List<ClientTooltipComponent> parts = new ArrayList<>(Screen.getTooltipFromItem(Minecraft.getInstance(), stack).stream()
				.map(line -> ClientTooltipComponent.create(line.getVisualOrderText())).toList());
		stack.getTooltipImage().ifPresent(image -> parts.add(Math.min(1, parts.size()), ClientTooltipComponent.create(image)));
		//? if >=26.3 {
		/*graphics.tooltip(font, parts, x, y, DefaultTooltipPositioner.INSTANCE, stack.get(DataComponents.TOOLTIP_STYLE), false);
		*///?} else if >=26.1 {
		graphics.tooltip(font, parts, x, y, DefaultTooltipPositioner.INSTANCE, stack.get(DataComponents.TOOLTIP_STYLE));
		//?} else {
		/*graphics.renderTooltip(font, parts, x, y, DefaultTooltipPositioner.INSTANCE, stack.get(DataComponents.TOOLTIP_STYLE));
		*///?}
	}

	public void push(float x, float y, float scale) {
		graphics.pose().pushMatrix();
		graphics.pose().translate(x, y);
		graphics.pose().scale(scale, scale);
	}

	public void rotate(float radians) {
		graphics.pose().rotate(radians);
	}

	public void pop() {
		graphics.pose().popMatrix();
	}
}
