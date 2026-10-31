package dev.fancybars.gifs;

import java.util.ArrayList;
import java.util.List;

/**
 * Removes the crafting grid / offhand slot from any inventory background using seamless cloning:
 * <ol>
 *     <li>search the inventory texture and the pack's other container textures for the patch that best continues
 *     the art around the hole (compared on the hole's open sides) and contains no UI-like straight edges;</li>
 *     <li>copy that patch in, keeping all its real detail;</li>
 *     <li>add a smooth "membrane" correction so the colours meet the surroundings exactly at the edges.</li>
 * </ol>
 * Works on plain int[] ARGB images so it can be tested without the game.
 */
public final class InventoryPatcher {
	/** A box to remove, in texture pixels (end exclusive), and which of its sides border clean background. */
	public record Hole(int x0, int y0, int x1, int y1, boolean top, boolean bottom, boolean left, boolean right) {}

	/** An image: ARGB pixels, row by row. */
	public record Img(int[] px, int w, int h) {
		int get(int x, int y) {
			return px[Math.clamp(y, 0, h - 1) * w + Math.clamp(x, 0, w - 1)];
		}
	}

	static boolean DEBUG = false;

	private InventoryPatcher() {}

	static int luma(int c) {
		return ((c >>> 16 & 0xFF) * 3 + (c >>> 8 & 0xFF) * 6 + (c & 0xFF)) / 10;
	}

	private static int diff(int a, int b) {
		return Math.abs((a >>> 16 & 0xFF) - (b >>> 16 & 0xFF)) + Math.abs((a >>> 8 & 0xFF) - (b >>> 8 & 0xFF)) + Math.abs((a & 0xFF) - (b & 0xFF));
	}

	private static boolean edgeH(Img img, int x, int y) {
		return Math.abs(luma(img.get(x, y)) - luma(img.get(x + 1, y))) > 16;
	}

	private static boolean edgeV(Img img, int x, int y) {
		return Math.abs(luma(img.get(x, y)) - luma(img.get(x, y + 1))) > 16;
	}

	/** 1 where a pixel looks like UI (see {@link #lineIntegral}), stored as longs for reuse. */
	private static long[] uiFlags(Img img) {
		int w = img.w(), h = img.h();
		long[] flags = new long[w * h];
		for (int y = 0; y < h; y++) {
			for (int x = 0; x < w; x++) {
				boolean ui = false;
				// an edge touching this pixel from any side that continues for 3 pixels
				for (int oy = -1; oy <= 0 && !ui; oy++) {
					for (int ox = -1; ox <= 0 && !ui; ox++) {
						int qx = x + ox, qy = y + oy;
						ui = edgeH(img, qx, qy) && edgeH(img, qx, qy + 1) && edgeH(img, qx, qy - 1)
								|| edgeV(img, qx, qy) && edgeV(img, qx + 1, qy) && edgeV(img, qx - 1, qy)
								|| edgeH(img, qx, qy) && edgeH(img, qx + 1, qy + 1) && edgeH(img, qx - 1, qy - 1)
								|| edgeH(img, qx, qy) && edgeH(img, qx - 1, qy + 1) && edgeH(img, qx + 1, qy - 1);
					}
				}
				flags[y * w + x] = ui ? 1 : 0;
			}
		}
		return flags;
	}

	/**
	 * Prefix sums of "UI-looking" pixels: a strong edge that continues for 3 pixels in a straight or diagonal line
	 * (slot borders, arrows, boxes). Stars are single pixels, so their edges never line up like that.
	 */
	private static long[] lineIntegral(Img img) {
		int w = img.w(), h = img.h();
		long[] sum = new long[(w + 1) * (h + 1)];
		for (int y = 0; y < h; y++) {
			long row = 0;
			for (int x = 0; x < w; x++) {
				boolean ui = edgeH(img, x, y) && edgeH(img, x, y + 1) && edgeH(img, x, y + 2)                // vertical line
						|| edgeV(img, x, y) && edgeV(img, x + 1, y) && edgeV(img, x + 2, y)                     // horizontal line
						|| edgeH(img, x, y) && edgeH(img, x + 1, y + 1) && edgeH(img, x + 2, y + 2)             // diagonal \
						|| edgeH(img, x + 2, y) && edgeH(img, x + 1, y + 1) && edgeH(img, x, y + 2);            // diagonal /
				row += ui ? 1 : 0;
				sum[(y + 1) * (w + 1) + x + 1] = sum[y * (w + 1) + x + 1] + row;
			}
		}
		return sum;
	}


	private static long boxSum(long[] sum, int w, int x0, int y0, int x1, int y1) {
		int W = w + 1;
		return sum[y1 * W + x1] - sum[y0 * W + x1] - sum[y1 * W + x0] + sum[y0 * W + x0];
	}

	/**
	 * Exemplar inpainting: fills the holes from their edges inwards, a small patch at a time. Each patch is copied
	 * from whichever texture (the inventory itself or another container screen) and nearby position best continues
	 * the pixels already known around it. UI never matches the surrounding background, so it's never copied; packs
	 * that share one background across screens get their exact art back (stars and all).
	 *
	 * @param target  the inventory texture (modified in place)
	 * @param sources other textures to borrow from (same size); the target itself is always searched too
	 * @param panel   the area donors may come from {x0, y0, x1, y1} in texture pixels
	 */
	public static void patch(Img target, List<Img> sources, Hole[] holes, int[] panel, float scale) {
		List<Img> all = new ArrayList<>();
		all.add(target);
		// screens with an identical texture (furnace / smoker / blast furnace...) only add work: keep one of each
		outer:
		for (Img s : sources) {
			if (s.w() != target.w() || s.h() != target.h()) continue;
			for (Img o : all) if (java.util.Arrays.equals(o.px(), s.px())) continue outer;
			all.add(s);
		}
		// choose patches on a 256-px copy (fast for any pack resolution), then copy full-resolution pixels
		int S = Math.max(1, Math.round(scale));
		List<Img> small = new ArrayList<>();
		for (Img img : all) small.add(S == 1 ? new Img(img.px().clone(), img.w(), img.h()) : down(img, S));
		Hole[] smallHoles = new Hole[holes.length];
		for (int i = 0; i < holes.length; i++) {
			Hole h = holes[i];
			smallHoles[i] = new Hole(h.x0() / S, h.y0() / S, (h.x1() + S - 1) / S, (h.y1() + S - 1) / S, h.top(), h.bottom(), h.left(), h.right());
		}
		int[] smallPanel = {panel[0] / S, panel[1] / S, panel[2] / S, panel[3] / S};
		Img smallTarget = small.getFirst();
		int sw = smallTarget.w(), sh = smallTarget.h();
		int[] choice = new int[sw * sh];
		java.util.Arrays.fill(choice, -1);
		List<long[]> lines = new ArrayList<>();
		for (Img img : small) lines.add(lineIntegral(img));
		boolean[] known = new boolean[sw * sh];
		java.util.Arrays.fill(known, true);
		// a hole's closed sides (next to the player box or frame) don't seed it
		boolean[] usable = new boolean[sw * sh];
		java.util.Arrays.fill(usable, true);
		int edge = 2;
		for (Hole hole : smallHoles) {
			for (int y = hole.y0(); y < hole.y1(); y++) for (int x = hole.x0(); x < hole.x1(); x++) known[y * sw + x] = false;
			if (!hole.left()) for (int y = hole.y0() - edge; y < hole.y1() + edge; y++) for (int x = hole.x0() - edge; x < hole.x0(); x++) mark(usable, sw, sh, x, y);
			if (!hole.right()) for (int y = hole.y0() - edge; y < hole.y1() + edge; y++) for (int x = hole.x1(); x < hole.x1() + edge; x++) mark(usable, sw, sh, x, y);
			if (!hole.top()) for (int y = hole.y0() - edge; y < hole.y0(); y++) for (int x = hole.x0() - edge; x < hole.x1() + edge; x++) mark(usable, sw, sh, x, y);
			if (!hole.bottom()) for (int y = hole.y1(); y < hole.y1() + edge; y++) for (int x = hole.x0() - edge; x < hole.x1() + edge; x++) mark(usable, sw, sh, x, y);
		}
		// the inventory may only lend its own real pixels (never ones that were just filled in)
		boolean[] original = known.clone();
		for (int i = 0; i < original.length; i++) original[i] &= usable[i];
		for (Hole hole : smallHoles) fillHole(smallTarget, small, lines, known, usable, original, hole, smallPanel, 2, 10, choice);
		// apply: every full-res hole pixel takes the full-res pixel the chosen donor has there
		int w = target.w();
		Img before = new Img(target.px().clone(), target.w(), target.h());
		for (Hole hole : holes) {
			for (int y = hole.y0(); y < hole.y1(); y++) {
				for (int x = hole.x0(); x < hole.x1(); x++) {
					int c = choice[Math.min(sh - 1, y / S) * sw + Math.min(sw - 1, x / S)];
					if (c < 0) continue;
					int si = c >>> 24, dx = (c << 8) >> 20, dy = (c << 20) >> 20;
					Img src = all.get(si);
					int v = src.get(x + dx * S, y + dy * S);
					target.px()[y * w + x] = (target.px()[y * w + x] & 0xFF000000) | (v & 0xFFFFFF);
				}
			}
		}
		// when the patches mostly came from somewhere else (no screen shares this art), keep their detail but take the
		// broad colour from a smooth blend of the surroundings, so patch edges and colour jumps disappear
		for (int i = 0; i < holes.length; i++) {
			Hole small0 = smallHoles[i];
			int aligned = 0, total = 0;
			for (int y = small0.y0(); y < small0.y1(); y++) {
				for (int x = small0.x0(); x < small0.x1(); x++) {
					int c = choice[y * sw + x];
					if (c < 0) continue;
					total++;
					if (c >>> 24 != 0 && (c & 0xFFFFFF) == 0) aligned++;
				}
			}
			if (DEBUG) System.out.println("hole " + i + " aligned " + aligned + "/" + total);
			if (total > 0 && aligned / (double) total < 0.7) harmonize(target, before, holes[i], S);
		}
	}

	/** Replaces the low-frequency colour of the filled hole with a smooth blend from its open edges. */
	private static void harmonize(Img target, Img original, Hole hole, int S) {
		int hw = hole.x1() - hole.x0(), hh = hole.y1() - hole.y0();
		int band = Math.max(2, 2 * S), blur = Math.max(2, 4 * S);
		float[][] top = new float[hw][], bottom = new float[hw][], left = new float[hh][], right = new float[hh][];
		for (int i = 0; i < hw; i++) {
			if (hole.top()) top[i] = avg(original, hole.x0() + i - blur, hole.y0() - band, hole.x0() + i + blur, hole.y0() - 1);
			if (hole.bottom()) bottom[i] = avg(original, hole.x0() + i - blur, hole.y1(), hole.x0() + i + blur, hole.y1() + band - 1);
		}
		for (int j = 0; j < hh; j++) {
			if (hole.left()) left[j] = avg(original, hole.x0() - band, hole.y0() + j - blur, hole.x0() - 1, hole.y0() + j + blur);
			if (hole.right()) right[j] = avg(original, hole.x1(), hole.y0() + j - blur, hole.x1() + band - 1, hole.y0() + j + blur);
		}
		// low-pass of the filled result (box blur over the hole)
		int[] filled = new int[hw * hh];
		for (int j = 0; j < hh; j++) for (int i = 0; i < hw; i++) filled[j * hw + i] = target.get(hole.x0() + i, hole.y0() + j);
		float[][] low = new float[3][hw * hh];
		int rad = Math.max(2, 3 * S);
		for (int k = 0; k < 3; k++) {
			int shift = 16 - k * 8;
			float[] tmp = new float[hw * hh];
			for (int j = 0; j < hh; j++) for (int i = 0; i < hw; i++) {
				float sum = 0;
				int n = 0;
				for (int d = -rad; d <= rad; d++) {
					int ii = Math.clamp(i + d, 0, hw - 1);
					sum += filled[j * hw + ii] >>> shift & 0xFF;
					n++;
				}
				tmp[j * hw + i] = sum / n;
			}
			for (int j = 0; j < hh; j++) for (int i = 0; i < hw; i++) {
				float sum = 0;
				int n = 0;
				for (int d = -rad; d <= rad; d++) {
					sum += tmp[Math.clamp(j + d, 0, hh - 1) * hw + i];
					n++;
				}
				low[k][j * hw + i] = sum / n;
			}
		}
		for (int j = 0; j < hh; j++) {
			for (int i = 0; i < hw; i++) {
				float dT = j + 1f, dB = hh - j, dL = i + 1f, dR = hw - i;
				float wt = hole.top() ? 1 / (dT * dT) : 0, wb = hole.bottom() ? 1 / (dB * dB) : 0;
				float wl = hole.left() ? 1 / (dL * dL) : 0, wr = hole.right() ? 1 / (dR * dR) : 0;
				float tw = Math.max(1e-6f, wt + wb + wl + wr);
				int c = filled[j * hw + i];
				int out = c & 0xFF000000;
				for (int k = 0; k < 3; k++) {
					int shift = 16 - k * 8;
					float smooth = ((hole.top() ? top[i][k] * wt : 0) + (hole.bottom() ? bottom[i][k] * wb : 0)
							+ (hole.left() ? left[j][k] * wl : 0) + (hole.right() ? right[j][k] * wr : 0)) / tw;
					// only bright specks (stars, sparkles) are carried over; mid-tone patch texture would look blocky
					float lowLuma = (low[0][j * hw + i] * 3 + low[1][j * hw + i] * 6 + low[2][j * hw + i]) / 10f;
					boolean speck = luma(c) - lowLuma > 35;
					float detail = speck ? (c >>> shift & 0xFF) - low[k][j * hw + i] : 0;
					out |= Math.clamp(Math.round(smooth + detail), 0, 255) << shift;
				}
				target.px()[(hole.y0() + j) * target.w() + hole.x0() + i] = out;
			}
		}
	}

	private static float[] avg(Img img, int x0, int y0, int x1, int y1) {
		float[] sum = new float[3];
		int n = 0;
		for (int y = y0; y <= y1; y++) for (int x = x0; x <= x1; x++) {
			int c = img.get(x, y);
			for (int k = 0; k < 3; k++) sum[k] += c >>> (16 - k * 8) & 0xFF;
			n++;
		}
		for (int k = 0; k < 3; k++) sum[k] /= Math.max(1, n);
		return sum;
	}

	/** Box-filtered downscale by a whole factor. */
	private static Img down(Img img, int S) {
		int w = img.w() / S, h = img.h() / S;
		int[] out = new int[w * h];
		for (int y = 0; y < h; y++) {
			for (int x = 0; x < w; x++) {
				int a = 0, r = 0, g = 0, b = 0;
				for (int j = 0; j < S; j++) for (int i = 0; i < S; i++) {
					int c = img.px()[(y * S + j) * img.w() + x * S + i];
					a += c >>> 24; r += c >>> 16 & 0xFF; g += c >>> 8 & 0xFF; b += c & 0xFF;
				}
				int n = S * S;
				out[y * w + x] = (a / n) << 24 | (r / n) << 16 | (g / n) << 8 | (b / n);
			}
		}
		return new Img(out, w, h);
	}

	private static void mark(boolean[] usable, int w, int h, int x, int y) {
		if (x >= 0 && y >= 0 && x < w && y < h) usable[y * w + x] = false;
	}

	private static void fillHole(Img target, List<Img> all, List<long[]> lines, boolean[] known, boolean[] usable, boolean[] original, Hole hole, int[] panel, int r, int search, int[] choice) {
		int w = target.w();
		int[] px = target.px();
		int lastSource = 0, lastDx = 0, lastDy = 0;
		while (true) {
			// pick the unknown pixel with the most known neighbours (the most constrained) to fill next
			int bestP = -1, bestCount = 0;
			for (int y = hole.y0(); y < hole.y1(); y++) {
				for (int x = hole.x0(); x < hole.x1(); x++) {
					if (known[y * w + x]) continue;
					int count = 0;
					for (int dy = -r; dy <= r; dy++) {
						for (int dx = -r; dx <= r; dx++) {
							int qx = x + dx, qy = y + dy;
							if (qx < 0 || qy < 0 || qx >= w || qy >= target.h()) continue;
							int q = qy * w + qx;
							if (known[q] && usable[q]) count++;
						}
					}
					if (count > bestCount) {
						bestCount = count;
						bestP = y * w + x;
					}
				}
			}
			if (bestP < 0) {
				// nothing left with known neighbours (shouldn't happen): fill the rest from the target as-is
				return;
			}
			int cx = bestP % w, cy = bestP / w;
			// find the best donor patch
			double best = Double.MAX_VALUE;
			int bs = 0, bdx = 0, bdy = 0;
			for (int si = 0; si < all.size(); si++) {
				Img src = all.get(si);
				// the inventory itself is searched everywhere; other screens near the same spot (where shared art lines up)
				int yLo = si == 0 ? panel[1] + r - cy : -search, yHi = si == 0 ? panel[3] - r - 1 - cy : search;
				int xLo = si == 0 ? panel[0] + r - cx : -search, xHi = si == 0 ? panel[2] - r - 1 - cx : search;
				for (int dy = yLo; dy <= yHi; dy++) {
					for (int dx = xLo; dx <= xHi; dx++) {
						int sx = cx + dx, sy = cy + dy;
						if (sx - r < panel[0] || sy - r < panel[1] || sx + r >= panel[2] || sy + r >= panel[3]) continue;
						long cost = 0;
						int n = 0;
						boolean ok = true;
						for (int py = -r; py <= r && ok; py++) {
							for (int pxo = -r; pxo <= r; pxo++) {
								int q = (cy + py) * w + (cx + pxo);
								int sq = (sy + py) * w + (sx + pxo);
								// the donor patch itself must be real pixels (in the target: not inside a hole)
								if (si == 0 && !original[sq]) {
									ok = false;
									break;
								}
								if (!known[q] || !usable[q]) continue;
								cost += diff(px[q], src.px()[sq]);
								n++;
							}
						}
						if (!ok || n == 0) continue;
						// UI (slot borders, arrows, boxes) is made of straight edges: never copy pixels that are part of it
						int pad = r + 1;
						long uiPixels = boxSum(lines.get(si), w, Math.max(0, sx - pad), Math.max(0, sy - pad), Math.min(w, sx + pad + 1), Math.min(target.h(), sy + pad + 1));
						// small preference for staying aligned and for continuing the previous choice (smoother)
						double score = cost / (double) n + uiPixels * 40.0 + Math.min(Math.abs(dx) + Math.abs(dy), 12) * 0.4
								+ (si == lastSource && dx == lastDx && dy == lastDy ? -2 : 0);
						if (score < best) {
							best = score;
							bs = si;
							bdx = dx;
							bdy = dy;
						}
					}
				}
			}
			// copy the unknown pixels of the patch from the donor
			Img src = all.get(bs);
			for (int py = -r; py <= r; py++) {
				for (int pxo = -r; pxo <= r; pxo++) {
					int x = cx + pxo, y = cy + py;
					if (x < hole.x0() || y < hole.y0() || x >= hole.x1() || y >= hole.y1()) continue;
					int q = y * w + x;
					if (known[q]) continue;
					int sq = (y + bdy) * w + (x + bdx);
					px[q] = (px[q] & 0xFF000000) | (src.px()[sq] & 0xFFFFFF);
					known[q] = true;
					// remember where this pixel came from, to copy the full-resolution pixels later
					choice[q] = bs << 24 | (bdx & 0xFFF) << 12 | (bdy & 0xFFF);
				}
			}
			lastSource = bs;
			lastDx = bdx;
			lastDy = bdy;
		}
	}



}
