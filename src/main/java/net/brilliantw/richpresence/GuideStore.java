package net.brilliantw.richpresence;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;

public final class GuideStore {
	public static final class Step {
		public String slug = "";
		public String title = "";
		@SerializedName("do")
		public String action = "";
	}

	public static final class Route {
		public String title = "";
		public String lead = "";
		public List<Step> steps = List.of();
	}

	public static final class Post {
		public String slug = "";
		public String title = "";
		public String excerpt = "";
		public String snippet = "";
	}

	public static final class Index {
		public String lead = "";
		public List<Route> routes = List.of();
		public List<Post> posts = List.of();
	}

	public static final class Results {
		public List<Post> results = List.of();
	}

	public static final class Article {
		public String slug = "";
		public String title = "";
		public String url = "";
		public List<AnnouncementStore.Line> lines = List.of();
	}

	private static final String API = RPConfig.WEBSITE_URL + "/api/guide/";
	private static final long ARTICLE_TTL = 10 * 60 * 1000;
	private static final HttpClient HTTP = RPConfig.HTTP;
	private static final Gson GSON = new Gson();
	private static final Map<String, Article> ARTICLES = new HashMap<>();
	private static final Map<String, Long> FETCHED = new HashMap<>();
	private static Index index;

	private GuideStore() {}

	public static Index index() {
		return index;
	}

	public static boolean isPost(String slug) {
		return index != null && index.posts.stream().anyMatch(post -> post.slug.equals(slug));
	}

	private static <T> void get(String path, Class<T> type, Consumer<T> done, Consumer<String> failed) {
		HttpRequest request = HttpRequest.newBuilder(URI.create(API + path))
				.timeout(Duration.ofSeconds(15))
				.header("User-Agent", RPConfig.USER_AGENT)
				.GET()
				.build();
		HTTP.sendAsync(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)).whenComplete((response, error) -> {
			String message = null;
			T value = null;
			if (error != null) {
				message = "無法連線到 brilliantw.net，請稍後再試";
			} else if (response.statusCode() == 404) {
				message = "找不到這篇文章";
			} else if (response.statusCode() != 200) {
				message = "官網暫時無法提供內容（HTTP " + response.statusCode() + "）";
			} else {
				try {
					value = GSON.fromJson(response.body(), type);
				} catch (RuntimeException ignored) {
				}
				if (value == null) {
					message = "官網資料格式不正確";
				}
			}
			String finalMessage = message;
			T finalValue = value;
			Minecraft.getInstance().execute(() -> {
				if (finalMessage != null) {
					failed.accept(finalMessage);
				} else {
					done.accept(finalValue);
				}
			});
		});
	}

	public static void loadIndex(Runnable done, Consumer<String> failed) {
		get("index", Index.class, value -> {
			index = value;
			done.run();
		}, failed);
	}

	public static void search(String query, Consumer<List<Post>> done, Consumer<String> failed) {
		get("search?q=" + URLEncoder.encode(query, StandardCharsets.UTF_8), Results.class, value -> done.accept(value.results), failed);
	}

	public static void article(String slug, Consumer<Article> done, Consumer<String> failed) {
		Article cached = ARTICLES.get(slug);
		if (cached != null && System.currentTimeMillis() - FETCHED.get(slug) < ARTICLE_TTL) {
			done.accept(cached);
			return;
		}
		get("article?slug=" + URLEncoder.encode(slug, StandardCharsets.UTF_8), Article.class, value -> {
			ARTICLES.put(slug, value);
			FETCHED.put(slug, System.currentTimeMillis());
			done.accept(value);
		}, failed);
	}

	public static String slugOf(String url) {
		try {
			URI uri = URI.create(url);
			URI site = URI.create(RPConfig.WEBSITE_URL);
			String host = uri.getHost();
			if (host == null || !(host.equalsIgnoreCase(site.getHost()) || ("www." + host).equalsIgnoreCase(site.getHost()))) {
				return null;
			}
			String path = uri.getPath() == null ? "" : uri.getPath().replaceAll("^/+|/+$", "");
			return path.isEmpty() || path.contains("/") || !isPost(path) ? null : path;
		} catch (IllegalArgumentException e) {
			return null;
		}
	}
}
