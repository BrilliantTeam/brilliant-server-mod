package net.brilliantw.richpresence;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.junit.jupiter.api.Test;

class MapStoreTest {
	private static MapStore.Region put(String key, long usedAt) {
		MapStore.Region region = new MapStore.Region(key, null);
		region.usedAt = usedAt;
		MapStore.REGIONS.put(key, region);
		return region;
	}

	@Test
	void keepsRegionsDrawnInTheSameFrame() {
		MapStore.Region first = put("first", System.currentTimeMillis());
		for (int i = 0; i < 200; i++) {
			put("r" + i, System.currentTimeMillis());
		}
		assertSame(first, MapStore.REGIONS.get("first"));
		MapStore.REGIONS.values().forEach(region -> region.usedAt = 0);
		put("idle", 0);
		assertFalse(MapStore.REGIONS.containsKey("r0"));
	}
}
