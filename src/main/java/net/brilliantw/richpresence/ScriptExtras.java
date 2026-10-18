package net.brilliantw.richpresence;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;

public final class ScriptExtras {
	public static final int MAX_REGEX = 120;
	public static final long MAX_LOG_BYTES = 5L * 1024 * 1024;
	private static final int REGEX_BUDGET = 200_000;
	private static final Pattern NUMBER = Pattern.compile("-?\\d+(?:\\.\\d+)?");
	private static final Pattern INTEGER = Pattern.compile("-?\\d+");
	private static final Pattern AFTER_COLON = Pattern.compile("[：:]\\s*([^：:]+)");
	private static final Pattern BRACKETED = Pattern.compile("[（(\\[【「]([^）)\\]】」]*)[）)\\]】」]");
	private static final Pattern WORD = Pattern.compile("[A-Za-z0-9_]{3,16}");
	private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");
	private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss");
	private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

	private ScriptExtras() {}

	private static final class Budget implements CharSequence {
		private final CharSequence text;
		private final int[] left;

		Budget(CharSequence text, int[] left) {
			this.text = text;
			this.left = left;
		}

		@Override
		public int length() {
			return text.length();
		}

		@Override
		public char charAt(int index) {
			if (--left[0] < 0) {
				throw new IllegalStateException("regex budget");
			}
			return text.charAt(index);
		}

		@Override
		public CharSequence subSequence(int start, int end) {
			return new Budget(text.subSequence(start, end), left);
		}

		@Override
		public String toString() {
			return text.toString();
		}
	}

	private static String nth(Pattern pattern, String text, int index, boolean group) {
		Matcher matcher = pattern.matcher(new Budget(text.length() > 2000 ? text.substring(0, 2000) : text, new int[] { REGEX_BUDGET }));
		int found = 0;
		while (matcher.find()) {
			if (++found == index) {
				return (group && matcher.groupCount() >= 1 && matcher.group(1) != null ? matcher.group(1) : matcher.group()).strip();
			}
		}
		return "";
	}

	public static String extract(String text, String rule, int index) {
		return switch (rule) {
			case "整數" -> nth(INTEGER, text, index, false);
			case "冒號後面" -> nth(AFTER_COLON, text, index, true);
			case "括號裡" -> nth(BRACKETED, text, index, true);
			case "英文或玩家名" -> nth(WORD, text, index, false);
			default -> nth(NUMBER, text, index, false);
		};
	}

	public static String regex(String pattern, String text, int index) {
		if (pattern.isEmpty() || pattern.length() > MAX_REGEX) {
			return null;
		}
		try {
			return nth(Pattern.compile(pattern), text, index, true);
		} catch (PatternSyntaxException | IllegalStateException | StackOverflowError e) {
			return null;
		}
	}

	public static String format(double value, String format) {
		DecimalFormatSymbols symbols = DecimalFormatSymbols.getInstance(Locale.ROOT);
		return switch (format) {
			case "整數" -> Long.toString(Math.round(value));
			case "1 位小數" -> new DecimalFormat("0.0", symbols).format(value);
			case "2 位小數" -> new DecimalFormat("0.00", symbols).format(value);
			case "百分比" -> new DecimalFormat("0.#", symbols).format(value * 100) + "%";
			default -> new DecimalFormat("#,##0.##", symbols).format(value);
		};
	}

	public static String timeFormat(double seconds, String format) {
		LocalDateTime time = LocalDateTime.ofInstant(Instant.ofEpochSecond((long) Math.clamp(seconds, 0, 253402300799d)), ZoneId.systemDefault());
		return switch (format) {
			case "日期" -> DATE.format(time);
			case "時間" -> TIME.format(time);
			default -> DATE_TIME.format(time);
		};
	}

	public static void sort(List<String> list, boolean descending) {
		Comparator<String> order = ScriptEngine::compare;
		list.sort(descending ? order.reversed() : order);
	}

	private static String key(String key) {
		return key.strip().replace('=', '＝');
	}

	private static int keyIndex(List<String> list, String key) {
		String prefix = key(key) + "=";
		for (int i = 0; i < list.size(); i++) {
			if (list.get(i).startsWith(prefix)) {
				return i;
			}
		}
		return -1;
	}

	public static void mapSet(List<String> list, String key, String value) {
		String entry = key(key) + "=" + value;
		int index = keyIndex(list, key);
		if (index >= 0) {
			list.set(index, entry);
		} else {
			list.add(entry);
		}
	}

	public static String mapGet(List<String> list, String key) {
		int index = keyIndex(list, key);
		return index < 0 ? "" : list.get(index).substring(key(key).length() + 1);
	}

	public static boolean mapRemove(List<String> list, String key) {
		int index = keyIndex(list, key);
		if (index >= 0) {
			list.remove(index);
		}
		return index >= 0;
	}

	public static boolean mapHas(List<String> list, String key) {
		return keyIndex(list, key) >= 0;
	}

	public static int hotbarFind(LocalPlayer player, String name) {
		String needle = name.strip().toLowerCase(Locale.ROOT);
		if (player == null || needle.isEmpty()) {
			return 0;
		}
		for (int i = 0; i < 9; i++) {
			var stack = player.getInventory().getItem(i);
			if (!stack.isEmpty() && stack.getHoverName().getString().toLowerCase(Locale.ROOT).contains(needle)) {
				return i + 1;
			}
		}
		return 0;
	}

	public static int inventoryCount(LocalPlayer player, String name) {
		if (player == null) {
			return 0;
		}
		String needle = name.strip().toLowerCase(Locale.ROOT);
		int total = 0;
		for (int i = 0; i < Math.min(36, player.getInventory().getContainerSize()); i++) {
			var stack = player.getInventory().getItem(i);
			if (!stack.isEmpty() && (needle.isEmpty() || stack.getHoverName().getString().toLowerCase(Locale.ROOT).contains(needle))) {
				total += stack.getCount();
			}
		}
		return total;
	}

	private static List<Entity> creatures(LocalPlayer player, double range) {
		if (player == null) {
			return List.of();
		}
		double r = Math.clamp(range, 0, 128);
		return player.level().getEntities(player, new AABB(player.blockPosition()).inflate(r),
				entity -> entity instanceof LivingEntity && !(entity instanceof Player) && entity.isAlive() && entity.distanceTo(player) <= r);
	}

	public static int entityCount(LocalPlayer player, double range, String name) {
		String needle = name.strip().toLowerCase(Locale.ROOT);
		return (int) creatures(player, range).stream().filter(entity -> needle.isEmpty() || entity.getName().getString().toLowerCase(Locale.ROOT).contains(needle)).count();
	}

	public static Entity nearestCreature(LocalPlayer player) {
		return creatures(player, 64).stream().min(Comparator.comparingDouble(entity -> entity.distanceTo(player))).orElse(null);
	}

	public static String safeLogName(String name) {
		String cleaned = name.strip().replaceAll("[^\\p{L}\\p{N}_\\- ]", "_").strip();
		if (cleaned.isEmpty()) {
			cleaned = "紀錄";
		}
		return cleaned.length() > 40 ? cleaned.substring(0, 40) : cleaned;
	}

	public static String appendLog(String name, String text) {
		try {
			Path folder = FabricLoader.getInstance().getConfigDir().resolve("brilliant-server-mod-logs");
			Files.createDirectories(folder);
			Path file = folder.resolve(safeLogName(name) + ".txt");
			if (Files.exists(file) && Files.size(file) > MAX_LOG_BYTES) {
				return "紀錄檔「" + safeLogName(name) + "」已超過 5 MB，請先整理或改用其他檔名";
			}
			String line = DATE_TIME.format(LocalDateTime.now()) + " " + text.replace('\n', ' ') + System.lineSeparator();
			Files.writeString(file, line, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
			return null;
		} catch (IOException | RuntimeException e) {
			return "無法寫入紀錄檔：" + e.getMessage();
		}
	}

	public static List<String> split(String text, String separator) {
		List<String> parts = new ArrayList<>();
		if (separator.isEmpty()) {
			text.codePoints().forEach(c -> parts.add(Character.toString(c)));
			return parts;
		}
		for (String part : text.split(Pattern.quote(separator), -1)) {
			if (!part.isBlank()) {
				parts.add(part.strip());
			}
		}
		return parts;
	}
}
