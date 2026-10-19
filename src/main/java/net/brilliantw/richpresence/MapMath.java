package net.brilliantw.richpresence;

public final class MapMath {
	public static final int UNKNOWN = 0;

	private MapMath() {}

	public static int tileOf(int block, int span) {
		return Math.floorDiv(block, span);
	}

	public static int inTile(int block, int span) {
		return Math.floorMod(block, span);
	}

	public static int scale(int argb, double factor) {
		int r = (int) Math.clamp(((argb >> 16) & 0xFF) * factor, 0, 255);
		int g = (int) Math.clamp(((argb >> 8) & 0xFF) * factor, 0, 255);
		int b = (int) Math.clamp((argb & 0xFF) * factor, 0, 255);
		return argb & 0xFF000000 | r << 16 | g << 8 | b;
	}

	public static int mix(int a, int b, double t) {
		double s = 1 - t;
		int r = (int) (((a >> 16) & 0xFF) * s + ((b >> 16) & 0xFF) * t);
		int g = (int) (((a >> 8) & 0xFF) * s + ((b >> 8) & 0xFF) * t);
		int bl = (int) ((a & 0xFF) * s + (b & 0xFF) * t);
		int al = (int) (((a >>> 24) & 0xFF) * s + ((b >>> 24) & 0xFF) * t);
		return al << 24 | r << 16 | g << 8 | bl;
	}

	public static double hillshade(int height, int north, int west) {
		int slope = (height - north) + (height - west);
		return Math.clamp(1 + slope * 0.045, 0.72, 1.22);
	}

	public static double waterShade(int depth) {
		return Math.clamp(1.0 - Math.max(0, depth - 1) * 0.04, 0.6, 1.0);
	}

	public static double[] rotate(double x, double y, double radians) {
		double c = Math.cos(radians);
		double s = Math.sin(radians);
		return new double[] { x * c - y * s, x * s + y * c };
	}

	public static int alpha(int argb, double alpha) {
		int a = (int) Math.clamp(((argb >>> 24) & 0xFF) * alpha, 0, 255);
		return a << 24 | argb & 0xFFFFFF;
	}

	public static int bluemapSpan(int lod) {
		int span = 500;
		for (int i = 1; i < lod; i++) {
			span *= 5;
		}
		return span;
	}

	public static int bluemapStep(int lod) {
		return bluemapSpan(lod) / 500;
	}

	public static int bluemapHeight(int metaArgb) {
		return (short) ((metaArgb >> 8 & 0xFF) << 8 | metaArgb & 0xFF);
	}
}
