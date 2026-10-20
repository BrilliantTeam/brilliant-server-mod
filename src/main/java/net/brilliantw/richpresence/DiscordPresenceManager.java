package net.brilliantw.richpresence;

import com.jagrosh.discordipc.IPCClient;
import com.jagrosh.discordipc.IPCListener;
import com.jagrosh.discordipc.entities.ActivityType;
import com.jagrosh.discordipc.entities.Packet;
import com.jagrosh.discordipc.entities.RichPresence;
import com.jagrosh.discordipc.entities.User;
import com.jagrosh.discordipc.entities.pipe.PipeStatus;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public final class DiscordPresenceManager {
	private static final long WATCHDOG_SECONDS = 15;

	private final ScheduledExecutorService watchdog = Executors.newSingleThreadScheduledExecutor(task -> {
		Thread thread = new Thread(task, "brilliantw-richpresence-watchdog");
		thread.setDaemon(true);
		return thread;
	});
	private volatile boolean active;
	private volatile boolean connecting;
	private volatile IPCClient client;
	private volatile String details;
	private volatile String state;
	private volatile String smallImageKey;
	private volatile long startTimestamp;

	public DiscordPresenceManager() {
		watchdog.scheduleWithFixedDelay(this::check, WATCHDOG_SECONDS, WATCHDOG_SECONDS, TimeUnit.SECONDS);
	}

	public synchronized void start(String details, String state, String smallImageKey) {
		this.details = details;
		this.state = state;
		this.smallImageKey = smallImageKey;
		this.startTimestamp = System.currentTimeMillis() / 1000L;
		active = true;
		reopen();
	}

	public void update(String details, String state, String smallImageKey) {
		this.details = details;
		this.state = state;
		this.smallImageKey = smallImageKey;
		send(client);
	}

	public boolean isActive() {
		return active;
	}

	public synchronized void stop() {
		active = false;
		closeClient();
	}

	private synchronized void check() {
		if (!active || connecting) {
			return;
		}
		IPCClient current = client;
		if (current == null || current.getStatus() != PipeStatus.CONNECTED || !send(current)) {
			reopen();
		}
	}

	private boolean send(IPCClient target) {
		if (target == null || target.getStatus() != PipeStatus.CONNECTED) {
			return false;
		}
		try {
			target.sendRichPresence(buildPresence());
			return true;
		} catch (Exception e) {
			return false;
		}
	}

	private void closeClient() {
		IPCClient closing = client;
		client = null;
		if (closing != null) {
			try {
				closing.close();
			} catch (Exception ignored) {
			}
		}
	}

	private void reopen() {
		closeClient();
		IPCClient fresh = new IPCClient(RPConfig.CLIENT_ID);
		fresh.setListener(new IPCListener() {
			@Override
			public void onReady(IPCClient readyClient) {
				send(readyClient);
			}

			@Override
			public void onPacketSent(IPCClient c, Packet packet) {
			}

			@Override
			public void onPacketReceived(IPCClient c, Packet packet) {
			}

			@Override
			public void onActivityJoin(IPCClient c, String secret) {
			}

			@Override
			public void onActivitySpectate(IPCClient c, String secret) {
			}

			@Override
			public void onActivityJoinRequest(IPCClient c, String secret, User user) {
			}

			@Override
			public void onClose(IPCClient c, JsonObject json) {
			}

			@Override
			public void onDisconnect(IPCClient c, Throwable t) {
			}
		});
		client = fresh;
		connecting = true;
		Thread connectThread = new Thread(() -> {
			try {
				fresh.connect();
			} catch (Exception e) {
				System.err.println("[brilliantw-richpresence] Failed to connect to Discord: " + e.getMessage());
			} finally {
				connecting = false;
			}
		}, "brilliantw-richpresence-connect");
		connectThread.setDaemon(true);
		connectThread.start();
	}

	private RichPresence buildPresence() {
		JsonObject button = new JsonObject();
		button.addProperty("label", RPConfig.WEBSITE_LABEL);
		button.addProperty("url", RPConfig.WEBSITE_URL);
		JsonArray buttons = new JsonArray();
		buttons.add(button);
		if (RPConfig.LAUNCHER_VERSION != null) {
			JsonObject launcher = new JsonObject();
			launcher.addProperty("label", "下載輝煌啟動器");
			launcher.addProperty("url", RPConfig.LAUNCHER_URL);
			buttons.add(launcher);
		}

		return new RichPresence.Builder()
				.setActivityType(ActivityType.Playing)
				.setDetails(details)
				.setState(state)
				.setStartTimestamp(startTimestamp)
				.setLargeImage(RPConfig.LARGE_IMAGE_KEY, RPConfig.LAUNCHER_VERSION != null ? "使用輝煌啟動器 v" + RPConfig.LAUNCHER_VERSION : RPConfig.LARGE_IMAGE_TEXT)
				.setSmallImage(smallImageKey, RPConfig.SMALL_IMAGE_TEXT)
				.setButtons(buttons)
				.build();
	}
}
