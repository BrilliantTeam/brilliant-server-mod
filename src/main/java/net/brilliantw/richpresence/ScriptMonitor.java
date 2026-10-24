package net.brilliantw.richpresence;

import com.mojang.blaze3d.platform.InputConstants;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

public final class ScriptMonitor {
	private record Rect(int x, int y, int w, int h) {
		boolean contains(double mx, double my) {
			return mx >= x && mx < x + w && my >= y && my < y + h;
		}
	}

	private record Preview(Scripts.Stack stack, ScriptView.Laid laid, float scale, int height) {}

	private record Tab(Rect rect, Scripts.Group group, boolean running) {}

	private record Layout(Rect frame, Scripts.Stack stack, Preview preview, List<String> lines, int controlsY, int previewY, int linesY) {}

	private static final int HEADER = 13;
	private static final int CONTROLS = 16;
	private static final int DEBUG_BAR = 14;
	private static final int LINE = 10;
	private static final int GRIP = 7;
	private static final int MIN_WIDTH = 160;
	private static final int MIN_BODY = 60;
	private static final int MAX_LOG = 12;
	private static final int MAX_VARIABLES = 6;
	private static final long ALERT_MS = 4000;
	private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss");
	private static final Deque<String> LOG = new ArrayDeque<>();
	private static final int MAX_FIELDS = 10;
	private static final java.util.Map<String, String> FIELDS = new java.util.LinkedHashMap<>();
	private static final java.util.Map<String, Double> BARS = new java.util.LinkedHashMap<>();
	private static ScriptView view;
	private static double scroll;
	private static String alert;
	private static long alertAt;
	private static int alertCount;
	private static Scripts.Stack picked;
	private static String tabGroup;
	private static String notice;
	private static long noticeAt;
	private static boolean dragging;
	private static double dragX;
	private static double dragY;
	private static boolean resizing;
	private static double resizeX;
	private static double resizeY;
	private static int resizeWidth;
	private static int resizeHeight;

	private ScriptMonitor() {}

	public static void log(String text) {
		String line = TIME.format(LocalTime.now()) + " " + text.replace('\n', ' ');
		LOG.addFirst(line.length() > 200 ? line.substring(0, 200) : line);
		while (LOG.size() > 30) {
			LOG.removeLast();
		}
	}

	public static void setField(String name, String value) {
		if (FIELDS.containsKey(name) || FIELDS.size() < MAX_FIELDS) {
			FIELDS.put(name, value.length() > 100 ? value.substring(0, 100) : value);
		}
	}

	public static void setBar(String name, double percent) {
		if (BARS.containsKey(name) || BARS.size() < MAX_FIELDS) {
			BARS.put(name, Math.clamp(percent, 0, 100));
		}
	}

	public static void clearFields() {
		FIELDS.clear();
		BARS.clear();
	}

	public static void alert(String text) {
		long now = System.currentTimeMillis();
		boolean repeat = text.equals(alert) && now - alertAt < ALERT_MS;
		alertCount = repeat ? alertCount + 1 : 1;
		alert = text;
		alertAt = now;
		if (!repeat) {
			log("⚠ " + text);
		}
	}

	private static Font font() {
		return Minecraft.getInstance().font;
	}

	private static ScriptView view() {
		if (view == null) {
			view = new ScriptView(font());
		}
		return view;
	}

	private static boolean visible() {
		Scripts.Data data = Scripts.data();
		return data.monitor && data.enabled && RichPresenceModClient.featuresActive() && (!Scripts.activeStacks().isEmpty() || !LOG.isEmpty());
	}

	private static List<String> lines() {
		List<String> lines = new ArrayList<>();
		Scripts.Group group = currentGroup();
		boolean paused = false;
		for (Scripts.Stack stack : group == null ? List.<Scripts.Stack>of() : group.stacks) {
			if (ScriptEngine.paused(stack)) {
				Scripts.Block at = ScriptEngine.currentOf(stack);
				lines.add("\u0004⏸ 已暫停：" + (at == null ? "腳本結尾" : describe(at)));
				paused = true;
			}
		}
		if (paused) {
			lines.add("\u0004" + ScriptEngine.debugKeyName(0) + " 下一步、" + ScriptEngine.debugKeyName(1) + " 繼續（開著介面時也能用）");
		}
		List<String> variables = Scripts.variablesOf(group);
		for (int i = 0; i < Math.min(paused ? variables.size() : MAX_VARIABLES, variables.size()); i++) {
			lines.add("§" + variables.get(i) + " = " + ScriptEngine.variable(group, variables.get(i)));
		}
		FIELDS.forEach((name, value) -> lines.add("\u0003" + name + "：" + value));
		BARS.forEach((name, value) -> lines.add("\u0001" + name + "\u0002" + value));
		for (String name : Scripts.listsOf(group)) {
			List<String> items = ScriptEngine.listValue(group, name);
			lines.add("§清單 " + name + "（" + items.size() + " 項）" + (items.isEmpty() ? "" : "：" + String.join(", ", items)));
		}
		int count = 0;
		for (String entry : LOG) {
			if (count++ >= MAX_LOG) {
				break;
			}
			lines.add(entry);
		}
		if (lines.isEmpty()) {
			lines.add("尚無紀錄");
		}
		return lines;
	}

	private static List<Scripts.Stack> runnable(Scripts.Group group) {
		List<Scripts.Stack> stacks = new ArrayList<>();
		for (Scripts.Stack stack : group.stacks) {
			if (!stack.disabled && !stack.blocks.isEmpty() && !stack.blocks.getFirst().def().isReporter()) {
				stacks.add(stack);
			}
		}
		return stacks;
	}

	private static List<Scripts.Group> groups() {
		List<Scripts.Group> groups = new ArrayList<>();
		for (Scripts.Group group : Scripts.data().groups) {
			if (group.enabled && !runnable(group).isEmpty()) {
				groups.add(group);
			}
		}
		return groups;
	}

	private static boolean running(Scripts.Group group) {
		for (Scripts.Stack stack : group.stacks) {
			if (ScriptEngine.isRunning(stack)) {
				return true;
			}
		}
		return false;
	}

	private static Scripts.Group currentGroup() {
		List<Scripts.Group> groups = groups();
		for (Scripts.Group group : groups) {
			if (group.name.equals(tabGroup)) {
				return group;
			}
		}
		Scripts.Stack spotlight = ScriptEngine.spotlight(System.currentTimeMillis());
		for (Scripts.Group group : groups) {
			if (spotlight != null && group.stacks.contains(spotlight)) {
				return group;
			}
		}
		return groups.isEmpty() ? null : groups.getFirst();
	}

	private static List<Scripts.Stack> candidates() {
		Scripts.Group group = currentGroup();
		return group == null ? List.of() : runnable(group);
	}

	private static List<Tab> tabs(Rect frame, boolean interactive) {
		List<Tab> tabs = new ArrayList<>();
		Font font = font();
		int x = frame.x() + 2;
		int limit = frame.x() + frame.w() - (interactive ? 30 : 4);
		for (Scripts.Group group : groups()) {
			boolean active = running(group);
			String label = font.plainSubstrByWidth(group.name, 60);
			int w = font.width(label) + 8 + (active ? 6 : 0);
			if (x + w > limit) {
				break;
			}
			tabs.add(new Tab(new Rect(x, frame.y() + 1, w, HEADER - 2), group, active));
			x += w + 2;
		}
		return tabs;
	}

	private static Scripts.Stack shown(boolean interactive) {
		List<Scripts.Stack> stacks = candidates();
		if (picked != null && stacks.contains(picked)) {
			return picked;
		}
		picked = null;
		Scripts.Stack spotlight = ScriptEngine.spotlight(System.currentTimeMillis());
		if (spotlight != null && stacks.contains(spotlight)) {
			return spotlight;
		}
		return interactive && !stacks.isEmpty() ? stacks.getFirst() : null;
	}

	static String describe(Scripts.Block block) {
		StringBuilder text = new StringBuilder();
		int slot = 0;
		for (Object part : block.def().parts()) {
			if (!text.isEmpty()) {
				text.append(' ');
			}
			if (part instanceof String label) {
				text.append(label);
			} else {
				Scripts.Arg arg = slot < block.args.size() ? block.args.get(slot) : null;
				text.append(arg != null && arg.block != null ? "(" + describe(arg.block) + ")" : "[" + block.text(slot) + "]");
				slot++;
			}
		}
		return text.toString();
	}

	private static Layout layout(int screenWidth, int screenHeight, boolean interactive) {
		boolean collapsed = Scripts.data().monitorCollapsed;
		int w = Math.clamp(Scripts.data().monitorWidth, MIN_WIDTH, Math.max(MIN_WIDTH, screenWidth - 8));
		int body = Math.clamp(Scripts.data().monitorHeight, MIN_BODY, Math.max(MIN_BODY, screenHeight - HEADER - 8));
		int h = collapsed ? HEADER : HEADER + body;
		int x = Math.clamp(Scripts.data().monitorX, 0, Math.max(0, screenWidth - w));
		int y = Math.clamp(Scripts.data().monitorY, 0, Math.max(0, screenHeight - h));
		Rect frame = new Rect(x, y, w, h);
		if (collapsed) {
			return new Layout(frame, null, null, List.of(), 0, 0, 0);
		}
		Scripts.Stack stack = shown(interactive);
		List<String> lines = lines();
		int controlsY = y + HEADER;
		int top = interactive ? controlsY + CONTROLS + DEBUG_BAR : controlsY;
		int available = y + h - top;
		Preview preview = null;
		int previewH = 0;
		if (stack != null) {
			ScriptView.Laid laid = view().layoutList(stack.blocks, 0, 0);
			float scale = Math.min(1f, (w - 12) / (float) Math.max(1, laid.width()));
			int needed = (int) Math.ceil((laid.height() + ScriptView.NOTCH_H) * scale) + 8;
			int linesWanted = Math.min(lines.size() * LINE + 4, available / 2);
			previewH = Math.max(0, Math.min(needed, available - linesWanted));
			preview = new Preview(stack, laid, scale, previewH);
		}
		return new Layout(frame, stack, preview, lines, controlsY, top, top + previewH);
	}

	private static Rect button(Rect frame, int slot) {
		return new Rect(frame.x() + frame.w() - 13 * (slot + 1), frame.y() + 1, 12, HEADER - 2);
	}

	private static Rect control(Layout layout, int slot) {
		Rect frame = layout.frame();
		int y = layout.controlsY() + 2;
		return switch (slot) {
			case 0 -> new Rect(frame.x() + 3, y, 12, CONTROLS - 4);
			case 1 -> new Rect(frame.x() + 16, y, 12, CONTROLS - 4);
			case 2 -> new Rect(frame.x() + frame.w() - 63, y, 30, CONTROLS - 4);
			default -> new Rect(frame.x() + frame.w() - 32, y, 29, CONTROLS - 4);
		};
	}

	private record Chip(Rect rect, String label, String action, boolean enabled) {}

	private static List<Chip> debugChips(Layout layout) {
		Font font = font();
		Scripts.Stack stack = layout.stack();
		boolean running = stack != null && ScriptEngine.isRunning(stack);
		boolean paused = stack != null && ScriptEngine.paused(stack);
		String first = !running ? "除錯執行" : paused ? "繼續" : "暫停";
		String[][] specs = {
				{first, !running ? "debug" : paused ? "resume" : "pause", String.valueOf(stack != null)},
				{"下一步", "step", String.valueOf(paused)},
				{trace() ? "追蹤：開" : "追蹤：關", "trace", "true"}
		};
		List<Chip> chips = new ArrayList<>();
		int x = layout.frame().x() + 3;
		int y = layout.controlsY() + CONTROLS + 1;
		for (String[] spec : specs) {
			int w = font.width(spec[0]) + 8;
			chips.add(new Chip(new Rect(x, y, w, DEBUG_BAR - 3), spec[0], spec[1], Boolean.parseBoolean(spec[2])));
			x += w + 3;
		}
		return chips;
	}

	private static boolean trace() {
		return ScriptEngine.trace;
	}

	private static Rect grip(Rect frame) {
		return new Rect(frame.x() + frame.w() - GRIP, frame.y() + frame.h() - GRIP, GRIP, GRIP);
	}

	public static void renderHud(Gfx gfx) {
		Minecraft client = Minecraft.getInstance();
		//? if >=26.2 {
		boolean chatOpen = client.gui.screen() instanceof ChatScreen;
		//?} else {
		/*boolean chatOpen = client.options.hideGui || client.screen instanceof ChatScreen;
		*///?}
		if (!chatOpen) {
			render(gfx, client.getWindow().getGuiScaledWidth(), client.getWindow().getGuiScaledHeight(), -1, -1, false);
		}
	}

	public static void renderAlert(Gfx gfx, int screenWidth) {
		long age = System.currentTimeMillis() - alertAt;
		if (alert == null || age > ALERT_MS) {
			return;
		}
		Font font = font();
		String text = alertCount > 1 ? alert + "（×" + alertCount + "）" : alert;
		List<FormattedCharSequence> wrapped = font.split(Component.literal(text), Math.min(300, screenWidth - 40));
		int w = wrapped.stream().mapToInt(font::width).max().orElse(0) + 24;
		int h = wrapped.size() * 10 + 8;
		int x = (screenWidth - w) / 2;
		int slide = age < 150 ? (int) ((150 - age) / 10) : 0;
		int y = 6 - slide;
		double fade = age > ALERT_MS - 500 ? (ALERT_MS - age) / 500.0 : 1;
		int a = (int) (fade * 0xE0);
		gfx.fill(x, y, x + w, y + h, a << 24 | 0x7A1F1F);
		gfx.fill(x, y, x + 3, y + h, (int) (fade * 0xFF) << 24 | 0xFF5A5A);
		gfx.fill(x + 3, y + h - 1, x + 3 + (int) ((w - 3) * (1 - age / (double) ALERT_MS)), y + h, (int) (fade * 0xFF) << 24 | 0xFF5A5A);
		int textColor = Math.max(4, (int) (fade * 0xFF)) << 24 | 0xFFFFFF;
		gfx.text(font, "⚠", x + 7, y + 4, textColor);
		for (int i = 0; i < wrapped.size(); i++) {
			gfx.text(font, wrapped.get(i), x + 17, y + 4 + i * 10, textColor);
		}
	}

	public static void render(Gfx gfx, int screenWidth, int screenHeight, int mouseX, int mouseY, boolean interactive) {
		if (RichPresenceModClient.featuresActive()) {
			renderAlert(gfx, screenWidth);
		}
		if (!visible()) {
			return;
		}
		Font font = font();
		Layout layout = layout(screenWidth, screenHeight, interactive);
		Rect frame = layout.frame();
		gfx.fill(frame.x(), frame.y(), frame.x() + frame.w(), frame.y() + frame.h(), 0xC0101418);
		gfx.fill(frame.x(), frame.y(), frame.x() + frame.w(), frame.y() + HEADER, Theme.HEADER);
		gfx.fill(frame.x(), frame.y() + HEADER - 1, frame.x() + frame.w(), frame.y() + HEADER, 0xFFFFAB19);
		List<Tab> tabs = tabs(frame, interactive);
		Scripts.Group current = currentGroup();
		if (tabs.isEmpty()) {
			gfx.label(font, "腳本", frame.x() + 4, frame.y(), HEADER, Theme.TEXT_SECONDARY);
		}
		for (Tab tab : tabs) {
			Rect r = tab.rect();
			boolean selected = tab.group() == current;
			boolean hovered = interactive && r.contains(mouseX, mouseY);
			gfx.fill(r.x(), r.y(), r.x() + r.w(), r.y() + r.h() + (selected ? 1 : 0), selected ? 0xFF2E3740 : hovered ? Theme.CHIP_HOVER : 0x60303840);
			if (selected) {
				gfx.fill(r.x(), r.y(), r.x() + r.w(), r.y() + 1, 0xFFFFAB19);
			}
			int tx = r.x() + 4;
			if (tab.running()) {
				double pulse = 0.6 + 0.4 * Math.sin(System.currentTimeMillis() / 160.0);
				int dot = (int) (pulse * 255) << 24 | Theme.SUCCESS & 0xFFFFFF;
				gfx.fill(tx, r.y() + r.h() / 2 - 2, tx + 4, r.y() + r.h() / 2 + 2, dot);
				tx += 6;
			}
			gfx.label(font, font.plainSubstrByWidth(tab.group().name, 60), tx, r.y(), r.h(), selected ? Theme.TEXT : Theme.TEXT_SECONDARY);
		}
		if (interactive) {
			for (int slot = 0; slot < 2; slot++) {
				Rect b = button(frame, slot);
				gfx.fill(b.x(), b.y(), b.x() + b.w(), b.y() + b.h(), b.contains(mouseX, mouseY) ? Theme.CHIP_HOVER : Theme.CHIP);
				(slot == 0 ? Icons.CLOSE : Icons.MINUS).drawCentered(gfx, b.x(), b.y(), b.w(), b.h(), Theme.TEXT);
			}
		}
		if (Scripts.data().monitorCollapsed) {
			return;
		}
		if (interactive) {
			drawControls(gfx, font, layout, mouseX, mouseY);
		}
		if (layout.preview() != null && layout.preview().height() > 8) {
			drawPreview(gfx, layout.preview(), frame.x(), frame.w(), layout.previewY());
			gfx.fill(frame.x() + 4, layout.linesY(), frame.x() + frame.w() - 4, layout.linesY() + 1, 0x40FFFFFF);
		}
		int y = layout.linesY() + 2;
		int bottom = frame.y() + frame.h() - 2;
		for (String line : layout.lines()) {
			if (y + LINE > bottom) {
				break;
			}
			if (line.startsWith("\u0001")) {
				String[] bar = line.substring(1).split("\u0002", 2);
				double percent = ScriptEngine.number(bar[1]);
				String label = font.plainSubstrByWidth(bar[0], frame.w() / 3);
				gfx.text(font, label, frame.x() + 4, y + 1, Theme.TEXT);
				int bx = frame.x() + 8 + font.width(label);
				int bw = frame.x() + frame.w() - 34 - bx;
				gfx.fill(bx, y + 2, bx + bw, y + 8, 0x60FFFFFF);
				gfx.fill(bx, y + 2, bx + (int) (bw * percent / 100), y + 8, 0xFF59C059);
				gfx.text(font, Math.round(percent) + "%", bx + bw + 3, y + 1, Theme.TEXT_SECONDARY);
				y += LINE;
				continue;
			}
			if (line.startsWith("\u0003")) {
				gfx.text(font, font.plainSubstrByWidth(line.substring(1), frame.w() - 8), frame.x() + 4, y + 1, 0xFF4C97FF);
				y += LINE;
				continue;
			}
			if (line.startsWith("\u0004")) {
				gfx.text(font, font.plainSubstrByWidth(line.substring(1), frame.w() - 8), frame.x() + 4, y + 1, 0xFFFFD43B);
				y += LINE;
				continue;
			}
			boolean variable = line.startsWith("§");
			boolean warning = line.length() > 9 && line.charAt(9) == '⚠';
			String text = variable ? line.substring(1) : line;
			gfx.text(font, font.plainSubstrByWidth(text, frame.w() - 8), frame.x() + 4, y + 1, variable ? 0xFFFF8C1A : warning ? 0xFFFF7A7A : Theme.TEXT);
			y += LINE;
		}
		if (interactive) {
			Rect g = grip(frame);
			int gripColor = resizing || g.contains(mouseX, mouseY) ? Theme.ACCENT : Theme.TEXT_DIM;
			for (int i = 0; i < 3; i++) {
				int d = i * 2 + 1;
				gfx.fill(g.x() + g.w() - d - 1, g.y() + g.h() - 2, g.x() + g.w() - d, g.y() + g.h() - 1, gripColor);
				gfx.fill(g.x() + g.w() - 2, g.y() + g.h() - d - 1, g.x() + g.w() - 1, g.y() + g.h() - d, gripColor);
			}
		}
	}

	private static void chip(Gfx gfx, Rect r, boolean hovered, boolean enabled) {
		gfx.fill(r.x(), r.y(), r.x() + r.w(), r.y() + r.h(), !enabled ? 0x60303840 : hovered ? Theme.CHIP_HOVER : Theme.CHIP);
	}

	private static void triangle(Gfx gfx, Rect r, boolean right, int color) {
		int cx = r.x() + r.w() / 2;
		int cy = r.y() + r.h() / 2;
		for (int i = 0; i < 4; i++) {
			int col = right ? cx - 2 + i : cx + 1 - i;
			gfx.fill(col, cy - 3 + i, col + 1, cy + 4 - i, color);
		}
	}

	private static void drawControls(Gfx gfx, Font font, Layout layout, int mouseX, int mouseY) {
		Rect frame = layout.frame();
		gfx.fill(frame.x(), layout.controlsY(), frame.x() + frame.w(), layout.controlsY() + CONTROLS, 0x60000000);
		Scripts.Stack stack = layout.stack();
		boolean many = candidates().size() > 1;
		for (int slot = 0; slot < 2; slot++) {
			Rect r = control(layout, slot);
			chip(gfx, r, r.contains(mouseX, mouseY), many);
			triangle(gfx, r, slot == 1, many ? Theme.TEXT : Theme.TEXT_DIM);
		}
		boolean running = stack != null && ScriptEngine.isRunning(stack);
		Rect run = control(layout, 2);
		Rect stop = control(layout, 3);
		chip(gfx, run, run.contains(mouseX, mouseY), stack != null);
		chip(gfx, stop, stop.contains(mouseX, mouseY), running);
		Icons.PLAY.drawCentered(gfx, run.x(), run.y(), run.w(), run.h(), stack != null ? Theme.SUCCESS : Theme.TEXT_DIM);
		int sx = stop.x() + stop.w() / 2 - 3;
		int sy = stop.y() + stop.h() / 2 - 3;
		gfx.fill(sx, sy, sx + 6, sy + 6, running ? Theme.DANGER : Theme.TEXT_DIM);
		int nameX = control(layout, 1).x() + 16;
		int nameW = run.x() - nameX - 4;
		boolean showNotice = notice != null && System.currentTimeMillis() - noticeAt < 2500;
		String name = showNotice ? notice : stack == null ? "沒有可執行的腳本" : describe(stack.blocks.getFirst());
		gfx.label(font, font.plainSubstrByWidth(name, nameW), nameX, layout.controlsY(), CONTROLS, showNotice ? 0xFFFF7A7A : running ? Theme.SUCCESS : Theme.TEXT);
		int barY = layout.controlsY() + CONTROLS;
		gfx.fill(frame.x(), barY, frame.x() + frame.w(), barY + DEBUG_BAR, 0x40000000);
		int end = frame.x() + 3;
		for (Chip chip : debugChips(layout)) {
			Rect r = chip.rect();
			chip(gfx, r, chip.enabled() && r.contains(mouseX, mouseY), chip.enabled());
			boolean on = chip.action().equals("trace") && trace() || chip.action().equals("resume");
			gfx.label(font, chip.label(), r.x() + 4, r.y(), r.h(), !chip.enabled() ? Theme.TEXT_DIM : on ? 0xFFFFAB19 : Theme.TEXT);
			end = r.x() + r.w() + 6;
		}
		String hint = "點預覽中的積木設中斷點";
		if (end + font.width(hint) < frame.x() + frame.w() - 4) {
			gfx.label(font, hint, frame.x() + frame.w() - 4 - font.width(hint), barY + 1, DEBUG_BAR - 3, Theme.TEXT_DIM);
		}
	}

	private static void drawPreview(Gfx gfx, Preview preview, int x, int w, int y) {
		ScriptView view = view();
		float scale = preview.scale();
		int area = preview.height() - 8;
		double content = (preview.laid().height() + ScriptView.NOTCH_H) * scale;
		double target = 0;
		Scripts.Block current = ScriptEngine.currentOf(preview.stack());
		ScriptView.Node focus = current == null ? null : view.find(preview.laid().nodes(), current);
		if (focus != null && content > area) {
			target = Math.clamp(focus.y * scale - area / 2.0, 0, content - area);
		}
		scroll += (target - scroll) * 0.2;
		gfx.scissor(x + 2, y + 2, x + w - 2, y + preview.height() - 2);
		gfx.push(x + 6, (float) (y + 4 - scroll), scale);
		for (ScriptView.Node node : preview.laid().nodes()) {
			view.drawNode(gfx, node);
		}
		gfx.pop();
		gfx.endScissor();
	}

	private static void cycle(int step) {
		List<Scripts.Stack> stacks = candidates();
		if (stacks.size() < 2) {
			return;
		}
		int index = stacks.indexOf(shown(true));
		picked = stacks.get(Math.floorMod(index + step, stacks.size()));
		scroll = 0;
	}

	private static void say(String text) {
		notice = text;
		noticeAt = System.currentTimeMillis();
	}

	public static boolean mouseClicked(ChatScreen screen, double mouseX, double mouseY, int button) {
		if (!visible() || button != InputConstants.MOUSE_BUTTON_LEFT) {
			return false;
		}
		Layout layout = layout(screen.width, screen.height, true);
		Rect frame = layout.frame();
		if (!frame.contains(mouseX, mouseY)) {
			return false;
		}
		if (button(frame, 0).contains(mouseX, mouseY)) {
			Scripts.data().monitor = false;
			Scripts.save();
			return true;
		}
		if (button(frame, 1).contains(mouseX, mouseY)) {
			Scripts.data().monitorCollapsed = !Scripts.data().monitorCollapsed;
			Scripts.save();
			return true;
		}
		for (Tab tab : tabs(frame, true)) {
			if (tab.rect().contains(mouseX, mouseY)) {
				tabGroup = tab.group().name;
				picked = null;
				scroll = 0;
				return true;
			}
		}
		if (mouseY < frame.y() + HEADER) {
			dragging = true;
			dragX = mouseX - frame.x();
			dragY = mouseY - frame.y();
			return true;
		}
		if (grip(frame).contains(mouseX, mouseY)) {
			resizing = true;
			resizeX = mouseX;
			resizeY = mouseY;
			resizeWidth = frame.w();
			resizeHeight = frame.h() - HEADER;
			return true;
		}
		Scripts.Stack stack = layout.stack();
		for (Chip chip : debugChips(layout)) {
			if (!chip.rect().contains(mouseX, mouseY)) {
				continue;
			}
			if (chip.enabled()) {
				debugAction(chip.action(), stack);
			}
			return true;
		}
		Preview preview = layout.preview();
		if (preview != null && mouseY >= layout.previewY() && mouseY < layout.previewY() + preview.height()) {
			double lx = (mouseX - frame.x() - 6) / preview.scale();
			double ly = (mouseY - layout.previewY() - 4 + scroll) / preview.scale();
			ScriptView.Node node = view().nodeAt(preview.laid().nodes(), lx, ly);
			if (node != null && !node.def.isReporter() && node.def.shape() != ScriptBlocks.Shape.HAT) {
				boolean set = ScriptEngine.toggleBreakpoint(node.block);
				say(set ? "已設中斷點" : "已移除中斷點");
				log((set ? "● 中斷點：" : "○ 移除中斷點：") + describe(node.block));
			}
			return true;
		}
		if (control(layout, 0).contains(mouseX, mouseY)) {
			cycle(-1);
		} else if (control(layout, 1).contains(mouseX, mouseY)) {
			cycle(1);
		} else if (control(layout, 2).contains(mouseX, mouseY) && stack != null) {
			picked = stack;
			if (!ScriptEngine.active()) {
				say(Scripts.data().enabled ? "要在輝煌伺服器上才能執行" : "腳本已停用");
			} else if (ScriptEngine.start(stack, Map.of(), true)) {
				scroll = 0;
			}
		} else if (control(layout, 3).contains(mouseX, mouseY) && stack != null && ScriptEngine.isRunning(stack)) {
			ScriptEngine.stop(stack);
			log("已停止：" + describe(stack.blocks.getFirst()));
		}
		return true;
	}

	private static void debugAction(String action, Scripts.Stack stack) {
		switch (action) {
			case "trace" -> {
				ScriptEngine.trace = !ScriptEngine.trace;
				say(ScriptEngine.trace ? "已開啟執行追蹤，每執行一塊都會記在下方" : "已關閉執行追蹤");
			}
			case "debug" -> {
				picked = stack;
				if (!ScriptEngine.active()) {
					say(Scripts.data().enabled ? "要在輝煌伺服器上才能執行" : "腳本已停用");
				} else if (ScriptEngine.startDebug(stack)) {
					scroll = 0;
					say("已暫停在第一塊，按「下一步」一次執行一塊");
				}
			}
			case "pause" -> ScriptEngine.pause(stack);
			case "resume" -> ScriptEngine.resume(stack);
			case "step" -> ScriptEngine.step(stack);
			default -> {
			}
		}
	}

	public static boolean mouseDragged(double mouseX, double mouseY) {
		if (resizing) {
			Minecraft client = Minecraft.getInstance();
			int screenWidth = client.getWindow().getGuiScaledWidth();
			int screenHeight = client.getWindow().getGuiScaledHeight();
			Scripts.data().monitorWidth = Math.clamp(resizeWidth + (int) (mouseX - resizeX), MIN_WIDTH, Math.max(MIN_WIDTH, screenWidth - 8));
			Scripts.data().monitorHeight = Math.clamp(resizeHeight + (int) (mouseY - resizeY), MIN_BODY, Math.max(MIN_BODY, screenHeight - HEADER - 8));
			return true;
		}
		if (!dragging) {
			return false;
		}
		Scripts.data().monitorX = (int) (mouseX - dragX);
		Scripts.data().monitorY = (int) (mouseY - dragY);
		return true;
	}

	public static boolean mouseReleased() {
		if (!dragging && !resizing) {
			return false;
		}
		dragging = false;
		resizing = false;
		Scripts.save();
		return true;
	}
}
