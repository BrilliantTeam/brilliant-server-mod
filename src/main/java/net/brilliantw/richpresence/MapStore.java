package net.brilliantw.richpresence;

import com.mojang.blaze3d.platform.NativeImage;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.FoliageColor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.material.MapColor;

public final class MapStore {
	public static final int SIZE = 256;
	private static final int MAGIC = 0x42534D31;
	private static final int MAX_REGIONS = 96;
	private static final int OVERVIEW = 64;
	private static final int MAX_OVERVIEWS = 1024;
	private static final int ROCK = 0xFF26292D;
	private static final int ABYSS = 0xFF0B0D10;
	private static final int CAVE_UP = 3;
	private static final int CAVE_DOWN = 40;
	private static final ExecutorService IO = Executors.newSingleThreadExecutor(runnable -> {
		Thread thread = new Thread(runnable, "brilliant-map-store");
		thread.setDaemon(true);
		return thread;
	});

	public static final class Region {
		final String key;
		final Path file;
		final int[] color = new int[SIZE * SIZE];
		final short[] height = new short[SIZE * SIZE];
		boolean dirty;
		boolean textureStale = true;
		long usedAt;
		Identifier texture;
		DynamicTexture dynamic;

		Region(String key, Path file) {
			this.key = key;
			this.file = file;
		}
	}

	static final Map<String, Region> REGIONS = new LinkedHashMap<>(64, 0.75f, true) {
		@Override
		protected boolean removeEldestEntry(Map.Entry<String, Region> eldest) {
			Region region = eldest.getValue();
			if (size() <= MAX_REGIONS || inUse(region.usedAt)) {
				return false;
			}
			if (region.dirty) {
				save(region);
			}
			releaseTexture(region);
			return true;
		}
	};
	private static final class Overview {
		volatile int[] pixels;
		long usedAt;
		Identifier texture;
	}

	private static final Map<String, Overview> OVERVIEWS = new LinkedHashMap<>(256, 0.75f, true) {
		@Override
		protected boolean removeEldestEntry(Map.Entry<String, Overview> eldest) {
			if (size() <= MAX_OVERVIEWS || inUse(eldest.getValue().usedAt)) {
				return false;
			}
			release(eldest.getValue().texture);
			return true;
		}
	};
	private static final Map<String, List<Long>> KNOWN = new ConcurrentHashMap<>();
	private static final Map<String, Long> KNOWN_AT = new HashMap<>();
	private static final Map<Long, Long> SCANNED = new HashMap<>();
	private static final Map<String, Long> ABSENT = new HashMap<>();
	private static String scannedFor = "";
	private static int counter;

	private MapStore() {}

	private static boolean inUse(long usedAt) {
		return System.currentTimeMillis() - usedAt < 1000;
	}

	static Path root() {
		return FabricLoader.getInstance().getConfigDir().resolve("brilliant-server-mod").resolve("map-cache").resolve("local");
	}

	private static Path file(String world, String layer, int rx, int rz) {
		return root().resolve(world).resolve(layer).resolve(rx + "_" + rz + ".bin");
	}

	public static Region region(String world, String layer, int rx, int rz, boolean create) {
		String key = world + "/" + layer + "/" + rx + "/" + rz;
		Region region = REGIONS.get(key);
		if (region != null) {
			region.usedAt = System.currentTimeMillis();
			return region;
		}
		Long absentAt = ABSENT.get(key);
		if (!create && absentAt != null && System.currentTimeMillis() - absentAt < 5000) {
			return null;
		}
		Path file = file(world, layer, rx, rz);
		boolean exists = ModSettings.get().mapMemory && Files.exists(file);
		if (!exists && !create) {
			if (ABSENT.size() > 4096) {
				ABSENT.clear();
			}
			ABSENT.put(key, System.currentTimeMillis());
			return null;
		}
		ABSENT.remove(key);
		region = new Region(key, file);
		region.usedAt = System.currentTimeMillis();
		if (exists) {
			read(region);
		}
		REGIONS.put(key, region);
		return region;
	}

	public static Set<Long> known(String world, String layer) {
		String key = world + "/" + layer;
		long now = System.currentTimeMillis();
		Long at = KNOWN_AT.get(key);
		if (at == null || now - at > 5000) {
			KNOWN_AT.put(key, now);
			Path dir = root().resolve(world).resolve(layer);
			boolean memory = ModSettings.get().mapMemory;
			IO.execute(() -> {
				List<Long> found = new ArrayList<>();
				if (memory && Files.isDirectory(dir)) {
					try (var stream = Files.list(dir)) {
						stream.forEach(path -> {
							String name = path.getFileName().toString();
							int sep = name.indexOf('_');
							if (sep > 0 && name.endsWith(".bin")) {
								try {
									found.add(MapMarkers.key(Integer.parseInt(name.substring(0, sep)), Integer.parseInt(name.substring(sep + 1, name.length() - 4))));
								} catch (NumberFormatException ignored) {
								}
							}
						});
					} catch (Exception ignored) {
					}
				}
				KNOWN.put(key, found);
			});
		}
		Set<Long> result = new LinkedHashSet<>(KNOWN.getOrDefault(key, List.of()));
		String prefix = key + "/";
		for (String loaded : REGIONS.keySet()) {
			if (loaded.startsWith(prefix)) {
				String[] parts = loaded.substring(prefix.length()).split("/");
				result.add(MapMarkers.key(Integer.parseInt(parts[0]), Integer.parseInt(parts[1])));
			}
		}
		return result;
	}

	public static Identifier overview(String world, String layer, int rx, int rz) {
		String key = world + "/" + layer + "/" + rx + "/" + rz;
		Region region = REGIONS.get(key);
		if (region != null) {
			region.usedAt = System.currentTimeMillis();
			return texture(region);
		}
		Overview overview = OVERVIEWS.get(key);
		if (overview == null) {
			Overview created = new Overview();
			created.usedAt = System.currentTimeMillis();
			OVERVIEWS.put(key, created);
			Path file = file(world, layer, rx, rz);
			IO.execute(() -> {
				Region loaded = new Region(key, file);
				read(loaded);
				int step = SIZE / OVERVIEW;
				int[] pixels = new int[OVERVIEW * OVERVIEW];
				for (int i = 0; i < pixels.length; i++) {
					pixels[i] = loaded.color[i / OVERVIEW * step * SIZE + i % OVERVIEW * step];
				}
				created.pixels = pixels;
			});
			return null;
		}
		overview.usedAt = System.currentTimeMillis();
		int[] pixels = overview.pixels;
		if (overview.texture == null && pixels != null) {
			DynamicTexture dynamic = new DynamicTexture(() -> "brilliant-server-mod map overview", OVERVIEW, OVERVIEW, false);
			NativeImage image = dynamic.getPixels();
			for (int i = 0; i < pixels.length; i++) {
				image.setPixel(i % OVERVIEW, i / OVERVIEW, pixels[i]);
			}
			dynamic.upload();
			overview.pixels = null;
			overview.texture = Identifier.fromNamespaceAndPath("brilliant-server-mod", "map/overview_" + counter++);
			Minecraft.getInstance().getTextureManager().register(overview.texture, dynamic);
		}
		return overview.texture;
	}

	private static void release(Identifier texture) {
		if (texture != null) {
			Minecraft.getInstance().getTextureManager().release(texture);
		}
	}

	private static Region lastRegion;
	private static String lastWorld;
	private static String lastLayer;
	private static int lastX;
	private static int lastZ;
	private static boolean lastMissing;
	private static long lastMissingAt;

	public static int sample(String world, String layer, int x, int z) {
		int rx = MapMath.tileOf(x, SIZE);
		int rz = MapMath.tileOf(z, SIZE);
		Region region;
		if (lastX == rx && lastZ == rz && world.equals(lastWorld) && layer.equals(lastLayer) && (lastRegion != null || lastMissing && System.currentTimeMillis() - lastMissingAt < 1000)) {
			region = lastRegion;
		} else {
			region = region(world, layer, rx, rz, false);
			lastRegion = region;
			lastWorld = world;
			lastLayer = layer;
			lastX = rx;
			lastZ = rz;
			lastMissing = region == null;
			lastMissingAt = System.currentTimeMillis();
		}
		return region == null ? MapMath.UNKNOWN : region.color[MapMath.inTile(z, SIZE) * SIZE + MapMath.inTile(x, SIZE)];
	}

	public static int height(String world, String layer, int x, int z) {
		Region region = region(world, layer, MapMath.tileOf(x, SIZE), MapMath.tileOf(z, SIZE), false);
		if (region == null || region.color[MapMath.inTile(z, SIZE) * SIZE + MapMath.inTile(x, SIZE)] == MapMath.UNKNOWN) {
			return Integer.MIN_VALUE;
		}
		return region.height[MapMath.inTile(z, SIZE) * SIZE + MapMath.inTile(x, SIZE)];
	}

	private record Listing(long at, List<String> layers) {}

	private static final Map<String, Listing> LAYERS = new HashMap<>();

	public static List<String> layers(String world) {
		Listing cached = LAYERS.get(world);
		if (cached != null && System.currentTimeMillis() - cached.at() < 2000) {
			return cached.layers();
		}
		List<String> layers = new ArrayList<>();
		if (ModSettings.get().mapMemory) {
			try (var stream = Files.list(root().resolve(world))) {
				stream.filter(Files::isDirectory).forEach(dir -> layers.add(dir.getFileName().toString()));
			} catch (Exception ignored) {
			}
		}
		for (Region region : REGIONS.values()) {
			String prefix = world + "/";
			if (region.key.startsWith(prefix)) {
				String layer = region.key.substring(prefix.length(), region.key.indexOf('/', prefix.length()));
				if (!layers.contains(layer)) {
					layers.add(layer);
				}
			}
		}
		LAYERS.put(world, new Listing(System.currentTimeMillis(), List.copyOf(layers)));
		return LAYERS.get(world).layers();
	}

	public static void scan(Minecraft client, String world, String layer, Integer caveY, int budget) {
		ClientLevel level = client.level;
		LocalPlayer player = client.player;
		if (level == null || player == null) {
			return;
		}
		String context = world + "/" + layer;
		if (!context.equals(scannedFor)) {
			SCANNED.clear();
			scannedFor = context;
		}
		int pcx = player.blockPosition().getX() >> 4;
		int pcz = player.blockPosition().getZ() >> 4;
		int radius = Math.min(client.options.getEffectiveRenderDistance(), caveY != null ? 8 : 12);
		long now = System.currentTimeMillis();
		for (int ring = 0; ring <= radius && budget > 0; ring++) {
			for (int dx = -ring; dx <= ring && budget > 0; dx++) {
				for (int dz = -ring; dz <= ring && budget > 0; dz++) {
					if (Math.max(Math.abs(dx), Math.abs(dz)) != ring) {
						continue;
					}
					int cx = pcx + dx;
					int cz = pcz + dz;
					long key = (long) cx << 32 | cz & 0xFFFFFFFFL;
					Long last = SCANNED.get(key);
					long interval = ring <= 1 ? 1500 : ring <= 3 ? 6000 : 60000;
					if (last != null && now - last < interval) {
						continue;
					}
					LevelChunk chunk = level.getChunkSource().getChunk(cx, cz, ChunkStatus.FULL, false);
					if (chunk == null) {
						continue;
					}
					scanChunk(level, chunk, world, layer, caveY);
					SCANNED.put(key, now);
					budget--;
				}
			}
		}
	}

	private static void scanChunk(ClientLevel level, LevelChunk chunk, String world, String layer, Integer caveY) {
		int baseX = chunk.getPos().getMinBlockX();
		int baseZ = chunk.getPos().getMinBlockZ();
		Region region = region(world, layer, MapMath.tileOf(baseX, SIZE), MapMath.tileOf(baseZ, SIZE), true);
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		int minY = level.getMinY();
		int ox = MapMath.inTile(baseX, SIZE);
		int oz = MapMath.inTile(baseZ, SIZE);
		int[] heights = new int[16 * 16];
		int[] colors = new int[16 * 16];
		for (int lz = 0; lz < 16; lz++) {
			for (int lx = 0; lx < 16; lx++) {
				int x = baseX + lx;
				int z = baseZ + lz;
				int i = lz * 16 + lx;
				if (caveY == null) {
					int top = topY(chunk, lx, lz, false);
					if (top < minY) {
						continue;
					}
					pos.set(x, top, z);
					BlockState state = chunk.getBlockState(pos);
					int depth = 0;
					while (state.getFluidState().is(FluidTags.WATER) && depth < 24 && pos.getY() > minY) {
						depth++;
						pos.move(0, -1, 0);
						state = chunk.getBlockState(pos);
					}
					heights[i] = top;
					if (depth > 0) {
						pos.set(x, top, z);
						colors[i] = MapMath.scale(water(level, pos), MapMath.waterShade(depth));
					} else {
						colors[i] = colorOf(level, state, pos, false);
					}
				} else {
					int floor = Integer.MIN_VALUE;
					boolean sawAir = false;
					boolean allAir = true;
					for (int y = caveY + CAVE_UP; y >= Math.max(minY, caveY - CAVE_DOWN); y--) {
						pos.set(x, y, z);
						BlockState state = chunk.getBlockState(pos);
						boolean open = state.isAir() || state.getFluidState().isEmpty() && state.getCollisionShape(level, pos).isEmpty();
						if (open) {
							sawAir = true;
						} else {
							allAir = false;
							if (sawAir) {
								floor = y;
								break;
							}
						}
					}
					if (floor == Integer.MIN_VALUE) {
						heights[i] = caveY;
						colors[i] = allAir ? ABYSS : ROCK;
						continue;
					}
					pos.set(x, floor, z);
					BlockState state = chunk.getBlockState(pos);
					heights[i] = floor;
					int base = state.getFluidState().is(FluidTags.WATER) ? water(level, pos) : colorOf(level, state, pos, true);
					colors[i] = MapMath.scale(base, Math.clamp(1 + (floor - caveY) * 0.025, 0.55, 1.12));
				}
			}
		}
		for (int lz = 0; lz < 16; lz++) {
			for (int lx = 0; lx < 16; lx++) {
				int i = lz * 16 + lx;
				if (colors[i] == MapMath.UNKNOWN) {
					continue;
				}
				int ri = (oz + lz) * SIZE + ox + lx;
				int north = lz > 0 ? heights[i - 16] : neighbour(region, ri - SIZE, oz + lz > 0, heights[i]);
				int west = lx > 0 ? heights[i - 1] : neighbour(region, ri - 1, ox + lx > 0, heights[i]);
				int shaded = MapMath.scale(colors[i], MapMath.hillshade(heights[i], north, west));
				if (region.color[ri] != shaded || region.height[ri] != heights[i]) {
					region.color[ri] = shaded;
					region.height[ri] = (short) heights[i];
					region.dirty = true;
					region.textureStale = true;
				}
			}
		}
	}

	public static int topY(LevelChunk chunk, int lx, int lz, boolean solid) {
		LevelChunkSection[] sections = chunk.getSections();
		for (int s = chunk.getHighestFilledSectionIndex(); s >= 0; s--) {
			LevelChunkSection section = sections[s];
			if (section.hasOnlyAir()) {
				continue;
			}
			int baseY = chunk.getSectionYFromSectionIndex(s) << 4;
			for (int ly = 15; ly >= 0; ly--) {
				BlockState state = section.getBlockState(lx, ly, lz);
				if (state.isAir()) {
					continue;
				}
				if (solid && (state.getBlock() instanceof LeavesBlock || state.getFluidState().isEmpty() && state.getCollisionShape(chunk, new BlockPos(chunk.getPos().getMinBlockX() + lx, baseY + ly, chunk.getPos().getMinBlockZ() + lz)).isEmpty())) {
					continue;
				}
				return baseY + ly;
			}
		}
		return chunk.getMinY() - 1;
	}

	public static int roofY(ClientLevel level, int x, int z) {
		LevelChunk chunk = level.getChunkSource().getChunk(x >> 4, z >> 4, ChunkStatus.FULL, false);
		return chunk == null ? Integer.MIN_VALUE : topY(chunk, x & 15, z & 15, true) + 1;
	}

	private static int neighbour(Region region, int index, boolean inside, int fallback) {
		return inside && region.color[index] != MapMath.UNKNOWN ? region.height[index] : fallback;
	}

	private static Biome biome(ClientLevel level, BlockPos pos) {
		return level.getBiome(pos).value();
	}

	private static BlockState mask(BlockState state, boolean cave) {
		String path = BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
		if (path.endsWith("_ore") || path.equals("ancient_debris")) {
			return path.startsWith("deepslate_") ? Blocks.DEEPSLATE.defaultBlockState()
					: path.startsWith("nether_") || path.equals("ancient_debris") ? Blocks.NETHERRACK.defaultBlockState()
					: Blocks.STONE.defaultBlockState();
		}
		if (cave && (path.equals("spawner") || path.equals("trial_spawner") || path.equals("vault") || path.endsWith("chest") || path.equals("barrel"))) {
			return Blocks.STONE.defaultBlockState();
		}
		return state;
	}

	private static int colorOf(ClientLevel level, BlockState original, BlockPos pos, boolean cave) {
		BlockState state = mask(original, cave);
		BlockTextureColors.Entry entry = BlockTextureColors.of(state.getBlock());
		if (entry.color() == MapMath.UNKNOWN) {
			MapColor mapColor = state.getMapColor(level, pos);
			return mapColor == MapColor.NONE ? 0xFF6F747A : 0xFF000000 | mapColor.col;
		}
		return switch (entry.tint()) {
			case GRASS -> BlockTextureColors.tinted(entry, biome(level, pos).getGrassColor(pos.getX(), pos.getZ()));
			case FOLIAGE -> {
				String path = BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
				yield BlockTextureColors.tinted(entry, path.startsWith("spruce") ? FoliageColor.FOLIAGE_EVERGREEN
						: path.startsWith("birch") ? FoliageColor.FOLIAGE_BIRCH
						: path.startsWith("mangrove") ? FoliageColor.FOLIAGE_MANGROVE
						: biome(level, pos).getFoliageColor());
			}
			case WATER -> BlockTextureColors.tinted(entry, biome(level, pos).getWaterColor());
			case NONE -> entry.color();
		};
	}

	private static int water(ClientLevel level, BlockPos pos) {
		BlockTextureColors.Entry entry = BlockTextureColors.of(Blocks.WATER);
		int tint = biome(level, pos).getWaterColor();
		return entry.color() == MapMath.UNKNOWN ? 0xFF000000 | tint : BlockTextureColors.tinted(entry, tint);
	}

	private static void read(Region region) {
		try (DataInputStream in = new DataInputStream(new GZIPInputStream(Files.newInputStream(region.file)))) {
			if (in.readInt() != MAGIC) {
				return;
			}
			for (int i = 0; i < region.color.length; i++) {
				region.color[i] = in.readInt();
			}
			for (int i = 0; i < region.height.length; i++) {
				region.height[i] = in.readShort();
			}
		} catch (Exception ignored) {
		}
	}

	private static void save(Region region) {
		if (!ModSettings.get().mapMemory) {
			region.dirty = false;
			return;
		}
		int[] color = region.color.clone();
		short[] height = region.height.clone();
		Path file = region.file;
		region.dirty = false;
		Overview stale = OVERVIEWS.remove(region.key);
		if (stale != null) {
			release(stale.texture);
		}
		IO.execute(() -> {
			try {
				Files.createDirectories(file.getParent());
				Path temp = file.resolveSibling(file.getFileName() + ".tmp");
				try (DataOutputStream out = new DataOutputStream(new java.io.BufferedOutputStream(new GZIPOutputStream(Files.newOutputStream(temp))))) {
					out.writeInt(MAGIC);
					for (int value : color) {
						out.writeInt(value);
					}
					for (short value : height) {
						out.writeShort(value);
					}
				}
				Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
			} catch (Exception e) {
				System.err.println("[brilliant-server-mod] Failed to save map region: " + e.getMessage());
			}
		});
	}

	private static volatile long diskBytes = -1;
	private static long diskAt;

	public static long diskBytes() {
		long now = System.currentTimeMillis();
		if (now - diskAt > 5000) {
			diskAt = now;
			IO.execute(() -> diskBytes = size(root()));
		}
		return diskBytes;
	}

	public static String diskLabel() {
		long bytes = diskBytes();
		if (bytes < 0) {
			return "計算中⋯";
		}
		return bytes < 1024 * 1024 ? String.format("%.0f KB", bytes / 1024.0) : String.format("%.1f MB", bytes / 1024.0 / 1024.0);
	}

	private static long size(Path dir) {
		if (!Files.exists(dir)) {
			return 0;
		}
		try (var stream = Files.walk(dir)) {
			return stream.filter(Files::isRegularFile).mapToLong(path -> {
				try {
					return Files.size(path);
				} catch (Exception e) {
					return 0;
				}
			}).sum();
		} catch (Exception e) {
			return 0;
		}
	}

	public static void clear(String world) {
		String prefix = world == null ? "" : world + "/";
		REGIONS.values().removeIf(region -> {
			if (region.key.startsWith(prefix)) {
				releaseTexture(region);
				return true;
			}
			return false;
		});
		OVERVIEWS.entrySet().removeIf(entry -> {
			if (entry.getKey().startsWith(prefix)) {
				release(entry.getValue().texture);
				return true;
			}
			return false;
		});
		KNOWN.clear();
		KNOWN_AT.clear();
		ABSENT.clear();
		SCANNED.clear();
		LAYERS.clear();
		lastRegion = null;
		lastMissing = false;
		Path target = world == null ? root() : root().resolve(world);
		IO.execute(() -> {
			if (!Files.exists(target)) {
				return;
			}
			try (var stream = Files.walk(target)) {
				stream.sorted(java.util.Comparator.reverseOrder()).forEach(path -> {
					try {
						Files.deleteIfExists(path);
					} catch (Exception ignored) {
					}
				});
			} catch (Exception ignored) {
			}
		});
		diskAt = 0;
	}

	public static void flush() {
		for (Region region : REGIONS.values()) {
			if (region.dirty) {
				save(region);
			}
		}
	}

	public static void flushAndWait() {
		flush();
		try {
			IO.submit(() -> {}).get();
		} catch (Exception ignored) {
		}
	}

	public static Identifier texture(Region region) {
		if (region.dynamic == null) {
			region.dynamic = new DynamicTexture(() -> "brilliant-server-mod map region", SIZE, SIZE, false);
			region.texture = Identifier.fromNamespaceAndPath("brilliant-server-mod", "map/region_" + counter++);
			Minecraft.getInstance().getTextureManager().register(region.texture, region.dynamic);
			region.textureStale = true;
		}
		if (region.textureStale) {
			NativeImage pixels = region.dynamic.getPixels();
			for (int i = 0; i < region.color.length; i++) {
				pixels.setPixel(i % SIZE, i / SIZE, region.color[i]);
			}
			region.dynamic.upload();
			region.textureStale = false;
		}
		return region.texture;
	}

	private static void releaseTexture(Region region) {
		if (region.texture != null) {
			Minecraft.getInstance().getTextureManager().release(region.texture);
			region.texture = null;
			region.dynamic = null;
		}
	}
}
