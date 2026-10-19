package net.brilliantw.richpresence;

import java.util.List;

public final class MinimapRaster {
	public static final int BACKGROUND = 0xFF15191D;
	public static final int FRAME_OUTER = 0xFF0B0E11;
	public static final int FRAME_INNER = 0xFF3C4955;
	public static final int OUTLINE = 0xFF101317;

	public interface Sampler {
		int color(int x, int z);
	}

	public interface Overlay {
		int color(double x, double z);
	}

	public static final Overlay NONE = (x, z) -> 0;

	public enum Shape { DISC, DIAMOND, SQUARE }

	public record Dot(double x, double z, double radius, int color, Shape shape, boolean pinToEdge) {}

	public record Frame(int res, double px, double pz, double bpp, double yawDegrees, boolean rotate, boolean circle, double unit, boolean border) {
		public double r() {
			return res / 2.0;
		}

		public double inner() {
			return border ? r() - Math.max(2.0, res / 48.0) : r();
		}

		double fx() {
			return -Math.sin(Math.toRadians(yawDegrees));
		}

		double fz() {
			return Math.cos(Math.toRadians(yawDegrees));
		}

		public double[] toTexel(double wx, double wz) {
			double dx = wx - px;
			double dz = wz - pz;
			if (!rotate) {
				return new double[] { r() + dx / bpp, r() + dz / bpp };
			}
			double fx = fx();
			double fz = fz();
			double up = dx * fx + dz * fz;
			double right = dx * -fz + dz * fx;
			return new double[] { r() + right / bpp, r() - up / bpp };
		}

		public double[] screenDirection(double dx, double dz) {
			if (!rotate) {
				return new double[] { dx, dz };
			}
			double fx = fx();
			double fz = fz();
			return new double[] { dx * -fz + dz * fx, -(dx * fx + dz * fz) };
		}

		public double arrowAngle() {
			return rotate ? 0 : Math.atan2(-Math.sin(Math.toRadians(yawDegrees)), -Math.cos(Math.toRadians(yawDegrees)));
		}

		public boolean inside(double ox, double oy, double radius) {
			return circle ? ox * ox + oy * oy <= radius * radius : Math.abs(ox) <= radius && Math.abs(oy) <= radius;
		}
	}

	private MinimapRaster() {}

	public static void render(int[] out, Frame f, Sampler sampler, Overlay overlay, List<Dot> dots) {
		int res = f.res();
		double r = f.r();
		double inner = f.inner();
		double fx = f.fx();
		double fz = f.fz();
		for (int ty = 0; ty < res; ty++) {
			for (int tx = 0; tx < res; tx++) {
				double ox = tx + 0.5 - r;
				double oy = ty + 0.5 - r;
				double d = f.circle() ? Math.sqrt(ox * ox + oy * oy) : Math.max(Math.abs(ox), Math.abs(oy));
				if (d > r) {
					out[ty * res + tx] = 0;
					continue;
				}
				int color;
				if (d > inner) {
					color = d > inner + (r - inner) * 0.45 ? FRAME_OUTER : FRAME_INNER;
				} else {
					double wx;
					double wz;
					if (f.rotate()) {
						double up = -oy * f.bpp();
						double right = ox * f.bpp();
						wx = f.px() + up * fx - right * fz;
						wz = f.pz() + up * fz + right * fx;
					} else {
						wx = f.px() + ox * f.bpp();
						wz = f.pz() + oy * f.bpp();
					}
					int sample = sampler.color((int) Math.floor(wx), (int) Math.floor(wz));
					color = sample == MapMath.UNKNOWN ? BACKGROUND : sample;
					int over = overlay.color(wx, wz);
					if (over != 0) {
						color = MapMath.mix(color, over | 0xFF000000, (over >>> 24) / 255.0) | 0xFF000000;
					}
				}
				if (f.circle() && d > r - 1) {
					color = MapMath.alpha(color, r - d);
				}
				out[ty * res + tx] = color;
			}
		}
		for (Dot dot : dots) {
			double[] t = f.toTexel(dot.x(), dot.z());
			double ox = t[0] - r;
			double oy = t[1] - r;
			double limit = inner - dot.radius() - f.unit();
			if (!f.inside(ox, oy, limit)) {
				if (!dot.pinToEdge()) {
					continue;
				}
				double scale = f.circle() ? limit / Math.sqrt(ox * ox + oy * oy) : limit / Math.max(Math.abs(ox), Math.abs(oy));
				ox *= scale;
				oy *= scale;
			}
			if (dot.shape() == Shape.SQUARE) {
				square(out, f, r + ox, r + oy, dot.radius(), dot.color());
			} else if (dot.shape() == Shape.DIAMOND) {
				diamond(out, f, r + ox, r + oy, dot.radius(), dot.color());
			} else {
				disc(out, f, r + ox, r + oy, dot.radius(), dot.color());
			}
		}
		double length = 5.5 * f.unit();
		fillArrow(out, f, arrowShape(r, r, length + 1.6 * f.unit(), f.arrowAngle()), OUTLINE);
		fillArrow(out, f, arrowShape(r, r, length, f.arrowAngle()), 0xFFFFFFFF);
	}

	private static boolean visible(Frame f, double x, double y) {
		return f.inside(x - f.r(), y - f.r(), f.inner());
	}

	private static void put(int[] out, Frame f, int x, int y, int color) {
		if (x >= 0 && y >= 0 && x < f.res() && y < f.res() && visible(f, x + 0.5, y + 0.5)) {
			out[y * f.res() + x] = color;
		}
	}

	private static void disc(int[] out, Frame f, double cx, double cy, double radius, int fill) {
		double edge = Math.max(1, radius * 0.35);
		for (int y = (int) Math.floor(cy - radius - 1); y <= Math.ceil(cy + radius + 1); y++) {
			for (int x = (int) Math.floor(cx - radius - 1); x <= Math.ceil(cx + radius + 1); x++) {
				double d = Math.hypot(x + 0.5 - cx, y + 0.5 - cy);
				if (d <= radius) {
					put(out, f, x, y, d > radius - edge ? OUTLINE : fill);
				}
			}
		}
	}

	private static void square(int[] out, Frame f, double cx, double cy, double radius, int fill) {
		double edge = Math.max(1, radius * 0.3);
		for (int y = (int) Math.floor(cy - radius); y <= Math.ceil(cy + radius); y++) {
			for (int x = (int) Math.floor(cx - radius); x <= Math.ceil(cx + radius); x++) {
				double d = Math.max(Math.abs(x + 0.5 - cx), Math.abs(y + 0.5 - cy));
				if (d <= radius) {
					put(out, f, x, y, d > radius - edge ? OUTLINE : fill);
				}
			}
		}
	}

	private static void diamond(int[] out, Frame f, double cx, double cy, double radius, int fill) {
		double edge = Math.max(1, radius * 0.3);
		for (int y = (int) Math.floor(cy - radius - 1); y <= Math.ceil(cy + radius + 1); y++) {
			for (int x = (int) Math.floor(cx - radius - 1); x <= Math.ceil(cx + radius + 1); x++) {
				double d = Math.abs(x + 0.5 - cx) + Math.abs(y + 0.5 - cy);
				if (d <= radius) {
					put(out, f, x, y, d > radius - edge ? OUTLINE : fill);
				}
			}
		}
	}

	public static double[][] arrowShape(double cx, double cy, double length, double angle) {
		double[][] local = { { 0, -1 }, { -0.72, 0.85 }, { 0, 0.4 }, { 0.72, 0.85 } };
		double[][] points = new double[4][];
		for (int i = 0; i < 4; i++) {
			double[] p = MapMath.rotate(local[i][0] * length, local[i][1] * length, angle);
			points[i] = new double[] { cx + p[0], cy + p[1] };
		}
		return points;
	}

	public static boolean inArrow(double[][] p, double x, double y) {
		return inTriangle(p[0], p[1], p[2], x, y) || inTriangle(p[0], p[2], p[3], x, y);
	}

	private static boolean inTriangle(double[] a, double[] b, double[] c, double x, double y) {
		double d1 = (x - b[0]) * (a[1] - b[1]) - (a[0] - b[0]) * (y - b[1]);
		double d2 = (x - c[0]) * (b[1] - c[1]) - (b[0] - c[0]) * (y - c[1]);
		double d3 = (x - a[0]) * (c[1] - a[1]) - (c[0] - a[0]) * (y - a[1]);
		return !((d1 < 0 || d2 < 0 || d3 < 0) && (d1 > 0 || d2 > 0 || d3 > 0));
	}

	private static void fillArrow(int[] out, Frame f, double[][] p, int color) {
		int minX = (int) Math.floor(Math.min(Math.min(p[0][0], p[1][0]), Math.min(p[2][0], p[3][0])));
		int maxX = (int) Math.ceil(Math.max(Math.max(p[0][0], p[1][0]), Math.max(p[2][0], p[3][0])));
		int minY = (int) Math.floor(Math.min(Math.min(p[0][1], p[1][1]), Math.min(p[2][1], p[3][1])));
		int maxY = (int) Math.ceil(Math.max(Math.max(p[0][1], p[1][1]), Math.max(p[2][1], p[3][1])));
		for (int y = minY; y <= maxY; y++) {
			for (int x = minX; x <= maxX; x++) {
				if (inArrow(p, x + 0.5, y + 0.5)) {
					put(out, f, x, y, color);
				}
			}
		}
	}
}
