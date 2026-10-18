package net.brilliantw.richpresence;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntConsumer;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;

public final class AnnouncementStore {
	public static final class Run {
		public String t = "";
		public int b;
		public int i;
		public int u;
		public int s;
		public int code;
		public int sp;
		public int dim;
		public String c;
		public String h;
		public String e;
	}

	public static final class Line {
		public String k = "p";
		public int q;
		public int d;
		public String bar;
		public String n;
		public Integer p;
		public List<Run> r = List.of();
		public String src;
		public String href;
		public String l;
		public int w;
		public int h;
		public int v;
		public int sp;
		public int sm;
		public List<Line> cells;
		public int tb;
		public int th;
		public int cs;
		public int rs;
	}

	public static final class Message {
		public String id = "";
		public String author = "";
		public long time;
		public boolean edited;
		public List<Line> lines = List.of();
	}

	public static final class Board {
		public String key = "";
		public String label = "";
	}

	private static final class Page {
		List<Board> boards = List.of();
		List<Message> messages = List.of();
		String last = "";
		boolean more;
	}

	public static final class BoardState {
		public final List<Message> messages = new ArrayList<>();
		public String last = "";
		public boolean more;
		public boolean loading;
		public boolean loaded;
		public String error = "";
		public long fetchedAt;
		public int version;
	}

	private static final String API = RPConfig.WEBSITE_URL + "/api/announcements/plain";
	private static final long STALE_MS = 60 * 1000;
	private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
	private static final Gson GSON = new Gson();
	private static final Map<String, BoardState> STATES = new HashMap<>();
	public static final List<Board> BOARDS = new ArrayList<>();
	private static boolean boardsLoading;
	private static String boardsError = "";
	private static Map<String, String> seen;

	private AnnouncementStore() {}

	public static BoardState state(String key) {
		return STATES.computeIfAbsent(key, k -> new BoardState());
	}

	public static boolean boardsLoading() {
		return boardsLoading;
	}

	public static String boardsError() {
		return boardsError;
	}

	private static CompletableFuture<Page> request(String board, String before) {
		StringBuilder url = new StringBuilder(API);
		if (board != null) {
			url.append("?ch=").append(URLEncoder.encode(board, StandardCharsets.UTF_8));
			if (before != null && !before.isEmpty()) {
				url.append("&before=").append(URLEncoder.encode(before, StandardCharsets.UTF_8));
			}
		}
		HttpRequest request = HttpRequest.newBuilder(URI.create(url.toString()))
				.timeout(Duration.ofSeconds(15))
				.header("User-Agent", RPConfig.USER_AGENT)
				.GET()
				.build();
		return HTTP.sendAsync(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)).thenApply(response -> {
			if (response.statusCode() != 200) {
				throw new IllegalStateException("伺服器暫時無法提供公告（HTTP " + response.statusCode() + "）");
			}
			Page page = GSON.fromJson(response.body(), Page.class);
			if (page == null) {
				throw new IllegalStateException("公告資料格式不正確");
			}
			return page;
		});
	}

	private static String errorText(Throwable error) {
		Throwable cause = error;
		while (cause.getCause() != null && !(cause instanceof IllegalStateException)) {
			cause = cause.getCause();
		}
		return cause instanceof IllegalStateException ? cause.getMessage() : "無法連線到 brilliantw.net，請稍後再試";
	}

	public static void loadBoards(Runnable done) {
		if (boardsLoading) {
			return;
		}
		boardsLoading = true;
		boardsError = "";
		request(null, null).whenComplete((page, error) -> Minecraft.getInstance().execute(() -> {
			boardsLoading = false;
			if (error != null) {
				boardsError = errorText(error);
			} else {
				BOARDS.clear();
				BOARDS.addAll(page.boards);
			}
			done.run();
		}));
	}

	public static boolean isStale(String key) {
		BoardState state = state(key);
		return !state.loaded || System.currentTimeMillis() - state.fetchedAt > STALE_MS;
	}

	public static void refresh(String key, Runnable done) {
		load(key, false, done);
	}

	public static void loadMore(String key, Runnable done) {
		BoardState state = state(key);
		if (state.more && !state.loading) {
			load(key, true, done);
		}
	}

	private static void load(String key, boolean more, Runnable done) {
		BoardState state = state(key);
		if (state.loading) {
			return;
		}
		state.loading = true;
		state.error = "";
		request(key, more ? state.last : null).whenComplete((page, error) -> Minecraft.getInstance().execute(() -> {
			state.loading = false;
			if (error != null) {
				state.error = errorText(error);
			} else {
				if (!more) {
					state.messages.clear();
					state.fetchedAt = System.currentTimeMillis();
				}
				state.messages.addAll(page.messages);
				state.last = page.last;
				state.more = page.more;
				state.loaded = true;
				state.version++;
				if (!more && !page.messages.isEmpty() && seen().get(key) == null) {
					seen().put(key, page.messages.getFirst().id);
					saveSeen();
				}
			}
			done.run();
		}));
	}

	public static void refreshAll(IntConsumer onUnread) {
		loadBoards(() -> {
			if (BOARDS.isEmpty()) {
				return;
			}
			AtomicInteger remaining = new AtomicInteger(BOARDS.size());
			for (Board board : List.copyOf(BOARDS)) {
				refresh(board.key, () -> {
					if (remaining.decrementAndGet() == 0) {
						onUnread.accept(totalUnread());
					}
				});
			}
		});
	}

	public static Message latest() {
		Message best = null;
		for (BoardState state : STATES.values()) {
			for (Message message : state.messages) {
				if (best == null || message.time > best.time) {
					best = message;
				}
			}
		}
		return best;
	}

	public static String titleOf(Message message) {
		if (message == null || message.lines == null) {
			return "";
		}
		for (Line line : message.lines) {
			StringBuilder text = new StringBuilder();
			if (line.r != null) {
				line.r.forEach(run -> text.append(run.t == null ? "" : run.t));
			}
			if (!text.toString().isBlank()) {
				return text.toString().strip();
			}
		}
		return "";
	}

	public static int compareIds(String a, String b) {
		return a.length() != b.length() ? Integer.compare(a.length(), b.length()) : a.compareTo(b);
	}

	public static String seenId(String key) {
		return seen().get(key);
	}

	public static boolean isNewer(String id, String seenId) {
		return seenId != null && compareIds(id, seenId) > 0;
	}

	public static int unread(String key) {
		String seenId = seenId(key);
		return (int) state(key).messages.stream().filter(message -> isNewer(message.id, seenId)).count();
	}

	public static int totalUnread() {
		return BOARDS.stream().mapToInt(board -> unread(board.key)).sum();
	}

	public static void markSeen(String key) {
		BoardState state = state(key);
		if (state.messages.isEmpty()) {
			return;
		}
		String newest = state.messages.getFirst().id;
		String current = seen().get(key);
		if (current == null || compareIds(newest, current) > 0) {
			seen().put(key, newest);
			saveSeen();
		}
	}

	private static Path seenFile() {
		return FabricLoader.getInstance().getConfigDir().resolve("brilliant-server-mod-announcements.json");
	}

	private static Map<String, String> seen() {
		if (seen == null) {
			seen = new HashMap<>();
			try {
				Path file = seenFile();
				if (Files.exists(file)) {
					Map<String, String> loaded = GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8), new TypeToken<Map<String, String>>() {}.getType());
					if (loaded != null) {
						loaded.forEach((key, value) -> {
							if (key != null && value != null && value.matches("\\d{1,20}")) {
								seen.put(key, value);
							}
						});
					}
				}
			} catch (Exception ignored) {
			}
		}
		return seen;
	}

	private static void saveSeen() {
		try {
			Path file = seenFile();
			Path temp = file.resolveSibling(file.getFileName() + ".tmp");
			Files.writeString(temp, GSON.toJson(seen()), StandardCharsets.UTF_8);
			Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
		} catch (IOException e) {
			System.err.println("[brilliant-server-mod] Failed to save announcement state: " + e.getMessage());
		}
	}
}
