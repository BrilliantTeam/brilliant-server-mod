package net.brilliantw.richpresence;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;
//? if >=26.1 {
import net.minecraft.client.gui.GuiGraphicsExtractor;
//?} else {
/*import net.minecraft.client.gui.GuiGraphics;
*///?}
import net.minecraft.client.gui.screens.Screen;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

public final class ModSettingsScreen extends Screen {
	private record Rect(int x, int y, int w, int h) {
		boolean contains(double mx, double my) {
			return mx >= x && mx < x + w && my >= y && my < y + h;
		}
	}

	private record Row(String section, String label, String detail, BooleanSupplier value, Consumer<Boolean> set, Supplier<String> choice, Runnable next) {
		Row(String section, String label, String detail, BooleanSupplier value, Consumer<Boolean> set) {
			this(section, label, detail, value, set, null, null);
		}

		static Row choice(String section, String label, String detail, Supplier<String> choice, Runnable next) {
			return new Row(section, label, detail, null, null, choice, next);
		}
	}

	private static String nextPreset(List<Theme.Preset> presets, String current) {
		int index = presets.indexOf(Theme.preset(presets, current));
		return presets.get((index + 1) % presets.size()).id();
	}

	private void editAccent() {
		ModSettings.Data s = ModSettings.get();
		CommandMenuScreen.setScreen(new EditDialog(this, "自訂強調色", List.of(new EditDialog.Field("色碼", "例如 #FF9AC8", Theme.hex(s.customAccent), 7)), null, 0, (values, group) -> {
			Integer color = Theme.parseHex(values.getFirst());
			if (color == null) {
				return "請輸入 6 位數的色碼，例如 #FF9AC8";
			}
			s.customAccent = color & 0xFFFFFF;
			s.accent = "custom";
			ModSettings.save();
			return null;
		}));
	}

	private static int cycle(List<Integer> options, int current) {
		int index = options.indexOf(current);
		return options.get((index + 1) % options.size());
	}

	private long clearArmedAt;
	private long chunkClearArmedAt;
	private KeyMapping listening;
	private record Hit(Rect rect, Runnable action) {}

	private static final int HEADER = 26;
	private static final int FOOTER = 14;
	private static final int ROW = 30;
	private static final int SECTION = 18;
	private static final int TABS_W = 92;
	private static final int TAB = 22;
	private static final List<String> TABS = List.of("一般", "聊天", "遊戲", "介面", "小地圖", "地圖", "快捷鍵");
	private static String tab = "一般";
	private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm").withZone(ZoneId.systemDefault());

	private final Screen parent;
	private final List<Row> rows = new ArrayList<>();
	private final List<Hit> hits = new ArrayList<>();
	private double scroll;
	private String status = "";
	private long statusUntil;

	public ModSettingsScreen(Screen parent) {
		super(Component.literal("模組設定"));
		this.parent = parent;
		ModSettings.Data s = ModSettings.get();
		rows.add(new Row("一般", "Discord 遊玩狀態", "在 Discord 個人檔案顯示所在分流與遊玩時間", () -> s.discordPresence, v -> s.discordPresence = v));
		rows.add(new Row("一般", "新公告通知", "進入伺服器時提示有幾則未讀公告", () -> s.announcements, v -> s.announcements = v));
		rows.add(new Row("一般", "自動檢查更新", "有新版本時通知，並可在遊戲內一鍵更新", () -> s.updateCheck, v -> s.updateCheck = v));
		rows.add(new Row("連線", "斷線自動重連", "被伺服器斷線時自動重新連入；在其他地方登入時不會重連", () -> s.autoReconnect, v -> s.autoReconnect = v));
		rows.add(Row.choice("連線", "失敗幾次後停止", "連續重連失敗達到次數就停止；點擊切換", () -> AutoReconnect.attemptsLabel(s.reconnectAttempts),
				() -> s.reconnectAttempts = cycle(AutoReconnect.ATTEMPTS, s.reconnectAttempts)));
		rows.add(Row.choice("連線", "重連頻率", "斷線後等多久再重連；點擊切換", () -> AutoReconnect.delayLabel(s.reconnectDelay),
				() -> s.reconnectDelay = cycle(AutoReconnect.DELAYS, s.reconnectDelay)));
		rows.add(new Row("連線", "自動續驗證", "正版驗證快過期時在背景自動續期，不必重開遊戲；與輝煌啟動器的登入互相同步", () -> s.autoReauth, v -> s.autoReauth = v));
		rows.add(Row.choice("連線", "微軟帳號", "啟動器沒登入時，點擊用瀏覽器登入一次；登入後輝煌啟動器也會一起登入", Reauth::label, () -> Reauth.startLogin(this)));
		rows.add(new Row("聊天", "頻道分頁", "聊天欄上方依頻道篩選訊息的分頁", () -> s.channelTabs, v -> s.channelTabs = v));
		rows.add(new Row("聊天", "私訊視窗", "用獨立視窗顯示與管理私訊對話", () -> s.privateWindow, v -> s.privateWindow = v));
		rows.add(new Row("聊天", "私訊只顯示在視窗", "私訊不出現在聊天欄；關閉私訊視窗時一律顯示在聊天欄。也可在私訊視窗切換", PrivateMessages::hideInChat, PrivateMessages::setHideInChat));
		rows.add(new Row("聊天", "表情符號選單", "聊天欄右下角的表情符號按鈕", () -> s.emoji, v -> s.emoji = v));
		rows.add(new Row("聊天", "高清看圖", "點擊 Discord 圖片連結時在遊戲內開啟", () -> s.imageLinks, v -> s.imageLinks = v));
		rows.add(new Row("聊天", "指令別名", "使用自訂的指令別名與對應的 TAB 補全", () -> s.aliases, v -> s.aliases = v));
		rows.add(new Row("遊戲", "自動腳本", "執行自動腳本；關閉時會停止所有執行中的腳本", () -> Scripts.data().enabled, v -> {
			Scripts.data().enabled = v;
			if (!v) {
				ScriptEngine.stopAll();
			}
			Scripts.save();
		}));
		rows.add(new Row("遊戲", "腳本懸浮視窗", "遊玩時在畫面上顯示腳本執行狀態", () -> Scripts.data().monitor, v -> {
			Scripts.data().monitor = v;
			Scripts.save();
		}));
		rows.add(new Row("遊戲", "夜視", "不需要藥水也能看清楚暗處；只改變你自己畫面的亮度。可在按鍵設定綁定快速切換鍵", () -> s.nightVision, v -> s.nightVision = v));
		rows.add(new Row("遊戲", "配方書修正", "讓配方書正確辨識伺服器的自訂材料", () -> s.recipeFix, v -> s.recipeFix = v));
		rows.add(new Row("遊戲", "格子編號", "按住 Alt 時在介面格子上顯示編號", () -> s.slotNumbers, v -> s.slotNumbers = v));
		rows.add(new Row("遊戲", "記分板位置", "套用自訂的記分板位置與縮放", () -> s.scoreboardLayout, v -> s.scoreboardLayout = v));
		rows.add(new Row("遊戲", "鐵砧完整花費", "超限附魔超過 40 級時顯示實際花費，取代「太貴了！」", () -> s.anvilCost, v -> s.anvilCost = v));
		rows.add(Row.choice("自由視角", "移動速度", "鏡頭每秒移動幾格；開啟自由視角時也可以用滾輪微調。自由視角的開關鍵在「快捷鍵」分頁設定", () -> Freecam.formatSpeed(s.freecamSpeed), () -> s.freecamSpeed = Freecam.nextSpeed(s.freecamSpeed)));
		rows.add(Row.choice("自由視角", "衝刺倍率", "按住衝刺鍵時速度乘上幾倍", () -> s.freecamBoost + " 倍", () -> s.freecamBoost = Freecam.nextBoost(s.freecamBoost)));
		rows.add(Row.choice("自由視角", "慣性", "放開按鍵後鏡頭滑行停下的程度；越高越像電影運鏡", () -> Freecam.SMOOTH_LABELS[Math.clamp(s.freecamSmooth, 0, Freecam.SMOOTH_LABELS.length - 1)], () -> s.freecamSmooth = (s.freecamSmooth + 1) % Freecam.SMOOTH_LABELS.length));
		rows.add(new Row("自由視角", "水平移動", "前進後退只在水平面移動，跳躍與潛行控制升降；關閉時往視線方向飛", () -> s.freecamFlat, v -> s.freecamFlat = v));
		rows.add(new Row("自由視角", "穿過方塊", "鏡頭可以穿過牆壁與地面；關閉時會被方塊擋住", () -> s.freecamNoclip, v -> s.freecamNoclip = v));
		rows.add(new Row("自由視角", "隱藏介面", "開啟自由視角時自動隱藏 HUD，關閉時恢復", () -> s.freecamHideHud, v -> s.freecamHideHud = v));
		rows.add(new Row("遠處區塊", "保留遠處區塊", "伺服器卸載的區塊會繼續顯示在畫面上，看得比伺服器的視野更遠；遠處畫面停在你最後看到的樣子，不會即時更新", () -> s.keepChunks, v -> s.keepChunks = v));
		rows.add(Row.choice("遠處區塊", "保留距離", "要看多遠；「跟隨顯示距離」會使用原版視訊設定的顯示距離，超過 32 會明顯吃效能", () -> FakeChunks.rangeLabel(s.keepChunksRange), () -> s.keepChunksRange = cycle(FakeChunks.RANGES, s.keepChunksRange)));
		rows.add(new Row("遠處區塊", "永久儲存", "把看過的區塊存到硬碟，下次回到附近時先顯示上次看到的樣子；關閉時走遠或離線就會丟掉", () -> s.keepChunksSave, v -> s.keepChunksSave = v));
		rows.add(Row.choice("遠處區塊", "儲存空間上限", "超過時先刪除最久沒去的區塊", () -> s.keepChunksDiskMb >= 1024 ? s.keepChunksDiskMb / 1024 + " GB" : s.keepChunksDiskMb + " MB", () -> s.keepChunksDiskMb = cycle(FakeChunks.DISK_MB, s.keepChunksDiskMb)));
		rows.add(Row.choice("遠處區塊", "自動清除", "多久沒回去的區塊會被刪除", () -> s.keepChunksDays > 0 ? s.keepChunksDays + " 天沒去" : "不自動清除", () -> s.keepChunksDays = cycle(FakeChunks.DAYS, s.keepChunksDays)));
		rows.add(Row.choice("遠處區塊", "讀取速度", "每半秒從硬碟讀回幾個區塊；越快越早看到全貌，但移動時可能稍微卡頓", () -> s.keepChunksRate + " 個", () -> s.keepChunksRate = cycle(FakeChunks.RATES, s.keepChunksRate)));
		rows.add(Row.choice("遠處區塊", "清除已儲存區塊", "刪除所有存在硬碟的區塊，連點兩下確認", () -> chunkClearArmedAt > 0 && System.currentTimeMillis() - chunkClearArmedAt < 3000 ? "再點一次刪除" : "已用 " + FakeChunks.diskLabel(), () -> {
			if (chunkClearArmedAt > 0 && System.currentTimeMillis() - chunkClearArmedAt < 3000) {
				chunkClearArmedAt = 0;
				FakeChunks.clear();
			} else {
				chunkClearArmedAt = System.currentTimeMillis();
			}
		}));
		rows.add(new Row("自由視角", "滾輪調整速度", "自由視角時滾動滑鼠滾輪調整移動速度，不會切換快捷欄", () -> s.freecamScroll, v -> s.freecamScroll = v));
		rows.add(Row.choice("介面", "強調色", "模組介面的按鈕、選取與標題顏色；點擊切換", () -> Theme.preset(Theme.ACCENTS, s.accent).name() + (s.accent.equals("custom") ? " " + Theme.hex(s.customAccent) : ""),
				() -> s.accent = nextPreset(Theme.ACCENTS, s.accent)));
		rows.add(Row.choice("介面", "自訂強調色", "輸入色碼，例如 #FF9AC8；點擊編輯後會自動切換成自訂", () -> Theme.hex(s.customAccent), this::editAccent));
		rows.add(Row.choice("介面", "背景色調", "模組介面面板與卡片的底色；點擊切換", () -> Theme.preset(Theme.TONES, s.tone).name(),
				() -> s.tone = nextPreset(Theme.TONES, s.tone)));
		rows.add(new Row("小地圖", "懸浮小地圖", "畫面上的小地圖；按 N 切換、按 M 開大地圖、按 B 新增記錄點", () -> s.minimap, v -> s.minimap = v));
		rows.add(Row.choice("小地圖", "大小", "小地圖的邊長；也可以在「小地圖位置」用滾輪調整", () -> s.minimapPixels + " 像素", () -> s.minimapPixels = cycle(Minimap.SIZES, Minimap.nextSize(s.minimapPixels, 0))));
		rows.add(Row.choice("小地圖", "縮放", "每個像素代表幾格；數字越大看得越遠", () -> "1:" + Minimap.formatZoom(Minimap.ZOOMS[s.minimapZoom]), () -> s.minimapZoom = (s.minimapZoom + 1) % Minimap.ZOOMS.length));
		rows.add(Row.choice("小地圖", "透明度", "小地圖本身的不透明度", () -> s.minimapOpacity + "%", () -> s.minimapOpacity = cycle(List.of(100, 90, 80, 70, 60, 50, 40), s.minimapOpacity)));
		rows.add(new Row("小地圖", "隨視角旋轉", "小地圖跟著面向旋轉；關閉時北方固定朝上", () -> s.minimapRotate, v -> s.minimapRotate = v));
		rows.add(new Row("小地圖", "圓形小地圖", "關閉時改為方形", () -> s.minimapCircle, v -> s.minimapCircle = v));
		rows.add(new Row("小地圖", "框線", "小地圖外圍的邊框", () -> s.minimapFrame, v -> s.minimapFrame = v));
		rows.add(new Row("小地圖", "方位", "在小地圖邊緣標示東西南北", () -> s.minimapCompass, v -> s.minimapCompass = v));
		rows.add(new Row("小地圖", "依晝夜調暗", "夜晚時小地圖跟著變暗，和天色一致", () -> s.minimapDayNight, v -> s.minimapDayNight = v));
		rows.add(Row.choice("小地圖", "圖示大小", "生物、玩家與記錄點圖示的大小", () -> Minimap.STEP_LABELS[s.minimapIcons], () -> s.minimapIcons = (s.minimapIcons + 1) % 3));
		rows.add(new Row("小地圖", "顯示生物", "只顯示你看得到的；紅框為敵對生物、綠框為其他生物", () -> s.minimapMobs, v -> s.minimapMobs = v));
		rows.add(new Row("小地圖", "生物圖示", "以生物的頭像顯示種類；關閉時改用色點", () -> s.minimapMobIcons, v -> s.minimapMobIcons = v));
		rows.add(new Row("小地圖", "顯示玩家", "標示附近你看得到的玩家", () -> s.minimapPlayers, v -> s.minimapPlayers = v));
		rows.add(new Row("小地圖", "玩家頭像", "以頭像顯示玩家；關閉時改用白點", () -> s.minimapPlayerHeads, v -> s.minimapPlayerHeads = v));
		rows.add(new Row("小地圖", "顯示記錄點", "在小地圖上標示記錄點，超出範圍時貼在邊緣指示方向", () -> s.minimapWaypoints, v -> s.minimapWaypoints = v));
		rows.add(Row.choice("小地圖文字", "文字大小", "小地圖下方資訊的字體大小", () -> Minimap.STEP_LABELS[s.minimapText], () -> s.minimapText = (s.minimapText + 1) % 3));
		rows.add(new Row("小地圖文字", "文字背景", "在資訊文字後方加上半透明底色，亮處也看得清楚", () -> s.minimapTextBg, v -> s.minimapTextBg = v));
		rows.add(new Row("小地圖文字", "座標", "目前的 X Y Z 座標", () -> s.minimapCoords, v -> s.minimapCoords = v));
		rows.add(new Row("小地圖文字", "區塊座標", "所在區塊與區塊內位置；有公開種子的主世界會標示史萊姆區塊", () -> s.minimapChunk, v -> s.minimapChunk = v));
		rows.add(new Row("小地圖文字", "生態域", "目前所在的生態域名稱", () -> s.minimapBiome, v -> s.minimapBiome = v));
		rows.add(new Row("小地圖文字", "遊戲時間", "第幾天、遊戲內時刻與白天／夜晚", () -> s.minimapTime, v -> s.minimapTime = v));
		rows.add(new Row("小地圖文字", "面向", "目前面向的方位與角度", () -> s.minimapFacing, v -> s.minimapFacing = v));
		rows.add(new Row("小地圖文字", "分流與維度", "目前分流、維度與洞穴層", () -> s.minimapInfo, v -> s.minimapInfo = v));
		rows.add(new Row("地圖", "地圖記憶", "記住跑過的地方，之後回來仍看得到；會佔用硬碟空間。關閉後只保留這次遊玩的地圖", () -> s.mapMemory, v -> s.mapMemory = v));
		rows.add(Row.choice("地圖", "清除地圖記憶", "刪除所有分流記住的地形資料，連點兩下確認", () -> clearArmedAt > 0 && System.currentTimeMillis() - clearArmedAt < 3000 ? "再點一次刪除" : "已用 " + MapStore.diskLabel(), () -> {
			if (clearArmedAt > 0 && System.currentTimeMillis() - clearArmedAt < 3000) {
				clearArmedAt = 0;
				MapStore.clear(null);
			} else {
				clearArmedAt = System.currentTimeMillis();
			}
		}));
		rows.add(new Row("地圖", "地下自動切換洞穴圖", "進入地底時改顯示你所在高度的洞穴地圖", () -> s.mapCave, v -> s.mapCave = v));
		rows.add(new Row("地圖", "顯示領地", "標示伺服器上的領地範圍，大地圖可看名稱與擁有者", () -> s.mapResidences, v -> s.mapResidences = v));
		rows.add(new Row("地圖", "顯示公開傳送點", "標示玩家的公開傳送點（/phome）", () -> s.mapPublicHomes, v -> s.mapPublicHomes = v));
		rows.add(new Row("地圖", "顯示資源再生區塊", "琥珀色為等待再生、紅色為已可再生；在這些區塊建築會被重置", () -> s.mapRegen, v -> s.mapRegen = v));
		rows.add(new Row("地圖", "大地圖區塊格線", "放大後顯示 16 格區塊線，黃線為 512 格區域", () -> s.mapGrid, v -> s.mapGrid = v));
		rows.add(new Row("地圖", "大地圖史萊姆區塊", "依官網公開種子標示主世界的史萊姆區塊", () -> s.mapSlime, v -> s.mapSlime = v));
		for (KeyMapping key : KeyCombos.mine()) {
			rows.add(Row.choice("快捷鍵", Component.translatable(key.getName()).getString(), "點擊後按下想要的按鍵，可同時按住 Ctrl／Alt／Shift；Esc 取消、Backspace 清除",
					() -> listening == key ? "請按下按鍵⋯" : KeyCombos.describe(key), () -> listening = key));
		}
	}

	private Rect panel() {
		int w = Math.min(width - 16, 560);
		return new Rect((width - w) / 2, 8, w, height - 16);
	}

	private Rect content() {
		Rect p = panel();
		return new Rect(p.x() + TABS_W + 10, p.y() + HEADER + 6, p.w() - TABS_W - 20, p.h() - HEADER - FOOTER - 10);
	}

	private static String tabOf(String section) {
		return switch (section) {
			case "連線" -> "一般";
			case "小地圖文字" -> "小地圖";
			case "自由視角", "遠處區塊" -> "遊戲";
			default -> section;
		};
	}

	private void tabs(Gfx gfx, Rect p, int mouseX, int mouseY) {
		int top = p.y() + HEADER;
		int bottom = p.y() + p.h() - FOOTER;
		gfx.fill(p.x(), top, p.x() + TABS_W, bottom, Theme.SIDEBAR);
		gfx.fill(p.x() + TABS_W - 1, top, p.x() + TABS_W, bottom, Theme.HEADER);
		for (int i = 0; i < TABS.size(); i++) {
			String name = TABS.get(i);
			Rect r = new Rect(p.x() + 4, top + 6 + i * (TAB + 2), TABS_W - 9, TAB);
			boolean selected = name.equals(tab);
			boolean hot = r.contains(mouseX, mouseY);
			if (selected || hot) {
				gfx.fill(r.x(), r.y(), r.x() + r.w(), r.y() + r.h(), selected ? Theme.SELECTED : Theme.CARD_HOVER);
			}
			if (selected) {
				gfx.fill(r.x(), r.y(), r.x() + 2, r.y() + r.h(), Theme.ACCENT);
			}
			gfx.text(font, name, r.x() + 9, r.y() + 7, selected ? Theme.ACCENT : Theme.TEXT);
			String count = Long.toString(rows.stream().filter(row -> tabOf(row.section()).equals(name)).count());
			gfx.text(font, count, r.x() + r.w() - font.width(count) - 6, r.y() + 7, Theme.TEXT_DIM);
			hits.add(new Hit(r, () -> {
				tab = name;
				scroll = 0;
			}));
		}
	}

	private void setStatus(String text) {
		status = text;
		statusUntil = System.currentTimeMillis() + 3000;
	}

	//? if >=26.1 {
	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		draw(new Gfx(graphics), mouseX, mouseY);
		super.extractRenderState(graphics, mouseX, mouseY, partialTick);
	}
	//?} else {
	/*@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		draw(new Gfx(graphics), mouseX, mouseY);
		super.render(graphics, mouseX, mouseY, partialTick);
	}
	*///?}

	private void toggleSwitch(Gfx gfx, int x, int y, boolean on, int onColor) {
		gfx.fill(x, y, x + 26, y + 12, on ? onColor : Theme.CHIP);
		int knob = on ? x + 15 : x + 1;
		gfx.fill(knob, y + 1, knob + 10, y + 11, on ? 0xFFFFFFFF : Theme.TEXT_SECONDARY);
	}

	private void draw(Gfx gfx, int mouseX, int mouseY) {
		hits.clear();
		Rect p = panel();
		gfx.fill(p.x(), p.y(), p.x() + p.w(), p.y() + p.h(), Theme.PANEL);
		Theme.watermark(gfx, p.x(), p.y(), p.w(), p.h());
		gfx.fill(p.x(), p.y(), p.x() + p.w(), p.y() + HEADER, Theme.HEADER);
		gfx.fill(p.x(), p.y() + HEADER - 1, p.x() + p.w(), p.y() + HEADER, Theme.ACCENT);
		Theme.drawBack(gfx, font, p.x(), p.y(), mouseX, mouseY);
		gfx.text(font, title.getString(), Theme.titleX(font, p.x()), p.y() + 9, Theme.TEXT);
		String version = "v" + RPConfig.MOD_VERSION;
		gfx.text(font, version, p.x() + p.w() - font.width(version) - 8, p.y() + 9, Theme.TEXT_DIM);

		Rect c = content();
		int height = layout(null, c, 0, -1, -1, false);
		scroll = Math.clamp(scroll, 0, Math.max(0, height - c.h()));
		boolean inside = c.contains(mouseX, mouseY);
		gfx.scissor(c.x(), c.y(), c.x() + c.w(), c.y() + c.h());
		layout(gfx, c, c.y() - (int) scroll, inside ? mouseX : -1, inside ? mouseY : -1, true);
		gfx.endScissor();
		hits.removeIf(hit -> hit.rect().y() + hit.rect().h() <= c.y() || hit.rect().y() >= c.y() + c.h());
		if (height > c.h()) {
			int bar = Math.max(16, c.h() * c.h() / height);
			int barY = c.y() + (int) ((c.h() - bar) * (scroll / (height - c.h())));
			gfx.fill(c.x() + c.w() + 3, barY, c.x() + c.w() + 5, barY + bar, Theme.CHIP_HOVER);
		}
		tabs(gfx, p, mouseX, mouseY);
		gfx.fill(p.x(), p.y() + p.h() - FOOTER, p.x() + p.w(), p.y() + p.h() - FOOTER + 1, Theme.HEADER);
		String footer = System.currentTimeMillis() < statusUntil ? status : "設定會自動儲存 · Esc 返回";
		gfx.text(font, footer, p.x() + 8, p.y() + p.h() - FOOTER + 4, Theme.TEXT_DIM);
	}

	private int layout(Gfx gfx, Rect c, int top, int mouseX, int mouseY, boolean draw) {
		int y = top;
		if (minecraft != null && minecraft.player != null && !RichPresenceModClient.isOnBrilliantServer()) {
			if (draw) {
				gfx.text(font, "目前不在輝煌伺服器，除非開啟危險設定，大部分功能只會在輝煌生效。", c.x(), y + 2, Theme.WARNING);
			}
			y += 16;
		}
		String section = "";
		for (Row row : rows) {
			if (!tabOf(row.section()).equals(tab)) {
				continue;
			}
			if (!row.section().equals(section)) {
				section = row.section();
				if (draw) {
					gfx.text(font, section, c.x(), y + 6, Theme.ACCENT);
				}
				y += SECTION;
			}
			if (draw && row.choice() != null) {
				Rect r = new Rect(c.x(), y, c.w() - 4, ROW - 2);
				gfx.fill(r.x(), r.y(), r.x() + r.w(), r.y() + r.h(), r.contains(mouseX, mouseY) ? Theme.CARD_HOVER : Theme.CARD);
				gfx.text(font, row.label(), r.x() + 8, r.y() + 5, Theme.TEXT);
				String value = row.choice().get() + " ▸";
				int vw = font.width(value) + 12;
				gfx.text(font, font.plainSubstrByWidth(row.detail(), r.w() - vw - 24), r.x() + 8, r.y() + 16, Theme.TEXT_DIM);
				gfx.fill(r.x() + r.w() - vw - 8, r.y() + 7, r.x() + r.w() - 8, r.y() + 21, Theme.CHIP);
				gfx.centered(font, value, r.x() + r.w() - vw - 8, r.y() + 7, vw, 14, Theme.ACCENT);
				hits.add(new Hit(r, () -> {
					row.next().run();
					ModSettings.save();
					setStatus("「" + row.label() + "」改為 " + row.choice().get());
				}));
			} else if (draw) {
				Rect r = new Rect(c.x(), y, c.w() - 4, ROW - 2);
				boolean on = row.value().getAsBoolean();
				gfx.fill(r.x(), r.y(), r.x() + r.w(), r.y() + r.h(), r.contains(mouseX, mouseY) ? Theme.CARD_HOVER : Theme.CARD);
				gfx.text(font, row.label(), r.x() + 8, r.y() + 5, Theme.TEXT);
				gfx.text(font, font.plainSubstrByWidth(row.detail(), r.w() - 50), r.x() + 8, r.y() + 16, Theme.TEXT_DIM);
				toggleSwitch(gfx, r.x() + r.w() - 34, r.y() + 8, on, Theme.SUCCESS);
				hits.add(new Hit(r, () -> {
					row.set().accept(!on);
					ModSettings.save();
					setStatus((on ? "已關閉「" : "已開啟「") + row.label() + "」");
				}));
			}
			y += ROW;
		}
		if (!tab.equals("一般")) {
			return y - top + 6;
		}
		if (draw) {
			gfx.text(font, "危險設定", c.x(), y + 6, Theme.DANGER);
		}
		y += SECTION;
		ModSettings.Data s = ModSettings.get();
		String detail = "讓聊天選單、自動腳本、懸浮視窗、格子編號與記分板位置在其他伺服器與單人世界也能使用。其他伺服器可能禁止這類模組，後果自負。";
		List<net.minecraft.util.FormattedCharSequence> lines = font.split(Component.literal(detail), c.w() - 60);
		int boxH = 22 + lines.size() * 10 + (s.anyServer ? 12 : 0);
		if (draw) {
			Rect r = new Rect(c.x(), y, c.w() - 4, boxH);
			gfx.fill(r.x(), r.y(), r.x() + r.w(), r.y() + r.h(), r.contains(mouseX, mouseY) ? 0x40FF7A7A : 0x24FF7A7A);
			gfx.fill(r.x(), r.y(), r.x() + 2, r.y() + r.h(), Theme.DANGER);
			gfx.text(font, "模組功能不再限於輝煌伺服器", r.x() + 8, r.y() + 6, Theme.DANGER);
			for (int i = 0; i < lines.size(); i++) {
				gfx.text(font, lines.get(i), r.x() + 8, r.y() + 18 + i * 10, Theme.TEXT_SECONDARY);
			}
			if (s.anyServer) {
				gfx.text(font, "已於 " + DATE.format(Instant.ofEpochMilli(s.anyServerAcceptedAt)) + " 同意免責聲明（模組 " + s.anyServerAcceptedVersion + "）",
						r.x() + 8, r.y() + 18 + lines.size() * 10 + 2, Theme.TEXT_DIM);
			}
			toggleSwitch(gfx, r.x() + r.w() - 34, r.y() + 5, s.anyServer, Theme.DANGER);
			hits.add(new Hit(r, () -> {
				if (s.anyServer) {
					s.anyServer = false;
					ModSettings.save();
					setStatus("已關閉危險設定，模組功能恢復為只在輝煌伺服器運作");
				} else {
					CommandMenuScreen.setScreen(new AnyServerConsentScreen(this));
				}
			}));
		}
		y += boxH + 6;
		return y - top;
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (listening == null) {
			return super.keyPressed(event);
		}
		if (event.isEscape()) {
			listening = null;
		} else if (!KeyCombos.isModifier(event.key())) {
			boolean clear = event.key() == InputConstants.KEY_BACKSPACE || event.key() == InputConstants.KEY_DELETE;
			bindListening(clear ? InputConstants.UNKNOWN : InputConstants.getKey(event));
		}
		return true;
	}

	private void bindListening(InputConstants.Key input) {
		KeyMapping key = listening;
		listening = null;
		KeyCombos.bind(key, input, KeyCombos.held());
		setStatus("「" + Component.translatable(key.getName()).getString() + "」改為 " + KeyCombos.describe(key));
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (listening != null) {
			if (event.button() >= 2) {
				bindListening(InputConstants.Type.MOUSE.getOrCreate(event.button()));
				return true;
			}
			listening = null;
		}
		if (super.mouseClicked(event, doubleClick)) {
			return true;
		}
		Rect p = panel();
		if (Theme.backHit(font, p.x(), p.y(), event.x(), event.y())) {
			onClose();
			return true;
		}
		for (Hit hit : List.copyOf(hits)) {
			if (hit.rect().contains(event.x(), event.y())) {
				hit.action().run();
				return true;
			}
		}
		return false;
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		scroll -= scrollY * 24;
		return true;
	}

	@Override
	public void onClose() {
		CommandMenuScreen.setScreen(parent);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
