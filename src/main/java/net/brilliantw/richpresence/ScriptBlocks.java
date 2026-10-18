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
	public static final List<String> KEYBOARD = List.of("ESC", "Enter", "Tab", "F1", "F2", "F3", "F5", "E");
	public static final List<String> CLICKS = List.of("左鍵", "右鍵", "Shift+左鍵", "丟出");
	public static final List<String> DIRECTIONS = List.of("南", "西", "北", "東");
	public static final List<String> COLORS = List.of("白", "紅", "橙", "黃", "綠", "藍", "紫", "灰");

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
		def("gui_click_slot", c, Shape.STACK, "點擊介面第 % 格 用 %", number("1"), dropdown(CLICKS));
		def("gui_move", c, Shape.STACK, "把介面第 % 格的物品移到第 % 格", number("10"), number("37"));
		def("gui_click_text", c, Shape.STACK, "點擊介面上寫著 % 的按鈕", text("確定"));
		def("gui_click_xy", c, Shape.STACK, "點擊介面座標 x % y %", number("0"), number("0"));
		def("gui_close", c, Shape.STACK, "關閉介面");
		def("s_gui_open", c, Shape.BOOLEAN, "有開啟介面?");
		def("s_gui_title", c, Shape.REPORTER, "目前介面標題");
		def("s_slot_item", c, Shape.REPORTER, "介面第 % 格的物品", number("1"));
		def("s_hover_slot", c, Shape.REPORTER, "滑鼠指著第幾格");

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
		def("s_key", c, Shape.BOOLEAN, "腳本鍵 % 被按住?", dropdown(KEYS));
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
