package net.brilliantw.richpresence;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class DiscordImageLinks {
	public record Image(String url, String name) {}

	private static final Pattern IMAGEMAP = Pattern.compile("^/?(?:interactivechatdiscordsrv|icd)\\s+imagemap\\s+([0-9a-fA-F-]{36})\\s*$", Pattern.CASE_INSENSITIVE);
	private static final Pattern VIDEO = Pattern.compile("\\.(mp4|webm|mov|mkv|m4v)(\\?|$)", Pattern.CASE_INSENSITIVE);
	private static final Logger LOGGER = LoggerFactory.getLogger("brilliant-server-mod");
	private static final int MAX_ENTRIES = 300;
	private static final Map<String, Image> IMAGES = new LinkedHashMap<>(16, 0.75f, true) {
		@Override
		protected boolean removeEldestEntry(Map.Entry<String, Image> eldest) {
			return size() > MAX_ENTRIES;
		}
	};

	private DiscordImageLinks() {}

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
		Minecraft.getInstance().execute(() -> CommandMenuScreen.setScreen(new ImageViewerScreen(image.url(), image.name())));
		return true;
	}

	public static void scan(Component message) {
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
