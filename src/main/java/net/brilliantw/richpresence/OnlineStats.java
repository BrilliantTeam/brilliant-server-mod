package net.brilliantw.richpresence;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;
import net.minecraft.client.Minecraft;

public final class OnlineStats {
	private static final long REFRESH_MS = 30_000;
	private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
	private static volatile int total;
	private record Server(String key, String label, int count) {}

	private static volatile java.util.List<Server> servers = java.util.List.of();
	private static volatile long fetchedAt;
	private static volatile boolean fetching;

	private OnlineStats() {}

	private static void ensure() {
		long now = System.currentTimeMillis();
		if (fetching || now - fetchedAt < REFRESH_MS || Minecraft.getInstance() == null) {
			return;
		}
		fetching = true;
		HttpRequest request = HttpRequest.newBuilder(URI.create(RPConfig.WEBSITE_URL + "/api/online-summary"))
				.timeout(Duration.ofSeconds(10))
				.header("User-Agent", RPConfig.USER_AGENT)
				.GET()
				.build();
		HTTP.sendAsync(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)).whenComplete((response, error) -> {
			try {
				if (error == null && response.statusCode() == 200) {
					JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
					java.util.List<Server> parsed = new java.util.ArrayList<>();
					json.getAsJsonArray("servers").forEach(element -> {
						JsonObject server = element.getAsJsonObject();
						parsed.add(new Server(server.get("key").getAsString(), server.get("label").getAsString(), server.get("count").getAsInt()));
					});
					servers = parsed;
					total = json.get("total").getAsInt();
				}
			} catch (RuntimeException ignored) {
			} finally {
				fetchedAt = System.currentTimeMillis();
				fetching = false;
			}
		});
	}

	public static int total() {
		ensure();
		return total;
	}

	public static int server(String name) {
		ensure();
		String needle = name.strip().toLowerCase(Locale.ROOT);
		if (needle.isEmpty()) {
			return 0;
		}
		for (Server server : servers) {
			if (server.key().toLowerCase(Locale.ROOT).contains(needle) || server.label().toLowerCase(Locale.ROOT).contains(needle)) {
				return server.count();
			}
		}
		return 0;
	}
}
