package net.brilliantw.richpresence;

import com.google.gson.Gson;
import com.mojang.blaze3d.platform.NativeImage;
import java.nio.charset.StandardCharsets;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

public final class RemoteImages {
	public enum State { LOADING, READY, FAILED }

	public static final class Entry {
		public State state = State.LOADING;
		public Identifier texture;
		public int width;
		public int height;
		public Animation animation;
		long failedAt;
	}

	public record Animation(int frames, int columns, int frameWidth, int frameHeight, int[] delays, int total, int sheetWidth, int sheetHeight) {
		public int frameAt(long millis) {
			long time = millis % total;
			for (int i = 0; i < frames; i++) {
				time -= delays[i];
				if (time < 0) {
					return i;
				}
			}
			return frames - 1;
		}
	}

	private static final class AnimationMeta {
		int n;
		int cols;
		int fw;
		int fh;
		double[] d;
	}

	private static final String ANIMATION_KEY = "bsm-anim";
	private static final Gson GSON = new Gson();

	public static Animation readAnimation(byte[] png, int sheetWidth, int sheetHeight) {
		int at = 8;
		while (at + 8 <= png.length) {
			int length = ((png[at] & 0xFF) << 24) | ((png[at + 1] & 0xFF) << 16) | ((png[at + 2] & 0xFF) << 8) | (png[at + 3] & 0xFF);
			String type = new String(png, at + 4, 4, StandardCharsets.ISO_8859_1);
			if (length < 0 || at + 12 + length > png.length || type.equals("IDAT")) {
				return null;
			}
			if (type.equals("tEXt")) {
				String text = new String(png, at + 8, length, StandardCharsets.ISO_8859_1);
				int split = text.indexOf('\0');
				if (split > 0 && text.substring(0, split).equals(ANIMATION_KEY)) {
					try {
						AnimationMeta meta = GSON.fromJson(text.substring(split + 1), AnimationMeta.class);
						if (meta == null || meta.n < 2 || meta.cols < 1 || meta.fw < 1 || meta.fh < 1 || meta.d == null || meta.d.length != meta.n
								|| meta.cols * meta.fw > sheetWidth || ((meta.n + meta.cols - 1) / meta.cols) * meta.fh > sheetHeight) {
							return null;
						}
						int[] delays = new int[meta.n];
						int total = 0;
						for (int i = 0; i < meta.n; i++) {
							delays[i] = (int) Math.max(20, Math.round(meta.d[i]));
							total += delays[i];
						}
						return new Animation(meta.n, meta.cols, meta.fw, meta.fh, delays, total, sheetWidth, sheetHeight);
					} catch (Exception e) {
						return null;
					}
				}
			}
			at += 12 + length;
		}
		return null;
	}

	public static void draw(Gfx gfx, Identifier texture, Animation animation, int x, int y, int width, int height) {
		if (animation == null) {
			gfx.image(texture, x, y, width, height);
			return;
		}
		int frame = animation.frameAt(System.currentTimeMillis());
		float u0 = (float) (frame % animation.columns() * animation.frameWidth()) / animation.sheetWidth();
		float v0 = (float) (frame / animation.columns() * animation.frameHeight()) / animation.sheetHeight();
		float u1 = u0 + (float) animation.frameWidth() / animation.sheetWidth();
		float v1 = v0 + (float) animation.frameHeight() / animation.sheetHeight();
		gfx.image(texture, x, y, width, height, u0, u1, v0, v1);
	}

	public static void draw(Gfx gfx, Entry entry, int x, int y, int width, int height) {
		draw(gfx, entry.texture, entry.animation, x, y, width, height);
	}

	private static final String MEDIA_PREFIX = "/api/announcements/media?";
	public static final String HEAD_PREFIX = "/api/head?name=";
	private static final int MAX_ENTRIES = 96;
	private static final long RETRY_MS = 30 * 1000;
	private static final int MAX_BYTES = 8 * 1024 * 1024;
	private static final HttpClient HTTP = RPConfig.HTTP;
	private static final Map<String, Entry> CACHE = new LinkedHashMap<>(16, 0.75f, true) {
		@Override
		protected boolean removeEldestEntry(Map.Entry<String, Entry> eldest) {
			if (size() <= MAX_ENTRIES) {
				return false;
			}
			if (eldest.getValue().texture != null) {
				Minecraft.getInstance().getTextureManager().release(eldest.getValue().texture);
			}
			return true;
		}
	};
	private static int counter;
	private static int version;

	private RemoteImages() {}

	public static int version() {
		return version;
	}

	public static int requestWidth(int guiWidth) {
		int pixels = (int) Math.ceil(guiWidth * (double) Minecraft.getInstance().getWindow().getGuiScale());
		return Math.clamp((pixels + 15) / 16 * 16, 16, 1024);
	}

	public static Entry peek(String src, int requestWidth) {
		return CACHE.get(src + "|" + requestWidth);
	}

	public static Entry get(String src, int requestWidth) {
		String key = src + "|" + requestWidth;
		Entry entry = CACHE.get(key);
		if (entry != null && !(entry.state == State.FAILED && entry.failedAt > 0 && System.currentTimeMillis() - entry.failedAt > RETRY_MS)) {
			return entry;
		}
		Entry created = new Entry();
		CACHE.put(key, created);
		if (src == null || !(src.startsWith(MEDIA_PREFIX) || src.startsWith(HEAD_PREFIX))) {
			created.state = State.FAILED;
			return created;
		}
		HttpRequest request = HttpRequest.newBuilder(URI.create(RPConfig.WEBSITE_URL + src + "&w=" + requestWidth))
				.timeout(Duration.ofSeconds(20))
				.header("User-Agent", RPConfig.USER_AGENT)
				.GET()
				.build();
		HTTP.sendAsync(request, HttpResponse.BodyHandlers.ofByteArray()).whenComplete((response, error) -> Minecraft.getInstance().execute(() -> {
			boolean ok = error == null && response.statusCode() == 200 && response.body().length <= MAX_BYTES;
			if (ok && CACHE.get(key) == created) {
				try {
					NativeImage image = NativeImage.read(response.body());
					created.animation = readAnimation(response.body(), image.getWidth(), image.getHeight());
					created.width = created.animation != null ? created.animation.frameWidth() : image.getWidth();
					created.height = created.animation != null ? created.animation.frameHeight() : image.getHeight();
					created.texture = Identifier.fromNamespaceAndPath("brilliant-server-mod", "announcement/" + counter++);
					Minecraft.getInstance().getTextureManager().register(created.texture, new DynamicTexture(() -> "brilliant-server-mod announcement image", image));
					created.state = State.READY;
				} catch (Exception e) {
					created.texture = null;
					created.state = State.FAILED;
				}
			} else {
				created.state = State.FAILED;
				created.failedAt = System.currentTimeMillis();
			}
			version++;
		}));
		return created;
	}
}
