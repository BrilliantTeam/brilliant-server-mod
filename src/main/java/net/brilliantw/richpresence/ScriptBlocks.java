package net.brilliantw.richpresence;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ScriptBlocks {
	public enum Shape { HAT, STACK, C, C_ELSE, CAP, REPORTER, BOOLEAN }

	public enum Category {
		EVENT("事件", 0xFFFFBF00),
		CONTROL("控制", 0xFFFFAB19),
		CHAT("聊天", 0xFF4C97FF),
		LOOKS("通知", 0xFF9966FF),
		ACTION("操作", 0xFF0FBD8C),
		SENSE("偵測", 0xFF5CB1D6),
		OPERATOR("運算", 0xFF59C059),
		VARIABLE("變數", 0xFFFF8C1A),
		PROC("自訂", 0xFFFF6680);

		public final String label;
		public final int color;

		Category(String label, int color) {
			this.label = label;
			this.color = color;
		}
	}

	public enum SlotKind { TEXT, NUMBER, BOOLEAN, DROPDOWN, VARIABLE, LIST, PROC }

	public record Slot(SlotKind kind, String value, List<String> options) {}

	public record Def(String id, Category category, Shape shape, List<Object> parts, String elseLabel) {
		public List<Slot> slots() {
			return parts.stream().filter(part -> part instanceof Slot).map(part -> (Slot) part).toList();
		}

		public boolean hasBody() {
			return shape == Shape.C || shape == Shape.C_ELSE;
		}

		public boolean isReporter() {
			return shape == Shape.REPORTER || shape == Shape.BOOLEAN;
		}

		public boolean endsStack() {
			return shape == Shape.CAP || id.equals("forever");
		}
	}

	public static final List<String> KEYS = List.of("1", "2", "3", "4", "5");
	public static final List<String> SOUNDS = List.of("叮", "鈴", "經驗", "升級");
	public static final List<String> AXES = List.of("X", "Y", "Z");
	public static final List<String> TIME_PARTS = List.of("時", "分", "秒");
	public static final List<String> CHANNELS = List.of("聊天", "交易", "區域", "公會", "領地");
	public static final List<String> MATH = List.of("絕對值", "平方根", "無條件捨去", "無條件進位");
	public static final List<String> ACTIONS = List.of("攻擊／挖掘", "使用／放置", "跳躍", "蹲下", "衝刺", "前進", "後退", "向左", "向右", "丟棄物品", "副手交換");
	public static final List<String> LETTERS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ".chars().mapToObj(c -> String.valueOf((char) c)).toList();
	public static final List<String> DIGITS = List.of("0", "1", "2", "3", "4", "5", "6", "7", "8", "9");
	public static final List<String> FUNCTION_KEYS = List.of("F1", "F2", "F3", "F4", "F5", "F6", "F7", "F8", "F9", "F10", "F11", "F12");
	public static final List<String> SPECIAL_KEYS = List.of("空白鍵", "Enter", "Tab", "Backspace", "Delete", "Insert", "Home", "End", "PageUp", "PageDown", "上", "下", "左", "右");
	public static final List<String> HOTKEYS = join(LETTERS, DIGITS, FUNCTION_KEYS, SPECIAL_KEYS);
	public static final List<String> KEYBOARD = join(List.of("ESC", "Enter", "Tab"), FUNCTION_KEYS, LETTERS, DIGITS, SPECIAL_KEYS.stream().filter(k -> !k.equals("Enter") && !k.equals("Tab")).toList());
	public static final List<String> MODIFIERS = List.of("Ctrl", "Shift", "Alt", "Ctrl+Shift", "Ctrl+Alt", "Shift+Alt", "Ctrl+Shift+Alt", "不按");
	public static final List<String> HELD_KEYS = join(List.of("Ctrl", "Shift", "Alt"), HOTKEYS);

	@SafeVarargs
	private static List<String> join(List<String>... lists) {
		return java.util.Arrays.stream(lists).flatMap(List::stream).toList();
	}
	public static final List<String> CLICKS = List.of("左鍵", "右鍵", "Shift+左鍵", "丟出", "丟出整組");
	public static final List<String> DIRECTIONS = List.of("南", "西", "北", "東");
	public static final List<String> HEARD = List.of("任何音效", "釣魚浮標濺水", "爆炸", "苦力怕引信", "雷聲", "物品撿起", "經驗球", "升級", "村民", "箱子開啟", "鐘聲", "傳送", "受傷", "附魔", "鐵砧");
	public static final List<String> EXTRACTS = List.of("數字", "整數", "冒號後面", "括號裡", "英文或玩家名");
	public static final List<String> FORMATS = List.of("千分位", "整數", "1 位小數", "2 位小數", "百分比");
	public static final List<String> ORDERS = List.of("由小到大", "由大到小");
	public static final List<String> TIME_FORMATS = List.of("日期和時間", "日期", "時間");
	public static final List<String> CASES = List.of("大寫", "小寫");
	public static final List<String> INSTRUMENTS = List.of("鋼琴", "貝斯", "鐘", "長笛", "吉他", "鐵琴", "風鈴", "牛鈴", "班鳩琴", "電子琴", "大鼓", "小鼓", "敲擊");
	public static final List<String> COLORS = List.of("白", "紅", "橙", "黃", "綠", "藍", "紫", "灰");
	public static final List<String> EQUIPMENT = List.of("頭盔", "胸甲", "護腿", "靴子");

	private static final Map<String, Def> DEFS = new LinkedHashMap<>();

	static {
		Category c = Category.EVENT;
		def("ev_start", c, Shape.HAT, "當按下執行時");
		def("ev_join", c, Shape.HAT, "當進入伺服器時");
		def("ev_server", c, Shape.HAT, "當切換分流時");
		def("ev_message", c, Shape.HAT, "當收到含有 % 的訊息時", text("收購"));
		def("ev_channel", c, Shape.HAT, "當收到來自 % 頻道、含有 % 的訊息時", dropdown(CHANNELS), text(""));
		def("ev_private", c, Shape.HAT, "當收到私訊時");
		def("ev_mention", c, Shape.HAT, "當有人提到我時");
		def("ev_key", c, Shape.HAT, "當按下腳本鍵 % 時", dropdown(KEYS));
		def("ev_timer", c, Shape.HAT, "每隔 % 秒", number("60"));
		def("ev_broadcast", c, Shape.HAT, "當收到廣播 % 時", text("訊息1"));
		def("ev_health", c, Shape.HAT, "當生命值低於 % 時", number("6"));
		def("ev_full", c, Shape.HAT, "當背包滿了時");
		def("ev_player_join", c, Shape.HAT, "當玩家 % 上線時", text("任何人"));
		def("ev_player_leave", c, Shape.HAT, "當玩家 % 下線時", text("任何人"));
		def("ev_var_change", c, Shape.HAT, "當變數 % 改變時", variable());
		def("ev_res_enter", c, Shape.HAT, "當進入領地 % 時", text("任何領地"));
		def("ev_res_leave", c, Shape.HAT, "當離開領地 % 時", text("任何領地"));
		def("ev_title", c, Shape.HAT, "當標題或副標題出現 % 時", text(""));
		def("ev_actionbar", c, Shape.HAT, "當動作列出現 % 時", text(""));
		def("ev_bossbar", c, Shape.HAT, "當 BossBar 出現 % 時", text(""));
		def("ev_sidebar", c, Shape.HAT, "當記分板出現 % 時", text(""));
		def("ev_bossbar_gone", c, Shape.HAT, "當含有 % 的 BossBar 消失時", text(""));
		def("ev_sound", c, Shape.HAT, "當在 % 格內聽到 % 音效時", number("16"), dropdown(HEARD));
		def("ev_daily", c, Shape.HAT, "每天 % 點 % 分時", number("20"), number("0"));
		def("ev_gui_open", c, Shape.HAT, "當開啟標題含有 % 的介面時", text(""));
		def("ev_gui_close", c, Shape.HAT, "當介面關閉時");
		def("ev_hurt", c, Shape.HAT, "當受到傷害時");
		def("ev_death", c, Shape.HAT, "當死亡時");
		def("ev_respawn", c, Shape.HAT, "當重生時");
		def("ev_area_enter", c, Shape.HAT, "當進入範圍 x % z % 到 x % z % 時", number("0"), number("0"), number("100"), number("100"));
		def("ev_area_leave", c, Shape.HAT, "當離開範圍 x % z % 到 x % z % 時", number("0"), number("0"), number("100"), number("100"));
		def("ev_dimension", c, Shape.HAT, "當切換世界時");
		def("ev_held", c, Shape.HAT, "當手上物品改變時");
		def("ev_item_count", c, Shape.HAT, "當背包中 % 的數量改變時", text("鑽石"));
		def("ev_dawn", c, Shape.HAT, "當天亮時");
		def("ev_dusk", c, Shape.HAT, "當天黑時");
		def("ev_key_release", c, Shape.HAT, "當放開腳本鍵 % 時", dropdown(KEYS));
		def("ev_hotkey", c, Shape.HAT, "當按下 % + % 時", dropdown(MODIFIERS), dropdown(HOTKEYS));
		def("ev_announcement", c, Shape.HAT, "當有新公告時");
		def("ev_pickup", c, Shape.HAT, "當撿到 % 時", text(""));
		def("ev_durability", c, Shape.HAT, "當手上物品耐久低於 % 時", number("20"));
		def("ev_food", c, Shape.HAT, "當飢餓值低於 % 時", number("14"));
		def("ev_entity_near", c, Shape.HAT, "當附近 % 格內出現 % 時", number("16"), text("苦力怕"));
		def("ev_target_block", c, Shape.HAT, "當看著的方塊改變時");
		def("ev_level", c, Shape.HAT, "當經驗等級改變時");
		def("ev_error", c, Shape.HAT, "當腳本出錯時");

		c = Category.CONTROL;
		def("wait", c, Shape.STACK, "等待 % 秒", number("1"));
		def("wait_random", c, Shape.STACK, "等待 % 到 % 秒之間", number("1"), number("3"));
		def("repeat", c, Shape.C, "重複 % 次", number("10"));
		def("forever", c, Shape.C, "重複無限次");
		def("if", c, Shape.C, "如果 % 那麼", bool());
		defElse("if_else", c, "如果 % 那麼", "否則", bool());
		def("wait_until", c, Shape.STACK, "等待直到 %", bool());
		def("repeat_until", c, Shape.C, "重複直到 %", bool());
		def("broadcast", c, Shape.STACK, "廣播 %", text("訊息1"));
		def("stop", c, Shape.CAP, "停止這個腳本");
		def("stop_all", c, Shape.CAP, "停止全部腳本");
		def("for_each", c, Shape.C, "對清單 % 的每一項，存到變數 %", list(), variable());
		def("break", c, Shape.STACK, "跳出迴圈");
		def("continue", c, Shape.STACK, "跳過這一次");
		def("wait_timeout", c, Shape.STACK, "等待直到 % ，最多 % 秒", bool(), number("10"));
		def("cooldown", c, Shape.C, "冷卻 % 秒（期間內不重複執行）", number("60"));
		defElse("if_elif", c, "如果 % 那麼…否則如果 %", "否則如果成立時", bool(), bool());
		def("fork", c, Shape.C, "在背景同時執行");
		def("stop_others", c, Shape.STACK, "停止這個分組的其他腳本");
		defElse("try", c, "嘗試執行", "如果失敗");
		def("wait_ticks", c, Shape.STACK, "等待 % 個 tick", number("20"));

		c = Category.CHAT;
		def("say", c, Shape.STACK, "送出訊息 %", text("大家好！"));
		def("command", c, Shape.STACK, "執行指令 / %", text("home"));
		def("msg", c, Shape.STACK, "私訊 % 說 %", text("玩家名稱"), text("你好"));
		def("insert", c, Shape.STACK, "在聊天欄填入 %", text("你好"));
		def("say_channel", c, Shape.STACK, "在 % 頻道說 %", dropdown(CHANNELS), text("大家好！"));
		def("reply", c, Shape.STACK, "回覆私訊 %", text("我現在不在，晚點回你"));
		def("copy", c, Shape.STACK, "複製 % 到剪貼簿", text("/home"));

		c = Category.LOOKS;
		def("local", c, Shape.STACK, "在聊天欄顯示 %", text("提醒！"));
		def("local_color", c, Shape.STACK, "在聊天欄用 % 色顯示 %", dropdown(COLORS), text("提醒！"));
		def("actionbar", c, Shape.STACK, "在畫面下方顯示 %", text("提醒！"));
		def("title", c, Shape.STACK, "在畫面中央顯示 % 小字 %", text("注意！"), text(""));
		def("sound", c, Shape.STACK, "播放音效 %", dropdown(SOUNDS));
		def("log", c, Shape.STACK, "在懸浮視窗記錄 %", text("完成"));
		def("monitor_show", c, Shape.STACK, "顯示懸浮視窗");
		def("monitor_hide", c, Shape.STACK, "隱藏懸浮視窗");
		def("monitor_field", c, Shape.STACK, "在懸浮視窗顯示 % ： %", text("收入"), text("0"));
		def("monitor_bar", c, Shape.STACK, "在懸浮視窗顯示進度條 % %", text("進度"), number("50"));
		def("monitor_clear", c, Shape.STACK, "清除懸浮視窗的自訂欄位");
		def("note", c, Shape.STACK, "播放音符 % 音高 %", dropdown(INSTRUMENTS), number("12"));
		def("notify", c, Shape.STACK, "發送通知 % 內容 %", text("提醒"), text(""));
		def("file_log", c, Shape.STACK, "寫入紀錄檔 % ： %", text("交易紀錄"), text(""));
		def("ask", c, Shape.STACK, "詢問 % 並等待", text("要買幾個？"));
		def("choose", c, Shape.STACK, "顯示選項 % 並等待", text("買,賣,取消"));

		c = Category.SENSE;
		c = Category.ACTION;
		def("act_tap", c, Shape.STACK, "按一下 %", dropdown(ACTIONS));
		def("act_hold", c, Shape.STACK, "按住 % 持續 % 秒", dropdown(ACTIONS), number("1"));
		def("act_press", c, Shape.STACK, "按下 % 不放", dropdown(ACTIONS));
		def("act_release", c, Shape.STACK, "放開 %", dropdown(ACTIONS));
		def("act_release_all", c, Shape.STACK, "放開所有按鍵");
		def("act_hotbar", c, Shape.STACK, "切換到快捷欄第 % 格", number("1"));
		def("act_face", c, Shape.STACK, "面向 % 方", dropdown(DIRECTIONS));
		def("act_turn", c, Shape.STACK, "轉動視角 左右 % 度 上下 % 度", number("90"), number("0"));
		def("s_pressing", c, Shape.BOOLEAN, "正在按著 % ?", dropdown(ACTIONS));
		def("key_press", c, Shape.STACK, "按下鍵盤 %", dropdown(KEYBOARD));
		def("key_combo", c, Shape.STACK, "按下組合鍵 % + %", dropdown(MODIFIERS), dropdown(KEYBOARD));
		def("gui_click_slot", c, Shape.STACK, "點擊介面第 % 格 用 %", number("1"), dropdown(CLICKS));
		def("gui_move", c, Shape.STACK, "把介面第 % 格的物品移到第 % 格", number("10"), number("37"));
		def("gui_click_text", c, Shape.STACK, "點擊介面上寫著 % 的按鈕", text("確定"));
		def("gui_click_xy", c, Shape.STACK, "點擊介面座標 x % y %", number("0"), number("0"));
		def("gui_close", c, Shape.STACK, "關閉介面");
		def("s_gui_open", c, Shape.BOOLEAN, "有開啟介面?");
		def("s_gui_title", c, Shape.REPORTER, "目前介面標題");
		def("s_slot_item", c, Shape.REPORTER, "介面第 % 格的物品", number("1"));
		def("s_hover_slot", c, Shape.REPORTER, "滑鼠指著第幾格");
		def("act_hotbar_find", c, Shape.STACK, "切換到含有 % 的快捷欄格", text("劍"));
		def("gui_click_name", c, Shape.STACK, "點擊介面中名稱含有 % 的物品 用 %", text("確認"), dropdown(CLICKS));
		def("gui_move_all", c, Shape.STACK, "把背包中所有 % Shift 移到另一邊", text("圓石"));
		def("act_face_pos", c, Shape.STACK, "面向座標 x % y % z %", number("0"), number("64"), number("0"));
		def("act_face_player", c, Shape.STACK, "面向最近的玩家");
		def("act_walk_to", c, Shape.STACK, "走向座標 x % z %", number("0"), number("0"));
		def("act_select_item", c, Shape.STACK, "拿出背包中的 %", text("鑽石劍"));
		def("act_drop_item", c, Shape.STACK, "丟掉背包中所有的 %", text("腐肉"));
		def("act_face_block", c, Shape.STACK, "面向附近 % 格內最近的 % 方塊", number("6"), text("箱子"));
		def("act_face_entity", c, Shape.STACK, "面向最近的 % 生物", text("殭屍"));
		def("act_mine", c, Shape.STACK, "挖掉看著的方塊 最多 % 秒", number("10"));
		def("act_eat", c, Shape.STACK, "吃 %", text("麵包"));
		def("act_best_tool", c, Shape.STACK, "換上最適合挖看著方塊的工具");
		def("act_walk_drop", c, Shape.STACK, "走去撿最近的 %", text(""));
		def("act_walk_block", c, Shape.STACK, "走到最近的 % 方塊", text("箱子"));
		def("act_hotbar_put", c, Shape.STACK, "把 % 放到快捷欄第 % 格", text("鑽石劍"), number("1"));
		def("act_offhand", c, Shape.STACK, "把副手換成 %", text("盾牌"));
		def("act_equip", c, Shape.STACK, "穿上 %", text("鞘翅"));
		def("gui_take", c, Shape.STACK, "從介面拿出 % 個 %", number("1"), text("鑽石"));
		def("act_attack_until", c, Shape.STACK, "攻擊看著的生物直到死亡 最多 % 秒", number("10"));

		c = Category.SENSE;
		def("s_message", c, Shape.REPORTER, "訊息內容");
		def("s_sender", c, Shape.REPORTER, "訊息發送者");
		def("s_channel", c, Shape.REPORTER, "訊息頻道");
		def("s_contains", c, Shape.BOOLEAN, "訊息包含 % ?", text("鑽石"));
		def("s_me", c, Shape.REPORTER, "我的名稱");
		def("s_server", c, Shape.REPORTER, "目前分流");
		def("s_pos", c, Shape.REPORTER, "座標 %", dropdown(AXES));
		def("s_health", c, Shape.REPORTER, "生命值");
		def("s_time", c, Shape.REPORTER, "現在的 %", dropdown(TIME_PARTS));
		def("s_weekday", c, Shape.REPORTER, "今天星期幾");
		def("s_dimension", c, Shape.REPORTER, "目前世界");
		def("s_facing", c, Shape.REPORTER, "面向方位");
		def("s_food", c, Shape.REPORTER, "飢餓值");
		def("s_level", c, Shape.REPORTER, "經驗等級");
		def("s_held", c, Shape.REPORTER, "手上物品名稱");
		def("s_count", c, Shape.REPORTER, "背包裡 % 的數量", text("鑽石"));
		def("s_armor", c, Shape.REPORTER, "護甲值");
		def("s_sneak", c, Shape.BOOLEAN, "正在蹲下?");
		def("s_online", c, Shape.REPORTER, "線上玩家數");
		def("s_is_online", c, Shape.BOOLEAN, "玩家 % 在線上?", text("玩家名稱"));
		def("s_ping", c, Shape.REPORTER, "我的延遲");
		def("s_date", c, Shape.REPORTER, "今天日期");
		def("s_title", c, Shape.REPORTER, "畫面標題");
		def("s_subtitle", c, Shape.REPORTER, "畫面副標題");
		def("s_actionbar", c, Shape.REPORTER, "動作列文字");
		def("s_bossbar", c, Shape.REPORTER, "BossBar 第 % 條的文字", number("1"));
		def("s_bossbar_progress", c, Shape.REPORTER, "BossBar 第 % 條的進度", number("1"));
		def("s_bossbar_count", c, Shape.REPORTER, "BossBar 數量");
		def("s_sidebar_title", c, Shape.REPORTER, "記分板標題");
		def("s_sidebar_line", c, Shape.REPORTER, "記分板第 % 行", number("1"));
		def("s_sidebar_find", c, Shape.REPORTER, "記分板含有 % 的那一行", text("在線"));
		def("s_tab_header", c, Shape.REPORTER, "Tab 列表上方文字");
		def("s_tab_footer", c, Shape.REPORTER, "Tab 列表下方文字");
		def("s_gamemode", c, Shape.REPORTER, "遊戲模式");
		def("s_on_ground", c, Shape.BOOLEAN, "站在地上?");
		def("s_in_water", c, Shape.BOOLEAN, "在水裡?");
		def("s_on_fire", c, Shape.BOOLEAN, "著火了?");
		def("s_riding", c, Shape.BOOLEAN, "正在騎乘?");
		def("s_effect", c, Shape.BOOLEAN, "有 % 效果?", text("速度"));
		def("s_block_below", c, Shape.REPORTER, "腳下的方塊");
		def("s_target_block", c, Shape.REPORTER, "看著的方塊");
		def("s_target_entity", c, Shape.REPORTER, "看著的生物");
		def("s_nearby_players", c, Shape.REPORTER, "附近 % 格內的玩家數", number("16"));
		def("s_nearest_player", c, Shape.REPORTER, "最近的玩家");
		def("s_biome", c, Shape.REPORTER, "所在的生態域");
		def("s_weather", c, Shape.REPORTER, "天氣");
		def("s_game_hour", c, Shape.REPORTER, "遊戲內時間（幾點）");
		def("s_is_day", c, Shape.BOOLEAN, "現在是白天?");
		def("s_durability", c, Shape.REPORTER, "手上物品剩餘耐久");
		def("s_held_count", c, Shape.REPORTER, "手上物品數量");
		def("s_free_slots", c, Shape.REPORTER, "背包空格數");
		def("s_hotbar_find", c, Shape.REPORTER, "快捷欄中含有 % 的是第幾格", text("劍"));
		def("s_gui_find", c, Shape.REPORTER, "介面中名稱含有 % 的是第幾格", text("鑽石"));
		def("s_entity_count", c, Shape.REPORTER, "附近 % 格內 % 的數量", number("16"), text("殭屍"));
		def("s_nearest_entity", c, Shape.REPORTER, "最近的生物");
		def("s_nearest_entity_distance", c, Shape.REPORTER, "到最近生物的距離");
		def("s_distance_to", c, Shape.REPORTER, "到座標 x % y % z % 的距離", number("0"), number("64"), number("0"));
		def("s_yaw", c, Shape.REPORTER, "視角水平角度");
		def("s_pitch", c, Shape.REPORTER, "視角垂直角度");
		def("s_sprinting", c, Shape.BOOLEAN, "正在衝刺?");
		def("s_flying", c, Shape.BOOLEAN, "正在飛行?");
		def("s_gliding", c, Shape.BOOLEAN, "正在滑翔?");
		def("s_using", c, Shape.BOOLEAN, "正在使用物品?");
		def("s_xp_progress", c, Shape.REPORTER, "經驗進度");
		def("s_air", c, Shape.REPORTER, "氧氣值");
		def("s_attack_cooldown", c, Shape.REPORTER, "攻擊冷卻");
		def("s_block_at", c, Shape.REPORTER, "座標 x % y % z % 的方塊", number("0"), number("64"), number("0"));
		def("s_online_list", c, Shape.REPORTER, "線上玩家名單");
		def("s_focused", c, Shape.BOOLEAN, "遊戲視窗在前景?");
		def("s_fps", c, Shape.REPORTER, "FPS");
		def("s_timed_out", c, Shape.BOOLEAN, "等待逾時了?");
		def("s_answer", c, Shape.REPORTER, "回答");
		def("s_choice", c, Shape.REPORTER, "選擇的項目");
		def("s_site_online", c, Shape.REPORTER, "網站：全服線上人數");
		def("s_site_server", c, Shape.REPORTER, "網站：% 線上人數", text("第一分流"));
		def("s_announcement", c, Shape.REPORTER, "最新公告標題");
		def("s_timestamp", c, Shape.REPORTER, "現在時間戳");
		def("s_key", c, Shape.BOOLEAN, "腳本鍵 % 被按住?", dropdown(KEYS));
		def("s_hotkey", c, Shape.BOOLEAN, "鍵盤 % 被按住?", dropdown(HELD_KEYS));
		def("s_held_has", c, Shape.BOOLEAN, "手上拿著 % ?", text("劍"));
		def("s_inv_has", c, Shape.BOOLEAN, "背包有 % ?", text("鑽石"));
		def("s_held_id", c, Shape.REPORTER, "手上物品 ID");
		def("s_held_lore", c, Shape.REPORTER, "手上物品的說明文字");
		def("s_durability_pct", c, Shape.REPORTER, "手上物品耐久百分比");
		def("s_offhand", c, Shape.REPORTER, "副手物品名稱");
		def("s_hotbar_item", c, Shape.REPORTER, "快捷欄第 % 格的物品", number("1"));
		def("s_equipment", c, Shape.REPORTER, "身上的 %", dropdown(EQUIPMENT));
		def("s_slot_count", c, Shape.REPORTER, "介面第 % 格的物品數量", number("1"));
		def("s_slot_lore", c, Shape.REPORTER, "介面第 % 格的說明文字", number("1"));
		def("s_gui_count", c, Shape.REPORTER, "介面中（不含背包）% 的總數量", text("鑽石"));
		def("s_block_rel", c, Shape.REPORTER, "相對我 x % y % z % 的方塊", number("0"), number("-1"), number("0"));
		def("s_block_near", c, Shape.BOOLEAN, "附近 % 格內看得到 % 方塊?", number("6"), text("鑽石礦"));
		def("s_block_count", c, Shape.REPORTER, "附近 % 格內看得到的 % 方塊數", number("6"), text("原木"));
		def("s_block_nearest", c, Shape.REPORTER, "附近 % 格內最近的 % 方塊座標 %", number("6"), text("箱子"), dropdown(AXES));
		def("s_target_pos", c, Shape.REPORTER, "看著的方塊座標 %", dropdown(AXES));
		def("s_target_grown", c, Shape.BOOLEAN, "看著的作物成熟了?");
		def("s_drop_count", c, Shape.REPORTER, "附近 % 格內地上 % 的數量", number("16"), text(""));
		def("s_nearest_drop", c, Shape.REPORTER, "最近的掉落物");
		def("s_pickup_count", c, Shape.REPORTER, "撿到的數量");
		def("s_error", c, Shape.REPORTER, "錯誤訊息");
		def("s_enchanted", c, Shape.BOOLEAN, "手上物品有 % 附魔?", text("效率"));
		def("s_enchant_level", c, Shape.REPORTER, "手上物品 % 附魔等級", text("效率"));
		def("s_cooldown", c, Shape.BOOLEAN, "% 正在冷卻中?", text("終界珍珠"));
		def("s_inv_item", c, Shape.REPORTER, "背包第 % 格的物品", number("1"));
		def("s_armor_durability", c, Shape.REPORTER, "盔甲最低耐久百分比");
		def("s_equip_durability", c, Shape.REPORTER, "身上的 % 耐久百分比", dropdown(EQUIPMENT));
		def("s_sign_text", c, Shape.REPORTER, "看著的告示牌文字");
		def("s_target_light", c, Shape.REPORTER, "看著的位置亮度（不含天空光）");
		def("s_target_distance", c, Shape.REPORTER, "看著的方塊距離");
		def("s_entity_pos", c, Shape.REPORTER, "最近的 % 生物座標 %", text("殭屍"), dropdown(AXES));
		def("s_target_health", c, Shape.REPORTER, "看著的生物血量");
		def("s_speed", c, Shape.REPORTER, "移動速度");
		def("s_falling", c, Shape.BOOLEAN, "正在掉落?");
		def("s_in_lava", c, Shape.BOOLEAN, "在岩漿裡?");
		def("s_moon", c, Shape.REPORTER, "月相");
		def("s_can_sleep", c, Shape.BOOLEAN, "現在可以睡覺?");
		def("s_score", c, Shape.REPORTER, "記分板 % 的分數", text("金錢"));
		def("s_timer", c, Shape.REPORTER, "計時器");
		def("timer_reset", c, Shape.STACK, "重設計時器");

		c = Category.OPERATOR;
		def("op_add", c, Shape.REPORTER, "% + %", number(""), number(""));
		def("op_sub", c, Shape.REPORTER, "% - %", number(""), number(""));
		def("op_mul", c, Shape.REPORTER, "% × %", number(""), number(""));
		def("op_div", c, Shape.REPORTER, "% ÷ %", number(""), number(""));
		def("op_random", c, Shape.REPORTER, "在 % 到 % 間隨機選一個數", number("1"), number("10"));
		def("op_gt", c, Shape.BOOLEAN, "% > %", text(""), text("50"));
		def("op_lt", c, Shape.BOOLEAN, "% < %", text(""), text("50"));
		def("op_eq", c, Shape.BOOLEAN, "% = %", text(""), text("50"));
		def("op_neq", c, Shape.BOOLEAN, "% ≠ %", text(""), text("50"));
		def("op_gte", c, Shape.BOOLEAN, "% ≥ %", text(""), text("50"));
		def("op_lte", c, Shape.BOOLEAN, "% ≤ %", text(""), text("50"));
		def("op_between", c, Shape.BOOLEAN, "% 介於 % 和 % 之間?", text(""), number("1"), number("10"));
		def("op_starts", c, Shape.BOOLEAN, "% 開頭是 % ?", text("蘋果汁"), text("蘋果"));
		def("op_ends", c, Shape.BOOLEAN, "% 結尾是 % ?", text("蘋果汁"), text("汁"));
		def("op_is_number", c, Shape.BOOLEAN, "% 是數字?", text("123"));
		def("op_extract", c, Shape.REPORTER, "從 % 擷取 % 的第 % 個", text("收購 鑽石 x64 價格 100"), dropdown(EXTRACTS), number("1"));
		def("op_regex", c, Shape.REPORTER, "用規則 % 從 % 擷取第 % 個", text("x(\\d+)"), text("收購 鑽石 x64"), number("1"));
		def("op_format", c, Shape.REPORTER, "把 % 格式化為 %", number("12345.678"), dropdown(FORMATS));
		def("op_max", c, Shape.REPORTER, "% 和 % 較大的", number(""), number(""));
		def("op_min", c, Shape.REPORTER, "% 和 % 較小的", number(""), number(""));
		def("op_pow", c, Shape.REPORTER, "% 的 % 次方", number("2"), number("3"));
		def("op_case", c, Shape.REPORTER, "把 % 轉成 %", text("Hello"), dropdown(CASES));
		def("op_trim", c, Shape.REPORTER, "% 去掉前後空白", text(" 蘋果 "));
		def("op_elapsed", c, Shape.REPORTER, "從時間戳 % 到現在經過幾秒", number(""));
		def("op_time_format", c, Shape.REPORTER, "把時間戳 % 顯示為 %", number(""), dropdown(TIME_FORMATS));
		def("op_and", c, Shape.BOOLEAN, "% 且 %", bool(), bool());
		def("op_or", c, Shape.BOOLEAN, "% 或 %", bool(), bool());
		def("op_not", c, Shape.BOOLEAN, "% 不成立", bool());
		def("op_join", c, Shape.REPORTER, "字串組合 % %", text("蘋果"), text("香蕉"));
		def("op_contains", c, Shape.BOOLEAN, "% 包含 % ?", text("蘋果"), text("果"));
		def("op_length", c, Shape.REPORTER, "% 的長度", text("蘋果"));
		def("op_mod", c, Shape.REPORTER, "% 除以 % 的餘數", number(""), number(""));
		def("op_round", c, Shape.REPORTER, "四捨五入 %", number(""));
		def("op_math", c, Shape.REPORTER, "% 的 %", dropdown(MATH), number(""));
		def("op_letter", c, Shape.REPORTER, "% 的第 % 個字", text("蘋果"), number("1"));
		def("op_replace", c, Shape.REPORTER, "把 % 中的 % 換成 %", text("我愛蘋果"), text("蘋果"), text("香蕉"));
		def("op_index", c, Shape.REPORTER, "% 在 % 中的位置", text("果"), text("蘋果"));
		def("op_split", c, Shape.REPORTER, "% 用 % 分開的第 % 項", text("蘋果,香蕉"), text(","), number("1"));
		def("op_pick", c, Shape.REPORTER, "從 % 隨機選一個", text("蘋果,香蕉,橘子"));
		def("op_if", c, Shape.REPORTER, "如果 % 就是 % 否則 %", bool(), text("是"), text("否"));
		def("op_chance", c, Shape.BOOLEAN, "有 % ％ 的機率成立?", number("50"));
		def("op_template", c, Shape.REPORTER, "文字範本 %", text("{名稱} 收購 {數量} 個"));

		c = Category.VARIABLE;
		def("var_get", c, Shape.REPORTER, "%", variable());
		def("var_set", c, Shape.STACK, "變數 % 設為 %", variable(), text("0"));
		def("var_change", c, Shape.STACK, "變數 % 改變 %", variable(), number("1"));
		def("list_add", c, Shape.STACK, "把 % 加入清單 %", text("東西"), list());
		def("list_delete", c, Shape.STACK, "刪除清單 % 的第 % 項", list(), number("1"));
		def("list_replace", c, Shape.STACK, "把清單 % 的第 % 項換成 %", list(), number("1"), text("東西"));
		def("list_clear", c, Shape.STACK, "清空清單 %", list());
		def("list_item", c, Shape.REPORTER, "清單 % 的第 % 項", list(), number("1"));
		def("list_length", c, Shape.REPORTER, "清單 % 的項目數", list());
		def("list_contains", c, Shape.BOOLEAN, "清單 % 包含 % ?", list(), text("東西"));
		def("list_all", c, Shape.REPORTER, "清單 % 的全部內容", list());
		def("list_insert", c, Shape.STACK, "在清單 % 的第 % 項插入 %", list(), number("1"), text("東西"));
		def("list_index", c, Shape.REPORTER, "% 在清單 % 的第幾項", text("東西"), list());
		def("list_sort", c, Shape.STACK, "把清單 % 排序 %", list(), dropdown(ORDERS));
		def("list_shuffle", c, Shape.STACK, "打亂清單 %", list());
		def("list_random", c, Shape.REPORTER, "清單 % 隨機一項", list());
		def("list_from_text", c, Shape.STACK, "把 % 用 % 分開存到清單 %", text("a,b,c"), text(","), list());
		def("map_set", c, Shape.STACK, "在清單 % 把 % 設為 %", list(), text("鑽石"), text("100"));
		def("map_get", c, Shape.REPORTER, "清單 % 中 % 的值", list(), text("鑽石"));
		def("map_remove", c, Shape.STACK, "從清單 % 刪除 % 這個鍵", list(), text("鑽石"));
		def("map_has", c, Shape.BOOLEAN, "清單 % 有 % 這個鍵?", list(), text("鑽石"));
		def("list_sum", c, Shape.REPORTER, "清單 % 的總和", list());
		def("list_max", c, Shape.REPORTER, "清單 % 的最大值", list());
		def("list_min", c, Shape.REPORTER, "清單 % 的最小值", list());
		def("list_join", c, Shape.REPORTER, "把清單 % 用 % 連成文字", list(), text("、"));
		def("list_from_inventory", c, Shape.STACK, "把背包物品存到清單 %", list());

		c = Category.PROC;
		def("proc_def", c, Shape.HAT, "定義積木 %", text("打招呼"));
		def("proc_call", c, Shape.STACK, "執行積木 % 帶入 % %", proc(), text(""), text(""));
		def("proc_arg", c, Shape.REPORTER, "帶入的第 % 個值", dropdown(List.of("1", "2")));
	}

	private ScriptBlocks() {}

	private static Slot text(String value) {
		return new Slot(SlotKind.TEXT, value, List.of());
	}

	private static Slot number(String value) {
		return new Slot(SlotKind.NUMBER, value, List.of());
	}

	private static Slot bool() {
		return new Slot(SlotKind.BOOLEAN, "", List.of());
	}

	private static Slot dropdown(List<String> options) {
		return new Slot(SlotKind.DROPDOWN, options.getFirst(), options);
	}

	private static Slot variable() {
		return new Slot(SlotKind.VARIABLE, "", List.of());
	}

	private static Slot list() {
		return new Slot(SlotKind.LIST, "", List.of());
	}

	private static Slot proc() {
		return new Slot(SlotKind.PROC, "", List.of());
	}

	private static List<Object> parts(String template, Slot... slots) {
		List<Object> parts = new ArrayList<>();
		String[] pieces = template.split("%", -1);
		if (pieces.length - 1 != slots.length) {
			throw new IllegalArgumentException("slot count mismatch: " + template);
		}
		for (int i = 0; i < pieces.length; i++) {
			if (!pieces[i].isBlank()) {
				parts.add(pieces[i].strip());
			}
			if (i < slots.length) {
				parts.add(slots[i]);
			}
		}
		return List.copyOf(parts);
	}

	private static void def(String id, Category category, Shape shape, String template, Slot... slots) {
		DEFS.put(id, new Def(id, category, shape, parts(template, slots), null));
	}

	private static void defElse(String id, Category category, String template, String elseLabel, Slot... slots) {
		DEFS.put(id, new Def(id, category, Shape.C_ELSE, parts(template, slots), elseLabel));
	}

	public static Def get(String id) {
		return id == null ? null : DEFS.get(id);
	}

	public static List<Def> of(Category category) {
		return DEFS.values().stream().filter(def -> def.category() == category).toList();
	}

	public static List<Def> all() {
		return List.copyOf(DEFS.values());
	}
}
