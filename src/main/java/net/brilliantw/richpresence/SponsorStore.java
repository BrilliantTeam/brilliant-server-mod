package net.brilliantw.richpresence;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;

public final class SponsorStore {
	public static final class Data {
		public String title = "";
		public String excerpt = "";
		public String usage = "";
		public List<String> notes = List.of();
		public List<Method> methods = List.of();
		public List<Tier> tiers = List.of();
		public List<Command> free = List.of();
		public Rewards rewards;
		public Links links = new Links();
	}

	public static final class Method {
		public String key = "";
		public int id;
		public boolean orderable;
		public String name = "";
		public String tag = "";
		public String kind = "";
		public List<String> desc = List.of();
		public String fee = "";
		public List<String> steps = List.of();
		public String settlement = "";
		public String url = "";
	}

	public static final class Tier {
		public int amount;
		public List<Special> specials = List.of();
		public List<Command> commands = List.of();
	}

	public static final class Special {
		public int amount;
		public String title = "";
		public List<String> detail = List.of();
		public String url = "";
	}

	public static final class Command {
		public String command = "";
		public String effect = "";
	}

	public static final class Rewards {
		public List<Column> columns = List.of();
		public List<Row> rows = List.of();
	}

	public static final class Column {
		public String label = "";
		public int amount;
		public boolean single;
	}

	public static final class Row {
		public String name = "";
		public List<String> values = List.of();
	}

	public static final class Links {
		public String page = RPConfig.WEBSITE_URL;
		public String records = RPConfig.WEBSITE_URL + "/donation/";
		public String rewards = RPConfig.WEBSITE_URL;
		public String discord = "https://discord.brilliantw.net/";
	}

	public record Estimate(Integer watercress, Integer nextTier) {}

	public record Check(boolean ok, String message) {}

	public record Field(String name, String value, boolean code) {}

	public record Order(int methodId, String message, String barcodeImage, String pageUrl) {
		public List<Field> fields() {
			List<Field> fields = new ArrayList<>();
			String[] blocks = message.split("\n\n");
			for (int i = 0; i < blocks.length; i++) {
				if (i == 1 && blocks.length > 2) {
					continue;
				}
				for (String line : blocks[i].split("\n")) {
					int split = line.indexOf('：');
					if (split > 0) {
						fields.add(new Field(line.substring(0, split).strip(), line.substring(split + 1).strip(), i == blocks.length - 1 && blocks.length > 2));
					}
				}
			}
			return fields;
		}
	}

	public record SubmitResult(Order order, String error) {}

	private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
	private static final Gson GSON = new Gson();
	private static final long STALE_MS = 10 * 60 * 1000;
	private static final String PROXY = "https://api.brilliantw.net/api/v1/proxy";
	private static Data data;
	private static long fetchedAt;
	private static boolean loading;
	private static String error = "";
	private static Order lastOrder;

	private SponsorStore() {}

	public static Data data() {
		return data;
	}

	public static boolean loading() {
		return loading;
	}

	public static String error() {
		return error;
	}

	public static Order lastOrder() {
		return lastOrder;
	}

	private static HttpRequest request(String url) {
		return HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(10)).header("User-Agent", RPConfig.USER_AGENT).GET().build();
	}

	public static void load(boolean force) {
		if (loading || !force && data != null && System.currentTimeMillis() - fetchedAt < STALE_MS) {
			return;
		}
		loading = true;
		error = "";
		HTTP.sendAsync(request(RPConfig.WEBSITE_URL + "/api/sponsor"), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
				.whenComplete((response, failure) -> Minecraft.getInstance().execute(() -> {
					loading = false;
					try {
						if (failure != null || response.statusCode() != 200) {
							throw new IllegalStateException(failure != null ? "連線失敗" : "官網回應 " + response.statusCode());
						}
						Data parsed = GSON.fromJson(response.body(), Data.class);
						if (parsed == null || parsed.tiers == null) {
							throw new IllegalStateException("資料格式不正確");
						}
						data = parsed;
						fetchedAt = System.currentTimeMillis();
					} catch (RuntimeException e) {
						error = "無法取得贊助資訊：" + e.getMessage();
					}
				}));
	}

	public static void estimate(int amount, int cumulative, Consumer<Estimate> done) {
		String url = RPConfig.WEBSITE_URL + "/donation/api/watercress?amount=" + Math.max(0, amount) + "&cumulative=" + Math.max(0, cumulative);
		HTTP.sendAsync(request(url), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
				.whenComplete((response, failure) -> Minecraft.getInstance().execute(() -> {
					try {
						if (failure != null || response.statusCode() != 200) {
							done.accept(null);
							return;
						}
						JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
						Integer watercress = json.has("watercress") && !json.get("watercress").isJsonNull() ? json.get("watercress").getAsInt() : null;
						Integer nextTier = json.has("nextTier") && !json.get("nextTier").isJsonNull() ? json.get("nextTier").getAsInt() : null;
						done.accept(new Estimate(watercress, nextTier));
					} catch (RuntimeException e) {
						done.accept(null);
					}
				}));
	}

	static String form(Map<String, String> fields) {
		return fields.entrySet().stream()
				.map(entry -> URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8) + "=" + URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8))
				.collect(Collectors.joining("&"));
	}

	private static HttpRequest proxy(Map<String, String> fields, int timeoutSeconds) {
		return HttpRequest.newBuilder(URI.create(PROXY)).timeout(Duration.ofSeconds(timeoutSeconds))
				.header("User-Agent", RPConfig.USER_AGENT)
				.header("Content-Type", "application/x-www-form-urlencoded")
				.POST(HttpRequest.BodyPublishers.ofString(form(fields))).build();
	}

	public static void check(String field, String value, Consumer<Check> done) {
		HTTP.sendAsync(proxy(Map.of(field, value), 10), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
				.whenComplete((response, failure) -> Minecraft.getInstance().execute(() -> {
					try {
						JsonObject result = JsonParser.parseString(response.body()).getAsJsonObject().getAsJsonObject("data");
						done.accept(new Check(result.get("ok").getAsBoolean(), result.get("message").getAsString()));
					} catch (RuntimeException e) {
						done.accept(null);
					}
				}));
	}

	static boolean succeeded(JsonElement status) {
		if (status == null || status.isJsonNull()) {
			return false;
		}
		return status.getAsJsonPrimitive().isBoolean() ? status.getAsBoolean() : status.getAsJsonPrimitive().isNumber() && status.getAsInt() == 1;
	}

	static String failure(JsonObject json) {
		JsonElement message = json.get("message");
		String text = message == null || message.isJsonNull() ? "" : message.getAsString();
		if (json.has("inputError") && json.get("inputError").getAsBoolean()) {
			return text;
		}
		return switch (text) {
			case "10002" -> "找不到這個遊戲 ID，請確認拼字，並至少進入過伺服器一次。";
			case "10003" -> "找不到這個 Discord 使用者，請確認名稱，並先加入輝煌 Discord 群組。";
			default -> "建立訂單失敗，請至輝煌 Discord 群組回報錯誤代碼 " + (text.isEmpty() ? "未知" : text) + "。";
		};
	}

	public static void submit(Map<String, String> fields, int methodId, Consumer<SubmitResult> done) {
		HTTP.sendAsync(proxy(fields, 40), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)).whenComplete((response, failure) -> {
			if (failure != null) {
				Minecraft.getInstance().execute(() -> done.accept(new SubmitResult(null, "連線失敗，請檢查網路，或稍等 5～10 分鐘後再試。")));
				return;
			}
			JsonObject json;
			try {
				json = JsonParser.parseString(response.body()).getAsJsonObject();
			} catch (RuntimeException e) {
				Minecraft.getInstance().execute(() -> done.accept(new SubmitResult(null, "贊助系統回應異常（HTTP " + response.statusCode() + "），請稍後再試。")));
				return;
			}
			if (!succeeded(json.get("status"))) {
				String message = failure(json);
				Minecraft.getInstance().execute(() -> done.accept(new SubmitResult(null, message)));
				return;
			}
			String message = json.get("message").getAsString();
			String barcode = json.has("barcodeImage") && !json.get("barcodeImage").isJsonNull() ? json.get("barcodeImage").getAsString() : "";
			JsonObject page = new JsonObject();
			page.addProperty("message", message);
			page.addProperty("barcodeImage", barcode);
			page.addProperty("method", Integer.toString(methodId));
			HttpRequest pageRequest = HttpRequest.newBuilder(URI.create(RPConfig.WEBSITE_URL + "/api/order")).timeout(Duration.ofSeconds(15))
					.header("User-Agent", RPConfig.USER_AGENT).header("Content-Type", "application/json")
					.POST(HttpRequest.BodyPublishers.ofString(page.toString())).build();
			HTTP.sendAsync(pageRequest, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)).whenComplete((pageResponse, pageFailure) -> {
				String url = "";
				try {
					if (pageFailure == null && pageResponse.statusCode() == 200) {
						String id = JsonParser.parseString(pageResponse.body()).getAsJsonObject().get("id").getAsString();
						url = RPConfig.WEBSITE_URL + "/" + URLEncoder.encode("贊助資訊", StandardCharsets.UTF_8) + "/" + URLEncoder.encode("訂單", StandardCharsets.UTF_8) + "?" + id;
					}
				} catch (RuntimeException ignored) {
				}
				Order order = new Order(methodId, message, barcode, url);
				Minecraft.getInstance().execute(() -> {
					lastOrder = order;
					done.accept(new SubmitResult(order, null));
				});
			});
		});
	}

	private static Path savedFile() {
		return FabricLoader.getInstance().getConfigDir().resolve("brilliant-server-mod-sponsor.json");
	}

	public static String savedDiscord() {
		try {
			JsonObject json = JsonParser.parseString(Files.readString(savedFile(), StandardCharsets.UTF_8)).getAsJsonObject();
			return json.has("discordTag") ? json.get("discordTag").getAsString() : "";
		} catch (Exception e) {
			return "";
		}
	}

	public static void saveDiscord(String discordTag) {
		try {
			JsonObject json = new JsonObject();
			json.addProperty("discordTag", discordTag);
			Files.writeString(savedFile(), json.toString(), StandardCharsets.UTF_8);
		} catch (Exception ignored) {
		}
	}
}
