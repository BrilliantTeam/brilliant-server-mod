package net.brilliantw.richpresence;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.regex.Pattern;

public final class DiscordWebhooks {
	public static final String PLACEHOLDER = "https://discord.com/api/webhooks/…";
	private static final Pattern URL = Pattern.compile("^https://(?:(?:ptb|canary)\\.)?discord(?:app)?\\.com/api(?:/v\\d{1,2})?/webhooks/\\d{5,30}/[A-Za-z0-9_-]{20,200}(?:\\?thread_id=\\d{5,30})?$");
	private static final int IS_COMPONENTS_V2 = 1 << 15;
	private static final int MAX_BODY = 64 * 1024;
	private static final int MAX_PENDING = 10;
	private static final long SPACING_MS = 1000;
	private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
	private static final ExecutorService SENDER = Executors.newSingleThreadExecutor(runnable -> {
		Thread thread = new Thread(runnable, "brilliant-webhooks");
		thread.setDaemon(true);
		return thread;
	});
	private static final AtomicInteger PENDING = new AtomicInteger();
	private static final Map<String, Long> LAST_SENT = new ConcurrentHashMap<>();

	private DiscordWebhooks() {}

	public static boolean validUrl(String url) {
		return url != null && URL.matcher(url.strip()).matches();
	}

	public static String hint(String url) {
		String clean = url.strip();
		int slash = clean.lastIndexOf('/');
		String token = slash < 0 ? clean : clean.substring(slash + 1).replaceFirst("\\?.*$", "");
		return "Webhook …" + (token.length() > 4 ? token.substring(token.length() - 4) : token);
	}

	public static JsonObject payload(String body) {
		String text = body == null ? "" : body.strip();
		if (text.startsWith("{")) {
			JsonElement parsed;
			try {
				parsed = JsonParser.parseString(text);
			} catch (RuntimeException e) {
				throw new IllegalArgumentException("內容看起來是 JSON，但格式不正確");
			}
			if (!parsed.isJsonObject()) {
				throw new IllegalArgumentException("內容看起來是 JSON，但格式不正確");
			}
			return parsed.getAsJsonObject();
		}
		if (text.isEmpty()) {
			throw new IllegalArgumentException("要傳送的內容是空的");
		}
		JsonObject message = new JsonObject();
		message.addProperty("content", text);
		message.add("allowed_mentions", noMentions());
		return message;
	}

	private static JsonObject noMentions() {
		JsonObject mentions = new JsonObject();
		mentions.add("parse", new JsonArray());
		return mentions;
	}

	static String endpoint(String url, JsonObject payload) {
		String base = url.strip();
		String join = base.contains("?") ? "&" : "?";
		return payload.has("components") ? base + join + "wait=true&with_components=true" : base + join + "wait=true";
	}

	public static String send(String url, String body, Consumer<String> done) {
		if (!validUrl(url)) {
			return "Webhook 網址格式不對，要是 https://discord.com/api/webhooks/… 開頭的完整網址";
		}
		JsonObject payload;
		try {
			payload = payload(body);
		} catch (IllegalArgumentException e) {
			return e.getMessage();
		}
		String json = payload.toString();
		if (json.getBytes(StandardCharsets.UTF_8).length > MAX_BODY) {
			return "內容太長了（上限 64 KB）";
		}
		if (PENDING.incrementAndGet() > MAX_PENDING) {
			PENDING.decrementAndGet();
			return "Webhook 傳送太頻繁，已略過（同時最多排 " + MAX_PENDING + " 則）";
		}
		String key = url.strip();
		String target = endpoint(key, payload);
		SENDER.execute(() -> {
			try {
				long wait = LAST_SENT.getOrDefault(key, 0L) + SPACING_MS - System.currentTimeMillis();
				if (wait > 0) {
					Thread.sleep(wait);
				}
				LAST_SENT.put(key, System.currentTimeMillis());
				HttpRequest request = HttpRequest.newBuilder(URI.create(target)).timeout(Duration.ofSeconds(15))
						.header("Content-Type", "application/json").header("User-Agent", "BrilliantServerMod")
						.POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8)).build();
				HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
				done.accept(describe(response.statusCode(), response.body(), hint(key)));
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			} catch (Exception e) {
				done.accept("⚠ " + hint(key) + " 傳送失敗：連不上 Discord");
			} finally {
				PENDING.decrementAndGet();
			}
		});
		return null;
	}

	static String describe(int status, String body, String hint) {
		if (status >= 200 && status < 300) {
			return "已傳送到 " + hint;
		}
		String reason = "";
		try {
			JsonObject error = JsonParser.parseString(body).getAsJsonObject();
			if (status == 429 && error.has("retry_after")) {
				return "⚠ " + hint + " 傳送太頻繁，Discord 要求等 " + error.get("retry_after").getAsString() + " 秒";
			}
			reason = error.has("message") ? "：" + error.get("message").getAsString() : "";
			if (error.has("errors")) {
				String detail = error.get("errors").toString();
				reason += "（" + (detail.length() > 160 ? detail.substring(0, 160) + "…" : detail) + "）";
			}
		} catch (RuntimeException ignored) {
		}
		return switch (status) {
			case 401, 403, 404 -> "⚠ " + hint + " 傳送失敗：網址無效或 Webhook 已被刪除";
			default -> "⚠ " + hint + " 傳送失敗（HTTP " + status + "）" + reason;
		};
	}

	private static JsonElement parse(String fragment) {
		String text = fragment == null ? "" : fragment.strip();
		if (text.startsWith("{") || text.startsWith("[")) {
			try {
				return JsonParser.parseString(text);
			} catch (RuntimeException ignored) {
			}
		}
		return text.isEmpty() ? null : new JsonPrimitive(text);
	}

	private static JsonArray items(String fragment, java.util.function.Function<String, JsonObject> fromText) {
		JsonArray out = new JsonArray();
		JsonElement parsed = parse(fragment);
		if (parsed == null) {
			return out;
		}
		for (JsonElement element : parsed.isJsonArray() ? parsed.getAsJsonArray() : wrap(parsed)) {
			if (element.isJsonObject()) {
				out.add(element);
			} else if (element.isJsonPrimitive() && fromText != null && !element.getAsString().isBlank()) {
				out.add(fromText.apply(element.getAsString()));
			}
		}
		return out;
	}

	private static JsonArray wrap(JsonElement element) {
		JsonArray array = new JsonArray();
		array.add(element);
		return array;
	}

	static Integer color(String text) {
		String value = text == null ? "" : text.strip().replaceFirst("^#", "").replaceFirst("^0x", "");
		if (value.matches("[0-9a-fA-F]{6}")) {
			return Integer.parseInt(value, 16);
		}
		if (value.matches("\\d{1,8}")) {
			int number = Integer.parseInt(value);
			return number <= 0xFFFFFF ? number : null;
		}
		return null;
	}

	private static void put(JsonObject object, String key, String value) {
		if (value != null && !value.isBlank()) {
			object.addProperty(key, value.strip());
		}
	}

	private static void putMedia(JsonObject object, String key, String url) {
		if (url != null && !url.isBlank()) {
			JsonObject media = new JsonObject();
			media.addProperty("url", url.strip());
			object.add(key, media);
		}
	}

	public static String and(String first, String second) {
		JsonArray out = new JsonArray();
		for (String part : new String[] { first, second }) {
			JsonElement parsed = parse(part);
			if (parsed != null) {
				out.addAll(parsed.isJsonArray() ? parsed.getAsJsonArray() : wrap(parsed));
			}
		}
		return out.toString();
	}

	public static String message(String content, String name, String avatar, String embeds) {
		JsonObject message = new JsonObject();
		put(message, "content", content);
		put(message, "username", name);
		put(message, "avatar_url", avatar);
		JsonArray list = items(embeds, text -> embed("", text, "", "", "", "", ""));
		if (!list.isEmpty()) {
			message.add("embeds", list);
		}
		message.add("allowed_mentions", noMentions());
		return message.toString();
	}

	public static String v2(String name, String avatar, String components) {
		JsonObject message = new JsonObject();
		put(message, "username", name);
		put(message, "avatar_url", avatar);
		message.addProperty("flags", IS_COMPONENTS_V2);
		message.add("components", items(components, DiscordWebhooks::textObject));
		message.add("allowed_mentions", noMentions());
		return message.toString();
	}

	public static String embedJson(String title, String description, String color, String fields, String image, String thumbnail, String footer) {
		return embed(title, description, color, fields, image, thumbnail, footer).toString();
	}

	private static JsonObject embed(String title, String description, String color, String fields, String image, String thumbnail, String footer) {
		JsonObject embed = new JsonObject();
		put(embed, "title", title);
		put(embed, "description", description);
		Integer rgb = color(color);
		if (rgb != null) {
			embed.addProperty("color", rgb);
		}
		JsonArray list = items(fields, text -> field("​", text, false));
		if (!list.isEmpty()) {
			embed.add("fields", list);
		}
		putMedia(embed, "image", image);
		putMedia(embed, "thumbnail", thumbnail);
		if (footer != null && !footer.isBlank()) {
			JsonObject foot = new JsonObject();
			foot.addProperty("text", footer.strip());
			embed.add("footer", foot);
		}
		return embed;
	}

	public static String fieldJson(String name, String value, boolean inline) {
		return field(name, value, inline).toString();
	}

	private static JsonObject field(String name, String value, boolean inline) {
		JsonObject field = new JsonObject();
		field.addProperty("name", name == null || name.isBlank() ? "​" : name.strip());
		field.addProperty("value", value == null || value.isBlank() ? "​" : value.strip());
		field.addProperty("inline", inline);
		return field;
	}

	private static JsonObject textObject(String content) {
		JsonObject text = new JsonObject();
		text.addProperty("type", 10);
		text.addProperty("content", content);
		return text;
	}

	public static String text(String content) {
		return textObject(content == null || content.isBlank() ? "​" : content).toString();
	}

	public static String container(String color, String components) {
		JsonObject container = new JsonObject();
		container.addProperty("type", 17);
		Integer rgb = color(color);
		if (rgb != null) {
			container.addProperty("accent_color", rgb);
		}
		container.add("components", items(components, DiscordWebhooks::textObject));
		return container.toString();
	}

	public static String section(String content, String thumbnail) {
		if (thumbnail == null || thumbnail.isBlank()) {
			return text(content);
		}
		JsonObject section = new JsonObject();
		section.addProperty("type", 9);
		section.add("components", wrap(textObject(content == null || content.isBlank() ? "​" : content)));
		JsonObject accessory = new JsonObject();
		accessory.addProperty("type", 11);
		putMedia(accessory, "media", thumbnail);
		section.add("accessory", accessory);
		return section.toString();
	}

	public static String gallery(String urls) {
		JsonObject gallery = new JsonObject();
		gallery.addProperty("type", 12);
		JsonArray list = new JsonArray();
		for (String url : (urls == null ? "" : urls).split("[\\s,]+")) {
			if (!url.isBlank() && list.size() < 10) {
				JsonObject item = new JsonObject();
				putMedia(item, "media", url);
				list.add(item);
			}
		}
		gallery.add("items", list);
		return gallery.toString();
	}

	public static String separator() {
		JsonObject separator = new JsonObject();
		separator.addProperty("type", 14);
		return separator.toString();
	}

	public static String link(String label, String url) {
		JsonObject button = new JsonObject();
		button.addProperty("type", 2);
		button.addProperty("style", 5);
		button.addProperty("label", label == null || label.isBlank() ? "開啟連結" : label.strip());
		button.addProperty("url", url == null ? "" : url.strip());
		JsonObject row = new JsonObject();
		row.addProperty("type", 1);
		row.add("components", wrap(button));
		return row.toString();
	}

	public static void main(String[] args) {
		check(validUrl("https://discord.com/api/webhooks/123456789012345678/abcdefghijklmnopqrstuvwxyzABCDEF_-123") && validUrl("https://canary.discord.com/api/v10/webhooks/123456789012345678/abcdefghijklmnopqrstuvwxyz?thread_id=123456789012345678"),
				"discord webhook urls pass");
		check(!validUrl("https://evil.example/api/webhooks/123456789012345678/abcdefghijklmnopqrstuvwxyz") && !validUrl("https://discord.com.evil.example/api/webhooks/123456789012345678/abcdefghijklmnopqrstuvwxyz")
				&& !validUrl("http://discord.com/api/webhooks/123456789012345678/abcdefghijklmnopqrstuvwxyz") && !validUrl(PLACEHOLDER), "other hosts and plain http are rejected");
		JsonObject plain = payload("哈囉 @everyone");
		check(plain.get("content").getAsString().equals("哈囉 @everyone") && plain.getAsJsonObject("allowed_mentions").getAsJsonArray("parse").isEmpty(), "plain text never pings");
		String embed = embedJson("標題", "內文", "#5865F2", and(fieldJson("等級", "10", true), fieldJson("金錢", "99", true)), "", "", "頁尾");
		JsonObject sent = payload(message("", "機器人", "", embed));
		JsonObject first = sent.getAsJsonArray("embeds").get(0).getAsJsonObject();
		check(first.get("color").getAsInt() == 0x5865F2 && first.getAsJsonArray("fields").size() == 2 && first.getAsJsonObject("footer").get("text").getAsString().equals("頁尾"), "embed with fields");
		JsonObject card = payload(v2("", "", and(container("ff0000", and(text("**粗體**"), separator())), and(gallery("https://a.png https://b.png"), "純文字"))));
		check(card.get("flags").getAsInt() == IS_COMPONENTS_V2 && card.getAsJsonArray("components").size() == 3
				&& card.getAsJsonArray("components").get(2).getAsJsonObject().get("type").getAsInt() == 10, "v2 components, plain text becomes a text display");
		check(endpoint("https://discord.com/api/webhooks/1/x", card).endsWith("?wait=true&with_components=true") && endpoint("https://discord.com/api/webhooks/1/x?thread_id=5", plain).endsWith("&wait=true"), "query flags");
		boolean rejected = false;
		try {
			payload("{壞掉的");
		} catch (IllegalArgumentException e) {
			rejected = true;
		}
		check(rejected && describe(404, "", "Webhook …abcd").contains("網址無效") && describe(429, "{\"retry_after\":1.5}", "x").contains("1.5"), "errors are explained");
		System.out.println("ok");
	}

	private static void check(boolean ok, String name) {
		if (!ok) {
			throw new AssertionError(name);
		}
	}
}
