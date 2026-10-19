package net.brilliantw.richpresence;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.world.entity.Entity;

public final class MobFaces {
	public record Face(Identifier texture, int width, int height) {}

	private static final Face NONE = new Face(null, 0, 0);
	private static final Set<String> SIDE = Set.of("horse", "donkey", "mule", "zombie_horse", "skeleton_horse", "camel", "camel_husk", "parrot", "dolphin", "turtle", "goat", "frog");
	private static final Map<String, Face> BY_TEXTURE = new HashMap<>();
	private static final Map<Integer, Face> BY_ENTITY = new HashMap<>();
	private static long clearedAt;
	private static int counter;

	private MobFaces() {}

	public static Face of(Entity entity) {
		long now = System.currentTimeMillis();
		if (now - clearedAt > 10_000) {
			BY_ENTITY.clear();
			clearedAt = now;
		}
		return BY_ENTITY.computeIfAbsent(entity.getId(), id -> resolve(entity));
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	private static Face resolve(Entity entity) {
		try {
			EntityRenderer renderer = Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(entity);
			if (!(renderer instanceof LivingEntityRenderer living)) {
				return NONE;
			}
			Object state = renderer.createRenderState(entity, 0f);
			Identifier texture = living.getTextureLocation((LivingEntityRenderState) state);
			ModelPart root = living.getModel().root();
			boolean side = SIDE.contains(BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).getPath());
			return BY_TEXTURE.computeIfAbsent(texture + "|" + entity.getType(), key -> build(texture, root, side));
		} catch (RuntimeException e) {
			return NONE;
		}
	}

	static int priority(String path) {
		String lower = path.toLowerCase(Locale.ROOT);
		if (lower.contains("hat") || lower.contains("wear")) {
			return 0;
		}
		return lower.endsWith("head") ? 2 : lower.contains("head") ? 1 : 0;
	}

	public static float[] faceUv(ModelPart root, boolean side) {
		float[] best = new float[4];
		double[] bestScore = { -1 };
		root.visit(new PoseStack(), (pose, path, index, cube) -> {
			int priority = priority(path);
			for (ModelPart.Polygon polygon : cube.polygons) {
				if (side ? polygon.normal().x() < 0.5f : polygon.normal().z() > -0.5f) {
					continue;
				}
				float u0 = Float.MAX_VALUE;
				float v0 = Float.MAX_VALUE;
				float u1 = -Float.MAX_VALUE;
				float v1 = -Float.MAX_VALUE;
				float a0 = Float.MAX_VALUE;
				float a1 = -Float.MAX_VALUE;
				float b0 = Float.MAX_VALUE;
				float b1 = -Float.MAX_VALUE;
				for (ModelPart.Vertex vertex : polygon.vertices()) {
					u0 = Math.min(u0, vertex.u());
					v0 = Math.min(v0, vertex.v());
					u1 = Math.max(u1, vertex.u());
					v1 = Math.max(v1, vertex.v());
					float across = side ? vertex.worldZ() : vertex.worldX();
					a0 = Math.min(a0, across);
					a1 = Math.max(a1, across);
					b0 = Math.min(b0, vertex.worldY());
					b1 = Math.max(b1, vertex.worldY());
				}
				double width = a1 - a0;
				double height = b1 - b0;
				double area = width * height;
				if (area <= 0 || height / width > 1.6 || width / height > 2.6) {
					continue;
				}
				double score = priority * 10 + Math.min(area, 9.99);
				if (score > bestScore[0]) {
					bestScore[0] = score;
					best[0] = u0;
					best[1] = v0;
					best[2] = u1;
					best[3] = v1;
				}
			}
		});
		return bestScore[0] < 0 ? null : best;
	}

	private static Face build(Identifier texture, ModelPart root, boolean side) {
		float[] best = faceUv(root, side);
		if (best == null) {
			return NONE;
		}
		Optional<Resource> resource = Minecraft.getInstance().getResourceManager().getResource(texture);
		if (resource.isEmpty()) {
			return NONE;
		}
		try (InputStream in = resource.get().open(); NativeImage image = NativeImage.read(in)) {
			int x0 = Math.round(best[0] * image.getWidth());
			int y0 = Math.round(best[1] * image.getHeight());
			int w = Math.min(Math.round(best[2] * image.getWidth()), image.getWidth()) - x0;
			int h = Math.min(Math.round(best[3] * image.getHeight()), image.getHeight()) - y0;
			if (x0 < 0 || y0 < 0 || w <= 0 || h <= 0 || w > 64 || h > 64) {
				return NONE;
			}
			DynamicTexture dynamic = new DynamicTexture(() -> "brilliant-server-mod mob face", w, h, false);
			NativeImage pixels = dynamic.getPixels();
			boolean visible = false;
			for (int y = 0; y < h; y++) {
				for (int x = 0; x < w; x++) {
					int argb = image.getPixel(x0 + x, y0 + y);
					visible |= (argb >>> 24) > 16;
					pixels.setPixel(x, y, argb);
				}
			}
			if (!visible) {
				dynamic.close();
				return NONE;
			}
			dynamic.upload();
			Identifier id = Identifier.fromNamespaceAndPath("brilliant-server-mod", "map/mob_face_" + counter++);
			Minecraft.getInstance().getTextureManager().register(id, dynamic);
			return new Face(id, w, h);
		} catch (Exception e) {
			return NONE;
		}
	}
}
