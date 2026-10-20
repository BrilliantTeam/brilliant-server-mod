package net.brilliantw.richpresence;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.levelgen.WorldgenRandom;

public final class MapInfo {
	private static final String[] DIRECTIONS = { "南", "西南", "西", "西北", "北", "東北", "東", "東南" };

	private MapInfo() {}

	public static String chunk(int x, int z) {
		return "區塊 " + Math.floorDiv(x, 16) + ", " + Math.floorDiv(z, 16) + "（" + Math.floorMod(x, 16) + ", " + Math.floorMod(z, 16) + "）";
	}

	public static boolean slime(WorldMaps.View view, int x, int z) {
		if (view == null || !view.dimension().equals("minecraft:overworld")) {
			return false;
		}
		Long seed = SeedMaps.seed(view);
		return seed != null && slime(seed, Math.floorDiv(x, 16), Math.floorDiv(z, 16));
	}

	public static boolean slime(long seed, int chunkX, int chunkZ) {
		return WorldgenRandom.seedSlimeChunk(chunkX, chunkZ, seed, 987234911L).nextInt(10) == 0;
	}

	public static String biome(ClientLevel level, BlockPos pos) {
		return level.getBiome(pos).unwrapKey()
				.map(key -> Component.translatable("biome." + key.identifier().getNamespace() + "." + key.identifier().getPath()).getString())
				.orElse("");
	}

	public static long dayTime(ClientLevel level) {
		long time = -1;
		//? if >=26.3 {
		/*time = level.dimensionType().defaultClock().map(clock -> level.clockManager().getInstance(clock).totalTicks()).orElse(-1L);
		*///?}
		//? if >=26.1 && <26.3 {
		time = level.dimensionType().defaultClock().map(clock -> level.clockManager().getTotalTicks(clock)).orElse(-1L);
		//?}
		//? if <26.1 {
		/*time = level.dimensionType().hasFixedTime() ? -1 : level.getDayTime();
		*///?}
		return time;
	}

	public static String time(ClientLevel level) {
		long time = dayTime(level);
		if (time < 0) {
			return "沒有晝夜";
		}
		long day = time / 24000 + 1;
		int tod = (int) (time % 24000);
		int hour = (tod / 1000 + 6) % 24;
		int minute = tod % 1000 * 60 / 1000;
		String phase = tod < 12000 ? "白天" : tod < 13800 ? "黃昏" : tod < 22200 ? "夜晚" : "黎明";
		return String.format("第 %d 天 %02d:%02d・%s", day, hour, minute, phase);
	}

	public static String facing(float yaw) {
		float wrapped = ((yaw % 360) + 360) % 360;
		int index = Math.round(wrapped / 45f) % 8;
		int bearing = Math.round((wrapped + 180) % 360);
		return "面向 " + DIRECTIONS[index] + "（" + bearing + "°）";
	}

	public static float daylight(ClientLevel level) {
		if (!level.dimensionType().hasSkyLight()) {
			return 1;
		}
		return 1 - level.getSkyDarken() / 11f * 0.5f;
	}
}
