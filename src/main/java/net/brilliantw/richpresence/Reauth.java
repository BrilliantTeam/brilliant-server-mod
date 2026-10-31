package net.brilliantw.richpresence;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;
import com.mojang.authlib.minecraft.UserApiService;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import net.brilliantw.richpresence.mixin.DisconnectedScreenAccessor;
import net.brilliantw.richpresence.mixin.MinecraftAccessor;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.User;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ProfileKeyPairManager;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;

public final class Reauth {
	static final String CLIENT_ID = "ce34fb5a-d8f0-4754-8fbf-68f6e034cdfc";
	static final String REDIRECT_URI = "https://login.microsoftonline.com/common/oauth2/nativeclient";
	private static final String AUTHORIZE_URL = "https://login.microsoftonline.com/consumers/oauth2/v2.0/authorize";
	private static final String TOKEN_URL = "https://login.microsoftonline.com/consumers/oauth2/v2.0/token";
	private static final String XBL_URL = "https://user.auth.xboxlive.com/user/authenticate";
	private static final String XSTS_URL = "https://xsts.auth.xboxlive.com/xsts/authorize";
	private static final String MC_LOGIN_URL = "https://api.minecraftservices.com/authentication/login_with_xbox";
	private static final String MC_PROFILE_URL = "https://api.minecraftservices.com/minecraft/profile";
	private static final String SCOPE = "XboxLive.signin offline_access";
	private static final long RENEW_BEFORE_MS = 15 * 60_000L;
	private static final long CHECK_MS = 60_000L;
	private static final long RETRY_MS = 10 * 60_000L;
	private static final long RATE_LIMIT_MS = 60 * 60_000L;
	private static final long RENEWING_MS = 90_000L;
	private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
	private static final Gson GSON = new Gson();
	private static final SecureRandom RANDOM = new SecureRandom();
	private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger("brilliant-server-mod");

	record Session(String name, UUID uuid, String accessToken, String refreshToken, String xuid) {}

	private static volatile boolean busy;
	private static volatile boolean needLogin;
	private static long nextCheck;
	private static String verifier = "";
	private static String state = "";
	private static String redirect = REDIRECT_URI;
	private static java.net.ServerSocket loopback;
	private static final String DONE_PAGE = "<!doctype html><meta charset=utf-8><title>輝煌模組</title><body style=\"margin:0;height:100vh;display:grid;place-items:center;background:#15171c;color:#f2f2f2;font:18px sans-serif;text-align:center\"><div><h1 style=\"color:#f6e0a4\">登入完成</h1><p>可以關閉這個分頁，回到 Minecraft。</p></div>";
	private static final String FAIL_PAGE = "<!doctype html><meta charset=utf-8><title>輝煌模組</title><body style=\"margin:0;height:100vh;display:grid;place-items:center;background:#15171c;color:#f2f2f2;font:18px sans-serif;text-align:center\"><div><h1 style=\"color:#ff8a80\">登入沒有完成</h1><p>請回到 Minecraft 再試一次。</p></div>";

	private Reauth() {}

	public static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(Reauth::tick);
		ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
			if (screen instanceof DisconnectedScreen disconnected && ModSettings.get().autoReauth
					&& loginFailed(((DisconnectedScreenAccessor) disconnected).brilliant$details().reason())) {
				renew(client);
			}
		});
	}

	public static boolean busy() {
		return busy;
	}

	public static String label() {
		if (busy) {
			return "驗證中…";
		}
		if (needLogin) {
			return "需要登入";
		}
		long exp = expiresAt(Minecraft.getInstance().getUser().getAccessToken());
		return exp <= 0 ? "未使用正版" : "有效（剩 " + Math.max(0, (exp - System.currentTimeMillis()) / 3_600_000L) + " 小時）";
	}

	static boolean loginFailed(Component reason) {
		return reason.getContents() instanceof TranslatableContents contents && contents.getKey().startsWith("disconnect.loginFailedInfo");
	}

	static long expiresAt(String jwt) {
		String[] parts = jwt == null ? new String[0] : jwt.split("\\.");
		if (parts.length != 3) {
			return 0;
		}
		try {
			JsonObject payload = JsonParser.parseString(new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8)).getAsJsonObject();
			return payload.has("exp") ? payload.get("exp").getAsLong() * 1000L : 0;
		} catch (RuntimeException e) {
			return 0;
		}
	}

	private static void tick(Minecraft client) {
		long now = System.currentTimeMillis();
		if (busy || now < nextCheck || !ModSettings.get().autoReauth) {
			return;
		}
		nextCheck = now + CHECK_MS;
		long exp = expiresAt(client.getUser().getAccessToken());
		if (exp > 0 && exp - now < RENEW_BEFORE_MS) {
			renew(client);
		}
	}

	private static void renew(Minecraft client) {
		if (busy) {
			return;
		}
		UUID uuid = client.getUser().getProfileId();
		JsonObject linked = launcherAccount(uuid);
		if (linked != null) {
			String token = open(text(linked, "accessToken"));
			long exp = expiresAt(token);
			if (exp - System.currentTimeMillis() > RENEW_BEFORE_MS && exp > expiresAt(client.getUser().getAccessToken())) {
				apply(client, new Session(text(linked, "name"), uuid, token, open(text(linked, "refreshToken")), text(linked, "xuid")));
				return;
			}
		}
		List<String> tokens = refreshTokens(uuid, linked);
		if (tokens.isEmpty()) {
			if (!needLogin) {
				needLogin = true;
				toast(client, "正版驗證已過期", "到「模組設定 → 連線 → 微軟帳號」登入一次，之後會自動續期");
			}
			nextCheck = System.currentTimeMillis() + RETRY_MS;
			return;
		}
		long now = System.currentTimeMillis();
		long cooldown = sharedCooldown();
		if (cooldown > now) {
			nextCheck = cooldown;
			return;
		}
		if (now - lastModified(authDir().resolve("auth-renewing")) < RENEWING_MS) {
			nextCheck = now + CHECK_MS;
			return;
		}
		touch(authDir().resolve("auth-renewing"));
		busy = true;
		CompletableFuture.supplyAsync(() -> {
			RuntimeException last = null;
			for (String token : tokens) {
				try {
					return finish(post(TOKEN_URL, form(Map.of("grant_type", "refresh_token", "client_id", CLIENT_ID, "refresh_token", token, "scope", SCOPE))));
				} catch (RuntimeException e) {
					last = e;
					if (rateLimited(e)) {
						break;
					}
				}
			}
			throw last;
		}).whenComplete((session, error) -> client.execute(() -> {
			busy = false;
			if (error != null && rateLimited(error)) {
				long until = System.currentTimeMillis() + RATE_LIMIT_MS;
				writeCooldown(until);
				nextCheck = until;
				LOGGER.warn("自動續驗證被 Mojang 限流，60 分鐘內不再重試", error);
				toast(client, "正版驗證暫時被限制", "Mojang 限制了這個網路的登入次數，約 60 分鐘後會自動再試");
			} else if (error != null) {
				needLogin = true;
				nextCheck = System.currentTimeMillis() + RETRY_MS;
				LOGGER.warn("自動續驗證失敗", error);
				toast(client, "自動續驗證失敗", "到「模組設定 → 連線 → 微軟帳號」重新登入");
			} else {
				apply(client, session);
			}
		}));
	}

	public static void startLogin(Screen parent) {
		stopLoopback();
		java.net.ServerSocket server;
		try {
			server = new java.net.ServerSocket(0, 4, java.net.InetAddress.getLoopbackAddress());
			server.setSoTimeout(10 * 60 * 1000);
		} catch (java.io.IOException e) {
			startPasteLogin(parent);
			return;
		}
		loopback = server;
		URI uri = authorize("http://localhost:" + server.getLocalPort());
		String expected = state;
		Minecraft client = Minecraft.getInstance();
		CommandMenuScreen.setScreen(new net.minecraft.client.gui.screens.ConfirmScreen(paste -> {
			stopLoopback();
			if (paste) {
				startPasteLogin(parent);
			} else {
				CommandMenuScreen.setScreen(parent);
			}
		}, Component.literal("在瀏覽器登入微軟帳號"),
				Component.literal("已經用預設瀏覽器打開微軟登入頁。瀏覽器若已登入過微軟帳號，點一下帳號就好；也能用密碼、手機驗證、指紋、Windows Hello 或通行金鑰登入。完成後會自動回到遊戲。"),
				Component.literal("改用貼上網址"), Component.literal("取消")));
		openBrowser(uri);
		Thread thread = new Thread(() -> {
			try (server) {
				while (true) {
					try (java.net.Socket socket = server.accept()) {
						socket.setSoTimeout(10000);
						String line = new java.io.BufferedReader(new java.io.InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8)).readLine();
						String target = line == null ? "/" : line.split(" ").length > 1 ? line.split(" ")[1] : "/";
						Map<String, String> query = parseQuery(target);
						boolean ours = expected.equals(query.get("state"));
						String page = !ours ? "" : query.containsKey("code") ? DONE_PAGE : FAIL_PAGE;
						byte[] body = page.getBytes(StandardCharsets.UTF_8);
						String head = (ours ? "HTTP/1.1 200 OK" : "HTTP/1.1 404 Not Found") + "\r\nContent-Type: text/html; charset=utf-8\r\nContent-Length: " + body.length + "\r\nConnection: close\r\n\r\n";
						socket.getOutputStream().write(head.getBytes(StandardCharsets.US_ASCII));
						socket.getOutputStream().write(body);
						socket.getOutputStream().flush();
						if (ours) {
							client.execute(() -> {
								if (loopback != server) {
									return;
								}
								loopback = null;
								CommandMenuScreen.setScreen(parent);
								String error = exchange(query);
								if (error != null) {
									toast(client, "微軟登入失敗", error);
								}
							});
							return;
						}
					}
				}
			} catch (java.io.IOException ignored) {
			}
		}, "brilliant-login");
		thread.setDaemon(true);
		thread.start();
	}

	private static void openBrowser(URI uri) {
		String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
		String[] command = os.contains("win") ? new String[] {"rundll32", "url.dll,FileProtocolHandler", uri.toString()}
				: os.contains("mac") ? new String[] {"open", uri.toString()} : new String[] {"xdg-open", uri.toString()};
		try {
			new ProcessBuilder(command).start();
		} catch (java.io.IOException e) {
			LOGGER.warn("無法開啟瀏覽器", e);
		}
	}

	private static void stopLoopback() {
		java.net.ServerSocket server = loopback;
		loopback = null;
		if (server != null) {
			try {
				server.close();
			} catch (java.io.IOException ignored) {
			}
		}
	}

	private static Map<String, String> parseQuery(String url) {
		Map<String, String> query = new HashMap<>();
		if (!url.contains("?")) {
			return query;
		}
		for (String pair : url.substring(url.indexOf('?') + 1).split("[&#]")) {
			int eq = pair.indexOf('=');
			if (eq > 0) {
				query.put(pair.substring(0, eq), URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8));
			}
		}
		return query;
	}

	private static URI authorize(String redirectUri) {
		byte[] bytes = new byte[32];
		RANDOM.nextBytes(bytes);
		verifier = HexFormat.of().formatHex(bytes);
		RANDOM.nextBytes(bytes);
		state = HexFormat.of().formatHex(bytes, 0, 16);
		String challenge;
		try {
			challenge = Base64.getUrlEncoder().withoutPadding().encodeToString(MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII)));
		} catch (java.security.NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
		redirect = redirectUri;
		return URI.create(AUTHORIZE_URL + "?" + form(Map.of("client_id", CLIENT_ID, "response_type", "code", "redirect_uri", redirectUri,
				"response_mode", "query", "scope", SCOPE, "code_challenge", challenge, "code_challenge_method", "S256", "state", state, "prompt", "select_account")));
	}

	private static void startPasteLogin(Screen parent) {
		URI uri = authorize(REDIRECT_URI);
		EditDialog dialog = new EditDialog(parent, "微軟帳號登入",
				List.of(new EditDialog.Field("登入完成後，把瀏覽器網址列整段貼上", "https://login.microsoftonline.com/common/oauth2/nativeclient?code=…", "", 4096)),
				null, 0, (values, group) -> completeLogin(values.getFirst()));
		CommandMenuScreen.setScreen(dialog);
		ConfirmLinkScreen.confirmLinkNow(dialog, uri);
	}

	static String completeLogin(String pasted) {
		String url = pasted.strip();
		if (!url.startsWith(REDIRECT_URI) || !url.contains("?")) {
			return "貼上的不是登入完成後的網址";
		}
		return exchange(parseQuery(url));
	}

	private static String exchange(Map<String, String> query) {
		if (state.isEmpty() || !state.equals(query.get("state"))) {
			return "網址已過期，請重新按一次登入";
		}
		if (!query.containsKey("code")) {
			return "登入失敗：" + query.getOrDefault("error_description", query.getOrDefault("error", "缺少授權碼"));
		}
		Minecraft client = Minecraft.getInstance();
		String code = query.get("code");
		String codeVerifier = verifier;
		state = "";
		busy = true;
		CompletableFuture.supplyAsync(() -> finish(post(TOKEN_URL, form(Map.of("grant_type", "authorization_code", "client_id", CLIENT_ID,
				"code", code, "redirect_uri", redirect, "code_verifier", codeVerifier, "scope", SCOPE)))))
				.whenComplete((session, error) -> client.execute(() -> {
					busy = false;
					if (error != null) {
						LOGGER.warn("微軟登入失敗", error);
						toast(client, "微軟登入失敗", message(error));
					} else {
						apply(client, session);
					}
				}));
		return null;
	}

	private static Session finish(JsonObject msa) {
		String msaToken = msa.get("access_token").getAsString();
		String refreshToken = msa.has("refresh_token") ? msa.get("refresh_token").getAsString() : "";
		JsonObject xbl = post(XBL_URL, json("{\"Properties\":{\"AuthMethod\":\"RPS\",\"SiteName\":\"user.auth.xboxlive.com\",\"RpsTicket\":" + GSON.toJson("d=" + msaToken)
				+ "},\"RelyingParty\":\"http://auth.xboxlive.com\",\"TokenType\":\"JWT\"}"));
		String uhs = xbl.getAsJsonObject("DisplayClaims").getAsJsonArray("xui").get(0).getAsJsonObject().get("uhs").getAsString();
		JsonArray userTokens = new JsonArray();
		userTokens.add(xbl.get("Token").getAsString());
		JsonObject xsts;
		try {
			xsts = post(XSTS_URL, json("{\"Properties\":{\"SandboxId\":\"RETAIL\",\"UserTokens\":" + userTokens + "},\"RelyingParty\":\"rp://api.minecraftservices.com/\",\"TokenType\":\"JWT\"}"));
		} catch (IllegalStateException e) {
			String raw = e.getMessage();
			throw new IllegalStateException(raw.contains("2148916233") ? "這個微軟帳號沒有 Xbox 個人資料"
					: raw.contains("2148916238") ? "未成年帳號需要先加入家庭群組" : "XSTS 授權失敗：" + raw);
		}
		String xuid = xsts.getAsJsonObject("DisplayClaims").getAsJsonArray("xui").get(0).getAsJsonObject().has("xid")
				? xsts.getAsJsonObject("DisplayClaims").getAsJsonArray("xui").get(0).getAsJsonObject().get("xid").getAsString() : "";
		JsonObject mc;
		try {
			mc = post(MC_LOGIN_URL, json("{\"identityToken\":" + GSON.toJson("XBL3.0 x=" + uhs + ";" + xsts.get("Token").getAsString()) + "}"));
		} catch (IllegalStateException e) {
			throw new IllegalStateException(e.getMessage().contains("Invalid app registration") ? "輝煌的正版登入尚待 Mojang 審核開通" : e.getMessage());
		}
		String mcToken = mc.get("access_token").getAsString();
		JsonObject profile = send(HttpRequest.newBuilder(URI.create(MC_PROFILE_URL)).header("Authorization", "Bearer " + mcToken).GET());
		String id = profile.get("id").getAsString();
		UUID uuid = UUID.fromString(id.replaceFirst("(\\w{8})(\\w{4})(\\w{4})(\\w{4})(\\w{12})", "$1-$2-$3-$4-$5"));
		return new Session(profile.get("name").getAsString(), uuid, mcToken, refreshToken, xuid);
	}

	private static void apply(Minecraft client, Session session) {
		needLogin = false;
		nextCheck = 0;
		if (!session.refreshToken().isEmpty()) {
			Map<String, String> stored = stored();
			stored.put(session.uuid().toString(), seal(session.refreshToken()));
			try {
				Files.createDirectories(storeFile().getParent());
				Files.writeString(storeFile(), GSON.toJson(stored));
			} catch (java.io.IOException e) {
				LOGGER.warn("無法儲存微軟登入資料", e);
			}
			saveToLauncher(session);
		}
		User user = new User(session.name(), session.uuid(), session.accessToken(), Optional.empty(), Optional.empty());
		//? if >=26.3 {
		/*UserApiService api = com.mojang.authlib.services.MinecraftServicesDiscoveryService.create(client.getProxy(), true).createUserApiService(session.accessToken());
		*///?} else {
		UserApiService api = new com.mojang.authlib.yggdrasil.YggdrasilAuthenticationService(client.getProxy()).createUserApiService(session.accessToken());
		//?}
		MinecraftAccessor accessor = (MinecraftAccessor) client;
		accessor.brilliant$setUser(user);
		accessor.brilliant$setUserApiService(api);
		accessor.brilliant$setProfileKeyPairManager(ProfileKeyPairManager.create(api, user, client.gameDirectory.toPath()));
		toast(client, "正版驗證已更新", "已以 " + session.name() + " 重新驗證");
	}

	private static List<String> refreshTokens(UUID uuid, JsonObject linked) {
		String own = open(stored().get(uuid.toString()));
		String launcher = linked == null ? null : open(text(linked, "refreshToken"));
		return java.util.stream.Stream.of(launcher, own).filter(token -> token != null && !token.isEmpty()).distinct().toList();
	}

	static boolean rateLimited(Throwable error) {
		for (Throwable e = error; e != null; e = e.getCause()) {
			if (e.getMessage() != null && e.getMessage().contains("HTTP 429")) {
				return true;
			}
		}
		return false;
	}

	private static Path authDir() {
		Path home = launcherHome();
		return Files.isDirectory(home) ? home : storeFile().getParent();
	}

	static long sharedCooldown() {
		try {
			return Long.parseLong(Files.readString(authDir().resolve("auth-cooldown")).trim()) * 1000L;
		} catch (java.io.IOException | RuntimeException e) {
			return 0;
		}
	}

	private static void writeCooldown(long untilMs) {
		try {
			Files.createDirectories(authDir());
			Files.writeString(authDir().resolve("auth-cooldown"), Long.toString(untilMs / 1000L));
		} catch (java.io.IOException e) {
			LOGGER.warn("無法記錄驗證冷卻時間", e);
		}
	}

	private static long lastModified(Path file) {
		try {
			return Files.getLastModifiedTime(file).toMillis();
		} catch (java.io.IOException e) {
			return 0;
		}
	}

	private static void touch(Path file) {
		try {
			Files.createDirectories(file.getParent());
			Files.writeString(file, Long.toString(System.currentTimeMillis()));
		} catch (java.io.IOException e) {
			LOGGER.warn("無法標記正在續驗證", e);
		}
	}

	private static final String SEALED = "dpapi:";

	static String seal(String text) {
		if (text == null || text.isEmpty() || text.startsWith(SEALED) || !System.getProperty("os.name", "").startsWith("Windows")) {
			return text;
		}
		try {
			return SEALED + Base64.getEncoder().encodeToString(com.sun.jna.platform.win32.Crypt32Util.cryptProtectData(text.getBytes(StandardCharsets.UTF_8)));
		} catch (Throwable e) {
			LOGGER.warn("無法加密登入資料，改用未加密的方式保存", e);
			return text;
		}
	}

	static String open(String text) {
		if (text == null || !text.startsWith(SEALED)) {
			return text;
		}
		try {
			return new String(com.sun.jna.platform.win32.Crypt32Util.cryptUnprotectData(Base64.getDecoder().decode(text.substring(SEALED.length()))), StandardCharsets.UTF_8);
		} catch (Throwable e) {
			return "";
		}
	}

	private static Path storeFile() {
		return Path.of(System.getProperty("user.home"), ".brilliant-server-mod", "auth.json");
	}

	private static Map<String, String> stored() {
		try {
			Map<String, String> map = GSON.fromJson(Files.readString(storeFile()), new TypeToken<Map<String, String>>() {}.getType());
			return map == null ? new HashMap<>() : new HashMap<>(map);
		} catch (java.io.IOException | RuntimeException e) {
			return new HashMap<>();
		}
	}

	private static String text(JsonObject object, String key) {
		return object.has(key) && object.get(key).isJsonPrimitive() ? object.get(key).getAsString() : "";
	}

	private static JsonArray launcherAccounts() {
		try {
			return JsonParser.parseString(Files.readString(launcherHome().resolve("accounts.json"))).getAsJsonArray();
		} catch (java.io.IOException | RuntimeException e) {
			return new JsonArray();
		}
	}

	private static JsonObject launcherAccount(UUID uuid) {
		for (var element : launcherAccounts()) {
			if (element.isJsonObject() && "msa".equals(text(element.getAsJsonObject(), "kind")) && uuid.toString().equalsIgnoreCase(text(element.getAsJsonObject(), "uuid"))) {
				return element.getAsJsonObject();
			}
		}
		return null;
	}

	static JsonArray upsertLauncherAccount(JsonArray accounts, Session session, long expiresAtSecs) {
		String id = session.uuid().toString().replace("-", "");
		JsonObject account = null;
		for (var element : accounts) {
			if (element.isJsonObject() && id.equals(text(element.getAsJsonObject(), "id"))) {
				account = element.getAsJsonObject();
			}
		}
		if (account == null) {
			account = new JsonObject();
			accounts.add(account);
		}
		account.addProperty("id", id);
		account.addProperty("kind", "msa");
		account.addProperty("name", session.name());
		account.addProperty("uuid", session.uuid().toString());
		account.addProperty("accessToken", seal(session.accessToken()));
		account.addProperty("refreshToken", seal(session.refreshToken()));
		if (!session.xuid().isEmpty()) {
			account.addProperty("xuid", session.xuid());
		}
		account.addProperty("expiresAt", expiresAtSecs);
		account.addProperty("sessionError", "");
		return accounts;
	}

	private static void saveToLauncher(Session session) {
		Path home = launcherHome();
		if (!Files.isDirectory(home)) {
			return;
		}
		long exp = expiresAt(session.accessToken());
		JsonArray accounts = upsertLauncherAccount(launcherAccounts(), session, exp > 0 ? exp / 1000L : System.currentTimeMillis() / 1000L + 3600);
		Path file = home.resolve("accounts.json");
		Path tmp = home.resolve("accounts.json.part" + RANDOM.nextInt(Integer.MAX_VALUE));
		try {
			Files.writeString(tmp, new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(accounts));
			Files.move(tmp, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING, java.nio.file.StandardCopyOption.ATOMIC_MOVE);
		} catch (java.io.IOException e) {
			LOGGER.warn("無法同步登入到輝煌啟動器", e);
			try {
				Files.deleteIfExists(tmp);
			} catch (java.io.IOException ignored) {
			}
		}
	}

	private static Path launcherHome() {
		String custom = System.getenv("BRILLIANT_LAUNCHER_HOME");
		if (custom != null && !custom.isEmpty()) {
			return Path.of(custom);
		}
		String home = System.getProperty("user.home");
		String os = System.getProperty("os.name").toLowerCase(Locale.ROOT);
		if (os.contains("win")) {
			return Path.of(System.getenv("APPDATA"), "BrilliantLauncher");
		}
		if (os.contains("mac")) {
			return Path.of(home, "Library", "Application Support", "BrilliantLauncher");
		}
		String xdg = System.getenv("XDG_DATA_HOME");
		return (xdg != null && !xdg.isEmpty() ? Path.of(xdg) : Path.of(home, ".local", "share")).resolve("BrilliantLauncher");
	}

	private static String form(Map<String, String> values) {
		return values.entrySet().stream()
				.map(e -> URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8) + "=" + URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8))
				.collect(Collectors.joining("&"));
	}

	private static HttpRequest.Builder json(String body) {
		return HttpRequest.newBuilder().header("Content-Type", "application/json").header("Accept", "application/json").POST(HttpRequest.BodyPublishers.ofString(body));
	}

	private static JsonObject post(String url, String formBody) {
		return send(HttpRequest.newBuilder(URI.create(url)).header("Content-Type", "application/x-www-form-urlencoded").POST(HttpRequest.BodyPublishers.ofString(formBody)));
	}

	private static JsonObject post(String url, HttpRequest.Builder builder) {
		return send(builder.uri(URI.create(url)));
	}

	private static JsonObject send(HttpRequest.Builder builder) {
		try {
			HttpResponse<String> response = HTTP.send(builder.timeout(Duration.ofSeconds(20)).header("User-Agent", RPConfig.USER_AGENT).build(),
					HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
			if (response.statusCode() / 100 != 2) {
				throw new IllegalStateException("HTTP " + response.statusCode() + "：" + response.body());
			}
			return JsonParser.parseString(response.body()).getAsJsonObject();
		} catch (java.io.IOException e) {
			throw new IllegalStateException("連線失敗：" + e.getMessage(), e);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException("已中斷", e);
		}
	}

	private static String message(Throwable error) {
		Throwable cause = error instanceof java.util.concurrent.CompletionException && error.getCause() != null ? error.getCause() : error;
		String text = cause.getMessage() == null ? cause.toString() : cause.getMessage();
		return text.length() > 120 ? text.substring(0, 120) + "…" : text;
	}

	private static void toast(Minecraft client, String heading, String body) {
		//? if >=26.2 {
		var toasts = client.gui.toastManager();
		//?} else {
		/*var toasts = client.getToastManager();
		*///?}
		net.minecraft.client.gui.components.toasts.SystemToast.add(toasts, net.minecraft.client.gui.components.toasts.SystemToast.SystemToastId.PERIODIC_NOTIFICATION,
				Component.literal(heading), Component.literal(body));
	}
}
