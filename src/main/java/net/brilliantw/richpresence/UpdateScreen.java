package net.brilliantw.richpresence;

import com.mojang.blaze3d.platform.InputConstants;
import java.net.URI;
import java.util.List;
import net.minecraft.client.gui.components.Button;
//? if >=26.1 {
import net.minecraft.client.gui.GuiGraphicsExtractor;
//?} else {
/*import net.minecraft.client.gui.GuiGraphics;
*///?}
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

public final class UpdateScreen extends Screen {
	private static final int WIDTH = 300;
	private static final int HEIGHT = 170;
	private static final int HEADER = Theme.HEADER_H;

	private final Screen parent;
	private Button install;

	public UpdateScreen(Screen parent) {
		super(Component.literal("模組更新"));
		this.parent = parent;
	}

	private int left() {
		return (width - WIDTH) / 2;
	}

	private int top() {
		return (height - HEIGHT) / 2;
	}

	@Override
	protected void init() {
		int y = top() + HEIGHT - 28;
		install = addRenderableWidget(Theme.button(Component.literal("下載並安裝"), b -> {
			if (ModUpdate.status() == ModUpdate.Status.READY) {
				minecraft.stop();
			} else {
				ModUpdate.download();
			}
		}, left() + 10, y, 136, 20).primary());
		addRenderableWidget(Theme.button(Component.literal("在瀏覽器開啟"), b -> {
			ModUpdate.Latest latest = ModUpdate.available();
			if (latest != null) {
				ConfirmLinkScreen.confirmLinkNow(this, URI.create(latest.page));
			}
		}, left() + WIDTH - 146, y, 136, 20));
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

	private String statusText() {
		return switch (ModUpdate.status()) {
			case DOWNLOADING -> "下載中… " + ModUpdate.progress() + "%";
			case READY -> "已下載並驗證完成，關閉遊戲後會自動安裝；下次啟動即為新版本";
			case FAILED -> "更新失敗：" + ModUpdate.message();
			case IDLE -> ModUpdate.canInstall() ? "會先比對檔案雜湊值，確認無誤後於關閉遊戲時替換" : "此安裝方式無法自動更新，請在瀏覽器下載";
		};
	}

	private void draw(Gfx gfx, int mouseX, int mouseY) {
		int x = left();
		int y = top();
		Theme.panel(gfx, x, y, x + WIDTH, y + HEIGHT);
		Theme.header(gfx, x, y, WIDTH);
		gfx.text(font, title.getString(), Theme.titleX(font, x), y + 9, Theme.ACCENT);
		Theme.drawBack(gfx, font, x, y, mouseX, mouseY);

		ModUpdate.Latest latest = ModUpdate.available();
		int ty = y + HEADER + 10;
		gfx.text(font, "目前版本：" + RPConfig.MOD_VERSION, x + 12, ty, Theme.TEXT_SECONDARY);
		gfx.text(font, "最新版本：" + (latest == null ? "已是最新版本" : ModUpdate.label(latest)), x + 12, ty + 13, latest == null ? Theme.TEXT_SECONDARY : Theme.WARNING);
		if (latest != null && !latest.notes.isEmpty()) {
			List<FormattedCharSequence> notes = font.split(Component.literal("更新內容：" + latest.notes), WIDTH - 24);
			for (int i = 0; i < Math.min(3, notes.size()); i++) {
				gfx.text(font, notes.get(i), x + 12, ty + 30 + i * 11, Theme.TEXT);
			}
		}
		ModUpdate.Status status = ModUpdate.status();
		int color = status == ModUpdate.Status.FAILED ? Theme.DANGER : status == ModUpdate.Status.READY ? Theme.SUCCESS : Theme.TEXT_DIM;
		List<FormattedCharSequence> lines = font.split(Component.literal(statusText()), WIDTH - 24);
		for (int i = 0; i < Math.min(2, lines.size()); i++) {
			gfx.text(font, lines.get(i), x + 12, y + HEIGHT - 52 + i * 11, color);
		}
		install.setMessage(Component.literal(status == ModUpdate.Status.READY ? "關閉遊戲以完成更新" : "下載並安裝"));
		install.active = status == ModUpdate.Status.READY
				|| latest != null && ModUpdate.canInstall() && (status == ModUpdate.Status.IDLE || status == ModUpdate.Status.FAILED);
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (super.mouseClicked(event, doubleClick)) {
			return true;
		}
		if (event.button() == InputConstants.MOUSE_BUTTON_LEFT && Theme.backHit(font, left(), top(), event.x(), event.y())) {
			Theme.click();
			onClose();
			return true;
		}
		return false;
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
