package net.brilliantw.richpresence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.Random;
import org.junit.jupiter.api.Test;

class MapMarkersTest {
	@Test
	void rectangleMatchesPolygonTest() {
		MapMarkers.Area rect = new MapMarkers.Area(MapMarkers.AreaKind.RESIDENCE, "", "", new double[] { -20, 30, 30, -20 }, new double[] { -7, -7, 41, 41 }, 0, 0, -20, -7, 30, 41);
		MapMarkers.Area poly = new MapMarkers.Area(MapMarkers.AreaKind.RESIDENCE, "", "", new double[] { -20, 30, 30, -20, -20 }, new double[] { -7, -7, 41, 41, 20 }, 0, 0, -20, -7, 30, 41);
		Random random = new Random(1);
		for (int i = 0; i < 200_000; i++) {
			double x = random.nextInt(120) * 0.5 - 30;
			double z = random.nextInt(120) * 0.5 - 15;
			double edge = random.nextInt(4) * 0.25 + 0.5;
			assertEquals(poly.contains(x, z), rect.contains(x, z), x + "," + z);
			if (rect.contains(x, z)) {
				assertEquals(poly.border(x, z, edge), rect.border(x, z, edge), x + "," + z + " edge " + edge);
			}
		}
	}

	@Test
	void regenRowsSortAcrossNegativeChunks() {
		MapMarkers.Regen regen = MapMarkers.parseRegen("{\"chunks\":[3,-1,5, -4,-1,0, 0,2,7, -2147483648,0,1, 2147483647,0,2, -1,-1,9]}");
		int[][] expected = { { -4, -1 }, { -1, -1 }, { 3, -1 }, { -2147483648, 0 }, { 2147483647, 0 }, { 0, 2 } };
		for (int i = 0; i < expected.length; i++) {
			assertEquals(expected[i][0], MapMarkers.Regen.rowX(regen.rows()[i]));
			assertEquals(expected[i][1], MapMarkers.Regen.rowZ(regen.rows()[i]));
		}
		assertEquals(5, regen.minutesLeft(3, -1));
		assertEquals(9, regen.minutesLeft(-1, -1));
		assertNull(regen.minutesLeft(0, -1));
		assertEquals(1, regen.lowerBound(-3, -1));
		assertEquals(5, regen.lowerBound(0, 1));
	}
}
