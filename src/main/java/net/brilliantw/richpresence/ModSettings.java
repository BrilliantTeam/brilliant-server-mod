package net.brilliantw.richpresence;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import net.fabricmc.loader.api.FabricLoader;

public final class ModSettings {
	public static final class Data {
		public boolean discordPresence = true;
		public boolean announcements = true;
		public boolean updateCheck = true;
		public boolean channelTabs = true;
		public boolean privateWindow = true;
		public boolean emoji = true;
		public boolean imageLinks = true;
		public boolean aliases = true;
		public boolean recipeFix = true;
		public boolean slotNumbers = true;
		public boolean scoreboardLayout = true;
		public boolean anvilCost = true;
		public boolean autoReconnect = true;
		public int reconnectAttempts;
		public int reconnectDelay = 1;
		public boolean minimap = true;
		public boolean minimapRotate;
		public boolean minimapCircle = true;
		public boolean minimapMobs = true;
		public boolean minimapPlayers = true;
		public boolean minimapCoords = true;
		public boolean minimapWaypoints = true;
		public boolean minimapFrame = true;
		public boolean minimapCompass = true;
		public boolean minimapMobIcons = true;
		public boolean minimapPlayerHeads = true;
		public boolean minimapInfo = true;
		public boolean mapCave = true;
		public boolean mapMemory = true;
		public boolean mapResidences = true;
		public boolean mapPublicHomes = true;
		public boolean mapRegen = true;
		public List<String> seedGroups;
		public int minimapSize = 1;
		public int minimapZoom = 1;
		public float minimapX;
		public float minimapY;
		public boolean anyServer;
		public long anyServerAcceptedAt;
		public String anyServerAcceptedVersion = "";
	}

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static Data data;

	private ModSettings() {}

	private static Path file() {
		return FabricLoader.getInstance().getConfigDir().resolve("brilliant-server-mod-settings.json");
	}

	static Data parse(String json) {
		Data parsed = null;
		try {
			parsed = json == null ? null : GSON.fromJson(json, Data.class);
		} catch (RuntimeException ignored) {
		}
		if (parsed == null) {
			parsed = new Data();
		}
		parsed.reconnectAttempts = Math.clamp(parsed.reconnectAttempts, 0, 1000);
		parsed.reconnectDelay = Math.clamp(parsed.reconnectDelay, 1, 600);
		parsed.minimapSize = Math.clamp(parsed.minimapSize, 0, 2);
		parsed.minimapZoom = Math.clamp(parsed.minimapZoom, 0, 3);
		if (parsed.anyServer && parsed.anyServerAcceptedAt <= 0) {
			parsed.anyServer = false;
		}
		return parsed;
	}

	public static Data get() {
		if (data == null) {
			String json = null;
			try {
				Path file = file();
				json = Files.exists(file) ? Files.readString(file, StandardCharsets.UTF_8) : null;
			} catch (Exception ignored) {
			}
			data = parse(json);
		}
		return data;
	}

	public static void save() {
		try {
			Path file = file();
			Path temp = file.resolveSibling(file.getFileName() + ".tmp");
			Files.writeString(temp, GSON.toJson(get()), StandardCharsets.UTF_8);
			Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
		} catch (Exception ignored) {
		}
	}

	public static void acceptAnyServer() {
		Data settings = get();
		settings.anyServer = true;
		settings.anyServerAcceptedAt = System.currentTimeMillis();
		settings.anyServerAcceptedVersion = RPConfig.MOD_VERSION;
		save();
	}
}
