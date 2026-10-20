package net.brilliantw.richpresence;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class PerfSampler {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final int KEEP = 10;
	private static final long FLUSH_MS = 120_000;
	private static final List<Integer> samples = new ArrayList<>();
	private static long started;
	private static long lastSample;
	private static long lastTick;
	private static long lastFlush;
	private static int stutters;

	private PerfSampler() {}

	public static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(PerfSampler::tick);
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> finish());
		ClientLifecycleEvents.CLIENT_STOPPING.register(client -> finish());
	}

	private static void tick(Minecraft client) {
		long now = System.currentTimeMillis();
		if (client.level == null || client.player == null || client.isPaused()) {
			lastTick = 0;
			return;
		}
		if (started == 0) {
			started = now;
			lastFlush = now;
		}
		if (lastTick != 0 && now - lastTick > 250) {
			stutters++;
		}
		lastTick = now;
		if (now - lastSample >= 1000) {
			lastSample = now;
			samples.add(client.getFps());
		}
		if (now - lastFlush >= FLUSH_MS) {
			lastFlush = now;
			write();
		}
	}

	private static void finish() {
		write();
		samples.clear();
		started = 0;
		stutters = 0;
		lastTick = 0;
	}

	private static void write() {
		if (samples.size() < 30) {
			return;
		}
		List<Integer> sorted = new ArrayList<>(samples);
		Collections.sort(sorted);
		double avg = sorted.stream().mapToInt(Integer::intValue).average().orElse(0);
		JsonObject session = new JsonObject();
		session.addProperty("start", started / 1000);
		session.addProperty("seconds", (System.currentTimeMillis() - started) / 1000);
		session.addProperty("avgFps", Math.round(avg));
		session.addProperty("low1", sorted.get(sorted.size() / 100));
		session.addProperty("stutters", stutters);
		session.addProperty("renderDistance", Minecraft.getInstance().options.renderDistance().get());
		Path file = FabricLoader.getInstance().getConfigDir().resolve("brilliant-server-mod/perf.json");
		try {
			JsonArray old = new JsonArray();
			if (Files.exists(file)) {
				JsonObject root = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
				if (root.has("sessions")) {
					old = root.getAsJsonArray("sessions");
				}
			}
			JsonArray sessions = new JsonArray();
			for (int i = 0; i < old.size(); i++) {
				JsonObject s = old.get(i).getAsJsonObject();
				if (!s.has("start") || s.get("start").getAsLong() != started / 1000) {
					sessions.add(s);
				}
			}
			sessions.add(session);
			while (sessions.size() > KEEP) {
				sessions.remove(0);
			}
			JsonObject root = new JsonObject();
			root.add("sessions", sessions);
			Files.createDirectories(file.getParent());
			Files.writeString(file, GSON.toJson(root), StandardCharsets.UTF_8);
		} catch (Exception ignored) {
		}
	}
}
