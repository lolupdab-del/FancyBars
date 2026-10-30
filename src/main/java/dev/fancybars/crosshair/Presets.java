package dev.fancybars.crosshair;

/** Ready-made crosshairs, drawn in white on an odd-sized grid. */
public final class Presets {
	public static final String[] NAMES = {"plus", "plusGap", "dot", "cross", "circle", "square", "t", "chevron"};
	private static final int WHITE = 0xFFFFFFFF;

	private Presets() {}

	public static int[] make(String name, int size) {
		int[] p = new int[size * size];
		int c = size / 2;
		int arm = Math.max(2, c - 1);
		for (int y = 0; y < size; y++) {
			for (int x = 0; x < size; x++) {
				int dx = x - c;
				int dy = y - c;
				boolean on = switch (name) {
					case "plus" -> (dx == 0 && Math.abs(dy) <= arm) || (dy == 0 && Math.abs(dx) <= arm);
					case "plusGap" -> (dx == 0 && Math.abs(dy) >= 2 && Math.abs(dy) <= arm) || (dy == 0 && Math.abs(dx) >= 2 && Math.abs(dx) <= arm) || (dx == 0 && dy == 0);
					case "dot" -> Math.abs(dx) <= 0 && Math.abs(dy) <= 0 || size >= 11 && Math.abs(dx) + Math.abs(dy) <= 1;
					case "cross" -> Math.abs(dx) == Math.abs(dy) && Math.abs(dx) <= arm && dx != 0 || dx == 0 && dy == 0;
					case "circle" -> {
						double r = Math.sqrt(dx * dx + dy * dy);
						yield Math.abs(r - arm * 0.8) < 0.6 || dx == 0 && dy == 0;
					}
					case "square" -> (Math.max(Math.abs(dx), Math.abs(dy)) == arm - 1) || dx == 0 && dy == 0;
					case "t" -> (dy == 0 && Math.abs(dx) <= arm) || (dx == 0 && dy >= 0 && dy <= arm);
					case "chevron" -> dy >= 0 && Math.abs(dx) == dy && dy <= arm - 1;
					default -> false;
				};
				if (on) p[y * size + x] = WHITE;
			}
		}
		return p;
	}

	/** Copies a drawing into a grid of another size, keeping it centred. */
	public static int[] resize(int[] old, int oldSize, int newSize) {
		int[] p = new int[newSize * newSize];
		int offset = (newSize - oldSize) / 2;
		for (int y = 0; y < oldSize; y++) {
			for (int x = 0; x < oldSize; x++) {
				int nx = x + offset;
				int ny = y + offset;
				if (nx >= 0 && ny >= 0 && nx < newSize && ny < newSize) p[ny * newSize + nx] = old[y * oldSize + x];
			}
		}
		return p;
	}
}
