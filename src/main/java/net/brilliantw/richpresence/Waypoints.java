package net.brilliantw.richpresence;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.loader.api.FabricLoader;

public final class Waypoints {
	public static final class Point {
		public String id = UUID.randomUUID().toString();
		public String name = "";
		public int x;
		public int y;
		public int z;
		public int color;
		public boolean visible = true;
		public boolean death;
	}

	public record Swatch(String name, int argb) {}

	public static final List<Swatch> COLORS = List.of(
			new Swatch("紅", 0xFFFF5D5D), new Swatch("橙", 0xFFFF9F43), new Swatch("黃", 0xFFFFD84D), new Swatch("綠", 0xFF5EDC6A),
			new Swatch("青", 0xFF4FD8D0), new Swatch("藍", 0xFF5B9CFF), new Swatch("紫", 0xFFB57BFF), new Swatch("白", 0xFFF2F4F6));
	public static final int DEATH_COLOR = 0xFFB0B6BD;
	public static final int MAX_PER_WORLD = 200;
	public static final int MAX_NAME = 24;

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static Map<String, List<Point>> data;

	private Waypoints() {}

	private static Path file() {
		return FabricLoader.getInstance().getConfigDir().resolve("brilliant-server-mod-waypoints.json");
	}

	private static Map<String, List<Point>> data() {
		if (data == null) {
			data = new LinkedHashMap<>();
			try {
				if (Files.exists(file())) {
					Map<String, List<Point>> loaded = GSON.fromJson(Files.readString(file(), StandardCharsets.UTF_8), new TypeToken<Map<String, List<Point>>>() {}.getType());
					if (loaded != null) {
						loaded.values().removeIf(list -> list == null);
						data = new LinkedHashMap<>(loaded);
					}
				}
			} catch (Exception ignored) {
			}
		}
		return data;
	}

	public static List<Point> list(String world) {
		return world == null ? List.of() : data().computeIfAbsent(world, key -> new ArrayList<>());
	}

	public static int color(Point point) {
		return point.death ? DEATH_COLOR : COLORS.get(Math.floorMod(point.color, COLORS.size())).argb();
	}

	public static String add(String world, String name, int x, int y, int z, int color) {
		List<Point> list = list(world);
		if (list.stream().filter(p -> !p.death).count() >= MAX_PER_WORLD) {
			return "這個世界最多只能有 " + MAX_PER_WORLD + " 個記錄點";
		}
		Point point = new Point();
		point.name = clean(name);
		point.x = x;
		point.y = y;
		point.z = z;
		point.color = color;
		list.add(point);
		save();
		return null;
	}

	public static String clean(String name) {
		String trimmed = name == null ? "" : name.strip();
		trimmed = trimmed.isEmpty() ? "記錄點" : trimmed;
		return trimmed.length() > MAX_NAME ? trimmed.substring(0, MAX_NAME) : trimmed;
	}

	public static void remove(String world, Point point) {
		list(world).remove(point);
		save();
	}

	public static void setDeath(String world, int x, int y, int z) {
		List<Point> list = list(world);
		Point point = list.stream().filter(p -> p.death).findFirst().orElse(null);
		if (point != null && point.x == x && point.y == y && point.z == z) {
			return;
		}
		if (point == null) {
			point = new Point();
			point.death = true;
			point.name = "上次死亡點";
			list.add(point);
		}
		point.x = x;
		point.y = y;
		point.z = z;
		point.visible = true;
		save();
	}

	public static void save() {
		try {
			Path temp = file().resolveSibling(file().getFileName() + ".tmp");
			Files.writeString(temp, GSON.toJson(data()), StandardCharsets.UTF_8);
			Files.move(temp, file(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
		} catch (Exception e) {
			System.err.println("[brilliant-server-mod] Failed to save waypoints: " + e.getMessage());
		}
	}
}
