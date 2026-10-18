package net.brilliantw.richpresence;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.EnumSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.network.chat.Component;

public final class ChatTabs {
	enum Tab {
		ALL("全部", null, 0xFFFFFFFF),
		CHAT("聊天", "｜聊天｜", 0xFFBBDDF6),
		TRADE("交易", "｜交易｜", 0xFFF9C3F5),
		AREA("區域", "｜區域｜", 0xFFF7E0A4),
		GUILD("公會", "｜公會｜", 0xFFCFFFC0),
		RESIDENCE("領地", "｜領地｜", 0xFFF6E0A4);

		final String label;
		final String prefix;
		final int color;

		Tab(String label, String prefix, int color) {
			this.label = label;
			this.prefix = prefix;
			this.color = color;
		}
	}

	private record Chip(Tab tab, int x, int y, int w, int h) {
		boolean contains(double mouseX, double mouseY) {
			return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
		}
	}

	private static final int HEIGHT = 12;
	private static final int GAP = 2;
	private static final int REMEMBERED = 500;
	private static final Pattern CONTINUATION = Pattern.compile("^\\s*[｜|](?![^｜|\\s]{1,8}[｜|])", Pattern.UNICODE_CHARACTER_CLASS);

	private static final Set<Tab> selected = EnumSet.noneOf(Tab.class);
	private static final Map<Component, Tab> channels = new IdentityHashMap<>();
	private static final Deque<Component> order = new ArrayDeque<>();
	private static Tab pending;
	private static boolean viewing;

	private ChatTabs() {}

	static Tab channelOf(String plain) {
		String text = plain.stripLeading();
		for (Tab tab : Tab.values()) {
			if (tab.prefix != null && text.startsWith(tab.prefix)) {
				return tab;
			}
		}
		return null;
	}

	static String channelLabel(String plain) {
		Tab tab = channelOf(plain);
		return tab == null ? null : tab.label;
	}

	static boolean isContinuation(String plain) {
		return CONTINUATION.matcher(plain).find();
	}

	static Tab classify(String plain) {
		Tab channel = channelOf(plain);
		if (channel != null) {
			pending = plain.stripTrailing().endsWith("：") ? channel : null;
			return channel;
		}
		Tab header = pending;
		pending = null;
		return header != null && CONTINUATION.matcher(plain).find() ? header : null;
	}

	public static void track(Component content) {
		Tab channel = classify(content.getString());
		if (channel != null) {
			channels.put(content, channel);
			order.addLast(content);
			while (order.size() > REMEMBERED) {
				channels.remove(order.removeFirst());
			}
		}
	}

	public static boolean shows(Component content) {
		if (!viewing || selected.isEmpty()) {
			return true;
		}
		Tab channel = channels.containsKey(content) ? channels.get(content) : channelOf(content.getString());
		return channel != null && selected.contains(channel);
	}

	private static ChatComponent chat() {
		Minecraft client = Minecraft.getInstance();
		//? if >=26.2 {
		return client.gui.hud.getChat();
		//?} else {
		/*return client.gui.getChat();
		*///?}
	}

	public static void open() {
		boolean active = RichPresenceModClient.isOnBrilliantServer();
		if (viewing != active) {
			viewing = active;
			if (!selected.isEmpty()) {
				chat().rescaleChat();
			}
		}
	}

	public static void close() {
		if (viewing) {
			viewing = false;
			if (!selected.isEmpty()) {
				chat().rescaleChat();
			}
		}
	}

	static void toggle(Tab tab) {
		if (tab == Tab.ALL) {
			selected.clear();
		} else if (!selected.remove(tab)) {
			selected.add(tab);
		}
		if (selected.size() == Tab.values().length - 1) {
			selected.clear();
		}
	}

	private static boolean active(Tab tab) {
		return tab == Tab.ALL ? selected.isEmpty() : selected.contains(tab);
	}

	private static List<Chip> chips(ChatScreen screen, Font font) {
		Minecraft client = Minecraft.getInstance();
		double scale = client.options.chatScale().get();
		int top = screen.height - 40 - (int) Math.ceil(ChatComponent.getHeight(client.options.chatHeightFocused().get()) * scale) - HEIGHT - GAP;
		List<Chip> chips = new ArrayList<>();
		int x = 2;
		for (Tab tab : Tab.values()) {
			int w = font.width(tab.label) + 8;
			chips.add(new Chip(tab, x, Math.max(2, top), w, HEIGHT));
			x += w + GAP;
		}
		return chips;
	}

	public static void render(ChatScreen screen, Gfx gfx, int mouseX, int mouseY) {
		if (!viewing) {
			return;
		}
		Font font = Minecraft.getInstance().font;
		for (Chip chip : chips(screen, font)) {
			boolean active = active(chip.tab());
			boolean hovered = chip.contains(mouseX, mouseY);
			gfx.fill(chip.x(), chip.y(), chip.x() + chip.w(), chip.y() + chip.h(), active ? 0xD0505050 : hovered ? 0xA0303030 : 0x80000000);
			if (active) {
				gfx.fill(chip.x(), chip.y() + chip.h() - 1, chip.x() + chip.w(), chip.y() + chip.h(), chip.tab().color);
			}
			gfx.centered(font, chip.tab().label, chip.x(), chip.y(), chip.w(), chip.h(), active ? chip.tab().color : Theme.TEXT_SECONDARY);
			if (hovered) {
				gfx.tooltip(font, List.of(Component.literal(chip.tab() == Tab.ALL ? "顯示所有訊息"
						: (active ? "取消顯示" : "加入顯示") + chip.tab().label + "頻道的訊息，可同時選擇多個頻道")), mouseX, mouseY);
			}
		}
	}

	public static boolean mouseClicked(ChatScreen screen, double mouseX, double mouseY, int button) {
		if (!viewing || button != InputConstants.MOUSE_BUTTON_LEFT) {
			return false;
		}
		for (Chip chip : chips(screen, Minecraft.getInstance().font)) {
			if (chip.contains(mouseX, mouseY)) {
				toggle(chip.tab());
				chat().rescaleChat();
				return true;
			}
		}
		return false;
	}
}
