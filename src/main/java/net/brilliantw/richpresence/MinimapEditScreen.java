package net.brilliantw.richpresence;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.List;
import net.minecraft.client.gui.components.Button;
//? if >=26.1 {
import net.minecraft.client.gui.GuiGraphicsExtractor;
//?} else {
/*import net.minecraft.client.gui.GuiGraphics;
*///?}
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

public final class MinimapEditScreen extends Screen {
	private final Screen parent;
	private boolean dragging;

	public MinimapEditScreen(Screen parent) {
		super(Component.literal("小地圖位置"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		int y = height - 28;
		int w = 76;
		int left = width / 2 - (w * 4 + 12) / 2;
		addRenderableWidget(Button.builder(Component.literal("重設"), b -> {
			ModSettings.Data s = ModSettings.get();
			s.minimapX = 0;
			s.minimapY = 0;
			s.minimapSize = 1;
		}).bounds(left, y, w, 20).build());
		addRenderableWidget(Button.builder(shapeLabel(), b -> {
			ModSettings.get().minimapCircle = !ModSettings.get().minimapCircle;
			b.setMessage(shapeLabel());
		}).bounds(left + w + 4, y, w, 20).build());
		addRenderableWidget(Button.builder(rotateLabel(), b -> {
			ModSettings.get().minimapRotate = !ModSettings.get().minimapRotate;
			b.setMessage(rotateLabel());
		}).bounds(left + (w + 4) * 2, y, w, 20).build());
		addRenderableWidget(Button.builder(Component.literal("完成"), b -> onClose()).bounds(left + (w + 4) * 3, y, w, 20).build());
	}

	private static Component shapeLabel() {
		return Component.literal(ModSettings.get().minimapCircle ? "形狀：圓形" : "形狀：方形");
	}

	private static Component rotateLabel() {
		return Component.literal(ModSettings.get().minimapRotate ? "旋轉：跟隨視角" : "旋轉：北方朝上");
	}

	//? if >=26.1 {
	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		draw(new Gfx(graphics), partialTick);
		super.extractRenderState(graphics, mouseX, mouseY, partialTick);
	}
	//?} else {
	/*@Override
	public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		draw(new Gfx(graphics), partialTick);
		super.render(graphics, mouseX, mouseY, partialTick);
	}
	*///?}

	private int[] bounds() {
		int[] at = Minimap.position(width, height);
		return new int[] { at[0], at[1], Minimap.size(), Minimap.size() + Minimap.infoHeight() };
	}

	private void draw(Gfx gfx, float partial) {
		int[] b = bounds();
		if (WorldMaps.current() != null) {
			Minimap.draw(gfx, font, b[0], b[1], partial);
		} else {
			gfx.fill(b[0], b[1], b[0] + b[2], b[1] + b[2], 0xC015191D);
			gfx.centered(font, "小地圖", b[0], b[1], b[2], b[2], Theme.TEXT_SECONDARY);
		}
		gfx.fill(b[0] - 1, b[1] - 1, b[0] + b[2] + 1, b[1], Theme.ACCENT);
		gfx.fill(b[0] - 1, b[1] + b[3], b[0] + b[2] + 1, b[1] + b[3] + 1, Theme.ACCENT);
		gfx.fill(b[0] - 1, b[1], b[0], b[1] + b[3], Theme.ACCENT);
		gfx.fill(b[0] + b[2], b[1], b[0] + b[2] + 1, b[1] + b[3], Theme.ACCENT);
		List<String> lines = List.of(
				"拖曳移動小地圖 · 滾輪調整大小",
				"目前大小：" + Minimap.SIZE_LABELS[ModSettings.get().minimapSize] + " · Esc 完成");
		int boxWidth = lines.stream().mapToInt(font::width).max().orElse(0) + 16;
		int x = (width - boxWidth) / 2;
		int top = height / 2 - 14;
		gfx.fill(x, top, x + boxWidth, top + lines.size() * 11 + 8, 0xC0101418);
		for (int i = 0; i < lines.size(); i++) {
			gfx.centered(font, lines.get(i), x, top + 4 + i * 11, boxWidth, 9, i == 0 ? Theme.ACCENT : Theme.TEXT_SECONDARY);
		}
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (super.mouseClicked(event, doubleClick)) {
			return true;
		}
		dragging = event.button() == InputConstants.MOUSE_BUTTON_LEFT;
		return dragging;
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
		if (!dragging) {
			return super.mouseDragged(event, dragX, dragY);
		}
		ModSettings.Data s = ModSettings.get();
		int freeX = Math.max(1, width - Minimap.size() - Minimap.MARGIN * 2);
		int freeY = Math.max(1, height - Minimap.size() - Minimap.infoHeight() - Minimap.MARGIN * 2);
		s.minimapX = Math.clamp(s.minimapX + (float) (dragX / freeX), 0f, 1f);
		s.minimapY = Math.clamp(s.minimapY + (float) (dragY / freeY), 0f, 1f);
		return true;
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		dragging = false;
		return super.mouseReleased(event);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		ModSettings.Data s = ModSettings.get();
		s.minimapSize = Math.clamp(s.minimapSize + (int) Math.signum(scrollY), 0, Minimap.SIZES.length - 1);
		return true;
	}

	@Override
	public void onClose() {
		ModSettings.save();
		CommandMenuScreen.setScreen(parent);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
