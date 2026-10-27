package net.brilliantw.richpresence;

import com.google.gson.Gson;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.zip.DataFormatException;
import net.fabricmc.loader.api.FabricLoader;

public final class Scripts {
	public static final class Arg {
		public String text = "";
		public Block block;

		public Arg() {}

		public Arg(String text) {
			this.text = text;
		}
	}

	public static final class Block {
		public String op = "";
		public List<Arg> args = new ArrayList<>();
		public List<Block> body = new ArrayList<>();
		public List<Block> elseBody = new ArrayList<>();

		public ScriptBlocks.Def def() {
			return ScriptBlocks.get(op);
		}

		public String text(int index) {
			return index < args.size() ? args.get(index).text : "";
		}
	}

	public static final class Note {
		public int x;
		public int y;
		public String text = "";

		public Note() {}

		public Note(int x, int y, String text) {
			this.x = x;
			this.y = y;
			this.text = text;
		}
	}

	public static final class Stack {
		public int x;
		public int y;
		public boolean disabled;
		public List<Block> blocks = new ArrayList<>();

		public Stack() {}

		public Stack(int x, int y, List<Block> blocks) {
			this.x = x;
			this.y = y;
			this.blocks = blocks;
		}
	}

	public static final class Group {
		public String id = newId();
		public String name = "預設";
		public boolean enabled = true;
		public List<String> variables = new ArrayList<>();
		public List<String> lists = new ArrayList<>();
		public List<Note> notes = new ArrayList<>();
		public List<Stack> stacks = new ArrayList<>();

		public Group() {}

		public Group(String name) {
			this.name = name;
		}
	}

	public static final class Data {
		public boolean enabled = true;
		public List<String> variables = new ArrayList<>();
		public List<String> lists = new ArrayList<>();
		public List<Group> groups = new ArrayList<>();
		public List<Stack> stacks;
		public Map<String, String> values = new LinkedHashMap<>();
		public Map<String, List<String>> listValues = new LinkedHashMap<>();
		public int selected;
		public boolean monitor = true;
		public boolean monitorCollapsed;
		public int monitorX = 4;
		public int monitorY = 40;
		public int monitorWidth = 200;
		public boolean tutorialSeen;
		public int paletteWidth = 196;
		public int monitorHeight = 170;
	}

	public static final String EXPORT_PREFIX = "BSM-SCRIPT:";
	public static final int MAX_BLOCKS = 5000;
	public static final int MAX_DEPTH = 12;
	public static final int MAX_TEXT = 256;
	public static final int MAX_VARIABLES = 30;
	public static final int MAX_GROUPS = 20;
	public static final int MAX_NAME = 20;
	public static final int MAX_LISTS = 20;
	public static final int MAX_LIST_ITEMS = 200;
	public static final int MAX_NOTES = 30;
	public static final int MAX_NOTE = 200;
	public static final int MAX_VALUE = 2000;
	private static final Gson COMPACT = new Gson();

	private static Data data;

	private Scripts() {}

	static String newId() {
		return UUID.randomUUID().toString().substring(0, 8);
	}

	public static Block create(ScriptBlocks.Def def) {
		Block block = new Block();
		block.op = def.id();
		for (ScriptBlocks.Slot slot : def.slots()) {
			List<String> names = names(slot.kind(), current());
			block.args.add(new Arg(names != null ? names.isEmpty() ? "" : names.getFirst() : slot.value()));
		}
		return block;
	}

	public static List<String> names(ScriptBlocks.SlotKind kind, Group group) {
		return switch (kind) {
			case VARIABLE -> variablesOf(group);
			case LIST -> listsOf(group);
			case PROC -> procsOf(group);
			default -> null;
		};
	}

	public static List<String> variablesOf(Group group) {
		LinkedHashSet<String> names = new LinkedHashSet<>();
		if (group != null) {
			names.addAll(group.variables);
		}
		names.addAll(data().variables);
		return new ArrayList<>(names);
	}

	public static List<String> listsOf(Group group) {
		LinkedHashSet<String> names = new LinkedHashSet<>();
		if (group != null) {
			names.addAll(group.lists);
		}
		names.addAll(data().lists);
		return new ArrayList<>(names);
	}

	public static List<String> usedNames(Group group, ScriptBlocks.SlotKind kind) {
		Set<String> used = new HashSet<>();
		if (group != null) {
			for (Stack stack : group.stacks) {
				collectNames(stack.blocks, kind, used);
			}
		}
		List<String> known = names(kind, group);
		return known == null ? List.of() : known.stream().filter(used::contains).toList();
	}

	private static void collectNames(List<Block> blocks, ScriptBlocks.SlotKind kind, Set<String> into) {
		for (Block block : blocks) {
			ScriptBlocks.Def def = block.def();
			List<ScriptBlocks.Slot> slots = def == null ? List.of() : def.slots();
			for (int i = 0; i < block.args.size(); i++) {
				Arg arg = block.args.get(i);
				if (arg.block != null) {
					collectNames(List.of(arg.block), kind, into);
				} else if (i < slots.size() && slots.get(i).kind() == kind) {
					into.add(arg.text);
				}
			}
			collectNames(block.body, kind, into);
			collectNames(block.elseBody, kind, into);
		}
	}

	public static List<String> procsOf(Group group) {
		LinkedHashSet<String> names = new LinkedHashSet<>();
		if (group != null) {
			for (Stack stack : group.stacks) {
				if (!stack.blocks.isEmpty() && stack.blocks.getFirst().op.equals("proc_def") && !stack.blocks.getFirst().text(0).isBlank()) {
					names.add(stack.blocks.getFirst().text(0).strip());
				}
			}
		}
		return new ArrayList<>(names);
	}

	public static String key(Group group, String name) {
		return group != null && group.variables.contains(name) ? group.id + "/" + name : name;
	}

	public static String listKey(Group group, String name) {
		return group != null && group.lists.contains(name) ? group.id + "/" + name : name;
	}

	public static Group groupOf(Stack stack) {
		for (Group group : data().groups) {
			for (Stack candidate : group.stacks) {
				if (candidate == stack) {
					return group;
				}
			}
		}
		return null;
	}

	public static Group current() {
		Data d = data();
		d.selected = Math.clamp(d.selected, 0, d.groups.size() - 1);
		return d.groups.get(d.selected);
	}

	public static List<Stack> activeStacks() {
		List<Stack> stacks = new ArrayList<>();
		for (Group group : data().groups) {
			if (group.enabled) {
				for (Stack stack : group.stacks) {
					if (!stack.disabled) {
						stacks.add(stack);
					}
				}
			}
		}
		return stacks;
	}

	public static String uniqueName(String base) {
		String name = base;
		for (int i = 2; nameTaken(name); i++) {
			name = base + " " + i;
		}
		return name;
	}

	private static boolean nameTaken(String name) {
		return data().groups.stream().anyMatch(group -> group.name.equals(name));
	}

	private static List<String> scopeNames(ScriptBlocks.SlotKind kind, Group scope) {
		boolean list = kind == ScriptBlocks.SlotKind.LIST;
		return scope == null ? list ? data().lists : data().variables : list ? scope.lists : scope.variables;
	}

	private static String storeKey(ScriptBlocks.SlotKind kind, Group scope, String name) {
		return scope == null ? name : scope.id + "/" + name;
	}

	private static boolean affects(ScriptBlocks.SlotKind kind, Group scope, Group group, String name) {
		return scope == null ? !scopeNames(kind, group).contains(name) : scope == group;
	}

	public static void rename(ScriptBlocks.SlotKind kind, Group scope, String from, String to) {
		List<String> names = scopeNames(kind, scope);
		int index = names.indexOf(from);
		if (index < 0) {
			return;
		}
		names.set(index, to);
		for (Group group : data().groups) {
			if (affects(kind, scope, group, from)) {
				for (Stack stack : group.stacks) {
					replaceName(stack.blocks, kind, from, to);
				}
			}
		}
		moveValue(kind, storeKey(kind, scope, from), storeKey(kind, scope, to));
	}

	public static void delete(ScriptBlocks.SlotKind kind, Group scope, String name) {
		scopeNames(kind, scope).remove(name);
		for (Group group : data().groups) {
			if (affects(kind, scope, group, name)) {
				List<String> left = names(kind, group);
				String fallback = left.isEmpty() ? "" : left.getFirst();
				for (Stack stack : group.stacks) {
					replaceName(stack.blocks, kind, name, fallback);
				}
			}
		}
		moveValue(kind, storeKey(kind, scope, name), null);
	}

	private static void moveValue(ScriptBlocks.SlotKind kind, String from, String to) {
		if (kind == ScriptBlocks.SlotKind.LIST) {
			List<String> value = data().listValues.remove(from);
			if (to != null && value != null) {
				data().listValues.put(to, value);
			}
		} else {
			String value = data().values.remove(from);
			if (to != null && value != null) {
				data().values.put(to, value);
			}
		}
	}

	public static Group scopeOf(ScriptBlocks.SlotKind kind, Group group, String name) {
		return group != null && scopeNames(kind, group).contains(name) ? group : null;
	}

	private static void replaceName(List<Block> blocks, ScriptBlocks.SlotKind kind, String from, String to) {
		for (Block block : blocks) {
			ScriptBlocks.Def def = block.def();
			List<ScriptBlocks.Slot> slots = def == null ? List.of() : def.slots();
			for (int i = 0; i < block.args.size(); i++) {
				Arg arg = block.args.get(i);
				if (i < slots.size() && slots.get(i).kind() == kind && arg.text.equals(from)) {
					arg.text = to;
				}
				if (arg.block != null) {
					replaceName(List.of(arg.block), kind, from, to);
				}
			}
			replaceName(block.body, kind, from, to);
			replaceName(block.elseBody, kind, from, to);
		}
	}

	public static Block copy(Block block) {
		return COMPACT.fromJson(COMPACT.toJson(block), Block.class);
	}

	private static Path file() {
		return FabricLoader.getInstance().getConfigDir().resolve("brilliant-server-mod-scripts.json");
	}

	public static Data data() {
		if (data == null) {
			data = sanitize(new Data());
			try {
				if (Files.exists(file())) {
					Data loaded = COMPACT.fromJson(Files.readString(file(), StandardCharsets.UTF_8), Data.class);
					if (loaded != null) {
						data = sanitize(loaded);
					}
				}
			} catch (Exception ignored) {
			}
		}
		return data;
	}

	public static void save() {
		try {
			Path temp = file().resolveSibling(file().getFileName() + ".tmp");
			Files.writeString(temp, COMPACT.toJson(data()), StandardCharsets.UTF_8);
			Files.move(temp, file(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
		} catch (IOException e) {
			System.err.println("[brilliant-server-mod] Failed to save scripts: " + e.getMessage());
		}
	}

	static Data sanitize(Data input) {
		Data clean = new Data();
		clean.enabled = input.enabled;
		clean.selected = input.selected;
		clean.monitor = input.monitor;
		clean.monitorCollapsed = input.monitorCollapsed;
		clean.monitorX = Math.clamp(input.monitorX, 0, 4000);
		clean.monitorY = Math.clamp(input.monitorY, 0, 4000);
		clean.monitorWidth = Math.clamp(input.monitorWidth, 160, 2000);
		clean.tutorialSeen = input.tutorialSeen;
		clean.paletteWidth = Math.clamp(input.paletteWidth == 0 ? 196 : input.paletteWidth, 150, 1000);
		clean.monitorHeight = Math.clamp(input.monitorHeight, 60, 2000);
		clean.variables = cleanNames(input.variables, MAX_VARIABLES);
		clean.lists = cleanNames(input.lists, MAX_LISTS);
		LinkedHashSet<String> ids = new LinkedHashSet<>();
		int[] budget = { MAX_BLOCKS };
		List<Group> groups = new ArrayList<>();
		if (input.groups != null) {
			groups.addAll(input.groups.stream().filter(group -> group != null).toList());
		}
		if (input.stacks != null && !input.stacks.isEmpty()) {
			Group legacy = new Group("預設");
			legacy.stacks = input.stacks;
			groups.add(0, legacy);
		}
		for (Group group : groups) {
			if (clean.groups.size() >= MAX_GROUPS) {
				break;
			}
			String name = group.name == null || group.name.isBlank() ? "分組" : group.name.strip();
			Group fixed = new Group(name.length() > MAX_NAME ? name.substring(0, MAX_NAME) : name);
			fixed.enabled = group.enabled;
			if (group.id != null && group.id.matches("[0-9a-f]{8}") && ids.add(group.id)) {
				fixed.id = group.id;
			} else {
				ids.add(fixed.id);
			}
			fixed.variables = cleanNames(group.variables, MAX_VARIABLES);
			fixed.lists = cleanNames(group.lists, MAX_LISTS);
			if (group.notes != null) {
				for (Note note : group.notes) {
					if (note != null && note.text != null && !note.text.isBlank() && fixed.notes.size() < MAX_NOTES) {
						String text = note.text.strip();
						fixed.notes.add(new Note(Math.clamp(note.x, -5000, 5000), Math.clamp(note.y, -5000, 5000), text.length() > MAX_NOTE ? text.substring(0, MAX_NOTE) : text));
					}
				}
			}
			if (group.stacks != null) {
				for (Stack stack : group.stacks) {
					if (stack == null) {
						continue;
					}
					List<Block> blocks = sanitizeList(stack.blocks, 0, budget);
					if (!blocks.isEmpty()) {
						Stack fixedStack = new Stack(Math.clamp(stack.x, -5000, 5000), Math.clamp(stack.y, -5000, 5000), blocks);
						fixedStack.disabled = stack.disabled;
						fixed.stacks.add(fixedStack);
					}
				}
			}
			clean.groups.add(fixed);
		}
		if (clean.groups.isEmpty()) {
			clean.groups.add(new Group("預設"));
		}
		clean.selected = Math.clamp(clean.selected, 0, clean.groups.size() - 1);
		LinkedHashSet<String> keys = new LinkedHashSet<>(clean.variables);
		LinkedHashSet<String> listKeys = new LinkedHashSet<>(clean.lists);
		for (Group group : clean.groups) {
			group.variables.forEach(name -> keys.add(group.id + "/" + name));
			group.lists.forEach(name -> listKeys.add(group.id + "/" + name));
		}
		if (input.values != null) {
			input.values.forEach((key, value) -> {
				if (key != null && value != null && keys.contains(key)) {
					clean.values.put(key, value.length() > MAX_VALUE ? value.substring(0, MAX_VALUE) : value);
				}
			});
		}
		if (input.listValues != null) {
			input.listValues.forEach((key, items) -> {
				if (key != null && items != null && listKeys.contains(key)) {
					List<String> fixed = new ArrayList<>();
					for (String item : items) {
						if (item != null && fixed.size() < MAX_LIST_ITEMS) {
							fixed.add(item.length() > MAX_TEXT ? item.substring(0, MAX_TEXT) : item);
						}
					}
					clean.listValues.put(key, fixed);
				}
			});
		}
		return clean;
	}

	private static List<String> cleanNames(List<String> input, int max) {
		LinkedHashSet<String> names = new LinkedHashSet<>();
		if (input != null) {
			for (String name : input) {
				String trimmed = name == null ? "" : name.strip();
				if (!trimmed.isEmpty() && trimmed.length() <= MAX_NAME && names.size() < max) {
					names.add(trimmed);
				}
			}
		}
		return new ArrayList<>(names);
	}

	private static List<Block> sanitizeList(List<Block> blocks, int depth, int[] budget) {
		List<Block> clean = new ArrayList<>();
		if (blocks == null || depth > MAX_DEPTH) {
			return clean;
		}
		for (Block block : blocks) {
			Block fixed = sanitizeBlock(block, depth, budget);
			if (fixed != null) {
				clean.add(fixed);
			}
		}
		return clean;
	}

	private static Block sanitizeBlock(Block block, int depth, int[] budget) {
		ScriptBlocks.Def def = block == null ? null : block.def();
		if (def == null || budget[0] <= 0 || depth > MAX_DEPTH) {
			return null;
		}
		budget[0]--;
		Block clean = new Block();
		clean.op = def.id();
		List<ScriptBlocks.Slot> slots = def.slots();
		for (int i = 0; i < slots.size(); i++) {
			Arg source = block.args != null && i < block.args.size() && block.args.get(i) != null ? block.args.get(i) : new Arg(slots.get(i).value());
			Arg arg = new Arg(source.text == null ? "" : source.text.length() > MAX_TEXT ? source.text.substring(0, MAX_TEXT) : source.text);
			if (slots.get(i).kind() == ScriptBlocks.SlotKind.DROPDOWN && !slots.get(i).options().contains(arg.text)) {
				arg.text = slots.get(i).value();
			}
			boolean pluggable = slots.get(i).kind() == ScriptBlocks.SlotKind.TEXT || slots.get(i).kind() == ScriptBlocks.SlotKind.NUMBER
					|| slots.get(i).kind() == ScriptBlocks.SlotKind.BOOLEAN;
			if (pluggable && source.block != null) {
				Block nested = sanitizeBlock(source.block, depth + 1, budget);
				if (nested != null && nested.def().isReporter()
						&& (slots.get(i).kind() != ScriptBlocks.SlotKind.BOOLEAN || nested.def().shape() == ScriptBlocks.Shape.BOOLEAN)) {
					arg.block = nested;
				}
			}
			clean.args.add(arg);
		}
		if (def.hasBody()) {
			clean.body = sanitizeList(block.body, depth + 1, budget);
			if (def.shape() == ScriptBlocks.Shape.C_ELSE) {
				clean.elseBody = sanitizeList(block.elseBody, depth + 1, budget);
			}
		}
		return clean;
	}

	public static int count(List<Block> blocks) {
		int total = 0;
		for (Block block : blocks) {
			total += 1 + count(block.body) + count(block.elseBody);
			for (Arg arg : block.args) {
				if (arg.block != null) {
					total += count(List.of(arg.block));
				}
			}
		}
		return total;
	}

	public static int count() {
		return data().groups.stream().flatMap(group -> group.stacks.stream()).mapToInt(stack -> count(stack.blocks)).sum();
	}

	public static String export(Group group) {
		return export(group, data().variables, data().lists);
	}

	public static String export(Group group, List<String> variables, List<String> lists) {
		Data shared = new Data();
		shared.variables = new ArrayList<>(variables);
		shared.lists = new ArrayList<>(lists);
		shared.groups = new ArrayList<>(List.of(group));
		return EXPORT_PREFIX + CommandStore.deflate(COMPACT.toJson(shared));
	}

	public static String snapshot() {
		Data state = new Data();
		state.variables = data().variables;
		state.lists = data().lists;
		state.groups = data().groups;
		state.values = Map.of();
		state.listValues = Map.of();
		return COMPACT.toJson(state);
	}

	public static void restore(String json) {
		Data state = COMPACT.fromJson(json, Data.class);
		data().variables = state.variables;
		data().lists = state.lists == null ? new ArrayList<>() : state.lists;
		data().groups = state.groups;
		data().selected = Math.clamp(data().selected, 0, data().groups.size() - 1);
	}

	public static Data parse(String text) {
		String trimmed = text == null ? "" : text.strip();
		if (!trimmed.startsWith(EXPORT_PREFIX)) {
			throw new IllegalArgumentException("這不是腳本代碼，代碼應以 " + EXPORT_PREFIX + " 開頭");
		}
		try {
			Data parsed = COMPACT.fromJson(CommandStore.inflate(Base64.getDecoder().decode(trimmed.substring(EXPORT_PREFIX.length()).replaceAll("\\s", ""))), Data.class);
			Data clean = sanitize(parsed == null ? new Data() : parsed);
			if (clean.groups.stream().allMatch(group -> group.stacks.isEmpty())) {
				throw new IllegalArgumentException("這份代碼裡沒有任何積木");
			}
			return clean;
		} catch (DataFormatException | RuntimeException e) {
			if (e instanceof IllegalArgumentException && e.getMessage() != null && e.getMessage().startsWith("這份")) {
				throw (IllegalArgumentException) e;
			}
			throw new IllegalArgumentException("代碼不完整或已損毀，請確認有完整複製");
		}
	}

	public static int blocksOf(Data incoming) {
		return incoming.groups.stream().mapToInt(group -> group.stacks.stream().mapToInt(stack -> count(stack.blocks)).sum()).sum();
	}

	public static String fitProblem(Data incoming) {
		Data target = data();
		int blocks = blocksOf(incoming);
		int room = Math.max(0, MAX_BLOCKS - count());
		if (blocks > room) {
			return "空間不足：需要 " + blocks + " 塊，只剩 " + room + " 塊（上限 " + MAX_BLOCKS + "），請先刪除一些積木";
		}
		int groupRoom = Math.max(0, MAX_GROUPS - target.groups.size());
		if (incoming.groups.size() > groupRoom) {
			return "分組不足：需要 " + incoming.groups.size() + " 個，只能再新增 " + groupRoom + " 個（上限 " + MAX_GROUPS + "）";
		}
		long newVariables = incoming.variables.stream().filter(name -> !target.variables.contains(name)).count();
		if (target.variables.size() + newVariables > MAX_VARIABLES) {
			return "共用變數不足：需要 " + newVariables + " 個，只能再新增 " + Math.max(0, MAX_VARIABLES - target.variables.size()) + " 個";
		}
		long newLists = incoming.lists.stream().filter(name -> !target.lists.contains(name)).count();
		if (target.lists.size() + newLists > MAX_LISTS) {
			return "共用清單不足：需要 " + newLists + " 個，只能再新增 " + Math.max(0, MAX_LISTS - target.lists.size()) + " 個";
		}
		return null;
	}

	public static int merge(Data incoming) {
		if (fitProblem(incoming) != null) {
			return 0;
		}
		Data target = data();
		for (String name : incoming.variables) {
			if (!target.variables.contains(name) && target.variables.size() < MAX_VARIABLES) {
				target.variables.add(name);
			}
		}
		for (String name : incoming.lists) {
			if (!target.lists.contains(name) && target.lists.size() < MAX_LISTS) {
				target.lists.add(name);
			}
		}
		int added = 0;
		for (Group group : incoming.groups) {
			Group copy = new Group(uniqueName(group.name));
			copy.variables = new ArrayList<>(group.variables);
			copy.lists = new ArrayList<>(group.lists);
			copy.notes = new ArrayList<>(group.notes);
			copy.stacks.addAll(group.stacks);
			added += group.stacks.stream().mapToInt(stack -> count(stack.blocks)).sum();
			if (!copy.stacks.isEmpty()) {
				target.groups.add(copy);
				target.selected = target.groups.size() - 1;
			}
		}
		return added;
	}
}
