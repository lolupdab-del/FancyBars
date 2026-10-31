package dev.fancybars.util.gui;

import java.util.LinkedHashMap;
import java.util.Map;

import org.joml.Matrix3x2fStack;

import com.mojang.blaze3d.platform.NativeImage;

import dev.fancybars.FancyBarsMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

/**
 * A clean rounded border of any thickness, drawn in real screen pixels: straight edges are solid fills and the
 * four corners come from one anti-aliased ring texture generated for that exact radius and thickness.
 */
public final class Ring {
	private static final Map<Long, Identifier> CORNERS = new LinkedHashMap<>(16, 0.75f, true);
	private static int counter;

	private Ring() {}

	/** Texture of a full ring (outer radius {@code r}, thickness {@code t}, both in screen pixels); size 2r × 2r. */
	private static Identifier corner(int r, float t) {
		long key = (long) r << 32 | Float.floatToIntBits(t);
		Identifier id = CORNERS.get(key);
		if (id != null) return id;
		int size = r * 2;
		NativeImage image = new NativeImage(size, size, false);
		float inner = r - t;
		for (int y = 0; y < size; y++) {
			for (int x = 0; x < size; x++) {
				// 4x4 supersampling for a smooth edge
				int hits = 0;
				for (int sy = 0; sy < 4; sy++) {
					for (int sx = 0; sx < 4; sx++) {
						float dx = x + (sx + 0.5f) / 4f - r;
						float dy = y + (sy + 0.5f) / 4f - r;
						float d = (float) Math.sqrt(dx * dx + dy * dy);
						if (d <= r && d >= inner) hits++;
					}
				}
				int alpha = Math.round(hits / 16f * 255);
				image.setPixel(x, y, alpha << 24 | 0xFFFFFF);
			}
		}
		id = FancyBarsMod.id("ui/ring_" + counter++);
		DynamicTexture texture = new DynamicTexture(() -> "SBCLIENT border", image);
		Minecraft.getInstance().getTextureManager().register(id, texture);
		texture.upload();
		CORNERS.put(key, id);
		if (CORNERS.size() > 1024) {
			var it = CORNERS.entrySet().iterator();
			Identifier old = it.next().getValue();
			it.remove();
			Minecraft.getInstance().getTextureManager().release(old);
		}
		return id;
	}

	// ---------------------------------------------------------------- absolute screen-pixel drawing

	/**
	 * Converts a box in the current pose's units to absolute screen pixels {x1, y1, x2, y2, pixelsPerUnit},
	 * and switches the pose to plain screen pixels (call {@code graphics.pose().popMatrix()} afterwards).
	 */
	private static float[] begin(GuiGraphicsExtractor graphics, float x, float y, float w, float h) {
		Matrix3x2fStack pose = graphics.pose();
		float gs = Theme.guiScale();
		float sx = pose.m00();
		float sy = pose.m11();
		float x1 = Math.round((sx * x + pose.m20()) * gs);
		float y1 = Math.round((sy * y + pose.m21()) * gs);
		float x2 = Math.round((sx * (x + w) + pose.m20()) * gs);
		float y2 = Math.round((sy * (y + h) + pose.m21()) * gs);
		pose.pushMatrix();
		pose.identity();
		pose.scale(1 / gs, 1 / gs);
		return new float[]{x1, y1, x2, y2, Math.abs(sx) * gs};
	}

	private static void fillPx(GuiGraphicsExtractor graphics, float x1, float y1, float x2, float y2, int color) {
		if (x2 > x1 && y2 > y1) graphics.fill((int) x1, (int) y1, (int) x2, (int) y2, color);
	}

	/** Thin line width in screen pixels for hairlines (about 0.6 GUI px, at least 1 pixel). */
	public static int hairline() {
		return Math.max(1, Math.round(Theme.guiScale() * 0.6f));
	}

	/** A plain rectangle snapped to screen pixels. */
	public static void fill(GuiGraphicsExtractor graphics, float x, float y, float w, float h, int color) {
		if (w <= 0 || h <= 0 || (color >>> 24) == 0) return;
		float[] b = begin(graphics, x, y, w, h);
		fillPx(graphics, b[0], b[1], Math.max(b[2], b[0] + 1), Math.max(b[3], b[1] + 1), color);
		graphics.pose().popMatrix();
	}

	/** A horizontal hairline from x1 to x2 at y. */
	public static void hline(GuiGraphicsExtractor graphics, float x1, float x2, float y, int color) {
		float[] b = begin(graphics, x1, y, x2 - x1, 0);
		fillPx(graphics, b[0], b[1], b[2], b[1] + hairline(), color);
		graphics.pose().popMatrix();
	}

	/** A vertical hairline from y1 to y2 at x. */
	public static void vline(GuiGraphicsExtractor graphics, float x, float y1, float y2, int color) {
		float[] b = begin(graphics, x, y1, 0, y2 - y1);
		fillPx(graphics, b[0], b[1], b[0] + hairline(), b[3], color);
		graphics.pose().popMatrix();
	}

	/** Smooth filled rounded rectangle. */
	public static void fillRounded(GuiGraphicsExtractor graphics, float x, float y, float w, float h, float radius, int color) {
		if (w <= 0 || h <= 0 || (color >>> 24) == 0) return;
		float[] b = begin(graphics, x, y, w, h);
		int X = (int) b[0], Y = (int) b[1], W = (int) (b[2] - b[0]), H = (int) (b[3] - b[1]);
		int R = Math.min(Math.round(radius * b[4]), Math.min(W, H) / 2);
		if (R <= 0) {
			fillPx(graphics, X, Y, X + W, Y + H, color);
		} else {
			fillPx(graphics, X + R, Y, X + W - R, Y + H, color);
			fillPx(graphics, X, Y + R, X + R, Y + H - R, color);
			fillPx(graphics, X + W - R, Y + R, X + W, Y + H - R, color);
			Identifier tex = corner(R, R);
			int size = R * 2;
			graphics.blit(RenderPipelines.GUI_TEXTURED, tex, X, Y, 0, 0, R, R, size, size, color);
			graphics.blit(RenderPipelines.GUI_TEXTURED, tex, X + W - R, Y, R, 0, R, R, size, size, color);
			graphics.blit(RenderPipelines.GUI_TEXTURED, tex, X, Y + H - R, 0, R, R, R, size, size, color);
			graphics.blit(RenderPipelines.GUI_TEXTURED, tex, X + W - R, Y + H - R, R, R, R, R, size, size, color);
		}
		graphics.pose().popMatrix();
	}

	/** Smooth rounded outline whose thickness is in screen pixels (fractions fade the inner edge). */
	public static void outlinePx(GuiGraphicsExtractor graphics, float x, float y, float w, float h, float radius, float thicknessPx, int color) {
		if (w <= 0 || h <= 0 || thicknessPx <= 0 || (color >>> 24) == 0) return;
		float[] b = begin(graphics, x, y, w, h);
		ring(graphics, (int) b[0], (int) b[1], (int) (b[2] - b[0]), (int) (b[3] - b[1]), Math.round(radius * b[4]), thicknessPx, color);
		graphics.pose().popMatrix();
	}

	/** Ring in screen pixel space (pose must already be plain screen pixels). */
	private static void ring(GuiGraphicsExtractor graphics, int X, int Y, int W, int H, int radius, float thickness, int color) {
		if (W <= 0 || H <= 0) return;
		float T = Math.min(thickness, Math.min(W, H) / 2f);
		int R = Math.min(radius, Math.min(W, H) / 2);
		int full = (int) T;
		float frac = T - full;
		int fracColor = Math.round((color >>> 24) * frac) << 24 | color & 0xFFFFFF;
		int left = X + R;
		int right = X + W - R;
		int top = Y + R;
		int bottom = Y + H - R;
		if (full > 0) {
			fillPx(graphics, left, Y, right, Y + full, color);
			fillPx(graphics, left, Y + H - full, right, Y + H, color);
			fillPx(graphics, X, top, X + full, bottom, color);
			fillPx(graphics, X + W - full, top, X + W, bottom, color);
		}
		if (frac > 0.01f) {
			fillPx(graphics, left, Y + full, right, Y + full + 1, fracColor);
			fillPx(graphics, left, Y + H - full - 1, right, Y + H - full, fracColor);
			fillPx(graphics, X + full, top, X + full + 1, bottom, fracColor);
			fillPx(graphics, X + W - full - 1, top, X + W - full, bottom, fracColor);
		}
		if (T > R && R > 0) {
			int extra = (int) Math.ceil(T);
			fillPx(graphics, left, top, X + extra, Y + extra, color);
			fillPx(graphics, X + W - extra, top, right, Y + extra, color);
			fillPx(graphics, left, Y + H - extra, X + extra, bottom, color);
			fillPx(graphics, X + W - extra, Y + H - extra, right, bottom, color);
		}
		if (R > 0) {
			Identifier tex = corner(R, Math.round(T * 8) / 8f);
			int size = R * 2;
			graphics.blit(RenderPipelines.GUI_TEXTURED, tex, X, Y, 0, 0, R, R, size, size, color);
			graphics.blit(RenderPipelines.GUI_TEXTURED, tex, X + W - R, Y, R, 0, R, R, size, size, color);
			graphics.blit(RenderPipelines.GUI_TEXTURED, tex, X, Y + H - R, 0, R, R, R, size, size, color);
			graphics.blit(RenderPipelines.GUI_TEXTURED, tex, X + W - R, Y + H - R, R, R, R, R, size, size, color);
		}
	}

	/**
	 * Draws the border inside the box {@code x, y, width, height} (GUI units).
	 * {@code radius} and {@code thickness} are in GUI units too (thickness may be fractional).
	 */
	public static void draw(GuiGraphicsExtractor graphics, int x, int y, int width, int height, int radius, float thickness, int color) {
		float px = thickness * Theme.guiScale() * Math.abs(graphics.pose().m00());
		outlinePx(graphics, x, y, width, height, radius, px, color);
	}
}
