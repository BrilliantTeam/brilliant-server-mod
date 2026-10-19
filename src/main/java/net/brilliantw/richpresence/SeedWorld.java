package net.brilliantw.richpresence;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collection;
import java.util.concurrent.CompletableFuture;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.QuartPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.biome.MultiNoiseBiomeSource;
import net.minecraft.world.level.biome.MultiNoiseBiomeSourceParameterLists;
import net.minecraft.world.level.biome.TheEndBiomeSource;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.placement.ConcentricRingsStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;
//? if >=26.3 {
/*import net.minecraft.world.level.biome.BiomeResolver;
import net.minecraft.world.level.levelgen.densityfunction.SamplerContext;
*///?}

public final class SeedWorld {
	public static final int CELL = 32;

	public record Found(String id, String group, int x, int z) {}

	private static HolderLookup.Provider lookup;
	private static Method bindTags;
	private static Method generateRings;

	private final long seed;
	private final String dimension;
	private final BiomeSource source;
	private final RandomState random;
	private final Climate.Sampler sampler;
	//? if >=26.3 {
	/*private final BiomeResolver resolver;
	*///?}
	private final NoiseBasedChunkGenerator generator;
	private final ChunkGeneratorStructureState state;
	private final LevelHeightAccessor heights;
	private final Map<Holder<Biome>, Integer> index = new IdentityHashMap<>();
	private final List<Holder<Biome>> biomes = new ArrayList<>();
	private final Map<ConcentricRingsStructurePlacement, List<ChunkPos>> rings = new IdentityHashMap<>();

	public static synchronized HolderLookup.Provider lookup() {
		if (lookup == null) {
			//? if >=26.3 {
			/*lookup = VanillaRegistries.createWorldLookup();
			*///?} else {
			lookup = VanillaRegistries.createLookup();
			//?}
		}
		return lookup;
	}

	private static Method declared(Class<?> owner, Class<?> returns, Class<?>... params) {
		for (Method method : owner.getDeclaredMethods()) {
			if (!method.isSynthetic() && method.getReturnType() == returns && Arrays.equals(method.getParameterTypes(), params)) {
				method.setAccessible(true);
				return method;
			}
		}
		throw new IllegalStateException("missing " + owner.getSimpleName() + " method");
	}

	public static synchronized void bindTags(Holder.Reference<Biome> biome, Collection<TagKey<Biome>> tags) {
		if (bindTags == null) {
			bindTags = declared(Holder.Reference.class, void.class, Collection.class);
		}
		try {
			bindTags.invoke(biome, tags);
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException(e);
		}
	}

	@SuppressWarnings("unchecked")
	private List<ChunkPos> rings(Holder<StructureSet> holder, ConcentricRingsStructurePlacement placement) {
		return rings.computeIfAbsent(placement, key -> {
			try {
				synchronized (SeedWorld.class) {
					if (generateRings == null) {
						generateRings = declared(ChunkGeneratorStructureState.class, CompletableFuture.class, Holder.class, ConcentricRingsStructurePlacement.class);
					}
				}
				return ((CompletableFuture<List<ChunkPos>>) generateRings.invoke(state, holder, placement)).join();
			} catch (ReflectiveOperationException e) {
				throw new IllegalStateException(e);
			}
		});
	}

	public SeedWorld(long seed, String dimension) {
		HolderLookup.Provider p = lookup();
		this.seed = seed;
		this.dimension = dimension;
		ResourceKey<NoiseGeneratorSettings> settingsKey = switch (dimension) {
			case "minecraft:the_nether" -> NoiseGeneratorSettings.NETHER;
			case "minecraft:the_end" -> NoiseGeneratorSettings.END;
			default -> NoiseGeneratorSettings.OVERWORLD;
		};
		Holder<NoiseGeneratorSettings> settings = p.lookupOrThrow(Registries.NOISE_SETTINGS).getOrThrow(settingsKey);
		source = switch (dimension) {
			case "minecraft:the_nether" -> MultiNoiseBiomeSource.createFromPreset(p.lookupOrThrow(Registries.MULTI_NOISE_BIOME_SOURCE_PARAMETER_LIST).getOrThrow(MultiNoiseBiomeSourceParameterLists.NETHER));
			case "minecraft:the_end" -> TheEndBiomeSource.create(p.lookupOrThrow(Registries.BIOME));
			default -> MultiNoiseBiomeSource.createFromPreset(p.lookupOrThrow(Registries.MULTI_NOISE_BIOME_SOURCE_PARAMETER_LIST).getOrThrow(MultiNoiseBiomeSourceParameterLists.OVERWORLD));
		};
		//? if >=26.3 {
		/*random = RandomState.create(p.lookupOrThrow(Registries.NOISE), seed, settings.value());
		sampler = random.createClimateSampler(SamplerContext.EMPTY_UNCACHED);
		resolver = source.createUncachedResolver(random);
		*///?} else {
		random = RandomState.create(p, settingsKey, seed);
		sampler = random.sampler();
		//?}
		generator = new NoiseBasedChunkGenerator(source, settings);
		List<Holder<StructureSet>> sets = p.lookupOrThrow(Registries.STRUCTURE_SET).listElements()
				.filter(set -> set.value().structures().stream().anyMatch(entry -> source.possibleBiomes().stream().anyMatch(entry.structure().value().biomes()::contains)))
				.map(set -> (Holder<StructureSet>) set)
				.toList();
		try {
			//? if >=26.3 {
			/*Constructor<ChunkGeneratorStructureState> constructor = ChunkGeneratorStructureState.class.getDeclaredConstructor(RandomState.class, BiomeSource.class, long.class, ChunkPos.class, long.class, List.class);
			constructor.setAccessible(true);
			state = constructor.newInstance(random, source, seed, generator.getOrigin(random), seed, sets);
			*///?} else {
			Constructor<ChunkGeneratorStructureState> constructor = ChunkGeneratorStructureState.class.getDeclaredConstructor(RandomState.class, BiomeSource.class, long.class, long.class, List.class);
			constructor.setAccessible(true);
			state = constructor.newInstance(random, source, seed, seed, sets);
			//?}
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException(e);
		}
		heights = LevelHeightAccessor.create(settings.value().noiseSettings().minY(), settings.value().noiseSettings().height());
		source.possibleBiomes().stream()
				.sorted((a, b) -> a.unwrapKey().map(k -> k.identifier().toString()).orElse("").compareTo(b.unwrapKey().map(k -> k.identifier().toString()).orElse("")))
				.forEach(this::indexOf);
	}

	public long seed() {
		return seed;
	}

	public String dimension() {
		return dimension;
	}

	public List<Holder<Biome>> biomes() {
		return biomes;
	}

	public int indexOf(Holder<Biome> biome) {
		Integer i = index.get(biome);
		if (i == null) {
			i = biomes.size();
			index.put(biome, i);
			biomes.add(biome);
		}
		return i;
	}

	public List<String> groups() {
		List<String> out = new ArrayList<>();
		for (Holder<StructureSet> holder : state.possibleStructureSets()) {
			for (StructureSet.StructureSelectionEntry entry : holder.value().structures()) {
				String group = SeedPalette.group(path(entry.structure()));
				if (!out.contains(group)) {
					out.add(group);
				}
			}
		}
		List<String> order = SeedPalette.GROUPS.stream().map(SeedPalette.Group::id).toList();
		out.sort((a, b) -> {
			int ia = order.indexOf(a);
			int ib = order.indexOf(b);
			return Integer.compare(ia < 0 ? order.size() : ia, ib < 0 ? order.size() : ib);
		});
		return out;
	}

	public Holder<Biome> biome(int x, int y, int z, boolean surface) {
		int qx = QuartPos.fromBlock(x);
		int qy = QuartPos.fromBlock(y);
		int qz = QuartPos.fromBlock(z);
		if (surface && source instanceof MultiNoiseBiomeSource multi) {
			Climate.TargetPoint t = sampler.sample(qx, qy, qz);
			return multi.getNoiseBiome(new Climate.TargetPoint(t.temperature(), t.humidity(), t.continentalness(), t.erosion(), 0, t.weirdness()));
		}
		//? if >=26.3 {
		/*return resolver.getNoiseBiome(qx, qy, qz);
		*///?} else {
		return source.getNoiseBiome(qx, qy, qz, sampler);
		//?}
	}

	private int height(int x, int z) {
		return generator.getBaseHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG, heights, random) - 1;
	}

	public static String path(Holder<?> holder) {
		return holder.unwrapKey().map(k -> k.identifier().getPath()).orElse("");
	}

	private boolean valid(Holder<Structure> structure, int cx, int cz) {
		String id = path(structure);
		int x = (cx << 4) + 8;
		int z = (cz << 4) + 8;
		Holder<Biome> biome = switch (id) {
			case "ancient_city" -> biome(x, -27, z, false);
			case "trial_chambers" -> biome(x, -30, z, false);
			default -> biome(x, 64, z, true);
		};
		if (!structure.value().biomes().contains(biome)) {
			return false;
		}
		if (id.equals("end_city") || id.equals("mansion")) {
			int bx = (cx << 4) + 7;
			int bz = (cz << 4) + 7;
			int lowest = Math.min(Math.min(height(bx, bz), height(bx + 5, bz)), Math.min(height(bx, bz + 5), height(bx + 5, bz + 5)));
			return lowest >= 60;
		}
		return true;
	}

	private Holder<Structure> pick(StructureSet set, int cx, int cz) {
		List<StructureSet.StructureSelectionEntry> entries = set.structures();
		if (entries.size() == 1) {
			return valid(entries.get(0).structure(), cx, cz) ? entries.get(0).structure() : null;
		}
		List<StructureSet.StructureSelectionEntry> left = new ArrayList<>(entries);
		WorldgenRandom r = new WorldgenRandom(new LegacyRandomSource(0L));
		r.setLargeFeatureSeed(seed, cx, cz);
		int total = 0;
		for (StructureSet.StructureSelectionEntry entry : left) {
			total += entry.weight();
		}
		while (!left.isEmpty()) {
			int roll = r.nextInt(total);
			int k = 0;
			for (StructureSet.StructureSelectionEntry entry : left) {
				roll -= entry.weight();
				if (roll < 0) {
					break;
				}
				k++;
			}
			StructureSet.StructureSelectionEntry entry = left.get(k);
			if (valid(entry.structure(), cx, cz)) {
				return entry.structure();
			}
			left.remove(k);
			total -= entry.weight();
		}
		return null;
	}

	private boolean wanted(StructureSet set, Predicate<String> groups) {
		for (StructureSet.StructureSelectionEntry entry : set.structures()) {
			if (groups.test(SeedPalette.group(path(entry.structure())))) {
				return true;
			}
		}
		return false;
	}

	private void add(List<Found> out, StructurePlacement placement, Holder<Structure> picked, ChunkPos chunk) {
		BlockPos at = placement.getLocatePos(chunk);
		String id = path(picked);
		out.add(new Found(id, SeedPalette.group(id), at.getX(), at.getZ()));
	}

	public List<Found> structures(int minCx, int minCz, int maxCx, int maxCz, Predicate<String> groups) {
		List<Found> out = new ArrayList<>();
		for (Holder<StructureSet> holder : state.possibleStructureSets()) {
			StructureSet set = holder.value();
			if (!wanted(set, groups)) {
				continue;
			}
			if (set.placement() instanceof RandomSpreadStructurePlacement spread) {
				int spacing = spread.spacing();
				for (int rz = Math.floorDiv(minCz, spacing); rz <= Math.floorDiv(maxCz, spacing); rz++) {
					for (int rx = Math.floorDiv(minCx, spacing); rx <= Math.floorDiv(maxCx, spacing); rx++) {
						ChunkPos chunk = spread.getPotentialStructureChunk(seed, rx * spacing, rz * spacing);
						int cx = chunk.getMinBlockX() >> 4;
						int cz = chunk.getMinBlockZ() >> 4;
						if (cx < minCx || cx > maxCx || cz < minCz || cz > maxCz || !spread.isStructureChunk(state, cx, cz)) {
							continue;
						}
						Holder<Structure> picked = pick(set, cx, cz);
						if (picked != null) {
							add(out, spread, picked, chunk);
						}
					}
				}
			} else if (set.placement() instanceof ConcentricRingsStructurePlacement rings) {
				for (ChunkPos chunk : rings(holder, rings)) {
					int cx = chunk.getMinBlockX() >> 4;
					int cz = chunk.getMinBlockZ() >> 4;
					if (cx >= minCx && cx <= maxCx && cz >= minCz && cz <= maxCz) {
						Holder<Structure> picked = set.structures().get(0).structure();
						add(out, rings, picked, chunk);
					}
				}
			}
		}
		return out;
	}

	public List<Found> cell(int cellX, int cellZ) {
		return structures(cellX * CELL, cellZ * CELL, cellX * CELL + CELL - 1, cellZ * CELL + CELL - 1, group -> true);
	}

	public Found nearest(String group, int x, int z, int maxCells) {
		int ccx = Math.floorDiv(x >> 4, CELL);
		int ccz = Math.floorDiv(z >> 4, CELL);
		Found best = null;
		double bestDistance = Double.MAX_VALUE;
		int stopAt = maxCells;
		for (int ring = 0; ring <= Math.min(stopAt, maxCells); ring++) {
			for (int dz = -ring; dz <= ring; dz++) {
				for (int dx = -ring; dx <= ring; dx++) {
					if (Math.max(Math.abs(dx), Math.abs(dz)) != ring) {
						continue;
					}
					int cx = ccx + dx;
					int cz = ccz + dz;
					for (Found found : structures(cx * CELL, cz * CELL, cx * CELL + CELL - 1, cz * CELL + CELL - 1, group::equals)) {
						double d = Math.hypot(found.x() - x, found.z() - z);
						if (found.group().equals(group) && d < bestDistance) {
							bestDistance = d;
							best = found;
							stopAt = Math.min(stopAt, ring + 1);
						}
					}
				}
			}
		}
		return best;
	}

	public BlockPos nearestBiome(Holder<Biome> target, int x, int y, int z, boolean surface, int radius, int step) {
		if (biome(x, y, z, surface) == target) {
			return new BlockPos(x, y, z);
		}
		for (int r = step; r <= radius; r += step) {
			BlockPos best = null;
			double bestDistance = Double.MAX_VALUE;
			for (int d = -r; d <= r; d += step) {
				int[][] points = { { x + d, z - r }, { x + d, z + r }, { x - r, z + d }, { x + r, z + d } };
				for (int[] p : points) {
					if (biome(p[0], y, p[1], surface) == target) {
						double distance = Math.hypot(p[0] - x, p[1] - z);
						if (distance < bestDistance) {
							bestDistance = distance;
							best = new BlockPos(p[0], y, p[1]);
						}
					}
				}
			}
			if (best != null) {
				return best;
			}
		}
		return null;
	}
}
