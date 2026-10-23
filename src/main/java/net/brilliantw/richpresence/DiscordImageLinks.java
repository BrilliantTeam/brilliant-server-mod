package net.brilliantw.richpresence;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class DiscordImageLinks {
	public record Image(String url, String name) {}

	private static final Pattern IMAGEMAP = Pattern.compile("^/?(?:interactivechatdiscordsrv|icd)\\s+imagemap\\s+([0-9a-fA-F-]{36})\\s*$", Pattern.CASE_INSENSITIVE);
	private static final Pattern DISCORD_URL = Pattern.compile("https://(?:cdn|media)\\.discordapp\\.(?:com|net)/attachments/[A-Za-z0-9._~:/?#@!$&'()*+,;=%-]+");
	private static final Pattern IMAGE = Pattern.compile("\\.(png|jpe?g|gif|webp)$");
	private static final Pattern VIDEO = Pattern.compile("\\.(mp4|webm|mov|mkv|m4v)(\\?|$)", Pattern.CASE_INSENSITIVE);
	private static final Logger LOGGER = LoggerFactory.getLogger("brilliant-server-mod");
	private static final int ACCENT = 0xF6E0A4;
	private static final int HINT = 0xF6C236;
	private static final int MAX_ENTRIES = 300;
	private static final Map<String, Image> IMAGES = new LinkedHashMap<>(16, 0.75f, true) {
		@Override
		protected boolean removeEldestEntry(Map.Entry<String, Image> eldest) {
			return size() > MAX_ENTRIES;
		}
	};

	private static final Map<String, String> FULL_URLS = new LinkedHashMap<>(16, 0.75f, true) {
		@Override
		protected boolean removeEldestEntry(Map.Entry<String, String> eldest) {
			return size() > MAX_ENTRIES;
		}
	};

	private DiscordImageLinks() {}

	static String base(String url) {
		int cut = url.length();
		for (char mark : new char[] { '?', '#' }) {
			int index = url.indexOf(mark);
			if (index >= 0) {
				cut = Math.min(cut, index);
			}
		}
		return url.substring(0, cut);
	}

	static void remember(String text) {
		Matcher matcher = DISCORD_URL.matcher(text);
		while (matcher.find()) {
			String url = matcher.group();
			if (url.indexOf('?') > 0) {
				FULL_URLS.put(base(url), url);
			}
		}
	}

	public static Component shorten(Component message) {
		if (message == null || !DISCORD_URL.matcher(message.getString()).find()) {
			return message;
		}
		remember(message.getString());
		MutableComponent out = Component.empty();
		message.visit((Style style, String text) -> {
			Matcher matcher = DISCORD_URL.matcher(text);
			int last = 0;
			while (matcher.find()) {
				if (matcher.start() > last) {
					out.append(Component.literal(text.substring(last, matcher.start())).setStyle(style));
				}
				out.append(label(matcher.group(), style));
				last = matcher.end();
			}
			if (last < text.length()) {
				out.append(Component.literal(text.substring(last)).setStyle(style));
			}
			return Optional.empty();
		}, Style.EMPTY);
		return out;
	}

	private static Component label(String url, Style style) {
		URI uri;
		try {
			uri = URI.create(url);
		} catch (IllegalArgumentException e) {
			return Component.literal(url).setStyle(style);
		}
		String lower = base(url).toLowerCase(Locale.ROOT);
		boolean image = IMAGE.matcher(lower).find();
		boolean video = !image && VIDEO.matcher(lower).find();
		String noun = image ? "一張圖片" : video ? "一段影片" : "一個檔案";
		String hint = image ? "點擊後即可在遊戲內預覽這張圖片" : video ? "點擊後即可在遊戲外查看這段影片" : "點擊後即可在遊戲外查看這個檔案";
		Style link = Style.EMPTY.withClickEvent(new ClickEvent.OpenUrl(uri))
				.withHoverEvent(new HoverEvent.ShowText(Component.literal(hint).withColor(HINT)));
		return Component.empty().setStyle(link)
				.append(Component.literal("發佈了：「").withStyle(ChatFormatting.GRAY))
				.append(Component.literal(noun).withColor(ACCENT))
				.append(Component.literal("」").withStyle(ChatFormatting.GRAY));
	}

	public static URI repair(URI uri) {
		if (uri == null || uri.getRawQuery() != null) {
			return uri;
		}
		String full = FULL_URLS.get(base(uri.toString()));
		try {
			return full == null ? uri : URI.create(full);
		} catch (IllegalArgumentException e) {
			return uri;
		}
	}

	public static void register() {
		ClientSendMessageEvents.ALLOW_COMMAND.register(command -> !intercept(command));
	}

	public static boolean intercept(String command) {
		Matcher matcher = IMAGEMAP.matcher(command);
		if (!matcher.matches() || !RichPresenceModClient.isOnBrilliantServer() || !ModSettings.get().imageLinks) {
			return false;
		}
		String uuid = matcher.group(1).toLowerCase(Locale.ROOT);
		Image image = IMAGES.get(uuid);
		if (image == null) {
			LOGGER.warn("No original image URL recorded for imagemap {}, falling back to the map preview", uuid);
			return false;
		}
		if (VIDEO.matcher(image.url()).find()) {
			return false;
		}
		String url = repair(URI.create(image.url())).toString();
		Minecraft.getInstance().execute(() -> CommandMenuScreen.setScreen(new ImageViewerScreen(url, image.name())));
		return true;
	}

	static boolean isDiscordImage(URI uri) {
		String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
		String path = uri.getPath() == null ? "" : uri.getPath().toLowerCase(Locale.ROOT);
		return "https".equalsIgnoreCase(uri.getScheme()) && uri.getPort() == -1
				&& (host.equals("cdn.discordapp.com") || host.equals("media.discordapp.net"))
				&& path.startsWith("/attachments/") && IMAGE.matcher(path).find();
	}

	public static boolean openUrl(URI clicked) {
		URI uri = repair(clicked);
		if (uri == null || !RichPresenceModClient.isOnBrilliantServer() || !ModSettings.get().imageLinks || !isDiscordImage(uri)) {
			return false;
		}
		Minecraft.getInstance().execute(() -> CommandMenuScreen.setScreen(new ImageViewerScreen(uri.toString(), null)));
		return true;
	}

	public static void scan(Component message) {
		remember(message.getString());
		String[] pending = new String[1];
		StringBuilder name = new StringBuilder();
		message.visit((Style style, String text) -> {
			ClickEvent click = style.getClickEvent();
			if (click instanceof ClickEvent.RunCommand run) {
				Matcher matcher = IMAGEMAP.matcher(run.command());
				if (matcher.matches()) {
					String uuid = matcher.group(1).toLowerCase(Locale.ROOT);
					if (!uuid.equals(pending[0])) {
						pending[0] = uuid;
						name.setLength(0);
					}
					name.append(text);
				}
			} else if (click instanceof ClickEvent.OpenUrl open && pending[0] != null) {
				IMAGES.put(pending[0], new Image(open.uri().toString(), name.toString().trim()));
				pending[0] = null;
			}
			return Optional.empty();
		}, Style.EMPTY);
	}
}
