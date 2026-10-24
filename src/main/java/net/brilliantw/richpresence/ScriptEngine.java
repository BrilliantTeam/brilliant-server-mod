package net.brilliantw.richpresence;

import com.mojang.blaze3d.platform.InputConstants;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Predicate;
import java.util.regex.Pattern;
//? if >=26.1 {
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
//?} else {
/*import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
*///?}
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.brilliantw.richpresence.mixin.KeyMappingAccessor;
import net.brilliantw.richpresence.mixin.KeyboardHandlerInvoker;

public final class ScriptEngine {
	static final class Frame {
		final List<Scripts.Block> list;
		int index;
		int counter;
		int total;
		String[] args;
		List<String> items;

		Frame(List<Scripts.Block> list, int index) {
			this.list = list;
			this.index = index;
		}
	}

	static final class Run {
		final Scripts.Stack stack;
		final Map<String, String> context;
		final Deque<Frame> frames = new ArrayDeque<>();
		long waitUntil;
		long waitStart;
		Scripts.Block waitBlock;
		Scripts.Block current;
		Scripts.Arg waitCondition;
		boolean done;
		boolean debug;
		int stepsRequested;
		Scripts.Block resumed;
		Scripts.Block retryBlock;
		long retryStart;
		int moveStage;
		int moveFrom;
		int moveTo;
		Scripts.Block turnBlock;
		float targetYaw;
		float targetPitch;
		long waitDeadline;
		boolean timedOut;
		boolean asking;
		Scripts.Block walkBlock;
		long walkStart;
		double walkBest;
		long walkProgressAt;
		java.util.Set<Integer> movedSlots = new java.util.HashSet<>();
		Scripts.Group group;

		Run(Scripts.Stack stack, Map<String, String> context) {
			this.stack = stack;
			this.context = context;
			this.group = Scripts.groupOf(stack);
		}
	}

	private record Outgoing(Scripts.Block block, boolean command, String text) {}

	public static final int MAX_RUNS = 32;
	public static final int MAX_PER_STACK = 4;
	public static final int STEPS_PER_TICK = 300;
	public static final long SEND_INTERVAL_MS = 3000;
	public static final long DUPLICATE_MS = 10000;
	public static final int MAX_QUEUE = 5;
	public static final int MIN_TIMER_SECONDS = 5;

	private static final List<Run> RUNS = new ArrayList<>();
	public static final int MAX_CALL_DEPTH = 60;
	public static final int MAX_HOLD_SECONDS = 600;
	public static final long GUI_WAIT_MS = 2000;
	public static final long INPUT_GAP_MS = 100;
	public static final float TURN_SPEED = 30f;
	private record Hold(Run owner, long until) {}
	private static final Map<KeyMapping, Hold> HELD = new IdentityHashMap<>();
	private static boolean valuesDirty;
	private static long valuesSavedAt;
	private static float lastHealth = -1;
	private static boolean wasFull;
	private static java.util.Set<String> online;
	private static String[] channelHeader;
	private static final java.util.Set<String> LOOP_OPS = java.util.Set.of("repeat", "forever", "repeat_until", "for_each");
	private static final Map<Scripts.Block, Long> COOLDOWNS = new IdentityHashMap<>();
	private static final Map<Scripts.Stack, String> DAILY = new IdentityHashMap<>();
	private static final Map<Scripts.Stack, Boolean> AREAS = new IdentityHashMap<>();
	private static final Map<Scripts.Stack, Integer> ITEM_COUNTS = new IdentityHashMap<>();
	private static final boolean[] KEY_DOWN = new boolean[5];
	private static net.minecraft.client.gui.screens.Screen lastScreen;
	private static String lastScreenTitle = "";
	private static boolean wasDead;
	private static String lastDimension;
	private static String lastHeld;
	private static Boolean lastDay;
	private static String lastAnnouncement;
	private static long announcementCheckedAt;
	private static String lastAnswer = "";
	private static String lastChoice = "";
	private static final double HEARING = 16;
	private static final Map<String, String> HEARD = Map.ofEntries(
			Map.entry("釣魚浮標濺水", "entity.fishing_bobber.splash"), Map.entry("爆炸", "explode"), Map.entry("苦力怕引信", "entity.creeper.primed"),
			Map.entry("雷聲", "lightning_bolt.thunder"), Map.entry("物品撿起", "entity.item.pickup"), Map.entry("經驗球", "experience_orb.pickup"),
			Map.entry("升級", "player.levelup"), Map.entry("村民", "entity.villager"), Map.entry("箱子開啟", "chest.open"),
			Map.entry("鐘聲", "block.bell"), Map.entry("傳送", "teleport"), Map.entry("受傷", "hurt"),
			Map.entry("附魔", "enchantment_table.use"), Map.entry("鐵砧", "block.anvil"));
	private static final Map<Scripts.Stack, Boolean> TEXT_SEEN = new IdentityHashMap<>();
	private static final java.util.Set<String> TEXT_EVENTS = java.util.Set.of("ev_title", "ev_actionbar", "ev_bossbar", "ev_sidebar", "ev_bossbar_gone");
	private static int onlineTick;
	private static final Deque<Outgoing> OUTBOX = new ArrayDeque<>();
	private static final Map<Scripts.Stack, Long> TIMERS = new IdentityHashMap<>();
	private static final Map<Scripts.Block, Long> FLASH = new IdentityHashMap<>();
	private static final Map<Scripts.Block, Long> ERRORS = new IdentityHashMap<>();
	private static Scripts.Stack lastStack;
	private static long lastStackAt;
	private static final KeyMapping[] KEYS = new KeyMapping[ScriptBlocks.KEYS.size()];
	private static final KeyMapping[] DEBUG_KEYS = new KeyMapping[2];
	private static final boolean[] DEBUG_HELD = new boolean[2];
	private static final java.util.Set<Scripts.Block> BREAKPOINTS = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
	static boolean trace;
	private static long lastSent;
	private static String lastText = "";
	private static boolean echoing;
	private static long timerStart = System.currentTimeMillis();
	private static final List<String> WEEKDAYS = List.of("一", "二", "三", "四", "五", "六", "日");
	private static final Map<String, String> CHANNEL_PREFIX = Map.of("聊天", "!", "交易", "?", "區域", "-", "公會", "+", "領地", "*");

	private ScriptEngine() {}

	public static void register() {
		KeyMapping.Category category = KeyMapping.Category.register(Identifier.fromNamespaceAndPath("brilliant-server-mod", "scripts"));
		for (int i = 0; i < KEYS.length; i++) {
			KeyMapping key = new KeyMapping("key.brilliant-server-mod.script_" + (i + 1), InputConstants.UNKNOWN.getValue(), category);
			//? if >=26.1 {
			KEYS[i] = KeyMappingHelper.registerKeyMapping(key);
			//?} else {
			/*KEYS[i] = KeyBindingHelper.registerKeyBinding(key);
			*///?}
		}
		String[] names = {"script_debug_step", "script_debug_continue"};
		int[] defaults = {InputConstants.KEY_F6, InputConstants.KEY_F7};
		for (int i = 0; i < DEBUG_KEYS.length; i++) {
			KeyMapping key = new KeyMapping("key.brilliant-server-mod." + names[i], defaults[i], category);
			//? if >=26.1 {
			DEBUG_KEYS[i] = KeyMappingHelper.registerKeyMapping(key);
			//?} else {
			/*DEBUG_KEYS[i] = KeyBindingHelper.registerKeyBinding(key);
			*///?}
		}
	}

	static String debugKeyName(int index) {
		KeyMapping key = DEBUG_KEYS[index];
		return key == null || key.isUnbound() ? "未設定" : key.getTranslatedKeyMessage().getString();
	}

	private static void debugKeys(Minecraft client) {
		for (int i = 0; i < DEBUG_KEYS.length; i++) {
			KeyMapping mapping = DEBUG_KEYS[i];
			if (mapping == null) {
				continue;
			}
			while (mapping.consumeClick()) {
			}
			InputConstants.Key bound = InputConstants.getKey(mapping.saveString());
			boolean down = bound != InputConstants.UNKNOWN && bound.getType() != InputConstants.Type.MOUSE && CommandKeys.keyDown(client, bound.getValue());
			if (down && !DEBUG_HELD[i]) {
				Run run = debugRun();
				if (run != null && i == 0) {
					run.stepsRequested++;
				} else if (run != null) {
					resume(run);
				}
			}
			DEBUG_HELD[i] = down;
		}
	}

	public static boolean isBreakpoint(Scripts.Block block) {
		return BREAKPOINTS.contains(block);
	}

	public static boolean toggleBreakpoint(Scripts.Block block) {
		if (BREAKPOINTS.remove(block)) {
			return false;
		}
		BREAKPOINTS.add(block);
		return true;
	}

	private static Run runOf(Scripts.Stack stack) {
		for (Run run : RUNS) {
			if (!run.done && run.stack == stack) {
				return run;
			}
		}
		return null;
	}

	public static boolean paused(Scripts.Stack stack) {
		Run run = runOf(stack);
		return run != null && run.debug;
	}

	public static void pause(Scripts.Stack stack) {
		Run run = runOf(stack);
		if (run != null && !run.debug) {
			run.debug = true;
			run.current = next(run);
		}
	}

	public static void step(Scripts.Stack stack) {
		Run run = runOf(stack);
		if (run != null && run.debug) {
			run.stepsRequested++;
		}
	}

	public static void resume(Scripts.Stack stack) {
		Run run = runOf(stack);
		if (run != null && run.debug) {
			resume(run);
		}
	}

	private static void resume(Run run) {
		run.debug = false;
		run.resumed = run.current;
	}

	static boolean active() {
		return Scripts.data().enabled && Minecraft.getInstance() != null && Minecraft.getInstance().player != null && RichPresenceModClient.featuresActive();
	}

	public static long flashAge(Scripts.Block block, long now) {
		Long at = FLASH.get(block);
		return at == null ? -1 : now - at;
	}

	public static long errorAge(Scripts.Block block, long now) {
		Long at = ERRORS.get(block);
		return at == null ? -1 : now - at;
	}

	public static boolean isCurrent(Scripts.Block block) {
		for (Run run : RUNS) {
			if (!run.done && run.current == block) {
				return true;
			}
		}
		return false;
	}

	public static double waitProgress(Scripts.Block block, long now) {
		for (Run run : RUNS) {
			if (!run.done && run.waitBlock == block && run.waitUntil > now && run.waitUntil > run.waitStart) {
				return Math.clamp((now - run.waitStart) / (double) (run.waitUntil - run.waitStart), 0, 1);
			}
		}
		return -1;
	}

	public static String loopLabel(Scripts.Block block) {
		for (Run run : RUNS) {
			if (run.done) {
				continue;
			}
			boolean top = true;
			for (Frame frame : run.frames) {
				if (!top && frame.index < frame.list.size() && frame.list.get(frame.index) == block) {
					return switch (block.op) {
						case "repeat" -> (frame.total - frame.counter + 1) + "/" + frame.total;
						case "for_each" -> (frame.counter + 1) + "/" + frame.total;
						case "forever", "repeat_until" -> "迴圈中";
						default -> null;
					};
				}
				top = false;
			}
		}
		return null;
	}

	public static Scripts.Block currentOf(Scripts.Stack stack) {
		for (Run run : RUNS) {
			if (!run.done && run.stack == stack) {
				return run.current;
			}
		}
		return null;
	}

	public static Scripts.Stack spotlight(long now) {
		for (Run run : RUNS) {
			if (!run.done && Scripts.activeStacks().contains(run.stack)) {
				return run.stack;
			}
		}
		return lastStack != null && now - lastStackAt < 6000 && Scripts.activeStacks().contains(lastStack) ? lastStack : null;
	}

	private static void fail(Scripts.Block block, String message) {
		if (block != null) {
			ERRORS.put(block, System.currentTimeMillis());
		}
		ScriptMonitor.alert(message);
	}

	public static int runningCount() {
		return (int) RUNS.stream().filter(run -> !run.done).count();
	}

	public static String variable(String name) {
		return Scripts.data().values.getOrDefault(name, "0");
	}

	public static String variable(Scripts.Group group, String name) {
		return Scripts.data().values.getOrDefault(Scripts.key(group, name), "0");
	}

	public static List<String> listValue(Scripts.Group group, String name) {
		return Scripts.data().listValues.getOrDefault(Scripts.listKey(group, name), List.of());
	}

	public static void saveValues() {
		if (valuesDirty) {
			valuesDirty = false;
			valuesSavedAt = System.currentTimeMillis();
			Scripts.save();
		}
	}

	public static boolean startDebug(Scripts.Stack stack) {
		RUNS.forEach(run -> {
			if (run.debug) {
				run.done = true;
			}
		});
		if (!start(stack, Map.of(), true)) {
			return false;
		}
		Run run = RUNS.getLast();
		run.debug = true;
		run.current = next(run);
		return true;
	}

	static Run debugRun() {
		for (Run run : RUNS) {
			if (run.debug && !run.done) {
				return run;
			}
		}
		return null;
	}

	public static boolean debugging() {
		return debugRun() != null;
	}

	public static Scripts.Block debugBlock() {
		Run run = debugRun();
		return run == null ? null : run.current;
	}

	public static Scripts.Stack debugStack() {
		Run run = debugRun();
		return run == null ? null : run.stack;
	}

	public static void debugStep() {
		Run run = debugRun();
		if (run != null) {
			run.stepsRequested++;
		}
	}

	public static void debugContinue() {
		Run run = debugRun();
		if (run != null) {
			resume(run);
		}
	}

	public static String debugValue(Scripts.Arg arg) {
		Run run = debugRun();
		try {
			return run == null ? "" : eval(run, arg);
		} catch (RuntimeException e) {
			return "（錯誤）";
		}
	}

	public static String debugWaiting() {
		Run run = debugRun();
		if (run == null) {
			return null;
		}
		if (run.waitCondition != null) {
			return "等待條件成立中";
		}
		return null;
	}

	private static Scripts.Block next(Run run) {
		for (Frame frame : run.frames) {
			if (frame.index < frame.list.size()) {
				return frame.list.get(frame.index);
			}
		}
		return null;
	}

	public static boolean isRunning(Scripts.Stack stack) {
		return RUNS.stream().anyMatch(run -> run.stack == stack && !run.done);
	}

	public static void stop(Scripts.Stack stack) {
		RUNS.forEach(run -> {
			if (run.stack == stack) {
				run.done = true;
			}
		});
	}

	public static void stopAll() {
		RUNS.clear();
		OUTBOX.clear();
		releaseAll();
		saveValues();
	}

	public static void tick(Minecraft client) {
		if (!active()) {
			if (!RUNS.isEmpty() || !OUTBOX.isEmpty()) {
				stopAll();
			}
			for (KeyMapping key : KEYS) {
				while (key != null && key.consumeClick()) {
				}
			}
			return;
		}
		hotkeys(client);
		debugKeys(client);
		for (int i = 0; i < KEYS.length; i++) {
			String index = ScriptBlocks.KEYS.get(i);
			while (KEYS[i] != null && KEYS[i].consumeClick()) {
				if (KeyCombos.matches(KEYS[i])) {
					fire("ev_key", hat -> hat.text(0).equals(index), Map.of());
				}
			}
		}
		long now = System.currentTimeMillis();
		List<Scripts.Stack> active = Scripts.activeStacks();
		for (Scripts.Stack stack : active) {
			if (stack.blocks.isEmpty() || !stack.blocks.getFirst().op.equals("ev_timer")) {
				continue;
			}
			long interval = (long) Math.clamp(number(stack.blocks.getFirst().text(0)), MIN_TIMER_SECONDS, 86400) * 1000;
			Long last = TIMERS.get(stack);
			if (last == null) {
				TIMERS.put(stack, now);
			} else if (now - last >= interval) {
				TIMERS.put(stack, now);
				start(stack, Map.of(), true);
			}
		}
		watchPlayer(client, active);
		watchText(active);
		watchWorld(client, active, now);
		for (Run run : List.copyOf(RUNS)) {
			if (!run.debug) {
				safeStep(run, now, STEPS_PER_TICK);
			} else if (run.stepsRequested > 0) {
				run.stepsRequested--;
				run.waitUntil = 0;
				safeStep(run, now, 1);
				run.current = next(run);
				if (run.current == null && run.frames.stream().allMatch(frame -> frame.index >= frame.list.size())) {
					run.done = true;
				}
			}
		}
		if (valuesDirty && now - valuesSavedAt > 10000) {
			saveValues();
		}
		RUNS.removeIf(run -> run.done || !active.contains(run.stack));
		updateHeld(now);
		flush(now);
	}

	public static void fire(String op, Predicate<Scripts.Block> filter, Map<String, String> context) {
		if (!active()) {
			return;
		}
		for (Scripts.Stack stack : List.copyOf(Scripts.activeStacks())) {
			if (!stack.blocks.isEmpty() && stack.blocks.getFirst().op.equals(op) && filter.test(stack.blocks.getFirst())) {
				start(stack, context, true);
			}
		}
	}

	public static boolean start(Scripts.Stack stack, Map<String, String> context, boolean skipHat) {
		if (stack.blocks.isEmpty()) {
			return false;
		}
		if (RUNS.size() >= MAX_RUNS || RUNS.stream().filter(run -> run.stack == stack && !run.done).count() >= MAX_PER_STACK) {
			fail(stack.blocks.getFirst(), "同一個腳本同時執行太多次，這次觸發已略過（每個最多 " + MAX_PER_STACK + " 個、全部最多 " + MAX_RUNS + " 個）");
			return false;
		}
		lastStack = stack;
		lastStackAt = System.currentTimeMillis();
		FLASH.put(stack.blocks.getFirst(), lastStackAt);
		Run run = new Run(stack, context);
		int index = skipHat && stack.blocks.getFirst().def().shape() == ScriptBlocks.Shape.HAT ? 1 : 0;
		run.frames.push(new Frame(stack.blocks, index));
		RUNS.add(run);
		return true;
	}

	public static String evaluate(Scripts.Block block) {
		try {
			Run run = new Run(new Scripts.Stack(), Map.of());
			run.group = Scripts.current();
			return evalBlock(run, block);
		} catch (RuntimeException e) {
			return "（錯誤）";
		}
	}

	public static void onStartPressed() {
		fire("ev_start", hat -> true, Map.of());
	}

	private static boolean matchesName(Scripts.Block hat, String name) {
		String wanted = hat.text(0).strip();
		return wanted.isEmpty() || wanted.equals("任何人") || wanted.equalsIgnoreCase(name);
	}

	private static void startAll(List<Scripts.Stack> active, String op, Predicate<Scripts.Block> filter, Map<String, String> context) {
		startAll(active, op, filter, context, null);
	}

	private static void startAll(List<Scripts.Stack> active, String op, Predicate<Scripts.Block> filter, Map<String, String> context, Predicate<Scripts.Stack> gate) {
		for (Scripts.Stack stack : active) {
			if (!stack.blocks.isEmpty() && stack.blocks.getFirst().op.equals(op) && filter.test(stack.blocks.getFirst()) && (gate == null || gate.test(stack))) {
				start(stack, context, true);
			}
		}
	}

	private static void watchPlayer(Minecraft client, List<Scripts.Stack> active) {
		LocalPlayer player = client.player;
		if (player == null) {
			return;
		}
		float health = player.getHealth();
		if (lastHealth >= 0 && health < lastHealth) {
			float before = lastHealth;
			startAll(active, "ev_health", hat -> {
				double threshold = number(hat.text(0));
				return before >= threshold && health < threshold;
			}, Map.of("message", format(health)));
			startAll(active, "ev_hurt", hat -> true, Map.of("message", format(Math.round((before - health) * 10) / 10.0)));
		}
		lastHealth = health;
		boolean full = true;
		for (int i = 0; i < Math.min(36, player.getInventory().getContainerSize()); i++) {
			if (player.getInventory().getItem(i).isEmpty()) {
				full = false;
				break;
			}
		}
		if (full && !wasFull) {
			startAll(active, "ev_full", hat -> true, Map.of());
		}
		wasFull = full;
		if (++onlineTick % 20 != 0 || client.getConnection() == null) {
			return;
		}
		java.util.Set<String> names = new java.util.HashSet<>();
		client.getConnection().getListedOnlinePlayers().forEach(info -> names.add(info.getProfile().name()));
		if (online != null) {
			java.util.Set<String> joined = new java.util.HashSet<>(names);
			joined.removeAll(online);
			java.util.Set<String> left = new java.util.HashSet<>(online);
			left.removeAll(names);
			if (joined.size() + left.size() <= 10) {
				for (String name : joined) {
					startAll(active, "ev_player_join", hat -> matchesName(hat, name), Map.of("sender", name, "message", name));
				}
				for (String name : left) {
					startAll(active, "ev_player_leave", hat -> matchesName(hat, name), Map.of("sender", name, "message", name));
				}
			}
		}
		online = names;
	}

	static String findText(String keyword, List<String> texts) {
		String needle = keyword.strip().toLowerCase(Locale.ROOT);
		for (String text : texts) {
			if (!text.isBlank() && (needle.isEmpty() || text.toLowerCase(Locale.ROOT).contains(needle))) {
				return text;
			}
		}
		return null;
	}

	private static List<String> textsFor(String op) {
		return switch (op) {
			case "ev_title" -> List.of(ScreenText.title(), ScreenText.subtitle());
			case "ev_actionbar" -> List.of(ScreenText.actionbar());
			case "ev_bossbar", "ev_bossbar_gone" -> ScreenText.bossbars().stream().map(ScreenText.Bar::name).toList();
			default -> ScreenText.sidebarLines();
		};
	}

	private static void watchText(List<Scripts.Stack> active) {
		Map<String, List<String>> cache = new HashMap<>();
		TEXT_SEEN.keySet().retainAll(new java.util.HashSet<>(active));
		for (Scripts.Stack stack : active) {
			if (stack.blocks.isEmpty() || !TEXT_EVENTS.contains(stack.blocks.getFirst().op)) {
				continue;
			}
			Scripts.Block hat = stack.blocks.getFirst();
			String found = findText(hat.text(0), cache.computeIfAbsent(hat.op, ScriptEngine::textsFor));
			boolean seen = TEXT_SEEN.getOrDefault(stack, false);
			if (hat.op.equals("ev_bossbar_gone") ? seen && found == null : found != null && !seen) {
				start(stack, Map.of("message", found == null ? hat.text(0) : found), true);
			}
			TEXT_SEEN.put(stack, found != null);
		}
	}

	public static void onJoin() {
		TEXT_SEEN.clear();
		TIMERS.clear();
		online = null;
		lastHealth = -1;
		wasFull = false;
		fire("ev_join", hat -> true, Map.of());
	}

	public static void onServerChange() {
		fire("ev_server", hat -> true, Map.of());
	}

	static String senderOf(String plain) {
		String header = plain.split("\n", 2)[0].strip();
		int colon = header.lastIndexOf('：');
		if (colon <= 0) {
			return "";
		}
		String head = header.substring(0, colon);
		String name = head.substring(Math.max(head.lastIndexOf('｜'), head.lastIndexOf('|')) + 1).strip();
		int tag = name.indexOf('（');
		return (tag > 0 ? name.substring(0, tag) : name).strip();
	}

	static String bodyOf(String plain) {
		String[] lines = plain.split("\n");
		if (lines.length < 2) {
			int colon = plain.indexOf('：');
			return colon >= 0 ? plain.substring(colon + 1).strip() : plain.strip();
		}
		StringBuilder body = new StringBuilder();
		for (int i = 1; i < lines.length; i++) {
			String line = lines[i].strip();
			if (line.startsWith("｜") || line.startsWith("|")) {
				line = line.substring(1).strip();
			}
			if (!body.isEmpty()) {
				body.append('\n');
			}
			body.append(line);
		}
		return body.toString();
	}

	static String[] channelEvent(String plain) {
		String channel = ChatTabs.channelLabel(plain);
		if (channel != null) {
			String sender = senderOf(plain);
			String body = bodyOf(plain);
			if (body.isBlank() && plain.stripTrailing().endsWith("：")) {
				channelHeader = new String[] { channel, sender };
				return null;
			}
			channelHeader = null;
			return new String[] { channel, sender, body };
		}
		String[] header = channelHeader;
		channelHeader = null;
		if (header != null && ChatTabs.isContinuation(plain)) {
			return new String[] { header[0], header[1], plain.split("\n", 2)[0].replaceFirst("^\\s*[｜|]\\s*", "").strip() };
		}
		return null;
	}

	public static void onChat(String plain) {
		if (echoing || plain.isBlank() || !active()) {
			return;
		}
		String[] channelMessage = channelEvent(plain);
		if (channelMessage != null) {
			String body = channelMessage[2].toLowerCase(Locale.ROOT);
			fire("ev_channel", hat -> {
				String needle = hat.text(1).strip().toLowerCase(Locale.ROOT);
				return hat.text(0).equals(channelMessage[0]) && (needle.isEmpty() || body.contains(needle));
			}, Map.of("message", channelMessage[2], "sender", channelMessage[1], "channel", channelMessage[0]));
		}
		String channel = ChatTabs.channelLabel(plain);
		Map<String, String> context = Map.of("message", bodyOf(plain), "sender", senderOf(plain), "channel", channel == null ? "" : channel);
		String lower = plain.toLowerCase(Locale.ROOT);
		fire("ev_message", hat -> {
			String needle = hat.text(0).strip().toLowerCase(Locale.ROOT);
			return !needle.isEmpty() && lower.contains(needle);
		}, context);
		String[] residence = residenceOf(plain);
		if (residence != null) {
			fire(residence[0].equals("進入") ? "ev_res_enter" : "ev_res_leave", hat -> {
				String wanted = hat.text(0).strip();
				return wanted.isEmpty() || wanted.equals("任何領地") || wanted.equalsIgnoreCase(residence[2]);
			}, Map.of("message", residence[2], "sender", residence[1]));
		}
		String me = Minecraft.getInstance().getUser().getName();
		if (lower.contains(me.toLowerCase(Locale.ROOT)) && !context.get("sender").equalsIgnoreCase(me)) {
			fire("ev_mention", hat -> true, context);
		}
	}

	private static final Pattern RESIDENCE = Pattern.compile("您已(進入|離開)\\s*(\\S+)\\s*的\\s*(.+?)\\s*領地。?\\s*$");

	static String[] residenceOf(String plain) {
		java.util.regex.Matcher matcher = RESIDENCE.matcher(plain.split("\n", 2)[0].strip());
		return matcher.find() ? new String[] { matcher.group(1), matcher.group(2), matcher.group(3) } : null;
	}

	public static void onPrivate(String sender, String text) {
		fire("ev_private", hat -> true, Map.of("message", text, "sender", sender));
	}

	static void safeStep(Run run, long now, int budget) {
		try {
			step(run, now, budget);
		} catch (RuntimeException | StackOverflowError e) {
			run.done = true;
			fail(run.current, "積木執行時發生錯誤，這個腳本已停止（" + e.getClass().getSimpleName() + "）");
			org.slf4j.LoggerFactory.getLogger("brilliant-server-mod").warn("Script error", e);
		}
	}

	static void step(Run run, long now) {
		step(run, now, STEPS_PER_TICK);
	}

	static void step(Run run, long now, int steps) {
		for (int budget = steps; budget > 0 && !run.done; budget--) {
			if (run.waitUntil > now) {
				return;
			}
			if (run.waitCondition != null) {
				if (truthy(eval(run, run.waitCondition))) {
					run.waitCondition = null;
					run.timedOut = false;
				} else if (run.waitDeadline > 0 && now >= run.waitDeadline) {
					run.waitCondition = null;
					run.timedOut = true;
				} else {
					return;
				}
			}
			if (run.asking) {
				return;
			}
			Frame frame = run.frames.peek();
			if (frame == null) {
				run.done = true;
				return;
			}
			if (frame.index >= frame.list.size()) {
				run.frames.pop();
				Frame parent = run.frames.peek();
				if (parent == null) {
					run.done = true;
					return;
				}
				if (parent.index >= parent.list.size()) {
					continue;
				}
				Scripts.Block owner = parent.list.get(parent.index);
				switch (owner.op) {
					case "repeat" -> {
						if (--parent.counter > 0) {
							run.frames.push(new Frame(owner.body, 0));
						} else {
							parent.index++;
						}
						return;
					}
					case "forever" -> {
						run.frames.push(new Frame(owner.body, 0));
						return;
					}
					case "for_each" -> {
						if (parent.items != null && ++parent.counter < parent.items.size()) {
							assign(run, owner.text(1), parent.items.get(parent.counter), owner);
							run.frames.push(new Frame(owner.body, 0));
						} else {
							parent.index++;
						}
						return;
					}
					case "repeat_until" -> {
						if (truthy(eval(run, owner.args.getFirst()))) {
							parent.index++;
						} else {
							run.frames.push(new Frame(owner.body, 0));
						}
						return;
					}
					default -> parent.index++;
				}
				continue;
			}
			Scripts.Block block = frame.list.get(frame.index);
			if (!run.debug && block != run.resumed && BREAKPOINTS.contains(block)) {
				run.debug = true;
				run.current = block;
				ScriptMonitor.log("⏸ 停在中斷點：" + ScriptMonitor.describe(block));
				return;
			}
			run.resumed = null;
			if (trace) {
				ScriptMonitor.log("→ " + ScriptMonitor.describe(block));
			}
			run.current = block;
			FLASH.put(block, now);
			switch (block.op) {
				case "wait" -> {
					run.waitStart = now;
					run.waitBlock = block;
					run.waitUntil = now + (long) (Math.clamp(number(eval(run, block.args.getFirst())), 0, 3600) * 1000);
					frame.index++;
					return;
				}
				case "key_press", "key_combo" -> {
					act(run, block);
					frame.index++;
					run.waitUntil = now + INPUT_GAP_MS;
					return;
				}
				case "act_tap" -> {
					if (playerControl(block)) {
						KeyMapping key = mapping(block.text(0));
						if (key != null && ((KeyMappingAccessor) key).brilliant$getClickCount() == 0) {
							hold(run, key, now + 120, true);
						}
					}
					frame.index++;
					run.waitUntil = now + INPUT_GAP_MS;
					return;
				}
				case "act_face", "act_turn", "act_face_pos", "act_face_player" -> {
					LocalPlayer player = player();
					if (player == null || !playerControl(block)) {
						run.turnBlock = null;
						frame.index++;
						continue;
					}
					if (run.turnBlock != block) {
						run.turnBlock = block;
						if (block.op.equals("act_face")) {
							float facing = switch (block.text(0)) {
								case "西" -> 90f;
								case "北" -> 180f;
								case "東" -> -90f;
								default -> 0f;
							};
							run.targetYaw = player.getYRot() + net.minecraft.util.Mth.wrapDegrees(facing - player.getYRot());
							run.targetPitch = player.getXRot();
						} else if (block.op.equals("act_turn")) {
							run.targetYaw = player.getYRot() + (float) Math.clamp(number(eval(run, block.args.get(0))), -360, 360);
							run.targetPitch = (float) Math.clamp(player.getXRot() + number(eval(run, block.args.get(1))), -90, 90);
						} else {
							if (block.op.equals("act_face_pos") && ScriptExtras.coordsHidden(player)) {
								fail(block, "伺服器隱藏了座標資訊，無法面向座標");
								run.turnBlock = null;
								frame.index++;
								continue;
							}
							net.minecraft.world.phys.Vec3 target = block.op.equals("act_face_pos")
									? new net.minecraft.world.phys.Vec3(number(eval(run, block.args.get(0))) + 0.5, number(eval(run, block.args.get(1))) + 0.5, number(eval(run, block.args.get(2))) + 0.5)
									: ScriptExtras.visiblePlayers(player, ScriptExtras.ENTITY_SIGHT).stream().findFirst()
											.map(net.minecraft.world.entity.Entity::getEyePosition).orElse(null);
							if (target == null) {
								fail(block, "附近沒有看得到的其他玩家");
								run.turnBlock = null;
								frame.index++;
								continue;
							}
							float[] look = lookAt(player, target);
							run.targetYaw = player.getYRot() + net.minecraft.util.Mth.wrapDegrees(look[0] - player.getYRot());
							run.targetPitch = look[1];
						}
					}
					float yaw = player.getYRot() + Math.clamp(run.targetYaw - player.getYRot(), -TURN_SPEED, TURN_SPEED);
					float pitch = player.getXRot() + Math.clamp(run.targetPitch - player.getXRot(), -TURN_SPEED, TURN_SPEED);
					player.setYRot(yaw);
					player.setYHeadRot(yaw);
					player.setXRot(pitch);
					if (Math.abs(run.targetYaw - yaw) < 0.01f && Math.abs(run.targetPitch - pitch) < 0.01f) {
						run.turnBlock = null;
						frame.index++;
					}
					run.waitUntil = now + 1;
					return;
				}
				case "gui_click_slot", "gui_move", "gui_click_text", "gui_click_xy", "gui_click_name", "gui_move_all" -> {
					if (!screenReady(block) && !(run.retryBlock == block && now - run.retryStart >= GUI_WAIT_MS)) {
						if (run.retryBlock != block) {
							run.retryBlock = block;
							run.retryStart = now;
						}
						run.waitUntil = now + 1;
						return;
					}
					run.retryBlock = null;
					if (screen() instanceof ChatScreen && now - lastSent < SEND_INTERVAL_MS) {
						run.waitUntil = lastSent + SEND_INTERVAL_MS;
						return;
					}
					if (screen() instanceof ChatScreen) {
						lastSent = now;
					}
					if (block.op.equals("gui_move")) {
						if (moveStep(run, block)) {
							run.moveStage = 0;
							frame.index++;
						}
					} else if (block.op.equals("gui_move_all")) {
						if (moveAllStep(run, block)) {
							run.moveStage = 0;
							run.movedSlots.clear();
							frame.index++;
						}
					} else {
						act(run, block);
						frame.index++;
					}
					run.waitUntil = now + INPUT_GAP_MS;
					return;
				}
				case "act_hold" -> {
					if (!playerControl(block)) {
						frame.index++;
						continue;
					}
					double seconds = Math.clamp(number(eval(run, block.args.get(1))), 0, MAX_HOLD_SECONDS);
					long until = now + (long) (seconds * 1000);
					hold(run, mapping(block.text(0)), until, true);
					run.waitStart = now;
					run.waitBlock = block;
					run.waitUntil = until;
					frame.index++;
					return;
				}
				case "wait_random" -> {
					double a = Math.clamp(number(eval(run, block.args.get(0))), 0, 3600);
					double b = Math.clamp(number(eval(run, block.args.get(1))), 0, 3600);
					double seconds = Math.min(a, b) + ThreadLocalRandom.current().nextDouble() * Math.abs(a - b);
					run.waitStart = now;
					run.waitBlock = block;
					run.waitUntil = now + (long) (seconds * 1000);
					frame.index++;
					return;
				}
				case "wait_until" -> {
					run.waitCondition = block.args.getFirst();
					run.waitDeadline = 0;
					frame.index++;
				}
				case "wait_timeout" -> {
					run.waitCondition = block.args.get(0);
					run.waitDeadline = now + (long) (Math.clamp(number(eval(run, block.args.get(1))), 0, 3600) * 1000);
					run.timedOut = false;
					frame.index++;
				}
				case "for_each" -> {
					List<String> items = new ArrayList<>(listValue(run.group, block.text(0)));
					if (block.text(1).isEmpty()) {
						fail(block, "還沒有選擇要存放每一項的變數");
						frame.index++;
					} else if (items.isEmpty()) {
						frame.index++;
					} else {
						frame.items = items;
						frame.counter = 0;
						frame.total = items.size();
						assign(run, block.text(1), items.getFirst(), block);
						run.frames.push(new Frame(block.body, 0));
					}
				}
				case "break", "continue" -> loopJump(run, block, block.op.equals("break"));
				case "cooldown" -> {
					long span = (long) (Math.clamp(number(eval(run, block.args.getFirst())), 0, 86400) * 1000);
					Long last = COOLDOWNS.get(block);
					if (last != null && now - last < span) {
						frame.index++;
					} else {
						COOLDOWNS.put(block, now);
						run.frames.push(new Frame(block.body, 0));
					}
				}
				case "if_elif" -> {
					if (truthy(eval(run, block.args.get(0)))) {
						run.frames.push(new Frame(block.body, 0));
					} else if (truthy(eval(run, block.args.get(1)))) {
						run.frames.push(new Frame(block.elseBody, 0));
					} else {
						frame.index++;
					}
				}
				case "fork" -> {
					if (RUNS.size() >= MAX_RUNS) {
						fail(block, "同時執行的腳本太多了，背景執行已略過");
					} else if (!block.body.isEmpty()) {
						Run child = new Run(run.stack, run.context);
						child.group = run.group;
						child.frames.push(new Frame(block.body, 0));
						RUNS.add(child);
					}
					frame.index++;
				}
				case "stop_others" -> {
					RUNS.forEach(other -> {
						if (other != run && other.group == run.group) {
							other.done = true;
						}
					});
					frame.index++;
				}
				case "ask", "choose" -> {
					net.minecraft.client.gui.screens.Screen open = screen();
					if (open != null && !(open instanceof ChatScreen)) {
						run.waitUntil = now + 1;
						return;
					}
					frame.index++;
					String prompt = cap(eval(run, block.args.getFirst()));
					if (Minecraft.getInstance() == null) {
						continue;
					}
					List<String> options = block.op.equals("choose") ? ScriptExtras.split(prompt, ",").stream().limit(8).toList() : null;
					if (options != null && options.isEmpty()) {
						fail(block, "選項是空的，請用逗號分開，例如：買,賣,取消");
						continue;
					}
					run.asking = true;
					boolean choosing = options != null;
					CommandMenuScreen.setScreen(new AskScreen(choosing ? "請選擇：" : prompt, options, value -> {
						if (choosing) {
							lastChoice = value;
						} else {
							lastAnswer = value;
						}
						run.asking = false;
					}));
					return;
				}
				case "act_walk_to" -> {
					if (walkStep(run, block, now)) {
						run.walkBlock = null;
						frame.index++;
					}
					run.waitUntil = now + 1;
					return;
				}
				case "repeat" -> {
					int times = (int) Math.min(100000, Math.round(number(eval(run, block.args.getFirst()))));
					if (times <= 0) {
						frame.index++;
					} else {
						frame.counter = times;
						frame.total = times;
						run.frames.push(new Frame(block.body, 0));
					}
				}
				case "forever" -> run.frames.push(new Frame(block.body, 0));
				case "repeat_until" -> {
					if (truthy(eval(run, block.args.getFirst()))) {
						frame.index++;
					} else {
						run.frames.push(new Frame(block.body, 0));
					}
				}
				case "if" -> {
					if (truthy(eval(run, block.args.getFirst()))) {
						run.frames.push(new Frame(block.body, 0));
					} else {
						frame.index++;
					}
				}
				case "if_else" -> run.frames.push(new Frame(truthy(eval(run, block.args.getFirst())) ? block.body : block.elseBody, 0));
				case "stop" -> run.done = true;
				case "proc_call" -> {
					String name = block.text(0).strip();
					Scripts.Stack definition = findProc(run, name);
					if (definition == null) {
						fail(block, name.isEmpty() ? "還沒有選擇要執行的自訂積木" : "找不到自訂積木「" + name + "」，已略過");
						frame.index++;
					} else if (run.frames.size() >= MAX_CALL_DEPTH) {
						fail(block, "自訂積木呼叫太多層（可能一直呼叫自己），這個腳本已停止");
						run.done = true;
					} else {
						Frame call = new Frame(definition.blocks, 1);
						call.args = new String[] { cap(eval(run, block.args.get(1))), cap(eval(run, block.args.get(2))) };
						run.frames.push(call);
					}
				}
				case "stop_all" -> {
					RUNS.forEach(other -> other.done = true);
					OUTBOX.clear();
					ScriptMonitor.log("已停止全部腳本");
				}
				default -> {
					act(run, block);
					frame.index++;
				}
			}
		}
	}

	private static final java.util.Set<String> CLIENT_ACTIONS = java.util.Set.of("insert", "local", "local_color", "actionbar", "sound", "copy", "title",
			"act_tap", "act_press", "act_release", "act_release_all", "act_hotbar", "act_face", "act_turn",
			"key_press", "key_combo", "gui_click_slot", "gui_move", "gui_click_text", "gui_click_xy", "gui_close", "gui_click_name",
			"act_hotbar_find", "note", "notify");

	static net.minecraft.client.gui.screens.Screen screen() {
		Minecraft client = Minecraft.getInstance();
		if (client == null) {
			return null;
		}
		//? if >=26.2 {
		return client.gui.screen();
		//?} else {
		/*return client.screen;
		*///?}
	}

	static boolean editorScreen(net.minecraft.client.gui.screens.Screen screen) {
		return screen instanceof ScriptEditorScreen || screen instanceof ScriptTemplateScreen || screen instanceof ScriptImportScreen;
	}

	private static net.minecraft.client.gui.screens.Screen gameScreen() {
		net.minecraft.client.gui.screens.Screen screen = screen();
		return editorScreen(screen) ? null : screen;
	}

	private static net.minecraft.client.gui.screens.Screen targetScreen(Scripts.Block block, boolean required) {
		net.minecraft.client.gui.screens.Screen screen = screen();
		if (screen instanceof ScriptEditorScreen || screen instanceof ScriptTemplateScreen || screen instanceof ScriptImportScreen) {
			fail(block, "腳本編輯器開著時不會操作介面或按鍵，請先關閉編輯器再測試");
			return null;
		}
		if (screen == null && required) {
			fail(block, "等了 " + GUI_WAIT_MS / 1000 + " 秒都沒有開啟任何介面，已略過");
		}
		return screen;
	}

	private static boolean screenReady(Scripts.Block block) {
		net.minecraft.client.gui.screens.Screen screen = screen();
		if (screen == null) {
			return false;
		}
		return !(block.op.equals("gui_click_slot") || block.op.equals("gui_move") || block.op.equals("gui_click_name") || block.op.equals("gui_move_all")) || screen instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?>
				|| screen instanceof ScriptEditorScreen || screen instanceof ScriptTemplateScreen || screen instanceof ScriptImportScreen;
	}

	private static int keyCode(String name) {
		return KeyCombos.code(name);
	}

	private static int modifiers(String name) {
		return KeyCombos.parse(name);
	}

	private static final java.util.Set<Integer> HOTKEY_DOWN = new java.util.HashSet<>();

	private static void hotkeys(Minecraft client) {
		List<Scripts.Block> hats = Scripts.activeStacks().stream().filter(s -> !s.blocks.isEmpty() && s.blocks.getFirst().op.equals("ev_hotkey")).map(s -> s.blocks.getFirst()).toList();
		java.util.Set<Integer> wanted = new java.util.HashSet<>();
		hats.forEach(hat -> wanted.add(keyCode(hat.text(1))));
		HOTKEY_DOWN.retainAll(wanted);
		boolean ready = screen() == null;
		int held = KeyCombos.held();
		for (int code : wanted) {
			if (!CommandKeys.keyDown(client, code)) {
				HOTKEY_DOWN.remove(code);
				continue;
			}
			if (!HOTKEY_DOWN.add(code) || !ready) {
				continue;
			}
			List<Scripts.Block> same = hats.stream().filter(hat -> keyCode(hat.text(1)) == code).toList();
			List<Integer> combos = same.stream().map(hat -> modifiers(hat.text(0))).distinct().toList();
			for (int want : combos) {
				if (KeyCombos.fires(want, held, combos.stream().filter(other -> other != want).toList())) {
					fire("ev_hotkey", hat -> keyCode(hat.text(1)) == code && modifiers(hat.text(0)) == want, Map.of());
				}
			}
		}
	}

	private static void pressKey(Minecraft client, Scripts.Block block) {
		boolean combo = block.op.equals("key_combo");
		pressKey(client, block, combo ? block.text(1) : block.text(0), combo ? modifiers(block.text(0)) : 0);
	}

	private static void pressKey(Minecraft client, Scripts.Block block, String name, int combo) {
		net.minecraft.client.gui.screens.Screen screen = screen();
		if (screen instanceof ScriptEditorScreen || screen instanceof ScriptTemplateScreen || screen instanceof ScriptImportScreen) {
			fail(block, "腳本編輯器開著時不會操作介面或按鍵，請先關閉編輯器再測試");
			return;
		}
		if (screen instanceof ChatScreen && name.equals("Enter")) {
			fail(block, "聊天欄開著時不能用 Enter 送出，要送訊息請用「送出訊息」積木（才會遵守每 3 秒一則的限制）");
			return;
		}
		KeyboardHandlerInvoker keyboard = (KeyboardHandlerInvoker) client.keyboardHandler;
		net.minecraft.client.input.KeyEvent event = new net.minecraft.client.input.KeyEvent(keyCode(name), 0, KeyCombos.eventMods(combo));
		long window = client.getWindow().handle();
		keyboard.brilliant$keyPress(window, InputConstants.PRESS, event);
		keyboard.brilliant$keyPress(window, InputConstants.RELEASE, event);
	}

	private static net.minecraft.world.inventory.Slot slot(Scripts.Block block, net.minecraft.client.gui.screens.Screen screen, double number, boolean quiet) {
		if (!(screen instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?> container)) {
			if (!quiet) {
				fail(block, "等了 " + GUI_WAIT_MS / 1000 + " 秒，目前開啟的仍不是物品介面（背包、箱子、選單等），已略過");
			}
			return null;
		}
		var slots = container.getMenu().slots;
		int index = (int) Math.clamp(Math.round(number), -1, Integer.MAX_VALUE - 1) - 1;
		if (index < 0 || index >= slots.size()) {
			if (!quiet) {
				fail(block, "這個介面沒有第 " + (index + 1) + " 格（共 " + slots.size() + " 格）");
			}
			return null;
		}
		net.minecraft.world.inventory.Slot slot = slots.get(index);
		if (!slot.isActive()) {
			if (!quiet) {
				fail(block, "第 " + (index + 1) + " 格目前是隱藏或停用的，玩家也點不到，已略過");
			}
			return null;
		}
		return slot;
	}

	private static void clickSlot(Minecraft client, Run run, Scripts.Block block) {
		net.minecraft.client.gui.screens.Screen screen = targetScreen(block, true);
		LocalPlayer player = client.player;
		net.minecraft.world.inventory.Slot slot = screen == null ? null : slot(block, screen, number(eval(run, block.args.get(0))), false);
		if (slot == null || player == null || client.gameMode == null) {
			return;
		}
		click(screen, slot, block.text(1));
	}

	private static void click(net.minecraft.client.gui.screens.Screen screen, net.minecraft.world.inventory.Slot slot, String how) {
		int button = how.equals("右鍵") || how.equals("丟出整組") ? 1 : 0;
		String kind = switch (how) {
			case "Shift+左鍵" -> "QUICK_MOVE";
			case "丟出", "丟出整組" -> "THROW";
			default -> "PICKUP";
		};
		click(screen, slot, button, kind);
	}

	private static void click(net.minecraft.client.gui.screens.Screen screen, net.minecraft.world.inventory.Slot slot, int button, String kind) {
		var container = (net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?>) screen;
		int slotId = container.getMenu().slots.indexOf(slot);
		var accessor = (net.brilliantw.richpresence.mixin.AbstractContainerScreenAccessor) screen;
		//? if >=26.1 {
		accessor.brilliant$slotClicked(slot, slotId, button, net.minecraft.world.inventory.ContainerInput.valueOf(kind));
		//?} else {
		/*accessor.brilliant$slotClicked(slot, slotId, button, net.minecraft.world.inventory.ClickType.valueOf(kind));
		*///?}
	}

	private static boolean moveStep(Run run, Scripts.Block block) {
		Minecraft client = Minecraft.getInstance();
		net.minecraft.client.gui.screens.Screen screen = client == null ? null : targetScreen(block, true);
		if (screen == null || client.player == null) {
			return true;
		}
		if (run.moveStage == 0) {
			run.moveFrom = (int) Math.clamp(Math.round(number(eval(run, block.args.get(0)))), -1, Integer.MAX_VALUE - 1);
			run.moveTo = (int) Math.clamp(Math.round(number(eval(run, block.args.get(1)))), -1, Integer.MAX_VALUE - 1);
		}
		net.minecraft.world.inventory.Slot from = slot(block, screen, run.moveFrom, false);
		net.minecraft.world.inventory.Slot to = from == null ? null : slot(block, screen, run.moveTo, false);
		if (to == null || from == to) {
			return true;
		}
		var menu = ((net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?>) screen).getMenu();
		switch (run.moveStage++) {
			case 0 -> {
				if (!from.hasItem()) {
					fail(block, "第 " + run.moveFrom + " 格是空的，沒有東西可以移動");
					return true;
				}
				if (!menu.getCarried().isEmpty()) {
					fail(block, "滑鼠上已經拿著物品，先放下再移動");
					return true;
				}
				click(screen, from, 0, "PICKUP");
				return false;
			}
			case 1 -> {
				click(screen, to, 0, "PICKUP");
				return menu.getCarried().isEmpty();
			}
			default -> {
				click(screen, from, 0, "PICKUP");
				if (!menu.getCarried().isEmpty()) {
					fail(block, "第 " + run.moveTo + " 格放不下，剩下的物品還在滑鼠上");
				}
				return true;
			}
		}
	}

	private static void collectWidgets(net.minecraft.client.gui.components.events.GuiEventListener listener, List<net.minecraft.client.gui.components.AbstractWidget> out, int depth) {
		if (listener instanceof net.minecraft.client.gui.components.AbstractWidget widget) {
			out.add(widget);
		}
		if (depth < 6 && listener instanceof net.minecraft.client.gui.components.events.ContainerEventHandler container) {
			for (var child : container.children()) {
				collectWidgets(child, out, depth + 1);
			}
		}
	}

	private static void clickAt(net.minecraft.client.gui.screens.Screen screen, double x, double y) {
		var event = new net.minecraft.client.input.MouseButtonEvent(x, y, new net.minecraft.client.input.MouseButtonInfo(0, 0));
		screen.mouseClicked(event, false);
		screen.mouseReleased(event);
	}

	private static void clickText(Run run, Scripts.Block block) {
		net.minecraft.client.gui.screens.Screen screen = targetScreen(block, true);
		if (screen == null) {
			return;
		}
		String wanted = eval(run, block.args.getFirst()).strip().toLowerCase(Locale.ROOT);
		List<net.minecraft.client.gui.components.AbstractWidget> widgets = new ArrayList<>();
		for (var child : screen.children()) {
			collectWidgets(child, widgets, 0);
		}
		for (var widget : widgets) {
			double cx = widget.getX() + widget.getWidth() / 2.0;
			double cy = widget.getY() + widget.getHeight() / 2.0;
			boolean onScreen = cx >= 0 && cx < screen.width && cy >= 0 && cy < screen.height;
			if (widget.visible && widget.active && onScreen && !wanted.isEmpty() && widget.getMessage().getString().toLowerCase(Locale.ROOT).contains(wanted)) {
				clickAt(screen, cx, cy);
				return;
			}
		}
		fail(block, "介面上找不到寫著「" + wanted + "」的按鈕");
	}

	static KeyMapping mapping(String action) {
		Minecraft client = Minecraft.getInstance();
		if (client == null) {
			return null;
		}
		var options = client.options;
		return switch (action) {
			case "使用／放置" -> options.keyUse;
			case "跳躍" -> options.keyJump;
			case "蹲下" -> options.keyShift;
			case "衝刺" -> options.keySprint;
			case "前進" -> options.keyUp;
			case "後退" -> options.keyDown;
			case "向左" -> options.keyLeft;
			case "向右" -> options.keyRight;
			case "丟棄物品" -> options.keyDrop;
			case "副手交換" -> options.keySwapOffhand;
			default -> options.keyAttack;
		};
	}

	private static void hold(Run run, KeyMapping key, long until, boolean click) {
		if (key == null) {
			return;
		}
		key.setDown(true);
		if (click) {
			KeyMappingAccessor accessor = (KeyMappingAccessor) key;
			accessor.brilliant$setClickCount(accessor.brilliant$getClickCount() + 1);
		}
		HELD.put(key, new Hold(run, until));
	}

	private static void release(KeyMapping key) {
		if (key != null && HELD.remove(key) != null) {
			key.setDown(false);
		}
	}

	public static void releaseAll() {
		HELD.keySet().forEach(key -> key.setDown(false));
		HELD.clear();
	}

	private static void updateHeld(long now) {
		boolean screenOpen = screen() != null;
		HELD.entrySet().removeIf(entry -> {
			Hold hold = entry.getValue();
			boolean expired = now >= hold.until() || hold.owner().done || !RUNS.contains(hold.owner());
			entry.getKey().setDown(!expired && !screenOpen);
			return expired;
		});
	}

	private static boolean playerControl(Scripts.Block block) {
		if (screen() == null) {
			return true;
		}
		fail(block, "開著介面（背包、聊天欄、選單等）時，生存模式也不能移動、攻擊、轉向或切換快捷欄，這一步已略過");
		return false;
	}

	private static void act(Run run, Scripts.Block block) {
		Minecraft client = Minecraft.getInstance();
		if (client == null && CLIENT_ACTIONS.contains(block.op)) {
			return;
		}
		switch (block.op) {
			case "say" -> {
				String text = eval(run, block.args.getFirst()).strip();
				if (text.startsWith("/")) {
					queue(block, true, text.substring(1));
				} else {
					queue(block, false, text);
				}
			}
			case "command" -> queue(block, true, stripSlash(eval(run, block.args.getFirst())));
			case "say_channel" -> {
				String text = eval(run, block.args.get(1)).strip();
				if (chatSafe(text).isEmpty()) {
					fail(block, "要送出的內容是空的，已略過");
				} else if (text.startsWith("/")) {
					fail(block, "頻道訊息不能是指令，已略過：" + text);
				} else {
					queue(block, false, CHANNEL_PREFIX.getOrDefault(block.text(0), "") + text);
				}
			}
			case "reply" -> {
				String target = run.context.getOrDefault("sender", "").strip();
				String body = chatSafe(eval(run, block.args.getFirst()));
				if (body.isEmpty()) {
					fail(block, "私訊內容是空的，已略過");
				} else if (target.matches("[A-Za-z0-9_.]{1,20}")) {
					queue(block, true, "msg " + target + " " + body);
				} else {
					fail(block, "「回覆私訊」失敗：只有「當收到私訊時」觸發的腳本才知道要回覆誰");
				}
			}
			case "broadcast" -> {
				String name = eval(run, block.args.getFirst()).strip();
				fire("ev_broadcast", hat -> hat.text(0).strip().equalsIgnoreCase(name), run.context);
			}
			case "log" -> ScriptMonitor.log(eval(run, block.args.getFirst()));
			case "act_press" -> {
				if (playerControl(block)) {
					hold(run, mapping(block.text(0)), Long.MAX_VALUE, true);
				}
			}
			case "act_release" -> release(mapping(block.text(0)));
			case "act_release_all" -> {
				List<KeyMapping> mine = HELD.entrySet().stream().filter(entry -> entry.getValue().owner() == run).map(Map.Entry::getKey).toList();
				mine.forEach(ScriptEngine::release);
			}
			case "act_hotbar" -> {
				LocalPlayer player = client.player;
				if (player != null && playerControl(block)) {
					player.getInventory().setSelectedSlot((int) Math.clamp(Math.round(number(eval(run, block.args.getFirst()))) - 1, 0, 8));
				}
			}
			case "key_press", "key_combo" -> pressKey(client, block);
			case "gui_click_slot" -> clickSlot(client, run, block);
			case "gui_click_text" -> clickText(run, block);
			case "gui_click_xy" -> {
				net.minecraft.client.gui.screens.Screen screen = targetScreen(block, true);
				if (screen != null) {
					clickAt(screen, Math.clamp(number(eval(run, block.args.get(0))), 0, screen.width), Math.clamp(number(eval(run, block.args.get(1))), 0, screen.height));
				}
			}
			case "gui_close" -> {
				net.minecraft.client.gui.screens.Screen screen = targetScreen(block, false);
				if (screen != null && !screen.shouldCloseOnEsc()) {
					fail(block, "這個介面不能直接關閉（玩家按 ESC 也關不掉），已略過");
				} else if (screen != null) {
					screen.onClose();
				}
			}
			case "act_hotbar_find" -> {
				LocalPlayer player = client.player;
				int slot = ScriptExtras.hotbarFind(player, eval(run, block.args.getFirst()));
				if (player != null && playerControl(block)) {
					if (slot == 0) {
						fail(block, "快捷欄裡沒有名稱含有「" + eval(run, block.args.getFirst()).strip() + "」的物品");
					} else {
						player.getInventory().setSelectedSlot(slot - 1);
					}
				}
			}
			case "gui_click_name" -> {
				net.minecraft.client.gui.screens.Screen screen = targetScreen(block, true);
				if (screen instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?> container) {
					String needle = eval(run, block.args.get(0)).strip().toLowerCase(Locale.ROOT);
					var found = needle.isEmpty() ? null : container.getMenu().slots.stream()
							.filter(slot -> slot.isActive() && slot.hasItem() && slot.getItem().getHoverName().getString().toLowerCase(Locale.ROOT).contains(needle)).findFirst().orElse(null);
					if (found == null) {
						fail(block, "介面裡找不到名稱含有「" + needle + "」的物品");
					} else {
						click(screen, found, block.text(1));
					}
				}
			}
			case "monitor_field" -> ScriptMonitor.setField(cap(eval(run, block.args.get(0))), eval(run, block.args.get(1)));
			case "monitor_bar" -> ScriptMonitor.setBar(cap(eval(run, block.args.get(0))), number(eval(run, block.args.get(1))));
			case "monitor_clear" -> ScriptMonitor.clearFields();
			case "note" -> {
				var instrument = switch (block.text(0)) {
					case "貝斯" -> SoundEvents.NOTE_BLOCK_BASS;
					case "鐘" -> SoundEvents.NOTE_BLOCK_BELL;
					case "長笛" -> SoundEvents.NOTE_BLOCK_FLUTE;
					case "吉他" -> SoundEvents.NOTE_BLOCK_GUITAR;
					case "鐵琴" -> SoundEvents.NOTE_BLOCK_XYLOPHONE;
					case "風鈴" -> SoundEvents.NOTE_BLOCK_CHIME;
					case "牛鈴" -> SoundEvents.NOTE_BLOCK_COW_BELL;
					case "班鳩琴" -> SoundEvents.NOTE_BLOCK_BANJO;
					case "電子琴" -> SoundEvents.NOTE_BLOCK_PLING;
					case "大鼓" -> SoundEvents.NOTE_BLOCK_BASEDRUM;
					case "小鼓" -> SoundEvents.NOTE_BLOCK_SNARE;
					case "敲擊" -> SoundEvents.NOTE_BLOCK_HAT;
					default -> SoundEvents.NOTE_BLOCK_HARP;
				};
				double pitch = Math.pow(2, (Math.clamp(number(eval(run, block.args.get(1))), 0, 24) - 12) / 12.0);
				client.getSoundManager().play(SimpleSoundInstance.forUI(instrument, (float) pitch));
			}
			case "notify" -> {
				String heading = cap(eval(run, block.args.get(0)));
				String body = cap(eval(run, block.args.get(1)));
				//? if >=26.2 {
				var toasts = client.gui.toastManager();
				//?} else {
				/*var toasts = client.getToastManager();
				*///?}
				net.minecraft.client.gui.components.toasts.SystemToast.add(toasts, net.minecraft.client.gui.components.toasts.SystemToast.SystemToastId.PERIODIC_NOTIFICATION,
						Component.literal(heading), Component.literal(body));
				if (!client.isWindowActive()) {
					//? if >=26.3 {
					/*org.lwjgl.sdl.SDLVideo.SDL_FlashWindow(client.getWindow().handle(), org.lwjgl.sdl.SDLVideo.SDL_FLASH_UNTIL_FOCUSED);
					*///?} else {
					org.lwjgl.glfw.GLFW.glfwRequestWindowAttention(client.getWindow().handle());
					//?}
				}
			}
			case "file_log" -> {
				String error = ScriptExtras.appendLog(eval(run, block.args.get(0)), cap(eval(run, block.args.get(1))));
				if (error != null) {
					fail(block, error);
				}
			}
			case "list_insert" -> {
				List<String> list = list(run, block, 0);
				if (list == null) {
					return;
				}
				int index = (int) Math.clamp(number(eval(run, block.args.get(1))), 1, list.size() + 1) - 1;
				if (list.size() >= Scripts.MAX_LIST_ITEMS) {
					fail(block, "清單最多 " + Scripts.MAX_LIST_ITEMS + " 項，這次沒有插入");
				} else {
					list.add(index, cap(eval(run, block.args.get(2))));
					valuesDirty = true;
				}
			}
			case "list_sort", "list_shuffle" -> {
				List<String> list = list(run, block, 0);
				if (list != null) {
					if (block.op.equals("list_sort")) {
						ScriptExtras.sort(list, block.text(1).equals("由大到小"));
					} else {
						java.util.Collections.shuffle(list);
					}
					valuesDirty = true;
				}
			}
			case "list_from_text" -> {
				List<String> list = list(run, block, 2);
				if (list != null) {
					list.clear();
					ScriptExtras.split(eval(run, block.args.get(0)), eval(run, block.args.get(1))).stream().limit(Scripts.MAX_LIST_ITEMS).map(ScriptEngine::cap).forEach(list::add);
					valuesDirty = true;
				}
			}
			case "map_set" -> {
				List<String> list = list(run, block, 0);
				if (list != null) {
					if (list.size() >= Scripts.MAX_LIST_ITEMS && !ScriptExtras.mapHas(list, eval(run, block.args.get(1)))) {
						fail(block, "清單最多 " + Scripts.MAX_LIST_ITEMS + " 項，這次沒有加入");
					} else {
						ScriptExtras.mapSet(list, cap(eval(run, block.args.get(1))), cap(eval(run, block.args.get(2))));
						valuesDirty = true;
					}
				}
			}
			case "map_remove" -> {
				List<String> list = list(run, block, 0);
				if (list != null && ScriptExtras.mapRemove(list, eval(run, block.args.get(1)))) {
					valuesDirty = true;
				}
			}
			case "monitor_show" -> Scripts.data().monitor = true;
			case "monitor_hide" -> Scripts.data().monitor = false;
			case "timer_reset" -> timerStart = System.currentTimeMillis();
			case "msg" -> {
				String target = eval(run, block.args.get(0)).strip();
				String body = chatSafe(eval(run, block.args.get(1)));
				if (body.isEmpty()) {
					fail(block, "私訊內容是空的，已略過");
				} else if (target.matches("[A-Za-z0-9_.]{1,20}")) {
					queue(block, true, "msg " + target + " " + body);
				} else {
					fail(block, "「私訊」失敗：「" + target + "」不是有效的玩家名稱");
				}
			}
			case "insert" -> {
				String text = eval(run, block.args.getFirst());
				//? if >=26.2 {
				Object screen = client.gui.screen();
				//?} else {
				/*Object screen = client.screen;
				*///?}
				if (screen instanceof ChatScreen chat) {
					PrivateWindow.insert(chat, text);
				} else if (screen == null) {
					CommandMenuScreen.setScreen(new ChatScreen(text, false));
				}
			}
			case "local" -> local(Component.literal(eval(run, block.args.getFirst())).withStyle(ChatFormatting.WHITE));
			case "local_color" -> local(Component.literal(eval(run, block.args.get(1))).withStyle(switch (block.text(0)) {
				case "紅" -> ChatFormatting.RED;
				case "橙" -> ChatFormatting.GOLD;
				case "黃" -> ChatFormatting.YELLOW;
				case "綠" -> ChatFormatting.GREEN;
				case "藍" -> ChatFormatting.AQUA;
				case "紫" -> ChatFormatting.LIGHT_PURPLE;
				case "灰" -> ChatFormatting.GRAY;
				default -> ChatFormatting.WHITE;
			}));
			case "copy" -> client.keyboardHandler.setClipboard(cap(eval(run, block.args.getFirst())));
			case "title" -> {
				String title = cap(eval(run, block.args.get(0)));
				String subtitle = cap(eval(run, block.args.get(1)));
				//? if >=26.2 {
				var hud = client.gui.hud;
				//?} else {
				/*var hud = client.gui;
				*///?}
				hud.setTimes(5, 50, 10);
				hud.setSubtitle(Component.literal(subtitle));
				hud.setTitle(Component.literal(title));
			}
			case "actionbar" -> {
				LocalPlayer player = client.player;
				if (player != null) {
					//? if >=26.1 {
					player.sendOverlayMessage(Component.literal(eval(run, block.args.getFirst())));
					//?} else {
					/*player.displayClientMessage(Component.literal(eval(run, block.args.getFirst())), true);
					*///?}
				}
			}
			case "sound" -> {
				switch (block.text(0)) {
					case "鈴" -> client.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_BELL, 1f));
					case "經驗" -> client.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.EXPERIENCE_ORB_PICKUP, 1f));
					case "升級" -> client.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.PLAYER_LEVELUP, 1f));
					default -> client.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_PLING, 1f));
				}
			}
			case "var_set" -> setVariable(run, block, eval(run, block.args.get(1)));
			case "var_change" -> setVariable(run, block, format(number(getVariable(run, block.text(0))) + number(eval(run, block.args.get(1)))));
			case "list_add" -> {
				List<String> list = list(run, block, 1);
				if (list == null) {
					return;
				}
				if (list.size() >= Scripts.MAX_LIST_ITEMS) {
					fail(block, "清單最多 " + Scripts.MAX_LIST_ITEMS + " 項，這次沒有加入");
				} else {
					String item = eval(run, block.args.get(0));
					list.add(item.length() > Scripts.MAX_TEXT ? item.substring(0, Scripts.MAX_TEXT) : item);
					valuesDirty = true;
				}
			}
			case "list_delete", "list_replace" -> {
				List<String> list = list(run, block, 0);
				if (list == null) {
					return;
				}
				int index = (int) Math.clamp(number(eval(run, block.args.get(1))), -1, Integer.MAX_VALUE - 1) - 1;
				if (index < 0 || index >= list.size()) {
					fail(block, "清單「" + block.text(0) + "」沒有第 " + (index + 1) + " 項（目前 " + list.size() + " 項）");
				} else if (block.op.equals("list_delete")) {
					list.remove(index);
					valuesDirty = true;
				} else {
					String item = eval(run, block.args.get(2));
					list.set(index, item.length() > Scripts.MAX_TEXT ? item.substring(0, Scripts.MAX_TEXT) : item);
					valuesDirty = true;
				}
			}
			case "list_clear" -> {
				List<String> list = list(run, block, 0);
				if (list != null && !list.isEmpty()) {
					list.clear();
					valuesDirty = true;
				}
			}
			default -> {
			}
		}
	}

	private static String stripSlash(String text) {
		String trimmed = text.strip();
		return trimmed.startsWith("/") ? trimmed.substring(1) : trimmed;
	}

	private static void local(Component message) {
		echoing = true;
		try {
			RichPresenceModClient.showMessage(Component.literal("｜").withStyle(ChatFormatting.GRAY)
					.append(Component.literal("腳本").withStyle(ChatFormatting.AQUA))
					.append(Component.literal("｜").withStyle(ChatFormatting.GRAY))
					.append(message));
		} finally {
			echoing = false;
		}
	}

	static String chatSafe(String text) {
		StringBuilder clean = new StringBuilder();
		text.replaceAll("[\\r\\n\\t]+", " ").codePoints()
				.filter(c -> c != '§' && c >= ' ' && c != 127)
				.forEach(clean::appendCodePoint);
		String result = clean.toString().strip();
		return result.length() > Scripts.MAX_TEXT ? result.substring(0, Scripts.MAX_TEXT) : result;
	}

	private static String cap(String text) {
		return text.length() > 200 ? text.substring(0, 200) : text;
	}

	private static List<String> items(String text) {
		List<String> items = new ArrayList<>();
		for (String item : text.split("[,，、]")) {
			if (!item.isBlank()) {
				items.add(item.strip());
			}
		}
		return items;
	}

	private static void queue(Scripts.Block block, boolean command, String text) {
		String clean = chatSafe(text);
		if (clean.isBlank()) {
			fail(block, "要送出的內容是空的，已略過");
			return;
		}
		if (OUTBOX.size() >= MAX_QUEUE) {
			fail(block, "送出太頻繁，已略過：" + clean + "（每 3 秒最多送出一則）");
			return;
		}
		OUTBOX.add(new Outgoing(block, command, clean));
	}

	private static void flush(long now) {
		LocalPlayer player = Minecraft.getInstance().player;
		if (player == null || OUTBOX.isEmpty() || now - lastSent < SEND_INTERVAL_MS) {
			return;
		}
		Outgoing next = OUTBOX.poll();
		String key = (next.command() ? "/" : "") + next.text();
		if (key.equals(lastText) && now - lastSent < DUPLICATE_MS) {
			fail(next.block(), "10 秒內不會重複送出同樣的內容，已略過：" + key);
			return;
		}
		ScriptMonitor.log((next.command() ? "指令：" : "送出：") + key);
		lastSent = now;
		lastText = key;
		if (next.command()) {
			player.connection.sendCommand(next.text());
		} else {
			player.connection.sendChat(next.text());
		}
	}

	private static String eval(Run run, Scripts.Arg arg) {
		return arg.block != null ? evalBlock(run, arg.block) : arg.text;
	}

	private static String arg(Run run, Scripts.Block block, int index) {
		return index < block.args.size() ? eval(run, block.args.get(index)) : "";
	}

	private static LocalPlayer player() {
		Minecraft client = Minecraft.getInstance();
		return client == null ? null : client.player;
	}

	private static String evalBlock(Run run, Scripts.Block block) {
		LocalPlayer player = switch (block.op) {
			case "s_pos", "s_health", "s_dimension", "s_facing", "s_food", "s_level", "s_held", "s_count", "s_armor", "s_sneak", "s_ping" -> player();
			default -> null;
		};
		return switch (block.op) {
			case "s_message" -> run.context.getOrDefault("message", "");
			case "s_sender" -> run.context.getOrDefault("sender", "");
			case "s_channel" -> run.context.getOrDefault("channel", "");
			case "s_contains" -> bool(run.context.getOrDefault("message", "").toLowerCase(Locale.ROOT).contains(arg(run, block, 0).toLowerCase(Locale.ROOT)));
			case "s_me" -> Minecraft.getInstance() == null ? "" : Minecraft.getInstance().getUser().getName();
			case "s_server" -> RichPresenceModClient.serverName();
			case "s_pos" -> ScriptExtras.coordsHidden(player) ? "" : switch (block.text(0)) {
				case "Y" -> Integer.toString(player.blockPosition().getY());
				case "Z" -> Integer.toString(player.blockPosition().getZ());
				default -> Integer.toString(player.blockPosition().getX());
			};
			case "s_health" -> player == null ? "0" : format(Math.round(player.getHealth() * 10) / 10.0);
			case "s_time" -> {
				LocalTime now = LocalTime.now();
				yield Integer.toString(switch (block.text(0)) {
					case "分" -> now.getMinute();
					case "秒" -> now.getSecond();
					default -> now.getHour();
				});
			}
			case "s_weekday" -> "星期" + WEEKDAYS.get(LocalDate.now().getDayOfWeek().getValue() - 1);
			case "s_dimension" -> player == null ? "" : CoordTools.dimensionName(player.level().dimension());
			case "s_facing" -> {
				if (player == null) {
					yield "";
				}
				float yaw = ((player.getYRot() % 360) + 360) % 360;
				yield List.of("南", "西", "北", "東").get(Math.round(yaw / 90f) % 4);
			}
			case "s_food" -> player == null ? "0" : Integer.toString(player.getFoodData().getFoodLevel());
			case "s_level" -> player == null ? "0" : Integer.toString(player.experienceLevel);
			case "s_held" -> player == null || player.getMainHandItem().isEmpty() ? "" : player.getMainHandItem().getHoverName().getString();
			case "s_count" -> {
				if (player == null) {
					yield "0";
				}
				String needle = arg(run, block, 0).strip().toLowerCase(Locale.ROOT);
				int total = 0;
				for (int i = 0; i < Math.min(36, player.getInventory().getContainerSize()); i++) {
					var stack = player.getInventory().getItem(i);
					if (!stack.isEmpty() && (needle.isEmpty() || stack.getHoverName().getString().toLowerCase(Locale.ROOT).contains(needle))) {
						total += stack.getCount();
					}
				}
				yield Integer.toString(total);
			}
			case "s_armor" -> player == null ? "0" : Integer.toString(player.getArmorValue());
			case "s_sneak" -> bool(player != null && player.isShiftKeyDown());
			case "s_ping" -> {
				var connection = Minecraft.getInstance() == null ? null : Minecraft.getInstance().getConnection();
				var info = connection == null || player == null ? null : connection.getPlayerInfo(player.getUUID());
				yield info == null ? "0" : Integer.toString(info.getLatency());
			}
			case "s_online" -> {
				var connection = Minecraft.getInstance() == null ? null : Minecraft.getInstance().getConnection();
				yield connection == null ? "0" : Integer.toString(connection.getListedOnlinePlayers().size());
			}
			case "s_is_online" -> {
				var connection = Minecraft.getInstance() == null ? null : Minecraft.getInstance().getConnection();
				String name = arg(run, block, 0).strip();
				yield bool(connection != null && !name.isEmpty() && connection.getListedOnlinePlayers().stream().anyMatch(info -> info.getProfile().name().equalsIgnoreCase(name)));
			}
			case "s_date" -> LocalDate.now().toString();
			case "s_title" -> ScreenText.title();
			case "s_subtitle" -> ScreenText.subtitle();
			case "s_actionbar" -> ScreenText.actionbar();
			case "s_bossbar", "s_bossbar_progress" -> {
				List<ScreenText.Bar> bars = ScreenText.bossbars();
				int index = (int) Math.clamp(number(arg(run, block, 0)), -1, Integer.MAX_VALUE - 1) - 1;
				if (index < 0 || index >= bars.size()) {
					yield block.op.equals("s_bossbar") ? "" : "0";
				}
				yield block.op.equals("s_bossbar") ? bars.get(index).name() : Integer.toString(Math.round(bars.get(index).progress() * 100));
			}
			case "s_bossbar_count" -> Integer.toString(ScreenText.bossbars().size());
			case "s_sidebar_title" -> ScreenText.sidebarTitle();
			case "s_sidebar_line" -> {
				List<String> lines = ScreenText.sidebarLines();
				int index = (int) Math.clamp(number(arg(run, block, 0)), -1, Integer.MAX_VALUE - 1) - 1;
				yield index >= 0 && index < lines.size() ? lines.get(index) : "";
			}
			case "s_sidebar_find" -> {
				String keyword = arg(run, block, 0);
				String found = keyword.isBlank() ? null : findText(keyword, ScreenText.sidebarLines());
				yield found == null ? "" : found;
			}
			case "s_tab_header" -> ScreenText.tabHeader();
			case "s_tab_footer" -> ScreenText.tabFooter();
			case "s_gamemode" -> {
				Minecraft client = Minecraft.getInstance();
				var mode = client == null || client.gameMode == null ? null : client.gameMode.getPlayerMode();
				yield mode == null ? "" : switch (mode) {
					case CREATIVE -> "創造";
					case ADVENTURE -> "冒險";
					case SPECTATOR -> "旁觀者";
					default -> "生存";
				};
			}
			case "s_on_ground" -> bool(player() != null && player().onGround());
			case "s_in_water" -> bool(player() != null && player().isInWater());
			case "s_on_fire" -> bool(player() != null && player().isOnFire());
			case "s_riding" -> bool(player() != null && player().isPassenger());
			case "s_effect" -> {
				LocalPlayer self = player();
				String wanted = arg(run, block, 0).strip().toLowerCase(Locale.ROOT);
				yield bool(self != null && !wanted.isEmpty() && self.getActiveEffects().stream()
						.anyMatch(effect -> effect.showIcon() && effect.getEffect().value().getDisplayName().getString().toLowerCase(Locale.ROOT).contains(wanted)));
			}
			case "s_block_below" -> {
				LocalPlayer self = player();
				yield self == null ? "" : ScriptExtras.blockName(self, self.blockPosition().below());
			}
			case "s_target_block" -> {
				Minecraft client = Minecraft.getInstance();
				LocalPlayer self = player();
				yield self != null && client.hitResult instanceof net.minecraft.world.phys.BlockHitResult hit && hit.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK
						? ScriptExtras.blockName(self, hit.getBlockPos()) : "";
			}
			case "s_target_entity" -> {
				Minecraft client = Minecraft.getInstance();
				yield client != null && client.hitResult instanceof net.minecraft.world.phys.EntityHitResult hit && player() != null && !hit.getEntity().isInvisibleTo(player()) ? hit.getEntity().getName().getString() : "";
			}
			case "s_nearby_players" -> {
				LocalPlayer self = player();
				yield Integer.toString(ScriptExtras.visiblePlayers(self, number(arg(run, block, 0))).size());
			}
			case "s_nearest_player" -> ScriptExtras.visiblePlayers(player(), ScriptExtras.ENTITY_SIGHT).stream().findFirst().map(other -> other.getName().getString()).orElse("");
			case "s_biome" -> {
				LocalPlayer self = player();
				if (ScriptExtras.coordsHidden(self)) {
					yield "";
				}
				String id = self.level().getBiome(self.blockPosition()).getRegisteredName();
				yield Component.translatable("biome." + id.replace(':', '.')).getString();
			}
			case "s_weather" -> {
				LocalPlayer self = player();
				yield self == null ? "" : self.level().isThundering() ? "雷雨" : self.level().isRaining() ? "下雨" : "晴天";
			}
			case "s_game_hour" -> {
				long time = dayTime();
				yield time < 0 ? "0" : Long.toString((time + 6000) % 24000 / 1000);
			}
			case "s_is_day" -> {
				long time = dayTime();
				yield bool(time >= 0 && time % 24000 < 12300);
			}
			case "s_durability" -> {
				LocalPlayer self = player();
				var stack = self == null ? null : self.getMainHandItem();
				yield stack == null || !stack.isDamageableItem() ? "0" : Integer.toString(stack.getMaxDamage() - stack.getDamageValue());
			}
			case "s_held_count" -> {
				LocalPlayer self = player();
				yield self == null ? "0" : Integer.toString(self.getMainHandItem().getCount());
			}
			case "s_free_slots" -> {
				LocalPlayer self = player();
				if (self == null) {
					yield "0";
				}
				int free = 0;
				for (int i = 0; i < Math.min(36, self.getInventory().getContainerSize()); i++) {
					if (self.getInventory().getItem(i).isEmpty()) {
						free++;
					}
				}
				yield Integer.toString(free);
			}
			case "s_hotbar_find" -> Integer.toString(ScriptExtras.hotbarFind(player(), arg(run, block, 0)));
			case "s_gui_find" -> {
				String needle = arg(run, block, 0).strip().toLowerCase(Locale.ROOT);
				if (!(gameScreen() instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?> container) || needle.isEmpty()) {
					yield "0";
				}
				var slots = container.getMenu().slots;
				int found = 0;
				for (int i = 0; i < slots.size(); i++) {
					if (slots.get(i).isActive() && slots.get(i).hasItem() && slots.get(i).getItem().getHoverName().getString().toLowerCase(Locale.ROOT).contains(needle)) {
						found = i + 1;
						break;
					}
				}
				yield Integer.toString(found);
			}
			case "s_entity_count" -> Integer.toString(ScriptExtras.entityCount(player(), number(arg(run, block, 0)), arg(run, block, 1)));
			case "s_nearest_entity" -> {
				var entity = ScriptExtras.nearestCreature(player());
				yield entity == null ? "" : entity.getName().getString();
			}
			case "s_nearest_entity_distance" -> {
				var entity = ScriptExtras.nearestCreature(player());
				yield entity == null ? "0" : format(Math.round(entity.distanceTo(player()) * 10) / 10.0);
			}
			case "s_distance_to" -> {
				LocalPlayer self = player();
				yield ScriptExtras.coordsHidden(self) ? "" : format(Math.round(Math.sqrt(self.distanceToSqr(number(arg(run, block, 0)) + 0.5, number(arg(run, block, 1)), number(arg(run, block, 2)) + 0.5)) * 10) / 10.0);
			}
			case "s_yaw" -> player() == null ? "0" : format(Math.round(net.minecraft.util.Mth.wrapDegrees(player().getYRot()) * 10) / 10.0);
			case "s_pitch" -> player() == null ? "0" : format(Math.round(player().getXRot() * 10) / 10.0);
			case "s_sprinting" -> bool(player() != null && player().isSprinting());
			case "s_flying" -> bool(player() != null && player().getAbilities().flying);
			case "s_gliding" -> bool(player() != null && player().isFallFlying());
			case "s_using" -> bool(player() != null && player().isUsingItem());
			case "s_xp_progress" -> player() == null ? "0" : Integer.toString(Math.round(player().experienceProgress * 100));
			case "s_air" -> player() == null ? "0" : Integer.toString(Math.max(0, player().getAirSupply()));
			case "s_attack_cooldown" -> player() == null ? "0" : Integer.toString(Math.round(player().getAttackStrengthScale(0) * 100));
			case "s_block_at" -> {
				LocalPlayer self = player();
				var pos = net.minecraft.core.BlockPos.containing(number(arg(run, block, 0)), number(arg(run, block, 1)), number(arg(run, block, 2)));
				yield ScriptExtras.visibleBlock(self, pos);
			}
			case "s_online_list" -> {
				Minecraft client = Minecraft.getInstance();
				var connection = client == null ? null : client.getConnection();
				yield connection == null ? "" : String.join(", ", connection.getListedOnlinePlayers().stream().map(info -> info.getProfile().name()).sorted(String.CASE_INSENSITIVE_ORDER).toList());
			}
			case "s_focused" -> bool(Minecraft.getInstance() != null && Minecraft.getInstance().isWindowActive());
			case "s_fps" -> Minecraft.getInstance() == null ? "0" : Integer.toString(Minecraft.getInstance().getFps());
			case "s_timed_out" -> bool(run.timedOut);
			case "s_answer" -> lastAnswer;
			case "s_choice" -> lastChoice;
			case "s_site_online" -> Integer.toString(OnlineStats.total());
			case "s_site_server" -> Integer.toString(OnlineStats.server(arg(run, block, 0)));
			case "s_announcement" -> AnnouncementStore.titleOf(AnnouncementStore.latest());
			case "s_timestamp" -> Long.toString(System.currentTimeMillis() / 1000);
			case "op_extract" -> ScriptExtras.extract(arg(run, block, 0), block.text(1), (int) Math.clamp(number(arg(run, block, 2)), 1, 1000));
			case "op_regex" -> {
				String result = ScriptExtras.regex(arg(run, block, 0), arg(run, block, 1), (int) Math.clamp(number(arg(run, block, 2)), 1, 1000));
				if (result == null) {
					fail(block, "規則寫錯了或太複雜（最多 " + ScriptExtras.MAX_REGEX + " 字），已略過");
					yield "";
				}
				yield result;
			}
			case "op_format" -> ScriptExtras.format(number(arg(run, block, 0)), block.text(1));
			case "op_max" -> format(Math.max(number(arg(run, block, 0)), number(arg(run, block, 1))));
			case "op_min" -> format(Math.min(number(arg(run, block, 0)), number(arg(run, block, 1))));
			case "op_pow" -> format(Math.pow(number(arg(run, block, 0)), Math.clamp(number(arg(run, block, 1)), -100, 100)));
			case "op_case" -> block.text(1).equals("小寫") ? arg(run, block, 0).toLowerCase(Locale.ROOT) : arg(run, block, 0).toUpperCase(Locale.ROOT);
			case "op_trim" -> arg(run, block, 0).strip();
			case "op_elapsed" -> format(System.currentTimeMillis() / 1000 - Math.round(number(arg(run, block, 0))));
			case "op_time_format" -> ScriptExtras.timeFormat(number(arg(run, block, 0)), block.text(1));
			case "list_index" -> {
				String needle = arg(run, block, 0).strip();
				List<String> items = listValue(run.group, block.text(1));
				int found = 0;
				for (int i = 0; i < items.size(); i++) {
					if (items.get(i).strip().equalsIgnoreCase(needle)) {
						found = i + 1;
						break;
					}
				}
				yield Integer.toString(found);
			}
			case "list_random" -> {
				List<String> items = listValue(run.group, block.text(0));
				yield items.isEmpty() ? "" : items.get(ThreadLocalRandom.current().nextInt(items.size()));
			}
			case "map_get" -> ScriptExtras.mapGet(listValue(run.group, block.text(0)), arg(run, block, 1));
			case "map_has" -> bool(ScriptExtras.mapHas(listValue(run.group, block.text(0)), arg(run, block, 1)));
			case "op_neq" -> bool(compare(arg(run, block, 0), arg(run, block, 1)) != 0);
			case "op_gte" -> bool(compare(arg(run, block, 0), arg(run, block, 1)) >= 0);
			case "op_lte" -> bool(compare(arg(run, block, 0), arg(run, block, 1)) <= 0);
			case "op_between" -> {
				double value = number(arg(run, block, 0));
				double a = number(arg(run, block, 1));
				double b = number(arg(run, block, 2));
				yield bool(isNumber(arg(run, block, 0)) && value >= Math.min(a, b) && value <= Math.max(a, b));
			}
			case "op_starts" -> bool(arg(run, block, 0).toLowerCase(Locale.ROOT).startsWith(arg(run, block, 1).toLowerCase(Locale.ROOT)));
			case "op_ends" -> bool(arg(run, block, 0).toLowerCase(Locale.ROOT).endsWith(arg(run, block, 1).toLowerCase(Locale.ROOT)));
			case "op_is_number" -> bool(isNumber(arg(run, block, 0)) && Double.isFinite(Double.parseDouble(arg(run, block, 0).strip())));
			case "op_index" -> {
				String needle = arg(run, block, 0);
				String text = arg(run, block, 1);
				int at = needle.isEmpty() ? -1 : text.toLowerCase(Locale.ROOT).indexOf(needle.toLowerCase(Locale.ROOT));
				yield at < 0 ? "0" : Integer.toString(text.codePointCount(0, at) + 1);
			}
			case "op_split" -> {
				String separator = arg(run, block, 1);
				String text = arg(run, block, 0);
				List<String> parts = separator.isEmpty() ? text.codePoints().mapToObj(Character::toString).toList() : List.of(text.split(Pattern.quote(separator), -1));
				int index = (int) Math.clamp(number(arg(run, block, 2)), -1, Integer.MAX_VALUE - 1) - 1;
				yield index >= 0 && index < parts.size() ? parts.get(index).strip() : "";
			}
			case "op_pick" -> {
				List<String> items = items(arg(run, block, 0));
				yield items.isEmpty() ? "" : items.get(ThreadLocalRandom.current().nextInt(items.size()));
			}
			case "op_if" -> truthy(arg(run, block, 0)) ? arg(run, block, 1) : arg(run, block, 2);
			case "s_gui_open" -> bool(gameScreen() != null);
			case "s_gui_title" -> gameScreen() == null ? "" : gameScreen().getTitle().getString();
			case "s_hover_slot" -> {
				var hovered = gameScreen() instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?> container
						? ((net.brilliantw.richpresence.mixin.AbstractContainerScreenAccessor) container).brilliant$hoveredSlot() : null;
				yield hovered == null ? "0" : Integer.toString(((net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?>) gameScreen()).getMenu().slots.indexOf(hovered) + 1);
			}
			case "s_slot_item" -> {
				net.minecraft.client.gui.screens.Screen screen = gameScreen();
				net.minecraft.world.inventory.Slot slot = screen == null ? null : slot(block, screen, number(arg(run, block, 0)), true);
				yield slot == null || !slot.isActive() || !slot.hasItem() ? "" : slot.getItem().getHoverName().getString();
			}
			case "s_pressing" -> {
				KeyMapping key = mapping(block.text(0));
				yield bool(key != null && key.isDown());
			}
			case "s_key" -> {
				int index = ScriptBlocks.KEYS.indexOf(block.text(0));
				yield bool(index >= 0 && KEYS[index] != null && KEYS[index].isDown());
			}
			case "s_hotkey" -> {
				String name = block.text(0);
				int combo = modifiers(name);
				yield bool(ScriptBlocks.MODIFIERS.contains(name) ? (KeyCombos.held() & combo) == combo : CommandKeys.keyDown(Minecraft.getInstance(), keyCode(name)));
			}
			case "s_timer" -> format(Math.round((System.currentTimeMillis() - timerStart) / 100.0) / 10.0);
			case "op_math" -> {
				double value = number(arg(run, block, 1));
				yield format(switch (block.text(0)) {
					case "平方根" -> value < 0 ? 0 : Math.sqrt(value);
					case "無條件捨去" -> Math.floor(value);
					case "無條件進位" -> Math.ceil(value);
					default -> Math.abs(value);
				});
			}
			case "op_letter" -> {
				String text = arg(run, block, 0);
				int index = (int) number(arg(run, block, 1)) - 1;
				int count = text.codePointCount(0, text.length());
				yield index < 0 || index >= count ? "" : new String(Character.toChars(text.codePointAt(text.offsetByCodePoints(0, index))));
			}
			case "op_replace" -> {
				String find = arg(run, block, 1);
				String replaced = find.isEmpty() ? arg(run, block, 0) : arg(run, block, 0).replace(find, arg(run, block, 2));
				yield replaced.length() > 2000 ? replaced.substring(0, 2000) : replaced;
			}
			case "op_add" -> format(number(arg(run, block, 0)) + number(arg(run, block, 1)));
			case "op_sub" -> format(number(arg(run, block, 0)) - number(arg(run, block, 1)));
			case "op_mul" -> format(number(arg(run, block, 0)) * number(arg(run, block, 1)));
			case "op_div" -> {
				double divisor = number(arg(run, block, 1));
				yield divisor == 0 ? "0" : format(number(arg(run, block, 0)) / divisor);
			}
			case "op_mod" -> {
				double divisor = number(arg(run, block, 1));
				double value = number(arg(run, block, 0));
				yield divisor == 0 ? "0" : format(value - Math.floor(value / divisor) * divisor);
			}
			case "op_round" -> format(Math.round(number(arg(run, block, 0))));
			case "op_random" -> {
				double a = Math.clamp(number(arg(run, block, 0)), -1e12, 1e12);
				double b = Math.clamp(number(arg(run, block, 1)), -1e12, 1e12);
				double low = Math.min(a, b);
				double high = Math.max(a, b);
				yield low == Math.floor(low) && high == Math.floor(high)
						? Long.toString(ThreadLocalRandom.current().nextLong((long) low, (long) high + 1))
						: format(low + ThreadLocalRandom.current().nextDouble() * (high - low));
			}
			case "op_gt" -> bool(compare(arg(run, block, 0), arg(run, block, 1)) > 0);
			case "op_lt" -> bool(compare(arg(run, block, 0), arg(run, block, 1)) < 0);
			case "op_eq" -> bool(compare(arg(run, block, 0), arg(run, block, 1)) == 0);
			case "op_and" -> bool(truthy(arg(run, block, 0)) && truthy(arg(run, block, 1)));
			case "op_or" -> bool(truthy(arg(run, block, 0)) || truthy(arg(run, block, 1)));
			case "op_not" -> bool(!truthy(arg(run, block, 0)));
			case "op_join" -> {
				String joined = arg(run, block, 0) + arg(run, block, 1);
				yield joined.length() > 2000 ? joined.substring(0, 2000) : joined;
			}
			case "op_contains" -> bool(arg(run, block, 0).toLowerCase(Locale.ROOT).contains(arg(run, block, 1).toLowerCase(Locale.ROOT)));
			case "op_length" -> {
				String text = arg(run, block, 0);
				yield Integer.toString(text.codePointCount(0, text.length()));
			}
			case "var_get" -> getVariable(run, block.text(0));
			case "list_item" -> {
				List<String> list = listValue(run.group, block.text(0));
				int index = (int) Math.clamp(number(arg(run, block, 1)), -1, Integer.MAX_VALUE - 1) - 1;
				yield index >= 0 && index < list.size() ? list.get(index) : "";
			}
			case "list_length" -> Integer.toString(listValue(run.group, block.text(0)).size());
			case "list_contains" -> {
				String needle = arg(run, block, 1).strip();
				yield bool(listValue(run.group, block.text(0)).stream().anyMatch(item -> item.strip().equalsIgnoreCase(needle)));
			}
			case "list_all" -> {
				String joined = String.join(", ", listValue(run.group, block.text(0)));
				yield joined.length() > Scripts.MAX_VALUE ? joined.substring(0, Scripts.MAX_VALUE) : joined;
			}
			case "proc_arg" -> {
				int index = "2".equals(block.text(0)) ? 1 : 0;
				for (Frame frame : run.frames) {
					if (frame.args != null) {
						yield frame.args[index];
					}
				}
				yield "";
			}
			default -> "";
		};
	}

	private static long dayTime() {
		LocalPlayer self = player();
		if (self == null) {
			return -1;
		}
		//? if >=26.1 {
		return self.level().getDefaultClockTime();
		//?} else {
		/*return self.level().getDayTime();
		*///?}
	}

	static float[] lookAt(LocalPlayer player, net.minecraft.world.phys.Vec3 target) {
		net.minecraft.world.phys.Vec3 eye = player.getEyePosition();
		double dx = target.x - eye.x;
		double dy = target.y - eye.y;
		double dz = target.z - eye.z;
		float yaw = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90f;
		float pitch = (float) Math.clamp(-Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz))), -90, 90);
		return new float[] { yaw, pitch };
	}

	private static void loopJump(Run run, Scripts.Block block, boolean exit) {
		List<Frame> frames = new ArrayList<>(run.frames);
		for (int i = 0; i < frames.size() - 1; i++) {
			Frame body = frames.get(i);
			if (body.args != null) {
				break;
			}
			Frame parent = frames.get(i + 1);
			if (parent.index < parent.list.size()) {
				Scripts.Block owner = parent.list.get(parent.index);
				if (LOOP_OPS.contains(owner.op) && owner.body == body.list) {
					for (int k = 0; k < i; k++) {
						run.frames.pop();
					}
					if (exit) {
						run.frames.pop();
						parent.index++;
					} else {
						body.index = body.list.size();
					}
					return;
				}
			}
		}
		fail(block, exit ? "「跳出迴圈」要放在重複類的積木裡面" : "「跳過這一次」要放在重複類的積木裡面");
		frames.getFirst().index++;
	}

	private static boolean walkStep(Run run, Scripts.Block block, long now) {
		LocalPlayer player = player();
		KeyMapping forward = mapping("前進");
		if (player == null || !playerControl(block)) {
			release(forward);
			return true;
		}
		if (ScriptExtras.coordsHidden(player)) {
			release(forward);
			fail(block, "伺服器隱藏了座標資訊，無法走到座標");
			return true;
		}
		double tx = number(eval(run, block.args.get(0))) + 0.5;
		double tz = number(eval(run, block.args.get(1))) + 0.5;
		double dx = tx - player.getX();
		double dz = tz - player.getZ();
		double distance = Math.sqrt(dx * dx + dz * dz);
		if (run.walkBlock != block) {
			run.walkBlock = block;
			run.walkStart = now;
			run.walkBest = distance;
			run.walkProgressAt = now;
		}
		if (distance < 0.8) {
			release(forward);
			return true;
		}
		if (distance < run.walkBest - 0.3) {
			run.walkBest = distance;
			run.walkProgressAt = now;
		}
		if (now - run.walkProgressAt > 3000) {
			release(forward);
			fail(block, "走不過去（可能被擋住或卡住），已停在離目標 " + Math.round(distance) + " 格的地方");
			return true;
		}
		if (now - run.walkStart > 300_000) {
			release(forward);
			fail(block, "走了 5 分鐘還沒到，已停止");
			return true;
		}
		float target = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90f;
		float yaw = player.getYRot() + Math.clamp(net.minecraft.util.Mth.wrapDegrees(target - player.getYRot()), -TURN_SPEED, TURN_SPEED);
		player.setYRot(yaw);
		player.setYHeadRot(yaw);
		hold(run, forward, now + 200, false);
		return false;
	}

	private static boolean moveAllStep(Run run, Scripts.Block block) {
		Minecraft client = Minecraft.getInstance();
		net.minecraft.client.gui.screens.Screen screen = client == null ? null : targetScreen(block, true);
		if (screen == null) {
			return true;
		}
		if (screen instanceof net.minecraft.client.gui.screens.inventory.InventoryScreen || screen instanceof net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen) {
			fail(block, "請先打開箱子等容器，自己的背包沒有「另一邊」可以移過去");
			return true;
		}
		if (!(screen instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?> container)) {
			return true;
		}
		String needle = eval(run, block.args.getFirst()).strip().toLowerCase(Locale.ROOT);
		var slots = container.getMenu().slots;
		for (int i = 0; i < slots.size(); i++) {
			var slot = slots.get(i);
			if (slot.container instanceof net.minecraft.world.entity.player.Inventory && slot.isActive() && slot.hasItem() && !run.movedSlots.contains(i)
					&& (needle.isEmpty() || slot.getItem().getHoverName().getString().toLowerCase(Locale.ROOT).contains(needle))) {
				run.movedSlots.add(i);
				click(screen, slot, 0, "QUICK_MOVE");
				return ++run.moveStage >= 64;
			}
		}
		if (run.moveStage == 0) {
			fail(block, "背包裡沒有名稱含有「" + needle + "」的物品");
		}
		return true;
	}

	public static void onSound(String id, double x, double y, double z) {
		LocalPlayer player = player();
		if (player == null || !active()) {
			return;
		}
		double distance = Math.sqrt(player.distanceToSqr(x, y, z));
		fire("ev_sound", hat -> {
			String wanted = HEARD.getOrDefault(hat.text(1), "");
			return distance <= Math.clamp(number(hat.text(0)), 0, HEARING) && (wanted.isEmpty() || id.contains(wanted));
		}, Map.of("message", id));
	}

	private static void watchWorld(Minecraft client, List<Scripts.Stack> active, long now) {
		java.time.LocalDateTime time = java.time.LocalDateTime.now();
		String minuteKey = time.toLocalDate() + "T" + time.getHour() + ":" + time.getMinute();
		DAILY.keySet().retainAll(new java.util.HashSet<>(active));
		startAll(active, "ev_daily", hat -> {
			return (int) number(hat.text(0)) == time.getHour() && (int) number(hat.text(1)) == time.getMinute();
		}, Map.of(), stack -> !minuteKey.equals(DAILY.put(stack, minuteKey)));
		net.minecraft.client.gui.screens.Screen open = gameScreen();
		if (open instanceof ChatScreen || open instanceof AskScreen) {
			open = null;
		}
		if (open != lastScreen) {
			if (open != null) {
				String title = open.getTitle().getString();
				startAll(active, "ev_gui_open", hat -> findText(hat.text(0), List.of(title)) != null, Map.of("message", title), null);
				lastScreenTitle = title;
			} else {
				startAll(active, "ev_gui_close", hat -> true, Map.of("message", lastScreenTitle), null);
			}
			lastScreen = open;
		}
		for (int i = 0; i < KEYS.length; i++) {
			boolean down = KEYS[i] != null && KEYS[i].isDown();
			if (KEY_DOWN[i] && !down) {
				String index = ScriptBlocks.KEYS.get(i);
				startAll(active, "ev_key_release", hat -> hat.text(0).equals(index), Map.of(), null);
			}
			KEY_DOWN[i] = down;
		}
		LocalPlayer player = client.player;
		if (player == null) {
			return;
		}
		boolean dead = player.isDeadOrDying();
		if (dead && !wasDead) {
			startAll(active, "ev_death", hat -> true, Map.of(), null);
		} else if (!dead && wasDead) {
			startAll(active, "ev_respawn", hat -> true, Map.of(), null);
		}
		wasDead = dead;
		AREAS.keySet().retainAll(new java.util.HashSet<>(active));
		for (Scripts.Stack stack : active) {
			if (stack.blocks.isEmpty() || !(stack.blocks.getFirst().op.equals("ev_area_enter") || stack.blocks.getFirst().op.equals("ev_area_leave"))) {
				continue;
			}
			Scripts.Block hat = stack.blocks.getFirst();
			if (ScriptExtras.coordsHidden(player)) {
				AREAS.remove(stack);
				continue;
			}
			double x1 = number(hat.text(0));
			double z1 = number(hat.text(1));
			double x2 = number(hat.text(2));
			double z2 = number(hat.text(3));
			boolean inside = player.getX() >= Math.min(x1, x2) && player.getX() <= Math.max(x1, x2) + 1 && player.getZ() >= Math.min(z1, z2) && player.getZ() <= Math.max(z1, z2) + 1;
			Boolean before = AREAS.put(stack, inside);
			if (before != null && before != inside && inside == hat.op.equals("ev_area_enter")) {
				start(stack, Map.of(), true);
			}
		}
		String dimension = CoordTools.dimensionName(player.level().dimension());
		if (lastDimension != null && !lastDimension.equals(dimension)) {
			startAll(active, "ev_dimension", hat -> true, Map.of("message", dimension), null);
		}
		lastDimension = dimension;
		var held = player.getMainHandItem();
		String heldKey = held.isEmpty() ? "" : held.getItem() + "|" + held.getHoverName().getString();
		if (lastHeld != null && !lastHeld.equals(heldKey)) {
			startAll(active, "ev_held", hat -> true, Map.of("message", held.isEmpty() ? "" : held.getHoverName().getString()), null);
		}
		lastHeld = heldKey;
		ITEM_COUNTS.keySet().retainAll(new java.util.HashSet<>(active));
		for (Scripts.Stack stack : active) {
			if (stack.blocks.isEmpty() || !stack.blocks.getFirst().op.equals("ev_item_count")) {
				continue;
			}
			int count = ScriptExtras.inventoryCount(player, stack.blocks.getFirst().text(0));
			Integer before = ITEM_COUNTS.put(stack, count);
			if (before != null && before != count) {
				start(stack, Map.of("message", Integer.toString(count)), true);
			}
		}
		long dayTime = dayTime();
		if (dayTime >= 0) {
			boolean day = dayTime % 24000 < 12300;
			if (lastDay != null && lastDay != day) {
				startAll(active, day ? "ev_dawn" : "ev_dusk", hat -> true, Map.of(), null);
			}
			lastDay = day;
		}
		boolean wantsAnnouncements = active.stream().anyMatch(stack -> !stack.blocks.isEmpty() && stack.blocks.getFirst().op.equals("ev_announcement"));
		if (wantsAnnouncements && now - announcementCheckedAt > 300_000) {
			announcementCheckedAt = now;
			AnnouncementStore.refreshAll(unread -> client.execute(() -> {
				var latest = AnnouncementStore.latest();
				String id = latest == null ? null : latest.id;
				if (id != null && lastAnnouncement != null && !id.equals(lastAnnouncement) && active()) {
					fire("ev_announcement", hat -> true, Map.of("message", AnnouncementStore.titleOf(latest)));
				}
				if (id != null) {
					lastAnnouncement = id;
				}
			}));
		}
	}

	private static void assign(Run run, String name, String value, Scripts.Block block) {
		if (name.isEmpty()) {
			fail(block, "還沒有選擇變數，請先在「變數」分類建立一個");
			return;
		}
		String key = Scripts.key(run.group, name);
		String clean = value.length() > Scripts.MAX_VALUE ? value.substring(0, Scripts.MAX_VALUE) : value;
		String old = Scripts.data().values.put(key, clean);
		valuesDirty = true;
		if (!clean.equals(old == null ? "0" : old) && active()) {
			for (Scripts.Stack stack : Scripts.activeStacks()) {
				if (!stack.blocks.isEmpty() && stack.blocks.getFirst().op.equals("ev_var_change")
						&& Scripts.key(Scripts.groupOf(stack), stack.blocks.getFirst().text(0)).equals(key)) {
					start(stack, Map.of("message", clean), true);
				}
			}
		}
	}

	private static String getVariable(Run run, String name) {
		return Scripts.data().values.getOrDefault(Scripts.key(run.group, name), "0");
	}

	private static void setVariable(Run run, Scripts.Block block, String value) {
		String name = block.text(0);
		if (name.isEmpty()) {
			fail(block, "還沒有選擇變數，請先在「變數」分類建立一個");
			return;
		}
		String key = Scripts.key(run.group, name);
		String clean = value.length() > Scripts.MAX_VALUE ? value.substring(0, Scripts.MAX_VALUE) : value;
		String old = Scripts.data().values.put(key, clean);
		valuesDirty = true;
		if (!clean.equals(old == null ? "0" : old) && active()) {
			for (Scripts.Stack stack : Scripts.activeStacks()) {
				if (!stack.blocks.isEmpty() && stack.blocks.getFirst().op.equals("ev_var_change")
						&& Scripts.key(Scripts.groupOf(stack), stack.blocks.getFirst().text(0)).equals(key)) {
					start(stack, Map.of("message", clean), true);
				}
			}
		}
	}

	private static List<String> list(Run run, Scripts.Block block, int slot) {
		String name = block.text(slot);
		if (name.isEmpty()) {
			fail(block, "還沒有選擇清單，請先在「變數」分類建立一個清單");
			return null;
		}
		return Scripts.data().listValues.computeIfAbsent(Scripts.listKey(run.group, name), key -> new ArrayList<>());
	}

	private static Scripts.Stack findProc(Run run, String name) {
		if (name.isEmpty()) {
			return null;
		}
		List<Scripts.Stack> candidates = new ArrayList<>();
		if (run.group != null) {
			candidates.addAll(run.group.stacks);
		}
		candidates.addAll(Scripts.activeStacks());
		for (Scripts.Stack stack : candidates) {
			if (!stack.disabled && !stack.blocks.isEmpty() && stack.blocks.getFirst().op.equals("proc_def") && stack.blocks.getFirst().text(0).strip().equals(name)) {
				return stack;
			}
		}
		return null;
	}

	static boolean truthy(String value) {
		String v = value == null ? "" : value.strip();
		return !(v.isEmpty() || v.equals("0") || v.equalsIgnoreCase("false"));
	}

	static String bool(boolean value) {
		return value ? "true" : "false";
	}

	static double number(String value) {
		try {
			double parsed = Double.parseDouble(value == null ? "" : value.strip());
			return Double.isFinite(parsed) ? parsed : 0;
		} catch (NumberFormatException e) {
			return 0;
		}
	}

	static boolean isNumber(String value) {
		try {
			Double.parseDouble(value.strip());
			return !value.isBlank();
		} catch (NumberFormatException e) {
			return false;
		}
	}

	static int compare(String a, String b) {
		if (isNumber(a) && isNumber(b)) {
			return Double.compare(number(a), number(b));
		}
		return a.strip().compareToIgnoreCase(b.strip());
	}

	static String format(double value) {
		if (!Double.isFinite(value)) {
			return "0";
		}
		if (value == Math.rint(value) && Math.abs(value) < 1e15) {
			return Long.toString((long) value);
		}
		String text = String.format(Locale.ROOT, "%.6f", value).replaceAll("0+$", "");
		return text.endsWith(".") ? text.substring(0, text.length() - 1) : text;
	}
}
