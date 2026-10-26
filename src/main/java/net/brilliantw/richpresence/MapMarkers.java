package net.brilliantw.richpresence;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;

public final class MapMarkers {
	public static final String BASE = "https://map.brilliantw.net";
	private static final long REFRESH_MS = 5 * 60 * 1000;
	private static final int MAX_BYTES = 8 * 1024 * 1024;
	private static final Pattern OWNER = Pattern.compile("擁有者：\\s*<span[^>]*>([^<]{1,40})</span>");
	private static final Pattern TAGS = Pattern.compile("<[^>]+>");
	private static final Pattern ENTITY = Pattern.compile("&(#x[0-9a-fA-F]{1,6}|#[0-9]{1,7}|amp|lt|gt|quot|apos|nbsp);");
	private static final Pattern COLOR = Pattern.compile("[&§][0-9a-fk-orA-FK-OR]");
	private static final Pattern HOME = Pattern.compile("^/(phome [A-Za-z0-9_]{1,16}\\.[^\\p{Cntrl}§]{1,64})$");
	static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).followRedirects(HttpClient.Redirect.NORMAL).build();

	public enum AreaKind { RESIDENCE, BORDER, OTHER }

	public record Poi(String label, String detail, double x, double z) {}

	public record Area(AreaKind kind, String label, String owner, double[] xs, double[] zs, int line, int fill,
			double minX, double minZ, double maxX, double maxZ) {
		public boolean contains(double x, double z) {
			if (x < minX || x > maxX || z < minZ || z > maxZ) {
				return false;
			}
			boolean inside = false;
			for (int i = 0, j = xs.length - 1; i < xs.length; j = i++) {
				if ((zs[i] > z) != (zs[j] > z) && x < (xs[j] - xs[i]) * (z - zs[i]) / (zs[j] - zs[i]) + xs[i]) {
					inside = !inside;
				}
			}
			return inside;
		}
	}

	public record Data(List<Poi> pois, List<Area> areas) {
		static final Data EMPTY = new Data(List.of(), List.of());
	}

	private static final class Entry {
		Data data = Data.EMPTY;
		long checkedAt;
		boolean loading;
	}

	private static final Map<String, Entry> ENTRIES = new HashMap<>();
	private static Set<String> maps;
	private static boolean mapsLoading;
	private static long mapsAt;

	private MapMarkers() {}

	private static Path cache(String map) {
		return FabricLoader.getInstance().getConfigDir().resolve("brilliant-server-mod").resolve("map-cache").resolve("markers").resolve(map + ".json");
	}

	public static void refreshMaps() {
		if (mapsLoading || maps != null && System.currentTimeMillis() - mapsAt < REFRESH_MS) {
			return;
		}
		mapsLoading = true;
		HttpRequest request = HttpRequest.newBuilder(URI.create(BASE + "/settings.json")).timeout(Duration.ofSeconds(15))
				.header("User-Agent", RPConfig.USER_AGENT).GET().build();
		HTTP.sendAsync(request, HttpResponse.BodyHandlers.ofString()).whenComplete((response, error) -> Minecraft.getInstance().execute(() -> {
			mapsLoading = false;
			mapsAt = System.currentTimeMillis();
			if (error != null || response.statusCode() != 200) {
				return;
			}
			try {
				Set<String> found = new HashSet<>();
				for (JsonElement map : JsonParser.parseString(response.body()).getAsJsonObject().getAsJsonArray("maps")) {
					String id = map.getAsString();
					if (id.matches("[a-z0-9_]{1,64}")) {
						found.add(id);
					}
				}
				maps = found;
			} catch (RuntimeException ignored) {
			}
		}));
	}

	public static String mapId(String shard, String dimension) {
		if (maps == null || shard == null || !shard.endsWith("." + RPConfig.BASE_DOMAIN)) {
			return null;
		}
		String world = switch (dimension) {
			case "minecraft:overworld" -> "world";
			case "minecraft:the_nether" -> "world_nether";
			case "minecraft:the_end" -> "world_the_end";
			default -> null;
		};
		String id = world == null ? null : shard.substring(0, shard.indexOf('.')) + "_" + world;
		return id != null && maps.contains(id) ? id : null;
	}

	public static Data get(String map) {
		if (map == null) {
			return Data.EMPTY;
		}
		Entry entry = ENTRIES.computeIfAbsent(map, key -> new Entry());
		if (!entry.loading && System.currentTimeMillis() - entry.checkedAt > REFRESH_MS) {
			load(map, entry);
		}
		return entry.data;
	}

	private static void load(String map, Entry entry) {
		entry.loading = true;
		entry.checkedAt = System.currentTimeMillis();
		boolean first = entry.data == Data.EMPTY;
		Thread.ofVirtual().start(() -> {
			Path file = cache(map);
			if (first && Files.exists(file)) {
				try {
					Data cached = parse(Files.readString(file, StandardCharsets.UTF_8));
					Minecraft.getInstance().execute(() -> entry.data = cached);
				} catch (Exception ignored) {
				}
			}
			try {
				HttpRequest request = HttpRequest.newBuilder(URI.create(BASE + "/maps/" + map + "/live/markers.json")).timeout(Duration.ofSeconds(20))
						.header("User-Agent", RPConfig.USER_AGENT).GET().build();
				HttpResponse<byte[]> response = HTTP.send(request, HttpResponse.BodyHandlers.ofByteArray());
				if (response.statusCode() == 200 && response.body().length <= MAX_BYTES) {
					String body = new String(response.body(), StandardCharsets.UTF_8);
					Data data = parse(body);
					Files.createDirectories(file.getParent());
					Files.writeString(file, body, StandardCharsets.UTF_8);
					Minecraft.getInstance().execute(() -> entry.data = data);
				}
			} catch (Exception ignored) {
			}
			Minecraft.getInstance().execute(() -> entry.loading = false);
		});
	}

	public record Regen(Map<Long, Integer> minutes, long fetchedAt) {
		static final Regen EMPTY = new Regen(Map.of(), 0);

		public Integer minutesLeft(int chunkX, int chunkZ) {
			Integer stored = minutes.get(key(chunkX, chunkZ));
			return stored == null ? null : stored - (int) ((System.currentTimeMillis() - fetchedAt) / 60000);
		}
	}

	private static final class RegenEntry {
		Regen data = Regen.EMPTY;
		long checkedAt;
		boolean loading;
	}

	private static final Map<String, RegenEntry> REGEN = new HashMap<>();

	public static long key(int chunkX, int chunkZ) {
		return (long) chunkX << 32 | chunkZ & 0xFFFFFFFFL;
	}

	public static Regen regen(String shard, String dimension) {
		if (shard == null || !shard.matches("s[1-4]\\.brilliantw\\.net")) {
			return Regen.EMPTY;
		}
		String world = switch (dimension) {
			case "minecraft:overworld" -> "world";
			case "minecraft:the_nether" -> "world_nether";
			case "minecraft:the_end" -> "world_the_end";
			default -> null;
		};
		if (world == null) {
			return Regen.EMPTY;
		}
		String url = RPConfig.WEBSITE_URL + "/naturerevive/chunks.json?server=" + shard.charAt(1) + "&world=" + world;
		RegenEntry entry = REGEN.computeIfAbsent(url, key -> new RegenEntry());
		if (!entry.loading && System.currentTimeMillis() - entry.checkedAt > REFRESH_MS) {
			entry.loading = true;
			entry.checkedAt = System.currentTimeMillis();
			HttpRequest request = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(20)).header("User-Agent", RPConfig.USER_AGENT).GET().build();
			HTTP.sendAsync(request, HttpResponse.BodyHandlers.ofString()).whenComplete((response, error) -> {
				Regen parsed = null;
				if (error == null && response.statusCode() == 200 && response.body().length() <= MAX_BYTES) {
					try {
						parsed = parseRegen(response.body());
					} catch (RuntimeException ignored) {
					}
				}
				Regen result = parsed;
				Minecraft.getInstance().execute(() -> {
					entry.loading = false;
					if (result != null) {
						entry.data = result;
					}
				});
			});
		}
		return entry.data;
	}

	static Regen parseRegen(String json) {
		JsonArray chunks = JsonParser.parseString(json).getAsJsonObject().getAsJsonArray("chunks");
		Map<Long, Integer> minutes = new HashMap<>(chunks.size() / 3 * 2);
		for (int i = 0; i + 2 < chunks.size(); i += 3) {
			minutes.put(key(chunks.get(i).getAsInt(), chunks.get(i + 1).getAsInt()), chunks.get(i + 2).getAsInt());
		}
		return new Regen(minutes, System.currentTimeMillis());
	}

	public static String homeOwner(Poi poi) {
		String text = poi.label().strip().replaceFirst("^/phome\\s+", "");
		int dot = text.indexOf('.');
		return dot > 0 ? text.substring(0, dot) : "";
	}

	public static String homeName(Poi poi) {
		String text = poi.label().strip().replaceFirst("^/phome\\s+", "");
		int dot = text.indexOf('.');
		return COLOR.matcher(dot > 0 ? text.substring(dot + 1) : text).replaceAll("");
	}

	public static String homeCommand(Poi poi) {
		Matcher matcher = HOME.matcher(poi.label().strip());
		return matcher.matches() ? matcher.group(1) : null;
	}

	public static String regenText(int minutes) {
		if (minutes <= 0) {
			return "已可再生";
		}
		int days = minutes / 1440;
		int hours = minutes % 1440 / 60;
		int mins = minutes % 60;
		return "剩餘 " + (days > 0 ? days + " 天 " : "") + (days > 0 || hours > 0 ? hours + " 小時 " : "") + mins + " 分鐘";
	}

	static Data parse(String json) {
		List<Poi> pois = new ArrayList<>();
		List<Area> areas = new ArrayList<>();
		JsonObject root = JsonParser.parseString(json).getAsJsonObject();
		for (Map.Entry<String, JsonElement> set : root.entrySet()) {
			if (!set.getValue().isJsonObject() || !set.getValue().getAsJsonObject().has("markers")) {
				continue;
			}
			String setId = set.getKey();
			for (Map.Entry<String, JsonElement> item : set.getValue().getAsJsonObject().getAsJsonObject("markers").entrySet()) {
				if (!item.getValue().isJsonObject()) {
					continue;
				}
				JsonObject marker = item.getValue().getAsJsonObject();
				String type = text(marker, "type");
				String label = clean(text(marker, "label"));
				String detail = text(marker, "detail");
				if (type.equals("poi") && marker.has("position")) {
					JsonObject position = marker.getAsJsonObject("position");
					pois.add(new Poi(label, clean(detail), position.get("x").getAsDouble(), position.get("z").getAsDouble()));
				} else if ((type.equals("shape") || type.equals("extrude")) && marker.has("shape") && marker.get("shape").isJsonArray()) {
					JsonArray shape = marker.getAsJsonArray("shape");
					if (shape.size() < 3) {
						continue;
					}
					double[] xs = new double[shape.size()];
					double[] zs = new double[shape.size()];
					double minX = Double.MAX_VALUE;
					double minZ = Double.MAX_VALUE;
					double maxX = -Double.MAX_VALUE;
					double maxZ = -Double.MAX_VALUE;
					for (int i = 0; i < shape.size(); i++) {
						JsonObject point = shape.get(i).getAsJsonObject();
						xs[i] = point.get("x").getAsDouble();
						zs[i] = point.get("z").getAsDouble();
						minX = Math.min(minX, xs[i]);
						minZ = Math.min(minZ, zs[i]);
						maxX = Math.max(maxX, xs[i]);
						maxZ = Math.max(maxZ, zs[i]);
					}
					Matcher owner = OWNER.matcher(detail);
					AreaKind kind = setId.toLowerCase().contains("residence") ? AreaKind.RESIDENCE : setId.contains("border") ? AreaKind.BORDER : AreaKind.OTHER;
					areas.add(new Area(kind, label, owner.find() ? owner.group(1).strip() : "", xs, zs,
							color(marker, "lineColor", 0xFFFF7A7A), color(marker, "fillColor", 0x40FF7A7A), minX, minZ, maxX, maxZ));
				}
			}
		}
		return new Data(List.copyOf(pois), List.copyOf(areas));
	}

	private static String text(JsonObject object, String key) {
		return object.has(key) && object.get(key).isJsonPrimitive() ? object.get(key).getAsString() : "";
	}

	private static String clean(String html) {
		return unescape(TAGS.matcher(html.replace("<br />", " ").replace("<br>", " ")).replaceAll("")).strip();
	}

	static String unescape(String text) {
		return ENTITY.matcher(text).replaceAll(m -> Matcher.quoteReplacement(switch (m.group(1)) {
			case "amp" -> "&";
			case "lt" -> "<";
			case "gt" -> ">";
			case "quot" -> "\"";
			case "apos" -> "'";
			case "nbsp" -> " ";
			default -> {
				int cp = m.group(1).startsWith("#x") ? Integer.parseInt(m.group(1).substring(2), 16) : Integer.parseInt(m.group(1).substring(1));
				yield Character.isValidCodePoint(cp) ? Character.toString(cp) : "";
			}
		}));
	}

	private static int color(JsonObject marker, String key, int fallback) {
		if (!marker.has(key) || !marker.get(key).isJsonObject()) {
			return fallback;
		}
		JsonObject c = marker.getAsJsonObject(key);
		int a = (int) Math.round(Math.clamp(c.has("a") ? c.get("a").getAsDouble() : 1, 0, 1) * 255);
		return a << 24 | (c.get("r").getAsInt() & 0xFF) << 16 | (c.get("g").getAsInt() & 0xFF) << 8 | c.get("b").getAsInt() & 0xFF;
	}

	public static void main(String[] args) {
		String json = "{\"homes\":{\"markers\":{\"a\":{\"type\":\"poi\",\"label\":\"/phome SKYOD1003.1服_&amp;e煌新聞&amp;6基地\",\"detail\":\"\",\"position\":{\"x\":1,\"z\":2}}}}}";
		Poi poi = parse(json).pois().getFirst();
		check("phome SKYOD1003.1服_&e煌新聞&6基地".equals(homeCommand(poi)), "command keeps the real name");
		check("1服_煌新聞基地".equals(homeName(poi)) && "SKYOD1003".equals(homeOwner(poi)), "display name drops color codes");
		check(unescape("&amp;lt;&#65;&#x42;&nbsp;").equals("&lt;AB "), "entities decode once");
		check(homeCommand(new Poi(clean("/phome a.b&#10;/op x"), "", 0, 0)) == null, "decoded line breaks never reach a command");
		System.out.println("ok");
	}

	private static void check(boolean ok, String name) {
		if (!ok) {
			throw new AssertionError(name);
		}
	}
}
