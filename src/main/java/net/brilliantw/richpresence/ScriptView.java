package net.brilliantw.richpresence;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.client.gui.Font;

public final class ScriptView {
	public static final int ROW = 18;
	public static final int REPORTER_ROW = 14;
	public static final int SLOT_H = 12;
	public static final int PAD = 5;
	public static final int GAP = 4;
	public static final int ARM = 12;
	public static final int C_BOTTOM = 10;
	public static final int ELSE_H = 16;
	public static final int HAT_TOP = 6;
	public static final int NOTCH_X = 10;
	public static final int NOTCH_W = 12;
	public static final int NOTCH_H = 3;
	public static final int MIN_BODY = 10;
	public static final int SLOT_TEXT = 0xFF575E75;
	public static final int CURRENT = 0xFFFFF27A;
	public static final int ERROR = 0xFFFF5A5A;
	public static final int WARNING = 0xFFFFCC33;

	public record SlotBox(Scripts.Block block, int index, ScriptBlocks.Slot slot, int x, int y, int w, int h, boolean filled) {
		public boolean contains(double mx, double my) {
			return mx >= x && mx < x + w && my >= y && my < y + h;
		}
	}

	public record Label(String text, int x) {}

	public static final class Node {
		public final Scripts.Block block;
		public final ScriptBlocks.Def def;
		public final List<Scripts.Block> ownerList;
		public final int index;
		public final Scripts.Arg ownerArg;
		public int x;
		public int y;
		public int w;
		public int h;
		public int rowY;
		public int rowH;
		public int bodyY;
		public int elseY;
		public int bottomY;
		public final List<Label> labels = new ArrayList<>();
		public final List<SlotBox> slots = new ArrayList<>();
		public final List<Node> children = new ArrayList<>();
		public final List<Node> body = new ArrayList<>();
		public final List<Node> elseBody = new ArrayList<>();

		Node(Scripts.Block block, List<Scripts.Block> ownerList, int index, Scripts.Arg ownerArg) {
			this.block = block;
			this.def = block.def();
			this.ownerList = ownerList;
			this.index = index;
			this.ownerArg = ownerArg;
		}

		public int headerBottom() {
			return def.hasBody() ? bodyY : y + h;
		}

		public boolean selfContains(double mx, double my) {
			if (!def.hasBody()) {
				return mx >= x && mx < x + w && my >= y && my < y + h;
			}
			boolean header = mx >= x && mx < x + w && my >= y && my < bodyY;
			boolean arm = mx >= x && mx < x + ARM && my >= y && my < y + h;
			boolean elseBar = def.shape() == ScriptBlocks.Shape.C_ELSE && mx >= x && mx < x + w && my >= elseY && my < elseY + ELSE_H;
			boolean bottom = mx >= x && mx < x + w && my >= bottomY && my < y + h;
			return header || arm || elseBar || bottom;
		}

		public boolean badgeContains(double mx, double my) {
			return mx >= x + w - 9 && mx < x + w + 1 && my >= y - 1 && my < y + 9;
		}
	}

	public record Laid(List<Node> nodes, int height, int width) {}

	private final Font font;
	public SlotBox editing;
	public Map<Scripts.Block, String> issues = Map.of();
	public boolean animate = true;

	public ScriptView(Font font) {
		this.font = font;
	}

	public static int darker(int color) {
		int r = (color >> 16 & 0xFF) * 4 / 5;
		int g = (color >> 8 & 0xFF) * 4 / 5;
		int b = (color & 0xFF) * 4 / 5;
		return 0xFF000000 | r << 16 | g << 8 | b;
	}

	public String slotText(Scripts.Block block, int index, ScriptBlocks.Slot slot) {
		String text = block.text(index);
		if (text.isEmpty()) {
			return switch (slot.kind()) {
				case VARIABLE -> "（無變數）";
				case LIST -> "（無清單）";
				case PROC -> "（無積木）";
				default -> text;
			};
		}
		return text;
	}

	private int[] slotSize(Scripts.Block block, int index, ScriptBlocks.Slot slot) {
		Scripts.Arg arg = index < block.args.size() ? block.args.get(index) : null;
		if (arg != null && arg.block != null) {
			return rowSize(arg.block);
		}
		return switch (slot.kind()) {
			case BOOLEAN -> new int[] { 24, SLOT_H };
			case DROPDOWN, VARIABLE, LIST, PROC -> new int[] { font.width(slotText(block, index, slot)) + 16, SLOT_H };
			default -> new int[] { Math.max(16, font.width(slotText(block, index, slot)) + 8), SLOT_H };
		};
	}

	private int[] rowSize(Scripts.Block block) {
		ScriptBlocks.Def def = block.def();
		int w = PAD;
		int h = def.isReporter() ? REPORTER_ROW : ROW;
		int slotIndex = 0;
		for (Object part : def.parts()) {
			if (part instanceof String text) {
				w += font.width(text) + GAP;
			} else {
				int[] size = slotSize(block, slotIndex, (ScriptBlocks.Slot) part);
				w += size[0] + GAP;
				h = Math.max(h, size[1] + 4);
				slotIndex++;
			}
		}
		w = w - GAP + PAD + (def.shape() == ScriptBlocks.Shape.BOOLEAN ? 6 : 0);
		return new int[] { Math.max(def.isReporter() ? 24 : 40, w), h };
	}

	private void layoutRow(Node node, int x, int y, int rowH) {
		int cx = x + PAD + (node.def.shape() == ScriptBlocks.Shape.BOOLEAN ? 3 : 0);
		int slotIndex = 0;
		for (Object part : node.def.parts()) {
			if (part instanceof String text) {
				node.labels.add(new Label(text, cx));
				cx += font.width(text) + GAP;
				continue;
			}
			ScriptBlocks.Slot slot = (ScriptBlocks.Slot) part;
			int[] size = slotSize(node.block, slotIndex, slot);
			int sy = y + (rowH - size[1]) / 2;
			Scripts.Arg arg = slotIndex < node.block.args.size() ? node.block.args.get(slotIndex) : null;
			if (arg != null && arg.block != null) {
				node.children.add(layoutBlock(arg.block, cx, sy, null, -1, arg));
			}
			node.slots.add(new SlotBox(node.block, slotIndex, slot, cx, sy, size[0], size[1], arg != null && arg.block != null));
			cx += size[0] + GAP;
			slotIndex++;
		}
	}

	public Node layoutBlock(Scripts.Block block, int x, int y, List<Scripts.Block> ownerList, int index, Scripts.Arg ownerArg) {
		Node node = new Node(block, ownerList, index, ownerArg);
		node.x = x;
		node.y = y;
		int[] row = rowSize(block);
		if (node.def.isReporter()) {
			node.rowY = y;
			node.rowH = row[1];
			node.w = row[0];
			node.h = row[1];
			layoutRow(node, x, y, row[1]);
			return node;
		}
		int top = node.def.shape() == ScriptBlocks.Shape.HAT ? HAT_TOP : 0;
		node.rowY = y + top;
		node.rowH = row[1];
		layoutRow(node, x, y + top, row[1]);
		int w = row[0];
		int h = top + row[1];
		if (node.def.hasBody()) {
			node.bodyY = y + h;
			Laid body = layoutList(block.body, x + ARM, node.bodyY);
			node.body.addAll(body.nodes());
			h += Math.max(MIN_BODY, body.height());
			if (node.def.shape() == ScriptBlocks.Shape.C_ELSE) {
				node.elseY = y + h;
				h += ELSE_H;
				Laid elseBody = layoutList(block.elseBody, x + ARM, y + h);
				node.elseBody.addAll(elseBody.nodes());
				h += Math.max(MIN_BODY, elseBody.height());
			}
			node.bottomY = y + h;
			h += C_BOTTOM;
			w = Math.max(w, 72);
		}
		node.w = Math.max(w, node.def.shape() == ScriptBlocks.Shape.HAT ? 84 : 40);
		node.h = h;
		return node;
	}

	public Laid layoutList(List<Scripts.Block> blocks, int x, int y) {
		List<Node> nodes = new ArrayList<>();
		int cy = y;
		int w = 0;
		for (int i = 0; i < blocks.size(); i++) {
			Node node = layoutBlock(blocks.get(i), x, cy, blocks, i, null);
			nodes.add(node);
			cy += node.h;
			w = Math.max(w, node.w + node.x - x);
		}
		return new Laid(nodes, cy - y, w);
	}

	public static void shape(Gfx gfx, ScriptBlocks.Shape shape, int x, int y, int w, int h, int color) {
		switch (shape) {
			case REPORTER -> {
				gfx.fill(x + 2, y, x + w - 2, y + h, color);
				gfx.fill(x + 1, y + 1, x + w - 1, y + h - 1, color);
				gfx.fill(x, y + 2, x + w, y + h - 2, color);
			}
			case BOOLEAN -> {
				int k = Math.min(h / 2, 5);
				for (int i = 0; i < k; i++) {
					gfx.fill(x + k - i, y + i, x + w - k + i, y + i + 1, color);
					gfx.fill(x + k - i, y + h - 1 - i, x + w - k + i, y + h - i, color);
				}
				gfx.fill(x, y + k, x + w, y + h - k, color);
			}
			default -> gfx.fill(x, y, x + w, y + h, color);
		}
	}

	private static void outline(Gfx gfx, int x, int y, int w, int h, int color) {
		gfx.fill(x - 1, y - 1, x + w + 1, y, color);
		gfx.fill(x - 1, y + h, x + w + 1, y + h + 1, color);
		gfx.fill(x - 1, y, x, y + h, color);
		gfx.fill(x + w, y, x + w + 1, y + h, color);
	}

	private static int alpha(int color, double fraction) {
		int a = (int) Math.round(Math.clamp(fraction, 0, 1) * 255);
		return a << 24 | color & 0xFFFFFF;
	}

	public void drawNode(Gfx gfx, Node node) {
		int color = node.def.category().color;
		int edge = darker(color);
		switch (node.def.shape()) {
			case REPORTER, BOOLEAN -> {
				shape(gfx, node.def.shape(), node.x, node.y, node.w, node.h, edge);
				shape(gfx, node.def.shape(), node.x, node.y, node.w, node.h - 1, color);
			}
			case HAT -> {
				int capW = Math.min(node.w - 4, 56);
				gfx.fill(node.x + 4, node.y, node.x + capW - 4, node.y + 2, color);
				gfx.fill(node.x + 1, node.y + 2, node.x + capW, node.y + HAT_TOP, color);
				gfx.fill(node.x, node.rowY, node.x + node.w, node.y + node.h, edge);
				gfx.fill(node.x + 1, node.rowY, node.x + node.w - 1, node.y + node.h - 1, color);
				gfx.fill(node.x + capW, node.rowY, node.x + node.w, node.rowY + 1, edge);
				gfx.fill(node.x + NOTCH_X, node.y + node.h, node.x + NOTCH_X + NOTCH_W, node.y + node.h + NOTCH_H, color);
			}
			case C, C_ELSE -> {
				gfx.fill(node.x, node.y, node.x + node.w, node.bodyY, color);
				gfx.fill(node.x, node.bodyY, node.x + ARM, node.y + node.h, color);
				gfx.fill(node.x + ARM + NOTCH_X, node.bodyY, node.x + ARM + NOTCH_X + NOTCH_W, node.bodyY + NOTCH_H, color);
				if (node.def.shape() == ScriptBlocks.Shape.C_ELSE) {
					gfx.fill(node.x, node.elseY, node.x + node.w, node.elseY + ELSE_H, color);
					gfx.label(font, node.def.elseLabel(), node.x + PAD, node.elseY, ELSE_H, 0xFFFFFFFF);
				}
				gfx.fill(node.x, node.bottomY, node.x + node.w, node.y + node.h, color);
				int right = node.x + node.w;
				int bottom = node.y + node.h;
				int arm = node.x + ARM;
				gfx.fill(node.x, node.y, right, node.y + 1, edge);
				gfx.fill(node.x, node.y, node.x + 1, bottom, edge);
				gfx.fill(node.x, bottom - 1, right, bottom, edge);
				gfx.fill(right - 1, node.y, right, node.bodyY, edge);
				gfx.fill(right - 1, node.bottomY, right, bottom, edge);
				gfx.fill(arm, node.bodyY - 1, right, node.bodyY, edge);
				gfx.fill(arm - 1, node.bodyY - 1, arm, node.bottomY + 1, edge);
				gfx.fill(arm, node.bottomY, right, node.bottomY + 1, edge);
				if (node.def.shape() == ScriptBlocks.Shape.C_ELSE) {
					gfx.fill(arm, node.elseY, right, node.elseY + 1, edge);
					gfx.fill(arm, node.elseY + ELSE_H - 1, right, node.elseY + ELSE_H, edge);
					gfx.fill(right - 1, node.elseY, right, node.elseY + ELSE_H, edge);
				}
				if (!node.def.endsStack()) {
					gfx.fill(node.x + NOTCH_X, node.y + node.h, node.x + NOTCH_X + NOTCH_W, node.y + node.h + NOTCH_H, color);
				}
			}
			default -> {
				gfx.fill(node.x, node.y, node.x + node.w, node.y + node.h, edge);
				gfx.fill(node.x + 1, node.y + 1, node.x + node.w - 1, node.y + node.h - 1, color);
				if (!node.def.endsStack()) {
					gfx.fill(node.x + NOTCH_X, node.y + node.h, node.x + NOTCH_X + NOTCH_W, node.y + node.h + NOTCH_H, color);
				}
			}
		}
		for (Label label : node.labels) {
			gfx.label(font, label.text(), label.x(), node.rowY, node.rowH, 0xFFFFFFFF);
		}
		for (SlotBox slot : node.slots) {
			if (slot.filled()) {
				continue;
			}
			String text = slotText(slot.block(), slot.index(), slot.slot());
			switch (slot.slot().kind()) {
				case BOOLEAN -> shape(gfx, ScriptBlocks.Shape.BOOLEAN, slot.x(), slot.y(), slot.w(), slot.h(), edge);
				case DROPDOWN, VARIABLE, LIST, PROC -> {
					gfx.fill(slot.x(), slot.y(), slot.x() + slot.w(), slot.y() + slot.h(), edge);
					gfx.label(font, text, slot.x() + 4, slot.y(), slot.h(), 0xFFFFFFFF);
					int tx = slot.x() + slot.w() - 8;
					int ty = slot.y() + slot.h() / 2 - 1;
					gfx.fill(tx, ty, tx + 5, ty + 1, 0xFFFFFFFF);
					gfx.fill(tx + 1, ty + 1, tx + 4, ty + 2, 0xFFFFFFFF);
					gfx.fill(tx + 2, ty + 2, tx + 3, ty + 3, 0xFFFFFFFF);
				}
				default -> {
					boolean editingThis = editing != null && editing.block() == slot.block() && editing.index() == slot.index();
					shape(gfx, slot.slot().kind() == ScriptBlocks.SlotKind.NUMBER ? ScriptBlocks.Shape.REPORTER : ScriptBlocks.Shape.STACK,
							slot.x(), slot.y(), slot.w(), slot.h(), 0xFFFFFFFF);
					if (!editingThis) {
						gfx.centered(font, font.plainSubstrByWidth(text, slot.w() - 4), slot.x(), slot.y(), slot.w(), slot.h(), SLOT_TEXT);
					}
				}
			}
		}
		for (Node child : node.children) {
			drawNode(gfx, child);
		}
		for (Node child : node.body) {
			drawNode(gfx, child);
		}
		for (Node child : node.elseBody) {
			drawNode(gfx, child);
		}
		if (animate) {
			drawAnimation(gfx, node);
		}
		if (issues.containsKey(node.block)) {
			int bx = node.x + node.w - 8;
			int by = node.y;
			gfx.fill(bx, by, bx + 8, by + 8, 0xFF1A1A1A);
			gfx.fill(bx + 1, by + 1, bx + 7, by + 7, WARNING);
			gfx.fill(bx + 3, by + 2, bx + 5, by + 5, 0xFF1A1A1A);
			gfx.fill(bx + 3, by + 6, bx + 5, by + 7, 0xFF1A1A1A);
		}
	}

	private void drawAnimation(Gfx gfx, Node node) {
		long now = System.currentTimeMillis();
		int top = node.y;
		int bottom = node.headerBottom();
		long flash = ScriptEngine.flashAge(node.block, now);
		if (flash >= 0 && flash < 450) {
			gfx.fill(node.x, top, node.x + node.w, bottom, alpha(0xFFFFFF, 0.45 * (1 - flash / 450.0)));
		}
		long error = ScriptEngine.errorAge(node.block, now);
		if (error >= 0 && error < 2500) {
			double pulse = 0.55 + 0.45 * Math.sin(now / 90.0);
			outline(gfx, node.x, top, node.w, bottom - top, alpha(ERROR, pulse * (1 - error / 2500.0)));
			gfx.fill(node.x, top, node.x + node.w, bottom, alpha(ERROR, 0.25 * (1 - error / 2500.0)));
		}
		if (ScriptEngine.isCurrent(node.block)) {
			double pulse = 0.65 + 0.35 * Math.sin(now / 160.0);
			outline(gfx, node.x, top, node.w, bottom - top, alpha(CURRENT, pulse));
		}
		if (ScriptEngine.isBreakpoint(node.block)) {
			int cy = node.rowY + node.rowH / 2;
			gfx.fill(node.x - 4, cy - 4, node.x + 4, cy + 4, 0xFFFFFFFF);
			gfx.fill(node.x - 3, cy - 3, node.x + 3, cy + 3, 0xFFE03131);
		}
		double progress = ScriptEngine.waitProgress(node.block, now);
		if (progress >= 0) {
			gfx.fill(node.x + 2, bottom - 3, node.x + node.w - 2, bottom - 1, 0x80000000);
			gfx.fill(node.x + 2, bottom - 3, node.x + 2 + (int) ((node.w - 4) * progress), bottom - 1, 0xFFFFFFFF);
		}
		String loop = ScriptEngine.loopLabel(node.block);
		if (loop != null) {
			int w = font.width(loop) + 6;
			int lx = node.x + node.w - w - 3;
			gfx.fill(lx, node.rowY + 3, lx + w, node.rowY + node.rowH - 3, 0xC0000000);
			gfx.centered(font, loop, lx, node.rowY + 3, w, node.rowH - 6, 0xFFFFFFFF);
		}
	}

	public Node nodeAt(List<Node> nodes, double mx, double my) {
		for (int i = nodes.size() - 1; i >= 0; i--) {
			Node node = nodes.get(i);
			Node inner = nodeAt(node.children, mx, my);
			if (inner == null) {
				inner = nodeAt(node.body, mx, my);
			}
			if (inner == null) {
				inner = nodeAt(node.elseBody, mx, my);
			}
			if (inner != null) {
				return inner;
			}
			if (node.selfContains(mx, my)) {
				return node;
			}
		}
		return null;
	}

	public Node badgeAt(List<Node> nodes, double mx, double my) {
		for (Node node : nodes) {
			Node inner = badgeAt(node.children, mx, my);
			if (inner == null) {
				inner = badgeAt(node.body, mx, my);
			}
			if (inner == null) {
				inner = badgeAt(node.elseBody, mx, my);
			}
			if (inner != null) {
				return inner;
			}
			if (issues.containsKey(node.block) && node.badgeContains(mx, my)) {
				return node;
			}
		}
		return null;
	}

	public SlotBox slotAt(List<Node> nodes, double mx, double my) {
		return slotAt(nodes, mx, my, false);
	}

	public SlotBox slotAtAny(List<Node> nodes, double mx, double my) {
		return slotAt(nodes, mx, my, true);
	}

	private SlotBox slotAt(List<Node> nodes, double mx, double my, boolean includeFilled) {
		for (Node node : nodes) {
			SlotBox inner = slotAt(node.children, mx, my, includeFilled);
			if (inner == null) {
				inner = slotAt(node.body, mx, my, includeFilled);
			}
			if (inner == null) {
				inner = slotAt(node.elseBody, mx, my, includeFilled);
			}
			if (inner != null) {
				return inner;
			}
			for (SlotBox slot : node.slots) {
				if ((includeFilled || !slot.filled()) && slot.contains(mx, my)) {
					return slot;
				}
			}
		}
		return null;
	}

	public boolean contains(List<Node> nodes, Node target) {
		for (Node node : nodes) {
			if (node == target || contains(node.children, target) || contains(node.body, target) || contains(node.elseBody, target)) {
				return true;
			}
		}
		return false;
	}

	public Node find(List<Node> nodes, Scripts.Block block) {
		for (Node node : nodes) {
			if (node.block == block) {
				return node;
			}
			Node inner = find(node.children, block);
			if (inner == null) {
				inner = find(node.body, block);
			}
			if (inner == null) {
				inner = find(node.elseBody, block);
			}
			if (inner != null) {
				return inner;
			}
		}
		return null;
	}
}
