package net.brilliantw.richpresence;

final class Scroller {
	private static final int MIN_THUMB = 8;

	private int offset;
	private int content;
	private int view;
	private boolean dragging;
	private double grab;

	int offset() {
		return offset;
	}

	void measure(int content, int view) {
		this.content = Math.max(0, content);
		this.view = Math.max(1, view);
		offset = Math.clamp(offset, 0, max());
	}

	int max() {
		return Math.max(0, content - view);
	}

	boolean overflow() {
		return content > view;
	}

	void scrollBy(int pixels) {
		offset = Math.clamp(offset + pixels, 0, max());
	}

	void reveal(int start, int length) {
		if (start < offset) {
			offset = Math.max(0, start);
		} else if (start + length > offset + view) {
			offset = Math.min(max(), start + length - view);
		}
	}

	void reset() {
		offset = 0;
	}

	int thumbLength(int track) {
		return overflow() ? Math.clamp((int) ((long) track * view / content), MIN_THUMB, track) : track;
	}

	int thumbStart(int track) {
		int length = thumbLength(track);
		return max() == 0 ? 0 : (int) ((long) (track - length) * offset / max());
	}

	boolean press(double pos, int trackStart, int track) {
		if (!overflow()) {
			return false;
		}
		int start = trackStart + thumbStart(track);
		int length = thumbLength(track);
		grab = pos >= start && pos < start + length ? pos - start : length / 2.0;
		dragging = true;
		drag(pos, trackStart, track);
		return true;
	}

	boolean drag(double pos, int trackStart, int track) {
		if (!dragging) {
			return false;
		}
		int free = track - thumbLength(track);
		offset = free <= 0 ? 0 : Math.clamp((int) Math.round((pos - grab - trackStart) * max() / free), 0, max());
		return true;
	}

	boolean release() {
		boolean was = dragging;
		dragging = false;
		return was;
	}

	boolean dragging() {
		return dragging;
	}

	public static void main(String[] args) {
		Scroller s = new Scroller();
		s.measure(300, 100);
		check(s.max() == 200 && s.thumbLength(100) == 33 && s.thumbStart(100) == 0, "thumb at start");
		s.scrollBy(1000);
		check(s.offset() == 200 && s.thumbStart(100) == 67, "clamped to end");
		s.press(0, 0, 100);
		check(s.offset() == 0, "press at track start goes to beginning");
		s.drag(100, 0, 100);
		check(s.offset() == 200 && s.release() && !s.dragging(), "drag to the end then release");
		s.reveal(10, 20);
		check(s.offset() == 10, "reveal scrolls back to show the item");
		s.measure(50, 100);
		check(!s.overflow() && s.offset() == 0 && !s.press(5, 0, 100), "no scrolling when content fits");
		System.out.println("ok");
	}

	private static void check(boolean ok, String name) {
		if (!ok) {
			throw new AssertionError(name);
		}
	}
}
