package net.brilliantw.richpresence;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
//? if >=26.1 {
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
//?} else {
/*import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
*///?}
import net.minecraft.ChatFormatting;
import net.minecraft.client.CameraType;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class Freecam {
	public static final float[] SPEEDS = {1, 2, 5, 10, 20, 40, 80};
	public static final int[] BOOSTS = {2, 3, 5, 10};
	public static final String[] SMOOTH_LABELS = {"無", "低", "中", "高"};
	private static final double[] SMOOTH = {0, 0.6, 0.85, 0.95};
	private static final float MIN_SPEED = 0.5F;
	private static final float MAX_SPEED = 200;

	private static KeyMapping toggle;
	private static boolean active;
	private static Level level;
	private static CameraType cameraType;
	private static boolean hudToggled;
	private static boolean sneaking;
	private static double x, y, z, vx, vy, vz;
	private static float yaw, pitch;
	private static long lastFrame;

	private Freecam() {}

	public static boolean active() {
		return active;
	}

	public static boolean sneaking() {
		return sneaking;
	}

	public static double x() {
		return x;
	}

	public static double y() {
		return y;
	}

	public static double z() {
		return z;
	}

	public static float yaw() {
		return yaw;
	}

	public static float pitch() {
		return pitch;
	}

	static void register(KeyMapping.Category category) {
		KeyMapping key = new KeyMapping("key.brilliant-server-mod.freecam", InputConstants.UNKNOWN.getValue(), category);
		//? if >=26.1 {
		toggle = KeyMappingHelper.registerKeyMapping(key);
		//?} else {
		/*toggle = KeyBindingHelper.registerKeyBinding(key);
		*///?}
		ClientTickEvents.START_CLIENT_TICK.register(client -> {
			if (!active) {
				return;
			}
			if (client.player == null || client.level != level || !client.player.isAlive() || client.getCameraEntity() != client.player || !RichPresenceModClient.featuresActive()) {
				stop(client);
				return;
			}
			Options o = client.options;
			for (KeyMapping blocked : new KeyMapping[] {o.keyAttack, o.keyUse, o.keyPickItem, o.keyTogglePerspective}) {
				while (blocked.consumeClick()) {
				}
				blocked.setDown(false);
			}
			if (o.getCameraType() != CameraType.THIRD_PERSON_BACK) {
				o.setCameraType(CameraType.THIRD_PERSON_BACK);
			}
		});
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (toggle.consumeClick()) {
				if (client.player == null || !RichPresenceModClient.featuresActive() || !KeyCombos.matches(toggle)) {
					continue;
				}
				if (active) {
					stop(client);
				} else {
					start(client);
				}
			}
			if (active) {
				client.hitResult = null;
			}
		});
	}

	private static void start(Minecraft client) {
		//? if >=26.2 {
		Vec3 eye = client.gameRenderer.mainCamera().position();
		//?} else {
		/*Vec3 eye = client.gameRenderer.getMainCamera().position();
		*///?}
		x = eye.x;
		y = eye.y;
		z = eye.z;
		vx = vy = vz = 0;
		yaw = client.player.getYRot();
		pitch = client.player.getXRot();
		level = client.level;
		sneaking = client.player.isShiftKeyDown();
		cameraType = client.options.getCameraType();
		client.options.setCameraType(CameraType.THIRD_PERSON_BACK);
		hudToggled = ModSettings.get().freecamHideHud && !hudHidden(client);
		if (hudToggled) {
			toggleHud(client);
		}
		lastFrame = System.nanoTime();
		active = true;
		RichPresenceModClient.showMessage(Component.literal("自由視角：開啟").withStyle(ChatFormatting.GREEN));
	}

	private static void stop(Minecraft client) {
		active = false;
		level = null;
		client.options.setCameraType(cameraType);
		if (hudToggled && hudHidden(client)) {
			toggleHud(client);
		}
		ModSettings.save();
		RichPresenceModClient.showMessage(Component.literal("自由視角：關閉").withStyle(ChatFormatting.GRAY));
	}

	private static boolean hudHidden(Minecraft client) {
		//? if >=26.2 {
		return client.gui.hud.isHidden();
		//?} else {
		/*return client.options.hideGui;
		*///?}
	}

	private static void toggleHud(Minecraft client) {
		//? if >=26.2 {
		client.gui.hud.toggle();
		//?} else {
		/*client.options.hideGui = !client.options.hideGui;
		*///?}
	}

	public static void turn(double dx, double dy) {
		yaw += (float) dx * 0.15F;
		pitch = Math.clamp(pitch + (float) dy * 0.15F, -90, 90);
	}

	public static boolean scroll(double amount) {
		ModSettings.Data s = ModSettings.get();
		if (!active || !s.freecamScroll || amount == 0 || ScriptEngine.screen() != null) {
			return false;
		}
		s.freecamSpeed = Math.clamp(s.freecamSpeed * (float) Math.pow(1.25, Math.signum(amount)), MIN_SPEED, MAX_SPEED);
		Minecraft client = Minecraft.getInstance();
		Component text = Component.literal("自由視角速度：" + formatSpeed(s.freecamSpeed));
		//? if >=26.2 {
		client.gui.hud.setOverlayMessage(text, false);
		//?} else {
		/*client.gui.setOverlayMessage(text, false);
		*///?}
		return true;
	}

	public static String formatSpeed(float speed) {
		return (speed >= 10 ? Integer.toString(Math.round(speed)) : String.format("%.1f", speed)) + " 格/秒";
	}

	public static float nextSpeed(float speed) {
		for (float option : SPEEDS) {
			if (option > speed + 0.01F) {
				return option;
			}
		}
		return SPEEDS[0];
	}

	public static int nextBoost(int boost) {
		for (int option : BOOSTS) {
			if (option > boost) {
				return option;
			}
		}
		return BOOSTS[0];
	}

	public static void frame() {
		long now = System.nanoTime();
		double dt = Math.min((now - lastFrame) / 1e9, 0.1);
		lastFrame = now;
		Minecraft client = Minecraft.getInstance();
		ModSettings.Data s = ModSettings.get();
		Options o = client.options;
		double forward = axis(o.keyUp, o.keyDown);
		double left = axis(o.keyLeft, o.keyRight);
		double up = axis(o.keyJump, o.keyShift);
		double sin = Math.sin(Math.toRadians(yaw));
		double cos = Math.cos(Math.toRadians(yaw));
		double look = s.freecamFlat ? 1 : Math.cos(Math.toRadians(pitch));
		double tx = -sin * look * forward + cos * left;
		double ty = (s.freecamFlat ? 0 : -Math.sin(Math.toRadians(pitch)) * forward) + up;
		double tz = cos * look * forward + sin * left;
		double length = Math.sqrt(tx * tx + ty * ty + tz * tz);
		double speed = s.freecamSpeed * (o.keySprint.isDown() ? s.freecamBoost : 1);
		if (length > 0) {
			tx *= speed / length;
			ty *= speed / length;
			tz *= speed / length;
		}
		double keep = Math.pow(SMOOTH[Math.clamp(s.freecamSmooth, 0, SMOOTH.length - 1)], dt * 20);
		vx = tx + (vx - tx) * keep;
		vy = ty + (vy - ty) * keep;
		vz = tz + (vz - tz) * keep;
		if (s.freecamNoclip || level == null) {
			x += vx * dt;
			y += vy * dt;
			z += vz * dt;
			return;
		}
		if (free(x + vx * dt, y, z)) {
			x += vx * dt;
		} else {
			vx = 0;
		}
		if (free(x, y + vy * dt, z)) {
			y += vy * dt;
		} else {
			vy = 0;
		}
		if (free(x, y, z + vz * dt)) {
			z += vz * dt;
		} else {
			vz = 0;
		}
	}

	private static boolean free(double px, double py, double pz) {
		return level.noCollision(AABB.ofSize(new Vec3(px, py, pz), 0.4, 0.4, 0.4));
	}

	private static double axis(KeyMapping positive, KeyMapping negative) {
		return (positive.isDown() ? 1 : 0) - (negative.isDown() ? 1 : 0);
	}

	public static void main(String[] args) {
		yaw = 0;
		pitch = 0;
		turn(600, 0);
		check(Math.abs(yaw - 90) < 1e-4, "turn yaw");
		turn(0, 10000);
		check(pitch == 90, "pitch clamp");
		check(nextSpeed(10) == 20 && nextSpeed(80) == 1 && nextSpeed(3) == 5, "speed cycle");
		check(nextBoost(3) == 5 && nextBoost(10) == 2, "boost cycle");
		check(formatSpeed(2.5F).equals(String.format("%.1f", 2.5F) + " 格/秒") && formatSpeed(40).equals("40 格/秒"), "format");
		System.out.println("ok");
	}

	private static void check(boolean ok, String name) {
		if (!ok) {
			throw new AssertionError(name);
		}
	}
}
