package net.brilliantw.richpresence;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class ScriptLint {
	public static final int TRADE_COOLDOWN = 1200;
	private static final Set<String> PACED = Set.of("act_tap", "key_press", "key_combo", "gui_click_slot", "gui_move", "gui_click_text", "gui_click_xy", "act_face", "act_turn", "act_select_item", "act_drop_item", "act_mine", "act_face_block", "act_face_entity",
			"act_eat", "act_best_tool", "act_walk_drop", "act_walk_block", "act_hotbar_put", "act_offhand", "act_equip", "gui_take", "act_attack_until");
	private static final Set<String> SENDS = Set.of("say", "command", "msg", "say_channel", "reply");
	private static final Set<String> LOOPS = Set.of("forever", "repeat", "repeat_until", "for_each");
	private static final Set<String> MESSAGE_EVENTS = Set.of("ev_message", "ev_channel", "ev_private", "ev_mention", "ev_player_join", "ev_player_leave", "ev_res_enter", "ev_res_leave",
			"ev_title", "ev_actionbar", "ev_bossbar", "ev_sidebar", "ev_bossbar_gone", "ev_sound", "ev_gui_open", "ev_gui_close", "ev_hurt",
			"ev_dimension", "ev_held", "ev_item_count", "ev_announcement", "ev_health", "ev_var_change",
			"ev_pickup", "ev_durability", "ev_food", "ev_entity_near", "ev_target_block", "ev_level", "ev_error");
	private static final Set<String> MESSAGE_READERS = Set.of("s_message", "s_sender", "s_contains", "s_channel");

	private ScriptLint() {}

	private record Context(String hat, String keyword, double loopWait, boolean waits) {
		boolean inLoop() {
			return !Double.isNaN(loopWait);
		}
	}

	private static Scripts.Group scope;

	public static Map<Scripts.Block, String> check(List<Scripts.Stack> stacks) {
		return check(stacks, null);
	}

	public static Map<Scripts.Block, String> check(List<Scripts.Stack> stacks, Scripts.Group group) {
		scope = group;
		try {
			return run(stacks);
		} finally {
			scope = null;
		}
	}

	private static Map<Scripts.Block, String> run(List<Scripts.Stack> stacks) {
		Map<Scripts.Block, String> issues = new IdentityHashMap<>();
		for (Scripts.Stack stack : stacks) {
			if (stack.blocks.isEmpty()) {
				continue;
			}
			Scripts.Block first = stack.blocks.getFirst();
			ScriptBlocks.Def def = first.def();
			if (def.isReporter()) {
				continue;
			}
			String hat = def.shape() == ScriptBlocks.Shape.HAT ? first.op : null;
			if (hat == null) {
				issues.put(first, "最上面沒有「事件」積木，這組積木只有在你點它時才會執行");
			} else {
				checkHat(first, stacks, issues);
			}
			String keyword = hat != null && Set.of("ev_message", "ev_broadcast", "ev_var_change", "ev_title", "ev_actionbar").contains(hat) ? first.text(0).strip().toLowerCase(Locale.ROOT)
					: "ev_channel".equals(hat) ? first.text(1).strip().toLowerCase(Locale.ROOT) : "";
			walk(stack.blocks, new Context(hat, keyword, Double.NaN, waits(stack.blocks) > 0), issues);
			if ("ev_var_change".equals(hat)) {
				flagSelfChange(stack.blocks, first.text(0), issues);
			}
		}
		for (Scripts.Stack stack : stacks) {
			checkCalls(stack.blocks, stacks, issues);
		}
		return issues;
	}

	private static void flagSelfChange(List<Scripts.Block> blocks, String name, Map<Scripts.Block, String> issues) {
		for (Scripts.Block block : blocks) {
			if ((block.op.equals("var_set") || block.op.equals("var_change")) && block.text(0).equals(name) && !name.isEmpty()) {
				issues.putIfAbsent(block, "改變的正是觸發這個腳本的變數，會不停重複觸發自己");
			}
			flagSelfChange(block.body, name, issues);
			flagSelfChange(block.elseBody, name, issues);
		}
	}

	private static boolean defined(List<Scripts.Stack> stacks, String name) {
		for (List<Scripts.Stack> source : List.of(stacks, Scripts.activeStacks())) {
			for (Scripts.Stack stack : source) {
				if (!stack.blocks.isEmpty() && stack.blocks.getFirst().op.equals("proc_def") && stack.blocks.getFirst().text(0).strip().equals(name)) {
					return true;
				}
			}
		}
		return false;
	}

	private static void checkCalls(List<Scripts.Block> blocks, List<Scripts.Stack> stacks, Map<Scripts.Block, String> issues) {
		for (Scripts.Block block : blocks) {
			if (block.op.equals("proc_call") && !block.text(0).isBlank() && !defined(stacks, block.text(0).strip())) {
				issues.putIfAbsent(block, "找不到「" + block.text(0).strip() + "」這個自訂積木，可能已經改名或刪除");
			}
			checkCalls(block.body, stacks, issues);
			checkCalls(block.elseBody, stacks, issues);
		}
	}

	private static void checkHat(Scripts.Block hat, List<Scripts.Stack> stacks, Map<Scripts.Block, String> issues) {
		switch (hat.op) {
			case "ev_timer" -> {
				if (ScriptEngine.number(hat.text(0)) < ScriptEngine.MIN_TIMER_SECONDS) {
					issues.put(hat, "最短每 " + ScriptEngine.MIN_TIMER_SECONDS + " 秒觸發一次，小於 " + ScriptEngine.MIN_TIMER_SECONDS + " 秒會以 " + ScriptEngine.MIN_TIMER_SECONDS + " 秒計算");
				}
			}
			case "ev_message" -> {
				if (hat.text(0).isBlank()) {
					issues.put(hat, "關鍵字是空的，這個事件永遠不會觸發");
				}
			}
			case "ev_daily" -> {
				double hour = ScriptEngine.number(hat.text(0));
				double minute = ScriptEngine.number(hat.text(1));
				if (hour < 0 || hour > 23 || minute < 0 || minute > 59 || hour != Math.floor(hour) || minute != Math.floor(minute)) {
					issues.put(hat, "時間要是 0～23 點、0～59 分的整數，否則永遠不會觸發");
				}
			}
			case "ev_health" -> {
				double threshold = ScriptEngine.number(hat.text(0));
				if (threshold <= 0) {
					issues.put(hat, "生命值不會低於 0，這個事件永遠不會觸發");
				} else if (threshold > 20) {
					issues.put(hat, "一般生命值最多 20，設定太高會在每次受傷時觸發");
				}
			}
			case "ev_player_join", "ev_player_leave" -> {
				String name = hat.text(0).strip();
				if (!name.isEmpty() && !name.equals("任何人") && !name.matches("[A-Za-z0-9_.]{1,20}")) {
					issues.put(hat, "「" + name + "」不是有效的玩家名稱；留空或填「任何人」代表所有玩家");
				}
			}
			case "proc_def" -> {
				String name = hat.text(0).strip();
				if (name.isEmpty()) {
					issues.put(hat, "請幫這個自訂積木取一個名稱");
				} else if (stacks.stream().filter(stack -> !stack.blocks.isEmpty() && stack.blocks.getFirst().op.equals("proc_def") && stack.blocks.getFirst().text(0).strip().equals(name)).count() > 1) {
					issues.put(hat, "有兩個名稱都是「" + name + "」的自訂積木，只會使用其中一個");
				}
			}
			case "ev_broadcast" -> {
				String name = hat.text(0).strip();
				if (!broadcasts(stacks, name) && !broadcasts(Scripts.activeStacks(), name)) {
					issues.put(hat, "沒有任何啟用中的「廣播 " + name + "」積木，這個事件不會被觸發");
				}
			}
			default -> {}
		}
	}

	private static boolean broadcasts(List<Scripts.Stack> stacks, String name) {
		for (Scripts.Stack stack : stacks) {
			if (broadcastsIn(stack.blocks, name)) {
				return true;
			}
		}
		return false;
	}

	private static boolean broadcastsIn(List<Scripts.Block> blocks, String name) {
		for (Scripts.Block block : blocks) {
			if (block.op.equals("broadcast") && (block.args.getFirst().block != null || block.text(0).strip().equalsIgnoreCase(name))) {
				return true;
			}
			if (broadcastsIn(block.body, name) || broadcastsIn(block.elseBody, name)) {
				return true;
			}
		}
		return false;
	}

	private static double waits(List<Scripts.Block> blocks) {
		double total = 0;
		for (Scripts.Block block : blocks) {
			if (block.op.equals("wait_ticks")) {
				if (block.args.getFirst().block != null) {
					return Double.POSITIVE_INFINITY;
				}
				total += ScriptEngine.number(block.text(0)) / 20;
			} else if (block.op.equals("wait")) {
				if (block.args.getFirst().block != null) {
					return Double.POSITIVE_INFINITY;
				}
				total += ScriptEngine.number(block.text(0));
			} else if (block.op.equals("wait_random")) {
				if (block.args.get(0).block != null || block.args.get(1).block != null) {
					return Double.POSITIVE_INFINITY;
				}
				total += Math.min(ScriptEngine.number(block.text(0)), ScriptEngine.number(block.text(1)));
			} else if (PACED.contains(block.op)) {
				total += ScriptEngine.INPUT_GAP_MS / 1000.0;
			} else if (block.op.equals("act_hold")) {
				total += block.args.get(1).block != null ? 1 : Math.min(ScriptEngine.MAX_HOLD_SECONDS, ScriptEngine.number(block.text(1)));
			} else if (block.op.equals("wait_until") || block.op.equals("wait_timeout") || block.op.equals("ask") || block.op.equals("choose") || block.op.equals("act_walk_to")) {
				return Double.POSITIVE_INFINITY;
			}
			if (block.op.equals("if_else")) {
				total += Math.min(waits(block.body), waits(block.elseBody));
			} else if (block.op.equals("repeat")) {
				total += waits(block.body) * Math.max(0, ScriptEngine.number(block.text(0)));
			} else if (!block.op.equals("if")) {
				total += waits(block.body);
			}
		}
		return total;
	}

	private static boolean literal(Scripts.Block block, int index) {
		return index < block.args.size() && block.args.get(index).block == null;
	}

	private static void walk(List<Scripts.Block> blocks, Context context, Map<Scripts.Block, String> issues) {
		for (Scripts.Block block : blocks) {
			checkBlock(block, context, issues);
			if (block.def().hasBody()) {
				Context inner = context;
				if (LOOPS.contains(block.op)) {
					double wait = waits(block.body);
					if (!block.op.equals("repeat") && !block.op.equals("for_each") && wait <= 0) {
						issues.put(block, "迴圈裡沒有「等待」積木，會不停地快速重複，建議加上「等待 1 秒」");
					}
					inner = new Context(context.hat(), context.keyword(), context.inLoop() ? Math.min(context.loopWait(), wait) : wait, context.waits());
				}
				walk(block.body, inner, issues);
				walk(block.elseBody, inner, issues);
			}
		}
	}

	private static void checkBlock(Scripts.Block block, Context context, Map<Scripts.Block, String> issues) {
		ScriptBlocks.Def def = block.def();
		List<ScriptBlocks.Slot> slots = def.slots();
		for (int i = 0; i < slots.size() && i < block.args.size(); i++) {
			Scripts.Arg arg = block.args.get(i);
			if (arg.block != null) {
				checkBlock(arg.block, context, issues);
			} else if (slots.get(i).kind() == ScriptBlocks.SlotKind.VARIABLE && arg.text.isEmpty()) {
				issues.put(block, "還沒有選擇變數，請先在「變數」分類建立一個");
			} else if (slots.get(i).kind() == ScriptBlocks.SlotKind.LIST && arg.text.isEmpty()) {
				issues.put(block, "還沒有選擇清單，請先在「變數」分類建立一個清單");
			} else if (scope != null && (slots.get(i).kind() == ScriptBlocks.SlotKind.VARIABLE || slots.get(i).kind() == ScriptBlocks.SlotKind.LIST)
					&& !Scripts.names(slots.get(i).kind(), scope).contains(arg.text)) {
				String noun = slots.get(i).kind() == ScriptBlocks.SlotKind.LIST ? "清單" : "變數";
				issues.put(block, "這個分組沒有「" + arg.text + "」這個" + noun + "，請點它重新選擇，或建立同名的" + noun);
			} else if (slots.get(i).kind() == ScriptBlocks.SlotKind.PROC && arg.text.isEmpty()) {
				issues.put(block, "還沒有選擇要執行的自訂積木，請先在「自訂」分類製作一個");
			} else if (slots.get(i).kind() == ScriptBlocks.SlotKind.BOOLEAN && def.shape() != ScriptBlocks.Shape.BOOLEAN) {
				issues.put(block, block.op.equals("wait_until") ? "條件是空的，會永遠等下去" : "條件是空的，會被當成「不成立」");
			}
		}
		if (issues.containsKey(block)) {
			return;
		}
		if (block.op.equals("broadcast") && "ev_broadcast".equals(context.hat()) && literal(block, 0) && block.text(0).strip().equalsIgnoreCase(context.keyword())) {
			issues.put(block, "廣播給自己會不停重複觸發，同時超過 " + ScriptEngine.MAX_PER_STACK + " 個的觸發會被略過");
			return;
		}
		if (block.op.equals("s_is_online") && literal(block, 0) && !block.text(0).strip().matches("[A-Za-z0-9_.]{1,20}")) {
			issues.put(block, "「" + block.text(0).strip() + "」不是有效的玩家名稱，結果永遠是「否」");
			return;
		}
		if (MESSAGE_READERS.contains(block.op) && (context.hat() == null || !MESSAGE_EVENTS.contains(context.hat()))) {
			issues.put(block, "只有「收到訊息／私訊／被提到」的事件才有訊息內容，這裡會是空的");
			return;
		}
		if (block.op.equals("act_hold") && literal(block, 1) && ScriptEngine.number(block.text(1)) > ScriptEngine.MAX_HOLD_SECONDS) {
			issues.put(block, "最多按住 " + ScriptEngine.MAX_HOLD_SECONDS + " 秒，超過會以 " + ScriptEngine.MAX_HOLD_SECONDS + " 秒計算");
			return;
		}
		if (block.op.equals("act_press") && !context.waits()) {
			issues.put(block, "腳本結束時會自動放開所有按鍵；要一直按著，後面請加上「等待」或改用「按住 … 持續 … 秒」");
			return;
		}
		if ((block.op.equals("title") && "ev_title".equals(context.hat()) || block.op.equals("actionbar") && "ev_actionbar".equals(context.hat()))
				&& literal(block, 0) && !context.keyword().isEmpty() && block.text(0).toLowerCase(Locale.ROOT).contains(context.keyword())) {
			issues.put(block, "顯示的文字含有觸發關鍵字「" + context.keyword() + "」，消失後再出現會再次觸發自己");
			return;
		}
		if ((block.op.equals("break") || block.op.equals("continue")) && !context.inLoop()) {
			issues.put(block, "要放在「重複」或「對清單的每一項」等迴圈裡面才有作用");
			return;
		}
		if (block.op.equals("file_log") && literal(block, 0) && block.text(0).isBlank()) {
			issues.put(block, "請填寫紀錄檔名稱");
			return;
		}
		if (block.op.equals("op_regex") && literal(block, 0) && block.text(0).length() > ScriptExtras.MAX_REGEX) {
			issues.put(block, "規則太長，最多 " + ScriptExtras.MAX_REGEX + " 個字");
			return;
		}
		if (block.op.equals("wh_send")) {
			Scripts.Arg url = block.args.isEmpty() ? null : block.args.getFirst();
			if (url != null && url.block != null) {
				issues.put(block, "Webhook 網址要直接填在積木上，不能用其他積木組成");
				return;
			}
			if (url == null || !DiscordWebhooks.validUrl(url.text)) {
				issues.put(block, "請填入完整的 Discord Webhook 網址（伺服器設定 → 整合 → Webhook → 複製網址）；匯入或分享的腳本會清空網址");
				return;
			}
		}
		if (block.op.equals("proc_arg") && !"proc_def".equals(context.hat())) {
			issues.put(block, "「帶入的值」只能用在「定義積木」底下的積木裡，這裡會是空的");
			return;
		}
		if (block.op.equals("reply") && !"ev_private".equals(context.hat())) {
			issues.put(block, "只有「當收到私訊時」觸發的腳本才知道要回覆誰，這裡會失敗");
			return;
		}
		if (block.op.equals("msg") && literal(block, 0) && !block.text(0).strip().matches("[A-Za-z0-9_.]{1,20}")) {
			issues.put(block, "「" + block.text(0).strip() + "」不是有效的玩家名稱，私訊會失敗");
			return;
		}
		if (!SENDS.contains(block.op)) {
			return;
		}
		if (block.op.equals("say_channel") && block.text(0).equals("交易") && context.inLoop() && context.loopWait() < TRADE_COOLDOWN) {
			issues.put(block, "交易頻道每 20 分鐘只能發言一次，迴圈裡至少要等待 " + TRADE_COOLDOWN + " 秒，否則會被伺服器擋下");
			return;
		}
		if (block.op.equals("say_channel") && literal(block, 1) && block.text(1).strip().startsWith("/")) {
			issues.put(block, "頻道訊息不能是指令，開頭是「/」的內容不會送出");
			return;
		}
		int textIndex = block.args.size() - 1;
		if (!literal(block, textIndex)) {
			if (context.inLoop() && context.loopWait() < 3) {
				issues.put(block, "迴圈中每次送出的間隔少於 3 秒，超過的訊息會被略過（每 3 秒最多送出一則）");
			}
			return;
		}
		String text = block.text(textIndex).strip();
		if (text.isEmpty()) {
			issues.put(block, "內容是空的，不會送出任何東西");
		} else if (!context.keyword().isEmpty() && text.toLowerCase(Locale.ROOT).contains(context.keyword())) {
			issues.put(block, "送出的內容含有觸發關鍵字「" + context.keyword() + "」，可能會再次觸發自己而不停重複");
		} else if (context.inLoop() && context.loopWait() < 3) {
			issues.put(block, "迴圈中每次送出的間隔少於 3 秒，超過的訊息會被略過（每 3 秒最多送出一則）");
		} else if (context.inLoop() && context.loopWait() < 10) {
			issues.put(block, "迴圈每次都送出同樣的內容，10 秒內重複的訊息會被略過");
		}
	}
}
