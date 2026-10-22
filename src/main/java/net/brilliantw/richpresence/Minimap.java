package net.brilliantw.richpresence;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.NativeImage;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
//? if >=26.1 {
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
//?} else {
/*import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
*///?}
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

public final class Minimap {
	public static final List<Integer> SIZES = List.of(64, 80, 96, 112, 128, 144, 160, 192, 224, 256);
	public static final double[] ZOOMS = { 0.25, 0.5, 1, 2, 4, 8 };
	public static final double[] ICON_SCALES = { 0.75, 1, 1.35 };
	public static final double[] TEXT_SCALES = { 0.75, 1, 1.25 };
	public static final String[] STEP_LABELS = { "小", "中", "大" };
	public static final int MARGIN = 6;
	private static final int HOSTILE = 0xFFFF5A5A;
	private static final int PASSIVE = 0xFF7BE07B;
	private static final int PLAYER_DOT = 0xFFFFFFFF;
	static final int POI = 0xFFFFC94D;
	static final int REGEN_PENDING = 0x38FFB020;
	static final int REGEN_READY = 0x48FF4D4D;
	private static final int OVERLAY_CELLS = 1 << 16;
	private static final int MAX_MARKERS = 64;

	enum Kind { PLAYER, HOSTILE, PASSIVE }

	record Marker(Kind kind, Entity entity, String name) {}

	private static KeyMapping openMap;
	private static KeyMapping toggle;
	private static KeyMapping addPoint;
	private static KeyMapping zoom;
	private static DynamicTexture texture;
	private static Identifier textureId;
	private static int resolution;
	private static long builtAt;
	private static MinimapRaster.Frame builtFrame;
	private static final int COLLECT_EVERY = 4;
	private static int collectTicks;
	private static String zoomNote = "";
	private static long zoomNoteUntil;
	static List<Marker> markers = List.of();

	private Minimap() {}

	private static KeyMapping key(String name, int code, KeyMapping.Category category) {
		KeyMapping key = new KeyMapping("key.brilliant-server-mod." + name, code, category);
		//? if >=26.1 {
		return KeyMappingHelper.registerKeyMapping(key);
		//?} else {
		/*return KeyBindingHelper.registerKeyBinding(key);
		*///?}
	}

	public static void register() {
		KeyMapping.Category category = KeyMapping.Category.register(Identifier.fromNamespaceAndPath("brilliant-server-mod", "map"));
		openMap = key("map", InputConstants.KEY_M, category);
		toggle = key("minimap", InputConstants.KEY_N, category);
		addPoint = key("waypoint_add", InputConstants.KEY_B, category);
		zoom = key("minimap_zoom", InputConstants.UNKNOWN.getValue(), category);
		ClientTickEvents.END_CLIENT_TICK.register(Minimap::tick);
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> MapStore.flush());
		ClientLifecycleEvents.CLIENT_STOPPING.register(client -> MapStore.flushAndWait());
	}

	private static void tick(Minecraft client) {
		WorldMaps.tick(client);
		boolean active = WorldMaps.current() != null;
		ModSettings.Data s = ModSettings.get();
		while (openMap.consumeClick()) {
			if (active && ScriptEngine.screen() == null && KeyCombos.matches(openMap)) {
				CommandMenuScreen.setScreen(new WorldMapScreen(null));
			}
		}
		while (toggle.consumeClick()) {
			if (active && KeyCombos.matches(toggle)) {
				s.minimap = !s.minimap;
				ModSettings.save();
				note(s.minimap ? "小地圖：開啟" : "小地圖：關閉");
			}
		}
		while (addPoint.consumeClick()) {
			if (active && ScriptEngine.screen() == null && KeyCombos.matches(addPoint)) {
				WaypointScreen.openAdd(null);
			}
		}
		while (zoom.consumeClick()) {
			if (active && KeyCombos.matches(zoom)) {
				cycleZoom(1);
			}
		}
		if (!active || !s.minimap) {
			markers = List.of();
		} else if (++collectTicks >= COLLECT_EVERY) {
			collectTicks = 0;
			markers = collect(client);
		}
	}

	static void cycleZoom(int step) {
		ModSettings.Data s = ModSettings.get();
		s.minimapZoom = Math.floorMod(s.minimapZoom + step, ZOOMS.length);
		ModSettings.save();
		note("縮放 1:" + formatZoom(ZOOMS[s.minimapZoom]));
	}

	static String formatZoom(double zoom) {
		return zoom == Math.rint(zoom) ? Integer.toString((int) zoom) : Double.toString(zoom);
	}

	private static void note(String text) {
		zoomNote = text;
		zoomNoteUntil = System.currentTimeMillis() + 1500;
	}

	private static List<Marker> collect(Minecraft client) {
		LocalPlayer player = client.player;
		if (player == null || client.level == null) {
			return List.of();
		}
		ModSettings.Data s = ModSettings.get();
		if (!s.minimapMobs && !s.minimapPlayers) {
			return List.of();
		}
		boolean all = player.isCreative() || player.isSpectator();
		double range = Math.min(size() * ZOOMS[s.minimapZoom] * 0.75, all ? 256 : ScriptExtras.ENTITY_SIGHT);
		List<Marker> found = new ArrayList<>();
		for (Entity entity : client.level.entitiesForRendering()) {
			if (entity == player || !entity.isAlive() || entity.isInvisible()) {
				continue;
			}
			double dx = entity.getX() - player.getX();
			double dz = entity.getZ() - player.getZ();
			if (dx * dx + dz * dz > range * range) {
				continue;
			}
			Kind kind;
			if (entity instanceof Player) {
				if (!s.minimapPlayers) {
					continue;
				}
				kind = Kind.PLAYER;
			} else if (entity instanceof LivingEntity && !(entity instanceof ArmorStand)) {
				if (!s.minimapMobs) {
					continue;
				}
				kind = entity instanceof Enemy ? Kind.HOSTILE : Kind.PASSIVE;
			} else {
				continue;
			}
			if (!all && !ScriptExtras.sees(player, entity)) {
				continue;
			}
			found.add(new Marker(kind, entity, kind == Kind.PLAYER ? entity.getName().getString() : ""));
		}
		found.sort(Comparator.comparingDouble(marker -> marker.entity().distanceToSqr(player)));
		return found.size() > MAX_MARKERS ? List.copyOf(found.subList(0, MAX_MARKERS)) : found;
	}

	static boolean isMapKey(net.minecraft.client.input.KeyEvent event) {
		return openMap != null && openMap.matches(event);
	}

	public static int size() {
		return ModSettings.get().minimapPixels;
	}

	public static int nextSize(int current, int step) {
		int index = 0;
		for (int i = 0; i < SIZES.size(); i++) {
			if (Math.abs(SIZES.get(i) - current) < Math.abs(SIZES.get(index) - current)) {
				index = i;
			}
		}
		return SIZES.get(Math.clamp(index + step, 0, SIZES.size() - 1));
	}

	private static int lineHeight() {
		return (int) Math.ceil(10 * TEXT_SCALES[ModSettings.get().minimapText]);
	}

	public static int infoHeight() {
		ModSettings.Data s = ModSettings.get();
		int lines = (s.minimapCoords ? 1 : 0) + (s.minimapChunk ? 1 : 0) + (s.minimapBiome ? 1 : 0) + (s.minimapTime ? 1 : 0)
				+ (s.minimapFacing ? 1 : 0) + (s.minimapInfo ? 1 : 0);
		return lines == 0 ? 0 : lines * lineHeight() + 4;
	}

	public static int[] position(int screenWidth, int screenHeight) {
		ModSettings.Data s = ModSettings.get();
		int size = size();
		int freeX = Math.max(0, screenWidth - size - MARGIN * 2);
		int freeY = Math.max(0, screenHeight - size - infoHeight() - MARGIN * 2);
		return new int[] { MARGIN + Math.round(Math.clamp(s.minimapX, 0f, 1f) * freeX), MARGIN + Math.round(Math.clamp(s.minimapY, 0f, 1f) * freeY) };
	}

	public static void renderHud(Gfx gfx) {
		Minecraft client = Minecraft.getInstance();
		if (WorldMaps.current() == null || !ModSettings.get().minimap || client.player == null) {
			return;
		}
		//? if <26.2 {
		/*if (client.options.hideGui) {
			return;
		}
		*///?}
		net.minecraft.client.gui.screens.Screen screen = ScriptEngine.screen();
		if (screen instanceof WorldMapScreen || screen instanceof MinimapEditScreen || client.debugEntries.isOverlayVisible()) {
			return;
		}
		int[] at = position(client.getWindow().getGuiScaledWidth(), client.getWindow().getGuiScaledHeight());
		draw(gfx, client.font, at[0], at[1], client.getDeltaTracker().getGameTimeDeltaPartialTick(true));
	}

	private static void ensureTexture(int res) {
		if (texture != null && resolution == res) {
			return;
		}
		if (textureId != null) {
			Minecraft.getInstance().getTextureManager().release(textureId);
		}
		resolution = res;
		texture = new DynamicTexture(() -> "brilliant-server-mod minimap", res, res, false);
		textureId = Identifier.fromNamespaceAndPath("brilliant-server-mod", "map/minimap_" + res);
		Minecraft.getInstance().getTextureManager().register(textureId, texture);
		builtAt = 0;
	}

	private static int[] buffer = new int[0];

	private static MinimapRaster.Frame frame(LocalPlayer player, float partial, int size, int res) {
		ModSettings.Data s = ModSettings.get();
		Vec3 pos = player.getPosition(partial);
		double bpp = ZOOMS[Math.clamp(s.minimapZoom, 0, ZOOMS.length - 1)] * size / res;
		return new MinimapRaster.Frame(res, pos.x, pos.z, bpp, player.getViewYRot(partial), s.minimapRotate, s.minimapCircle, (double) res / size, s.minimapFrame);
	}

	public static void draw(Gfx gfx, Font font, int x, int y, float partial) {
		Minecraft client = Minecraft.getInstance();
		LocalPlayer player = client.player;
		WorldMaps.View view = WorldMaps.current();
		if (player == null || view == null) {
			return;
		}
		int size = size();
		int res = Math.clamp((int) Math.round(size * client.getWindow().getGuiScale()), 64, 512);
		ensureTexture(res);
		MinimapRaster.Frame frame = frame(player, partial, size, res);
		long now = System.currentTimeMillis();
		if (now - builtAt >= 33 && (!frame.equals(builtFrame) || now - builtAt >= 250)) {
			builtAt = now;
			builtFrame = frame;
			build(view, frame, partial);
		}
		ModSettings.Data s = ModSettings.get();
		int alpha = (int) Math.round(255 * s.minimapOpacity / 100.0);
		int shade = s.minimapDayNight && client.level != null ? (int) Math.round(255 * MapInfo.daylight(client.level)) : 255;
		gfx.texture(textureId, x, y, size, size, res, res, alpha << 24 | shade << 16 | shade << 8 | shade);
		double toGui = (double) size / res;
		double iconScale = ICON_SCALES[s.minimapIcons];
		if (s.minimapMobIcons) {
			int box = (int) Math.round((size >= 144 ? 9 : 8) * iconScale);
			for (int i = markers.size() - 1; i >= 0; i--) {
				Marker marker = markers.get(i);
				if (marker.kind() == Kind.PLAYER || marker.entity().isRemoved()) {
					continue;
				}
				Vec3 at = marker.entity().getPosition(partial);
				double[] t = frame.toTexel(at.x, at.z);
				if (!frame.inside(t[0] - frame.r(), t[1] - frame.r(), frame.inner() - (box / 2.0 + 2) / toGui)) {
					continue;
				}
				int cx = x + (int) Math.round(t[0] * toGui);
				int cy = y + (int) Math.round(t[1] * toGui);
				int ring = marker.kind() == Kind.HOSTILE ? HOSTILE : PASSIVE;
				MobHeads.Head head = MobHeads.of(marker.entity());
				if (head != null) {
					int left = cx - box / 2;
					int top = cy - box / 2;
					gfx.fill(left - 2, top - 2, left + box + 2, top + box + 2, MapIcons.OUTLINE);
					gfx.fill(left - 1, top - 1, left + box + 1, top + box + 1, ring);
					gfx.region(MobHeads.ATLAS, left, top, box, box, head.u(), head.v(), 8, 8, MobHeads.width, MobHeads.height);
					continue;
				}
				MobFaces.Face face = MobFaces.of(marker.entity());
				if (face.texture() == null) {
					ItemStack egg = MapIcons.egg(marker.entity().getType());
					MapIcons.badge(gfx, cx, cy, box / 2 + 1, ring, egg);
					continue;
				}
				double fit = (double) box / Math.max(face.width(), face.height());
				int w = Math.max(2, (int) Math.round(face.width() * fit));
				int h = Math.max(2, (int) Math.round(face.height() * fit));
				int left = cx - w / 2;
				int top = cy - h / 2;
				gfx.fill(left - 2, top - 2, left + w + 2, top + h + 2, MapIcons.OUTLINE);
				gfx.fill(left - 1, top - 1, left + w + 1, top + h + 1, ring);
				gfx.image(face.texture(), left, top, w, h);
			}
		}
		for (Marker marker : markers) {
			if (marker.kind() != Kind.PLAYER || !s.minimapPlayerHeads || marker.entity().isRemoved()) {
				continue;
			}
			Vec3 at = marker.entity().getPosition(partial);
			double[] t = frame.toTexel(at.x, at.z);
			if (!frame.inside(t[0] - frame.r(), t[1] - frame.r(), frame.inner() - 6 / toGui)) {
				continue;
			}
			int hs = (int) Math.round(8 * iconScale);
			int hx = x + (int) Math.round(t[0] * toGui) - hs / 2;
			int hy = y + (int) Math.round(t[1] * toGui) - hs / 2;
			RemoteImages.Entry head = RemoteImages.get(RemoteImages.HEAD_PREFIX + URLEncoder.encode(marker.name(), StandardCharsets.UTF_8), RemoteImages.requestWidth(hs));
			gfx.fill(hx - 1, hy - 1, hx + hs + 1, hy + hs + 1, 0xFFFFFFFF);
			if (head.state == RemoteImages.State.READY) {
				RemoteImages.draw(gfx, head, hx, hy, hs, hs);
			} else {
				gfx.fill(hx, hy, hx + hs, hy + hs, 0xFF5C6773);
			}
		}
		if (s.minimapCompass) {
			compass(gfx, font, x, y, size, frame);
		}
		info(gfx, font, x, y + size + 2, size, player, view);
		if (now < zoomNoteUntil) {
			label(gfx, font, zoomNote, x, y + size - 14, size, Theme.ACCENT);
		}
	}

	private static void label(Gfx gfx, Font font, String text, int x, int y, int width, int color) {
		int w = font.width(text) + 6;
		int left = x + (width - w) / 2;
		gfx.fill(left, y, left + w, y + 11, 0xA0101418);
		gfx.text(font, text, left + 3, y + 2, color);
	}

	private static void info(Gfx gfx, Font font, int x, int y, int size, LocalPlayer player, WorldMaps.View view) {
		ModSettings.Data s = ModSettings.get();
		boolean hidden = ScriptExtras.coordsHidden(player);
		int px = player.blockPosition().getX();
		int pz = player.blockPosition().getZ();
		List<String> lines = new ArrayList<>();
		List<Integer> colors = new ArrayList<>();
		if (s.minimapCoords && !hidden) {
			lines.add(px + "  " + player.blockPosition().getY() + "  " + pz);
			colors.add(Theme.TEXT);
		}
		if (s.minimapChunk && !hidden) {
			boolean slime = MapInfo.slime(view, px, pz);
			lines.add(MapInfo.chunk(px, pz) + (slime ? "・史萊姆" : ""));
			colors.add(slime ? 0xFF8BE08B : Theme.TEXT_SECONDARY);
		}
		if (s.minimapBiome && player.level() instanceof net.minecraft.client.multiplayer.ClientLevel level) {
			lines.add(MapInfo.biome(level, player.blockPosition()));
			colors.add(Theme.TEXT_SECONDARY);
		}
		if (s.minimapTime && player.level() instanceof net.minecraft.client.multiplayer.ClientLevel level) {
			lines.add(MapInfo.time(level));
			colors.add(Theme.TEXT_SECONDARY);
		}
		if (s.minimapFacing) {
			lines.add(MapInfo.facing(player.getYRot()));
			colors.add(Theme.TEXT_SECONDARY);
		}
		if (s.minimapInfo) {
			lines.add(WorldMaps.shardLabel(view.shard()) + "・" + WorldMaps.dimensionLabel(view.dimension()) + (view.cave() ? "・" + WorldMaps.layerLabel(view) : ""));
			colors.add(Theme.TEXT_SECONDARY);
		}
		float scale = (float) TEXT_SCALES[s.minimapText];
		int lineHeight = lineHeight();
		int maxWidth = (int) Math.floor(Math.max(size, 120) / scale);
		for (int i = 0; i < lines.size(); i++) {
			String text = font.plainSubstrByWidth(lines.get(i), maxWidth);
			int w = (int) Math.ceil(font.width(text) * scale);
			int left = x + (size - w) / 2;
			int top = y + i * lineHeight;
			if (s.minimapTextBg) {
				gfx.fill(left - 2, top - 1, left + w + 2, top + lineHeight, 0xA0101418);
			}
			gfx.push(left, top, scale);
			gfx.text(font, text, 1, 1, 0xC0000000);
			gfx.text(font, text, 0, 0, colors.get(i));
			gfx.pop();
		}
	}

	private static void compass(Gfx gfx, Font font, int x, int y, int size, MinimapRaster.Frame frame) {
		String[] letters = { "N", "E", "S", "W" };
		double[][] dirs = { { 0, -1 }, { 1, 0 }, { 0, 1 }, { -1, 0 } };
		double radius = frame.inner() * size / frame.res() - 5;
		for (int i = 0; i < 4; i++) {
			double[] d = frame.screenDirection(dirs[i][0], dirs[i][1]);
			double scale = frame.circle() ? 1 : 1 / Math.max(Math.abs(d[0]), Math.abs(d[1]));
			int cx = x + size / 2 + (int) Math.round(d[0] * radius * scale);
			int cy = y + size / 2 + (int) Math.round(d[1] * radius * scale);
			int w = font.width(letters[i]);
			gfx.text(font, letters[i], cx - w / 2 + 1, cy - 3, 0xC0000000);
			gfx.text(font, letters[i], cx - w / 2, cy - 4, i == 0 ? 0xFFFF6B6B : 0xFFE8EEF2);
		}
	}

	static MinimapRaster.Overlay overlay(WorldMaps.View view, MapMarkers.Data data, double bpp, double minX, double minZ, double maxX, double maxZ) {
		ModSettings.Data s = ModSettings.get();
		List<MapMarkers.Area> areas = s.mapResidences
				? data.areas().stream().filter(a -> a.maxX() >= minX && a.minX() <= maxX && a.maxZ() >= minZ && a.minZ() <= maxZ).toList()
				: List.of();
		MapMarkers.Regen regen = s.mapRegen ? WorldMaps.regen(view) : null;
		if (areas.isEmpty() && (regen == null || regen.minutes().isEmpty())) {
			return MinimapRaster.NONE;
		}
		double edge = Math.max(bpp, 0.5);
		int cx0 = Math.floorDiv((int) Math.floor(minX), 16);
		int cz0 = Math.floorDiv((int) Math.floor(minZ), 16);
		int w = Math.floorDiv((int) Math.floor(maxX), 16) - cx0 + 1;
		int h = Math.floorDiv((int) Math.floor(maxZ), 16) - cz0 + 1;
		if (w <= 0 || h <= 0 || (long) w * h > OVERLAY_CELLS) {
			return MinimapRaster.NONE;
		}
		int[] regenCells = new int[w * h];
		if (regen != null && !regen.minutes().isEmpty()) {
			int elapsed = (int) ((System.currentTimeMillis() - regen.fetchedAt()) / 60000);
			for (int cz = 0; cz < h; cz++) {
				for (int cx = 0; cx < w; cx++) {
					Integer stored = regen.minutes().get(MapMarkers.key(cx0 + cx, cz0 + cz));
					if (stored != null) {
						regenCells[cz * w + cx] = stored - elapsed <= 0 ? REGEN_READY : REGEN_PENDING;
					}
				}
			}
		}
		@SuppressWarnings("unchecked")
		List<MapMarkers.Area>[] areaCells = new List[w * h];
		for (MapMarkers.Area area : areas) {
			int ax0 = Math.max(0, Math.floorDiv((int) Math.floor(area.minX()), 16) - cx0);
			int az0 = Math.max(0, Math.floorDiv((int) Math.floor(area.minZ()), 16) - cz0);
			int ax1 = Math.min(w - 1, Math.floorDiv((int) Math.floor(area.maxX()), 16) - cx0);
			int az1 = Math.min(h - 1, Math.floorDiv((int) Math.floor(area.maxZ()), 16) - cz0);
			for (int cz = az0; cz <= az1; cz++) {
				for (int cx = ax0; cx <= ax1; cx++) {
					int cell = cz * w + cx;
					if (areaCells[cell] == null) {
						areaCells[cell] = new ArrayList<>(2);
					}
					areaCells[cell].add(area);
				}
			}
		}
		return (wx, wz) -> {
			int cx = Math.floorDiv((int) Math.floor(wx), 16) - cx0;
			int cz = Math.floorDiv((int) Math.floor(wz), 16) - cz0;
			if (cx < 0 || cz < 0 || cx >= w || cz >= h) {
				return 0;
			}
			int cell = cz * w + cx;
			List<MapMarkers.Area> here = areaCells[cell];
			if (here != null) {
				for (MapMarkers.Area area : here) {
					if (area.contains(wx, wz)) {
						boolean border = !area.contains(wx - edge, wz) || !area.contains(wx + edge, wz) || !area.contains(wx, wz - edge) || !area.contains(wx, wz + edge);
						return border ? MapMath.alpha(area.line() | 0xFF000000, 0.85) : MapMath.alpha(area.fill() | 0xFF000000, Math.min(0.32, Math.max(0.12, (area.fill() >>> 24) / 255.0)));
					}
				}
			}
			return regenCells[cell];
		};
	}

	private static void build(WorldMaps.View view, MinimapRaster.Frame frame, float partial) {
		int res = frame.res();
		if (buffer.length != res * res) {
			buffer = new int[res * res];
		}
		ModSettings.Data s = ModSettings.get();
		double unit = frame.unit();
		List<MinimapRaster.Dot> dots = new ArrayList<>();
		if (s.minimapWaypoints) {
			for (Waypoints.Point point : Waypoints.list(view.waypoints())) {
				if (point.visible) {
					dots.add(new MinimapRaster.Dot(point.x + 0.5, point.z + 0.5, 3.2 * unit * ICON_SCALES[s.minimapIcons], Waypoints.color(point), MinimapRaster.Shape.DIAMOND, true));
				}
			}
		}
		for (Marker marker : markers) {
			if ((marker.kind() == Kind.PLAYER ? s.minimapPlayerHeads : s.minimapMobIcons) || marker.entity().isRemoved()) {
				continue;
			}
			Vec3 at = marker.entity().getPosition(partial);
			int color = switch (marker.kind()) {
				case HOSTILE -> HOSTILE;
				case PASSIVE -> PASSIVE;
				case PLAYER -> PLAYER_DOT;
			};
			dots.add(new MinimapRaster.Dot(at.x, at.z, (marker.kind() == Kind.PLAYER ? 2 : 2.2) * unit * ICON_SCALES[s.minimapIcons], color, MinimapRaster.Shape.DISC, false));
		}
		double reach = frame.r() * frame.bpp() * 1.5;
		double minX = frame.px() - reach;
		double maxX = frame.px() + reach;
		double minZ = frame.pz() - reach;
		double maxZ = frame.pz() + reach;
		MapMarkers.Data data = WorldMaps.markers(view);
		if (s.mapPublicHomes) {
			for (MapMarkers.Poi poi : data.pois()) {
				if (poi.x() >= minX && poi.x() <= maxX && poi.z() >= minZ && poi.z() <= maxZ) {
					dots.add(new MinimapRaster.Dot(poi.x(), poi.z(), 2.4 * unit, POI, MinimapRaster.Shape.SQUARE, false));
				}
			}
		}
		MinimapRaster.render(buffer, frame, (wx, wz) -> WorldMaps.sample(view, wx, wz), overlay(view, data, frame.bpp(), minX, minZ, maxX, maxZ), dots);
		NativeImage image = texture.getPixels();
		for (int i = 0; i < buffer.length; i++) {
			image.setPixel(i % res, i / res, buffer[i]);
		}
		texture.upload();
	}
}
