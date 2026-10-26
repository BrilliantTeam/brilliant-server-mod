package net.brilliantw.richpresence;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;

public final class PrivateMessages {
	private static final class Settings {
		boolean hideInChat = true;
		boolean collapsed;
		int x = 4;
		int y = 4;
		int width = 240;
		int body = 96;
	}

	public record Message(boolean mine, String server, String text, long time) {}

	public static final class Conversation {
		public final String name;
		public final List<Message> messages = new ArrayList<>();
		public String display;
		public int unread;
		public long updated;

		Conversation(String name) {
			this.name = name;
			this.display = name;
		}

		public String label() {
			return display.equalsIgnoreCase(name) ? name : display + "（" + name + "）";
		}
	}

	private static final Pattern HEADER = Pattern.compile("^\\s*[｜|]\\s*私訊\\s*[｜|]([^｜|\\n]*)[｜|]\\s*(.+?)\\s*-+>\\s*(.+?)\\s*[：:]\\s*(?:\\n\\s*[｜|]\\s*(.*?))?\\s*$", Pattern.DOTALL | Pattern.UNICODE_CHARACTER_CLASS);
	private static final Pattern BODY = Pattern.compile("^\\s*[｜|](?![^｜|\\s]{1,8}[｜|])\\s*(.*?)\\s*$", Pattern.DOTALL | Pattern.UNICODE_CHARACTER_CLASS);
	private static final Pattern TARGET_COMMAND = Pattern.compile("^/(?:msg|tell|w|whisper|m)\\s+([A-Za-z0-9_]{3,16})(?:\\s.*)?$", Pattern.CASE_INSENSITIVE);
	private static final String SELF = "我";
	private static final int MAX_MESSAGES = 200;
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Map<String, Conversation> CONVERSATIONS = new LinkedHashMap<>();
	private static final Map<String, String> KNOWN_NAMES = new LinkedHashMap<>(16, 0.75f, true) {
		@Override
		protected boolean removeEldestEntry(Map.Entry<String, String> eldest) {
			return size() > 1000;
		}
	};

	private static Settings settings;
	private static String[] pendingHeader;
	private static String selected;

	private PrivateMessages() {}

	private static String key(String name) {
		return name.toLowerCase(Locale.ROOT);
	}

	private static Path file() {
		return FabricLoader.getInstance().getConfigDir().resolve("brilliant-server-mod-messages.json");
	}

	private static Settings settings() {
		if (settings == null) {
			settings = new Settings();
			try {
				if (Files.exists(file())) {
					Settings loaded = GSON.fromJson(Files.readString(file(), StandardCharsets.UTF_8), Settings.class);
					if (loaded != null) {
						settings = loaded;
					}
				}
			} catch (Exception ignored) {
			}
		}
		return settings;
	}

	public static void saveSettings() {
		try {
			Path temp = file().resolveSibling(file().getFileName() + ".tmp");
			Files.writeString(temp, GSON.toJson(settings()), StandardCharsets.UTF_8);
			Files.move(temp, file(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
		} catch (IOException e) {
			System.err.println("[brilliant-server-mod] Failed to save private message settings: " + e.getMessage());
		}
	}

	public static boolean hideInChat() {
		return settings().hideInChat;
	}

	public static void setHideInChat(boolean value) {
		settings().hideInChat = value;
		saveSettings();
	}

	public static boolean collapsed() {
		return settings().collapsed;
	}

	public static void setCollapsed(boolean value) {
		settings().collapsed = value;
		if (!value && selected != null) {
			conversation(selected).unread = 0;
		}
		saveSettings();
	}

	public static int windowX() {
		return settings().x;
	}

	public static int windowY() {
		return settings().y;
	}

	public static int windowWidth() {
		return settings().width;
	}

	public static int bodyHeight() {
		return settings().body;
	}

	public static void resizeWindow(int width, int body) {
		settings().width = width;
		settings().body = body;
	}

	public static void moveWindow(int x, int y) {
		settings().x = x;
		settings().y = y;
	}

	public static Conversation selected() {
		return selected == null ? null : CONVERSATIONS.get(selected);
	}

	public static void select(String name) {
		selected = key(name);
		if (!collapsed()) {
			conversation(name).unread = 0;
		}
	}

	public static int maxMessageLength(String name) {
		return Math.max(1, 256 - ("msg " + name + " ").length());
	}

	public static void send(String name, String text) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.player != null && !text.isBlank()) {
			minecraft.player.connection.sendCommand("msg " + name + " " + text.strip());
		}
	}

	public static boolean onChatMessage(Component message) {
		learnNames(message);
		String raw = ChatFormatting.stripFormatting(ChatTabs.plain(message));
		String[] header = pendingHeader;
		pendingHeader = null;
		Matcher matcher = HEADER.matcher(raw);
		if (matcher.matches()) {
			if (matcher.group(4) == null) {
				pendingHeader = new String[] { matcher.group(1).trim(), matcher.group(2).trim(), matcher.group(3).trim() };
			} else {
				recordPrivate(matcher.group(1).trim(), matcher.group(2).trim(), matcher.group(3).trim(), matcher.group(4).trim());
			}
			return true;
		}
		Matcher body = BODY.matcher(raw);
		if (header != null && body.matches()) {
			recordPrivate(header[0], header[1], header[2], body.group(1).trim());
			return true;
		}
		return false;
	}

	static void learnNames(Component message) {
		message.visit((Style style, String text) -> {
			String command = style.getClickEvent() instanceof ClickEvent.SuggestCommand suggest ? suggest.command()
					: style.getClickEvent() instanceof ClickEvent.RunCommand run ? run.command() : null;
			String display = ChatFormatting.stripFormatting(text).strip();
			if (command != null && !display.isEmpty()) {
				Matcher matcher = TARGET_COMMAND.matcher(command.strip());
				if (matcher.matches()) {
					KNOWN_NAMES.put(key(display), matcher.group(1));
				}
			}
			return Optional.empty();
		}, Style.EMPTY);
	}

	public static String resolveName(String display) {
		String known = KNOWN_NAMES.get(key(display));
		if (known != null) {
			return known;
		}
		ClientPacketListener connection = Minecraft.getInstance() == null ? null : Minecraft.getInstance().getConnection();
		if (connection != null) {
			String match = null;
			for (PlayerInfo info : connection.getOnlinePlayers()) {
				String account = info.getProfile().name();
				if (account.equalsIgnoreCase(display)) {
					return account;
				}
				Component tab = info.getTabListDisplayName();
				if (tab != null && ChatFormatting.stripFormatting(ChatTabs.plain(tab)).contains(display)) {
					if (match != null && !match.equals(account)) {
						match = null;
						break;
					}
					match = account;
				}
			}
			if (match != null) {
				return match;
			}
		}
		return display;
	}

	private static void recordPrivate(String server, String from, String to, String text) {
		if (from.equals(SELF) && !to.equals(SELF)) {
			record(to, new Message(true, server, text, System.currentTimeMillis()));
		} else if (to.equals(SELF) && !from.equals(SELF)) {
			record(from, new Message(false, server, text, System.currentTimeMillis()));
			ScriptEngine.onPrivate(from, text);
		}
	}

	private static void record(String display, Message message) {
		Conversation conversation = conversation(resolveName(display));
		conversation.display = display;
		conversation.messages.add(message);
		if (conversation.messages.size() > MAX_MESSAGES) {
			conversation.messages.removeFirst();
		}
		conversation.updated = message.time();
		if (selected == null || CONVERSATIONS.get(selected) == null) {
			selected = key(conversation.name);
		}
		if (!message.mine() && (collapsed() || !key(conversation.name).equals(selected))) {
			conversation.unread++;
		}
	}

	public static Conversation conversation(String name) {
		return CONVERSATIONS.computeIfAbsent(key(name), k -> new Conversation(name));
	}

	public static List<Conversation> conversations() {
		return CONVERSATIONS.values().stream().sorted(Comparator.comparingLong((Conversation c) -> c.updated).reversed()).toList();
	}

	public static int totalUnread() {
		return CONVERSATIONS.values().stream().mapToInt(c -> c.unread).sum();
	}

	public static void close(Conversation conversation) {
		CONVERSATIONS.remove(key(conversation.name));
		if (key(conversation.name).equals(selected)) {
			selected = conversations().isEmpty() ? null : key(conversations().getFirst().name);
		}
	}
}
