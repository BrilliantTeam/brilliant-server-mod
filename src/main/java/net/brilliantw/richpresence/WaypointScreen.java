package net.brilliantw.richpresence;

import java.util.ArrayList;
import java.util.List;
//? if >=26.1 {
import net.minecraft.client.gui.GuiGraphicsExtractor;
//?} else {
/*import net.minecraft.client.gui.GuiGraphics;
*///?}
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;

public final class WaypointScreen extends Screen {
	private record Rect(int x, int y, int w, int h) {
		boolean contains(double mx, double my) {
			return mx >= x && mx < x + w && my >= y && my < y + h;
		}
	}

	private record Hit(Rect rect, Runnable action) {}

	private static final int HEADER = 26;
	private static final int FOOTER = 14;
	private static final int ROW = 30;

	private final Screen parent;
	private final WorldMaps.View view;
	private final List<Hit> hits = new ArrayList<>();
	private double scroll;
	private Waypoints.Point confirmDelete;
	private Rect addButton;

	public WaypointScreen(Screen parent) {
		this(parent, WorldMaps.current());
	}

	public WaypointScreen(Screen parent, WorldMaps.View view) {
		super(Component.literal("記錄點"));
		this.parent = parent;
		this.view = view;
	}

	private static boolean hidden() {
		return ScriptExtras.coordsHidden(Minecraft.getInstance().player);
	}

	public static void openAdd(Screen parent) {
		LocalPlayer player = Minecraft.getInstance().player;
		WorldMaps.View view = WorldMaps.current();
		if (player == null || view == null) {
			return;
		}
		openAddAt(parent, view, player.blockPosition().getX(), player.blockPosition().getY(), player.blockPosition().getZ());
	}

	public static void openAddAt(Screen parent, WorldMaps.View view, int x, int y, int z) {
		CommandMenuScreen.setScreen(dialog(parent, view, null, x, y, z));
	}

	public static void edit(Screen parent, WorldMaps.View view, Waypoints.Point point) {
		CommandMenuScreen.setScreen(dialog(parent, view, point, point.x, point.y, point.z));
	}

	private static Screen dialog(Screen parent, WorldMaps.View view, Waypoints.Point existing, int x, int y, int z) {
		boolean hidden = hidden();
		List<EditDialog.Field> fields = new ArrayList<>();
		fields.add(new EditDialog.Field("名稱", "例如：家、礦坑入口", existing != null ? existing.name : "", Waypoints.MAX_NAME));
		if (!hidden) {
			fields.add(new EditDialog.Field("X", "", Integer.toString(x), 9));
			fields.add(new EditDialog.Field("Y", "", Integer.toString(y), 5));
			fields.add(new EditDialog.Field("Z", "", Integer.toString(z), 9));
		}
		List<String> colors = Waypoints.COLORS.stream().map(Waypoints.Swatch::name).toList();
		int colorIndex = existing != null ? Math.floorMod(existing.color, colors.size()) : Waypoints.list(view.waypoints()).size() % colors.size();
		String title = existing == null ? "新增記錄點" : "編輯記錄點";
		return new EditDialog(parent, title, fields, colors, colorIndex, (values, group) -> {
			int px = x;
			int py = y;
			int pz = z;
			if (!hidden) {
				try {
					px = Integer.parseInt(values.get(1).strip());
					py = Integer.parseInt(values.get(2).strip());
					pz = Integer.parseInt(values.get(3).strip());
				} catch (NumberFormatException e) {
					return "座標要填整數";
				}
				if (Math.abs(px) > 30_000_000 || Math.abs(pz) > 30_000_000 || py < -2048 || py > 2048) {
					return "座標超出世界範圍";
				}
			}
			if (existing == null) {
				return Waypoints.add(view.waypoints(), values.get(0), px, py, pz, group);
			}
			existing.name = Waypoints.clean(values.get(0));
			existing.x = px;
			existing.y = py;
			existing.z = pz;
			existing.color = group;
			Waypoints.save();
			return null;
		}).choice("顏色");
	}

	private Rect panel() {
		int w = Math.min(width - 16, 420);
		return new Rect((width - w) / 2, 8, w, height - 16);
	}

	//? if >=26.1 {
	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		draw(new Gfx(graphics), mouseX, mouseY);
		super.extractRenderState(graphics, mouseX, mouseY, partialTick);
	}
	//?} else {
	/*@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		draw(new Gfx(graphics), mouseX, mouseY);
		super.render(graphics, mouseX, mouseY, partialTick);
	}
	*///?}

	private void draw(Gfx gfx, int mouseX, int mouseY) {
		hits.clear();
		addButton = null;
		Rect p = panel();
		gfx.fill(p.x(), p.y(), p.x() + p.w(), p.y() + p.h(), Theme.PANEL);
		gfx.fill(p.x(), p.y(), p.x() + p.w(), p.y() + HEADER, Theme.HEADER);
		gfx.fill(p.x(), p.y() + HEADER - 1, p.x() + p.w(), p.y() + HEADER, Theme.ACCENT);
		Theme.drawBack(gfx, font, p.x(), p.y(), mouseX, mouseY);
		String where = view == null ? "" : "・" + WorldMaps.shardLabel(view.shard()) + "・" + WorldMaps.dimensionLabel(view.dimension());
		gfx.text(font, "記錄點" + where, Theme.titleX(font, p.x()), p.y() + 9, Theme.TEXT);
		if (view == null) {
			gfx.centered(font, "進入伺服器後才能管理記錄點", p.x(), p.y() + p.h() / 2 - 5, p.w(), 10, Theme.TEXT_SECONDARY);
			return;
		}
		WorldMaps.View current = WorldMaps.current();
		boolean here = current != null && current.waypoints().equals(view.waypoints());
		if (here) {
			String add = "＋ 新增目前位置";
			int w = font.width(add) + 14;
			Rect r = new Rect(p.x() + p.w() - w - 8, p.y() + 6, w, 14);
			gfx.fill(r.x(), r.y(), r.x() + r.w(), r.y() + r.h(), r.contains(mouseX, mouseY) ? Theme.CHIP_HOVER : Theme.ACCENT_DIM);
			gfx.centered(font, add, r.x(), r.y(), r.w(), r.h(), Theme.ACCENT);
			addButton = r;
		}
		Rect c = new Rect(p.x() + 10, p.y() + HEADER + 6, p.w() - 20, p.h() - HEADER - FOOTER - 10);
		List<Waypoints.Point> points = Waypoints.list(view.waypoints());
		int total = points.size() * ROW;
		scroll = Math.clamp(scroll, 0, Math.max(0, total - c.h()));
		if (points.isEmpty()) {
			gfx.centered(font, "還沒有記錄點。按 B 或右上角新增目前位置，也可以在大地圖按右鍵新增。", c.x(), c.y() + 20, c.w(), 10, Theme.TEXT_SECONDARY);
		}
		LocalPlayer player = minecraft.player;
		boolean hidden = hidden();
		gfx.scissor(c.x(), c.y(), c.x() + c.w(), c.y() + c.h());
		int y = c.y() - (int) scroll;
		for (Waypoints.Point point : List.copyOf(points)) {
			Rect row = new Rect(c.x(), y, c.w() - 4, ROW - 2);
			if (row.y() + row.h() > c.y() && row.y() < c.y() + c.h()) {
				drawRow(gfx, row, point, player, hidden, here, mouseX, mouseY, c);
			}
			y += ROW;
		}
		gfx.endScissor();
		gfx.fill(p.x(), p.y() + p.h() - FOOTER, p.x() + p.w(), p.y() + p.h() - FOOTER + 1, Theme.HEADER);
		gfx.text(font, "記錄點只是標記，不能傳送 · 點眼睛切換在地圖上顯示", p.x() + 8, p.y() + p.h() - FOOTER + 4, Theme.TEXT_DIM);
	}

	private void drawRow(Gfx gfx, Rect row, Waypoints.Point point, LocalPlayer player, boolean hidden, boolean here, int mouseX, int mouseY, Rect clip) {
		boolean inClip = clip.contains(mouseX, mouseY);
		gfx.fill(row.x(), row.y(), row.x() + row.w(), row.y() + row.h(), inClip && row.contains(mouseX, mouseY) ? Theme.CARD_HOVER : Theme.CARD);
		int color = Waypoints.color(point);
		gfx.push(row.x() + 14, row.y() + row.h() / 2f, 1);
		gfx.rotate((float) (Math.PI / 4));
		gfx.fill(-5, -5, 5, 5, 0xFF101317);
		gfx.fill(-4, -4, 4, 4, point.visible ? color : MapMath.alpha(color, 0.35));
		gfx.pop();
		gfx.text(font, font.plainSubstrByWidth(point.name, row.w() - 170), row.x() + 28, row.y() + 5, point.visible ? Theme.TEXT : Theme.TEXT_DIM);
		String detail;
		if (hidden) {
			detail = "伺服器隱藏了座標資訊";
		} else {
			detail = point.x + ", " + point.y + ", " + point.z;
			if (here && player != null) {
				detail += "　距離 " + Math.round(Math.sqrt(player.distanceToSqr(point.x + 0.5, point.y, point.z + 0.5))) + " 格";
			}
		}
		gfx.text(font, detail, row.x() + 28, row.y() + 16, Theme.TEXT_DIM);
		int right = row.x() + row.w() - 6;
		boolean confirming = confirmDelete == point;
		right = button(gfx, right, row.y() + 7, confirming ? "確定刪除？" : "刪除", confirming ? Theme.DANGER : Theme.TEXT, mouseX, mouseY, clip, () -> {
			if (confirmDelete == point) {
				Waypoints.remove(view.waypoints(), point);
				confirmDelete = null;
			} else {
				confirmDelete = point;
			}
		});
		right = button(gfx, right, row.y() + 7, "編輯", Theme.TEXT, mouseX, mouseY, clip, () -> edit(this, view, point));
		Rect eye = new Rect(right - 18, row.y() + 7, 18, 14);
		gfx.fill(eye.x(), eye.y(), eye.x() + eye.w(), eye.y() + eye.h(), inClip && eye.contains(mouseX, mouseY) ? Theme.CHIP_HOVER : Theme.CHIP);
		(point.visible ? Icons.EYE : Icons.EYE_OFF).drawCentered(gfx, eye.x(), eye.y(), eye.w(), eye.h(), point.visible ? Theme.TEXT : Theme.TEXT_DIM);
		hits.add(new Hit(eye, () -> {
			point.visible = !point.visible;
			Waypoints.save();
		}));
	}

	private int button(Gfx gfx, int right, int y, String label, int color, int mouseX, int mouseY, Rect clip, Runnable action) {
		int w = font.width(label) + 12;
		Rect r = new Rect(right - w, y, w, 14);
		gfx.fill(r.x(), r.y(), r.x() + r.w(), r.y() + r.h(), clip.contains(mouseX, mouseY) && r.contains(mouseX, mouseY) ? Theme.CHIP_HOVER : Theme.CHIP);
		gfx.centered(font, label, r.x(), r.y(), r.w(), r.h(), color);
		hits.add(new Hit(r, action));
		return r.x() - 4;
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (super.mouseClicked(event, doubleClick)) {
			return true;
		}
		Rect p = panel();
		if (Theme.backHit(font, p.x(), p.y(), event.x(), event.y())) {
			onClose();
			return true;
		}
		if (addButton != null && addButton.contains(event.x(), event.y())) {
			openAdd(this);
			return true;
		}
		Rect c = new Rect(p.x() + 10, p.y() + HEADER + 6, p.w() - 20, p.h() - HEADER - FOOTER - 10);
		if (c.contains(event.x(), event.y())) {
			for (Hit hit : List.copyOf(hits)) {
				if (hit.rect().contains(event.x(), event.y())) {
					hit.action().run();
					return true;
				}
			}
		}
		confirmDelete = null;
		return false;
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		scroll -= scrollY * 24;
		return true;
	}

	@Override
	public void onClose() {
		CommandMenuScreen.setScreen(parent);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
