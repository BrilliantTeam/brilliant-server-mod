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
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.network.chat.Component;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.item.equipment.Equippable;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.brilliantw.richpresence.mixin.BlockStatePredictionHandlerAccessor;
import net.brilliantw.richpresence.mixin.ClientLevelAccessor;
import net.brilliantw.richpresence.mixin.ServerVerifiedStateAccessor;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

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
		boolean any = name.isBlank();
		int total = 0;
		for (int i = 0; i < Math.min(36, player.getInventory().getContainerSize()); i++) {
			var stack = player.getInventory().getItem(i);
			if (!stack.isEmpty() && (any || matches(stack, name))) {
				total += stack.getCount();
			}
		}
		return total;
	}

	public static int inventoryFind(LocalPlayer player, String name) {
		if (player == null || name.isBlank()) {
			return -1;
		}
		for (int i = 0; i < Math.min(36, player.getInventory().getContainerSize()); i++) {
			if (matches(player.getInventory().getItem(i), name)) {
				return i;
			}
		}
		return -1;
	}

	private static boolean named(String name, String id, String wanted) {
		String needle = wanted.strip().toLowerCase(Locale.ROOT);
		return !needle.isEmpty() && (name.toLowerCase(Locale.ROOT).contains(needle) || id.contains(needle));
	}

	public static boolean matches(ItemStack stack, String wanted) {
		return stack != null && !stack.isEmpty() && named(stack.getHoverName().getString(), itemId(stack), wanted);
	}

	public static String itemName(ItemStack stack) {
		return stack == null || stack.isEmpty() ? "" : stack.getHoverName().getString();
	}

	public static String itemId(ItemStack stack) {
		return stack == null || stack.isEmpty() ? "" : BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
	}

	public static String lore(ItemStack stack) {
		ItemLore lore = stack == null ? null : stack.get(DataComponents.LORE);
		return lore == null ? "" : String.join(" ", lore.lines().stream().map(line -> line.getString().strip()).filter(line -> !line.isEmpty()).toList());
	}

	public static ItemStack equipment(LocalPlayer player, String part) {
		if (player == null) {
			return ItemStack.EMPTY;
		}
		return player.getItemBySlot(switch (part) {
			case "胸甲" -> EquipmentSlot.CHEST;
			case "護腿" -> EquipmentSlot.LEGS;
			case "靴子" -> EquipmentSlot.FEET;
			default -> EquipmentSlot.HEAD;
		});
	}

	public static int enchantLevel(ItemStack stack, String name) {
		if (stack == null || stack.isEmpty() || name.isBlank()) {
			return 0;
		}
		int best = 0;
		for (ItemEnchantments enchantments : List.of(stack.getEnchantments(), stack.getOrDefault(DataComponents.STORED_ENCHANTMENTS, ItemEnchantments.EMPTY))) {
			for (var entry : enchantments.entrySet()) {
				if (named(entry.getKey().value().description().getString(), entry.getKey().getRegisteredName(), name)) {
					best = Math.max(best, entry.getIntValue());
				}
			}
		}
		return best;
	}

	public static int durabilityPercent(ItemStack stack) {
		return stack == null || !stack.isDamageableItem() ? -1 : Math.round(100f * (stack.getMaxDamage() - stack.getDamageValue()) / stack.getMaxDamage());
	}

	public static boolean isFood(ItemStack stack) {
		return stack != null && stack.has(DataComponents.FOOD);
	}

	public static boolean canEat(LocalPlayer player, ItemStack stack) {
		FoodProperties food = stack.get(DataComponents.FOOD);
		return food != null && player.canEat(food.canAlwaysEat());
	}

	public static int armorMenuSlot(ItemStack stack) {
		Equippable equippable = stack.get(DataComponents.EQUIPPABLE);
		if (equippable == null) {
			return -1;
		}
		return switch (equippable.slot()) {
			case HEAD -> 5;
			case CHEST -> 6;
			case LEGS -> 7;
			case FEET -> 8;
			default -> -1;
		};
	}

	public static float toolScore(ItemStack stack, BlockState state) {
		return (stack.isCorrectToolForDrops(state) ? 1000 : 0) + stack.getDestroySpeed(state);
	}

	public static String signText(LocalPlayer player, BlockPos pos) {
		if (!(player.level().getBlockEntity(pos) instanceof SignBlockEntity sign)) {
			return "";
		}
		//? if >=26.3 {
		/*List<Component> lines = sign.getText(sign.getSlotPlayerIsFacing(player)).getMessages(false);
		*///?} else {
		List<Component> lines = List.of(sign.getText(sign.isFacingFrontText(player)).getMessages(false));
		//?}
		return String.join(" ", lines.stream().map(line -> line.getString().strip()).filter(line -> !line.isEmpty()).toList());
	}

	public static String axis(BlockPos pos, String axis) {
		return pos == null ? "" : Integer.toString(switch (axis) {
			case "Y" -> pos.getY();
			case "Z" -> pos.getZ();
			default -> pos.getX();
		});
	}

	public static boolean grown(BlockState state) {
		for (Property<?> property : state.getProperties()) {
			if (property.getName().equals("age") && property instanceof IntegerProperty age) {
				return state.getValue(age) >= Collections.max(age.getPossibleValues());
			}
		}
		return false;
	}

	public static final double ENTITY_SIGHT = 64;
	public static final double BLOCK_SIGHT = 128;
	public static final int BLOCK_SCAN = 8;
	private static final int MAX_RAYS = 512;

	public static boolean coordsHidden(LocalPlayer player) {
		return player == null || player.isReducedDebugInfo();
	}

	private static boolean clear(LocalPlayer player, Vec3 target, BlockPos expect) {
		BlockHitResult hit = player.level().clip(new ClipContext(player.getEyePosition(), target, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, player));
		return hit.getType() == HitResult.Type.MISS || expect != null && hit.getBlockPos().equals(expect);
	}

	public static boolean sees(LocalPlayer player, Entity entity) {
		return player != null && entity != player && entity.isAlive() && !entity.isInvisibleTo(player) && entity.distanceTo(player) <= ENTITY_SIGHT
				&& (clear(player, entity.getEyePosition(), null) || clear(player, entity.getBoundingBox().getCenter(), null));
	}

	public static BlockState confirmedState(LocalPlayer player, BlockPos pos) {
		if (player.level() instanceof ClientLevel level) {
			Object pending = ((BlockStatePredictionHandlerAccessor) ((ClientLevelAccessor) level).brilliant$getPredictions())
					.brilliant$getServerVerifiedStates().get(pos.asLong());
			if (pending != null) {
				return ((ServerVerifiedStateAccessor) pending).brilliant$getBlockState();
			}
		}
		return player.level().getBlockState(pos);
	}

	public static String blockName(LocalPlayer player, BlockPos pos) {
		return confirmedState(player, pos).getBlock().getName().getString();
	}

	public static String visibleBlock(LocalPlayer player, BlockPos pos) {
		return coordsHidden(player) ? "" : seenBlock(player, pos);
	}

	public static String seenBlock(LocalPlayer player, BlockPos pos) {
		if (player == null || !player.level().isLoaded(pos)) {
			return "";
		}
		Vec3 center = Vec3.atCenterOf(pos);
		if (player.getEyePosition().distanceTo(center) > BLOCK_SIGHT || !clear(player, center, pos)) {
			return "";
		}
		return blockName(player, pos);
	}

	public static List<BlockPos> blocksNear(LocalPlayer player, double range, String name, int limit) {
		if (player == null || name.isBlank()) {
			return List.of();
		}
		int r = (int) Math.clamp(Math.round(range), 0, BLOCK_SCAN);
		Vec3 eye = player.getEyePosition();
		BlockPos center = player.blockPosition();
		List<BlockPos> found = new ArrayList<>();
		for (BlockPos pos : BlockPos.betweenClosed(center.offset(-r, -r, -r), center.offset(r, r, r))) {
			BlockState state = confirmedState(player, pos);
			if (!state.isAir() && eye.distanceTo(Vec3.atCenterOf(pos)) <= r + 0.5
					&& named(state.getBlock().getName().getString(), BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString(), name)) {
				found.add(pos.immutable());
			}
		}
		found.sort(Comparator.comparingDouble(pos -> eye.distanceToSqr(Vec3.atCenterOf(pos))));
		List<BlockPos> seen = new ArrayList<>();
		for (int i = 0; i < Math.min(found.size(), MAX_RAYS) && seen.size() < limit; i++) {
			if (clear(player, Vec3.atCenterOf(found.get(i)), found.get(i))) {
				seen.add(found.get(i));
			}
		}
		return seen;
	}

	public static List<ItemEntity> drops(LocalPlayer player, double range, String name) {
		if (player == null) {
			return List.of();
		}
		double r = Math.clamp(range, 0, ENTITY_SIGHT);
		return player.level().getEntities(player, new AABB(player.blockPosition()).inflate(r),
				entity -> entity instanceof ItemEntity item && entity.distanceTo(player) <= r && (name.isBlank() || matches(item.getItem(), name)) && sees(player, entity))
				.stream().map(entity -> (ItemEntity) entity).sorted(Comparator.comparingDouble(entity -> entity.distanceTo(player))).toList();
	}

	public static List<Player> visiblePlayers(LocalPlayer player, double range) {
		if (player == null) {
			return List.of();
		}
		double r = Math.clamp(range, 0, ENTITY_SIGHT);
		return player.level().players().stream().filter(other -> other.distanceTo(player) <= r && sees(player, other))
				.map(other -> (Player) other).sorted(Comparator.comparingDouble(other -> other.distanceTo(player))).toList();
	}

	private static List<Entity> creatures(LocalPlayer player, double range) {
		if (player == null) {
			return List.of();
		}
		double r = Math.clamp(range, 0, ENTITY_SIGHT);
		return player.level().getEntities(player, new AABB(player.blockPosition()).inflate(r),
				entity -> entity instanceof LivingEntity && !(entity instanceof Player) && entity.distanceTo(player) <= r && sees(player, entity));
	}

	public static int entityCount(LocalPlayer player, double range, String name) {
		String needle = name.strip().toLowerCase(Locale.ROOT);
		return (int) creatures(player, range).stream().filter(entity -> needle.isEmpty() || entity.getName().getString().toLowerCase(Locale.ROOT).contains(needle)).count();
	}

	public static Entity nearestCreature(LocalPlayer player) {
		return nearestCreature(player, "");
	}

	public static Entity nearestCreature(LocalPlayer player, String name) {
		String needle = name.strip().toLowerCase(Locale.ROOT);
		return creatures(player, 64).stream().filter(entity -> needle.isEmpty() || entity.getName().getString().toLowerCase(Locale.ROOT).contains(needle))
				.min(Comparator.comparingDouble(entity -> entity.distanceTo(player))).orElse(null);
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
