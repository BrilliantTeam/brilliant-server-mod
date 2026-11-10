package net.brilliantw.richpresence;

import java.util.List;
import java.util.Map;
import net.minecraft.client.gui.Font;

public final class ScriptTutorial {
	public static final String[] TITLES = { "拖曳積木", "放進空格", "執行腳本", "檢查與提醒", "懸浮視窗", "快捷鍵" };
	public static final String[] CAPTIONS = {
			"從左邊把積木拖到右邊，接在黃色「事件」積木下面",
			"圓角積木可以放進空格，C 形積木可以把其他積木包起來",
			"點一下腳本就會執行，正在執行的積木會發亮",
			"黃色驚嘆號表示可能有問題，執行被略過時會跳出紅色提示",
			"打開聊天欄時，懸浮視窗可以執行、停止、拖曳與縮放",
			"按 F1 隨時可以再打開這份教學",
	};
	private static final long[] LOOPS = { 5200, 6200, 7000, 6400, 6400, 6000 };
	private static final int PALETTE_BG = 0xFF20272E;
	private static final int SHADOW = 0x50000000;

	private final Font font;
	private final ScriptView view;
	private int page = -1;
	private long start;

	private final Scripts.Block dragHat = block("ev_start");
	private final Scripts.Block dragSay = block("say", "大家好！");

	private final Scripts.Block emptySay = block("say", "");
	private final Scripts.Block filledSay = block("say", "");
	private final Scripts.Block reporter = block("s_me");
	private final Scripts.Block emptyForever = block("forever");
	private final Scripts.Block filledForever = block("forever");
	private final Scripts.Block waitBlock = block("wait", "1");

	private final Scripts.Block runHat = block("ev_start");
	private final Scripts.Block runRepeat = block("repeat", "3");
	private final Scripts.Block runSay = block("say", "嗨");
	private final Scripts.Block runWait = block("wait", "1");

	private final Scripts.Block lintHat = block("ev_start");
	private final Scripts.Block lintForever = block("forever");
	private final Scripts.Block lintSay = block("say", "收購鑽石");

	private final Scripts.Block monitorHat = block("ev_start");
	private final Scripts.Block monitorSay = block("say", "嗨");

	public ScriptTutorial(Font font) {
		this.font = font;
		this.view = new ScriptView(font);
		this.view.animate = false;
		filledSay.args.getFirst().block = block("s_me");
		filledForever.body.add(block("wait", "1"));
		runRepeat.body.add(runSay);
		runRepeat.body.add(runWait);
		lintForever.body.add(lintSay);
	}

	private static Scripts.Block block(String op, String... texts) {
		Scripts.Block block = Scripts.create(ScriptBlocks.get(op));
		for (int i = 0; i < texts.length && i < block.args.size(); i++) {
			block.args.get(i).text = texts[i];
		}
		return block;
	}

	private static double seg(double t, double a, double b) {
		double x = Math.clamp((t - a) / (b - a), 0, 1);
		return x * x * (3 - 2 * x);
	}

	private static double lerp(double a, double b, double f) {
		return a + (b - a) * f;
	}

	private static int alpha(int color, double fraction) {
		return (int) Math.round(Math.clamp(fraction, 0, 1) * (color >>> 24)) << 24 | color & 0xFFFFFF;
	}

	public void draw(Gfx gfx, int page, int x, int y, int w, int h) {
		long now = System.currentTimeMillis();
		if (page != this.page) {
			this.page = page;
			start = now;
		}
		double t = (now - start) % LOOPS[page] / 1000.0;
		view.issues = Map.of();
		gfx.scissor(x, y, x + w, y + h);
		switch (page) {
			case 0 -> dragPage(gfx, x, y, w, h, t);
			case 1 -> slotPage(gfx, x, y, w, h, t);
			case 2 -> runPage(gfx, x, y, w, h, t);
			case 3 -> lintPage(gfx, x, y, w, h, t);
			case 4 -> monitorPage(gfx, x, y, w, h, t);
			default -> keysPage(gfx, x, y, w, h, t);
		}
		gfx.endScissor();
	}

	private ScriptView.Laid stack(Gfx gfx, List<Scripts.Block> blocks, int x, int y) {
		ScriptView.Laid laid = view.layoutList(blocks, x, y);
		for (ScriptView.Node node : laid.nodes()) {
			view.drawNode(gfx, node);
		}
		return laid;
	}

	private void dragged(Gfx gfx, List<Scripts.Block> blocks, int x, int y) {
		ScriptView.Laid laid = view.layoutList(blocks, x, y);
		gfx.fill(x + 3, y + 4, x + laid.width() + 3, y + laid.height() + 4, SHADOW);
		for (ScriptView.Node node : laid.nodes()) {
			view.drawNode(gfx, node);
		}
	}

	private static void outline(Gfx gfx, int x, int y, int w, int h, int color) {
		gfx.fill(x - 1, y - 1, x + w + 1, y, color);
		gfx.fill(x - 1, y + h, x + w + 1, y + h + 1, color);
		gfx.fill(x - 1, y, x, y + h, color);
		gfx.fill(x + w, y, x + w + 1, y + h, color);
	}

	private static void glow(Gfx gfx, ScriptView.Node node, int color) {
		outline(gfx, node.x, node.y, node.w, node.headerBottom() - node.y, color);
		outline(gfx, node.x - 1, node.y - 1, node.w + 2, node.headerBottom() - node.y + 2, alpha(color, 0.5));
	}

	private static void cursor(Gfx gfx, double cx, double cy, double press) {
		int x = (int) Math.round(cx);
		int y = (int) Math.round(cy);
		if (press > 0 && press < 1) {
			int r = (int) (3 + press * 8);
			outline(gfx, x - r, y - r, r * 2, r * 2, alpha(0xFFFFFFFF, 1 - press));
		}
		for (int i = 0; i < 11; i++) {
			int span = i < 8 ? i + 2 : i == 8 ? 5 : 3;
			int from = i < 9 ? x : x + 3 + (i - 9);
			gfx.fill(from, y + i, from + (i < 9 ? span : 2), y + i + 1, 0xFF000000);
		}
		for (int i = 1; i < 8; i++) {
			gfx.fill(x + 1, y + i, x + i, y + i + 1, 0xFFFFFFFF);
		}
		gfx.fill(x + 3, y + 8, x + 4, y + 9, 0xFFFFFFFF);
	}

	private void dragPage(Gfx gfx, int x, int y, int w, int h, double t) {
		gfx.fill(x, y, x + 104, y + h, PALETTE_BG);
		gfx.text(font, "積木庫", x + 6, y + 4, Theme.TEXT_DIM);
		gfx.text(font, "工作區", x + 112, y + 4, Theme.TEXT_DIM);
		int hatX = x + 8;
		int hatY = y + 18;
		int sayX = x + 8;
		int sayY = y + 58;
		stack(gfx, List.of(block("ev_start")), hatX, hatY);
		stack(gfx, List.of(block("say", "大家好！")), sayX, sayY);
		int targetX = x + 132;
		int targetY = y + 26;
		int hatH = view.layoutList(List.of(dragHat), 0, 0).height();
		double hatMove = seg(t, 0.7, 1.7);
		double sayMove = seg(t, 2.5, 3.4);
		double snap = seg(t, 3.4, 3.6);
		int hx = (int) lerp(hatX, targetX, hatMove);
		int hy = (int) lerp(hatY, targetY, hatMove);
		boolean joined = t >= 3.6;
		if (joined) {
			ScriptView.Laid laid = stack(gfx, List.of(dragHat, dragSay), targetX, targetY);
			double flash = 1 - seg(t, 3.6, 4.2);
			if (flash > 0) {
				gfx.fill(targetX, targetY, targetX + laid.width(), targetY + laid.height(), alpha(0x80FFFFFF, flash));
			}
		} else if (t >= 0.7) {
			if (hatMove < 1) {
				dragged(gfx, List.of(dragHat), hx, hy);
			} else {
				stack(gfx, List.of(dragHat), targetX, targetY);
			}
		}
		double cx;
		double cy;
		if (t < 0.7) {
			double f = seg(t, 0, 0.6);
			cx = lerp(x + w - 30, hatX + 20, f);
			cy = lerp(y + h - 16, hatY + 12, f);
		} else if (t < 1.9) {
			cx = hx + 20;
			cy = hy + 12;
		} else if (t < 2.5) {
			double f = seg(t, 1.9, 2.4);
			cx = lerp(targetX + 20, sayX + 20, f);
			cy = lerp(targetY + 12, sayY + 8, f);
		} else {
			int nearX = targetX + 10;
			int nearY = targetY + hatH + 8;
			int sx = (int) lerp(lerp(sayX, nearX, sayMove), targetX, snap);
			int sy = (int) lerp(lerp(sayY, nearY, sayMove), targetY + hatH, snap);
			if (!joined) {
				if (sayMove > 0.6) {
					gfx.fill(targetX, targetY + hatH, targetX + 80, targetY + hatH + 3, alpha(0xFFFFFFFF, 0.6));
				}
				dragged(gfx, List.of(dragSay), sx, sy);
			}
			cx = sx + 20;
			cy = sy + 8;
		}
		cursor(gfx, cx, cy, t > 0.6 && t < 0.9 ? (t - 0.6) / 0.3 : t > 2.4 && t < 2.7 ? (t - 2.4) / 0.3 : 0);
	}

	private void slotPage(Gfx gfx, int x, int y, int w, int h, double t) {
		int sayX = x + 12;
		int sayY = y + 14;
		int repX = x + w - 90;
		int repY = y + 16;
		ScriptView.Laid sayLaid = view.layoutList(List.of(emptySay), sayX, sayY);
		ScriptView.SlotBox slot = sayLaid.nodes().getFirst().slots.getFirst();
		double move = seg(t, 0.7, 1.8);
		boolean placed = t >= 1.8;
		stack(gfx, List.of(placed ? filledSay : emptySay), sayX, sayY);
		if (!placed && move > 0.7) {
			outline(gfx, slot.x(), slot.y(), slot.w(), slot.h(), 0xFFFFFFFF);
		}
		int rx = (int) lerp(repX, slot.x(), move);
		int ry = (int) lerp(repY, slot.y(), move);
		if (!placed) {
			dragged(gfx, List.of(reporter), rx, ry);
		}

		int cX = x + 12;
		int cY = y + 58;
		int waitX = x + w - 90;
		int waitY = y + 70;
		ScriptView.Laid cLaid = view.layoutList(List.of(emptyForever), cX, cY);
		ScriptView.Node cNode = cLaid.nodes().getFirst();
		double drop = seg(t, 3.4, 4.6);
		boolean inside = t >= 4.6;
		stack(gfx, List.of(inside ? filledForever : emptyForever), cX, cY);
		int wx = (int) lerp(waitX, cNode.x + ScriptView.ARM, drop);
		int wy = (int) lerp(waitY, cNode.bodyY, drop);
		if (!inside) {
			if (drop > 0.7) {
				gfx.fill(cNode.x + ScriptView.ARM, cNode.bodyY, cNode.x + ScriptView.ARM + 60, cNode.bodyY + 2, alpha(0xFFFFFFFF, 0.7));
			}
			dragged(gfx, List.of(waitBlock), wx, wy);
		}
		if (inside) {
			double flash = 1 - seg(t, 4.6, 5.2);
			ScriptView.Laid laid = view.layoutList(List.of(filledForever), cX, cY);
			gfx.fill(cX, cY, cX + laid.width(), cY + laid.height(), alpha(0x80FFFFFF, flash));
		}
		if (placed && t < 3.4) {
			double flash = 1 - seg(t, 1.8, 2.4);
			ScriptView.Laid laid = view.layoutList(List.of(filledSay), sayX, sayY);
			gfx.fill(sayX, sayY, sayX + laid.width(), sayY + laid.height(), alpha(0x80FFFFFF, flash));
		}
		double cx;
		double cy;
		if (t < 0.7) {
			double f = seg(t, 0, 0.6);
			cx = lerp(x + w - 20, repX + 14, f);
			cy = lerp(y + h - 12, repY + 6, f);
		} else if (t < 2.6) {
			cx = rx + 14;
			cy = ry + 6;
		} else if (t < 3.4) {
			double f = seg(t, 2.6, 3.3);
			cx = lerp(slot.x() + 14, waitX + 16, f);
			cy = lerp(slot.y() + 6, waitY + 8, f);
		} else {
			cx = wx + 16;
			cy = wy + 8;
		}
		cursor(gfx, cx, cy, t > 0.6 && t < 0.9 ? (t - 0.6) / 0.3 : t > 3.3 && t < 3.6 ? (t - 3.3) / 0.3 : 0);
	}

	private void runPage(Gfx gfx, int x, int y, int w, int h, double t) {
		int sx = x + 10;
		int sy = y + 8;
		ScriptView.Laid laid = stack(gfx, List.of(runHat, runRepeat), sx, sy);
		ScriptView.Node hat = laid.nodes().get(0);
		ScriptView.Node repeat = laid.nodes().get(1);
		ScriptView.Node say = repeat.body.get(0);
		ScriptView.Node wait = repeat.body.get(1);
		int chatX = x + w - 112;
		gfx.fill(chatX, y + 8, x + w - 6, y + h - 8, 0x90000000);
		gfx.text(font, "聊天欄", chatX + 4, y + 11, Theme.TEXT_DIM);
		double runStart = 1.0;
		double iteration = 1.1;
		int sent = 0;
		if (t >= runStart && t < runStart + 0.3) {
			glow(gfx, hat, ScriptView.CURRENT);
		}
		for (int i = 0; i < 3; i++) {
			double base = runStart + 0.3 + i * iteration;
			if (t >= base) {
				sent = i + 1;
			}
			if (t >= base && t < base + iteration) {
				String label = (i + 1) + "/3";
				int lw = font.width(label) + 6;
				int lx = repeat.x + repeat.w - lw - 3;
				gfx.fill(lx, repeat.rowY + 3, lx + lw, repeat.rowY + repeat.rowH - 3, 0xC0000000);
				gfx.centered(font, label, lx, repeat.rowY + 3, lw, repeat.rowH - 6, 0xFFFFFFFF);
				if (t < base + 0.3) {
					glow(gfx, say, ScriptView.CURRENT);
					gfx.fill(say.x, say.y, say.x + say.w, say.y + say.h, alpha(0x70FFFFFF, 1 - (t - base) / 0.3));
				} else {
					glow(gfx, wait, ScriptView.CURRENT);
					double progress = (t - base - 0.3) / (iteration - 0.3);
					gfx.fill(wait.x + 2, wait.y + wait.h - 4, wait.x + wait.w - 2, wait.y + wait.h - 2, 0x80000000);
					gfx.fill(wait.x + 2, wait.y + wait.h - 4, wait.x + 2 + (int) ((wait.w - 4) * progress), wait.y + wait.h - 2, 0xFFFFFFFF);
				}
			}
		}
		for (int i = 0; i < sent; i++) {
			gfx.text(font, "<你> 嗨", chatX + 4, y + 24 + i * 11, Theme.TEXT);
		}
		if (t >= runStart + 0.3 + 3 * iteration && t < runStart + 0.3 + 3 * iteration + 1.2) {
			gfx.text(font, "✔ 執行完畢", sx, sy + laid.height() + 8, Theme.SUCCESS);
		}
		double f = seg(t, 0, 0.8);
		double cx = lerp(x + w / 2.0, hat.x + 30, f);
		double cy = lerp(y + h - 10, hat.y + 12, f);
		cursor(gfx, cx, cy, t > 0.8 && t < 1.1 ? (t - 0.8) / 0.3 : 0);
	}

	private void lintPage(Gfx gfx, int x, int y, int w, int h, double t) {
		view.issues = Map.of(lintForever, "");
		int sx = x + 14;
		int sy = y + 34;
		ScriptView.Laid laid = stack(gfx, List.of(lintHat, lintForever), sx, sy);
		view.issues = Map.of();
		ScriptView.Node forever = laid.nodes().get(1);
		ScriptView.Node say = forever.body.getFirst();
		int badgeX = forever.x + forever.w - 4;
		int badgeY = forever.y + 4;
		double f = seg(t, 0, 0.9);
		double cx = lerp(x + w - 30, badgeX, f);
		double cy = lerp(y + h - 10, badgeY, f);
		if (t >= 0.9 && t < 3.0) {
			String[] lines = { "迴圈裡沒有「等待」積木，", "會不停地快速重複" };
			int tw = Math.max(font.width(lines[0]), font.width(lines[1])) + 10;
			int tx = Math.min(badgeX + 8, x + w - tw - 2);
			int ty = badgeY + 8;
			double fade = seg(t, 0.9, 1.1);
			gfx.fill(tx - 1, ty - 1, tx + tw + 1, ty + 25, alpha(ScriptView.WARNING, fade));
			gfx.fill(tx, ty, tx + tw, ty + 24, alpha(0xF0202428, fade));
			gfx.text(font, lines[0], tx + 5, ty + 3, alpha(0xFFFFFFFF, fade));
			gfx.text(font, lines[1], tx + 5, ty + 13, alpha(0xFFFFFFFF, fade));
		}
		if (t >= 3.0) {
			double age = t - 3.0;
			double pulse = 0.55 + 0.45 * Math.sin(age * 12);
			outline(gfx, say.x, say.y, say.w, say.h, alpha(ScriptView.ERROR, pulse));
			gfx.fill(say.x, say.y, say.x + say.w, say.y + say.h, alpha(0x40FF5A5A, 1));
			String text = "送出太頻繁，已略過：收購鑽石";
			int bw = font.width(text) + 24;
			int bx = x + (w - bw) / 2;
			int by = y + 4 - (int) ((1 - seg(age, 0, 0.2)) * 12);
			gfx.fill(bx, by, bx + bw, by + 18, 0xE07A1F1F);
			gfx.fill(bx, by, bx + 3, by + 18, 0xFFFF5A5A);
			gfx.fill(bx + 3, by + 17, bx + 3 + (int) ((bw - 3) * (1 - age / 3.4)), by + 18, 0xFFFF5A5A);
			gfx.text(font, "⚠", bx + 7, by + 5, 0xFFFFFFFF);
			gfx.text(font, text, bx + 17, by + 5, 0xFFFFFFFF);
		}
		cursor(gfx, cx, cy, 0);
	}

	private void monitorPage(Gfx gfx, int x, int y, int w, int h, double t) {
		gfx.fill(x, y, x + w, y + h, 0xFF3A5A2A);
		gfx.fill(x, y + h - 28, x + w, y + h, 0xFF5A3E28);
		double grow = seg(t, 2.6, 3.8);
		int mw = (int) lerp(150, Math.min(220, w - 40), grow);
		int mh = (int) lerp(84, Math.min(118, h - 16), grow);
		int mx = x + 14;
		int my = y + 8;
		gfx.fill(mx, my, mx + mw, my + mh, 0xC0101418);
		gfx.fill(mx, my, mx + mw, my + 13, Theme.HEADER);
		gfx.fill(mx, my + 12, mx + mw, my + 13, 0xFFFFAB19);
		boolean running = t >= 1.1 && t < 4.6;
		int tabW = font.width("預設") + 8 + (running ? 6 : 0);
		gfx.fill(mx + 2, my + 1, mx + 2 + tabW, my + 13, 0xFF2E3740);
		gfx.fill(mx + 2, my + 1, mx + 2 + tabW, my + 2, 0xFFFFAB19);
		if (running) {
			gfx.fill(mx + 6, my + 5, mx + 10, my + 9, Theme.SUCCESS);
		}
		gfx.label(font, "預設", mx + 6 + (running ? 6 : 0), my + 1, 11, Theme.TEXT);
		gfx.fill(mx + 4 + tabW, my + 1, mx + 12 + tabW + font.width("掛機"), my + 12, 0x60303840);
		gfx.label(font, "掛機", mx + 8 + tabW, my + 1, 11, Theme.TEXT_SECONDARY);
		int cy = my + 13;
		gfx.fill(mx, cy, mx + mw, cy + 16, 0x60000000);
		int playX = mx + mw - 63;
		int stopX = mx + mw - 32;
		gfx.round(playX, cy + 2, playX + 30, cy + 14, t > 0.8 && t < 1.2 ? Theme.CHIP_HOVER : Theme.CHIP);
		Icons.PLAY.drawCentered(gfx, playX, cy + 2, 30, 12, Theme.SUCCESS);
		gfx.round(stopX, cy + 2, stopX + 29, cy + 14, running ? Theme.CHIP : 0x60303840);
		gfx.fill(stopX + 11, cy + 5, stopX + 17, cy + 11, running ? Theme.DANGER : Theme.TEXT_DIM);
		gfx.label(font, font.plainSubstrByWidth("當按下執行時", playX - mx - 10), mx + 6, cy, 16, running ? Theme.SUCCESS : Theme.TEXT);
		int py = cy + 18;
		gfx.scissor(mx + 2, py, mx + mw - 2, my + mh - 14);
		gfx.push(mx + 6, py + 2, 0.75f);
		ScriptView.Laid laid = view.layoutList(List.of(monitorHat, monitorSay), 0, 0);
		for (ScriptView.Node node : laid.nodes()) {
			view.drawNode(gfx, node);
		}
		if (running) {
			ScriptView.Node current = laid.nodes().get(t < 1.6 ? 0 : 1);
			glow(gfx, current, ScriptView.CURRENT);
		}
		gfx.pop();
		gfx.endScissor();
		if (t >= 1.6) {
			gfx.text(font, font.plainSubstrByWidth("12:00:01 已送出：嗨", mw - 8), mx + 4, my + mh - 11, Theme.TEXT);
		}
		int gx = mx + mw - 7;
		int gy = my + mh - 7;
		for (int i = 0; i < 3; i++) {
			int d = i * 2 + 1;
			gfx.fill(gx + 7 - d - 1, gy + 5, gx + 7 - d, gy + 6, Theme.ACCENT);
			gfx.fill(gx + 5, gy + 7 - d - 1, gx + 6, gy + 7 - d, Theme.ACCENT);
		}
		double cx;
		double cyc;
		if (t < 1.4) {
			double f = seg(t, 0, 0.8);
			cx = lerp(x + w - 30, playX + 15, f);
			cyc = lerp(y + h - 10, cy + 8, f);
		} else if (t < 2.6) {
			double f = seg(t, 1.6, 2.5);
			cx = lerp(playX + 15, gx + 4, f);
			cyc = lerp(cy + 8, gy + 4, f);
		} else {
			cx = gx + 4;
			cyc = gy + 4;
		}
		cursor(gfx, cx, cyc, t > 0.8 && t < 1.1 ? (t - 0.8) / 0.3 : 0);
	}

	private int key(Gfx gfx, String label, int x, int y, boolean lit) {
		int w = Math.max(14, font.width(label) + 8);
		gfx.fill(x, y + 1, x + w, y + 14, 0xFF15191D);
		gfx.fill(x, y, x + w, y + 12, lit ? 0xFFFFAB19 : 0xFF3A424B);
		gfx.centered(font, label, x, y, w, 12, lit ? 0xFF202020 : Theme.TEXT);
		return x + w + 2;
	}

	private void mouse(Gfx gfx, int x, int y, int button, boolean lit) {
		int color = lit ? 0xFFFFAB19 : 0xFF3A424B;
		gfx.fill(x + 1, y, x + 9, y + 13, 0xFF15191D);
		gfx.fill(x + 2, y + 1, x + 8, y + 12, 0xFFC8CDD2);
		switch (button) {
			case 0 -> gfx.fill(x + 2, y + 1, x + 5, y + 5, color);
			case 1 -> gfx.fill(x + 5, y + 1, x + 8, y + 5, color);
			default -> gfx.fill(x + 4, y + 2, x + 6, y + 5, color);
		}
		gfx.fill(x + 2, y + 5, x + 8, y + 6, 0xFF15191D);
	}

	private void keysPage(Gfx gfx, int x, int y, int w, int h, double t) {
		Object[][] rows = {
				{ new String[] { "Ctrl", "Z" }, "復原" },
				{ new String[] { "Ctrl", "Y" }, "重做" },
				{ new String[] { "Ctrl", "C" }, "複製" },
				{ new String[] { "Ctrl", "V" }, "貼上" },
				{ new String[] { "Ctrl", "D" }, "再複製一份" },
				{ new String[] { "Del" }, "刪除選取" },
				{ 0, "拖曳空白處：框選" },
				{ 1, "拖曳：移動畫面" },
				{ 2, "滾輪：縮放" },
				{ new String[] { "Shift", "點擊" }, "加選" },
				{ new String[] { "Ctrl", "1" }, "顯示全部" },
				{ new String[] { "Ctrl", "F" }, "搜尋積木" },
				{ new String[] { "F10" }, "除錯：下一步" },
				{ new String[] { "F1" }, "教學" },
		};
		int lit = (int) (t / 0.5) % rows.length;
		int colW = w / 2;
		int rowH = Math.max(15, Math.min(20, (h - 4) / 7));
		for (int i = 0; i < rows.length; i++) {
			int cx = x + 8 + (i / 7) * colW;
			int cy = y + 4 + (i % 7) * rowH;
			boolean on = i == lit;
			int tx;
			if (rows[i][0] instanceof String[] keys) {
				tx = cx;
				for (int k = 0; k < keys.length; k++) {
					if (k > 0) {
						gfx.text(font, "+", tx, cy + 2, Theme.TEXT_DIM);
						tx += 8;
					}
					tx = key(gfx, keys[k], tx, cy, on);
				}
			} else {
				mouse(gfx, cx, cy, (Integer) rows[i][0], on);
				tx = cx + 12;
			}
			gfx.text(font, (String) rows[i][1], Math.max(tx + 4, cx + 62), cy + 2, on ? Theme.ACCENT : Theme.TEXT);
		}
	}
}
