package net.brilliantw.richpresence;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;
import net.fabricmc.loader.api.FabricLoader;

public final class CommandStore {
	public static final class Entry {
		public String command = "";
		public String note = "";
		public String alias = "";
		public String key = "";
		public int mods;

		public Entry() {}

		public Entry(String command, String note) {
			this.command = command;
			this.note = note;
		}
	}

	public static final class Group {
		public String name = "";
		public List<Entry> commands = new ArrayList<>();
		public List<Group> children = new ArrayList<>();
		public transient boolean collapsed;

		public Group() {}

		public Group(String name) {
			this.name = name;
		}

		public int totalCommands() {
			return commands.size() + children.stream().mapToInt(Group::totalCommands).sum();
		}

		public int totalChildren() {
			return children.size() + children.stream().mapToInt(Group::totalChildren).sum();
		}
	}

	public record Node(Group group, Group parent, int depth, String path) {}

	public record PreviewLine(String group, boolean newGroup, Entry entry, boolean added) {}

	private static final class Data {
		List<Group> groups = new ArrayList<>();
	}

	public static final int MAX_DEPTH = 3;
	public static final int MAX_COMMAND = 256;
	public static final int MAX_NOTE = 100;
	public static final int MAX_NAME = 32;
	public static final String PATH_SEPARATOR = " › ";
	private static final String EXPORT_PREFIX = "BSM-CMD:";
	private static final int MAX_DECOMPRESSED = 4 * 1024 * 1024;
	public static final long MAX_IMPORT_FILE = 8 * 1024 * 1024;
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Gson COMPACT = new Gson();
	private static CommandStore instance;

	private final Path file;
	public final List<Group> groups;

	private CommandStore(Path file, List<Group> groups) {
		this.file = file;
		this.groups = groups;
	}

	public static CommandStore get() {
		if (instance == null) {
			instance = load(FabricLoader.getInstance().getConfigDir().resolve("brilliant-server-mod-commands.json"));
		}
		return instance;
	}

	private static CommandStore load(Path file) {
		if (!Files.exists(file)) {
			return new CommandStore(file, new ArrayList<>());
		}
		try {
			return new CommandStore(file, sanitize(GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8), Data.class)));
		} catch (Exception e) {
			try {
				Files.move(file, file.resolveSibling(file.getFileName() + "." + System.currentTimeMillis() + ".bak"));
			} catch (IOException ignored) {
			}
			return new CommandStore(file, new ArrayList<>());
		}
	}

	public void save() {
		try {
			Path temp = file.resolveSibling(file.getFileName() + ".tmp");
			Data data = new Data();
			data.groups = groups;
			Files.writeString(temp, GSON.toJson(data), StandardCharsets.UTF_8);
			Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
		} catch (IOException e) {
			System.err.println("[brilliant-server-mod] Failed to save commands: " + e.getMessage());
		}
	}

	public String replaceAll(List<Group> incoming) {
		String backup = null;
		try {
			if (Files.exists(file)) {
				Path target = file.resolveSibling(file.getFileName() + "." + System.currentTimeMillis() + ".bak");
				Files.copy(file, target);
				backup = target.getFileName().toString();
			}
		} catch (IOException e) {
			throw new IllegalStateException("無法備份現有指令，已取消覆蓋", e);
		}
		groups.clear();
		merge(groups, incoming);
		save();
		return backup;
	}

	public static List<Node> flatten(List<Group> groups, boolean skipCollapsed) {
		List<Node> nodes = new ArrayList<>();
		walk(groups, null, 0, "", skipCollapsed, nodes);
		return nodes;
	}

	private static void walk(List<Group> groups, Group parent, int depth, String prefix, boolean skipCollapsed, List<Node> nodes) {
		for (Group group : groups) {
			String path = prefix.isEmpty() ? group.name : prefix + PATH_SEPARATOR + group.name;
			nodes.add(new Node(group, parent, depth, path));
			if (!skipCollapsed || !group.collapsed) {
				walk(group.children, group, depth + 1, path, skipCollapsed, nodes);
			}
		}
	}

	public static String export(List<Group> groups) {
		Data data = new Data();
		data.groups = groups;
		return EXPORT_PREFIX + deflate(COMPACT.toJson(data));
	}

	static String deflate(String json) {
		Deflater deflater = new Deflater(Deflater.BEST_COMPRESSION);
		deflater.setInput(json.getBytes(StandardCharsets.UTF_8));
		deflater.finish();
		ByteArrayOutputStream output = new ByteArrayOutputStream();
		byte[] buffer = new byte[8192];
		while (!deflater.finished()) {
			output.write(buffer, 0, deflater.deflate(buffer));
		}
		deflater.end();
		return Base64.getEncoder().encodeToString(output.toByteArray());
	}

	static String inflate(byte[] compressed) throws DataFormatException {
		Inflater inflater = new Inflater();
		try {
			inflater.setInput(compressed);
			ByteArrayOutputStream output = new ByteArrayOutputStream();
			byte[] buffer = new byte[8192];
			while (!inflater.finished()) {
				int count = inflater.inflate(buffer);
				if (count == 0 && (inflater.needsInput() || inflater.needsDictionary())) {
					throw new DataFormatException("Truncated");
				}
				output.write(buffer, 0, count);
				if (output.size() > MAX_DECOMPRESSED) {
					throw new DataFormatException("Too large");
				}
			}
			return output.toString(StandardCharsets.UTF_8);
		} finally {
			inflater.end();
		}
	}

	public static boolean looksLikeCode(String text) {
		return text != null && text.trim().startsWith(EXPORT_PREFIX);
	}

	public static List<Group> parse(String text) {
		String trimmed = text == null ? "" : text.trim();
		if (trimmed.startsWith("﻿")) {
			trimmed = trimmed.substring(1).trim();
		}
		if (trimmed.isEmpty()) {
			throw new IllegalArgumentException("請貼上指令集代碼");
		}
		boolean prefixed = trimmed.startsWith(EXPORT_PREFIX);
		Data data;
		try {
			String json = prefixed
					? inflate(Base64.getDecoder().decode(trimmed.substring(EXPORT_PREFIX.length()).replaceAll("\\s", "")))
					: trimmed;
			data = COMPACT.fromJson(json, Data.class);
		} catch (RuntimeException | DataFormatException e) {
			throw new IllegalArgumentException(prefixed ? "代碼不完整或已損毀，請確認有從頭到尾完整複製" : "這不是有效的指令集代碼，代碼應以 " + EXPORT_PREFIX + " 開頭");
		}
		List<Group> groups = sanitize(data);
		if (groups.stream().allMatch(group -> group.totalCommands() == 0)) {
			throw new IllegalArgumentException("這份指令集裡沒有任何指令");
		}
		return groups;
	}

	public static int merge(List<Group> target, List<Group> incoming) {
		List<PreviewLine> lines = new ArrayList<>();
		mergeInto(target, incoming, "", false, lines);
		return (int) lines.stream().filter(PreviewLine::added).count();
	}

	public static List<PreviewLine> preview(List<Group> target, List<Group> incoming) {
		List<PreviewLine> lines = new ArrayList<>();
		mergeInto(copy(target), incoming, "", false, lines);
		return lines;
	}

	private static void mergeInto(List<Group> target, List<Group> incoming, String prefix, boolean parentNew, List<PreviewLine> lines) {
		for (Group group : incoming) {
			String path = prefix.isEmpty() ? group.name : prefix + PATH_SEPARATOR + group.name;
			Group existing = target.stream().filter(g -> g.name.equals(group.name)).findFirst().orElse(null);
			boolean isNew = existing == null;
			if (isNew) {
				existing = new Group(group.name);
				target.add(existing);
			}
			for (Entry entry : group.commands) {
				Group finalExisting = existing;
				boolean added = finalExisting.commands.stream().noneMatch(e -> e.command.equals(entry.command));
				if (added) {
					Entry copy = new Entry(entry.command, entry.note);
					copy.alias = entry.alias != null && !entry.alias.isEmpty() && !CommandAliases.all().containsKey(entry.alias) ? entry.alias : "";
					if (CommandKeys.key(entry) != null && flatten(target, false).stream().flatMap(n -> n.group().commands.stream())
							.noneMatch(e -> e.key.equals(entry.key) && e.mods == entry.mods)) {
						copy.key = entry.key;
						copy.mods = entry.mods;
					}
					finalExisting.commands.add(copy);
				}
				lines.add(new PreviewLine(path, isNew || parentNew, entry, added));
			}
			mergeInto(existing.children, group.children, path, isNew || parentNew, lines);
		}
	}

	private static List<Group> copy(List<Group> groups) {
		List<Group> result = new ArrayList<>();
		for (Group group : groups) {
			Group clone = new Group(group.name);
			group.commands.forEach(entry -> {
				Entry copy = new Entry(entry.command, entry.note);
				copy.alias = entry.alias;
				clone.commands.add(copy);
			});
			clone.children = copy(group.children);
			result.add(clone);
		}
		return result;
	}

	public static String cleanCommand(String command) {
		String cleaned = command == null ? "" : command.trim();
		while (cleaned.startsWith("/")) {
			cleaned = cleaned.substring(1).trim();
		}
		return truncate(cleaned.replaceAll("[\\r\\n]", " "), MAX_COMMAND);
	}

	public static String cleanText(String text, int max) {
		return truncate(text == null ? "" : text.trim().replaceAll("[\\r\\n]", " "), max);
	}

	private static String truncate(String text, int max) {
		return text.length() > max ? text.substring(0, max) : text;
	}

	private static List<Group> sanitize(Data data) {
		return data == null ? new ArrayList<>() : sanitize(data.groups, 0);
	}

	private static List<Group> sanitize(List<Group> groups, int depth) {
		List<Group> result = new ArrayList<>();
		if (groups == null) {
			return result;
		}
		for (Group group : groups.stream().filter(Objects::nonNull).toList()) {
			String name = cleanText(group.name, MAX_NAME);
			Group clean = new Group(name.isEmpty() ? "未命名" : name);
			addCommands(clean, group.commands);
			if (depth < MAX_DEPTH) {
				clean.children = sanitize(group.children, depth + 1);
			} else {
				flattenInto(clean, group.children);
			}
			result.add(clean);
		}
		return result;
	}

	private static void addCommands(Group target, List<Entry> entries) {
		if (entries == null) {
			return;
		}
		for (Entry entry : entries.stream().filter(Objects::nonNull).toList()) {
			String command = cleanCommand(entry.command);
			if (!command.isEmpty()) {
				Entry clean = new Entry(command, cleanText(entry.note, MAX_NOTE));
				clean.alias = CommandAliases.clean(entry.alias);
				clean.key = entry.key == null ? "" : cleanText(entry.key, 64);
				clean.mods = KeyCombos.CHOICES.contains(entry.mods) ? entry.mods : 0;
				if (CommandKeys.key(clean) == null) {
					clean.key = "";
					clean.mods = 0;
				}
				target.commands.add(clean);
			}
		}
	}

	private static void flattenInto(Group target, List<Group> children) {
		if (children == null) {
			return;
		}
		for (Group child : children.stream().filter(Objects::nonNull).toList()) {
			addCommands(target, child.commands);
			flattenInto(target, child.children);
		}
	}
}
