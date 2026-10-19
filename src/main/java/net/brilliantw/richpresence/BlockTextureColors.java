package net.brilliantw.richpresence;

import com.mojang.blaze3d.platform.NativeImage;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.world.level.block.Block;

public final class BlockTextureColors {
	public enum Tint { NONE, GRASS, FOLIAGE, WATER }

	public record Entry(int color, Tint tint) {}

	private static final Entry MISSING = new Entry(MapMath.UNKNOWN, Tint.NONE);
	private static final Set<String> GRASS = Set.of("grass_block", "short_grass", "tall_grass", "fern", "large_fern", "vine", "sugar_cane", "attached_melon_stem", "attached_pumpkin_stem");
	private static final Set<String> UNTINTED_LEAVES = Set.of("cherry_leaves", "azalea_leaves", "flowering_azalea_leaves", "pale_oak_leaves");
	private static final List<String> SUFFIXES = List.of("_slab", "_stairs", "_wall", "_fence_gate", "_fence", "_pressure_plate", "_button", "_carpet", "_trapdoor", "_door", "_sign", "_hanging_sign", "_wall_sign");
	private static final Map<Block, Entry> CACHE = new HashMap<>();

	private BlockTextureColors() {}

	public static void clear() {
		CACHE.clear();
	}

	public static Entry of(Block block) {
		Entry cached = CACHE.get(block);
		if (cached != null) {
			return cached;
		}
		Identifier id = BuiltInRegistries.BLOCK.getKey(block);
		String path = id.getPath();
		Tint tint = GRASS.contains(path) ? Tint.GRASS
				: path.endsWith("_leaves") && !UNTINTED_LEAVES.contains(path) ? Tint.FOLIAGE
				: path.equals("water") || path.equals("bubble_column") ? Tint.WATER
				: Tint.NONE;
		Entry entry = MISSING;
		for (String candidate : candidates(path)) {
			Integer color = average(Identifier.fromNamespaceAndPath(id.getNamespace(), "textures/block/" + candidate + ".png"));
			if (color != null) {
				entry = new Entry(color, tint);
				break;
			}
		}
		CACHE.put(block, entry);
		return entry;
	}

	static List<String> candidates(String path) {
		List<String> names = new ArrayList<>();
		if (path.equals("water") || path.equals("bubble_column")) {
			names.add("water_still");
		} else if (path.equals("lava")) {
			names.add("lava_still");
		}
		names.add(path + "_top");
		names.add(path);
		for (String suffix : SUFFIXES) {
			if (path.endsWith(suffix)) {
				String base = path.substring(0, path.length() - suffix.length());
				names.add(base + "_planks");
				names.add(base + "_top");
				names.add(base);
				names.add(base + "s");
				names.add(base.replace("_brick", "_bricks"));
				names.add(base.replace("_tile", "_tiles"));
				names.add(base + "_block");
				break;
			}
		}
		if (path.startsWith("potted_")) {
			names.add(path.substring(7));
		}
		if (path.endsWith("_wall_torch") || path.endsWith("_wall_banner") || path.endsWith("_wall_head") || path.endsWith("_wall_skull")) {
			names.add(path.replace("_wall", ""));
		}
		return names;
	}

	private static Integer average(Identifier texture) {
		Optional<Resource> resource = Minecraft.getInstance().getResourceManager().getResource(texture);
		if (resource.isEmpty()) {
			return null;
		}
		try (InputStream in = resource.get().open(); NativeImage image = NativeImage.read(in)) {
			int size = Math.min(image.getWidth(), image.getHeight());
			long r = 0;
			long g = 0;
			long b = 0;
			long weight = 0;
			for (int y = 0; y < size; y++) {
				for (int x = 0; x < image.getWidth(); x++) {
					int argb = image.getPixel(x, y);
					int a = argb >>> 24;
					if (a < 32) {
						continue;
					}
					r += (long) ((argb >> 16) & 0xFF) * a;
					g += (long) ((argb >> 8) & 0xFF) * a;
					b += (long) (argb & 0xFF) * a;
					weight += a;
				}
			}
			if (weight == 0) {
				return null;
			}
			return 0xFF000000 | (int) (r / weight) << 16 | (int) (g / weight) << 8 | (int) (b / weight);
		} catch (Exception e) {
			return null;
		}
	}

	public static int tinted(Entry entry, int tint) {
		int r = ((entry.color() >> 16) & 0xFF) * ((tint >> 16) & 0xFF) / 255;
		int g = ((entry.color() >> 8) & 0xFF) * ((tint >> 8) & 0xFF) / 255;
		int b = (entry.color() & 0xFF) * (tint & 0xFF) / 255;
		return 0xFF000000 | r << 16 | g << 8 | b;
	}
}
