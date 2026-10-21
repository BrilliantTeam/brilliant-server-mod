package net.brilliantw.richpresence;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;
import java.util.stream.Collectors;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.ChatFormatting;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;

public final class RichPresenceModClient implements ClientModInitializer {
	private static final int POLL_TICKS = 200;
	private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

	private final DiscordPresenceManager presence = new DiscordPresenceManager();
	private static String host;
	private Connection session;
	private static final long CONFIRM_MS = 6000;
	private static boolean shardPending;
	private static long switchedAt;
	private static String switchedFrom;
	private int ticks;
	private int updateTicks;
	private boolean polling;

	@Override
	public void onInitializeClient() {
		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
			String joined = brilliantHost(client);
			if (joined == null) {
				endSession();
				if (ModSettings.get().anyServer) {
					showMessage(Component.literal("｜").withStyle(ChatFormatting.GRAY)
							.append(Component.literal("輝煌模組").withStyle(ChatFormatting.RED))
							.append(Component.literal("｜").withStyle(ChatFormatting.GRAY))
							.append(Component.literal("危險設定已開啟：模組功能正在非輝煌伺服器上運作，請自行確認此伺服器允許使用，後果自負。可在「選單 → 模組設定」關閉。").withStyle(ChatFormatting.YELLOW)));
				}
				return;
			}
			boolean sameSession = session == handler.getConnection() && host != null;
			shardPending = true;
			switchedAt = System.currentTimeMillis();
			switchedFrom = sameSession ? host : null;
			ticks = POLL_TICKS;
			updateTicks = 0;
			if (ModSettings.get().updateCheck) {
				ModUpdate.check();
			}
			if (session == handler.getConnection() && host != null) {
				return;
			}
			session = handler.getConnection();
			host = joined;
			if (ModSettings.get().discordPresence) {
				presence.start(label(host), state(host), imageKey(host));
			}
			ScriptEngine.onJoin();
			AnnouncementStore.refreshAll(RichPresenceModClient::notifyUnread);
		});

		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			if (!handler.getConnection().isConnected()) {
				endSession();
			}
		});
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			ScriptEngine.tick(client);
			if (host != null && ++updateTicks >= ModUpdate.CHECK_TICKS) {
				updateTicks = 0;
				if (ModSettings.get().updateCheck) {
					ModUpdate.check();
				}
			}
			boolean wantPresence = host != null && ModSettings.get().discordPresence;
			if (wantPresence != presence.isActive()) {
				if (wantPresence) {
					presence.start(label(host), state(host), imageKey(host));
				} else {
					presence.stop();
				}
			}
			if (session != null && !session.isConnected()) {
				endSession();
			}
			if (host != null && !polling && ++ticks >= (shardPending ? 40 : POLL_TICKS)) {
				ticks = 0;
				pollServer(client);
			}
		});
		DiscordImageLinks.register();
		ModUpdate.cleanup();
		ScriptEngine.register();
		SlotNumbers.register();
		Minimap.register();
		AutoReconnect.register();
		Reauth.register();
		NightVision.register();
		PerfSampler.register();
		ScreenshotMeta.register();
		SettingsEntry.register();
		CommandKeys.register();
	}

	public static boolean isOnBrilliantServer() {
		return brilliantHost(Minecraft.getInstance()) != null;
	}

	public static boolean featuresActive() {
		return isOnBrilliantServer() || ModSettings.get().anyServer && Minecraft.getInstance().player != null;
	}

	private static String brilliantHost(Minecraft client) {
		ServerData server = client.getCurrentServer();
		if (server == null || server.ip == null) {
			return null;
		}

		String host = server.ip.toLowerCase(Locale.ROOT).split(":")[0];
		return host.equals(RPConfig.BASE_DOMAIN) || host.endsWith("." + RPConfig.BASE_DOMAIN) ? host : null;
	}

	private static void notifyUnread(int unread) {
		Minecraft client = Minecraft.getInstance();
		if (unread <= 0 || client.player == null || !isOnBrilliantServer() || !ModSettings.get().announcements) {
			return;
		}
		String boards = AnnouncementStore.BOARDS.stream()
				.filter(board -> AnnouncementStore.unread(board.key) > 0)
				.map(board -> board.label)
				.collect(Collectors.joining("、"));
		Component message = Component.literal("｜").withStyle(ChatFormatting.GRAY)
				.append(Component.literal("公告").withStyle(ChatFormatting.GOLD))
				.append(Component.literal("｜").withStyle(ChatFormatting.GRAY))
				.append(Component.literal("有 " + unread + " 則新公告（" + boards + "），打開聊天欄點右下角「公告」查看。").withStyle(ChatFormatting.WHITE));
		showMessage(message);
	}

	public static void showMessage(Component message) {
		Minecraft client = Minecraft.getInstance();
		if (client.player == null) {
			return;
		}
		//? if >=26.1 {
		client.player.sendSystemMessage(message);
		//?} else {
		/*client.player.displayClientMessage(message, false);
		*///?}
	}

	private void endSession() {
		session = null;
		host = null;
		shardPending = false;
		presence.stop();
	}

	public static String mapShard() {
		if (host != null) {
			return !shardPending && label(host).contains("｜") ? host : null;
		}
		Minecraft client = Minecraft.getInstance();
		if (!featuresActive()) {
			return null;
		}
		if (client.getSingleplayerServer() != null) {
			return "local";
		}
		ServerData server = client.getCurrentServer();
		return server == null || server.ip == null ? null : "server_" + server.ip.toLowerCase(Locale.ROOT);
	}

	public static String serverName() {
		if (host == null) {
			return "";
		}
		String label = label(host);
		int split = label.indexOf('｜');
		return split < 0 ? "" : label.substring(split + 1);
	}

	private static String label(String host) {
		return RPConfig.SERVER_LABELS.getOrDefault(host, RPConfig.SERVER_LABELS.get(RPConfig.BASE_DOMAIN));
	}

	private static String state(String host) {
		return host + "｜" + RPConfig.MINECRAFT_VERSION + (RPConfig.LAUNCHER_VERSION != null ? "｜輝煌啟動器" : "");
	}

	private static String imageKey(String host) {
		return RPConfig.SERVER_IMAGE_KEYS.getOrDefault(host, RPConfig.DEFAULT_IMAGE_KEY);
	}

	static String hostOf(String body) {
		try {
			JsonObject json = JsonParser.parseString(body).getAsJsonObject();
			if (!json.has("online") || !json.get("online").getAsBoolean() || !json.has("host") || json.get("host").isJsonNull()) {
				return null;
			}
			String found = json.get("host").getAsString();
			return RPConfig.SERVER_LABELS.containsKey(found) ? found : null;
		} catch (RuntimeException e) {
			return null;
		}
	}

	private void pollServer(Minecraft client) {
		polling = true;
		long sentAt = System.currentTimeMillis();
		String name = client.getUser().getName();
		HttpRequest request = HttpRequest.newBuilder(URI.create(RPConfig.WEBSITE_URL + "/api/presence?name=" + URLEncoder.encode(name, StandardCharsets.UTF_8)))
				.timeout(Duration.ofSeconds(10))
				.header("User-Agent", RPConfig.USER_AGENT)
				.GET()
				.build();
		HTTP.sendAsync(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)).whenComplete((response, error) -> client.execute(() -> {
			polling = false;
			String found = error == null && response.statusCode() == 200 ? hostOf(response.body()) : null;
			if (shardPending && found != null && (switchedFrom != null && !found.equals(switchedFrom) || sentAt - switchedAt >= CONFIRM_MS)) {
				shardPending = false;
			}
			if (host != null && found != null && !found.equals(host)) {
				host = found;
				presence.update(label(host), state(host), imageKey(host));
				ScriptEngine.onServerChange();
			}
		}));
	}
}
