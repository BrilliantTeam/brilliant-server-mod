package net.brilliantw.richpresence;

import com.mojang.blaze3d.platform.NativeImage;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import javax.imageio.ImageIO;
import net.minecraft.client.Minecraft;
//? if >=26.1 {
import net.minecraft.client.gui.GuiGraphicsExtractor;
//?} else {
/*import net.minecraft.client.gui.GuiGraphics;
*///?}
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;

public final class SponsorOrderScreen extends Screen {
	private record Rect(int x, int y, int w, int h) {
		boolean contains(double mx, double my) {
			return mx >= x && mx < x + w && my >= y && my < y + h;
		}
	}

	private record Hit(Rect rect, Runnable action) {}

	private static final int HEADER = 26;
	private static final int FOOTER = 14;
	private static final int LINE = 10;
	private static final int GOLD = 0xFFFFBB06;
	private static final int MAX_BARCODE = 1024;
	private static int counter;

	private final Screen parent;
	private final SponsorStore.Order order;
	private final List<SponsorStore.Field> fields;
	private final List<Hit> hits = new ArrayList<>();
	private Identifier barcode;
	private int barcodeWidth;
	private int barcodeHeight;
	private boolean barcodeFailed;
	private boolean closed;
	private String copied = "";
	private long copiedAt;
	private double scroll;

	public SponsorOrderScreen(Screen parent, SponsorStore.Order order) {
		super(Component.literal("贊助訂單"));
		this.parent = parent;
		this.order = order;
		this.fields = order.fields();
		if (order.barcodeImage().startsWith("data:image/png;base64,")) {
			loadBarcode(order.barcodeImage().substring("data:image/png;base64,".length()));
		}
	}

	private void loadBarcode(String base64) {
		CompletableFuture.supplyAsync(() -> {
			try {
				BufferedImage source = ImageIO.read(new ByteArrayInputStream(Base64.getDecoder().decode(base64)));
				double scale = Math.min(1, (double) MAX_BARCODE / Math.max(source.getWidth(), source.getHeight()));
				int w = Math.max(1, (int) (source.getWidth() * scale));
				int h = Math.max(1, (int) (source.getHeight() * scale));
				BufferedImage scaled = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
				scaled.getGraphics().drawImage(source.getScaledInstance(w, h, Image.SCALE_AREA_AVERAGING), 0, 0, null);
				ByteArrayOutputStream out = new ByteArrayOutputStream();
				ImageIO.write(scaled, "png", out);
				return NativeImage.read(out.toByteArray());
			} catch (Exception e) {
				return null;
			}
		}).thenAccept(image -> Minecraft.getInstance().execute(() -> {
			if (image == null) {
				barcodeFailed = true;
				return;
			}
			if (closed) {
				image.close();
				return;
			}
			barcodeWidth = image.getWidth();
			barcodeHeight = image.getHeight();
			barcode = Identifier.fromNamespaceAndPath("brilliant-server-mod", "sponsor_barcode/" + counter++);
			Minecraft.getInstance().getTextureManager().register(barcode, new DynamicTexture(() -> "brilliant-server-mod sponsor barcode", image));
		}));
	}

	private Rect panel() {
		int w = Math.min(width - 16, 560);
		return new Rect((width - w) / 2, 8, w, height - 16);
	}

	private Rect content() {
		Rect p = panel();
		return new Rect(p.x() + 10, p.y() + HEADER + 6, p.w() - 20, p.h() - HEADER - FOOTER - 10);
	}

	private void copy(String label, String text) {
		minecraft.keyboardHandler.setClipboard(text);
		copied = label;
		copiedAt = System.currentTimeMillis();
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
		gfx.fill(p.x(), p.y(), p.x() + p.w(), p.y() + HEADER, Theme.HEADER);
		gfx.fill(p.x(), p.y() + HEADER - 1, p.x() + p.w(), p.y() + HEADER, Theme.SUCCESS);
		Theme.drawBack(gfx, font, p.x(), p.y(), mouseX, mouseY);
		gfx.text(font, "✔ 贊助訂單已建立", Theme.titleX(font, p.x()), p.y() + 9, Theme.SUCCESS);
		Rect c = content();
		int height = layout(null, c, 0, -1, -1, false);
		hits.clear();
		scroll = Math.clamp(scroll, 0, Math.max(0, height - c.h()));
		boolean inside = c.contains(mouseX, mouseY);
		gfx.scissor(c.x(), c.y(), c.x() + c.w(), c.y() + c.h());
		layout(gfx, c, c.y() + 2 - (int) scroll, inside ? mouseX : -1, inside ? mouseY : -1, true);
		gfx.endScissor();
		hits.removeIf(hit -> hit.rect().y() + hit.rect().h() <= c.y() || hit.rect().y() >= c.y() + c.h());
		if (height > c.h()) {
			int bar = Math.max(16, c.h() * c.h() / height);
			int barY = c.y() + (int) ((c.h() - bar) * (scroll / (height - c.h())));
			gfx.fill(c.x() + c.w() + 3, barY, c.x() + c.w() + 5, barY + bar, Theme.CHIP_HOVER);
		}
		gfx.fill(p.x(), p.y() + p.h() - FOOTER, p.x() + p.w(), p.y() + p.h() - FOOTER + 1, Theme.HEADER);
		String footer = !copied.isEmpty() && System.currentTimeMillis() - copiedAt < 2000 ? "已複製「" + copied + "」" : "訂單資訊也已私訊到你的 Discord · Esc 返回";
		gfx.text(font, footer, p.x() + 8, p.y() + p.h() - FOOTER + 4, Theme.TEXT_DIM);
	}

	private int paragraph(Gfx gfx, String text, int x, int y, int width, int color, boolean draw) {
		for (FormattedCharSequence line : font.split(Component.literal(text), Math.max(20, width))) {
			if (draw) {
				gfx.text(font, line, x, y, color);
			}
			y += LINE;
		}
		return y;
	}

	private int button(Gfx gfx, String label, int x, int y, int mouseX, int mouseY, boolean primary, boolean draw, Runnable action) {
		int w = font.width(label) + 20;
		if (draw) {
			Rect r = new Rect(x, y, w, 18);
			boolean hover = r.contains(mouseX, mouseY);
			gfx.fill(r.x(), r.y(), r.x() + r.w(), r.y() + r.h(), primary ? hover ? 0xFFFFCB3A : GOLD : hover ? Theme.CHIP_HOVER : Theme.CHIP);
			gfx.centered(font, label, r.x(), r.y(), r.w(), r.h(), primary ? 0xFF1A1A1A : Theme.TEXT);
			hits.add(new Hit(r, action));
		}
		return x + w + 6;
	}

	private int layout(Gfx gfx, Rect c, int top, int mouseX, int mouseY, boolean draw) {
		int y = paragraph(gfx, "請截圖或記下以下繳費資訊。離開後仍可在官網訂單頁或 Discord 私訊中查看。", c.x(), top, c.w() - 4, GOLD, draw) + 6;
		int labelW = 0;
		for (SponsorStore.Field field : fields) {
			labelW = Math.max(labelW, font.width(field.name()));
		}
		labelW = Math.min(labelW + 10, c.w() / 3);
		for (SponsorStore.Field field : fields) {
			boolean important = field.code() || field.name().equals("訂單編號");
			int rowH = field.code() ? 22 : 14;
			if (draw) {
				if (field.code()) {
					gfx.fill(c.x(), y - 3, c.x() + c.w() - 4, y + rowH - 5, 0x30FFBB06);
				}
				gfx.text(font, font.plainSubstrByWidth(field.name(), labelW - 6), c.x() + 4, field.code() ? y + 3 : y, Theme.TEXT_SECONDARY);
				int copyW = important ? font.width("複製") + 12 : 0;
				int valueW = c.w() - labelW - copyW - 16;
				if (field.code()) {
					gfx.push(c.x() + labelW, y, 1.5f);
					gfx.text(font, font.plainSubstrByWidth(field.value(), (int) (valueW / 1.5f)), 0, 0, GOLD);
					gfx.pop();
				} else {
					gfx.text(font, font.plainSubstrByWidth(field.value(), valueW), c.x() + labelW, y, Theme.TEXT);
				}
				if (important) {
					Rect r = new Rect(c.x() + c.w() - copyW - 8, field.code() ? y + 1 : y - 2, copyW, 13);
					gfx.fill(r.x(), r.y(), r.x() + r.w(), r.y() + r.h(), r.contains(mouseX, mouseY) ? Theme.CHIP_HOVER : Theme.CHIP);
					gfx.centered(font, "複製", r.x(), r.y(), r.w(), r.h(), Theme.ACCENT);
					hits.add(new Hit(r, () -> copy(field.name(), field.value())));
				}
			}
			y += rowH;
		}
		if (fields.isEmpty()) {
			y = paragraph(gfx, order.message(), c.x(), y, c.w() - 4, Theme.TEXT, draw);
		}
		y += 6;
		if (barcode != null) {
			int w = Math.min(c.w() - 4, 300);
			int h = w * barcodeHeight / barcodeWidth;
			if (draw) {
				gfx.image(barcode, c.x() + (c.w() - w) / 2, y, w, h);
			}
			y += h + 4;
			y = paragraph(gfx, "到 7-11、全家、萊爾富或 OK 櫃檯出示三段條碼繳費；用手機開啟訂單頁出示會更方便掃描。", c.x(), y, c.w() - 4, Theme.TEXT_DIM, draw) + 6;
		} else if (!order.barcodeImage().isEmpty()) {
			y = paragraph(gfx, barcodeFailed ? "條碼圖片無法顯示，請開啟訂單頁查看。" : "正在載入條碼…", c.x(), y, c.w() - 4, Theme.TEXT_DIM, draw) + 6;
		}
		SponsorStore.Data data = SponsorStore.data();
		SponsorStore.Method method = data == null ? null : data.methods.stream().filter(m -> m.id == order.methodId()).findFirst().orElse(null);
		if (method != null && !method.steps.isEmpty()) {
			if (draw) {
				gfx.text(font, "繳費步驟", c.x(), y, GOLD);
			}
			y += 13;
			for (int i = 0; i < method.steps.size(); i++) {
				if (draw) {
					gfx.text(font, (i + 1) + ".", c.x() + 2, y, Theme.TEXT_DIM);
				}
				y = paragraph(gfx, method.steps.get(i), c.x() + 14, y, c.w() - 18, Theme.TEXT, draw) + 2;
			}
			if (!method.settlement.isBlank()) {
				y = paragraph(gfx, method.settlement, c.x(), y + 2, c.w() - 4, Theme.TEXT_SECONDARY, draw);
			}
			y += 8;
		}
		int x = c.x();
		if (!order.pageUrl().isEmpty()) {
			x = button(gfx, "開啟訂單頁 ↗", x, y, mouseX, mouseY, true, draw, () -> open(order.pageUrl()));
		}
		x = button(gfx, "複製全部資訊", x, y, mouseX, mouseY, order.pageUrl().isEmpty(), draw, () -> copy("全部資訊", order.message()));
		button(gfx, "返回贊助頁", x, y, mouseX, mouseY, false, draw, this::onClose);
		return y + 24 - top;
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
		closed = true;
		if (barcode != null) {
			Minecraft.getInstance().getTextureManager().release(barcode);
			barcode = null;
		}
		CommandMenuScreen.setScreen(parent);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
