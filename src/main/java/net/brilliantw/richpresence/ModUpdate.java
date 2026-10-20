package net.brilliantw.richpresence;

import com.google.gson.Gson;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

public final class ModUpdate {
	public static final class Latest {
		public int build;
		public String version = "";
		public String notes = "";
		public String page = "";
		public String url = "";
		public String file = "";
		public long size;
		public String sha256;
		public String signature;
	}

	public enum Status { IDLE, DOWNLOADING, READY, FAILED }

	private static final String API = RPConfig.WEBSITE_URL + "/api/mod/latest?mc=";
	private static final long MAX_BYTES = 32L * 1024 * 1024;
	private static final Pattern BUILD = Pattern.compile("^\\d+\\.\\d+\\.(\\d+)");
	private static final Pattern FILE = Pattern.compile("^brilliant-server-mod-[0-9.]+\\+[0-9.]+\\.jar$");
	private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).followRedirects(HttpClient.Redirect.NORMAL).build();
	private static final Gson GSON = new Gson();

	public static final int CHECK_TICKS = 5 * 60 * 20;

	private static Latest available;
	private static boolean checking;
	private static int notifiedBuild;
	private static volatile Status status = Status.IDLE;
	private static volatile String message = "";
	private static volatile int progress;

	private ModUpdate() {}

	static int buildOf(String version) {
		Matcher matcher = BUILD.matcher(version == null ? "" : version);
		return matcher.find() ? Integer.parseInt(matcher.group(1)) : 0;
	}

	public static int currentBuild() {
		return buildOf(RPConfig.MOD_VERSION);
	}

	public static Latest available() {
		return available;
	}

	public static Status status() {
		return status;
	}

	public static String message() {
		return message;
	}

	public static int progress() {
		return progress;
	}

	private static Path updateDir() {
		return FabricLoader.getInstance().getConfigDir().resolve("brilliant-server-mod-update");
	}

	public static void cleanup() {
		try (Stream<Path> files = Files.exists(updateDir()) ? Files.list(updateDir()) : Stream.empty()) {
			for (Path file : files.toList()) {
				Files.deleteIfExists(file);
			}
		} catch (IOException ignored) {
		}
	}

	public static Path currentJar() {
		return FabricLoader.getInstance().getModContainer("brilliant-server-mod")
				.flatMap(container -> container.getOrigin().getPaths().stream().findFirst())
				.filter(path -> path.getFileName().toString().endsWith(".jar") && Files.isRegularFile(path))
				.orElse(null);
	}

	public static boolean canInstall() {
		Latest latest = available;
		return latest != null && latest.sha256 != null && latest.sha256.matches("[0-9a-f]{64}") && latest.signature != null && !latest.signature.isBlank() && FILE.matcher(latest.file).matches()
				&& latest.url.startsWith(RPConfig.WEBSITE_URL + "/api/mod/download/") && currentJar() != null;
	}

	public static void check() {
		if (checking || currentBuild() == 0 || status == Status.DOWNLOADING || status == Status.READY) {
			return;
		}
		checking = true;
		HttpRequest request = HttpRequest.newBuilder(URI.create(API + URLEncoder.encode(RPConfig.MINECRAFT_VERSION, StandardCharsets.UTF_8)))
				.timeout(Duration.ofSeconds(15))
				.header("User-Agent", RPConfig.USER_AGENT)
				.GET()
				.build();
		HTTP.sendAsync(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)).whenComplete((response, error) -> {
			checking = false;
			if (error != null || response.statusCode() != 200) {
				return;
			}
			Latest latest;
			try {
				latest = GSON.fromJson(response.body(), Latest.class);
			} catch (RuntimeException e) {
				return;
			}
			if (latest == null || latest.build <= currentBuild()) {
				return;
			}
			Minecraft.getInstance().execute(() -> {
				if (status == Status.DOWNLOADING || status == Status.READY) {
					return;
				}
				available = latest;
				if (latest.build == notifiedBuild) {
					return;
				}
				notifiedBuild = latest.build;
				RichPresenceModClient.showMessage(Component.literal("｜").withStyle(ChatFormatting.GRAY)
						.append(Component.literal("模組").withStyle(ChatFormatting.GOLD))
						.append(Component.literal("｜").withStyle(ChatFormatting.GRAY))
						.append(Component.literal("輝煌模組有新版本 " + label(latest) + (latest.notes.isEmpty() ? "" : "：" + latest.notes) + "。打開聊天欄右下角「選單」即可一鍵更新。")
								.withStyle(ChatFormatting.WHITE)));
			});
		});
	}

	public static String label(Latest latest) {
		return latest.version == null || latest.version.isEmpty() ? "第 " + latest.build + " 版" : latest.version;
	}

	public static void download() {
		Latest latest = available;
		Path oldJar = currentJar();
		if (status == Status.DOWNLOADING || status == Status.READY || !canInstall() || oldJar == null) {
			return;
		}
		status = Status.DOWNLOADING;
		progress = 0;
		message = "";
		Thread worker = new Thread(() -> {
			try {
				Files.createDirectories(updateDir());
				Path newJar = updateDir().resolve(latest.file);
				fetch(latest, newJar);
				Path target = oldJar.resolveSibling(latest.file);
				if (target.equals(oldJar)) {
					throw new IOException("新版本與目前檔名相同");
				}
				launchHelper(oldJar, newJar, target);
				status = Status.READY;
				Minecraft.getInstance().execute(() -> RichPresenceModClient.showMessage(Component.literal("｜").withStyle(ChatFormatting.GRAY)
						.append(Component.literal("模組").withStyle(ChatFormatting.GOLD))
						.append(Component.literal("｜").withStyle(ChatFormatting.GRAY))
						.append(Component.literal("新版本已下載完成，關閉遊戲後會自動安裝；也可以在「選單」的更新頁面直接關閉遊戲。").withStyle(ChatFormatting.WHITE))));
			} catch (Exception e) {
				message = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
				status = Status.FAILED;
			}
		}, "brilliant-server-mod-update");
		worker.setDaemon(true);
		worker.start();
	}

	private static void fetch(Latest latest, Path destination) throws Exception {
		HttpRequest request = HttpRequest.newBuilder(URI.create(latest.url))
				.timeout(Duration.ofMinutes(2))
				.header("User-Agent", RPConfig.USER_AGENT)
				.GET()
				.build();
		HttpResponse<InputStream> response = HTTP.send(request, HttpResponse.BodyHandlers.ofInputStream());
		if (response.statusCode() != 200) {
			throw new IOException("下載失敗（HTTP " + response.statusCode() + "）");
		}
		MessageDigest digest = MessageDigest.getInstance("SHA-256");
		Path part = destination.resolveSibling(destination.getFileName() + ".part");
		long total = 0;
		try (InputStream in = response.body(); OutputStream out = Files.newOutputStream(part)) {
			byte[] buffer = new byte[64 * 1024];
			for (int read = in.read(buffer); read >= 0; read = in.read(buffer)) {
				total += read;
				if (total > MAX_BYTES) {
					throw new IOException("檔案過大");
				}
				digest.update(buffer, 0, read);
				out.write(buffer, 0, read);
				if (latest.size > 0) {
					progress = (int) Math.min(100, total * 100 / latest.size);
				}
			}
		}
		String sha = HexFormat.of().formatHex(digest.digest());
		if (!sha.equals(latest.sha256)) {
			Files.deleteIfExists(part);
			throw new IOException("檔案驗證失敗，已取消更新");
		}
		if (!UpdateTrust.verify(latest.file, sha, latest.signature)) {
			Files.deleteIfExists(part);
			throw new IOException("更新檔沒有通過輝煌官方數位簽章驗證，為了安全已取消更新");
		}
		Files.move(part, destination, StandardCopyOption.REPLACE_EXISTING);
	}

	private static void launchHelper(Path oldJar, Path newJar, Path target) throws IOException {
		String java = ProcessHandle.current().info().command().orElseThrow(() -> new IOException("找不到 Java 執行檔"));
		new ProcessBuilder(List.of(java, "-cp", newJar.toString(), UpdateHelper.class.getName(),
				Long.toString(ProcessHandle.current().pid()), oldJar.toString(), newJar.toString(), target.toString()))
				.redirectErrorStream(true)
				.redirectOutput(updateDir().resolve("helper.log").toFile())
				.start();
	}
}
