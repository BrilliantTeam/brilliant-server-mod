package net.brilliantw.richpresence;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.panda.Panda;
import net.minecraft.world.entity.npc.villager.VillagerDataHolder;

public final class MobHeads {
	public static final Identifier ATLAS = Identifier.fromNamespaceAndPath("brilliant-server-mod", "textures/gui/mob_heads.png");

	public record Head(int u, int v) {}

	private static final List<DataComponentType<?>> VARIANTS = List.of(DataComponents.AXOLOTL_VARIANT, DataComponents.CAT_VARIANT, DataComponents.CHICKEN_VARIANT,
			DataComponents.COW_VARIANT, DataComponents.FOX_VARIANT, DataComponents.FROG_VARIANT, DataComponents.HORSE_VARIANT, DataComponents.LLAMA_VARIANT,
			DataComponents.MOOSHROOM_VARIANT, DataComponents.PARROT_VARIANT, DataComponents.PIG_VARIANT, DataComponents.RABBIT_VARIANT, DataComponents.WOLF_VARIANT,
			DataComponents.ZOMBIE_NAUTILUS_VARIANT, DataComponents.SHEEP_COLOR, DataComponents.SHULKER_COLOR);
	private static final Map<String, String> ALIASES = Map.of("red_blue", "red", "yellow_blue", "cyan", "white_splotched", "black_and_white", "salt", "salt_and_pepper", "evil", "the_killer_bunny");
	private static Map<String, Integer> index;
	private static int columns = 1;
	public static int width = 8;
	public static int height = 8;

	private MobHeads() {}

	private static void load() {
		if (index != null) {
			return;
		}
		index = new HashMap<>();
		try (InputStream in = MobHeads.class.getResourceAsStream("/assets/brilliant-server-mod/mob_heads.json")) {
			if (in == null) {
				return;
			}
			JsonObject root = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
			columns = root.get("columns").getAsInt();
			width = root.get("width").getAsInt();
			height = root.get("height").getAsInt();
			for (Map.Entry<String, JsonElement> entry : root.getAsJsonObject("heads").entrySet()) {
				index.put(entry.getKey(), entry.getValue().getAsInt());
			}
		} catch (Exception ignored) {
		}
	}

	public static Head of(Entity entity) {
		load();
		String type = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).getPath();
		String variant = variant(entity);
		Integer i = variant == null ? null : index.get(type + "/" + variant);
		if (i == null) {
			i = index.get(type);
		}
		return i == null ? null : new Head(i % columns * 8, i / columns * 8);
	}

	static String variant(Entity entity) {
		if (entity instanceof Panda panda) {
			return name(panda.getMainGene());
		}
		if (entity instanceof VillagerDataHolder villager) {
			return name(villager.getVillagerData().profession());
		}
		for (DataComponentType<?> type : VARIANTS) {
			Object value = entity.get(type);
			if (value != null) {
				return name(value);
			}
		}
		return null;
	}

	static String name(Object value) {
		String name = value instanceof Holder<?> holder ? holder.unwrapKey().map(key -> key.identifier().getPath()).orElse(null)
				: value instanceof StringRepresentable named ? named.getSerializedName() : null;
		return name == null ? null : ALIASES.getOrDefault(name, name);
	}
}
