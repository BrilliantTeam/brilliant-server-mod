package net.brilliantw.richpresence;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public final class ScriptTemplates {
	public record Template(String name, String description, Supplier<Scripts.Group> build) {
		public String code() {
			return Scripts.export(build.get(), List.of(), List.of());
		}
	}

	public static final List<Template> ALL = List.of(
			new Template("掛機自動回覆", "收到私訊時自動回覆，每分鐘最多回一次，不會洗版", () -> group("掛機自動回覆",
					stack(b("ev_private"), body(b("cooldown", "60"), b("wait_random", "1", "3"), b("reply", "我現在掛機中，晚點回你！"))))),
			new Template("喝水提醒", "每 30 分鐘提醒你休息一下，遊戲縮小時也會跳通知", () -> group("喝水提醒",
					stack(b("ev_timer", "1800"), b("sound", "鈴"), b("title", "喝水時間！", "已經玩 30 分鐘了"), b("notify", "喝水時間！", "已經玩 30 分鐘了")))),
			new Template("私訊通知", "收到私訊時播放音效並顯示是誰，遊戲不在前景時跳出系統通知", () -> group("私訊通知",
					stack(b("ev_private"), b("sound", "叮"), b("actionbar", join("收到 ", join(b("s_sender"), " 的私訊"))), whenAway(join("私訊：", b("s_sender")), b("s_message"))))),
			new Template("指定玩家上線通知", "Tab 列表出現指定的玩家時提醒你，把「玩家名稱」改成對方的遊戲名稱", () -> group("指定玩家上線通知",
					stack(b("ev_player_join", "玩家名稱"), b("sound", "升級"), b("local_color", "綠", join(b("s_sender"), " 上線了！")), whenAway("玩家上線", join(b("s_sender"), " 上線了"))))),
			new Template("低血量警報", "生命值掉到 6 以下時跳出大字提醒，遊戲縮小時也會通知", () -> group("低血量警報",
					stack(b("ev_health", "6"), b("sound", "鈴"), b("title", "生命值危險！", join("剩下 ", b("s_health"))), whenAway("生命值危險！", join("剩下 ", b("s_health")))))),
			new Template("背包滿了提醒", "背包塞滿時提醒你回家整理，一分鐘內不重複提醒", () -> group("背包滿了提醒",
					stack(b("ev_full"), body(b("cooldown", "60"), b("sound", "鈴"), b("actionbar", "背包滿了，記得回家整理"), whenAway("背包滿了", "記得回家整理"))))),
			new Template("收購關鍵字通知", "交易頻道出現「收購」時發出音效，並用黃色重點顯示", () -> group("收購關鍵字通知",
					stack(b("ev_channel", "交易", "收購"), b("sound", "叮"), b("local_color", "黃", join(b("s_sender"), join("：", b("s_message"))))))),
			new Template("交易頻道輪播公告", "每 20 分鐘輪流在交易頻道送出清單裡的公告（配合交易頻道冷卻，用到清單）", ScriptTemplates::rotation),
			new Template("上線天數統計", "每天第一次上線時累計天數，重開遊戲也會記得（用到變數存檔）", ScriptTemplates::days),
			new Template("自訂積木範例", "把「大字提醒」做成自己的積木，按腳本鍵 1 呼叫它", ScriptTemplates::procedure),
			new Template("被提到時通知", "聊天中有人提到你的名字時發出音效並跳出大字，10 秒內不重複", () -> group("被提到時通知",
					stack(b("ev_mention"), body(b("cooldown", "10"), b("sound", "鈴"), b("title", "有人提到你！", join(b("s_sender"), join("：", b("s_message")))),
							whenAway(join(b("s_sender"), " 提到你"), b("s_message")))))),
			new Template("進入領地提示", "走進別人的領地時，在畫面下方顯示領地名稱與擁有者", () -> group("進入領地提示",
					stack(b("ev_res_enter", "任何領地"), b("actionbar", join("你在 ", join(b("s_sender"), join(" 的 ", join(b("s_message"), " 領地")))))))),
			new Template("切換分流通知", "切換分流後在聊天欄提示你目前在哪個分流", () -> group("切換分流通知",
					stack(b("ev_server"), b("local_color", "藍", join("已切換到 ", b("s_server")))))),
			new Template("整點報時", "每到整點播放鐘聲並提醒現在幾點", () -> group("整點報時",
					stack(b("ev_timer", "30"), body(b("if", b("op_eq", b("s_time", "分"), "0")),
							body(b("cooldown", "120"), b("note", "鐘", "12"), b("local_color", "黃", join("現在 ", join(b("s_time", "時"), " 點整")))))))),
			new Template("飢餓提醒", "每 30 秒檢查一次，飢餓值低於 7 時提醒你吃東西", () -> group("飢餓提醒",
					stack(b("ev_timer", "30"), body(b("if", b("op_lt", b("s_food"), "7")),
							b("sound", "叮"), b("actionbar", join("肚子餓了（", join(b("s_food"), "／20），記得吃東西"))))))),
			new Template("收購紀錄清單", "把交易頻道的收購訊息記到清單和紀錄檔，按腳本鍵 5 一次列出（用到清單）", ScriptTemplates::buyLog),
			new Template("自動挖掘 60 秒", "按腳本鍵 2 後自動按住左鍵挖掘 60 秒", () -> group("自動挖掘 60 秒",
					stack(b("ev_key", "2"), b("actionbar", "開始自動挖掘 60 秒"), b("act_hold", "攻擊／挖掘", "60"), b("actionbar", "自動挖掘結束")))),
			new Template("自動跑圖 30 秒", "按腳本鍵 3 後自動衝刺前進 30 秒，結束時放開按鍵", () -> group("自動跑圖 30 秒",
					stack(b("ev_key", "3"), b("act_press", "衝刺"), b("act_hold", "前進", "30"), b("act_release_all")))),
			new Template("一鍵開選單點選", "按腳本鍵 4 開啟選單並點「傳送」，把「menu」換成選單指令、「傳送」換成要點的物品名稱", () -> group("一鍵開選單點選",
					stack(b("ev_key", "4"), b("command", "menu"), b("gui_click_name", "傳送", "左鍵")))),
			new Template("自動釣魚", "手上拿著釣竿時，聽到魚上鉤的水花聲就自動收竿再拋竿", () -> group("自動釣魚",
					stack(b("ev_sound", "4", "釣魚浮標濺水"), body(b("if", b("op_contains", b("s_held"), "釣竿")),
							body(b("cooldown", "2"), b("act_tap", "使用／放置"), b("wait_random", "0.5", "0.9"), b("act_tap", "使用／放置")))))),
			new Template("自動吃東西", "飢餓值低於 14 時，自動拿出背包裡的食物吃下去再換回原本的物品；沒有食物時提醒你", () -> group("自動吃東西",
					stack(b("ev_food", "14"), attempt(List.of(b("act_eat", "")), b("actionbar", join("沒辦法自動吃東西：", b("s_error"))))))),
			new Template("簽到提醒", "上線時伺服器提醒還沒簽到，就跳出大字和通知提醒你", () -> group("簽到提醒",
					stack(b("ev_message", "還沒有簽到"), b("wait", "3"), b("sound", "升級"), b("title", "記得簽到！", "今天還沒有簽到喔"), b("notify", "記得簽到", "今天還沒有簽到喔")))),
			new Template("死亡座標紀錄", "死亡時顯示座標、複製到剪貼簿，並寫入「死亡紀錄」檔案", ScriptTemplates::deathLog),
			new Template("附近苦力怕警報", "附近 16 格內出現苦力怕就跳出警告，把「苦力怕」改成其他生物也可以", () -> group("附近苦力怕警報",
					stack(b("ev_entity_near", "16", "苦力怕"), body(b("cooldown", "15"), b("note", "鐘", "18"),
							b("title", "苦力怕在附近！", join("數量：", b("s_entity_count", "16", "苦力怕"))))))),
			new Template("背包滿了自動丟垃圾", "背包塞滿時自動丟掉清單裡的垃圾，可以自己增減「垃圾清單」的內容", ScriptTemplates::trash),
			new Template("作物成熟提醒", "看著農作物時，成熟了就在畫面下方提醒你可以收成", () -> group("作物成熟提醒",
					stack(b("ev_target_block"), body(b("if", b("s_target_grown")), b("note", "鐵琴", "18"), b("actionbar", join(b("s_message"), " 成熟了，可以收成")))))),
			new Template("工具快壞提醒", "手上工具耐久剩不到 20 時提醒你修理或換一把", () -> group("工具快壞提醒",
					stack(b("ev_durability", "20"), b("sound", "鈴"), b("title", "工具快壞了！", join(b("s_held"), join(" 剩下耐久 ", b("s_message"))))))),
			new Template("礦物自動存箱", "打開名稱含「礦物」的箱子時，自動把背包裡的礦物存進去（箱子可以用鐵砧改名）", ScriptTemplates::oreStorage),
			new Template("交易計算機", "按「▶ 執行」後輸入數量和單價，自動算出總價", ScriptTemplates::calculator),
			new Template("天黑提醒", "遊戲內天黑時提醒你小心怪物", () -> group("天黑提醒",
					stack(b("ev_dusk"), b("note", "長笛", "6"), b("title", "天黑了", "小心怪物出沒")))),
			new Template("BossBar 出現通知", "畫面出現 BossBar 時在聊天欄顯示內容，遊戲縮小時跳出通知", () -> group("BossBar 出現通知",
					stack(b("ev_bossbar", ""), b("sound", "叮"), b("local_color", "紫", join("BossBar：", b("s_message"))), whenAway("BossBar 出現", b("s_message"))))),
			new Template("到家提醒", "走進家的範圍時跳出歡迎文字，把座標改成你家的範圍", () -> group("到家提醒",
					stack(b("ev_area_enter", "0", "0", "50", "50"), b("title", "到家了", "歡迎回來")),
					stack(b("ev_area_leave", "0", "0", "50", "50"), b("actionbar", "已經離開家了")))),
			new Template("全服人數報告", "每 10 分鐘在聊天欄顯示全服線上人數（資料來自官方網站）", () -> group("全服人數報告",
					stack(b("ev_timer", "600"), b("local_color", "綠", join("目前全服線上 ", join(b("s_site_online"), " 人")))))));

	private static Scripts.Block whenAway(Object heading, Object body) {
		return body(b("if", b("op_not", b("s_focused"))), b("notify", heading, body));
	}

	private static Scripts.Group buyLog() {
		Scripts.Group group = group("收購紀錄清單",
				stack(b("ev_channel", "交易", "收購"),
						body(b("if", b("op_gt", b("list_length", "收購紀錄"), "49")), b("list_delete", "收購紀錄", "1")),
						b("list_add", join(b("s_sender"), join("：", b("s_message"))), "收購紀錄"),
						b("file_log", "收購紀錄", join(b("s_sender"), join("：", b("s_message"))))),
				stack(b("ev_key", "5"), b("local_color", "黃", join("收購紀錄：", b("list_all", "收購紀錄")))));
		group.lists.add("收購紀錄");
		return group;
	}

	private static Scripts.Group deathLog() {
		Scripts.Group group = group("死亡座標紀錄", stack(b("ev_death"),
				b("var_set", "座標", join(b("s_pos", "X"), join(" ", join(b("s_pos", "Y"), join(" ", b("s_pos", "Z")))))),
				b("local_color", "紅", join("死亡座標：", join(b("s_dimension"), join(" ", b("var_get", "座標"))))),
				b("copy", b("var_get", "座標")),
				b("file_log", "死亡紀錄", join(b("s_dimension"), join(" ", b("var_get", "座標"))))));
		group.variables.add("座標");
		return group;
	}

	private static Scripts.Group oreStorage() {
		Scripts.Group group = group("礦物自動存箱", stack(b("ev_gui_open", "礦物"),
				b("list_from_text", "鑽石,綠寶石,鐵錠,金錠,煤炭,紅石,青金石,石英", ",", "礦物清單"),
				b("wait", "0.3"),
				body(b("for_each", "礦物清單", "礦物"),
						body(b("if", b("op_gt", b("s_count", b("var_get", "礦物")), "0")), b("gui_move_all", b("var_get", "礦物")))),
				b("actionbar", "礦物已存入箱子")));
		group.variables.add("礦物");
		group.lists.add("礦物清單");
		return group;
	}

	private static Scripts.Group trash() {
		Scripts.Group group = group("背包滿了自動丟垃圾", stack(b("ev_full"),
				b("list_from_text", "腐肉,泥土,碎石,閃長岩,安山岩,花崗岩,深板岩", ",", "垃圾清單"),
				body(b("for_each", "垃圾清單", "垃圾"),
						body(b("if", b("s_inv_has", b("var_get", "垃圾"))), b("act_drop_item", b("var_get", "垃圾")))),
				b("actionbar", "背包滿了，已丟掉垃圾")));
		group.variables.add("垃圾");
		group.lists.add("垃圾清單");
		return group;
	}

	private static Scripts.Block attempt(List<Scripts.Block> body, Scripts.Block... handler) {
		Scripts.Block block = body(b("try"), body.toArray(Scripts.Block[]::new));
		block.elseBody = new ArrayList<>(List.of(handler));
		return block;
	}

	private static Scripts.Group calculator() {
		Scripts.Group group = group("交易計算機", stack(b("ev_start"),
				b("ask", "要賣幾個？"),
				b("var_set", "數量", b("s_answer")),
				b("ask", "單價多少？"),
				b("var_set", "單價", b("s_answer")),
				b("local_color", "黃", join("總價：", b("op_format", b("op_mul", b("var_get", "數量"), b("var_get", "單價")), "千分位")))));
		group.variables.add("數量");
		group.variables.add("單價");
		return group;
	}

	private ScriptTemplates() {}

	private static Scripts.Block b(String op, Object... args) {
		Scripts.Block block = new Scripts.Block();
		block.op = op;
		ScriptBlocks.Def def = ScriptBlocks.get(op);
		List<ScriptBlocks.Slot> slots = def.slots();
		for (int i = 0; i < slots.size(); i++) {
			Scripts.Arg arg = new Scripts.Arg(slots.get(i).value());
			if (i < args.length && args[i] instanceof Scripts.Block nested) {
				arg.block = nested;
			} else if (i < args.length) {
				arg.text = String.valueOf(args[i]);
			}
			block.args.add(arg);
		}
		return block;
	}

	private static Scripts.Block join(Object a, Object b) {
		return b("op_join", a, b);
	}

	private static Scripts.Block body(Scripts.Block block, Scripts.Block... inner) {
		block.body = new ArrayList<>(List.of(inner));
		return block;
	}

	private static Scripts.Stack stack(Scripts.Block... blocks) {
		return new Scripts.Stack(10, 10, new ArrayList<>(List.of(blocks)));
	}

	private static Scripts.Group group(String name, Scripts.Stack... stacks) {
		Scripts.Group group = new Scripts.Group(name);
		int y = 10;
		for (Scripts.Stack stack : stacks) {
			stack.y = y;
			y += 30 + Scripts.count(stack.blocks) * 14;
			group.stacks.add(stack);
		}
		return group;
	}

	private static Scripts.Group rotation() {
		Scripts.Block loop = body(b("forever"),
				b("var_change", "第幾則", "1"),
				body(b("if", b("op_gt", b("var_get", "第幾則"), b("list_length", "公告"))), b("var_set", "第幾則", "1")),
				b("say_channel", "交易", b("list_item", "公告", b("var_get", "第幾則"))),
				b("wait", "1210"));
		Scripts.Group group = group("交易頻道輪播公告", stack(b("ev_start"),
				b("list_clear", "公告"),
				b("list_add", "出售鑽石，有需要私訊我", "公告"),
				b("list_add", "收購村民交易所，價格好談", "公告"),
				b("var_set", "第幾則", "0"),
				loop));
		group.variables.add("第幾則");
		group.lists.add("公告");
		return group;
	}

	private static Scripts.Group days() {
		Scripts.Group group = group("上線天數統計", stack(b("ev_join"),
				body(b("if", b("op_not", b("op_eq", b("var_get", "上次上線"), b("s_date")))),
						b("var_change", "上線天數", "1"),
						b("var_set", "上次上線", b("s_date")),
						b("local_color", "綠", join("今天是你第 ", join(b("var_get", "上線天數"), " 天上線！"))))));
		group.variables.add("上線天數");
		group.variables.add("上次上線");
		return group;
	}

	private static Scripts.Group procedure() {
		return group("自訂積木範例",
				stack(b("proc_def", "大字提醒"), b("sound", "升級"), b("title", b("proc_arg", "1"), b("proc_arg", "2"))),
				stack(b("ev_key", "1"), b("proc_call", "大字提醒", "記得回家", "按下腳本鍵 1 觸發")));
	}
}
