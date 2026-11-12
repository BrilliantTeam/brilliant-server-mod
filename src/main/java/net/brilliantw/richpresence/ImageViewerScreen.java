package net.brilliantw.richpresence;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.NativeImage;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import javax.imageio.ImageIO;
//? if >=26.1 {
import net.minecraft.client.gui.GuiGraphicsExtractor;
//?} else {
/*import net.minecraft.client.gui.GuiGraphics;
*///?}
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

public final class ImageViewerScreen extends Screen {
	private record Rect(int x, int y, int w, int h) {
		boolean contains(double mouseX, double mouseY) {
			return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
		}
	}

	private record Loaded(NativeImage image, RemoteImages.Animation animation) {}

	private static final int MAX_SIZE = 2048;
	private static final int MAX_BYTES = 25 * 1024 * 1024;
	private static final double MAX_ZOOM = 8;
	private static final HttpClient HTTP = RPConfig.HTTP_REDIRECT;
	private static int counter;
	private static Identifier shown;

	private final String url;
	private final String name;
	private Identifier texture;
	private RemoteImages.Animation animation;
	private int imageWidth;
	private int imageHeight;
	private String error = "";
	private boolean closed;
	private double zoom = -1;
	private double panX;
	private double panY;

	public ImageViewerScreen(String url, String name) {
		super(Component.literal("圖片"));
		this.url = url;
		this.name = name == null || name.isBlank() ? fileName(url) : name;
		load();
	}

	private static String fileName(String url) {
		try {
			String path = URI.create(url).getPath();
			return path == null || path.isEmpty() ? url : path.substring(path.lastIndexOf('/') + 1);
		} catch (IllegalArgumentException e) {
			return url;
		}
	}

	private static boolean isDiscordMedia(URI uri) {
		String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
		return "https".equalsIgnoreCase(uri.getScheme()) && uri.getPort() == -1
				&& (host.equals("cdn.discordapp.com") || host.equals("media.discordapp.net") || host.matches("images-ext-\\d+\\.discordapp\\.net"));
	}

	private void load() {
		load(false);
	}

	private void load(boolean direct) {
		URI source;
		try {
			source = URI.create(url);
		} catch (IllegalArgumentException e) {
			error = "圖片網址無效";
			return;
		}
		if (!"https".equalsIgnoreCase(source.getScheme()) && !"http".equalsIgnoreCase(source.getScheme())) {
			error = "圖片網址無效";
			return;
		}
		boolean proxied = !direct && isDiscordMedia(source);
		URI target = proxied
				? URI.create(RPConfig.WEBSITE_URL + "/api/announcements/media?u=" + URLEncoder.encode(url, StandardCharsets.UTF_8) + "&w=" + MAX_SIZE)
				: source;
		HttpRequest request = HttpRequest.newBuilder(target).timeout(Duration.ofSeconds(30)).header("User-Agent", RPConfig.USER_AGENT).GET().build();
		CompletableFuture<Loaded> future = HTTP.sendAsync(request, HttpResponse.BodyHandlers.ofByteArray()).thenApply(response -> {
			if (response.statusCode() != 200) {
				throw new IllegalStateException("圖片載入失敗（HTTP " + response.statusCode() + "）");
			}
			if (response.body().length > MAX_BYTES) {
				throw new IllegalStateException("圖片太大，請在瀏覽器開啟");
			}
			try {
				byte[] png = proxied ? response.body() : toPng(response.body());
				NativeImage image = NativeImage.read(png);
				return new Loaded(image, RemoteImages.readAnimation(png, image.getWidth(), image.getHeight()));
			} catch (IllegalStateException e) {
				throw e;
			} catch (Exception e) {
				throw new IllegalStateException("不支援的圖片格式，請在瀏覽器開啟");
			}
		});
		future.whenComplete((loaded, failure) -> Minecraft.getInstance().execute(() -> {
			if (failure != null && proxied && !closed) {
				load(true);
				return;
			}
			if (failure != null) {
				Throwable cause = failure.getCause() != null ? failure.getCause() : failure;
				error = cause instanceof IllegalStateException ? cause.getMessage() : "無法下載圖片，請在瀏覽器開啟";
				return;
			}
			NativeImage image = loaded.image();
			if (closed) {
				image.close();
				return;
			}
			releaseShown();
			animation = loaded.animation();
			imageWidth = animation != null ? animation.frameWidth() : image.getWidth();
			imageHeight = animation != null ? animation.frameHeight() : image.getHeight();
			texture = Identifier.fromNamespaceAndPath("brilliant-server-mod", "viewer/" + counter++);
			Minecraft.getInstance().getTextureManager().register(texture, new DynamicTexture(() -> "brilliant-server-mod image viewer", image));
			shown = texture;
		}));
	}

	private static byte[] toPng(byte[] data) throws Exception {
		BufferedImage source = ImageIO.read(new ByteArrayInputStream(data));
		if (source == null) {
			throw new IllegalArgumentException("unsupported");
		}
		double scale = Math.min(1, (double) MAX_SIZE / Math.max(source.getWidth(), source.getHeight()));
		int width = Math.max(1, (int) Math.round(source.getWidth() * scale));
		int height = Math.max(1, (int) Math.round(source.getHeight() * scale));
		BufferedImage output = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
		Graphics2D graphics = output.createGraphics();
		graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
		graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
		graphics.drawImage(source, 0, 0, width, height, null);
		graphics.dispose();
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		ImageIO.write(output, "png", bytes);
		return bytes.toByteArray();
	}

	private Rect area() {
		return new Rect(8, 24, width - 16, height - 44);
	}

	private Rect closeChip() {
		int w = font.width("關閉") + 12;
		return new Rect(width - 8 - w, 5, w, 14);
	}

	private Rect browserChip() {
		Rect close = closeChip();
		int w = font.width("在瀏覽器開啟") + 12;
		return new Rect(close.x() - 4 - w, 5, w, 14);
	}

	private double guiScale() {
		return Math.max(1, minecraft.getWindow().getGuiScale());
	}

	private double fitZoom() {
		Rect area = area();
		double naturalWidth = imageWidth / guiScale();
		double naturalHeight = imageHeight / guiScale();
		return Math.min(2, Math.min(area.w() / naturalWidth, area.h() / naturalHeight));
	}

	private double currentZoom() {
		if (zoom < 0) {
			zoom = fitZoom();
		}
		return zoom;
	}

	private void clampPan() {
		Rect area = area();
		double drawnWidth = imageWidth / guiScale() * currentZoom();
		double drawnHeight = imageHeight / guiScale() * currentZoom();
		double maxX = Math.max(0, (drawnWidth - area.w()) / 2);
		double maxY = Math.max(0, (drawnHeight - area.h()) / 2);
		panX = Math.clamp(panX, -maxX, maxX);
		panY = Math.clamp(panY, -maxY, maxY);
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

	private void drawChip(Gfx gfx, Rect chip, String label, int mouseX, int mouseY) {
		gfx.round(chip.x(), chip.y(), chip.x() + chip.w(), chip.y() + chip.h(), chip.contains(mouseX, mouseY) ? Theme.CHIP_HOVER : Theme.CHIP);
		gfx.text(font, label, chip.x() + 6, chip.y() + 3, Theme.TEXT);
	}

	private void draw(Gfx gfx, int mouseX, int mouseY) {
		gfx.fill(0, 0, width, height, 0xE60C0E10);
		Rect browser = browserChip();
		gfx.text(font, Gfx.fit(font, name, browser.x() - 16), 8, 8, Theme.TEXT);
		drawChip(gfx, browser, "在瀏覽器開啟", mouseX, mouseY);
		drawChip(gfx, closeChip(), "關閉", mouseX, mouseY);

		Rect area = area();
		if (texture == null) {
			String text = error.isEmpty() ? "載入高清圖片中…" : error;
			gfx.text(font, text, (width - font.width(text)) / 2, area.y() + area.h() / 2 - 4, error.isEmpty() ? Theme.TEXT_SECONDARY : Theme.DANGER);
			return;
		}
		clampPan();
		double scale = currentZoom();
		int drawnWidth = (int) Math.round(imageWidth / guiScale() * scale);
		int drawnHeight = (int) Math.round(imageHeight / guiScale() * scale);
		int x = (int) Math.round(area.x() + (area.w() - drawnWidth) / 2.0 + panX);
		int y = (int) Math.round(area.y() + (area.h() - drawnHeight) / 2.0 + panY);
		gfx.scissor(area.x(), area.y(), area.x() + area.w(), area.y() + area.h());
		RemoteImages.draw(gfx, texture, animation, x, y, drawnWidth, drawnHeight);
		gfx.endScissor();

		String info = imageWidth + "×" + imageHeight + "・" + Math.round(scale * 100) + "%" + (animation != null ? "・動畫 " + animation.frames() + " 格" : "");
		String hint = "滾輪縮放 · 拖曳移動 · 雙擊還原 · Esc 關閉";
		gfx.text(font, info, 8, height - 14, Theme.TEXT_SECONDARY);
		gfx.text(font, hint, width - 8 - font.width(hint), height - 14, Theme.TEXT_DIM);
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (super.mouseClicked(event, doubleClick)) {
			return true;
		}
		if (event.button() != InputConstants.MOUSE_BUTTON_LEFT) {
			return false;
		}
		if (closeChip().contains(event.x(), event.y())) {
			Theme.click();
			onClose();
			return true;
		}
		if (browserChip().contains(event.x(), event.y())) {
			Theme.click();
			try {
				ConfirmLinkScreen.confirmLinkNow(this, URI.create(url));
			} catch (IllegalArgumentException ignored) {
			}
			return true;
		}
		if (doubleClick && texture != null) {
			zoom = fitZoom();
			panX = 0;
			panY = 0;
		}
		return true;
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
		if (texture != null && event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
			panX += dragX;
			panY += dragY;
			return true;
		}
		return super.mouseDragged(event, dragX, dragY);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		if (texture == null || scrollY == 0) {
			return true;
		}
		Rect area = area();
		double old = currentZoom();
		double next = Math.clamp(old * (scrollY > 0 ? 1.25 : 0.8), Math.min(fitZoom(), 1), MAX_ZOOM);
		double centerX = area.x() + area.w() / 2.0 + panX;
		double centerY = area.y() + area.h() / 2.0 + panY;
		panX += (mouseX - centerX) * (1 - next / old);
		panY += (mouseY - centerY) * (1 - next / old);
		zoom = next;
		return true;
	}

	private static void releaseShown() {
		if (shown != null) {
			Minecraft.getInstance().getTextureManager().release(shown);
			shown = null;
		}
	}

	@Override
	public void onClose() {
		closed = true;
		releaseShown();
		texture = null;
		CommandMenuScreen.setScreen(null);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
