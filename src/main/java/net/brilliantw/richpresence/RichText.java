package net.brilliantw.richpresence;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;

public final class RichText {
	public record Segment(int x, int dy, int width, String text, Style style, AnnouncementStore.Run run) {}

	public record Cell(int x, int width, AnnouncementStore.Line line, int height) {}

	public record Row(int y, int height, int indent, String kind, List<Integer> bars, Integer poll, List<Segment> segments,
			AnnouncementStore.Line image, int imageWidth, int requestWidth, List<Cell> cells) {}

	public record Layout(List<Row> rows, int height) {}

	public record Hit(String href, boolean spoiler) {}

	public static final int LINE = 11;
	private static final int BAR_STEP = 8;
	private static final int QUOTE = 0xFF4E5058;
	private static final int EMOJI = 9;
	private static final int EMOJI_ADVANCE = 11;
	private static final int MAX_IMAGE_HEIGHT = 200;
	private static final int SMALL_IMAGE = 96;
	private static final int CODE_BG = 0xFF0F1316;
	private static final int CELL_GAP = 2;
	private static final int MAX_COLUMNS = 32;
	private static final int CELL_PAD = 3;
	private static final int ICON = 16;
	private static final int CELL_BG = 0xFF222A31;
	private static final int HEAD_BG = 0xFF253543;

	private RichText() {}

	private static int colorOf(String hex, int fallback) {
		if (hex != null && hex.matches("#[0-9a-fA-F]{6}")) {
			return 0xFF000000 | Integer.parseInt(hex.substring(1), 16);
		}
		return fallback;
	}

	private static Style styleOf(AnnouncementStore.Run run, String kind, boolean reveal) {
		int base = switch (kind) {
			case "h1" -> Theme.ACCENT;
			case "small" -> Theme.TEXT_SECONDARY;
			case "code" -> 0xFFC8D0D8;
			default -> Theme.TEXT;
		};
		int color = run.dim == 1 ? Theme.TEXT_DIM : run.code == 1 ? 0xFFE6C07B : colorOf(run.c, base);
		return Style.EMPTY
				.withColor(TextColor.fromRgb(color & 0xFFFFFF))
				.withBold(run.b == 1 || kind.startsWith("h"))
				.withItalic(run.i == 1)
				.withUnderlined(run.u == 1 || run.h != null)
				.withStrikethrough(run.s == 1)
				.withObfuscated(run.sp == 1 && !reveal);
	}

	private static RemoteImages.Entry emoji(String src) {
		return RemoteImages.get(src, RemoteImages.requestWidth(EMOJI));
	}

	private static boolean emojiFailed(String src) {
		RemoteImages.Entry entry = RemoteImages.peek(src, RemoteImages.requestWidth(EMOJI));
		return entry != null && entry.state == RemoteImages.State.FAILED;
	}

	private static int[] imageSize(AnnouncementStore.Line line, int maxWidth) {
		int limit = Math.max(16, line.sm == 1 ? Math.min(SMALL_IMAGE, maxWidth) : maxWidth);
		int scale = Math.max(1, Minecraft.getInstance().getWindow().getGuiScale());
		int sourceWidth = line.w;
		int sourceHeight = line.h;
		if (sourceWidth <= 0 || sourceHeight <= 0) {
			RemoteImages.Entry probe = RemoteImages.peek(line.src, RemoteImages.requestWidth(limit));
			if (probe == null || probe.state != RemoteImages.State.READY) {
				int w = Math.min(limit, 160);
				return new int[] { w, w * 9 / 16, RemoteImages.requestWidth(limit) };
			}
			sourceWidth = probe.width;
			sourceHeight = probe.height;
		}
		int w = Math.min(limit, Math.max(24, sourceWidth / scale));
		int h = Math.max(8, w * sourceHeight / sourceWidth);
		if (h > MAX_IMAGE_HEIGHT) {
			h = MAX_IMAGE_HEIGHT;
			w = Math.max(16, h * sourceWidth / sourceHeight);
		}
		int request = line.w > 0 && line.h > 0 ? RemoteImages.requestWidth(w) : RemoteImages.requestWidth(limit);
		return new int[] { w, h, request };
	}

	public static Layout layout(Font font, List<AnnouncementStore.Line> lines, int width, boolean reveal, int startY) {
		List<Row> rows = new ArrayList<>();
		int rowY = startY;
		int skipTo = 0;
		for (int index = 0; index < lines.size(); index++) {
			if (index < skipTo) {
				continue;
			}
			AnnouncementStore.Line line = lines.get(index);
			if ("gap".equals(line.k)) {
				rowY += 5;
				continue;
			}
			if (line.k.startsWith("h") && !rows.isEmpty()) {
				rowY += 3;
			}
			List<Integer> bars = new ArrayList<>();
			if (line.q == 1) {
				bars.add(QUOTE);
			}
			if (line.bar != null) {
				bars.add(colorOf(line.bar, QUOTE));
			}
			int indent = bars.size() * BAR_STEP;
			if ("img".equals(line.k)) {
				int[] size = imageSize(line, width - indent);
				rows.add(new Row(rowY, size[1], indent, line.k, bars, null, List.of(), line, size[0], size[2], null));
				rowY += size[1] + 3;
				continue;
			}
			if ("tr".equals(line.k)) {
				int end = index;
				while (end < lines.size() && "tr".equals(lines.get(end).k) && lines.get(end).tb == line.tb) {
					end++;
				}
				rowY = layoutTable(font, lines.subList(index, end), width - indent, indent, bars, reveal, rowY, rows);
				skipTo = end;
				continue;
			}
			String marker = "li".equals(line.k) && line.n != null ? line.n + " " : "";
			int markerX = indent + Math.max(0, line.d) * 10;
			int hang = markerX + (marker.isEmpty() ? 0 : font.width(marker));
			int available = Math.max(20, width - hang - ("poll".equals(line.k) ? 8 : 0));
			int textX = hang + ("poll".equals(line.k) ? 4 : 0);

			List<List<Segment>> wrapped = wrap(font, line.r, line.k, reveal, available, textX);
			for (int i = 0; i < wrapped.size(); i++) {
				List<Segment> segments = new ArrayList<>(wrapped.get(i));
				if (i == 0 && !marker.isEmpty()) {
					segments.addFirst(new Segment(markerX, 0, font.width(marker), marker, Style.EMPTY.withColor(TextColor.fromRgb(Theme.TEXT_DIM & 0xFFFFFF)), new AnnouncementStore.Run()));
				}
				int height = "poll".equals(line.k) ? LINE + 4 : LINE;
				rows.add(new Row(rowY, height, indent, line.k, bars, "poll".equals(line.k) ? line.p : null, segments, null, 0, 0, null));
				rowY += height + ("poll".equals(line.k) ? 2 : 0);
			}
		}
		return new Layout(rows, rowY);
	}

	private static int naturalWidth(Font font, AnnouncementStore.Line cell, boolean reveal) {
		if (cell == null) {
			return 8;
		}
		int width = 0;
		for (AnnouncementStore.Run run : cell.r) {
			width += run.e != null && !emojiFailed(run.e) ? EMOJI_ADVANCE : font.width(Component.literal(run.t == null ? "" : run.t).withStyle(styleOf(run, "p", reveal)));
		}
		if (cell.src != null) {
			width = Math.max(width, ICON);
		}
		return width + CELL_PAD * 2;
	}

	private static List<AnnouncementStore.Line> cellsOf(AnnouncementStore.Line tr) {
		return tr.cells == null ? List.of() : tr.cells;
	}

	private static int colSpan(AnnouncementStore.Line cell) {
		return cell == null ? 1 : Math.max(1, cell.cs);
	}

	private static int rowSpan(AnnouncementStore.Line cell) {
		return cell == null ? 1 : Math.max(1, cell.rs);
	}

	private record Placement(int[][] starts, int columns) {}

	private static Placement place(List<AnnouncementStore.Line> table) {
		int[][] starts = new int[table.size()][];
		int[] busy = new int[MAX_COLUMNS];
		int columns = 1;
		for (int r = 0; r < table.size(); r++) {
			List<AnnouncementStore.Line> cells = cellsOf(table.get(r));
			starts[r] = new int[cells.size()];
			int column = 0;
			for (int c = 0; c < cells.size(); c++) {
				while (column < MAX_COLUMNS - 1 && busy[column] > 0) {
					column++;
				}
				starts[r][c] = column;
				int end = Math.min(MAX_COLUMNS, column + colSpan(cells.get(c)));
				for (int i = column; i < end; i++) {
					busy[i] = rowSpan(cells.get(c));
				}
				column = end;
			}
			for (int i = 0; i < MAX_COLUMNS; i++) {
				if (busy[i] > 0) {
					columns = Math.max(columns, i + 1);
					busy[i]--;
				}
			}
		}
		return new Placement(starts, columns);
	}

	private static int layoutTable(Font font, List<AnnouncementStore.Line> table, int width, int indent, List<Integer> bars, boolean reveal, int rowY, List<Row> rows) {
		Placement placement = place(table);
		int[][] starts = placement.starts();
		int columns = placement.columns();
		int[] natural = new int[columns];
		for (int r = 0; r < table.size(); r++) {
			List<AnnouncementStore.Line> cells = cellsOf(table.get(r));
			for (int c = 0; c < cells.size(); c++) {
				if (colSpan(cells.get(c)) == 1) {
					natural[starts[r][c]] = Math.max(natural[starts[r][c]], naturalWidth(font, cells.get(c), reveal));
				}
			}
		}
		int available = Math.max(columns * 12, width - (columns - 1) * CELL_GAP);
		int[] widths = fit(natural, available);
		int[] offsets = new int[columns + 1];
		for (int i = 0; i < columns; i++) {
			offsets[i + 1] = offsets[i] + widths[i] + CELL_GAP;
		}
		int first = rows.size();
		List<int[]> tall = new ArrayList<>();
		for (int r = 0; r < table.size(); r++) {
			List<AnnouncementStore.Line> lineCells = cellsOf(table.get(r));
			List<Segment> segments = new ArrayList<>();
			List<Cell> cells = new ArrayList<>();
			int height = LINE + 3;
			for (int c = 0; c < lineCells.size(); c++) {
				AnnouncementStore.Line cell = lineCells.get(c);
				int start = starts[r][c];
				int end = Math.min(columns, start + colSpan(cell));
				int cx = indent + offsets[start];
				int cw = Math.max(widths[start], offsets[end] - offsets[start] - CELL_GAP);
				cells.add(new Cell(cx, cw, cell, 0));
				if (rowSpan(cell) > 1 && r + 1 < table.size()) {
					tall.add(new int[] { first + r, cells.size() - 1, Math.min(rowSpan(cell), table.size() - r) });
				}
				if (cell != null) {
					int top = cell.src != null ? ICON + 3 : 2;
					List<List<Segment>> wrapped = cell.r.isEmpty() ? List.of() : wrap(font, cell.r, "p", reveal, Math.max(8, cw - CELL_PAD * 2), cx + CELL_PAD);
					for (int i = 0; i < wrapped.size(); i++) {
						for (Segment segment : wrapped.get(i)) {
							segments.add(new Segment(segment.x(), top + i * LINE, segment.width(), segment.text(), segment.style(), segment.run()));
						}
					}
					if (rowSpan(cell) == 1) {
						height = Math.max(height, top + wrapped.size() * LINE + 1);
					}
				}
			}
			rows.add(new Row(rowY, height, indent, "tr", bars, null, segments, null, 0, 0, cells));
			rowY += height + CELL_GAP;
		}
		for (int[] span : tall) {
			Row top = rows.get(span[0]);
			Row bottom = rows.get(span[0] + span[2] - 1);
			Cell cell = top.cells().get(span[1]);
			top.cells().set(span[1], new Cell(cell.x(), cell.width(), cell.line(), bottom.y() + bottom.height() - top.y()));
		}
		return rowY + 2;
	}

	private static int[] fit(int[] natural, int available) {
		int total = 0;
		for (int w : natural) {
			total += w;
		}
		if (total <= available) {
			return natural.clone();
		}
		int low = 0;
		int high = available;
		while (low < high) {
			int cap = (low + high + 1) / 2;
			int sum = 0;
			for (int w : natural) {
				sum += Math.min(w, cap);
			}
			if (sum <= available) {
				low = cap;
			} else {
				high = cap - 1;
			}
		}
		int[] widths = new int[natural.length];
		for (int i = 0; i < widths.length; i++) {
			widths[i] = Math.max(12, Math.min(natural[i], low));
		}
		return widths;
	}

	private static List<List<Segment>> wrap(Font font, List<AnnouncementStore.Run> runs, String kind, boolean reveal, int available, int textX) {
		List<List<Segment>> wrapped = new ArrayList<>();
		List<Segment> current = new ArrayList<>();
		int x = 0;
		for (AnnouncementStore.Run run : runs) {
			Style style = styleOf(run, kind, reveal);
			if (run.e != null && !emojiFailed(run.e)) {
				if (x > 0 && x + EMOJI_ADVANCE > available) {
					wrapped.add(current);
					current = new ArrayList<>();
					x = 0;
				}
				current.add(new Segment(textX + x, 0, EMOJI_ADVANCE, run.t, style, run));
				x += EMOJI_ADVANCE;
				continue;
			}
			String rest = run.t == null ? "" : run.t;
			while (!rest.isEmpty()) {
				int fit = font.getSplitter().plainIndexAtWidth(rest, available - x, style);
				if (fit >= rest.length()) {
					int w = font.width(Component.literal(rest).withStyle(style));
					current.add(new Segment(textX + x, 0, w, rest, style, run));
					x += w;
					break;
				}
				if (fit <= 0) {
					if (x == 0) {
						fit = 1;
					} else {
						wrapped.add(current);
						current = new ArrayList<>();
						x = 0;
						continue;
					}
				}
				int space = rest.lastIndexOf(' ', fit);
				int take = space > 0 && rest.substring(0, fit).chars().allMatch(ch -> ch < 0x2E80) ? space + 1 : fit;
				String piece = rest.substring(0, take);
				int w = font.width(Component.literal(piece).withStyle(style));
				current.add(new Segment(textX + x, 0, w, piece, style, run));
				rest = rest.substring(take);
				wrapped.add(current);
				current = new ArrayList<>();
				x = 0;
			}
		}
		if (!current.isEmpty() || wrapped.isEmpty()) {
			wrapped.add(current);
		}
		return wrapped;
	}

	public static void draw(Gfx gfx, Font font, Layout layout, int x, int y, int width, boolean reveal, int mouseX, int mouseY, boolean mouseInside, List<Component> tooltip) {
		int screenHeight = Minecraft.getInstance().getWindow().getGuiScaledHeight();
		for (Row row : layout.rows()) {
			int ry = y + row.y();
			int extent = row.height();
			if (row.cells() != null) {
				for (Cell cell : row.cells()) {
					extent = Math.max(extent, cell.height());
				}
			}
			if (ry > screenHeight || ry + extent < 0) {
				continue;
			}
			for (int i = 0; i < row.bars().size(); i++) {
				gfx.fill(x + i * BAR_STEP, ry - 1, x + i * BAR_STEP + 2, ry + row.height(), row.bars().get(i));
			}
			if ("code".equals(row.kind())) {
				gfx.fill(x + row.indent(), ry - 1, x + width, ry + row.height(), CODE_BG);
			}
			if (row.poll() != null) {
				int px = x + row.indent();
				int pw = width - row.indent();
				gfx.fill(px, ry, px + pw, ry + row.height() - 1, Theme.CHIP);
				gfx.fill(px, ry, px + pw * Math.clamp(row.poll(), 0, 100) / 100, ry + row.height() - 1, Theme.ACCENT_DIM);
			}
			if (row.image() != null) {
				drawImage(gfx, font, row, x + row.indent(), ry, reveal, mouseX, mouseY, mouseInside, tooltip);
				continue;
			}
			if (row.cells() != null) {
				drawCells(gfx, row, x, ry, mouseX, mouseY, mouseInside, tooltip);
			}
			for (Segment segment : row.segments()) {
				int sx = x + segment.x();
				int top = row.cells() != null ? ry + segment.dy() : ry - 1;
				int bottom = row.cells() != null ? top + LINE : ry + row.height();
				int textY = row.cells() != null ? top : ry + (row.poll() != null ? 3 : 1);
				if (segment.run().code == 1 && !"code".equals(row.kind())) {
					gfx.fill(sx - 1, top, sx + segment.width() + 1, bottom - 1, CODE_BG);
				}
				boolean hovered = mouseInside && mouseX >= sx && mouseX < sx + segment.width() && mouseY >= top && mouseY < bottom;
				if (segment.run().e != null && emoji(segment.run().e).state != RemoteImages.State.FAILED) {
					RemoteImages.Entry icon = emoji(segment.run().e);
					if (icon.state == RemoteImages.State.READY) {
						RemoteImages.draw(gfx, icon, sx + 1, textY - 1, EMOJI, EMOJI);
					}
					if (hovered) {
						tooltip.add(Component.literal(segment.text()));
					}
					continue;
				}
				gfx.text(font, Component.literal(segment.text()).withStyle(segment.style()), sx, textY, Theme.TEXT);
				if (hovered && segment.run().h != null) {
					tooltip.add(Component.literal(segment.run().h).withStyle(ChatFormatting.AQUA));
					tooltip.add(Component.literal("點擊開啟連結").withStyle(ChatFormatting.GRAY));
				} else if (hovered && segment.run().sp == 1 && !reveal) {
					tooltip.add(Component.literal("暴雷內容，點擊顯示"));
				}
			}
		}
	}

	private static void drawCells(Gfx gfx, Row row, int x, int ry, int mouseX, int mouseY, boolean mouseInside, List<Component> tooltip) {
		for (Cell cell : row.cells()) {
			int cx = x + cell.x();
			int h = cell.height() > 0 ? cell.height() : row.height();
			AnnouncementStore.Line line = cell.line();
			gfx.fill(cx, ry, cx + cell.width(), ry + h, line != null && line.th == 1 ? HEAD_BG : CELL_BG);
			if (line == null || line.src == null) {
				continue;
			}
			int ix = cx + (cell.width() - ICON) / 2;
			int iy = line.r.isEmpty() ? ry + (h - ICON) / 2 : ry + 2;
			RemoteImages.Entry entry = RemoteImages.get(line.src, RemoteImages.requestWidth(ICON));
			if (entry.state == RemoteImages.State.READY) {
				RemoteImages.draw(gfx, entry, ix, iy, ICON, ICON);
			}
			if (mouseInside && line.l != null && !line.l.isEmpty() && mouseX >= cx && mouseX < cx + cell.width() && mouseY >= ry && mouseY < ry + h) {
				tooltip.add(Component.literal(line.l));
			}
		}
	}

	private static void drawImage(Gfx gfx, Font font, Row row, int x, int y, boolean reveal, int mouseX, int mouseY, boolean mouseInside, List<Component> tooltip) {
		AnnouncementStore.Line line = row.image();
		int w = row.imageWidth();
		int h = row.height();
		RemoteImages.Entry entry = RemoteImages.get(line.src, row.requestWidth());
		boolean hidden = line.sp == 1 && !reveal;
		boolean hovered = mouseInside && mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
		if (entry.state == RemoteImages.State.READY) {
			RemoteImages.draw(gfx, entry, x, y, w, h);
		} else {
			gfx.fill(x, y, x + w, y + h, Theme.CHIP);
			String text = entry.state == RemoteImages.State.LOADING ? "載入中…" : "無法載入，點擊在瀏覽器開啟";
			gfx.text(font, Gfx.fit(font, text, w - 8), x + Math.max(4, (w - font.width(text)) / 2), y + (h - 8) / 2, Theme.TEXT_SECONDARY);
		}
		if (hidden) {
			gfx.fill(x, y, x + w, y + h, 0xF01A1E22);
			String text = "暴雷內容・點擊顯示";
			gfx.text(font, Gfx.fit(font, text, w - 8), x + Math.max(4, (w - font.width(text)) / 2), y + (h - 8) / 2, Theme.TEXT);
		} else if (line.v == 1 && entry.state == RemoteImages.State.READY) {
			int bw = 22;
			int bh = 16;
			int bx = x + (w - bw) / 2;
			int by = y + (h - bh) / 2;
			gfx.fill(bx, by, bx + bw, by + bh, hovered ? 0xE0000000 : 0xB0000000);
			Icons.PLAY.drawCentered(gfx, bx + 1, by, bw, bh, 0xFFFFFFFF);
		}
		if (!hidden && line.l != null && !line.l.isEmpty() && entry.state == RemoteImages.State.READY) {
			String label = Gfx.fit(font, line.l, w - 8);
			gfx.fill(x, y + h - 11, x + font.width(label) + 6, y + h, 0xB0000000);
			gfx.text(font, label, x + 3, y + h - 9, 0xFFFFFFFF);
		}
		if (hovered) {
			gfx.fill(x, y, x + w, y + 1, Theme.ACCENT);
			gfx.fill(x, y + h - 1, x + w, y + h, Theme.ACCENT);
			if (hidden) {
				tooltip.add(Component.literal("暴雷內容，點擊顯示"));
			} else if (line.href != null) {
				tooltip.add(Component.literal(line.v == 1 ? "點擊在瀏覽器播放影片" : "點擊在瀏覽器開啟原圖"));
			}
		}
	}

	public static Hit hit(Layout layout, int x, int y, double mouseX, double mouseY, boolean reveal) {
		for (Row row : layout.rows()) {
			int ry = y + row.y();
			if (mouseY < ry - 1 || mouseY >= ry + row.height()) {
				continue;
			}
			if (row.image() != null) {
				int ix = x + row.indent();
				if (mouseX >= ix && mouseX < ix + row.imageWidth()) {
					if (row.image().sp == 1 && !reveal) {
						return new Hit(null, true);
					}
					return row.image().href != null ? new Hit(row.image().href, false) : null;
				}
				return null;
			}
			for (Segment segment : row.segments()) {
				int sx = x + segment.x();
				int top = row.cells() != null ? ry + segment.dy() : ry - 1;
				int bottom = row.cells() != null ? top + LINE : ry + row.height();
				if (mouseX < sx || mouseX >= sx + segment.width() || mouseY < top || mouseY >= bottom) {
					continue;
				}
				if (segment.run().h != null) {
					return new Hit(segment.run().h, false);
				}
				return segment.run().sp == 1 ? new Hit(null, true) : null;
			}
		}
		return null;
	}

	public static void openLink(Screen screen, String url) {
		try {
			URI uri = URI.create(url);
			if ("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme())) {
				ConfirmLinkScreen.confirmLinkNow(screen, uri);
			}
		} catch (IllegalArgumentException ignored) {
		}
	}
}
