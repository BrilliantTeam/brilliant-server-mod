package net.brilliantw.richpresence;

import com.google.gson.Gson;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import org.joml.Matrix3x2fStack;

public final class ScoreboardLayout {
	private static final class Settings {
		float dx;
		float dy;
		float scale = 1f;
	}

	public static final float MIN_SCALE = 0.5f;
	public static final float MAX_SCALE = 2f;
	private static final Gson GSON = new Gson();

	private static Settings settings;
	private static boolean pushed;

	private ScoreboardLayout() {}

	private static Path file() {
		return FabricLoader.getInstance().getConfigDir().resolve("brilliant-server-mod-hud.json");
	}

	private static Settings settings() {
		if (settings == null) {
			settings = new Settings();
			try {
				if (Files.exists(file())) {
					Settings loaded = GSON.fromJson(Files.readString(file(), StandardCharsets.UTF_8), Settings.class);
					if (loaded != null) {
						loaded.scale = Math.clamp(loaded.scale, MIN_SCALE, MAX_SCALE);
						settings = loaded;
					}
				}
			} catch (Exception ignored) {
			}
		}
		return settings;
	}

	public static void save() {
		try {
			Path temp = file().resolveSibling(file().getFileName() + ".tmp");
			Files.writeString(temp, GSON.toJson(settings()), StandardCharsets.UTF_8);
			Files.move(temp, file(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
		} catch (IOException e) {
			System.err.println("[brilliant-server-mod] Failed to save scoreboard layout: " + e.getMessage());
		}
	}

	public static float scale() {
		return settings().scale;
	}

	public static void move(double dx, double dy) {
		settings().dx += (float) dx;
		settings().dy += (float) dy;
	}

	public static void zoom(float delta) {
		settings().scale = Math.clamp(Math.round((settings().scale + delta) * 20f) / 20f, MIN_SCALE, MAX_SCALE);
	}

	public static void reset() {
		settings = new Settings();
	}

	static boolean isDefault() {
		Settings s = settings();
		return s.dx == 0 && s.dy == 0 && s.scale == 1f;
	}

	public static void begin(Matrix3x2fStack pose) {
		pushed = RichPresenceModClient.featuresActive() && ModSettings.get().scoreboardLayout && !isDefault();
		if (!pushed) {
			return;
		}
		Minecraft client = Minecraft.getInstance();
		float pivotX = client.getWindow().getGuiScaledWidth();
		float pivotY = client.getWindow().getGuiScaledHeight() / 2f;
		Settings s = settings();
		pose.pushMatrix();
		pose.translate(pivotX + s.dx, pivotY + s.dy);
		pose.scale(s.scale, s.scale);
		pose.translate(-pivotX, -pivotY);
	}

	public static void end(Matrix3x2fStack pose) {
		if (pushed) {
			pose.popMatrix();
			pushed = false;
		}
	}
}
