package net.brilliantw.richpresence;

import java.util.List;
import java.util.Map;

public final class SeedPalette {
	public record Group(String id, String name, int color, boolean shownByDefault, String icon) {}

	public static final List<Group> GROUPS = List.of(
			new Group("village", "村莊", 0xFF6BD36B, true, "bell"),
			new Group("stronghold", "要塞", 0xFFB28CFF, true, "ender_eye"),
			new Group("mansion", "林地府邸", 0xFFA0703C, true, "totem_of_undying"),
			new Group("monument", "海底神殿", 0xFF3FD0C8, true, "sponge"),
			new Group("outpost", "掠奪者前哨站", 0xFFB4BAC0, true, "crossbow"),
			new Group("desert_pyramid", "沙漠神殿", 0xFFF0D27A, true, "chiseled_sandstone"),
			new Group("jungle_pyramid", "叢林神殿", 0xFF5DB043, true, "mossy_cobblestone"),
			new Group("swamp_hut", "女巫小屋", 0xFF9A7CC8, true, "cauldron"),
			new Group("igloo", "雪屋", 0xFFE6F2FF, true, "snow_block"),
			new Group("ancient_city", "遠古城市", 0xFF2BC4CB, true, "echo_shard"),
			new Group("trial_chambers", "試煉密室", 0xFFE39A4A, true, "trial_key"),
			new Group("trail_ruins", "古蹟廢墟", 0xFFD49C7A, true, "brush"),
			new Group("abandoned_camp", "廢棄營地", 0xFFC8B48A, true, "campfire"),
			new Group("fortress", "地獄要塞", 0xFFE05252, true, "blaze_rod"),
			new Group("bastion", "堡壘遺蹟", 0xFFC9A227, true, "gilded_blackstone"),
			new Group("end_city", "終界城", 0xFFE6D6FF, true, "elytra"),
			new Group("shipwreck", "沉船", 0xFFB08A58, true, "oak_boat"),
			new Group("ocean_ruin", "海底廢墟", 0xFF7FB0C0, true, "suspicious_sand"),
			new Group("ruined_portal", "廢棄傳送門", 0xFFA45CF0, true, "crying_obsidian"),
			new Group("buried_treasure", "埋藏的寶藏", 0xFFFFD700, true, "heart_of_the_sea"),
			new Group("mineshaft", "廢棄礦坑", 0xFFB89870, true, "minecart"),
			new Group("nether_fossil", "地獄化石", 0xFFE8E8D8, true, "bone_block"));

	private static final Map<String, Integer> BIOMES = Map.ofEntries(
			Map.entry("ocean", 0xFF1A3A8C),
			Map.entry("deep_ocean", 0xFF10286A),
			Map.entry("warm_ocean", 0xFF1F6FB8),
			Map.entry("lukewarm_ocean", 0xFF1C5AA6),
			Map.entry("deep_lukewarm_ocean", 0xFF144684),
			Map.entry("cold_ocean", 0xFF2A3F80),
			Map.entry("deep_cold_ocean", 0xFF1D2D62),
			Map.entry("frozen_ocean", 0xFF7486C8),
			Map.entry("deep_frozen_ocean", 0xFF4E5E9E),
			Map.entry("river", 0xFF3060E0),
			Map.entry("frozen_river", 0xFFA0B4F0),
			Map.entry("beach", 0xFFE8D98A),
			Map.entry("snowy_beach", 0xFFF2EDD0),
			Map.entry("stony_shore", 0xFF9A9A86),
			Map.entry("plains", 0xFF8DB360),
			Map.entry("sunflower_plains", 0xFFB0CF6A),
			Map.entry("snowy_plains", 0xFFF0F4F8),
			Map.entry("ice_spikes", 0xFFB4DCE8),
			Map.entry("desert", 0xFFE8B44C),
			Map.entry("swamp", 0xFF4C7A5A),
			Map.entry("mangrove_swamp", 0xFF3E8C6A),
			Map.entry("forest", 0xFF2E7D32),
			Map.entry("flower_forest", 0xFF5AA048),
			Map.entry("birch_forest", 0xFF4F9A5E),
			Map.entry("old_growth_birch_forest", 0xFF6AAE7A),
			Map.entry("dark_forest", 0xFF2E4A1C),
			Map.entry("pale_garden", 0xFF9DA39A),
			Map.entry("taiga", 0xFF2F6E5E),
			Map.entry("snowy_taiga", 0xFF4F7A70),
			Map.entry("old_growth_pine_taiga", 0xFF5A6E52),
			Map.entry("old_growth_spruce_taiga", 0xFF6E7E60),
			Map.entry("savanna", 0xFFBDB25F),
			Map.entry("savanna_plateau", 0xFFA79D64),
			Map.entry("windswept_savanna", 0xFFD2C67A),
			Map.entry("windswept_hills", 0xFF7A8070),
			Map.entry("windswept_gravelly_hills", 0xFF8E8E8A),
			Map.entry("windswept_forest", 0xFF56705A),
			Map.entry("jungle", 0xFF4F8A12),
			Map.entry("sparse_jungle", 0xFF6E9A2A),
			Map.entry("bamboo_jungle", 0xFF7FA21E),
			Map.entry("badlands", 0xFFD2643A),
			Map.entry("eroded_badlands", 0xFFE8784A),
			Map.entry("wooded_badlands", 0xFFB08A5A),
			Map.entry("meadow", 0xFF9CC870),
			Map.entry("cherry_grove", 0xFFF0B4D2),
			Map.entry("grove", 0xFFB8D4CC),
			Map.entry("snowy_slopes", 0xFFD8DEE4),
			Map.entry("frozen_peaks", 0xFFC0D0EC),
			Map.entry("jagged_peaks", 0xFFE4E4DA),
			Map.entry("stony_peaks", 0xFF8E9A88),
			Map.entry("mushroom_fields", 0xFFC86EC8),
			Map.entry("dripstone_caves", 0xFF8A6E52),
			Map.entry("lush_caves", 0xFF5AA03A),
			Map.entry("deep_dark", 0xFF123438),
			Map.entry("nether_wastes", 0xFF8E2E2A),
			Map.entry("soul_sand_valley", 0xFF5E4638),
			Map.entry("crimson_forest", 0xFFB01E1E),
			Map.entry("warped_forest", 0xFF2E8A7A),
			Map.entry("basalt_deltas", 0xFF4A4448),
			Map.entry("the_end", 0xFF8A80C8),
			Map.entry("end_highlands", 0xFFCBC6E8),
			Map.entry("end_midlands", 0xFFA9A2D6),
			Map.entry("end_barrens", 0xFF7A72AE),
			Map.entry("small_end_islands", 0xFF4E4880),
			Map.entry("the_void", 0xFF101010));

	private SeedPalette() {}

	public static String group(String structure) {
		if (structure.startsWith("village_")) {
			return "village";
		}
		if (structure.equals("pillager_outpost")) {
			return "outpost";
		}
		if (structure.equals("bastion_remnant")) {
			return "bastion";
		}
		for (String prefix : List.of("abandoned_camp", "shipwreck", "ocean_ruin", "ruined_portal", "mineshaft")) {
			if (structure.startsWith(prefix)) {
				return prefix;
			}
		}
		return structure;
	}

	public static Group info(String group) {
		for (Group g : GROUPS) {
			if (g.id().equals(group)) {
				return g;
			}
		}
		return new Group(group, group, hashColor(group), true, "compass");
	}

	public static int biomeColor(String path) {
		Integer color = BIOMES.get(path);
		return color != null ? color : hashColor(path);
	}

	private static int hashColor(String text) {
		int h = text.hashCode();
		return 0xFF000000 | (0x50 + (h & 0x7F)) << 16 | (0x50 + (h >> 8 & 0x7F)) << 8 | 0x50 + (h >> 16 & 0x7F);
	}
}
