package net.brilliantw.richpresence;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.StringRange;
import com.mojang.brigadier.suggestion.Suggestion;
import com.mojang.brigadier.suggestion.Suggestions;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.ClientSuggestionProvider;

public final class CommandAliases {
	public static final int MAX_ALIAS = 20;
	private static final Map<Integer, int[]> SHIFTS = new HashMap<>();
	private static final Set<String> INSTALLED = new HashSet<>();
	private static boolean sending;

	private CommandAliases() {}

	public static String clean(String alias) {
		String cleaned = alias == null ? "" : alias.strip();
		while (cleaned.startsWith("/")) {
			cleaned = cleaned.substring(1).strip();
		}
		cleaned = cleaned.replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
		return cleaned.length() > MAX_ALIAS ? cleaned.substring(0, MAX_ALIAS) : cleaned;
	}

	public static Map<String, String> all() {
		Map<String, String> aliases = new LinkedHashMap<>();
		collect(CommandStore.get().groups, aliases);
		return aliases;
	}

	private static void collect(List<CommandStore.Group> groups, Map<String, String> out) {
		for (CommandStore.Group group : groups) {
			for (CommandStore.Entry entry : group.commands) {
				if (entry.alias != null && !entry.alias.isEmpty()) {
					out.putIfAbsent(entry.alias, entry.command);
				}
			}
			collect(group.children, out);
		}
	}

	static String expand(String body, Map<String, String> aliases) {
		int space = body.indexOf(' ');
		String head = space < 0 ? body : body.substring(0, space);
		String target = aliases.get(head.toLowerCase(Locale.ROOT));
		if (target == null) {
			return null;
		}
		return space < 0 ? target : target + body.substring(space);
	}

	public static boolean send(ClientPacketListener connection, String command) {
		if (sending) {
			return false;
		}
		String expanded = expand(command.strip(), all());
		if (expanded == null) {
			return false;
		}
		sending = true;
		try {
			connection.sendCommand(expanded);
		} finally {
			sending = false;
		}
		return true;
	}

	public static String serverCommand(String alias) {
		Minecraft client = Minecraft.getInstance();
		ClientPacketListener connection = client == null ? null : client.getConnection();
		if (connection == null || INSTALLED.contains(alias)) {
			return null;
		}
		return connection.getCommands().getRoot().getChild(alias) != null ? alias : null;
	}

	public static void install() {
		Minecraft client = Minecraft.getInstance();
		ClientPacketListener connection = client == null ? null : client.getConnection();
		if (connection == null) {
			return;
		}
		CommandDispatcher<ClientSuggestionProvider> dispatcher = connection.getCommands();
		if (dispatcher.getRoot().getChild("brilliant-alias-marker") == null) {
			INSTALLED.clear();
		}
		dispatcher.getRoot().addChild(LiteralArgumentBuilder.<ClientSuggestionProvider>literal("brilliant-alias-marker").requires(source -> false).build());
		for (Map.Entry<String, String> alias : all().entrySet()) {
			String name = alias.getKey();
			if (dispatcher.getRoot().getChild(name) != null) {
				continue;
			}
			LiteralArgumentBuilder<ClientSuggestionProvider> node = LiteralArgumentBuilder.<ClientSuggestionProvider>literal(name).requires(source -> all().containsKey(name));
			ParseResults<ClientSuggestionProvider> parse = dispatcher.parse(alias.getValue(), connection.getSuggestionsProvider());
			var nodes = parse.getContext().getNodes();
			if (!nodes.isEmpty() && !parse.getReader().canRead() && parse.getExceptions().isEmpty()) {
				node.redirect(nodes.getLast().getNode());
			} else {
				node.then(RequiredArgumentBuilder.<ClientSuggestionProvider, String>argument("參數", StringArgumentType.greedyString()));
			}
			dispatcher.getRoot().addChild(node.build());
			INSTALLED.add(name);
		}
	}

	public static String rewriteSuggestion(int id, String input) {
		boolean slash = input.startsWith("/");
		String body = slash ? input.substring(1) : input;
		int space = body.indexOf(' ');
		if (space < 0) {
			return null;
		}
		String expanded = expand(body, all());
		if (expanded == null) {
			return null;
		}
		int prefix = slash ? 1 : 0;
		int targetEnd = prefix + expanded.length() - (body.length() - space);
		SHIFTS.put(id, new int[] { expanded.length() - body.length(), targetEnd });
		return (slash ? "/" : "") + expanded;
	}

	public static Suggestions shift(int id, Suggestions suggestions) {
		int[] shift = SHIFTS.remove(id);
		if (shift == null) {
			return null;
		}
		int delta = shift[0];
		List<Suggestion> moved = new ArrayList<>();
		for (Suggestion suggestion : suggestions.getList()) {
			StringRange range = suggestion.getRange();
			if (range.getStart() >= shift[1]) {
				moved.add(new Suggestion(new StringRange(range.getStart() - delta, range.getEnd() - delta), suggestion.getText(), suggestion.getTooltip()));
			}
		}
		StringRange range = suggestions.getRange();
		StringRange movedRange = range.getStart() >= shift[1] ? new StringRange(range.getStart() - delta, range.getEnd() - delta) : StringRange.at(Math.max(0, range.getEnd() - delta));
		return new Suggestions(movedRange, moved);
	}
}
