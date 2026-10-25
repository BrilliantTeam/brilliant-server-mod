package net.brilliantw.richpresence;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.brilliantw.richpresence.mixin.BossHealthOverlayAccessor;
import net.brilliantw.richpresence.mixin.HudTextAccessor;
import net.brilliantw.richpresence.mixin.PlayerTabOverlayAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerScoreEntry;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;

public final class ScreenText {
	public record Bar(String name, float progress) {}

	private ScreenText() {}

	private static Object hud() {
		Minecraft client = Minecraft.getInstance();
		if (client == null || client.gui == null) {
			return null;
		}
		//? if >=26.2 {
		return client.gui.hud;
		//?} else {
		/*return client.gui;
		*///?}
	}

	private static String text(Component component) {
		return component == null ? "" : component.getString();
	}

	public static String title() {
		return hud() instanceof HudTextAccessor hud && hud.brilliant$titleTime() > 0 ? text(hud.brilliant$title()) : "";
	}

	public static String subtitle() {
		return hud() instanceof HudTextAccessor hud && hud.brilliant$titleTime() > 0 ? text(hud.brilliant$subtitle()) : "";
	}

	public static String actionbar() {
		return hud() instanceof HudTextAccessor hud && hud.brilliant$overlayMessageTime() > 0 ? text(hud.brilliant$overlayMessage()) : "";
	}

	public static List<Bar> bossbars() {
		Minecraft client = Minecraft.getInstance();
		List<Bar> bars = new ArrayList<>();
		if (client == null || client.gui == null) {
			return bars;
		}
		//? if >=26.2 {
		var overlay = client.gui.hud.getBossOverlay();
		//?} else {
		/*var overlay = client.gui.getBossOverlay();
		*///?}
		for (var event : ((BossHealthOverlayAccessor) overlay).brilliant$events().values()) {
			bars.add(new Bar(text(event.getName()), event.getProgress()));
		}
		return bars;
	}

	private static PlayerTabOverlayAccessor tabList() {
		Minecraft client = Minecraft.getInstance();
		if (client == null || client.gui == null) {
			return null;
		}
		//? if >=26.2 {
		return (PlayerTabOverlayAccessor) client.gui.hud.getTabList();
		//?} else {
		/*return (PlayerTabOverlayAccessor) client.gui.getTabList();
		*///?}
	}

	public static String tabHeader() {
		PlayerTabOverlayAccessor tab = tabList();
		return tab == null ? "" : text(tab.brilliant$header());
	}

	public static String tabFooter() {
		PlayerTabOverlayAccessor tab = tabList();
		return tab == null ? "" : text(tab.brilliant$footer());
	}

	private static Objective sidebar() {
		Minecraft client = Minecraft.getInstance();
		return client == null || client.level == null ? null : client.level.getScoreboard().getDisplayObjective(DisplaySlot.SIDEBAR);
	}

	public static String sidebarTitle() {
		Objective objective = sidebar();
		return objective == null ? "" : text(objective.getDisplayName());
	}

	public static List<String> sidebarLines() {
		return sidebarEntries().stream().map(Map.Entry::getKey).toList();
	}

	private static List<Map.Entry<String, Integer>> sidebarEntries() {
		Objective objective = sidebar();
		List<Map.Entry<String, Integer>> lines = new ArrayList<>();
		if (objective == null) {
			return lines;
		}
		Scoreboard scoreboard = objective.getScoreboard();
		scoreboard.listPlayerScores(objective).stream()
				.filter(entry -> !entry.isHidden())
				.sorted(Comparator.comparingInt(PlayerScoreEntry::value).reversed().thenComparing(PlayerScoreEntry::owner, String.CASE_INSENSITIVE_ORDER))
				.limit(15)
				.forEach(entry -> lines.add(Map.entry(PlayerTeam.formatNameForTeam(scoreboard.getPlayersTeam(entry.owner()), entry.ownerName()).getString(), entry.value())));
		return lines;
	}

	private static final Pattern SCORE_NUMBER = Pattern.compile("-?\\d[\\d,]*(?:\\.\\d+)?");

	public static String sidebarScore(String keyword) {
		String needle = keyword.strip().toLowerCase(Locale.ROOT);
		if (needle.isEmpty()) {
			return "";
		}
		for (Map.Entry<String, Integer> line : sidebarEntries()) {
			String lower = line.getKey().toLowerCase(Locale.ROOT);
			int at = lower.indexOf(needle);
			if (at >= 0) {
				Matcher number = SCORE_NUMBER.matcher(line.getKey().substring(at + needle.length()));
				return number.find() ? number.group().replace(",", "") : Integer.toString(line.getValue());
			}
		}
		return "";
	}
}
