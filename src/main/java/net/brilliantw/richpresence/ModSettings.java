package net.brilliantw.richpresence;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Map;
import net.fabricmc.loader.api.FabricLoader;

public final class ModSettings {
	public static final class Data {
		public boolean discordPresence = true;
		public boolean announcements = true;
		public boolean updateCheck = true;
		public boolean channelTabs = true;
		public boolean privateWindow = true;
		public boolean emoji = true;
		public int emojiTone;
		public Map<String, Integer> emojiRecent;
		public boolean imageLinks = true;
		public boolean aliases = true;
		public boolean recipeFix = true;
		public boolean slotNumbers = true;
		public boolean scoreboardLayout = true;
		public boolean anvilCost = true;
		public boolean nightVision;
		public boolean autoReconnect = true;
		public boolean autoReauth = true;
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
		public int minimapPixels = 112;
		public int minimapOpacity = 100;
		public int minimapIcons = 1;
		public int minimapText = 1;
		public boolean minimapTextBg;
		public boolean minimapDayNight;
		public boolean minimapChunk;
		public boolean minimapBiome;
		public boolean minimapTime;
		public boolean minimapFacing;
		public boolean mapGrid;
		public boolean mapSlime;
		public String accent = "sky";
		public int customAccent = 0x7AD3FF;
		public String tone = "graphite";
		public int settingsVersion;
		public float minimapX;
		public float minimapY;
		public boolean anyServer;
		public long anyServerAcceptedAt;
		public String anyServerAcceptedVersion = "";
		public java.util.Map<String, Integer> keyCombos = new java.util.HashMap<>();
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
		if (parsed.keyCombos == null) {
			parsed.keyCombos = new java.util.HashMap<>();
		}
		parsed.keyCombos.entrySet().removeIf(e -> e.getKey() == null || e.getValue() == null || !KeyCombos.CHOICES.contains(e.getValue()) || e.getValue() == 0);
		parsed.reconnectAttempts = Math.clamp(parsed.reconnectAttempts, 0, 1000);
		parsed.reconnectDelay = Math.clamp(parsed.reconnectDelay, 1, 600);
		if (parsed.settingsVersion < 2) {
			parsed.minimapPixels = new int[] { 80, 112, 144 }[Math.clamp(parsed.minimapSize, 0, 2)];
			parsed.minimapZoom = Math.clamp(parsed.minimapZoom, 0, 3) + 1;
			parsed.settingsVersion = 2;
		}
		parsed.minimapPixels = Math.clamp(parsed.minimapPixels, 48, 320);
		parsed.minimapZoom = Math.clamp(parsed.minimapZoom, 0, 5);
		parsed.minimapOpacity = Math.clamp(parsed.minimapOpacity, 20, 100);
		parsed.minimapIcons = Math.clamp(parsed.minimapIcons, 0, 2);
		parsed.minimapText = Math.clamp(parsed.minimapText, 0, 2);
		parsed.customAccent &= 0xFFFFFF;
		if (parsed.accent == null) {
			parsed.accent = "sky";
		}
		if (parsed.tone == null) {
			parsed.tone = "graphite";
		}
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
			Theme.apply(data);
		}
		return data;
	}

	public static void save() {
		Theme.apply(get());
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
