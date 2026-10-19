package net.brilliantw.richpresence;

import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;

public final class WorldMaps {
	public static final String SURFACE = "surface";
	private static final int ENTER_TICKS = 20;
	private static final int LEAVE_TICKS = 10;
	private static final long FLUSH_MS = 30 * 1000;

	public record View(String shard, String dimension, String markers, String local, String layer) {
		public boolean cave() {
			return !layer.equals(SURFACE);
		}

		public String waypoints() {
			return shard + "|" + dimension;
		}

		public int band() {
			return cave() ? Integer.parseInt(layer.substring(5)) : 0;
		}
	}

	private static View current;
	private static int below;
	private static int above;
	private static boolean underground;
	private static long flushedAt = System.currentTimeMillis();

	private WorldMaps() {}

	public static View current() {
		return current;
	}

	private static String safe(String text) {
		return text.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._-]", "_");
	}

	public static View view(String shard, String dimension, String layer) {
		return new View(shard, dimension, MapMarkers.mapId(shard, dimension), safe(shard) + "/" + safe(dimension), layer);
	}

	public static int sample(View view, int x, int z) {
		return MapStore.sample(view.local(), view.layer(), x, z);
	}

	public static int height(View view, int x, int z) {
		return MapStore.height(view.local(), view.layer(), x, z);
	}

	public static MapMarkers.Data markers(View view) {
		return MapMarkers.get(view.markers());
	}

	public static MapMarkers.Regen regen(View view) {
		return MapMarkers.regen(view.shard(), view.dimension());
	}

	public static String dimensionLabel(String dimension) {
		return switch (dimension) {
			case "minecraft:overworld" -> "主世界";
			case "minecraft:the_nether" -> "地獄";
			case "minecraft:the_end" -> "終界";
			default -> dimension.substring(dimension.indexOf(':') + 1);
		};
	}

	public static String shardLabel(String shard) {
		String label = RPConfig.SERVER_LABELS.get(shard);
		if (label != null && label.contains("｜")) {
			return label.substring(label.indexOf('｜') + 1);
		}
		return shard.equals("local") ? "單人世界" : shard.replaceFirst("^server_", "");
	}

	public static String layerLabel(View view) {
		return view.cave() ? "洞穴 Y " + view.band() * 16 + "～" + (view.band() * 16 + 15) : "地表";
	}

	public static void tick(Minecraft client) {
		LocalPlayer player = client.player;
		ClientLevel level = client.level;
		String shard = player == null || level == null || !RichPresenceModClient.featuresActive() ? null : RichPresenceModClient.mapShard();
		if (shard == null) {
			if (current != null) {
				MapStore.flush();
				current = null;
			}
			return;
		}
		MapMarkers.refreshMaps();
		String dimension = level.dimension().identifier().toString();
		int feet = player.blockPosition().getY();
		int roof = MapStore.roofY(level, player.blockPosition().getX(), player.blockPosition().getZ());
		if (feet + 5 < roof) {
			below++;
			above = 0;
		} else {
			above++;
			below = 0;
		}
		if (below >= ENTER_TICKS) {
			underground = true;
		} else if (above >= LEAVE_TICKS) {
			underground = false;
		}
		boolean cave = level.dimensionType().hasCeiling() || ModSettings.get().mapCave && underground;
		View next = view(shard, dimension, cave ? "cave_" + Math.floorDiv(feet, 16) : SURFACE);
		if (!next.equals(current)) {
			MapStore.flush();
			current = next;
		}
		MapStore.scan(client, current.local(), current.layer(), cave ? feet : null, cave ? 3 : 4);
		markers(current);
		regen(current);
		player.getLastDeathLocation().ifPresent(death -> {
			if (death.dimension().identifier().toString().equals(dimension)) {
				Waypoints.setDeath(current.waypoints(), death.pos().getX(), death.pos().getY(), death.pos().getZ());
			}
		});
		if (System.currentTimeMillis() - flushedAt > FLUSH_MS) {
			flushedAt = System.currentTimeMillis();
			MapStore.flush();
		}
	}
}
