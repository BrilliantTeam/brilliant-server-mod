package net.brilliantw.richpresence;

import java.util.List;
import java.util.Locale;
import net.brilliantw.richpresence.mixin.DisconnectedScreenAccessor;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.network.chat.Component;

public final class AutoReconnect {
	public static final List<Integer> ATTEMPTS = List.of(0, 3, 5, 10, 20, 50);
	public static final List<Integer> DELAYS = List.of(1, 2, 3, 5, 10, 15, 30, 60);
	private static final List<String> NO_RETRY = List.of("logged in from another location", "從其他位置登入", "从其他位置登录", "duplicate login");

	private static ServerData server;
	private static int attempts;
	private static long reconnectAt;
	private static Screen watched;
	private static String stopped = "";

	private AutoReconnect() {}

	public static String attemptsLabel(int value) {
		return value <= 0 ? "無限" : value + " 次";
	}

	public static String delayLabel(int value) {
		return value + " 秒";
	}

	public static void register() {
		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
			ServerData current = client.getCurrentServer();
			if (current != null && current.ip != null && !current.isLan()) {
				server = current;
			}
			attempts = 0;
			reconnectAt = 0;
			stopped = "";
		});
		ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
			if (screen instanceof DisconnectedScreen disconnected) {
				attach(client, disconnected, width);
			}
		});
		ClientTickEvents.END_CLIENT_TICK.register(AutoReconnect::tick);
	}

	private static boolean allowed() {
		if (server == null || !ModSettings.get().autoReconnect) {
			return false;
		}
		String host = server.ip.toLowerCase(Locale.ROOT).split(":")[0];
		return host.equals(RPConfig.BASE_DOMAIN) || host.endsWith("." + RPConfig.BASE_DOMAIN) || ModSettings.get().anyServer;
	}

	private static void attach(Minecraft client, DisconnectedScreen screen, int width) {
		if (!allowed()) {
			return;
		}
		if (watched != screen) {
			watched = screen;
			String reason = ((DisconnectedScreenAccessor) screen).brilliant$details().reason().getString().toLowerCase(Locale.ROOT);
			int max = ModSettings.get().reconnectAttempts;
			if (NO_RETRY.stream().anyMatch(reason::contains)) {
				stopped = "你的帳號在其他地方登入，已停止自動重連";
				reconnectAt = 0;
			} else if (max > 0 && attempts >= max) {
				stopped = "已重連 " + attempts + " 次仍失敗，停止自動重連";
				reconnectAt = 0;
			} else {
				stopped = "";
				reconnectAt = System.currentTimeMillis() + Math.max(1, ModSettings.get().reconnectDelay) * 1000L;
			}
		}
		Button now = Button.builder(Component.literal("立即重連"), b -> connect(client)).bounds(width / 2 - 102, 8, 100, 20).build();
		Button cancel = Button.builder(Component.literal("取消自動重連"), b -> {
			reconnectAt = 0;
			stopped = "已取消自動重連";
			b.active = false;
		}).bounds(width / 2 + 2, 8, 100, 20).build();
		cancel.active = reconnectAt > 0;
		//? if >=26.1 {
		Screens.getWidgets(screen).add(now);
		Screens.getWidgets(screen).add(cancel);
		//?} else {
		/*Screens.getButtons(screen).add(now);
		Screens.getButtons(screen).add(cancel);
		*///?}
		//? if >=26.1 {
		ScreenEvents.afterExtract(screen).register((current, graphics, mouseX, mouseY, delta) -> status(new Gfx(graphics), current));
		//?} else {
		/*ScreenEvents.afterRender(screen).register((current, graphics, mouseX, mouseY, delta) -> status(new Gfx(graphics), current));
		*///?}
	}

	private static void status(Gfx gfx, Screen screen) {
		String text;
		int color;
		if (!stopped.isEmpty()) {
			text = stopped;
			color = Theme.WARNING;
		} else if (reconnectAt > 0) {
			long left = Math.max(0, (reconnectAt - System.currentTimeMillis() + 999) / 1000);
			int max = ModSettings.get().reconnectAttempts;
			text = left + " 秒後自動重連（第 " + (attempts + 1) + " 次" + (max > 0 ? "／共 " + max + " 次" : "") + "）";
			color = Theme.ACCENT;
		} else {
			return;
		}
		Minecraft client = Minecraft.getInstance();
		gfx.centered(client.font, text, 0, 32, screen.width, 10, color);
	}

	private static void tick(Minecraft client) {
		if (reconnectAt <= 0 || Reauth.busy()) {
			return;
		}
		if (ScriptEngine.screen() != watched) {
			reconnectAt = 0;
			return;
		}
		if (System.currentTimeMillis() >= reconnectAt) {
			connect(client);
		}
	}

	private static void connect(Minecraft client) {
		if (server == null) {
			return;
		}
		reconnectAt = 0;
		attempts++;
		watched = null;
		ConnectScreen.startConnecting(new JoinMultiplayerScreen(new TitleScreen()), client, ServerAddress.parseString(server.ip), server, false, null);
	}
}
