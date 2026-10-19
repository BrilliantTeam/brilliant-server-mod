package net.brilliantw.richpresence;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.NativeImage;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Consumer;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.biome.Biome;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class SeedMaps {
	public static final int TILE = 64;
	public static final int LODS = 3;
	//? if >=26.3 {
	/*public static final boolean SAME_AS_SERVER = true;
	*///?} else {
	public static final boolean SAME_AS_SERVER = false;
	//?}
	private static final Logger LOGGER = LoggerFactory.getLogger("brilliant-server-mod");
	private static final long REFRESH_MS = 60 * 60 * 1000;
	private static final int MAX_TILES = 700;

	public record TileKey(String world, String layer, int lod, int tx, int tz) {}

	public record CellKey(String world, int cx, int cz) {}

	public static final class Tile {
		final short[] ids;
		final SeedWorld world;
		DynamicTexture dynamic;
		Identifier texture;
		int painted = Integer.MIN_VALUE;
		long usedAt;

		Tile(short[] ids, SeedWorld world) {
			this.ids = ids;
			this.world = world;
		}
	}

	private static Map<String, Map<String, Long>> seeds = Map.of();
	private static boolean seedsLoading;
	private static long seedsAt;
	private static final Map<String, SeedWorld> WORLDS = new ConcurrentHashMap<>();
	private static final Map<String, String> FAILED = new ConcurrentHashMap<>();
	private static final Map<TileKey, Tile> TILES = new ConcurrentHashMap<>();
	private static final Map<CellKey, List<SeedWorld.Found>> CELLS = new ConcurrentHashMap<>();
	private static final ConcurrentLinkedQueue<Runnable> JOBS = new ConcurrentLinkedQueue<>();
	private static final Object LOCK = new Object();
	private static volatile List<Object> wanted = List.of();
	private static volatile String building;
	private static Thread worker;
	private static int counter;
	private static long evictedAt;

	private SeedMaps() {}

	private static Path cache() {
		return FabricLoader.getInstance().getConfigDir().resolve("brilliant-server-mod").resolve("map-cache").resolve("seeds.json");
	}

	private static String dimensionKey(String dimension) {
		return switch (dimension) {
			case "minecraft:overworld" -> "overworld";
			case "minecraft:the_nether" -> "nether";
			case "minecraft:the_end" -> "end";
			default -> null;
		};
	}

	static Map<String, Map<String, Long>> parseSeeds(String json) {
		Map<String, Map<String, Long>> out = new HashMap<>();
		for (Map.Entry<String, JsonElement> server : JsonParser.parseString(json).getAsJsonObject().getAsJsonObject("seeds").entrySet()) {
			Map<String, Long> dims = new HashMap<>();
			for (Map.Entry<String, JsonElement> dim : server.getValue().getAsJsonObject().entrySet()) {
				try {
					dims.put(dim.getKey(), Long.parseLong(dim.getValue().getAsString().trim()));
				} catch (RuntimeException ignored) {
				}
			}
			out.put(server.getKey(), Map.copyOf(dims));
		}
		return Map.copyOf(out);
	}

	private static void refreshSeeds() {
		if (seedsLoading || System.currentTimeMillis() - seedsAt < REFRESH_MS) {
			return;
		}
		seedsLoading = true;
		seedsAt = System.currentTimeMillis();
		if (seeds.isEmpty()) {
			try {
				Path file = cache();
				if (Files.exists(file)) {
					seeds = parseSeeds(Files.readString(file, StandardCharsets.UTF_8));
				}
			} catch (Exception ignored) {
			}
		}
		HttpRequest request = HttpRequest.newBuilder(URI.create(RPConfig.WEBSITE_URL + "/api/mod/seeds")).timeout(Duration.ofSeconds(15))
				.header("User-Agent", RPConfig.USER_AGENT).GET().build();
		MapMarkers.HTTP.sendAsync(request, HttpResponse.BodyHandlers.ofString()).whenComplete((response, error) -> {
			Map<String, Map<String, Long>> parsed = null;
			if (error == null && response.statusCode() == 200 && response.body().length() < 64 * 1024) {
				try {
					parsed = parseSeeds(response.body());
					Path file = cache();
					Files.createDirectories(file.getParent());
					Files.writeString(file, response.body(), StandardCharsets.UTF_8);
				} catch (Exception ignored) {
					parsed = null;
				}
			}
			Map<String, Map<String, Long>> result = parsed;
			Minecraft.getInstance().execute(() -> {
				seedsLoading = false;
				if (result != null) {
					seeds = result;
				}
			});
		});
	}

	public static Long seed(WorldMaps.View view) {
		refreshSeeds();
		String dim = dimensionKey(view.dimension());
		Map<String, Long> server = seeds.get(view.shard());
		return dim == null || server == null ? null : server.get(dim);
	}

	public static String worldKey(WorldMaps.View view) {
		Long seed = seed(view);
		return seed == null ? null : seed + "|" + view.dimension();
	}

	public static String failure(WorldMaps.View view) {
		String key = worldKey(view);
		return key == null ? null : FAILED.get(key);
	}

	public static boolean surface(WorldMaps.View view) {
		return !(view.cave() && view.dimension().equals("minecraft:overworld"));
	}

	public static int sampleY(WorldMaps.View view) {
		return surface(view) ? 64 : view.band() * WorldMaps.BAND + WorldMaps.BAND / 2;
	}

	public static SeedWorld world(WorldMaps.View view) {
		String key = worldKey(view);
		if (key == null) {
			return null;
		}
		SeedWorld world = WORLDS.get(key);
		if (world == null && building == null && !FAILED.containsKey(key)) {
			Map<ResourceKey<Biome>, List<TagKey<Biome>>> tags = clientTags();
			long seed = seed(view);
			String dimension = view.dimension();
			building = key;
			submit(() -> {
				try {
					SeedWorld.lookup().lookupOrThrow(Registries.BIOME).listElements()
							.forEach(ref -> SeedWorld.bindTags(ref, tags.getOrDefault(ref.key(), List.of())));
					WORLDS.put(key, new SeedWorld(seed, dimension));
				} catch (Throwable t) {
					FAILED.put(key, String.valueOf(t.getMessage()));
					LOGGER.warn("Seed map failed", t);
				} finally {
					building = null;
				}
			});
		}
		return world;
	}

	public static boolean loading(WorldMaps.View view) {
		String key = worldKey(view);
		return key != null && key.equals(building);
	}

	private static Map<ResourceKey<Biome>, List<TagKey<Biome>>> clientTags() {
		Map<ResourceKey<Biome>, List<TagKey<Biome>>> out = new HashMap<>();
		ClientLevel level = Minecraft.getInstance().level;
		if (level != null) {
			level.registryAccess().lookupOrThrow(Registries.BIOME).listElements().forEach(ref -> out.put(ref.key(), ref.tags().toList()));
		}
		return out;
	}

	public static void want(List<Object> keys) {
		wanted = keys;
		synchronized (LOCK) {
			LOCK.notifyAll();
		}
	}

	private static void submit(Runnable job) {
		JOBS.add(job);
		ensureWorker();
		synchronized (LOCK) {
			LOCK.notifyAll();
		}
	}

	private static synchronized void ensureWorker() {
		if (worker != null) {
			return;
		}
		worker = new Thread(SeedMaps::loop, "brilliant-seed-map");
		worker.setDaemon(true);
		worker.setPriority(Thread.MIN_PRIORITY + 1);
		worker.start();
	}

	private static void loop() {
		while (true) {
			try {
				Runnable job = JOBS.poll();
				if (job != null) {
					job.run();
					continue;
				}
				Object next = null;
				for (Object key : wanted) {
					if (key instanceof TileKey t && !TILES.containsKey(t) || key instanceof CellKey c && !CELLS.containsKey(c)) {
						next = key;
						break;
					}
				}
				if (next == null) {
					synchronized (LOCK) {
						LOCK.wait(250);
					}
					continue;
				}
				compute(next);
			} catch (Throwable t) {
				LOGGER.warn("Seed map worker error", t);
			}
		}
	}

	public static int span(int lod) {
		return TILE * step(lod);
	}

	public static int step(int lod) {
		return 4 << (lod * 2);
	}

	private static void compute(Object key) {
		if (key instanceof TileKey t) {
			SeedWorld world = WORLDS.get(t.world());
			if (world == null) {
				TILES.put(t, new Tile(null, null));
				return;
			}
			boolean surface = t.layer().equals(WorldMaps.SURFACE) || !world.dimension().equals("minecraft:overworld");
			int y = surface ? 64 : WorldMaps.bandOf(t.layer()) * WorldMaps.BAND + WorldMaps.BAND / 2;
			int step = step(t.lod());
			int x0 = t.tx() * span(t.lod());
			int z0 = t.tz() * span(t.lod());
			short[] ids = new short[TILE * TILE];
			for (int z = 0; z < TILE; z++) {
				for (int x = 0; x < TILE; x++) {
					ids[z * TILE + x] = (short) world.indexOf(world.biome(x0 + x * step + step / 2, y, z0 + z * step + step / 2, surface));
				}
			}
			TILES.put(t, new Tile(ids, world));
		} else if (key instanceof CellKey c) {
			SeedWorld world = WORLDS.get(c.world());
			CELLS.put(c, world == null ? List.of() : List.copyOf(world.cell(c.cx(), c.cz())));
		}
	}

	public static Tile tile(TileKey key) {
		Tile tile = TILES.get(key);
		if (tile != null) {
			tile.usedAt = System.currentTimeMillis();
		}
		return tile;
	}

	public static List<SeedWorld.Found> cell(CellKey key) {
		return CELLS.get(key);
	}

	public static Identifier texture(Tile tile, int highlight, boolean allowPaint) {
		if (tile.ids == null) {
			return null;
		}
		if (tile.dynamic == null) {
			if (!allowPaint) {
				return null;
			}
			tile.dynamic = new DynamicTexture(() -> "brilliant-server-mod seed tile", TILE, TILE, false);
			tile.texture = Identifier.fromNamespaceAndPath("brilliant-server-mod", "map/seed_" + counter++);
			Minecraft.getInstance().getTextureManager().register(tile.texture, tile.dynamic);
		}
		if (tile.painted != highlight) {
			if (!allowPaint && tile.painted != Integer.MIN_VALUE) {
				return tile.texture;
			}
			List<Holder<Biome>> biomes = tile.world.biomes();
			int[] palette = new int[biomes.size()];
			for (int i = 0; i < palette.length; i++) {
				palette[i] = SeedPalette.biomeColor(SeedWorld.path(biomes.get(i)));
			}
			NativeImage pixels = tile.dynamic.getPixels();
			for (int z = 0; z < TILE; z++) {
				for (int x = 0; x < TILE; x++) {
					int id = tile.ids[z * TILE + x];
					int color = id < palette.length ? palette[id] : 0xFF000000;
					boolean edge = x + 1 < TILE && tile.ids[z * TILE + x + 1] != id || z + 1 < TILE && tile.ids[(z + 1) * TILE + x] != id;
					if (edge) {
						color = MapMath.scale(color, 0.84);
					}
					if (highlight >= 0 && id != highlight) {
						color = MapMath.mix(color, 0xFF101418, 0.72) | 0xFF000000;
					}
					pixels.setPixel(x, z, color);
				}
			}
			tile.dynamic.upload();
			tile.painted = highlight;
		}
		return tile.texture;
	}

	public static void evict() {
		if (System.currentTimeMillis() - evictedAt < 1000 || TILES.size() <= MAX_TILES) {
			return;
		}
		evictedAt = System.currentTimeMillis();
		List<Map.Entry<TileKey, Tile>> entries = new ArrayList<>(TILES.entrySet());
		entries.sort((a, b) -> Long.compare(a.getValue().usedAt, b.getValue().usedAt));
		for (int i = 0; i < entries.size() - MAX_TILES; i++) {
			Tile tile = entries.get(i).getValue();
			TILES.remove(entries.get(i).getKey());
			if (tile.texture != null) {
				Minecraft.getInstance().getTextureManager().release(tile.texture);
			}
		}
		if (CELLS.size() > 4000) {
			CELLS.clear();
		}
	}

	public static int biomeAt(WorldMaps.View view, int x, int z) {
		String key = worldKey(view);
		if (key == null) {
			return -1;
		}
		for (int lod = 0; lod < LODS; lod++) {
			int span = span(lod);
			Tile tile = TILES.get(new TileKey(key, layer(view), lod, Math.floorDiv(x, span), Math.floorDiv(z, span)));
			if (tile != null && tile.ids != null) {
				int step = step(lod);
				return tile.ids[Math.floorMod(z, span) / step * TILE + Math.floorMod(x, span) / step];
			}
		}
		return -1;
	}

	public static String layer(WorldMaps.View view) {
		return surface(view) ? WorldMaps.SURFACE : view.layer();
	}

	public static String biomeName(Holder<Biome> biome) {
		return biome.unwrapKey().map(k -> Component.translatable("biome." + k.identifier().getNamespace() + "." + k.identifier().getPath()).getString()).orElse("?");
	}

	public static void nearestStructure(WorldMaps.View view, String group, int x, int z, Consumer<SeedWorld.Found> done) {
		SeedWorld world = world(view);
		if (world == null) {
			done.accept(null);
			return;
		}
		submit(() -> {
			SeedWorld.Found found = null;
			try {
				found = world.nearest(group, x, z, 24);
			} catch (Throwable t) {
				LOGGER.warn("Seed map search failed", t);
			}
			SeedWorld.Found result = found;
			Minecraft.getInstance().execute(() -> done.accept(result));
		});
	}

	public static void nearestBiome(WorldMaps.View view, Holder<Biome> biome, int x, int z, Consumer<BlockPos> done) {
		SeedWorld world = world(view);
		if (world == null) {
			done.accept(null);
			return;
		}
		boolean surface = surface(view);
		int y = sampleY(view);
		submit(() -> {
			BlockPos found = null;
			try {
				found = world.nearestBiome(biome, x, y, z, surface, 12000, 32);
			} catch (Throwable t) {
				LOGGER.warn("Seed map search failed", t);
			}
			BlockPos result = found;
			Minecraft.getInstance().execute(() -> done.accept(result));
		});
	}
}
