package net.brilliantw.richpresence;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;

public final class ChatReply {
	private static final Pattern COMMAND = Pattern.compile("^/?chatreply\\s+(-?\\d+)\\s*$");
	private static final int HEIGHT = 14;
	private static final int MAX_MESSAGES = 200;
	private static final Map<String, String> MESSAGES = new LinkedHashMap<>() {
		@Override
		protected boolean removeEldestEntry(Map.Entry<String, String> eldest) {
			return size() > MAX_MESSAGES;
		}
	};

	private static String target;
	private static String quote;

	private ChatReply() {}

	public static void scan(Component message) {
		StringBuilder text = new StringBuilder();
		String[] id = new String[1];
		message.visit((Style style, String part) -> {
			if (style.getClickEvent() instanceof ClickEvent.RunCommand run) {
				Matcher matcher = COMMAND.matcher(run.command());
				if (matcher.matches()) {
					id[0] = matcher.group(1);
					text.append(part);
				}
			}
			return Optional.empty();
		}, Style.EMPTY);
		if (id[0] != null) {
			MESSAGES.put(id[0], text.toString().trim());
		}
	}

	public static boolean intercept(String command, Screen screen) {
		Matcher matcher = COMMAND.matcher(command);
		if (!matcher.matches() || !(screen instanceof ChatScreen) || !RichPresenceModClient.featuresActive()) {
			return false;
		}
		target = matcher.group(1);
		quote = MESSAGES.getOrDefault(target, "");
		return true;
	}

	public static boolean active() {
		return target != null;
	}

	public static void clear() {
		target = null;
		quote = null;
	}

	public static String apply(String message) {
		if (target == null || message.isBlank() || message.startsWith("/")) {
			return message;
		}
		String wrapped = "/chatreply " + target + " " + message;
		clear();
		return wrapped;
	}

	private static int left(ChatScreen screen) {
		return ChannelBar.chipsRight(screen) + 4;
	}

	private static int closeX(ChatScreen screen) {
		return Math.max(left(screen), ChannelBar.menuLeft(screen) - 4 - HEIGHT);
	}

	public static void render(ChatScreen screen, Gfx gfx, int mouseX, int mouseY) {
		if (!active()) {
			return;
		}
		Font font = Minecraft.getInstance().font;
		int top = ChannelBar.barY(screen);
		int left = left(screen);
		gfx.fill(left, top, closeX(screen) + HEIGHT, top + HEIGHT, 0x80000000);
		gfx.fill(left, top, left + 2, top + HEIGHT, Theme.ACCENT);
		String label = "回覆 ";
		gfx.label(font, label, left + 5, top, HEIGHT, Theme.ACCENT);
		int x = left + 5 + font.width(label);
		gfx.label(font, font.plainSubstrByWidth(quote, Math.max(0, closeX(screen) - x - 4)), x, top, HEIGHT, Theme.TEXT_SECONDARY);
		boolean hovered = mouseX >= closeX(screen) && mouseX < closeX(screen) + HEIGHT && mouseY >= top && mouseY < top + HEIGHT;
		Icons.CLOSE.drawCentered(gfx, closeX(screen), top, HEIGHT, HEIGHT, hovered ? 0xFFFFFFFF : Theme.TEXT_SECONDARY);
	}

	public static boolean mouseClicked(ChatScreen screen, double mouseX, double mouseY, int button) {
		if (!active() || button != InputConstants.MOUSE_BUTTON_LEFT) {
			return false;
		}
		int top = ChannelBar.barY(screen);
		if (mouseX >= closeX(screen) && mouseX < closeX(screen) + HEIGHT && mouseY >= top && mouseY < top + HEIGHT) {
			clear();
			return true;
		}
		return false;
	}
}
