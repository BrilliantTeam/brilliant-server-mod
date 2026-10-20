package net.brilliantw.richpresence;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

public final class ScreenshotMeta {
	private static final Gson GSON = new GsonBuilder().create();
	private static final int KEEP = 3000;
	private static long lastSeen = System.currentTimeMillis();
	private static int ticks;

	private ScreenshotMeta() {}

	public static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(ScreenshotMeta::tick);
	}

	private static void tick(Minecraft client) {
		if (++ticks % 10 != 0 || client.level == null || client.player == null) {
			return;
		}
		File[] files = new File(client.gameDirectory, "screenshots").listFiles((dir, name) -> name.endsWith(".png"));
		if (files == null) {
			return;
		}
		long newest = lastSeen;
		JsonObject added = new JsonObject();
		for (File file : files) {
			long modified = file.lastModified();
			if (modified > lastSeen) {
				newest = Math.max(newest, modified);
				added.add(file.getName(), describe(client, client.level, client.player));
			}
		}
		lastSeen = newest;
		if (added.size() > 0) {
			save(added);
		}
	}

	private static JsonObject describe(Minecraft client, ClientLevel level, Player player) {
		BlockPos pos = player.blockPosition();
		JsonObject meta = new JsonObject();
		meta.addProperty("dimension", level.dimension().identifier().toString());
		level.getBiome(pos).unwrapKey().ifPresent(key -> meta.addProperty("biome", key.identifier().toString()));
		meta.addProperty("biomeName", MapInfo.biome(level, pos));
		meta.addProperty("x", pos.getX());
		meta.addProperty("y", pos.getY());
		meta.addProperty("z", pos.getZ());
		long time = MapInfo.dayTime(level);
		if (time >= 0) {
			meta.addProperty("dayTime", time % 24000);
		}
		meta.addProperty("weather", level.isThundering() ? "thunder" : level.isRaining() ? "rain" : "clear");
		meta.addProperty("nearby", level.players().stream().filter(p -> p != player && p.distanceTo(player) < 24).count());
		ServerData server = client.getCurrentServer();
		if (server != null && server.ip != null) {
			String host = server.ip.toLowerCase(Locale.ROOT).split(":")[0];
			if (host.endsWith(RPConfig.BASE_DOMAIN)) {
				meta.addProperty("server", host);
			}
		}
		return meta;
	}

	private static void save(JsonObject added) {
		Path file = FabricLoader.getInstance().getConfigDir().resolve("brilliant-server-mod/screenshots.json");
		try {
			JsonObject root = Files.exists(file) ? JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject() : new JsonObject();
			added.entrySet().forEach(e -> root.add(e.getKey(), e.getValue()));
			while (root.size() > KEEP) {
				root.remove(root.keySet().iterator().next());
			}
			Files.createDirectories(file.getParent());
			Files.writeString(file, GSON.toJson(root), StandardCharsets.UTF_8);
		} catch (Exception ignored) {
		}
	}
}
