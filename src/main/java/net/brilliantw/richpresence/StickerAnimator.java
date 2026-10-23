package net.brilliantw.richpresence;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
//? if >=26.3 {
/*import com.mojang.renderpearl.api.textures.GpuTexture;
*///?} else {
import com.mojang.blaze3d.textures.GpuTexture;
//?}
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.metadata.IIOMetadataNode;
import javax.imageio.stream.ImageInputStream;
import net.brilliantw.richpresence.mixin.BakedSheetGlyphAccessor;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.gui.font.glyphs.BakedSheetGlyph;
import org.w3c.dom.Node;

public final class StickerAnimator {
	private static final long ACTIVE_MS = 5_000;
	private static final long FORGET_MS = 60_000;
	private static final long RETRY_MS = 300_000;
	private static final float MAX_RATIO = 1.7f;
	private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).followRedirects(HttpClient.Redirect.NORMAL).build();

	private record Info(String url, int x0, int y0, int x1, int y1) {}

	private record Frames(NativeImage[] images, int[] ends) {
		int at(long now) {
			long t = now % ends[ends.length - 1];
			for (int i = 0; i < ends.length; i++) {
				if (t < ends[i]) {
					return i;
				}
			}
			return 0;
		}

		void close() {
			for (NativeImage image : images) {
				image.close();
			}
		}
	}

	private static final class Slot {
		final Info info;
		final GpuTexture texture;
		final int x;
		final int y;
		final int width;
		final int height;
		volatile Frames frames;
		volatile boolean loading;
		long failedAt;
		int shown = -1;

		Slot(Info info, GpuTexture texture, int x, int y, int width, int height) {
			this.info = info;
			this.texture = texture;
			this.x = x;
			this.y = y;
			this.width = width;
			this.height = height;
		}
	}

	private static final Map<Integer, Info> INFO = load();
	private static final BitSet ANIMATED = new BitSet(0x10000);
	private static final Map<Object, Integer> UNBAKED = Collections.synchronizedMap(new WeakHashMap<>());
	private static final Map<Integer, Slot> SLOTS = new HashMap<>();
	private static final Map<Integer, Long> SEEN = new HashMap<>();

	static {
		INFO.keySet().forEach(ANIMATED::set);
	}

	private StickerAnimator() {}

	public static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(client -> tick());
	}

	private static Map<Integer, Info> load() {
		Map<Integer, Info> info = new HashMap<>();
		read(info, "/stickers.txt", parts -> parts.length >= 6 && parts[4].equals("gif") ? new String[] { parts[0], "https://media.discordapp.net/stickers/" + parts[3] + ".gif?size=320", parts[3], parts[5] } : null);
		read(info, "/animated_emojis.txt", parts -> parts.length >= 3 ? new String[] { parts[0], "https://cdn.discordapp.com/emojis/" + parts[1] + ".gif?size=64", parts[1], parts[2] } : null);
		return info;
	}

	private static void read(Map<Integer, Info> info, String resource, java.util.function.Function<String[], String[]> row) {
		var stream = StickerAnimator.class.getResourceAsStream(resource);
		if (stream == null) {
			return;
		}
		try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
			reader.lines().map(line -> row.apply(line.split("\t"))).filter(java.util.Objects::nonNull).forEach(parts -> {
				String[] box = parts[3].split(",");
				int codepoint = Integer.parseInt(parts[0], 16);
				if (box.length == 4 && parts[2].matches("\\d{1,20}") && codepoint <= 0xFFFF) {
					info.put(codepoint, new Info(parts[1], Integer.parseInt(box[0]), Integer.parseInt(box[1]), Integer.parseInt(box[2]), Integer.parseInt(box[3])));
				}
			});
		} catch (Exception ignored) {
		}
	}

	public static boolean isAnimated(char c) {
		return ANIMATED.get(c);
	}

	public static void seen(char c) {
		SEEN.put((int) c, System.currentTimeMillis());
	}

	public static void unbaked(int codepoint, Object glyph) {
		if (glyph != null && INFO.containsKey(codepoint)) {
			UNBAKED.put(glyph, codepoint);
		}
	}

	public static void baked(Object glyph, Object baked) {
		Integer codepoint = UNBAKED.get(glyph);
		if (codepoint == null || !(baked instanceof BakedSheetGlyph)) {
			return;
		}
		BakedSheetGlyphAccessor sheet = (BakedSheetGlyphAccessor) baked;
		GpuTexture texture = sheet.brilliant$textureView().texture();
		int size = texture.getWidth(0);
		int x = Math.round(sheet.brilliant$u0() * size);
		int y = Math.round(sheet.brilliant$v0() * size);
		int width = Math.round((sheet.brilliant$u1() - sheet.brilliant$u0()) * size);
		int height = Math.round((sheet.brilliant$v1() - sheet.brilliant$v0()) * size);
		Slot old = SLOTS.put(codepoint, new Slot(INFO.get(codepoint), texture, x, y, width, height));
		if (old != null && old.frames != null) {
			old.frames.close();
		}
	}

	private static void tick() {
		if (SLOTS.isEmpty()) {
			return;
		}
		long now = System.currentTimeMillis();
		for (Iterator<Map.Entry<Integer, Slot>> it = SLOTS.entrySet().iterator(); it.hasNext();) {
			Map.Entry<Integer, Slot> entry = it.next();
			Slot slot = entry.getValue();
			if (slot.texture.isClosed()) {
				it.remove();
				if (slot.frames != null) {
					slot.frames.close();
				}
				continue;
			}
			long idle = now - SEEN.getOrDefault(entry.getKey(), 0L);
			if (idle > FORGET_MS) {
				if (slot.frames != null) {
					slot.frames.close();
					slot.frames = null;
					slot.shown = -1;
				}
				continue;
			}
			if (idle > ACTIVE_MS) {
				continue;
			}
			Frames frames = slot.frames;
			if (frames == null) {
				if (!slot.loading && now - slot.failedAt > RETRY_MS) {
					fetch(slot);
				}
				continue;
			}
			int index = frames.at(now);
			if (index != slot.shown) {
				slot.shown = index;
				upload(slot, frames.images[index]);
			}
		}
	}

	private static void upload(Slot slot, NativeImage image) {
		//? if >=26.2 {
		RenderSystem.getDevice().createCommandEncoder().writeToTexture(slot.texture, image, 0, 0, slot.x, slot.y);
		//?} else {
		/*RenderSystem.getDevice().createCommandEncoder().writeToTexture(slot.texture, image, 0, 0, slot.x, slot.y, slot.width, slot.height, 0, 0);
		*///?}
	}

	private static void fetch(Slot slot) {
		slot.loading = true;
		HttpRequest request = HttpRequest.newBuilder(URI.create(slot.info.url()))
				.timeout(Duration.ofSeconds(20)).header("User-Agent", RPConfig.USER_AGENT).GET().build();
		HTTP.sendAsync(request, HttpResponse.BodyHandlers.ofByteArray()).thenApply(response -> {
			if (response.statusCode() != 200 || response.body().length > 8 << 20) {
				throw new IllegalStateException("HTTP " + response.statusCode());
			}
			return frames(response.body(), slot);
		}).whenComplete((frames, error) -> {
			if (error != null || frames == null) {
				slot.failedAt = System.currentTimeMillis();
			} else {
				slot.frames = frames;
			}
			slot.loading = false;
		});
	}

	private static Frames frames(byte[] gif, Slot slot) {
		List<Integer> delays = new ArrayList<>();
		List<BufferedImage> canvases = decode(gif, slot.info, slot.width, slot.height, delays);
		if (canvases == null || canvases.size() < 2) {
			return null;
		}
		NativeImage[] images = new NativeImage[canvases.size()];
		int[] ends = new int[canvases.size()];
		int total = 0;
		for (int i = 0; i < images.length; i++) {
			BufferedImage source = canvases.get(i);
			NativeImage image = new NativeImage(slot.width, slot.height, false);
			for (int py = 0; py < slot.height; py++) {
				for (int px = 0; px < slot.width; px++) {
					image.setPixel(px, py, source.getRGB(px, py));
				}
			}
			images[i] = image;
			total += delays.get(i);
			ends[i] = total;
		}
		return new Frames(images, ends);
	}

	static List<BufferedImage> decode(byte[] gif, int x0, int y0, int x1, int y1, int width, int height, List<Integer> delays) {
		return decode(gif, new Info("", x0, y0, x1, y1), width, height, delays);
	}

	private static List<BufferedImage> decode(byte[] gif, Info info, int width, int height, List<Integer> delays) {
		List<BufferedImage> canvases = new ArrayList<>();
		try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(gif))) {
			ImageReader reader = ImageIO.getImageReadersByFormatName("gif").next();
			reader.setInput(input, false);
			int count = reader.getNumImages(true);
			BufferedImage canvas = null;
			for (int i = 0; i < count && i < 240; i++) {
				BufferedImage frame = reader.read(i);
				IIOMetadataNode root = (IIOMetadataNode) reader.getImageMetadata(i).getAsTree("javax_imageio_gif_image_1.0");
				Node descriptor = root.getElementsByTagName("ImageDescriptor").item(0);
				Node control = root.getElementsByTagName("GraphicControlExtension").item(0);
				int left = attribute(descriptor, "imageLeftPosition", 0);
				int top = attribute(descriptor, "imageTopPosition", 0);
				if (canvas == null) {
					canvas = new BufferedImage(Math.max(320, left + frame.getWidth()), Math.max(320, top + frame.getHeight()), BufferedImage.TYPE_INT_ARGB);
				}
				String disposal = control == null ? "none" : control.getAttributes().getNamedItem("disposalMethod").getNodeValue();
				int delay = attribute(control, "delayTime", 10);
				BufferedImage previous = disposal.equals("restoreToPrevious") ? copy(canvas) : null;
				Graphics2D g = canvas.createGraphics();
				g.drawImage(frame, left, top, null);
				g.dispose();
				canvases.add(fit(canvas, info, width, height));
				delays.add(delay <= 1 ? 100 : delay * 10);
				if (disposal.equals("restoreToBackgroundColor")) {
					Graphics2D clear = canvas.createGraphics();
					clear.setComposite(java.awt.AlphaComposite.Clear);
					clear.fillRect(left, top, frame.getWidth(), frame.getHeight());
					clear.dispose();
				} else if (previous != null) {
					canvas = previous;
				}
			}
			reader.dispose();
		} catch (Exception e) {
			return null;
		}
		return canvases;
	}

	private static BufferedImage fit(BufferedImage canvas, Info info, int width, int height) {
		int x0 = Math.clamp(info.x0(), 0, canvas.getWidth() - 1);
		int y0 = Math.clamp(info.y0(), 0, canvas.getHeight() - 1);
		int cw = Math.clamp(info.x1(), x0 + 1, canvas.getWidth()) - x0;
		int ch = Math.clamp(info.y1(), y0 + 1, canvas.getHeight()) - y0;
		int padded = Math.max(ch, Math.round(cw / MAX_RATIO));
		double scale = (double) height / padded;
		BufferedImage out = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = out.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
		g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
		g.drawImage(canvas.getSubimage(x0, y0, cw, ch), 0, (int) Math.round((padded - ch) * scale), width, (int) Math.round(ch * scale), null);
		g.dispose();
		return out;
	}

	private static BufferedImage copy(BufferedImage image) {
		BufferedImage out = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = out.createGraphics();
		g.drawImage(image, 0, 0, null);
		g.dispose();
		return out;
	}

	private static int attribute(Node node, String name, int fallback) {
		if (node == null || node.getAttributes().getNamedItem(name) == null) {
			return fallback;
		}
		try {
			return Integer.parseInt(node.getAttributes().getNamedItem(name).getNodeValue());
		} catch (NumberFormatException e) {
			return fallback;
		}
	}
}
