package net.brilliantw.richpresence;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.NativeImage;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
//? if >=26.1 {
import net.minecraft.client.gui.GuiGraphicsExtractor;
//?} else {
/*import net.minecraft.client.gui.GuiGraphics;
*///?}
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.Holder;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.phys.Vec3;

public final class WorldMapScreen extends Screen {
	private static final int HEADER = 24;
	private static final int FOOTER = 16;
	private static final double MIN_SCALE = 0.5;
	private static final double MAX_SCALE = 8;
	private static final double MIN_SEED_SCALE = 1.0 / 32;
	private static final int PANEL = 176;
	private static final int ROW = 16;
	private static final int ROW2 = 24;
	private static final List<String> DIMENSIONS = List.of("minecraft:overworld", "minecraft:the_nether", "minecraft:the_end");
	private static final List<String> SHARDS = List.of("s1.brilliantw.net", "s2.brilliantw.net", "s3.brilliantw.net", "s4.brilliantw.net");
	private static Identifier arrow;
	private static double rememberedScale = 1;
	private static boolean seedMode;
	private static boolean queryOpen;
	private static Tab tab = Tab.STRUCTURES;
	private static String query = "";

	private enum Tab { STRUCTURES, BIOMES, RESIDENCES, HOMES }

	private record Rect(int x, int y, int w, int h) {
		boolean contains(double mx, double my) {
			return mx >= x && mx < x + w && my >= y && my < y + h;
		}
	}

	private record Hit(Rect rect, Runnable action) {}

	private final Screen parent;
	private final List<Hit> hits = new ArrayList<>();
	private WorldMaps.View view;
	private double centerX;
	private double centerZ;
	private double scale = rememberedScale;
	private boolean dragging;
	private double dragged;
	private Waypoints.Point hovered;
	private MapMarkers.Area hoveredArea;
	private MapMarkers.Poi hoveredPoi;
	private SeedWorld.Found hoveredStructure;
	private final List<String> hoveredPlayers = new ArrayList<>();
	private int highlight = -1;
	private int panelScroll;
	private boolean searching;
	private String notice;
	private String targetLabel;
	private int targetX;
	private int targetZ;
	private long targetAt;
	private EditBox search;
	private long clearArmedAt;
	private MapMarkers.Area selectedArea;

	public WorldMapScreen(Screen parent) {
		super(Component.literal("地圖"));
		this.parent = parent;
		this.view = WorldMaps.current();
		LocalPlayer player = Minecraft.getInstance().player;
		if (player != null) {
			centerX = player.getX();
			centerZ = player.getZ();
		}
	}

	@Override
	protected void init() {
		search = new EditBox(font, 0, 0, PANEL - 12, 16, Component.literal("搜尋"));
		search.setMaxLength(48);
		Theme.hint(search, "搜尋名稱或擁有者…");
		search.setValue(query);
		search.setResponder(value -> {
			query = value;
			panelScroll = 0;
		});
		search.visible = false;
		addRenderableWidget(search);
	}

	private Rect mapArea() {
		return new Rect(0, HEADER, width - (panelOpen() ? PANEL : 0), height - HEADER - FOOTER);
	}

	private boolean panelOpen() {
		return view != null && (seedActive() || queryOpen);
	}

	private Tab currentTab() {
		if (!seedActive() && (tab == Tab.STRUCTURES || tab == Tab.BIOMES)) {
			return Tab.RESIDENCES;
		}
		if (!queryOpen && !seedActive()) {
			return Tab.RESIDENCES;
		}
		return tab;
	}

	private boolean seedActive() {
		return seedMode && view != null && SeedMaps.seed(view) != null;
	}

	private double minScale() {
		return seedActive() ? MIN_SEED_SCALE : MIN_SCALE;
	}

	private double toScreenX(double worldX) {
		Rect area = mapArea();
		return area.x() + area.w() / 2.0 + (worldX - centerX) * scale;
	}

	private double toScreenY(double worldZ) {
		Rect area = mapArea();
		return area.y() + area.h() / 2.0 + (worldZ - centerZ) * scale;
	}

	private double toWorldX(double screenX) {
		Rect area = mapArea();
		return centerX + (screenX - area.x() - area.w() / 2.0) / scale;
	}

	private double toWorldZ(double screenY) {
		Rect area = mapArea();
		return centerZ + (screenY - area.y() - area.h() / 2.0) / scale;
	}

	private boolean coordsHidden() {
		return ScriptExtras.coordsHidden(Minecraft.getInstance().player);
	}

	private boolean viewingCurrent() {
		WorldMaps.View current = WorldMaps.current();
		return current != null && view != null && current.shard().equals(view.shard()) && current.dimension().equals(view.dimension());
	}

	//? if >=26.1 {
	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		draw(new Gfx(graphics), mouseX, mouseY, partialTick);
		super.extractRenderState(graphics, mouseX, mouseY, partialTick);
	}
	//?} else {
	/*@Override
	public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		draw(new Gfx(graphics), mouseX, mouseY, partialTick);
		super.render(graphics, mouseX, mouseY, partialTick);
	}
	*///?}

	private void draw(Gfx gfx, int mouseX, int mouseY, float partial) {
		hits.clear();
		hovered = null;
		hoveredArea = null;
		hoveredPoi = null;
		hoveredStructure = null;
		hoveredPlayers.clear();
		if (search != null) {
			search.visible = false;
		}
		gfx.fill(0, 0, width, height, 0xFF0E1114);
		if (view == null) {
			gfx.centered(font, "目前沒有可顯示的地圖", 0, height / 2 - 5, width, 10, Theme.TEXT_SECONDARY);
			header(gfx, mouseX, mouseY);
			return;
		}
		Rect area = mapArea();
		gfx.scissor(area.x(), area.y(), area.x() + area.w(), area.y() + area.h());
		List<Object> wanted = new ArrayList<>();
		if (seedActive()) {
			drawSeed(gfx, area, wanted);
		} else {
			drawTerrain(gfx, area);
		}
		drawRegen(gfx, area);
		drawAreas(gfx, mouseX, mouseY, area);
		outlineSelected(gfx);
		drawPois(gfx, mouseX, mouseY, area);
		if (seedActive()) {
			drawStructures(gfx, mouseX, mouseY, area, wanted);
		}
		drawWaypoints(gfx, mouseX, mouseY, area);
		drawPlayers(gfx, partial, mouseX, mouseY);
		drawTarget(gfx);
		gfx.endScissor();
		SeedMaps.want(wanted);
		SeedMaps.evict();
		header(gfx, mouseX, mouseY);
		footer(gfx, mouseX, mouseY, area);
		if (panelOpen()) {
			panel(gfx, mouseX, mouseY);
		}
		if (!hoveredPlayers.isEmpty() && hovered == null) {
			List<Component> lines = new ArrayList<>();
			lines.add(Component.literal(hoveredPlayers.size() > 1 ? hoveredPlayers.size() + " 位玩家" : "玩家"));
			for (String name : hoveredPlayers) {
				lines.add(Component.literal(name));
			}
			gfx.tooltip(font, lines, mouseX, mouseY);
		} else if (hovered != null) {
			List<Component> lines = new ArrayList<>();
			lines.add(Component.literal(hovered.name));
			if (!coordsHidden()) {
				lines.add(Component.literal(hovered.x + ", " + hovered.y + ", " + hovered.z));
				LocalPlayer player = minecraft.player;
				if (player != null && viewingCurrent()) {
					lines.add(Component.literal("距離 " + Math.round(Math.sqrt(player.distanceToSqr(hovered.x + 0.5, hovered.y, hovered.z + 0.5))) + " 格"));
				}
			}
			lines.add(Component.literal(hovered.death ? "點擊可編輯 · 這是自動標示的死亡點" : "點擊編輯"));
			gfx.tooltip(font, lines, mouseX, mouseY);
		} else if (hoveredStructure != null) {
			List<Component> lines = new ArrayList<>();
			lines.add(Component.literal(structureName(hoveredStructure.id())));
			if (!coordsHidden()) {
				lines.add(Component.literal(hoveredStructure.x() + ", " + hoveredStructure.z()));
				LocalPlayer player = minecraft.player;
				if (player != null && viewingCurrent()) {
					lines.add(Component.literal("距離 " + Math.round(Math.hypot(player.getX() - hoveredStructure.x(), player.getZ() - hoveredStructure.z())) + " 格"));
				}
			}
			lines.add(Component.literal("依種子推算，實際可能因地形而未生成"));
			gfx.tooltip(font, lines, mouseX, mouseY);
		} else if (hoveredPoi != null) {
			List<Component> lines = new ArrayList<>();
			lines.add(Component.literal("公開傳送點"));
			lines.add(Component.literal(hoveredPoi.detail().isEmpty() ? hoveredPoi.label() : hoveredPoi.detail()));
			if (MapMarkers.homeCommand(hoveredPoi) != null) {
				lines.add(Component.literal("點擊傳送"));
			}
			gfx.tooltip(font, lines, mouseX, mouseY);
		} else if (hoveredArea != null && hoveredArea.kind() == MapMarkers.AreaKind.RESIDENCE) {
			List<Component> lines = new ArrayList<>();
			lines.add(Component.literal("領地：" + hoveredArea.label()));
			if (!hoveredArea.owner().isEmpty()) {
				lines.add(Component.literal("擁有者：" + hoveredArea.owner()));
			}
			gfx.tooltip(font, lines, mouseX, mouseY);
		}
	}

	private double[] visibleWorld(Rect area) {
		return new double[] { toWorldX(area.x()), toWorldZ(area.y()), toWorldX(area.x() + area.w()), toWorldZ(area.y() + area.h()) };
	}

	private void drawRegen(Gfx gfx, Rect area) {
		if (!ModSettings.get().mapRegen) {
			return;
		}
		MapMarkers.Regen regen = WorldMaps.regen(view);
		if (regen.minutes().isEmpty()) {
			return;
		}
		double[] w = visibleWorld(area);
		for (int cz = Math.floorDiv((int) Math.floor(w[1]), 16); cz <= Math.floorDiv((int) Math.floor(w[3]), 16); cz++) {
			for (int cx = Math.floorDiv((int) Math.floor(w[0]), 16); cx <= Math.floorDiv((int) Math.floor(w[2]), 16); cx++) {
				Integer left = regen.minutesLeft(cx, cz);
				if (left == null) {
					continue;
				}
				int x0 = (int) Math.round(toScreenX(cx * 16.0));
				int y0 = (int) Math.round(toScreenY(cz * 16.0));
				int x1 = (int) Math.round(toScreenX(cx * 16.0 + 16));
				int y1 = (int) Math.round(toScreenY(cz * 16.0 + 16));
				gfx.fill(x0, y0, x1, y1, left <= 0 ? Minimap.REGEN_READY : Minimap.REGEN_PENDING);
				if (scale >= 2) {
					int edge = left <= 0 ? 0x80FF4D4D : 0x70FFB020;
					gfx.fill(x0, y0, x1, y0 + 1, edge);
					gfx.fill(x0, y0, x0 + 1, y1, edge);
				}
			}
		}
	}

	private void drawAreas(Gfx gfx, int mouseX, int mouseY, Rect area) {
		if (!ModSettings.get().mapResidences) {
			return;
		}
		double[] w = visibleWorld(area);
		double mx = toWorldX(mouseX);
		double mz = toWorldZ(mouseY);
		boolean inside = area.contains(mouseX, mouseY);
		for (MapMarkers.Area a : WorldMaps.markers(view).areas()) {
			if (a.maxX() < w[0] || a.minX() > w[2] || a.maxZ() < w[1] || a.minZ() > w[3]) {
				continue;
			}
			int fill = MapMath.alpha(a.fill() | 0xFF000000, Math.min(0.32, Math.max(0.12, (a.fill() >>> 24) / 255.0)));
			int line = MapMath.alpha(a.line() | 0xFF000000, 0.9);
			fillPolygon(gfx, a, fill, area);
			for (int i = 0, j = a.xs().length - 1; i < a.xs().length; j = i++) {
				line(gfx, toScreenX(a.xs()[j]), toScreenY(a.zs()[j]), toScreenX(a.xs()[i]), toScreenY(a.zs()[i]), line);
			}
			if (inside && a.kind() == MapMarkers.AreaKind.RESIDENCE && a.contains(mx, mz)
					&& (hoveredArea == null || (a.maxX() - a.minX()) * (a.maxZ() - a.minZ()) < (hoveredArea.maxX() - hoveredArea.minX()) * (hoveredArea.maxZ() - hoveredArea.minZ()))) {
				hoveredArea = a;
			}
			double widthPx = (a.maxX() - a.minX()) * scale;
			if (a.kind() == MapMarkers.AreaKind.RESIDENCE && widthPx > font.width(a.label()) + 8 && (a.maxZ() - a.minZ()) * scale > 14) {
				int cx = (int) Math.round(toScreenX((a.minX() + a.maxX()) / 2));
				int cy = (int) Math.round(toScreenY((a.minZ() + a.maxZ()) / 2));
				int tw = font.width(a.label());
				gfx.text(font, a.label(), cx - tw / 2 + 1, cy - 3, 0xA0000000);
				gfx.text(font, a.label(), cx - tw / 2, cy - 4, 0xFFFFFFFF);
			}
		}
	}

	private void outlineSelected(Gfx gfx) {
		MapMarkers.Area a = selectedArea;
		if (a == null || !ModSettings.get().mapResidences) {
			return;
		}
		for (int i = 0, j = a.xs().length - 1; i < a.xs().length; j = i++) {
			for (int d = 0; d < 2; d++) {
				line(gfx, toScreenX(a.xs()[j]) + d, toScreenY(a.zs()[j]) + d, toScreenX(a.xs()[i]) + d, toScreenY(a.zs()[i]) + d, Theme.ACCENT);
			}
		}
	}

	private void fillPolygon(Gfx gfx, MapMarkers.Area a, int color, Rect clip) {
		double[] xs = a.xs();
		double[] zs = a.zs();
		boolean rectangle = xs.length == 4 && (xs[0] == xs[1] && zs[1] == zs[2] && xs[2] == xs[3] && zs[3] == zs[0]
				|| zs[0] == zs[1] && xs[1] == xs[2] && zs[2] == zs[3] && xs[3] == xs[0]);
		if (rectangle) {
			gfx.fill((int) Math.round(toScreenX(a.minX())), (int) Math.round(toScreenY(a.minZ())), (int) Math.round(toScreenX(a.maxX())), (int) Math.round(toScreenY(a.maxZ())), color);
			return;
		}
		int top = Math.max(clip.y(), (int) Math.floor(toScreenY(a.minZ())));
		int bottom = Math.min(clip.y() + clip.h(), (int) Math.ceil(toScreenY(a.maxZ())));
		List<Double> hits = new ArrayList<>();
		for (int y = top; y < bottom; y++) {
			double wz = toWorldZ(y + 0.5);
			hits.clear();
			for (int i = 0, j = xs.length - 1; i < xs.length; j = i++) {
				if ((zs[i] > wz) != (zs[j] > wz)) {
					hits.add(xs[i] + (wz - zs[i]) * (xs[j] - xs[i]) / (zs[j] - zs[i]));
				}
			}
			hits.sort(Double::compare);
			for (int k = 0; k + 1 < hits.size(); k += 2) {
				gfx.fill((int) Math.round(toScreenX(hits.get(k))), y, (int) Math.round(toScreenX(hits.get(k + 1))), y + 1, color);
			}
		}
	}

	private static void line(Gfx gfx, double x0, double y0, double x1, double y1, int color) {
		if (Math.abs(x0 - x1) < 0.5) {
			int x = (int) Math.round(x0);
			gfx.fill(x, (int) Math.round(Math.min(y0, y1)), x + 1, (int) Math.round(Math.max(y0, y1)) + 1, color);
		} else if (Math.abs(y0 - y1) < 0.5) {
			int y = (int) Math.round(y0);
			gfx.fill((int) Math.round(Math.min(x0, x1)), y, (int) Math.round(Math.max(x0, x1)) + 1, y + 1, color);
		} else {
			int steps = (int) Math.min(2000, Math.ceil(Math.max(Math.abs(x1 - x0), Math.abs(y1 - y0))));
			for (int i = 0; i <= steps; i++) {
				int x = (int) Math.round(x0 + (x1 - x0) * i / steps);
				int y = (int) Math.round(y0 + (y1 - y0) * i / steps);
				gfx.fill(x, y, x + 1, y + 1, color);
			}
		}
	}

	private void drawPois(Gfx gfx, int mouseX, int mouseY, Rect area) {
		if (!ModSettings.get().mapPublicHomes) {
			return;
		}
		boolean inside = area.contains(mouseX, mouseY);
		for (MapMarkers.Poi poi : WorldMaps.markers(view).pois()) {
			int sx = (int) Math.round(toScreenX(poi.x()));
			int sy = (int) Math.round(toScreenY(poi.z()));
			if (sx < area.x() - 60 || sx > area.x() + area.w() + 60 || sy < area.y() - 20 || sy > area.y() + area.h() + 20) {
				continue;
			}
			boolean hot = inside && Math.hypot(mouseX - sx, mouseY - sy) <= 7;
			if (hot) {
				hoveredPoi = poi;
			}
			MapIcons.badge(gfx, sx, sy, hot ? 9 : 7, hot ? 0xFFFFE08A : Minimap.POI, MapIcons.item("ender_pearl"));
			if (scale >= 2 || hot) {
				String label = font.plainSubstrByWidth(poi.label().replaceFirst("^/phome\\s+", ""), 120);
				int w = font.width(label);
				gfx.fill(sx - w / 2 - 3, sy + 10, sx + w / 2 + 3, sy + 21, 0xB0101418);
				gfx.text(font, label, sx - w / 2, sy + 12, Minimap.POI);
			}
		}
	}

	private int seedLod() {
		int lod = 0;
		while (lod < SeedMaps.LODS - 1 && SeedMaps.step(lod) < 0.75 / scale) {
			lod++;
		}
		return lod;
	}

	private void drawSeed(Gfx gfx, Rect area, List<Object> wanted) {
		SeedWorld world = SeedMaps.world(view);
		if (world == null) {
			String failure = SeedMaps.failure(view);
			gfx.centered(font, failure != null ? "種子地圖產生失敗" : "正在依種子產生地圖⋯", area.x(), area.y() + area.h() / 2 - 5, area.w(), 10, Theme.TEXT_SECONDARY);
			return;
		}
		String key = SeedMaps.worldKey(view);
		String layer = SeedMaps.layer(view);
		int lod = seedLod();
		double[] w = visibleWorld(area);
		double cx = (w[0] + w[2]) / 2;
		double cz = (w[1] + w[3]) / 2;
		int paints = 0;
		for (int l = Math.min(lod + 1, SeedMaps.LODS - 1); l >= lod; l--) {
			int span = SeedMaps.span(l);
			List<SeedMaps.TileKey> keys = new ArrayList<>();
			for (int tz = Math.floorDiv((int) Math.floor(w[1]), span); tz <= Math.floorDiv((int) Math.floor(w[3]), span); tz++) {
				for (int tx = Math.floorDiv((int) Math.floor(w[0]), span); tx <= Math.floorDiv((int) Math.floor(w[2]), span); tx++) {
					keys.add(new SeedMaps.TileKey(key, layer, l, tx, tz));
				}
			}
			keys.sort((a, b) -> Double.compare(Math.hypot((a.tx() + 0.5) * span - cx, (a.tz() + 0.5) * span - cz), Math.hypot((b.tx() + 0.5) * span - cx, (b.tz() + 0.5) * span - cz)));
			for (SeedMaps.TileKey k : keys) {
				SeedMaps.Tile tile = SeedMaps.tile(k);
				if (tile == null) {
					wanted.add(k);
					continue;
				}
				boolean fresh = tile.painted != highlight;
				Identifier texture = SeedMaps.texture(tile, highlight, !fresh || paints < 32);
				if (fresh && paints < 32) {
					paints++;
				}
				if (texture != null) {
					blitTile(gfx, texture, (double) k.tx() * span, (double) k.tz() * span, span);
				}
			}
		}
	}

	private boolean groupShown(String group) {
		List<String> shown = ModSettings.get().seedGroups;
		return shown == null ? SeedPalette.info(group).shownByDefault() : shown.contains(group);
	}

	private void toggleGroup(SeedWorld world, String group) {
		ModSettings.Data s = ModSettings.get();
		if (s.seedGroups == null) {
			s.seedGroups = new ArrayList<>(SeedPalette.GROUPS.stream().filter(SeedPalette.Group::shownByDefault).map(SeedPalette.Group::id).toList());
			for (String g : world.groups()) {
				if (SeedPalette.info(g).shownByDefault() && !s.seedGroups.contains(g)) {
					s.seedGroups.add(g);
				}
			}
		} else {
			s.seedGroups = new ArrayList<>(s.seedGroups);
		}
		if (!s.seedGroups.remove(group)) {
			s.seedGroups.add(group);
		}
		ModSettings.save();
	}

	private void drawStructures(Gfx gfx, int mouseX, int mouseY, Rect area, List<Object> wanted) {
		if (scale < 1.0 / 16 || SeedMaps.world(view) == null) {
			return;
		}
		String key = SeedMaps.worldKey(view);
		int span = SeedWorld.CELL * 16;
		double[] w = visibleWorld(area);
		boolean inside = area.contains(mouseX, mouseY);
		List<Object> cells = new ArrayList<>();
		for (int cz = Math.floorDiv((int) Math.floor(w[1]) - 64, span); cz <= Math.floorDiv((int) Math.floor(w[3]) + 64, span); cz++) {
			for (int cx = Math.floorDiv((int) Math.floor(w[0]) - 64, span); cx <= Math.floorDiv((int) Math.floor(w[2]) + 64, span); cx++) {
				SeedMaps.CellKey cell = new SeedMaps.CellKey(key, cx, cz);
				List<SeedWorld.Found> found = SeedMaps.cell(cell);
				if (found == null) {
					cells.add(cell);
					continue;
				}
				for (SeedWorld.Found f : found) {
					if (!groupShown(f.group())) {
						continue;
					}
					int sx = (int) Math.round(toScreenX(f.x() + 0.5));
					int sy = (int) Math.round(toScreenY(f.z() + 0.5));
					if (sx < area.x() - 10 || sx > area.x() + area.w() + 10 || sy < area.y() - 10 || sy > area.y() + area.h() + 10) {
						continue;
					}
					SeedPalette.Group info = SeedPalette.info(f.group());
					boolean hot = inside && Math.hypot(mouseX - sx, mouseY - sy) <= 8;
					if (hot) {
						hoveredStructure = f;
					}
					int r = hot ? 10 : 8;
					MapIcons.badge(gfx, sx, sy, r, info.color(), MapIcons.item(info.icon()));
					if (scale >= 1 || hot) {
						int tw = font.width(info.name());
						gfx.fill(sx - tw / 2 - 3, sy + r + 2, sx + tw / 2 + 3, sy + r + 13, 0xB0101418);
						gfx.text(font, info.name(), sx - tw / 2, sy + r + 4, info.color());
					}
				}
			}
		}
		wanted.addAll(0, cells);
	}

	private void drawTarget(Gfx gfx) {
		if (targetLabel == null) {
			return;
		}
		int sx = (int) Math.round(toScreenX(targetX + 0.5));
		int sy = (int) Math.round(toScreenY(targetZ + 0.5));
		double pulse = (System.currentTimeMillis() - targetAt) % 1200 / 1200.0;
		int r = 9 + (int) Math.round(pulse * 8);
		int color = MapMath.alpha(Theme.ACCENT, 1 - pulse * 0.8);
		line(gfx, sx - r, sy - r, sx + r, sy - r, color);
		line(gfx, sx - r, sy + r, sx + r, sy + r, color);
		line(gfx, sx - r, sy - r, sx - r, sy + r, color);
		line(gfx, sx + r, sy - r, sx + r, sy + r, color);
	}

	private int[] searchOrigin() {
		LocalPlayer player = minecraft.player;
		if (player != null && viewingCurrent()) {
			return new int[] { (int) Math.floor(player.getX()), (int) Math.floor(player.getZ()) };
		}
		return new int[] { (int) Math.floor(centerX), (int) Math.floor(centerZ) };
	}

	private void showTarget(int x, int z, String label) {
		targetX = x;
		targetZ = z;
		targetLabel = label;
		targetAt = System.currentTimeMillis();
		centerX = x + 0.5;
		centerZ = z + 0.5;
		notice = null;
	}

	private void findStructure(String group) {
		if (searching) {
			return;
		}
		WorldMaps.View from = view;
		int[] origin = searchOrigin();
		String name = SeedPalette.info(group).name();
		searching = true;
		notice = null;
		SeedMaps.nearestStructure(view, group, origin[0], origin[1], found -> {
			searching = false;
			if (view != from) {
				return;
			}
			if (found == null) {
				targetLabel = null;
				notice = "附近 12000 格內找不到" + name;
			} else {
				showTarget(found.x(), found.z(), name);
				scale = Math.max(scale, 0.5);
			}
		});
	}

	private void findBiome(SeedWorld world, int index) {
		if (searching) {
			return;
		}
		WorldMaps.View from = view;
		int[] origin = searchOrigin();
		Holder<Biome> biome = world.biomes().get(index);
		String name = SeedMaps.biomeName(biome);
		searching = true;
		notice = null;
		SeedMaps.nearestBiome(view, biome, origin[0], origin[1], pos -> {
			searching = false;
			if (view != from) {
				return;
			}
			if (pos == null) {
				targetLabel = null;
				notice = "附近 12000 格內找不到" + name;
			} else {
				showTarget(pos.getX(), pos.getZ(), name);
			}
		});
	}

	private static final Map<String, String> VARIANT_NAMES = Map.ofEntries(Map.entry("snowy", "雪原"), Map.entry("beached", "擱淺"), Map.entry("cold", "冷水"),
			Map.entry("warm", "暖水"), Map.entry("mountain", "山地"), Map.entry("nether", "地獄"), Map.entry("mesa", "惡地"));

	private String structureName(String id) {
		SeedPalette.Group info = SeedPalette.info(SeedPalette.group(id));
		String suffix = id.length() > info.id().length() && id.startsWith(info.id()) ? id.substring(info.id().length() + 1)
				: id.startsWith("village_") ? id.substring(8) : "";
		if (suffix.isEmpty()) {
			return info.name();
		}
		String label = VARIANT_NAMES.get(suffix);
		if (label == null && Language.getInstance().has("biome.minecraft." + suffix)) {
			label = Language.getInstance().getOrDefault("biome.minecraft." + suffix);
		}
		return label == null ? info.name() : info.name() + "（" + label + "）";
	}

	private void setAllGroups(SeedWorld world, boolean shown) {
		ModSettings.Data s = ModSettings.get();
		List<String> list = s.seedGroups == null
				? new ArrayList<>(SeedPalette.GROUPS.stream().filter(SeedPalette.Group::shownByDefault).map(SeedPalette.Group::id).toList())
				: new ArrayList<>(s.seedGroups);
		list.removeAll(world.groups());
		if (shown) {
			list.addAll(world.groups());
		}
		s.seedGroups = list;
		ModSettings.save();
	}

	private static boolean matches(String q, String... texts) {
		if (q.isBlank()) {
			return true;
		}
		String needle = q.strip().toLowerCase(java.util.Locale.ROOT);
		for (String text : texts) {
			if (text != null && text.toLowerCase(java.util.Locale.ROOT).contains(needle)) {
				return true;
			}
		}
		return false;
	}

	private String distanceText(double x, double z) {
		if (coordsHidden()) {
			return "";
		}
		int[] o = searchOrigin();
		return Math.round(Math.hypot(x - o[0], z - o[1])) + " 格";
	}

	private void focusArea(MapMarkers.Area a) {
		selectedArea = a;
		int cx = (int) Math.floor((a.minX() + a.maxX()) / 2);
		int cz = (int) Math.floor((a.minZ() + a.maxZ()) / 2);
		showTarget(cx, cz, a.label());
		Rect area = mapArea();
		double fit = Math.min(area.w() / Math.max(16, (a.maxX() - a.minX()) * 1.6), area.h() / Math.max(16, (a.maxZ() - a.minZ()) * 1.6));
		scale = Math.clamp(fit, minScale(), 4);
	}

	private void panel(Gfx gfx, int mouseX, int mouseY) {
		int x = width - PANEL;
		int top = HEADER;
		int bottom = height - FOOTER;
		gfx.fill(x, top, width, bottom, Theme.SIDEBAR);
		gfx.fill(x, top, x + 1, bottom, 0xFF0B0E11);
		Tab current = currentTab();
		List<Tab> tabs = new ArrayList<>();
		if (seedActive()) {
			tabs.add(Tab.STRUCTURES);
			tabs.add(Tab.BIOMES);
		}
		tabs.add(Tab.RESIDENCES);
		tabs.add(Tab.HOMES);
		int tabW = (PANEL - 12) / tabs.size();
		for (int i = 0; i < tabs.size(); i++) {
			Tab t = tabs.get(i);
			String label = switch (t) {
				case STRUCTURES -> "結構";
				case BIOMES -> "生態域";
				case RESIDENCES -> "領地";
				case HOMES -> "傳送點";
			};
			tab(gfx, x + 6 + i * tabW, top + 6, tabW - (i < tabs.size() - 1 ? 2 : 0), label, t == current, mouseX, mouseY, () -> {
				tab = t;
				panelScroll = 0;
			});
		}
		if (current == Tab.RESIDENCES || current == Tab.HOMES) {
			queryPanel(gfx, mouseX, mouseY, x, top, bottom, current);
			return;
		}
		int listTop = top + 26;
		int listBottom = bottom - 46;
		SeedWorld world = SeedMaps.world(view);
		if (world == null) {
			gfx.centered(font, SeedMaps.failure(view) != null ? "產生失敗" : "載入中⋯", x, listTop + 10, PANEL, 10, Theme.TEXT_SECONDARY);
		} else {
			if (current == Tab.STRUCTURES) {
				int bw = (PANEL - 16) / 2;
				Rect all = new Rect(x + 6, listTop, bw, 14);
				Rect none = new Rect(x + 10 + bw, listTop, bw, 14);
				for (Rect r : List.of(all, none)) {
					boolean hot = r.contains(mouseX, mouseY);
					gfx.fill(r.x(), r.y(), r.x() + r.w(), r.y() + r.h(), hot ? Theme.CHIP_HOVER : Theme.CHIP);
					gfx.centered(font, r == all ? "全選" : "全不選", r.x(), r.y(), r.w(), r.h(), Theme.TEXT);
				}
				hits.add(new Hit(all, () -> setAllGroups(world, true)));
				hits.add(new Hit(none, () -> setAllGroups(world, false)));
				listTop += 18;
			}
			boolean biomes = current == Tab.BIOMES;
			int rows = biomes ? world.biomes().size() : world.groups().size();
			panelScroll = Math.clamp(panelScroll, 0, Math.max(0, rows * ROW - (listBottom - listTop)));
			gfx.scissor(x, listTop, width, listBottom);
			int y = listTop - panelScroll;
			if (biomes) {
				for (int i = 0; i < world.biomes().size(); i++, y += ROW) {
					Holder<Biome> biome = world.biomes().get(i);
					Rect row = new Rect(x + 4, y, PANEL - 8, ROW - 1);
					boolean hot = row.contains(mouseX, mouseY) && mouseY >= listTop && mouseY < listBottom;
					if (highlight == i || hot) {
						gfx.fill(row.x(), row.y(), row.x() + row.w(), row.y() + row.h(), highlight == i ? Theme.SELECTED : Theme.CARD_HOVER);
					}
					int color = SeedPalette.biomeColor(SeedWorld.path(biome));
					gfx.fill(row.x() + 3, y + 3, row.x() + 13, y + 13, 0xFF0B0E11);
					gfx.fill(row.x() + 4, y + 4, row.x() + 12, y + 12, color);
					gfx.text(font, font.plainSubstrByWidth(SeedMaps.biomeName(biome), row.w() - 22), row.x() + 18, y + 4, highlight == i ? Theme.ACCENT : Theme.TEXT);
					int index = i;
					if (y >= listTop && y + ROW <= listBottom) {
						hits.add(new Hit(row, () -> {
							if (highlight == index) {
								highlight = -1;
								targetLabel = null;
							} else {
								highlight = index;
								findBiome(world, index);
							}
						}));
					}
				}
			} else {
				for (String group : world.groups()) {
					SeedPalette.Group info = SeedPalette.info(group);
					boolean shown = groupShown(group);
					Rect row = new Rect(x + 4, y, PANEL - 8, ROW - 1);
					String find = "最近";
					int fw = font.width(find) + 8;
					Rect chip = new Rect(row.x() + row.w() - fw, y + 1, fw, ROW - 3);
					boolean visible = y >= listTop && y + ROW <= listBottom;
					boolean hot = visible && row.contains(mouseX, mouseY);
					if (hot) {
						gfx.fill(row.x(), row.y(), row.x() + row.w(), row.y() + row.h(), Theme.CARD_HOVER);
					}
					if (shown) {
						MapIcons.badge(gfx, row.x() + 8, y + 8, 7, info.color(), MapIcons.item(info.icon()));
					} else {
						MapIcons.disc(gfx, row.x() + 8, y + 8, 7.5, MapIcons.OUTLINE);
						MapIcons.disc(gfx, row.x() + 8, y + 8, 6.5, 0xFF3A424C);
						MapIcons.disc(gfx, row.x() + 8, y + 8, 5.4, 0xFF1B2026);
					}
					gfx.text(font, font.plainSubstrByWidth(info.name(), row.w() - fw - 24), row.x() + 19, y + 4, shown ? Theme.TEXT : Theme.TEXT_DIM);
					boolean chipHot = visible && chip.contains(mouseX, mouseY);
					gfx.fill(chip.x(), chip.y(), chip.x() + chip.w(), chip.y() + chip.h(), chipHot ? Theme.CHIP_HOVER : Theme.CHIP);
					gfx.centered(font, find, chip.x(), chip.y(), chip.w(), chip.h(), searching ? Theme.TEXT_DIM : Theme.TEXT);
					if (visible) {
						hits.add(new Hit(chip, () -> findStructure(group)));
						hits.add(new Hit(row, () -> toggleGroup(world, group)));
					}
					y += ROW;
				}
			}
			gfx.endScissor();
		}
		int sy = bottom - 42;
		gfx.fill(x + 1, sy - 2, width, sy - 1, 0xFF0B0E11);
		String status = searching ? "搜尋中⋯" : notice != null ? notice : targetLabel != null ? "已標示最近的" + targetLabel : current == Tab.BIOMES ? "點選生態域可高亮並找最近處" : "點選名稱切換顯示";
		gfx.text(font, font.plainSubstrByWidth(status, PANEL - 12), x + 6, sy + 2, notice != null ? Theme.WARNING : Theme.TEXT_SECONDARY);
		if (scale < 1.0 / 16 && current == Tab.STRUCTURES) {
			gfx.text(font, "放大地圖以顯示結構", x + 6, sy + 14, Theme.TEXT_DIM);
		} else {
			gfx.text(font, font.plainSubstrByWidth("種子 " + SeedMaps.seed(view), PANEL - 12), x + 6, sy + 14, Theme.TEXT_DIM);
		}
		gfx.text(font, font.plainSubstrByWidth(SeedMaps.SAME_AS_SERVER ? "依官網公開種子推算" : "遊戲版本與伺服器不同，可能有落差", PANEL - 12), x + 6, sy + 26, SeedMaps.SAME_AS_SERVER ? Theme.TEXT_DIM : Theme.WARNING);
	}

	private void queryPanel(Gfx gfx, int mouseX, int mouseY, int x, int top, int bottom, Tab current) {
		search.visible = true;
		search.setX(x + 6);
		search.setY(top + 24);
		search.setWidth(PANEL - 12);
		int listTop = top + 46;
		int listBottom = bottom - 16;
		MapMarkers.Data data = WorldMaps.markers(view);
		int[] origin = searchOrigin();
		int count;
		gfx.scissor(x, listTop, width, listBottom);
		int y = listTop - panelScroll;
		if (current == Tab.RESIDENCES) {
			List<MapMarkers.Area> list = new ArrayList<>();
			for (MapMarkers.Area a : data.areas()) {
				if (a.kind() == MapMarkers.AreaKind.RESIDENCE && matches(query, a.label(), a.owner())) {
					list.add(a);
				}
			}
			list.sort(java.util.Comparator.comparingDouble(a -> Math.hypot((a.minX() + a.maxX()) / 2 - origin[0], (a.minZ() + a.maxZ()) / 2 - origin[1])));
			count = list.size();
			panelScroll = Math.clamp(panelScroll, 0, Math.max(0, count * ROW2 - (listBottom - listTop)));
			for (MapMarkers.Area a : list) {
				if (y + ROW2 > listTop - ROW2 && y < listBottom) {
					Rect row = new Rect(x + 4, y, PANEL - 8, ROW2 - 2);
					boolean visible = y >= listTop && y + ROW2 <= listBottom;
					boolean hot = visible && row.contains(mouseX, mouseY);
					if (a == selectedArea || hot) {
						gfx.fill(row.x(), row.y(), row.x() + row.w(), row.y() + row.h(), a == selectedArea ? Theme.SELECTED : Theme.CARD_HOVER);
					}
					gfx.fill(row.x() + 3, y + 4, row.x() + 6, y + ROW2 - 6, a.line() | 0xFF000000);
					gfx.text(font, font.plainSubstrByWidth(a.label(), row.w() - 14), row.x() + 10, y + 3, a == selectedArea ? Theme.ACCENT : Theme.TEXT);
					String sub = (a.owner().isEmpty() ? "" : a.owner()) + (coordsHidden() ? "" : (a.owner().isEmpty() ? "" : " · ") + distanceText((a.minX() + a.maxX()) / 2, (a.minZ() + a.maxZ()) / 2));
					gfx.text(font, font.plainSubstrByWidth(sub, row.w() - 14), row.x() + 10, y + 13, Theme.TEXT_SECONDARY);
					if (visible) {
						hits.add(new Hit(row, () -> focusArea(a)));
					}
				}
				y += ROW2;
			}
		} else {
			List<MapMarkers.Poi> list = new ArrayList<>();
			for (MapMarkers.Poi poi : data.pois()) {
				if (matches(query, MapMarkers.homeName(poi), MapMarkers.homeOwner(poi))) {
					list.add(poi);
				}
			}
			list.sort(java.util.Comparator.comparingDouble(poi -> Math.hypot(poi.x() - origin[0], poi.z() - origin[1])));
			count = list.size();
			panelScroll = Math.clamp(panelScroll, 0, Math.max(0, count * ROW2 - (listBottom - listTop)));
			for (MapMarkers.Poi poi : list) {
				if (y + ROW2 > listTop - ROW2 && y < listBottom) {
					Rect row = new Rect(x + 4, y, PANEL - 8, ROW2 - 2);
					String command = MapMarkers.homeCommand(poi);
					String go = "傳送";
					int gw = font.width(go) + 8;
					Rect chip = new Rect(row.x() + row.w() - gw, y + 4, gw, 14);
					boolean visible = y >= listTop && y + ROW2 <= listBottom;
					boolean hot = visible && row.contains(mouseX, mouseY);
					boolean selected = targetLabel != null && targetX == (int) Math.floor(poi.x()) && targetZ == (int) Math.floor(poi.z());
					if (selected || hot) {
						gfx.fill(row.x(), row.y(), row.x() + row.w(), row.y() + row.h(), selected ? Theme.SELECTED : Theme.CARD_HOVER);
					}
					MapIcons.badge(gfx, row.x() + 9, y + 11, 7, Minimap.POI, MapIcons.item("ender_pearl"));
					int textW = row.w() - 22 - (command != null ? gw + 4 : 0);
					gfx.text(font, font.plainSubstrByWidth(MapMarkers.homeName(poi), textW), row.x() + 20, y + 3, selected ? Theme.ACCENT : Theme.TEXT);
					String sub = MapMarkers.homeOwner(poi) + (coordsHidden() ? "" : " · " + distanceText(poi.x(), poi.z()));
					gfx.text(font, font.plainSubstrByWidth(sub, textW), row.x() + 20, y + 13, Theme.TEXT_SECONDARY);
					if (command != null) {
						boolean chipHot = visible && chip.contains(mouseX, mouseY);
						gfx.fill(chip.x(), chip.y(), chip.x() + chip.w(), chip.y() + chip.h(), chipHot ? Theme.ACCENT_DIM : Theme.CHIP);
						gfx.centered(font, go, chip.x(), chip.y(), chip.w(), chip.h(), chipHot ? Theme.ACCENT : Theme.TEXT);
						if (visible) {
							hits.add(new Hit(chip, () -> teleport(command)));
						}
					}
					if (visible) {
						hits.add(new Hit(row, () -> {
							selectedArea = null;
							showTarget((int) Math.floor(poi.x()), (int) Math.floor(poi.z()), MapMarkers.homeName(poi));
							scale = Math.max(scale, 1);
						}));
					}
				}
				y += ROW2;
			}
		}
		gfx.endScissor();
		if (count == 0) {
			gfx.centered(font, view.markers() == null ? "這裡沒有網頁地圖資料" : query.isBlank() ? "沒有資料" : "找不到符合的結果", x, listTop + 8, PANEL, 10, Theme.TEXT_DIM);
		}
		gfx.fill(x + 1, bottom - 15, width, bottom - 14, 0xFF0B0E11);
		gfx.text(font, "共 " + count + " 筆 · 依距離排序", x + 6, bottom - 11, Theme.TEXT_DIM);
	}

	private void teleport(String command) {
		if (minecraft.player != null) {
			minecraft.player.connection.sendCommand(command);
			CommandMenuScreen.setScreen(null);
		}
	}

	private void tab(Gfx gfx, int x, int y, int w, String label, boolean selected, int mouseX, int mouseY, Runnable action) {
		Rect r = new Rect(x, y, w, 14);
		boolean hot = r.contains(mouseX, mouseY);
		gfx.fill(r.x(), r.y(), r.x() + r.w(), r.y() + r.h(), selected ? Theme.ACCENT_DIM : hot ? Theme.CHIP_HOVER : Theme.CHIP);
		gfx.centered(font, label, r.x(), r.y(), r.w(), r.h(), selected ? Theme.ACCENT : Theme.TEXT);
		hits.add(new Hit(r, action));
	}

	private void drawTerrain(Gfx gfx, Rect area) {
		double left = toWorldX(area.x());
		double right = toWorldX(area.x() + area.w());
		double top = toWorldZ(area.y());
		double bottom = toWorldZ(area.y() + area.h());
		{
			int span = MapStore.SIZE;
			for (int rz = MapMath.tileOf((int) Math.floor(top), span); rz <= MapMath.tileOf((int) Math.floor(bottom), span); rz++) {
				for (int rx = MapMath.tileOf((int) Math.floor(left), span); rx <= MapMath.tileOf((int) Math.floor(right), span); rx++) {
					MapStore.Region region = MapStore.region(view.local(), view.layer(), rx, rz, false);
					if (region != null) {
						blitTile(gfx, MapStore.texture(region), (double) rx * span, (double) rz * span, span);
					}
				}
			}
		}
	}

	private void blitTile(Gfx gfx, Identifier texture, double worldX, double worldZ, int span) {
		int x0 = (int) Math.round(toScreenX(worldX));
		int y0 = (int) Math.round(toScreenY(worldZ));
		int x1 = (int) Math.round(toScreenX(worldX + span));
		int y1 = (int) Math.round(toScreenY(worldZ + span));
		if (x1 > x0 && y1 > y0) {
			gfx.image(texture, x0, y0, x1 - x0, y1 - y0);
		}
	}

	private void drawWaypoints(Gfx gfx, int mouseX, int mouseY, Rect area) {
		boolean inside = area.contains(mouseX, mouseY);
		for (Waypoints.Point point : Waypoints.list(view.waypoints())) {
			if (!point.visible) {
				continue;
			}
			int sx = (int) Math.round(toScreenX(point.x + 0.5));
			int sy = (int) Math.round(toScreenY(point.z + 0.5));
			if (sx < -40 || sy < area.y() - 20 || sx > width + 40 || sy > area.y() + area.h() + 20) {
				continue;
			}
			int color = Waypoints.color(point);
			boolean hot = inside && Math.abs(mouseX - sx) <= 6 && Math.abs(mouseY - sy) <= 6;
			if (hot) {
				hovered = point;
			}
			gfx.push(sx, sy, 1);
			gfx.rotate((float) (Math.PI / 4));
			int r = hot ? 6 : 5;
			gfx.fill(-r, -r, r, r, 0xFF101317);
			gfx.fill(-r + 1, -r + 1, r - 1, r - 1, color);
			gfx.pop();
			String name = font.plainSubstrByWidth(point.name, 120);
			int w = font.width(name);
			gfx.fill(sx - w / 2 - 3, sy + 9, sx + w / 2 + 3, sy + 20, 0xB0101418);
			gfx.text(font, name, sx - w / 2, sy + 11, hot ? Theme.ACCENT : Theme.TEXT);
		}
	}

	private void drawPlayers(Gfx gfx, float partial, int mouseX, int mouseY) {
		LocalPlayer player = minecraft.player;
		if (player == null || !viewingCurrent()) {
			return;
		}
		List<Rect> heads = new ArrayList<>();
		List<String> names = new ArrayList<>();
		for (Minimap.Marker marker : Minimap.markers) {
			if (marker.kind() != Minimap.Kind.PLAYER) {
				continue;
			}
			Vec3 at = marker.entity().getPosition(partial);
			int hx = (int) Math.round(toScreenX(at.x)) - 5;
			int hy = (int) Math.round(toScreenY(at.z)) - 5;
			RemoteImages.Entry head = RemoteImages.get(RemoteImages.HEAD_PREFIX + URLEncoder.encode(marker.name(), StandardCharsets.UTF_8), RemoteImages.requestWidth(10));
			gfx.fill(hx - 1, hy - 1, hx + 11, hy + 11, 0xFFFFFFFF);
			if (head.state == RemoteImages.State.READY) {
				RemoteImages.draw(gfx, head, hx, hy, 10, 10);
			} else {
				gfx.fill(hx, hy, hx + 10, hy + 10, 0xFF5C6773);
			}
			Rect box = new Rect(hx - 1, hy - 1, 12, 12);
			heads.add(box);
			names.add(marker.name());
			if (mapArea().contains(mouseX, mouseY) && (box.contains(mouseX, mouseY) || Math.hypot(mouseX - (hx + 5), mouseY - (hy + 5)) <= 8)) {
				hoveredPlayers.add(marker.name());
			}
		}
		List<Rect> placed = new ArrayList<>(heads);
		for (int i = 0; i < heads.size(); i++) {
			Rect box = heads.get(i);
			String name = names.get(i);
			int w = font.width(name) + 4;
			int cx = box.x() + box.w() / 2;
			Rect[] options = {
				new Rect(cx - w / 2, box.y() + box.h() + 1, w, 10),
				new Rect(cx - w / 2, box.y() - 11, w, 10),
				new Rect(box.x() + box.w() + 1, box.y() + 1, w, 10),
				new Rect(box.x() - w - 1, box.y() + 1, w, 10)
			};
			for (Rect option : options) {
				boolean free = true;
				for (int j = 0; j < placed.size() && free; j++) {
					Rect other = placed.get(j);
					if (other != box && option.x() < other.x() + other.w() && other.x() < option.x() + option.w() && option.y() < other.y() + other.h() && other.y() < option.y() + option.h()) {
						free = false;
					}
				}
				if (free) {
					placed.add(option);
					gfx.fill(option.x(), option.y(), option.x() + option.w(), option.y() + option.h(), 0xB0101418);
					gfx.text(font, name, option.x() + 2, option.y() + 1, Theme.TEXT);
					break;
				}
			}
		}
		Vec3 pos = player.getPosition(partial);
		float yaw = player.getViewYRot(partial);
		double angle = Math.atan2(-Math.sin(Math.toRadians(yaw)), -Math.cos(Math.toRadians(yaw)));
		gfx.push((float) toScreenX(pos.x), (float) toScreenY(pos.z), 1);
		gfx.rotate((float) angle);
		gfx.texture(arrowTexture(), -8, -8, 16, 16, 32, 32, 0xFFFFFFFF);
		gfx.pop();
	}

	private static Identifier arrowTexture() {
		if (arrow == null) {
			DynamicTexture texture = new DynamicTexture(() -> "brilliant-server-mod map arrow", 32, 32, false);
			NativeImage image = texture.getPixels();
			double[][] outline = MinimapRaster.arrowShape(16, 16, 14.5, 0);
			double[][] body = MinimapRaster.arrowShape(16, 16, 11.5, 0);
			for (int y = 0; y < 32; y++) {
				for (int x = 0; x < 32; x++) {
					double px = x + 0.5;
					double py = y + 0.5;
					int color = MinimapRaster.inArrow(body, px, py) ? 0xFFFFFFFF : MinimapRaster.inArrow(outline, px, py) ? 0xFF101317 : 0;
					image.setPixel(x, y, color);
				}
			}
			texture.upload();
			arrow = Identifier.fromNamespaceAndPath("brilliant-server-mod", "map/arrow");
			Minecraft.getInstance().getTextureManager().register(arrow, texture);
		}
		return arrow;
	}

	private int chip(Gfx gfx, int right, int y, String label, boolean selected, int mouseX, int mouseY, Runnable action) {
		int w = font.width(label) + 12;
		Rect r = new Rect(right - w, y, w, 14);
		boolean hot = r.contains(mouseX, mouseY);
		gfx.fill(r.x(), r.y(), r.x() + r.w(), r.y() + r.h(), selected ? Theme.ACCENT_DIM : hot ? Theme.CHIP_HOVER : Theme.CHIP);
		gfx.centered(font, label, r.x(), r.y(), r.w(), r.h(), selected ? Theme.ACCENT : Theme.TEXT);
		if (action != null) {
			hits.add(new Hit(r, action));
		}
		return r.x() - 4;
	}

	private void header(Gfx gfx, int mouseX, int mouseY) {
		gfx.fill(0, 0, width, HEADER, Theme.HEADER);
		gfx.fill(0, HEADER - 1, width, HEADER, Theme.ACCENT);
		Theme.drawBack(gfx, font, 0, 2, mouseX, mouseY);
		hits.add(new Hit(new Rect(6, 8, Theme.backWidth(font), 14), this::onClose));
		String title = view == null ? "地圖" : "地圖・" + WorldMaps.shardLabel(view.shard()) + "・" + WorldMaps.dimensionLabel(view.dimension()) + "・" + WorldMaps.layerLabel(view);
		gfx.text(font, title, Theme.titleX(font, 0), 9, Theme.TEXT);
		if (view == null) {
			return;
		}
		int right = width - 6;
		right = chip(gfx, right, 5, "記錄點", false, mouseX, mouseY, () -> CommandMenuScreen.setScreen(new WaypointScreen(this, view)));
		right = chip(gfx, right, 5, "回到我的位置", false, mouseX, mouseY, this::recenter);
		if (view.markers() != null) {
			boolean querying = panelOpen() && (currentTab() == Tab.RESIDENCES || currentTab() == Tab.HOMES);
			right = chip(gfx, right, 5, "查詢", querying, mouseX, mouseY, () -> {
				if (querying) {
					queryOpen = false;
					tab = Tab.STRUCTURES;
				} else {
					queryOpen = true;
					tab = Tab.RESIDENCES;
				}
				panelScroll = 0;
			});
		}
		if (SeedMaps.seed(view) != null) {
			right = chip(gfx, right, 5, "種子地圖", seedMode, mouseX, mouseY, () -> {
				seedMode = !seedMode;
				if (seedMode) {
					tab = Tab.STRUCTURES;
				}
				scale = Math.clamp(scale, minScale(), MAX_SCALE);
				targetLabel = null;
				highlight = -1;
			});
		}
		right -= 6;
		WorldMaps.View surface = WorldMaps.view(view.shard(), view.dimension(), WorldMaps.SURFACE);
		List<String> caves = MapStore.layers(surface.local()).stream().filter(layer -> layer.startsWith("cave_")).sorted().toList();
		if (!caves.isEmpty()) {
			String label = view.cave() ? WorldMaps.layerLabel(view) : "地表";
			right = chip(gfx, right, 5, label + " ▾", view.cave(), mouseX, mouseY, () -> {
				List<String> all = new ArrayList<>();
				all.add(WorldMaps.SURFACE);
				all.addAll(caves);
				int index = all.indexOf(view.layer());
				view = WorldMaps.view(view.shard(), view.dimension(), all.get((index + 1) % all.size()));
				scale = Math.max(scale, minScale());
			});
			right -= 6;
		}
		for (int i = DIMENSIONS.size() - 1; i >= 0; i--) {
			String dimension = DIMENSIONS.get(i);
			WorldMaps.View candidate = WorldMaps.view(view.shard(), dimension, WorldMaps.SURFACE);
			boolean available = !MapStore.layers(candidate.local()).isEmpty();
			if (!available && !dimension.equals(view.dimension())) {
				continue;
			}
			right = chip(gfx, right, 5, WorldMaps.dimensionLabel(dimension), dimension.equals(view.dimension()), mouseX, mouseY, () -> switchTo(view.shard(), dimension));
		}
		if (SHARDS.contains(view.shard())) {
			right -= 6;
			for (int i = SHARDS.size() - 1; i >= 0; i--) {
				String shard = SHARDS.get(i);
				if (MapStore.layers(WorldMaps.view(shard, view.dimension(), WorldMaps.SURFACE).local()).isEmpty() && !shard.equals(view.shard())) {
					continue;
				}
				right = chip(gfx, right, 5, WorldMaps.shardLabel(shard), shard.equals(view.shard()), mouseX, mouseY, () -> switchTo(shard, view.dimension()));
			}
		}
	}

	private void switchTo(String shard, String dimension) {
		WorldMaps.View current = WorldMaps.current();
		highlight = -1;
		targetLabel = null;
		if (current != null && current.shard().equals(shard) && current.dimension().equals(dimension)) {
			view = current;
			recenter();
			return;
		}
		view = WorldMaps.view(shard, dimension, WorldMaps.SURFACE);
		scale = Math.max(scale, minScale());
		highlight = -1;
		targetLabel = null;
		notice = null;
		selectedArea = null;
	}

	private void footer(Gfx gfx, int mouseX, int mouseY, Rect area) {
		int y = height - FOOTER;
		gfx.fill(0, y, width, height, Theme.HEADER);
		String left;
		if (!area.contains(mouseX, mouseY)) {
			left = "";
		} else if (coordsHidden()) {
			left = "伺服器隱藏了座標資訊";
		} else {
			int wx = (int) Math.floor(toWorldX(mouseX));
			int wz = (int) Math.floor(toWorldZ(mouseY));
			int h = WorldMaps.height(view, wx, wz);
			left = "X " + wx + (h == Integer.MIN_VALUE ? "" : "  Y " + h) + "  Z " + wz;
		}
		if (area.contains(mouseX, mouseY) && seedActive()) {
			SeedWorld world = SeedMaps.world(view);
			int id = SeedMaps.biomeAt(view, (int) Math.floor(toWorldX(mouseX)), (int) Math.floor(toWorldZ(mouseY)));
			if (world != null && id >= 0 && id < world.biomes().size()) {
				left += (left.isEmpty() ? "" : "　") + SeedMaps.biomeName(world.biomes().get(id));
			}
		}
		if (area.contains(mouseX, mouseY) && ModSettings.get().mapRegen) {
			Integer regen = WorldMaps.regen(view).minutesLeft(Math.floorDiv((int) Math.floor(toWorldX(mouseX)), 16), Math.floorDiv((int) Math.floor(toWorldZ(mouseY)), 16));
			if (regen != null) {
				left += (left.isEmpty() ? "" : "　") + "資源再生：" + MapMarkers.regenText(regen);
			}
		}
		gfx.text(font, left, 6, y + 4, Theme.TEXT_SECONDARY);
		boolean armed = clearArmedAt > 0 && System.currentTimeMillis() - clearArmedAt < 3000;
		String clearLabel = armed ? "確定清除？" : "清除此地圖記憶";
		int cw = font.width(clearLabel) + 10;
		Rect clear = new Rect(width - cw - 4, y + 2, cw, FOOTER - 4);
		boolean clearHot = clear.contains(mouseX, mouseY);
		gfx.fill(clear.x(), clear.y(), clear.x() + clear.w(), clear.y() + clear.h(), armed ? 0xFF6B2A2A : clearHot ? Theme.CHIP_HOVER : Theme.CHIP);
		gfx.centered(font, clearLabel, clear.x(), clear.y(), clear.w(), clear.h(), armed ? Theme.DANGER : Theme.TEXT_SECONDARY);
		hits.add(new Hit(clear, () -> {
			if (clearArmedAt > 0 && System.currentTimeMillis() - clearArmedAt < 3000) {
				clearArmedAt = 0;
				MapStore.clear(view.local());
			} else {
				clearArmedAt = System.currentTimeMillis();
			}
		}));
		String hint = "拖曳移動 · 滾輪縮放 · 右鍵新增記錄點 · 空白鍵回到自己" + (seedActive() ? " · 種子地圖依原版生成規則推算" : view.markers() != null ? " · 領地與傳送點來自伺服器網頁地圖" : "");
		hint = font.plainSubstrByWidth(hint, Math.max(0, clear.x() - font.width(left) - 24));
		gfx.text(font, hint, clear.x() - font.width(hint) - 8, y + 4, Theme.TEXT_DIM);
	}

	private void recenter() {
		LocalPlayer player = minecraft.player;
		WorldMaps.View current = WorldMaps.current();
		if (player != null && current != null) {
			view = current;
			centerX = player.getX();
			centerZ = player.getZ();
			scale = Math.max(scale, minScale());
		}
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (super.mouseClicked(event, doubleClick)) {
			return true;
		}
		setFocused(null);
		if (search != null) {
			search.setFocused(false);
		}
		for (Hit hit : List.copyOf(hits)) {
			if (hit.rect().contains(event.x(), event.y())) {
				hit.action().run();
				return true;
			}
		}
		if (view == null || !mapArea().contains(event.x(), event.y())) {
			return false;
		}
		if (event.button() == InputConstants.MOUSE_BUTTON_RIGHT) {
			int wx = (int) Math.floor(toWorldX(event.x()));
			int wz = (int) Math.floor(toWorldZ(event.y()));
			int h = WorldMaps.height(view, wx, wz);
			LocalPlayer player = minecraft.player;
			int wy = h != Integer.MIN_VALUE ? h + 1 : player != null ? player.blockPosition().getY() : 64;
			WaypointScreen.openAddAt(this, view, wx, wy, wz);
			return true;
		}
		if (event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
			dragging = true;
			dragged = 0;
			return true;
		}
		return false;
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
		if (dragging) {
			centerX -= dragX / scale;
			centerZ -= dragY / scale;
			dragged += Math.abs(dragX) + Math.abs(dragY);
			return true;
		}
		return super.mouseDragged(event, dragX, dragY);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		if (dragging && dragged < 3 && hovered != null) {
			WaypointScreen.edit(this, view, hovered);
		} else if (dragging && dragged < 3 && hoveredPoi != null) {
			String command = MapMarkers.homeCommand(hoveredPoi);
			if (command != null && minecraft.player != null) {
				dragging = false;
				teleport(command);
				return true;
			}
		}
		dragging = false;
		return super.mouseReleased(event);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		if (view == null || scrollY == 0) {
			return false;
		}
		if (panelOpen() && mouseX >= width - PANEL) {
			panelScroll -= (int) Math.round(scrollY * ROW * 2);
			return true;
		}
		double wx = toWorldX(mouseX);
		double wz = toWorldZ(mouseY);
		scale = Math.clamp(scale * (scrollY > 0 ? 1.25 : 0.8), minScale(), MAX_SCALE);
		rememberedScale = scale;
		Rect area = mapArea();
		centerX = wx - (mouseX - area.x() - area.w() / 2.0) / scale;
		centerZ = wz - (mouseY - area.y() - area.h() / 2.0) / scale;
		return true;
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (search != null && search.visible && search.isFocused() && event.key() != InputConstants.KEY_ESCAPE) {
			return super.keyPressed(event);
		}
		if (event.key() == InputConstants.KEY_SPACE) {
			recenter();
			return true;
		}
		if (Minimap.isMapKey(event)) {
			onClose();
			return true;
		}
		return super.keyPressed(event);
	}

	@Override
	public void removed() {
		SeedMaps.want(List.of());
		super.removed();
	}

	@Override
	public void onClose() {
		rememberedScale = scale;
		CommandMenuScreen.setScreen(parent);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
