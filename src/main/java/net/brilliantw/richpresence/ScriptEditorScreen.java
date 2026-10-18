package net.brilliantw.richpresence;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.gui.components.EditBox;
//? if >=26.1 {
import net.minecraft.client.gui.GuiGraphicsExtractor;
//?} else {
/*import net.minecraft.client.gui.GuiGraphics;
*///?}
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import static net.brilliantw.richpresence.ScriptView.*;

public final class ScriptEditorScreen extends Screen {
	private static final int HEADER = 26;
	private static final int GROUP_BAR = 18;
	private static final int CATS = 44;
	private static final int MIN_FLYOUT = 150;
	private static final int SNAP = 18;
	private static final int WORKSPACE_BG = 0xFF161B20;
	private static final int RUNNING = 0xFFFFE066;
	private static final int SELECTED = 0xFF4C97FF;
	private static final double MIN_ZOOM = 0.4;
	private static final double MAX_ZOOM = 2.0;
	private static final int MAX_UNDO = 60;
	private static final int SEARCH_H = 20;
	private static final int NOTE_W = 130;
	private static final int MAP_W = 110;
	private static final int MAP_H = 70;
	private static final int DEBUG_W = 190;
	private static final int NOTE_BODY = 0xFFFFF0A0;
	private static final int NOTE_HEAD = 0xFFE6C84A;

	private record Placed(Scripts.Stack stack, List<Node> nodes, int x, int y, int w, int h) {}

	private record PaletteItem(Node node, String header, String button, boolean dim, int y, int h) {}

	private record Target(List<Scripts.Block> list, int index, Scripts.Stack stack, int shiftY) {}

	private record Popup(Scripts.Block block, int index, List<String> options, int x, int y, int w) {}

	private record MenuEntry(String label, Runnable action) {}

	private final Screen parent;
	private ScriptView view;
	private final List<Placed> placed = new ArrayList<>();
	private final List<PaletteItem> palette = new ArrayList<>();
	private double panX;
	private double panY;
	private double paletteScroll;
	private int paletteHeight;

	private List<Scripts.Block> dragChunk;
	private Scripts.Block dragReporter;
	private int dragOffsetX;
	private int dragOffsetY;
	private boolean dragFromPalette;

	private Node pressNode;
	private boolean pressFromPalette;
	private double pressX;
	private double pressY;
	private boolean panning;

	private EditBox editor;
	private SlotBox editing;
	private Popup popup;
	private List<MenuEntry> menu;
	private int menuX;
	private int menuY;
	private String bubble;
	private int bubbleX;
	private int bubbleY;
	private long bubbleUntil;
	private String status = "";
	private long statusUntil;
	private double zoom = 1;
	private final Deque<String> undo = new ArrayDeque<>();
	private final Deque<String> redo = new ArrayDeque<>();
	private String checkpoint;
	private final Set<Scripts.Stack> selected = Collections.newSetFromMap(new IdentityHashMap<>());
	private boolean selecting;
	private double selectX;
	private double selectY;
	private double selectToX;
	private double selectToY;
	private List<Scripts.Stack> moving;
	private int[] movingStart;
	private double moveX;
	private double moveY;
	private boolean rightPressed;
	private boolean rightPanning;
	private double rightX;
	private double rightY;
	private double lastMouseX;
	private double lastMouseY;
	private int helpPage = -1;
	private ScriptTutorial tutorial;
	private CommandSuggestionBox slotSuggestions;
	private EditBox search;
	private String query = "";
	private Scripts.Note draggingNote;
	private int noteOffsetX;
	private int noteOffsetY;
	private boolean minimapDrag;
	private double mapMinX;
	private double mapMinY;
	private double mapScale = 1;
	private boolean debugMode;
	private boolean resizingPalette;

	public ScriptEditorScreen(Screen parent) {
		super(Component.literal("自動腳本"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		view = new ScriptView(font);
		editor = new EditBox(font, 0, 0, 60, SLOT_H, Component.literal("輸入"));
		editor.setBordered(false);
		editor.setTextColor(SLOT_TEXT);
		editor.setTextShadow(false);
		editor.setMaxLength(Scripts.MAX_TEXT);
		editor.visible = false;
		editor.setResponder(value -> {
			if (slotSuggestions != null) {
				slotSuggestions.update();
			}
		});
		addRenderableWidget(editor);
		search = new EditBox(font, CATS + 6, HEADER + 4, flyout() - 12, 13, Component.literal("搜尋積木"));
		search.setMaxLength(20);
		Theme.hint(search, "搜尋積木…（Ctrl+F）");
		search.setValue(query);
		search.setResponder(value -> {
			query = value.strip();
			paletteScroll = 0;
		});
		addRenderableWidget(search);
		if (!Scripts.data().tutorialSeen) {
			Scripts.data().tutorialSeen = true;
			Scripts.save();
			helpPage = 0;
		}
	}

	private int flyout() {
		return Math.clamp(Scripts.data().paletteWidth, MIN_FLYOUT, Math.max(MIN_FLYOUT, width / 2 - CATS));
	}

	private int palette() {
		return CATS + flyout();
	}

	private boolean onDivider(double mx, double my) {
		return my >= HEADER && Math.abs(mx - palette()) <= 3;
	}

	private int areaX() {
		return palette();
	}

	private int areaY() {
		return HEADER + GROUP_BAR;
	}

	private record Tab(int index, int x, int w) {
		boolean contains(double mx, double my) {
			return mx >= x && mx < x + w && my >= HEADER + 2 && my < HEADER + GROUP_BAR - 2;
		}
	}

	private List<Tab> groupTabs() {
		List<Tab> tabs = new ArrayList<>();
		int x = areaX() + 4;
		List<Scripts.Group> groups = Scripts.data().groups;
		for (int i = 0; i < groups.size(); i++) {
			int w = font.width(groupLabel(groups.get(i))) + 12;
			tabs.add(new Tab(i, x, w));
			x += w + 2;
		}
		if (groups.size() < Scripts.MAX_GROUPS) {
			tabs.add(new Tab(-1, x, font.width("＋ 新增分組") + 12));
		}
		return tabs;
	}

	private String groupLabel(Scripts.Group group) {
		return group.enabled ? group.name : group.name + "（停用）";
	}

	private void selectGroup(int index) {
		commitEdit();
		Scripts.data().selected = index;
		panX = 0;
		panY = 0;
	}

	private void renameGroup(Scripts.Group group) {
		setScreen(new EditDialog(this, "重新命名分組", List.of(new EditDialog.Field("分組名稱", "例如：掛機", group.name, Scripts.MAX_NAME)), null, 0, (values, index) -> {
			String name = values.getFirst().strip();
			if (name.isEmpty()) {
				return "請輸入分組名稱";
			}
			if (!name.equals(group.name) && Scripts.data().groups.stream().anyMatch(other -> other.name.equals(name))) {
				return "已經有這個名稱的分組了";
			}
			group.name = name;
			Scripts.save();
			return null;
		}));
	}

	private void deleteGroup(Scripts.Group group) {
		if (Scripts.data().groups.size() <= 1) {
			group.stacks.clear();
			setStatus("已清空這個分組（至少要保留一個分組）");
			return;
		}
		Scripts.data().groups.remove(group);
		selectGroup(Math.min(Scripts.data().selected, Scripts.data().groups.size() - 1));
		Scripts.save();
		setStatus("已刪除分組「" + group.name + "」");
	}

	private boolean clickGroupBar(double mx, double my, int button) {
		for (Tab tab : groupTabs()) {
			if (!tab.contains(mx, my)) {
				continue;
			}
			if (tab.index() < 0) {
				if (button == InputConstants.MOUSE_BUTTON_LEFT) {
					Scripts.data().groups.add(new Scripts.Group(Scripts.uniqueName("分組")));
					selectGroup(Scripts.data().groups.size() - 1);
					Scripts.save();
				}
				return true;
			}
			Scripts.Group group = Scripts.data().groups.get(tab.index());
			if (button == InputConstants.MOUSE_BUTTON_RIGHT) {
				int blocks = group.stacks.stream().mapToInt(stack -> Scripts.count(stack.blocks)).sum();
				menuX = (int) mx;
				menuY = (int) my;
				menu = List.of(
						new MenuEntry("重新命名", () -> renameGroup(group)),
						new MenuEntry(group.enabled ? "停用這個分組" : "啟用這個分組", () -> {
							group.enabled = !group.enabled;
							Scripts.save();
						}),
						new MenuEntry(blocks > 0 ? "刪除分組（含 " + blocks + " 塊積木）" : "刪除分組", () -> deleteGroup(group)));
			} else {
				selectGroup(tab.index());
			}
			return true;
		}
		return true;
	}

	private static String noun(ScriptBlocks.SlotKind kind) {
		return kind == ScriptBlocks.SlotKind.LIST ? "清單" : "變數";
	}

	private void createName(ScriptBlocks.SlotKind kind) {
		Scripts.Group group = Scripts.current();
		String noun = noun(kind);
		boolean list = kind == ScriptBlocks.SlotKind.LIST;
		setScreen(new EditDialog(this, "建立" + noun, List.of(new EditDialog.Field(noun + "名稱", list ? "例如：公告" : "例如：次數", "", Scripts.MAX_NAME)),
				List.of("所有分組共用", "只有「" + group.name + "」可以用"), 0, (values, index) -> {
					String name = values.getFirst().strip();
					if (name.isEmpty()) {
						return "請輸入" + noun + "名稱";
					}
					if (Scripts.names(kind, group).contains(name)) {
						return "已經有這個" + noun + "了";
					}
					List<String> target = index == 0 ? list ? Scripts.data().lists : Scripts.data().variables : list ? group.lists : group.variables;
					int max = list ? Scripts.MAX_LISTS : Scripts.MAX_VARIABLES;
					if (target.size() >= max) {
						return "最多 " + max + " 個" + noun;
					}
					target.add(name);
					Scripts.save();
					return null;
				}).choice("範圍"));
	}

	private void renameName(ScriptBlocks.SlotKind kind, String name) {
		Scripts.Group group = Scripts.current();
		Scripts.Group scope = Scripts.scopeOf(kind, group, name);
		String noun = noun(kind);
		setScreen(new EditDialog(this, "重新命名" + noun, List.of(new EditDialog.Field(noun + "名稱", "", name, Scripts.MAX_NAME)), null, 0, (values, index) -> {
			String next = values.getFirst().strip();
			if (next.isEmpty()) {
				return "請輸入" + noun + "名稱";
			}
			if (!next.equals(name) && Scripts.names(kind, group).contains(next)) {
				return "已經有這個" + noun + "了";
			}
			Scripts.rename(kind, scope, name, next);
			Scripts.save();
			return null;
		}));
	}

	private void nameMenu(ScriptBlocks.SlotKind kind, String name, double mx, double my) {
		Scripts.Group group = Scripts.current();
		Scripts.Group scope = Scripts.scopeOf(kind, group, name);
		String noun = noun(kind);
		boolean list = kind == ScriptBlocks.SlotKind.LIST;
		String where = scope == null ? "（所有分組共用）" : "（只有這個分組）";
		String reset = list ? "清空內容（目前 " + ScriptEngine.listValue(group, name).size() + " 項）" : "重設為 0（目前：" + font.plainSubstrByWidth(ScriptEngine.variable(group, name), 80) + "）";
		menuX = (int) mx;
		menuY = (int) my;
		menu = List.of(
				new MenuEntry("重新命名" + noun + "「" + name + "」" + where, () -> renameName(kind, name)),
				new MenuEntry(reset, () -> {
					if (list) {
						Scripts.data().listValues.remove(Scripts.listKey(group, name));
					} else {
						Scripts.data().values.remove(Scripts.key(group, name));
					}
					Scripts.save();
					setStatus("已" + (list ? "清空清單" : "重設變數") + "「" + name + "」");
				}),
				new MenuEntry("刪除" + noun + "「" + name + "」", () -> {
					Scripts.delete(kind, scope, name);
					Scripts.save();
					setStatus("已刪除" + noun + "「" + name + "」");
				}));
	}

	private void createProc() {
		Scripts.Group group = Scripts.current();
		setScreen(new EditDialog(this, "製作新積木", List.of(new EditDialog.Field("積木名稱", "例如：打招呼", "", Scripts.MAX_NAME)), null, 0, (values, index) -> {
			String name = values.getFirst().strip();
			if (name.isEmpty()) {
				return "請輸入積木名稱";
			}
			if (Scripts.procsOf(group).contains(name)) {
				return "已經有這個名稱的積木了";
			}
			if (Scripts.count() >= Scripts.MAX_BLOCKS) {
				return "積木數量已達上限";
			}
			Scripts.Block definition = Scripts.create(ScriptBlocks.get("proc_def"));
			definition.args.getFirst().text = name;
			group.stacks.add(new Scripts.Stack(toWorkspaceX(lx(areaX() + 30)), toWorkspaceY(ly(areaY() + 30)), new ArrayList<>(List.of(definition))));
			Scripts.save();
			setStatus("已建立「" + name + "」：把要做的事接在它下面，再用「執行積木」呼叫它");
			return null;
		}));
	}

	private void paletteButton(String button) {
		switch (button) {
			case "var" -> createName(ScriptBlocks.SlotKind.VARIABLE);
			case "list" -> createName(ScriptBlocks.SlotKind.LIST);
			default -> createProc();
		}
	}

	private int noteHeight(Scripts.Note note) {
		return 12 + Math.min(12, font.split(Component.literal(note.text), NOTE_W - 8).size()) * 10;
	}

	private void drawNote(Gfx gfx, Scripts.Note note) {
		int x = toScreenX(note.x);
		int y = toScreenY(note.y);
		int h = noteHeight(note);
		gfx.fill(x + 2, y + 3, x + NOTE_W + 2, y + h + 3, 0x50000000);
		gfx.fill(x, y, x + NOTE_W, y + h, NOTE_BODY);
		gfx.fill(x, y, x + NOTE_W, y + 6, NOTE_HEAD);
		int ty = y + 9;
		List<net.minecraft.util.FormattedCharSequence> lines = font.split(Component.literal(note.text), NOTE_W - 8);
		for (int i = 0; i < Math.min(12, lines.size()); i++) {
			gfx.text(font, lines.get(i), x + 4, ty, 0xFF3A3220);
			ty += 10;
		}
	}

	private Scripts.Note noteAt(double lx, double ly) {
		List<Scripts.Note> notes = Scripts.current().notes;
		for (int i = notes.size() - 1; i >= 0; i--) {
			Scripts.Note note = notes.get(i);
			int x = toScreenX(note.x);
			int y = toScreenY(note.y);
			if (lx >= x && lx < x + NOTE_W && ly >= y && ly < y + noteHeight(note)) {
				return note;
			}
		}
		return null;
	}

	private void editNote(Scripts.Note note) {
		setScreen(new EditDialog(this, note == null ? "新增註解" : "編輯註解", List.of(new EditDialog.Field("註解內容", "例如：這組腳本負責自動回覆", note == null ? "" : note.text, Scripts.MAX_NOTE)), null, 0, (values, index) -> {
			String text = values.getFirst().strip();
			if (text.isEmpty()) {
				return "請輸入註解內容";
			}
			if (note != null) {
				note.text = text;
			} else if (Scripts.current().notes.size() >= Scripts.MAX_NOTES) {
				return "每個分組最多 " + Scripts.MAX_NOTES + " 張註解";
			} else {
				Scripts.current().notes.add(new Scripts.Note(toWorkspaceX(lx(menuX)), toWorkspaceY(ly(menuY)), text));
			}
			Scripts.save();
			return null;
		}));
	}

	private double[] worldBounds() {
		double minX = Double.MAX_VALUE;
		double minY = Double.MAX_VALUE;
		double maxX = -Double.MAX_VALUE;
		double maxY = -Double.MAX_VALUE;
		for (Placed p : placed) {
			Scripts.Stack stack = p.stack();
			minX = Math.min(minX, stack.x);
			minY = Math.min(minY, stack.y);
			maxX = Math.max(maxX, stack.x + p.w());
			maxY = Math.max(maxY, stack.y + p.h());
		}
		for (Scripts.Note note : Scripts.current().notes) {
			minX = Math.min(minX, note.x);
			minY = Math.min(minY, note.y);
			maxX = Math.max(maxX, note.x + NOTE_W);
			maxY = Math.max(maxY, note.y + noteHeight(note));
		}
		return minX > maxX ? null : new double[] { minX, minY, maxX, maxY };
	}

	private void fitAll() {
		layoutWorkspace();
		double[] bounds = worldBounds();
		if (bounds == null) {
			zoom = 1;
			panX = 0;
			panY = 0;
			return;
		}
		double availW = width - areaX() - 40;
		double availH = height - areaY() - 40;
		double bw = Math.max(1, bounds[2] - bounds[0]);
		double bh = Math.max(1, bounds[3] - bounds[1]);
		zoom = Math.clamp(Math.min(availW / bw, availH / bh), MIN_ZOOM, 1.0);
		panX = bounds[0] - (20 + (availW - bw * zoom) / 2) / zoom;
		panY = bounds[1] - (20 + (availH - bh * zoom) / 2) / zoom;
	}

	private int mapX() {
		return width - MAP_W - 6;
	}

	private int mapY() {
		return height - MAP_H - 18;
	}

	private boolean inMinimap(double mx, double my) {
		return worldBounds() != null && mx >= mapX() && mx < mapX() + MAP_W && my >= mapY() && my < mapY() + MAP_H;
	}

	private void drawMinimap(Gfx gfx) {
		double[] bounds = worldBounds();
		if (bounds == null) {
			return;
		}
		double viewW = (width - areaX()) / zoom;
		double viewH = (height - areaY()) / zoom;
		double minX = Math.min(bounds[0], panX);
		double minY = Math.min(bounds[1], panY);
		double maxX = Math.max(bounds[2], panX + viewW);
		double maxY = Math.max(bounds[3], panY + viewH);
		mapScale = Math.min((MAP_W - 8) / Math.max(1, maxX - minX), (MAP_H - 8) / Math.max(1, maxY - minY));
		mapMinX = minX;
		mapMinY = minY;
		int x0 = mapX();
		int y0 = mapY();
		gfx.fill(x0 - 1, y0 - 1, x0 + MAP_W + 1, y0 + MAP_H + 1, 0x80FFFFFF);
		gfx.fill(x0, y0, x0 + MAP_W, y0 + MAP_H, 0xE0101418);
		for (Scripts.Note note : Scripts.current().notes) {
			mapRect(gfx, note.x, note.y, NOTE_W, noteHeight(note), NOTE_HEAD);
		}
		for (Placed p : placed) {
			int color = p.stack().blocks.isEmpty() ? 0xFF888888 : p.stack().blocks.getFirst().def().category().color;
			mapRect(gfx, p.stack().x, p.stack().y, p.w(), p.h(), p.stack().disabled ? 0xFF555555 : color);
		}
		int vx = (int) (x0 + 4 + (panX - minX) * mapScale);
		int vy = (int) (y0 + 4 + (panY - minY) * mapScale);
		int vw = Math.max(2, (int) (viewW * mapScale));
		int vh = Math.max(2, (int) (viewH * mapScale));
		gfx.fill(vx, vy, vx + vw, vy + 1, 0xFFFFFFFF);
		gfx.fill(vx, vy + vh - 1, vx + vw, vy + vh, 0xFFFFFFFF);
		gfx.fill(vx, vy, vx + 1, vy + vh, 0xFFFFFFFF);
		gfx.fill(vx + vw - 1, vy, vx + vw, vy + vh, 0xFFFFFFFF);
	}

	private void mapRect(Gfx gfx, double x, double y, double w, double h, int color) {
		int rx = (int) (mapX() + 4 + (x - mapMinX) * mapScale);
		int ry = (int) (mapY() + 4 + (y - mapMinY) * mapScale);
		gfx.fill(rx, ry, rx + Math.max(1, (int) (w * mapScale)), ry + Math.max(1, (int) (h * mapScale)), color);
	}

	private void centerMinimap(double mx, double my) {
		double viewW = (width - areaX()) / zoom;
		double viewH = (height - areaY()) / zoom;
		panX = mapMinX + (mx - mapX() - 4) / mapScale - viewW / 2;
		panY = mapMinY + (my - mapY() - 4) / mapScale - viewH / 2;
	}

	private int debugX() {
		return width - DEBUG_W - 6;
	}

	private int debugY() {
		return areaY() + 6;
	}

	private Chip debugButton(int index) {
		String[] labels = { "下一步 F10", "繼續 F9", "停止" };
		int x = debugX() + 6;
		for (int i = 0; i < index; i++) {
			x += font.width(labels[i]) + 12;
		}
		return new Chip(labels[index], x, font.width(labels[index]) + 10);
	}

	private boolean debugButtonHit(Chip chip, double mx, double my) {
		return mx >= chip.x() && mx < chip.x() + chip.w() && my >= debugY() + 16 && my < debugY() + 30;
	}

	private void drawDebug(Gfx gfx, int mouseX, int mouseY) {
		List<String> lines = new ArrayList<>();
		Scripts.Group group = Scripts.current();
		boolean running = ScriptEngine.debugging();
		if (!running) {
			lines.add("點一下工作區的腳本，");
			lines.add("就會停在第一塊積木等你按下一步");
		} else {
			Scripts.Block next = ScriptEngine.debugBlock();
			lines.add("§下一步：" + (next == null ? "（執行完畢）" : ScriptMonitor.describe(next)));
			String waiting = ScriptEngine.debugWaiting();
			if (waiting != null) {
				lines.add("§" + waiting);
			}
			if (next != null) {
				for (int i = 0; i < next.args.size(); i++) {
					if (next.args.get(i).block != null) {
						lines.add("第 " + (i + 1) + " 格 = " + ScriptEngine.debugValue(next.args.get(i)));
					}
				}
			}
		}
		for (String name : Scripts.variablesOf(group)) {
			lines.add("變數 " + name + " = " + ScriptEngine.variable(group, name));
		}
		for (String name : Scripts.listsOf(group)) {
			lines.add("清單 " + name + "（" + ScriptEngine.listValue(group, name).size() + " 項）");
		}
		int x = debugX();
		int y = debugY();
		int h = 34 + Math.min(14, lines.size()) * 10 + 4;
		gfx.fill(x - 1, y - 1, x + DEBUG_W + 1, y + h + 1, 0xFFFFAB19);
		gfx.fill(x, y, x + DEBUG_W, y + h, 0xF0181D22);
		gfx.text(font, "逐步執行", x + 6, y + 4, 0xFFFFAB19);
		if (running) {
			for (int i = 0; i < 3; i++) {
				Chip chip = debugButton(i);
				gfx.fill(chip.x(), y + 16, chip.x() + chip.w(), y + 30, debugButtonHit(chip, mouseX, mouseY) ? Theme.CHIP_HOVER : Theme.CHIP);
				gfx.centered(font, chip.label(), chip.x(), y + 16, chip.w(), 14, i == 0 ? Theme.SUCCESS : i == 2 ? Theme.DANGER : Theme.TEXT);
			}
		}
		int ly = y + 34;
		for (int i = 0; i < Math.min(14, lines.size()); i++) {
			String line = lines.get(i);
			boolean strong = line.startsWith("§");
			gfx.text(font, font.plainSubstrByWidth(strong ? line.substring(1) : line, DEBUG_W - 12), x + 6, ly, strong ? ScriptView.CURRENT : Theme.TEXT);
			ly += 10;
		}
	}

	private boolean clickDebug(double mx, double my) {
		if (mx < debugX() || mx >= debugX() + DEBUG_W || my < debugY() || my >= debugY() + 34) {
			return false;
		}
		if (ScriptEngine.debugging()) {
			if (debugButtonHit(debugButton(0), mx, my)) {
				ScriptEngine.debugStep();
			} else if (debugButtonHit(debugButton(1), mx, my)) {
				ScriptEngine.debugContinue();
			} else if (debugButtonHit(debugButton(2), mx, my)) {
				ScriptEngine.stop(ScriptEngine.debugStack());
			}
		}
		return true;
	}

	private void stopDebug() {
		Scripts.Stack stack = ScriptEngine.debugStack();
		if (stack != null) {
			ScriptEngine.stop(stack);
		}
	}

	private int toScreenX(int wx) {
		return areaX() + wx - (int) panX;
	}

	private int toScreenY(int wy) {
		return areaY() + wy - (int) panY;
	}

	private int toWorkspaceX(double sx) {
		return (int) Math.round(sx - areaX() + panX);
	}

	private int toWorkspaceY(double sy) {
		return (int) Math.round(sy - areaY() + panY);
	}

	private double lx(double sx) {
		return areaX() + (sx - areaX()) / zoom;
	}

	private double ly(double sy) {
		return areaY() + (sy - areaY()) / zoom;
	}

	private int sx(double lx) {
		return (int) Math.round(areaX() + (lx - areaX()) * zoom);
	}

	private int sy(double ly) {
		return (int) Math.round(areaY() + (ly - areaY()) * zoom);
	}

	private void pushZoom(Gfx gfx) {
		gfx.push((float) (areaX() * (1 - zoom)), (float) (areaY() * (1 - zoom)), (float) zoom);
	}

	private boolean inWorkspace(double mx, double my) {
		return mx >= areaX() && mx < width && my >= areaY() && my < height;
	}

	private boolean inPalette(double mx, double my) {
		return mx < palette() && my >= HEADER;
	}

	private void layoutWorkspace() {
		placed.clear();
		for (Scripts.Stack stack : Scripts.current().stacks) {
			int x = toScreenX(stack.x);
			int y = toScreenY(stack.y);
			Laid laid = view.layoutList(stack.blocks, x, y);
			placed.add(new Placed(stack, laid.nodes(), x, y, laid.width(), laid.height()));
		}
	}

	private int categoryStep() {
		return Math.clamp((height - HEADER - 10) / ScriptBlocks.Category.values().length, 26, 32);
	}

	private int paletteTop() {
		return HEADER + SEARCH_H + 4;
	}

	private int paletteMax() {
		return Math.max(0, paletteHeight - (height - paletteTop()));
	}

	private static String searchText(ScriptBlocks.Def def) {
		StringBuilder text = new StringBuilder(def.category().label);
		for (Object part : def.parts()) {
			if (part instanceof String piece) {
				text.append(' ').append(piece);
			}
		}
		return text.toString().toLowerCase(Locale.ROOT);
	}

	private boolean available(ScriptBlocks.Def def, Scripts.Group group) {
		if (def.id().equals("proc_def")) {
			return false;
		}
		for (ScriptBlocks.Slot slot : def.slots()) {
			List<String> names = Scripts.names(slot.kind(), group);
			if (names != null && names.isEmpty()) {
				return false;
			}
		}
		return true;
	}

	private int addPaletteBlock(Scripts.Block block, int x, int y) {
		ScriptBlocks.Def def = block.def();
		int top = def.shape() == ScriptBlocks.Shape.HAT ? 2 : 0;
		Node node = view.layoutBlock(block, x, y + top, null, -1, null);
		palette.add(new PaletteItem(node, null, null, false, y, node.h + top));
		return node.h + top + (def.isReporter() ? 6 : 8);
	}

	private Scripts.Block named(String op, String name) {
		Scripts.Block block = Scripts.create(ScriptBlocks.get(op));
		block.args.getFirst().text = name;
		return block;
	}

	private void layoutPalette() {
		palette.clear();
		Scripts.Group group = Scripts.current();
		int y = paletteTop() - (int) paletteScroll;
		int x = CATS + 8;
		if (!query.isEmpty()) {
			String needle = query.toLowerCase(Locale.ROOT);
			palette.add(new PaletteItem(null, "搜尋結果", null, false, y, 14));
			y += 16;
			int found = 0;
			for (ScriptBlocks.Def def : ScriptBlocks.all()) {
				if (searchText(def).contains(needle) && available(def, group)) {
					y += addPaletteBlock(Scripts.create(def), x, y);
					found++;
				}
			}
			if (found == 0) {
				palette.add(new PaletteItem(null, "找不到「" + query + "」相關的積木", null, true, y, 14));
				y += 16;
			}
			paletteHeight = y + (int) paletteScroll - paletteTop();
			return;
		}
		for (ScriptBlocks.Category category : ScriptBlocks.Category.values()) {
			palette.add(new PaletteItem(null, category.label, null, false, y, 14));
			y += 16;
			if (category == ScriptBlocks.Category.VARIABLE) {
				palette.add(new PaletteItem(null, null, "var", false, y, 16));
				y += 22;
				for (String name : Scripts.variablesOf(group)) {
					y += addPaletteBlock(named("var_get", name), x, y);
				}
				for (ScriptBlocks.Def def : ScriptBlocks.of(category)) {
					if (def.id().startsWith("var_") && !def.id().equals("var_get") && available(def, group)) {
						y += addPaletteBlock(Scripts.create(def), x, y);
					}
				}
				palette.add(new PaletteItem(null, null, "list", false, y, 16));
				y += 22;
				for (String name : Scripts.listsOf(group)) {
					y += addPaletteBlock(named("list_all", name), x, y);
				}
				for (ScriptBlocks.Def def : ScriptBlocks.of(category)) {
					if (def.id().startsWith("list_") && !def.id().equals("list_all") && available(def, group)) {
						y += addPaletteBlock(Scripts.create(def), x, y);
					}
				}
			} else if (category == ScriptBlocks.Category.PROC) {
				palette.add(new PaletteItem(null, null, "proc", false, y, 16));
				y += 22;
				List<String> procs = Scripts.procsOf(group);
				if (procs.isEmpty()) {
					palette.add(new PaletteItem(null, "做好的積木會出現在這裡", null, true, y, 14));
					y += 16;
				}
				for (String name : procs) {
					y += addPaletteBlock(named("proc_call", name), x, y);
				}
				if (!procs.isEmpty()) {
					y += addPaletteBlock(Scripts.create(ScriptBlocks.get("proc_arg")), x, y);
				}
			} else {
				for (ScriptBlocks.Def def : ScriptBlocks.of(category)) {
					y += addPaletteBlock(Scripts.create(def), x, y);
				}
			}
			y += 8;
		}
		paletteHeight = y + (int) paletteScroll - paletteTop();
	}

	private int categoryOffset(ScriptBlocks.Category category) {
		for (PaletteItem item : palette) {
			if (category.label.equals(item.header())) {
				return item.y() + (int) paletteScroll - paletteTop();
			}
		}
		return 0;
	}

	//? if >=26.1 {
	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		draw(new Gfx(graphics), mouseX, mouseY);
		super.extractRenderState(graphics, mouseX, mouseY, partialTick);
		drawOverlay(new Gfx(graphics), mouseX, mouseY);
		if (slotSuggestions != null) {
			slotSuggestions.render(graphics, mouseX, mouseY);
		}
	}
	//?} else {
	/*@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		draw(new Gfx(graphics), mouseX, mouseY);
		super.render(graphics, mouseX, mouseY, partialTick);
		drawOverlay(new Gfx(graphics), mouseX, mouseY);
		if (slotSuggestions != null) {
			slotSuggestions.render(graphics, mouseX, mouseY);
		}
	}
	*///?}

	private record Chip(String label, int x, int w) {
		boolean contains(double mx, double my) {
			return mx >= x && mx < x + w && my >= 6 && my < 20;
		}
	}

	private List<Chip> chips() {
		List<Chip> chips = new ArrayList<>();
		String[] labels = { "▶ 執行", "停止", debugMode ? "除錯：開" : "除錯：關", Scripts.data().enabled ? "已啟用" : "已停用", Scripts.data().monitor ? "視窗：開" : "視窗：關", "範本", "分享", "說明" };
		int x = width - 8;
		for (int i = labels.length - 1; i >= 0; i--) {
			int w = font.width(labels[i]) + 12;
			x -= w;
			chips.add(0, new Chip(labels[i], x, w));
			x -= 3;
		}
		return chips;
	}

	private void draw(Gfx gfx, int mouseX, int mouseY) {
		view.editing = editing;
		view.issues = ScriptLint.check(Scripts.current().stacks, Scripts.current());
		layoutWorkspace();
		layoutPalette();
		lastMouseX = mouseX;
		lastMouseY = mouseY;
		selected.retainAll(Scripts.current().stacks);
		gfx.fill(0, 0, width, height, WORKSPACE_BG);
		Theme.watermark(gfx, areaX(), areaY(), width - areaX(), height - areaY());
		gfx.scissor(areaX(), areaY(), width, height);
		pushZoom(gfx);
		int dot = 0xFF1E252C;
		int startX = areaX() - Math.floorMod((int) panX, 24);
		int startY = areaY() - Math.floorMod((int) panY, 24);
		for (int gx = startX; gx < lx(width); gx += 24) {
			for (int gy = startY; gy < ly(height); gy += 24) {
				gfx.fill(gx, gy, gx + 1, gy + 1, dot);
			}
		}
		for (Scripts.Note note : Scripts.current().notes) {
			drawNote(gfx, note);
		}
		for (Placed p : placed) {
			boolean picked = selected.contains(p.stack());
			if (ScriptEngine.isRunning(p.stack()) || picked) {
				gfx.fill(p.x() - 3, p.y() - 3, p.x() + p.w() + 3, p.y() + p.h() + 3, picked ? SELECTED : RUNNING);
				gfx.fill(p.x() - 1, p.y() - 1, p.x() + p.w() + 1, p.y() + p.h() + 1, WORKSPACE_BG);
			}
			for (Node node : p.nodes()) {
				view.drawNode(gfx, node);
			}
			if (p.stack().disabled) {
				gfx.fill(p.x() - 2, p.y() - 2, p.x() + p.w() + 2, p.y() + p.h() + 4, 0xB0161B20);
				gfx.text(font, "已停用", p.x() + p.w() + 5, p.y() + 3, Theme.TEXT_DIM);
			}
		}
		gfx.pop();
		gfx.endScissor();
		drawMinimap(gfx);
		if (debugMode) {
			drawDebug(gfx, mouseX, mouseY);
		}
		if (selecting) {
			int x1 = (int) Math.min(selectX, selectToX);
			int y1 = (int) Math.max(areaY(), Math.min(selectY, selectToY));
			int x2 = (int) Math.max(selectX, selectToX);
			int y2 = (int) Math.max(selectY, selectToY);
			gfx.fill(x1, y1, x2, y2, 0x304C97FF);
			gfx.fill(x1, y1, x2, y1 + 1, SELECTED);
			gfx.fill(x1, y2 - 1, x2, y2, SELECTED);
			gfx.fill(x1, y1, x1 + 1, y2, SELECTED);
			gfx.fill(x2 - 1, y1, x2, y2, SELECTED);
		}

		gfx.fill(0, HEADER, CATS, height, 0xFF1A2026);
		gfx.fill(CATS, HEADER, palette(), height, 0xFF20272E);
		boolean dividerActive = resizingPalette || onDivider(mouseX, mouseY) && dragChunk == null && dragReporter == null;
		gfx.fill(palette() - (dividerActive ? 2 : 1), HEADER, palette() + (dividerActive ? 1 : 0), height, dividerActive ? Theme.ACCENT : Theme.ACCENT_DIM);
		search.setWidth(flyout() - 12);
		int cy = HEADER + 6;
		for (ScriptBlocks.Category category : ScriptBlocks.Category.values()) {
			int cx = CATS / 2;
			boolean hovered = mouseX < CATS && mouseY >= cy && mouseY < cy + 30;
			gfx.fill(cx - 7, cy + 2, cx + 7, cy + 16, category.color);
			if (hovered) {
				gfx.fill(cx - 8, cy + 1, cx + 8, cy + 2, 0xFFFFFFFF);
			}
			gfx.centered(font, category.label, 0, cy + 18, CATS, 10, hovered ? Theme.TEXT : Theme.TEXT_SECONDARY);
			cy += categoryStep();
		}
		gfx.scissor(CATS, paletteTop() - 2, palette() - 1, height);
		Scripts.Group group = Scripts.current();
		for (PaletteItem item : palette) {
			if (item.header() != null) {
				gfx.label(font, font.plainSubstrByWidth(item.header(), flyout() - 16), CATS + 8, item.y(), item.h(), item.dim() ? Theme.TEXT_DIM : Theme.TEXT);
			} else if (item.button() != null) {
				String label = switch (item.button()) {
					case "var" -> "建立變數";
					case "list" -> "建立清單";
					default -> "製作新積木";
				};
				boolean hovered = mouseX >= CATS + 8 && mouseX < CATS + 98 && mouseY >= item.y() && mouseY < item.y() + item.h() && mouseY >= paletteTop();
				gfx.fill(CATS + 8, item.y(), CATS + 98, item.y() + item.h(), hovered ? Theme.CHIP_HOVER : Theme.CHIP);
				gfx.centered(font, label, CATS + 8, item.y(), 90, item.h(), Theme.TEXT);
			} else if (item.node() != null) {
				view.drawNode(gfx, item.node());
				Node node = item.node();
				boolean local = node.block.op.equals("var_get") && group.variables.contains(node.block.text(0))
						|| node.block.op.equals("list_all") && group.lists.contains(node.block.text(0));
				if (local) {
					gfx.text(font, "本組", node.x + node.w + 4, node.y + 3, Theme.TEXT_DIM);
				}
			}
		}
		gfx.endScissor();

		gfx.fill(areaX(), HEADER, width, areaY(), 0xFF1A2026);
		for (Tab tab : groupTabs()) {
			boolean selected = tab.index() == Scripts.data().selected;
			boolean hovered = tab.contains(mouseX, mouseY);
			gfx.fill(tab.x(), HEADER + 2, tab.x() + tab.w(), areaY() - 2, selected ? Theme.SELECTED : hovered ? Theme.CHIP_HOVER : Theme.CHIP);
			if (selected) {
				gfx.fill(tab.x(), areaY() - 3, tab.x() + tab.w(), areaY() - 2, 0xFFFFAB19);
			}
			String label = tab.index() < 0 ? "＋ 新增分組" : groupLabel(Scripts.data().groups.get(tab.index()));
			boolean disabled = tab.index() >= 0 && !Scripts.data().groups.get(tab.index()).enabled;
			gfx.centered(font, label, tab.x(), HEADER + 2, tab.w(), GROUP_BAR - 4, disabled ? Theme.TEXT_DIM : Theme.TEXT);
		}

		gfx.fill(0, 0, width, HEADER, Theme.HEADER);
		gfx.fill(0, HEADER - 1, width, HEADER, Theme.ACCENT_DIM);
		Theme.drawBack(gfx, font, 0, 0, mouseX, mouseY);
		gfx.text(font, title.getString(), Theme.titleX(font, 0), 9, Theme.ACCENT);
		String counter = Scripts.count() + " / " + Scripts.MAX_BLOCKS + " 塊 · " + Math.round(zoom * 100) + "%" + (selected.isEmpty() ? "" : " · 已選取 " + selected.size() + " 組");
		gfx.text(font, counter, Theme.titleX(font, 0) + font.width(title) + 10, 9, Theme.TEXT_DIM);
		for (Chip chip : chips()) {
			boolean enabledChip = chip.label().startsWith("已");
			int bg = chip.contains(mouseX, mouseY) ? Theme.CHIP_HOVER : Theme.CHIP;
			gfx.fill(chip.x(), 6, chip.x() + chip.w(), 20, bg);
			int color = chip.label().startsWith("▶") ? Theme.SUCCESS : enabledChip ? (Scripts.data().enabled ? Theme.SUCCESS : Theme.DANGER) : Theme.TEXT;
			gfx.centered(font, chip.label(), chip.x(), 6, chip.w(), 14, color);
		}

		String hint;
		if (System.currentTimeMillis() < statusUntil) {
			hint = status;
		} else if (!Scripts.data().enabled) {
			hint = "腳本已停用，點右上角「已停用」開啟";
		} else if (!RichPresenceModClient.isOnBrilliantServer()) {
			hint = "腳本只會在輝煌伺服器上執行";
		} else if (Scripts.current().stacks.isEmpty()) {
			hint = "從左邊拖曳積木到這裡開始組合腳本，最上面要放一個「事件」積木";
		} else if (!Scripts.current().enabled) {
			hint = "這個分組已停用，在分組標籤上按右鍵可以啟用";
		} else if (debugMode) {
			hint = "除錯模式：點一下腳本開始逐步執行 · F10 下一步 · F9 繼續執行 · 再點「除錯：開」可以關閉";
		} else if (!view.issues.isEmpty()) {
			hint = "⚠ 有 " + view.issues.size() + " 個積木可能無法如預期運作，滑鼠移到黃色驚嘆號上查看原因";
		} else {
			hint = "點積木執行 · 拖回左邊刪除 · 左鍵拖曳空白處框選 · 右鍵／中鍵拖曳移動畫面 · 滾輪縮放 · Ctrl+1 顯示全部 · F1 說明";
		}
		gfx.text(font, font.plainSubstrByWidth(hint, width - areaX() - 12), areaX() + 6, height - 12, Theme.TEXT_DIM);

		if (editing != null && editor != null) {
			gfx.fill(editor.getX() - 2, editor.getY() - 2, editor.getX() + editor.getWidth() + 2, editor.getY() + SLOT_H, 0xFFFFFFFF);
		}
	}

	private void drawOverlay(Gfx gfx, int mouseX, int mouseY) {
		if (dragChunk != null) {
			pushZoom(gfx);
			Laid laid = view.layoutList(dragChunk, (int) lx(mouseX) - dragOffsetX, (int) ly(mouseY) - dragOffsetY);
			for (Node node : laid.nodes()) {
				view.drawNode(gfx, node);
			}
			gfx.pop();
		} else if (dragReporter != null) {
			pushZoom(gfx);
			view.drawNode(gfx, view.layoutBlock(dragReporter, (int) lx(mouseX) - dragOffsetX, (int) ly(mouseY) - dragOffsetY, null, -1, null));
			gfx.pop();
		}
		if (popup != null) {
			int h = popup.options().size() * 12 + 4;
			gfx.fill(popup.x() - 1, popup.y() - 1, popup.x() + popup.w() + 1, popup.y() + h + 1, Theme.ACCENT_DIM);
			gfx.fill(popup.x(), popup.y(), popup.x() + popup.w(), popup.y() + h, Theme.PANEL);
			for (int i = 0; i < popup.options().size(); i++) {
				int oy = popup.y() + 2 + i * 12;
				if (mouseX >= popup.x() && mouseX < popup.x() + popup.w() && mouseY >= oy && mouseY < oy + 12) {
					gfx.fill(popup.x(), oy, popup.x() + popup.w(), oy + 12, Theme.SELECTED);
				}
				gfx.label(font, popup.options().get(i), popup.x() + 4, oy, 12, Theme.TEXT);
			}
		}
		if (menu != null) {
			int w = menuWidth();
			int h = menu.size() * 14 + 4;
			int x = menuLeft();
			int y = menuTop();
			gfx.fill(x - 1, y - 1, x + w + 1, y + h + 1, Theme.ACCENT_DIM);
			gfx.fill(x, y, x + w, y + h, Theme.PANEL);
			for (int i = 0; i < menu.size(); i++) {
				int oy = y + 2 + i * 14;
				if (mouseX >= x && mouseX < x + w && mouseY >= oy && mouseY < oy + 14) {
					gfx.fill(x, oy, x + w, oy + 14, Theme.SELECTED);
				}
				gfx.label(font, menu.get(i).label(), x + 6, oy, 14, Theme.TEXT);
			}
		}
		if (dragChunk == null && dragReporter == null && menu == null && popup == null && inWorkspace(mouseX, mouseY)) {
			for (Placed p : placed) {
				Node flagged = view.badgeAt(p.nodes(), lx(mouseX), ly(mouseY));
				if (flagged != null) {
					List<net.minecraft.util.FormattedCharSequence> lines = font.split(Component.literal(view.issues.get(flagged.block)), 200);
					int w = lines.stream().mapToInt(font::width).max().orElse(0) + 10;
					int h = lines.size() * 10 + 6;
					int tx = Math.min(mouseX + 8, width - w - 2);
					int ty = Math.min(mouseY + 8, height - h - 2);
					gfx.fill(tx - 1, ty - 1, tx + w + 1, ty + h + 1, WARNING);
					gfx.fill(tx, ty, tx + w, ty + h, 0xF0202428);
					for (int i = 0; i < lines.size(); i++) {
						gfx.text(font, lines.get(i), tx + 5, ty + 4 + i * 10, Theme.TEXT);
					}
					break;
				}
			}
		}
		if (bubble != null && System.currentTimeMillis() < bubbleUntil) {
			int w = Math.min(220, font.width(bubble) + 12);
			String shown = font.plainSubstrByWidth(bubble, w - 12);
			gfx.fill(bubbleX - 1, bubbleY - 19, bubbleX + w + 1, bubbleY - 1, 0xFFB0B8C0);
			gfx.fill(bubbleX, bubbleY - 18, bubbleX + w, bubbleY - 2, 0xFFFFFFFF);
			gfx.label(font, shown, bubbleX + 6, bubbleY - 18, 16, 0xFF202020);
		}
		if (helpPage >= 0) {
			drawHelp(gfx, mouseX, mouseY);
		}
		ScriptMonitor.renderAlert(gfx, width);
	}

	private int helpW() {
		return Math.min(380, width - 20);
	}

	private int helpH() {
		return Math.min(250, height - 20);
	}

	private Chip helpButton(int index) {
		int x = (width - helpW()) / 2;
		int y = (height - helpH()) / 2 + helpH() - 22;
		return switch (index) {
			case 0 -> new Chip("上一頁", x + 8, 50);
			case 1 -> new Chip(helpPage == ScriptTutorial.TITLES.length - 1 ? "開始使用" : "下一頁", x + helpW() - 58, 50);
			default -> new Chip("略過", x + helpW() - 58 - 46, 40);
		};
	}

	private boolean helpButtonHit(Chip chip, double mx, double my) {
		int y = (height - helpH()) / 2 + helpH() - 22;
		return mx >= chip.x() && mx < chip.x() + chip.w() && my >= y && my < y + 16;
	}

	private void drawHelp(Gfx gfx, int mouseX, int mouseY) {
		gfx.fill(0, 0, width, height, 0xA0000000);
		int w = helpW();
		int h = helpH();
		int x = (width - w) / 2;
		int y = (height - h) / 2;
		gfx.fill(x - 1, y - 1, x + w + 1, y + h + 1, 0xFFFFAB19);
		gfx.fill(x, y, x + w, y + h, Theme.PANEL);
		gfx.fill(x, y, x + w, y + 20, Theme.HEADER);
		gfx.label(font, "使用教學 · " + ScriptTutorial.TITLES[helpPage], x + 8, y, 20, Theme.ACCENT);
		String count = (helpPage + 1) + " / " + ScriptTutorial.TITLES.length;
		gfx.label(font, count, x + w - 8 - font.width(count), y, 20, Theme.TEXT_DIM);
		List<net.minecraft.util.FormattedCharSequence> caption = font.split(Component.literal(ScriptTutorial.CAPTIONS[helpPage]), w - 20);
		for (int i = 0; i < Math.min(2, caption.size()); i++) {
			gfx.text(font, caption.get(i), x + 10, y + 27 + i * 10, Theme.TEXT);
		}
		int top = y + 30 + Math.min(2, caption.size()) * 10;
		gfx.fill(x + 8, top, x + w - 8, y + h - 28, WORKSPACE_BG);
		if (tutorial == null) {
			tutorial = new ScriptTutorial(font);
		}
		tutorial.draw(gfx, helpPage, x + 8, top, w - 16, y + h - 28 - top);
		int dots = ScriptTutorial.TITLES.length * 8;
		for (int i = 0; i < ScriptTutorial.TITLES.length; i++) {
			int dx = x + (w - dots) / 2 + i * 8;
			gfx.fill(dx, y + h - 15, dx + 4, y + h - 11, i == helpPage ? 0xFFFFAB19 : Theme.TEXT_DIM);
		}
		for (int i = 0; i < 3; i++) {
			Chip chip = helpButton(i);
			if (i == 0 && helpPage == 0 || i == 2 && helpPage == ScriptTutorial.TITLES.length - 1) {
				continue;
			}
			int by = y + h - 22;
			gfx.fill(chip.x(), by, chip.x() + chip.w(), by + 16, helpButtonHit(chip, mouseX, mouseY) ? Theme.CHIP_HOVER : Theme.CHIP);
			gfx.centered(font, chip.label(), chip.x(), by, chip.w(), 16, i == 1 ? Theme.SUCCESS : Theme.TEXT);
		}
	}

	private void clickHelp(double mx, double my) {
		if (helpPage > 0 && helpButtonHit(helpButton(0), mx, my)) {
			helpPage--;
		} else if (helpButtonHit(helpButton(1), mx, my)) {
			helpPage = helpPage == ScriptTutorial.TITLES.length - 1 ? -1 : helpPage + 1;
		} else if (helpPage < ScriptTutorial.TITLES.length - 1 && helpButtonHit(helpButton(2), mx, my)) {
			helpPage = -1;
		}
	}

	private void checkpoint() {
		if (checkpoint == null) {
			checkpoint = Scripts.snapshot();
		}
	}

	private void settle() {
		if (checkpoint == null || dragChunk != null || dragReporter != null) {
			return;
		}
		if (!Scripts.snapshot().equals(checkpoint)) {
			undo.push(checkpoint);
			while (undo.size() > MAX_UNDO) {
				undo.removeLast();
			}
			redo.clear();
		}
		checkpoint = null;
	}

	private void undo(boolean back) {
		Deque<String> from = back ? undo : redo;
		if (from.isEmpty()) {
			setStatus(back ? "沒有可以復原的動作" : "沒有可以重做的動作");
			return;
		}
		commitEdit();
		(back ? redo : undo).push(Scripts.snapshot());
		Scripts.restore(from.pop());
		checkpoint = null;
		selected.clear();
		setStatus(back ? "已復原" : "已重做");
	}

	private List<Scripts.Stack> selection() {
		List<Scripts.Stack> stacks = new ArrayList<>();
		for (Scripts.Stack stack : Scripts.current().stacks) {
			if (selected.contains(stack)) {
				stacks.add(stack);
			}
		}
		if (stacks.isEmpty()) {
			layoutWorkspace();
			Node node = inWorkspace(lastMouseX, lastMouseY) ? nodeAtWorkspace(lx(lastMouseX), ly(lastMouseY)) : null;
			if (node != null) {
				List<Scripts.Block> source = node.ownerArg != null || node.def.isReporter() ? List.of(node.block) : node.ownerList.subList(node.index, node.ownerList.size());
				stacks.add(new Scripts.Stack(toWorkspaceX(node.x), toWorkspaceY(node.y), new ArrayList<>(source)));
			}
		}
		return stacks;
	}

	private String encode(List<Scripts.Stack> stacks) {
		Scripts.Group group = new Scripts.Group("剪貼簿");
		group.stacks.addAll(stacks);
		return Scripts.export(group);
	}

	private boolean copySelection() {
		List<Scripts.Stack> stacks = selection();
		if (stacks.isEmpty()) {
			setStatus("先框選積木，或把滑鼠移到積木上再複製");
			return false;
		}
		minecraft.keyboardHandler.setClipboard(encode(stacks));
		setStatus("已複製 " + stacks.stream().mapToInt(stack -> Scripts.count(stack.blocks)).sum() + " 塊積木，可以按 Ctrl+V 貼上，也能貼給其他玩家");
		return true;
	}

	private void paste(String code, int offsetX, int offsetY, boolean atMouse) {
		Scripts.Data data;
		try {
			data = Scripts.parse(code);
		} catch (IllegalArgumentException e) {
			setStatus("剪貼簿裡沒有積木");
			return;
		}
		List<Scripts.Stack> stacks = new ArrayList<>();
		data.groups.forEach(group -> stacks.addAll(group.stacks));
		int blocks = stacks.stream().mapToInt(stack -> Scripts.count(stack.blocks)).sum();
		if (Scripts.count() + blocks > Scripts.MAX_BLOCKS) {
			setStatus("積木數量會超過上限 " + Scripts.MAX_BLOCKS + " 塊，沒有貼上");
			return;
		}
		for (String name : data.variables) {
			if (!Scripts.data().variables.contains(name) && Scripts.data().variables.size() < Scripts.MAX_VARIABLES) {
				Scripts.data().variables.add(name);
			}
		}
		int minX = stacks.stream().mapToInt(stack -> stack.x).min().orElse(0);
		int minY = stacks.stream().mapToInt(stack -> stack.y).min().orElse(0);
		int baseX = minX + offsetX;
		int baseY = minY + offsetY;
		if (atMouse) {
			boolean inside = inWorkspace(lastMouseX, lastMouseY);
			baseX = toWorkspaceX(inside ? lx(lastMouseX) : lx(areaX() + 30));
			baseY = toWorkspaceY(inside ? ly(lastMouseY) : ly(areaY() + 30));
		}
		selected.clear();
		for (Scripts.Stack stack : stacks) {
			stack.x = stack.x - minX + baseX;
			stack.y = stack.y - minY + baseY;
			Scripts.current().stacks.add(stack);
			selected.add(stack);
		}
		setStatus("已貼上 " + blocks + " 塊積木");
	}

	private void deleteSelection() {
		if (selected.isEmpty()) {
			layoutWorkspace();
			Node node = inWorkspace(lastMouseX, lastMouseY) ? nodeAtWorkspace(lx(lastMouseX), ly(lastMouseY)) : null;
			if (node != null) {
				deleteBlock(node);
			}
			return;
		}
		int count = selected.size();
		Scripts.current().stacks.removeIf(selected::contains);
		selected.clear();
		setStatus("已刪除 " + count + " 組積木，按 Ctrl+Z 可以復原");
	}

	private void selectAll() {
		selected.clear();
		selected.addAll(Scripts.current().stacks);
	}

	private void finishSelect() {
		selecting = false;
		if (Math.abs(selectToX - selectX) < 3 && Math.abs(selectToY - selectY) < 3) {
			return;
		}
		double x1 = lx(Math.min(selectX, selectToX));
		double y1 = ly(Math.min(selectY, selectToY));
		double x2 = lx(Math.max(selectX, selectToX));
		double y2 = ly(Math.max(selectY, selectToY));
		layoutWorkspace();
		for (Placed p : placed) {
			if (p.x() < x2 && p.x() + p.w() > x1 && p.y() < y2 && p.y() + p.h() > y1) {
				selected.add(p.stack());
			}
		}
	}

	private void setZoom(double next, double anchorX, double anchorY) {
		next = Math.clamp(next, MIN_ZOOM, MAX_ZOOM);
		double wx = (anchorX - areaX()) / zoom + panX;
		double wy = (anchorY - areaY()) / zoom + panY;
		zoom = next;
		panX = wx - (anchorX - areaX()) / zoom;
		panY = wy - (anchorY - areaY()) / zoom;
	}

	private void openContextMenu(double mx, double my) {
		layoutWorkspace();
		Node node = nodeAtWorkspace(lx(mx), ly(my));
		menuX = (int) mx;
		menuY = (int) my;
		Placed owner = node == null ? null : placedOf(node);
		Scripts.Note note = node == null ? noteAt(lx(mx), ly(my)) : null;
		if (owner != null && selected.contains(owner.stack()) && selected.size() > 1) {
			menu = List.of(
					new MenuEntry("複製選取的 " + selected.size() + " 組（Ctrl+C）", this::copySelection),
					new MenuEntry("再複製一份（Ctrl+D）", () -> paste(encode(selection()), 20, 20, false)),
					new MenuEntry("刪除選取的 " + selected.size() + " 組（Delete）", this::deleteSelection));
		} else if (node != null) {
			menu = List.of(
					new MenuEntry("再複製一份（Ctrl+D）", () -> duplicate(node)),
					new MenuEntry("複製到剪貼簿（Ctrl+C）", () -> {
						selected.clear();
						lastMouseX = mx;
						lastMouseY = my;
						copySelection();
					}),
					new MenuEntry("刪除積木", () -> deleteBlock(node)),
					new MenuEntry(owner != null && owner.stack().disabled ? "啟用這個腳本" : "停用這個腳本", () -> {
						if (owner != null) {
							owner.stack().disabled = !owner.stack().disabled;
							if (owner.stack().disabled) {
								ScriptEngine.stop(owner.stack());
							}
							setStatus(owner.stack().disabled ? "已停用這個腳本，它不會再被觸發" : "已重新啟用這個腳本");
						}
					}));
		} else if (note != null) {
			menu = List.of(
					new MenuEntry("編輯註解", () -> editNote(note)),
					new MenuEntry("刪除註解", () -> Scripts.current().notes.remove(note)));
		} else {
			menu = List.of(
					new MenuEntry("貼上（Ctrl+V）", () -> paste(minecraft.keyboardHandler.getClipboard(), 0, 0, true)),
					new MenuEntry("新增註解", () -> editNote(null)),
					new MenuEntry("顯示全部積木（Ctrl+1）", this::fitAll),
					new MenuEntry("全選（Ctrl+A）", this::selectAll),
					new MenuEntry("整理積木", this::cleanUp),
					new MenuEntry("復原（Ctrl+Z）", () -> undo(true)),
					new MenuEntry("重設縮放（Ctrl+0）", () -> {
						zoom = 1;
						panX = 0;
						panY = 0;
					}),
					new MenuEntry("使用教學（F1）", () -> helpPage = 0));
		}
	}

	private int menuWidth() {
		return menu.stream().mapToInt(entry -> font.width(entry.label())).max().orElse(0) + 12;
	}

	private int menuLeft() {
		return Math.clamp(menuX, 2, Math.max(2, width - menuWidth() - 2));
	}

	private int menuTop() {
		return Math.clamp(menuY, 2, Math.max(2, height - menu.size() * 14 - 6));
	}

	private Placed placedOf(Node node) {
		for (Placed p : placed) {
			if (view.find(p.nodes(), node.block) != null) {
				return p;
			}
		}
		return null;
	}

	private void setStatus(String message) {
		status = message;
		statusUntil = System.currentTimeMillis() + 4000;
	}

	private void commitEdit() {
		if (editing == null) {
			return;
		}
		String value = editor.getValue();
		if (editing.slot().kind() == ScriptBlocks.SlotKind.NUMBER) {
			value = value.replaceAll("[^0-9.\\-]", "");
		}
		if (editing.index() < editing.block().args.size()) {
			editing.block().args.get(editing.index()).text = value;
		}
		editing = null;
		editor.visible = false;
		slotSuggestions = null;
		setFocused(null);
	}

	private void startEdit(SlotBox slot) {
		commitEdit();
		editing = slot;
		editor.setValue(slot.block().text(slot.index()));
		editor.setX(sx(slot.x() + 3));
		editor.setY(sy(slot.y() + 2));
		editor.setWidth(Math.max(60, (int) (slot.w() * zoom) - 6));
		editor.visible = true;
		setFocused(editor);
		slotSuggestions = null;
		if (slot.block().op.equals("command")) {
			slotSuggestions = new CommandSuggestionBox(this, editor);
		}
	}

	private void openPopup(SlotBox slot) {
		List<String> names = Scripts.names(slot.slot().kind(), Scripts.current());
		List<String> options = names != null ? names : slot.slot().options();
		if (options.isEmpty()) {
			setStatus(switch (slot.slot().kind()) {
				case LIST -> "還沒有清單，先到「變數」分類按「建立清單」";
				case PROC -> "還沒有自訂積木，先到「自訂」分類按「製作新積木」";
				default -> "還沒有變數，先到「變數」分類按「建立變數」";
			});
			return;
		}
		int w = options.stream().mapToInt(font::width).max().orElse(0) + 12;
		int h = options.size() * 12 + 4;
		int top = sy(slot.y());
		int bottom = sy(slot.y() + slot.h());
		int y = bottom + h > height ? top - h : bottom;
		popup = new Popup(slot.block(), slot.index(), List.copyOf(options), sx(slot.x()), y, w);
	}

	private void detach(Node node) {
		if (node.ownerArg != null) {
			node.ownerArg.block = null;
			dragReporter = node.block;
			return;
		}
		List<Scripts.Block> list = node.ownerList;
		if (node.def.isReporter()) {
			dragReporter = node.block;
			list.remove(node.index);
		} else {
			dragChunk = new ArrayList<>(list.subList(node.index, list.size()));
			list.subList(node.index, list.size()).clear();
		}
		Scripts.current().stacks.removeIf(stack -> stack.blocks.isEmpty());
	}

	private void beginDrag(Node node, boolean fromPalette, double mx, double my) {
		dragOffsetX = (int) mx - node.x;
		dragOffsetY = (int) my - node.y;
		dragFromPalette = fromPalette;
		if (fromPalette) {
			Scripts.Block copy = Scripts.copy(node.block);
			if (node.def.isReporter()) {
				dragReporter = copy;
			} else {
				dragChunk = new ArrayList<>(List.of(copy));
			}
		} else {
			detach(node);
		}
	}

	private int chunkHeight(List<Scripts.Block> chunk) {
		return view.layoutList(chunk, 0, 0).height();
	}

	private void collectTargets(List<Node> nodes, List<Target> out, List<Scripts.Block> chunk, int dropX, int dropY, double[] best, Target[] chosen) {
		ScriptBlocks.Def first = chunk.getFirst().def();
		ScriptBlocks.Def last = chunk.getLast().def();
		for (Node node : nodes) {
			if (node.def.isReporter()) {
				continue;
			}
			if (first.shape() != ScriptBlocks.Shape.HAT && !node.def.endsStack()
					&& (!last.endsStack() || node.index + 1 >= node.ownerList.size())) {
				consider(node.x, node.y + node.h, dropX, dropY, new Target(node.ownerList, node.index + 1, null, 0), best, chosen);
			}
			if (node.def.hasBody() && first.shape() != ScriptBlocks.Shape.HAT) {
				if (!last.endsStack() || node.block.body.isEmpty()) {
					consider(node.x + ARM, node.bodyY, dropX, dropY, new Target(node.block.body, 0, null, 0), best, chosen);
				}
				if (node.def.shape() == ScriptBlocks.Shape.C_ELSE && (!last.endsStack() || node.block.elseBody.isEmpty())) {
					consider(node.x + ARM, node.elseY + ELSE_H, dropX, dropY, new Target(node.block.elseBody, 0, null, 0), best, chosen);
				}
			}
			collectTargets(node.body, out, chunk, dropX, dropY, best, chosen);
			collectTargets(node.elseBody, out, chunk, dropX, dropY, best, chosen);
		}
	}

	private void consider(int px, int py, int dropX, int dropY, Target target, double[] best, Target[] chosen) {
		double dx = dropX - px;
		double dy = dropY - py;
		if (Math.abs(dx) > SNAP * 1.6 || Math.abs(dy) > SNAP) {
			return;
		}
		double distance = dx * dx + dy * dy;
		if (distance < best[0]) {
			best[0] = distance;
			chosen[0] = target;
		}
	}

	private Target findTarget(List<Scripts.Block> chunk, int dropX, int dropY) {
		double[] best = { Double.MAX_VALUE };
		Target[] chosen = { null };
		int height = chunkHeight(chunk);
		ScriptBlocks.Def last = chunk.getLast().def();
		for (Placed p : placed) {
			collectTargets(p.nodes(), new ArrayList<>(), chunk, dropX, dropY, best, chosen);
			if (!p.nodes().isEmpty() && !last.endsStack()) {
				ScriptBlocks.Def top = p.nodes().getFirst().def;
				if (top.shape() != ScriptBlocks.Shape.HAT && !top.isReporter()) {
					consider(p.x(), p.y() - height, dropX, dropY, new Target(p.stack().blocks, 0, p.stack(), height), best, chosen);
				}
			}
		}
		return chosen[0];
	}

	private void dropChunk(double screenX, double screenY) {
		List<Scripts.Block> chunk = dragChunk;
		dragChunk = null;
		if (inPalette(screenX, screenY) || screenY < HEADER) {
			return;
		}
		double mx = lx(screenX);
		double my = ly(screenY);
		int dropX = (int) mx - dragOffsetX;
		int dropY = (int) my - dragOffsetY;
		if (dragFromPalette && Scripts.count() + Scripts.count(chunk) > Scripts.MAX_BLOCKS) {
			setStatus("積木數量已達上限 " + Scripts.MAX_BLOCKS + " 塊");
			return;
		}
		layoutWorkspace();
		Target target = findTarget(chunk, dropX, dropY);
		if (target != null) {
			target.list().addAll(Math.min(target.index(), target.list().size()), chunk);
			if (target.stack() != null) {
				target.stack().y -= target.shiftY();
			}
		} else {
			Scripts.current().stacks.add(new Scripts.Stack(toWorkspaceX(dropX), toWorkspaceY(dropY), chunk));
		}
	}

	private void dropReporter(double screenX, double screenY) {
		Scripts.Block reporter = dragReporter;
		dragReporter = null;
		if (inPalette(screenX, screenY) || screenY < HEADER) {
			return;
		}
		double mx = lx(screenX);
		double my = ly(screenY);
		if (dragFromPalette && Scripts.count() + Scripts.count(List.of(reporter)) > Scripts.MAX_BLOCKS) {
			setStatus("積木數量已達上限 " + Scripts.MAX_BLOCKS + " 塊");
			return;
		}
		layoutWorkspace();
		SlotBox target = null;
		for (Placed p : placed) {
			SlotBox slot = view.slotAtAny(p.nodes(), mx, my);
			if (slot != null) {
				target = slot;
			}
		}
		boolean booleanBlock = reporter.def().shape() == ScriptBlocks.Shape.BOOLEAN;
		if (target != null) {
			ScriptBlocks.SlotKind kind = target.slot().kind();
			boolean accepts = kind == ScriptBlocks.SlotKind.TEXT || kind == ScriptBlocks.SlotKind.NUMBER
					|| (kind == ScriptBlocks.SlotKind.BOOLEAN && booleanBlock);
			if (accepts && target.index() < target.block().args.size()) {
				Scripts.Arg arg = target.block().args.get(target.index());
				if (arg.block != null) {
					Scripts.current().stacks.add(new Scripts.Stack(toWorkspaceX(mx) + 20, toWorkspaceY(my) + 20, new ArrayList<>(List.of(arg.block))));
				}
				arg.block = reporter;
				return;
			}
		}
		Scripts.current().stacks.add(new Scripts.Stack(toWorkspaceX(mx - dragOffsetX), toWorkspaceY(my - dragOffsetY), new ArrayList<>(List.of(reporter))));
	}

	private void runStackOf(Node node, double mx, double my) {
		Placed p = placedOf(node);
		if (p == null) {
			return;
		}
		if (p.stack().disabled) {
			setStatus("這個腳本已停用，在它上面按右鍵可以重新啟用");
			return;
		}
		if (p.nodes().size() == 1 && p.nodes().getFirst().def.isReporter()) {
			String op = p.nodes().getFirst().block.op;
			if (op.startsWith("s_gui") || op.equals("s_slot_item") || op.equals("s_hover_slot")) {
				setStatus("介面相關的積木要在遊戲中開著介面時才有值；編輯器開著時一律當作沒有介面。在背包或箱子裡按住 Alt 可以看格子編號");
			}
			bubble = ScriptEngine.evaluate(p.nodes().getFirst().block);
			bubbleX = sx(p.x());
			bubbleY = sy(p.y());
			bubbleUntil = System.currentTimeMillis() + 4000;
			return;
		}
		if (!ScriptEngine.active()) {
			setStatus(Scripts.data().enabled ? "要在輝煌伺服器上才能執行腳本" : "腳本已停用，請先點右上角「已停用」開啟");
			return;
		}
		if (debugMode) {
			if (ScriptEngine.startDebug(p.stack())) {
				setStatus("已暫停在第一塊積木，按 F10 或「下一步」一次執行一塊");
			}
			return;
		}
		if (!ScriptEngine.start(p.stack(), Map.of(), true)) {
			setStatus("同時執行的腳本太多了");
		}
	}

	private void cleanUp() {
		int y = 10;
		for (Scripts.Stack stack : Scripts.current().stacks) {
			stack.x = 10;
			stack.y = y;
			y += view.layoutList(stack.blocks, 0, 0).height() + 16;
		}
		panX = 0;
		panY = 0;
	}

	private void deleteBlock(Node node) {
		if (node.ownerArg != null) {
			node.ownerArg.block = null;
		} else if (node.ownerList != null) {
			node.ownerList.remove(node.index);
		}
		Scripts.current().stacks.removeIf(stack -> stack.blocks.isEmpty());
	}

	private void duplicate(Node node) {
		List<Scripts.Block> source = node.ownerArg != null || node.def.isReporter() ? List.of(node.block) : node.ownerList.subList(node.index, node.ownerList.size());
		List<Scripts.Block> copies = new ArrayList<>();
		for (Scripts.Block block : source) {
			copies.add(Scripts.copy(block));
		}
		if (Scripts.count() + Scripts.count(copies) > Scripts.MAX_BLOCKS) {
			setStatus("積木數量已達上限 " + Scripts.MAX_BLOCKS + " 塊");
			return;
		}
		Scripts.current().stacks.add(new Scripts.Stack(toWorkspaceX(node.x) + 20, toWorkspaceY(node.y) + 20, copies));
	}

	private void setScreen(Screen screen) {
		CommandMenuScreen.setScreen(screen);
	}

	private boolean clickChip(double mx, double my) {
		for (Chip chip : chips()) {
			if (!chip.contains(mx, my)) {
				continue;
			}
			switch (chip.label()) {
				case "▶ 執行" -> {
					if (ScriptEngine.active()) {
						ScriptEngine.onStartPressed();
						setStatus("已觸發「當按下執行時」");
					} else {
						setStatus(Scripts.data().enabled ? "要在輝煌伺服器上才能執行腳本" : "腳本已停用");
					}
				}
				case "停止" -> {
					ScriptEngine.stopAll();
					setStatus("已停止所有腳本");
				}
				case "說明" -> helpPage = 0;
				case "除錯：開", "除錯：關" -> {
					debugMode = !debugMode;
					if (!debugMode) {
						stopDebug();
					}
					setStatus(debugMode ? "除錯模式：點一下腳本，就會停在第一塊積木等你逐步執行" : "已關閉除錯模式");
				}
				case "範本" -> setScreen(new ScriptTemplateScreen(this, this::setStatus));
				case "分享" -> {
					menuX = chip.x();
					menuY = 22;
					menu = List.of(
							new MenuEntry("匯出這個分組（複製代碼）", () -> {
								Scripts.Group group = Scripts.current();
								minecraft.keyboardHandler.setClipboard(Scripts.export(group));
								setStatus("已複製分組「" + group.name + "」的代碼到剪貼簿，可以貼給其他玩家");
							}),
							new MenuEntry("匯入代碼或檔案", () -> setScreen(new ScriptImportScreen(this, this::setStatus))));
				}
				case "視窗：開", "視窗：關" -> {
					Scripts.data().monitor = !Scripts.data().monitor;
					Scripts.save();
					setStatus(Scripts.data().monitor ? "已開啟懸浮視窗，遊玩時會顯示在畫面上，打開聊天欄可拖曳" : "已關閉懸浮視窗");
				}
				default -> {
					Scripts.data().enabled = !Scripts.data().enabled;
					if (!Scripts.data().enabled) {
						ScriptEngine.stopAll();
					}
					Scripts.save();
				}
			}
			return true;
		}
		return false;
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		double mx = event.x();
		double my = event.y();
		if (helpPage >= 0) {
			if (event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
				clickHelp(mx, my);
			}
			return true;
		}
		if (slotSuggestions != null && slotSuggestions.mouseClicked(event)) {
			return true;
		}
		if (event.button() == InputConstants.MOUSE_BUTTON_LEFT && onDivider(mx, my) && menu == null && popup == null) {
			resizingPalette = true;
			return true;
		}
		checkpoint();
		if (editing != null && !(mx >= editor.getX() - 2 && mx < editor.getX() + editor.getWidth() + 2 && my >= editor.getY() - 2 && my < editor.getY() + SLOT_H)) {
			commitEdit();
		}
		if (super.mouseClicked(event, doubleClick)) {
			return true;
		}
		if (menu != null) {
			int w = menuWidth();
			int x = menuLeft();
			int y = menuTop();
			int index = (int) ((my - y - 2) / 14);
			List<MenuEntry> entries = menu;
			menu = null;
			if (mx >= x && mx < x + w && index >= 0 && index < entries.size() && my >= y + 2) {
				entries.get(index).action().run();
			}
			return true;
		}
		if (popup != null) {
			int index = (int) ((my - popup.y() - 2) / 12);
			Popup open = popup;
			popup = null;
			if (mx >= open.x() && mx < open.x() + open.w() && index >= 0 && index < open.options().size() && my >= open.y() + 2) {
				open.block().args.get(open.index()).text = open.options().get(index);
			}
			return true;
		}
		if (my < HEADER) {
			if (event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
				if (Theme.backHit(font, 0, 0, mx, my)) {
					onClose();
				} else {
					clickChip(mx, my);
				}
			}
			return true;
		}
		if (mx >= areaX() && my < areaY()) {
			return clickGroupBar(mx, my, event.button());
		}
		layoutWorkspace();
		layoutPalette();
		if (getFocused() == search && !(mx >= search.getX() && mx < search.getX() + search.getWidth() && my >= search.getY() && my < search.getY() + search.getHeight())) {
			search.setFocused(false);
			setFocused(null);
		}
		if (event.button() == InputConstants.MOUSE_BUTTON_RIGHT && inPalette(mx, my)) {
			for (PaletteItem item : palette) {
				if (item.node() != null && my >= paletteTop() && view.nodeAt(List.of(item.node()), mx, my) != null) {
					String op = item.node().block.op;
					if (op.equals("var_get") || op.equals("list_all")) {
						nameMenu(op.equals("var_get") ? ScriptBlocks.SlotKind.VARIABLE : ScriptBlocks.SlotKind.LIST, item.node().block.text(0), mx, my);
					}
				}
			}
			return true;
		}
		if (event.button() == InputConstants.MOUSE_BUTTON_RIGHT) {
			if (inWorkspace(mx, my)) {
				rightPressed = true;
				rightPanning = false;
				rightX = mx;
				rightY = my;
			}
			return true;
		}
		if (event.button() == InputConstants.MOUSE_BUTTON_MIDDLE) {
			panning = inWorkspace(mx, my);
			return true;
		}
		if (event.button() != InputConstants.MOUSE_BUTTON_LEFT) {
			return false;
		}
		if (mx < CATS) {
			int index = (int) ((my - HEADER - 6) / categoryStep());
			ScriptBlocks.Category[] categories = ScriptBlocks.Category.values();
			if (index >= 0 && index < categories.length) {
				if (!query.isEmpty()) {
					search.setValue("");
					layoutPalette();
				}
				paletteScroll = Math.clamp(categoryOffset(categories[index]), 0, paletteMax());
			}
			return true;
		}
		if (inPalette(mx, my)) {
			for (PaletteItem item : palette) {
				if (my < paletteTop()) {
					continue;
				}
				if (item.button() != null && mx >= CATS + 8 && mx < CATS + 98 && my >= item.y() && my < item.y() + item.h()) {
					paletteButton(item.button());
					return true;
				}
				if (item.node() != null) {
					Node hit = view.nodeAt(List.of(item.node()), mx, my);
					if (hit != null) {
						pressNode = item.node();
						pressFromPalette = true;
						pressX = mx;
						pressY = my;
						return true;
					}
				}
			}
			return true;
		}
		if (debugMode && clickDebug(mx, my)) {
			return true;
		}
		if (inMinimap(mx, my)) {
			minimapDrag = true;
			centerMinimap(mx, my);
			return true;
		}
		double sxPress = mx;
		double syPress = my;
		mx = lx(mx);
		my = ly(my);
		boolean shift = minecraft.hasShiftDown();
		for (Placed p : placed) {
			SlotBox slot = shift ? null : view.slotAt(p.nodes(), mx, my);
			if (slot != null && slot.slot().kind() != ScriptBlocks.SlotKind.BOOLEAN) {
				if (slot.slot().kind() == ScriptBlocks.SlotKind.DROPDOWN || slot.slot().kind() == ScriptBlocks.SlotKind.VARIABLE) {
					openPopup(slot);
				} else {
					startEdit(slot);
				}
				return true;
			}
		}
		Node node = nodeAtWorkspace(mx, my);
		if (node != null) {
			Placed owner = placedOf(node);
			if (shift) {
				if (owner != null && !selected.remove(owner.stack())) {
					selected.add(owner.stack());
				}
				return true;
			}
			if (owner != null && !selected.contains(owner.stack())) {
				selected.clear();
			}
			pressNode = node;
			pressFromPalette = false;
			pressX = sxPress;
			pressY = syPress;
			return true;
		}
		Scripts.Note note = noteAt(mx, my);
		if (note != null) {
			if (doubleClick) {
				menuX = (int) sxPress;
				menuY = (int) syPress;
				editNote(note);
				return true;
			}
			draggingNote = note;
			noteOffsetX = toWorkspaceX(mx) - note.x;
			noteOffsetY = toWorkspaceY(my) - note.y;
			return true;
		}
		if (!shift) {
			selected.clear();
		}
		selecting = true;
		selectX = sxPress;
		selectY = syPress;
		selectToX = sxPress;
		selectToY = syPress;
		return true;
	}

	private Node nodeAtWorkspace(double mx, double my) {
		for (int i = placed.size() - 1; i >= 0; i--) {
			Node node = view.nodeAt(placed.get(i).nodes(), mx, my);
			if (node != null) {
				return node;
			}
		}
		return null;
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
		double mx = event.x();
		double my = event.y();
		if (resizingPalette) {
			Scripts.data().paletteWidth = Math.clamp((int) mx - CATS, MIN_FLYOUT, Math.max(MIN_FLYOUT, width / 2 - CATS));
			return true;
		}
		if (minimapDrag) {
			centerMinimap(mx, my);
			return true;
		}
		if (draggingNote != null) {
			draggingNote.x = Math.clamp(toWorkspaceX(lx(mx)) - noteOffsetX, -5000, 5000);
			draggingNote.y = Math.clamp(toWorkspaceY(ly(my)) - noteOffsetY, -5000, 5000);
			return true;
		}
		if (pressNode != null && (Math.abs(mx - pressX) > 3 || Math.abs(my - pressY) > 3)) {
			Node node = pressNode;
			pressNode = null;
			Placed owner = pressFromPalette ? null : placedOf(node);
			if (owner != null && selected.size() > 1 && selected.contains(owner.stack()) && node.ownerArg == null && node.index == 0 && node.ownerList == owner.stack().blocks) {
				moving = selection();
				movingStart = new int[moving.size() * 2];
				for (int i = 0; i < moving.size(); i++) {
					movingStart[i * 2] = moving.get(i).x;
					movingStart[i * 2 + 1] = moving.get(i).y;
				}
				moveX = pressX;
				moveY = pressY;
				return true;
			}
			selected.clear();
			beginDrag(node, pressFromPalette, pressFromPalette ? pressX : lx(pressX), pressFromPalette ? pressY : ly(pressY));
			return true;
		}
		if (moving != null) {
			for (int i = 0; i < moving.size(); i++) {
				moving.get(i).x = movingStart[i * 2] + (int) Math.round((mx - moveX) / zoom);
				moving.get(i).y = movingStart[i * 2 + 1] + (int) Math.round((my - moveY) / zoom);
			}
			return true;
		}
		if (selecting) {
			selectToX = Math.clamp(mx, areaX(), width);
			selectToY = Math.clamp(my, areaY(), height);
			return true;
		}
		if (rightPressed && !rightPanning && (Math.abs(mx - rightX) > 3 || Math.abs(my - rightY) > 3)) {
			rightPanning = true;
		}
		if (panning || rightPanning) {
			panX -= dragX / zoom;
			panY -= dragY / zoom;
			return true;
		}
		return dragChunk != null || dragReporter != null || super.mouseDragged(event, dragX, dragY);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		boolean handled = release(event);
		settle();
		return handled;
	}

	private boolean release(MouseButtonEvent event) {
		double mx = event.x();
		double my = event.y();
		panning = false;
		if (resizingPalette) {
			resizingPalette = false;
			Scripts.save();
			return true;
		}
		if (minimapDrag || draggingNote != null) {
			minimapDrag = false;
			draggingNote = null;
			return true;
		}
		if (rightPressed && event.button() == InputConstants.MOUSE_BUTTON_RIGHT) {
			rightPressed = false;
			if (!rightPanning) {
				openContextMenu(mx, my);
			}
			rightPanning = false;
			return true;
		}
		if (moving != null) {
			moving = null;
			return true;
		}
		if (selecting) {
			finishSelect();
			return true;
		}
		if (pressNode != null) {
			Node node = pressNode;
			pressNode = null;
			if (!pressFromPalette) {
				runStackOf(node, mx, my);
			}
			return true;
		}
		if (dragChunk != null) {
			dropChunk(mx, my);
			return true;
		}
		if (dragReporter != null) {
			dropReporter(mx, my);
			return true;
		}
		return super.mouseReleased(event);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		if (popup != null || menu != null) {
			return true;
		}
		if (helpPage >= 0) {
			return true;
		}
		if (slotSuggestions != null && slotSuggestions.mouseScrolled(scrollY)) {
			return true;
		}
		if (mouseX < palette()) {
			paletteScroll = Math.clamp(paletteScroll - scrollY * 24, 0, Math.max(0, paletteHeight - (height - paletteTop())));
		} else if (minecraft.hasShiftDown()) {
			panX -= scrollY * 24 / zoom;
		} else {
			if (scrollY != 0) {
				setZoom(zoom * Math.pow(1.1, scrollY), mouseX, mouseY);
			}
			panX -= scrollX * 24 / zoom;
		}
		return true;
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (helpPage >= 0) {
			if (event.isEscape()) {
				helpPage = -1;
			} else if (event.isLeft() && helpPage > 0) {
				helpPage--;
			} else if (event.isRight() || event.isConfirmation()) {
				helpPage = helpPage == ScriptTutorial.TITLES.length - 1 ? -1 : helpPage + 1;
			}
			return true;
		}
		if (search.isFocused()) {
			if (event.isEscape()) {
				search.setValue("");
				search.setFocused(false);
				setFocused(null);
				return true;
			}
			return super.keyPressed(event);
		}
		boolean busy = dragChunk != null || dragReporter != null || moving != null;
		boolean control = event.hasControlDownWithQuirk();
		if (editing == null && !busy && control && event.key() == InputConstants.KEY_Z) {
			undo(!event.hasShiftDown());
			return true;
		}
		if (editing == null && !busy && control && event.key() == InputConstants.KEY_Y) {
			undo(false);
			return true;
		}
		checkpoint();
		boolean handled = key(event, busy, control);
		settle();
		return handled;
	}

	private boolean key(KeyEvent event, boolean busy, boolean control) {
		if (editing == null && !busy && popup == null && menu == null) {
			if (event.key() == InputConstants.KEY_F1) {
				helpPage = 0;
				return true;
			}
			if (event.key() == InputConstants.KEY_F10) {
				ScriptEngine.debugStep();
				return true;
			}
			if (event.key() == InputConstants.KEY_F9) {
				ScriptEngine.debugContinue();
				return true;
			}
			if (control && event.key() == InputConstants.KEY_1) {
				fitAll();
				return true;
			}
			if (control && event.key() == InputConstants.KEY_F) {
				setFocused(search);
				search.setFocused(true);
				return true;
			}
			if (event.isCopy()) {
				copySelection();
				return true;
			}
			if (event.isCut()) {
				if (copySelection()) {
					deleteSelection();
				}
				return true;
			}
			if (event.isPaste()) {
				paste(minecraft.keyboardHandler.getClipboard(), 0, 0, true);
				return true;
			}
			if (event.isSelectAll()) {
				selectAll();
				return true;
			}
			if (control && event.key() == InputConstants.KEY_D) {
				List<Scripts.Stack> stacks = selection();
				if (!stacks.isEmpty()) {
					paste(encode(stacks), 20, 20, false);
				}
				return true;
			}
			if (control && event.key() == InputConstants.KEY_0) {
				zoom = 1;
				panX = 0;
				panY = 0;
				return true;
			}
			if (event.key() == InputConstants.KEY_DELETE || event.key() == InputConstants.KEY_BACKSPACE) {
				deleteSelection();
				return true;
			}
			if (event.isEscape() && !selected.isEmpty()) {
				selected.clear();
				return true;
			}
		}
		if (editing != null) {
			if (slotSuggestions != null && slotSuggestions.keyPressed(event)) {
				return true;
			}
			if (event.isConfirmation()) {
				commitEdit();
				return true;
			}
			if (event.isEscape()) {
				editing = null;
				slotSuggestions = null;
				editor.visible = false;
				setFocused(null);
				return true;
			}
			return super.keyPressed(event);
		}
		if (event.isEscape() && (popup != null || menu != null)) {
			popup = null;
			menu = null;
			return true;
		}
		return super.keyPressed(event);
	}

	@Override
	public void removed() {
		stopDebug();
		commitEdit();
		Scripts.save();
		super.removed();
	}

	@Override
	public void onClose() {
		commitEdit();
		Scripts.save();
		CommandMenuScreen.setScreen(parent);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
