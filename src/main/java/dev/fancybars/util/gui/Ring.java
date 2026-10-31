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
		if (CORNERS.size() > 48) {
			var it = CORNERS.entrySet().iterator();
			Identifier old = it.next().getValue();
			it.remove();
			Minecraft.getInstance().getTextureManager().release(old);
		}
		return id;
	}

	/**
	 * Draws the border inside the box {@code x, y, width, height} (GUI units).
	 * {@code radius} and {@code thickness} are in GUI units too (thickness may be fractional).
	 */
	public static void draw(GuiGraphicsExtractor graphics, int x, int y, int width, int height, int radius, float thickness, int color) {
		if (width <= 0 || height <= 0 || thickness <= 0 || (color >>> 24) == 0) return;
		float gs = Theme.guiScale();
		int X = Math.round(x * gs);
		int Y = Math.round(y * gs);
		int W = Math.round(width * gs);
		int H = Math.round(height * gs);
		float T = Math.min(thickness * gs, Math.min(W, H) / 2f);
		int R = Math.min(Math.round(radius * gs), Math.min(W, H) / 2);
		int full = (int) T;
		float frac = T - full;
		int fracColor = Math.round((color >>> 24) * frac) << 24 | color & 0xFFFFFF;

		Matrix3x2fStack pose = graphics.pose();
		pose.pushMatrix();
		pose.scale(1 / gs, 1 / gs);

		// straight edges (top/bottom between the corners, left/right between the corners)
		int left = X + R;
		int right = X + W - R;
		int top = Y + R;
		int bottom = Y + H - R;
		if (full > 0) {
			graphics.fill(left, Y, right, Y + full, color);
			graphics.fill(left, Y + H - full, right, Y + H, color);
			graphics.fill(X, top, X + full, bottom, color);
			graphics.fill(X + W - full, top, X + W, bottom, color);
		}
		if (frac > 0.01f) {
			graphics.fill(left, Y + full, right, Y + full + 1, fracColor);
			graphics.fill(left, Y + H - full - 1, right, Y + H - full, fracColor);
			graphics.fill(X + full, top, X + full + 1, bottom, fracColor);
			graphics.fill(X + W - full - 1, top, X + W - full, bottom, fracColor);
		}
		// thickness bigger than the corner: fill the inside corner squares so there are no gaps
		if (T > R && R > 0) {
			int extra = (int) Math.ceil(T);
			graphics.fill(left, top, X + extra, Y + extra, color);
			graphics.fill(X + W - extra, top, right, Y + extra, color);
			graphics.fill(left, Y + H - extra, X + extra, bottom, color);
			graphics.fill(X + W - extra, Y + H - extra, right, bottom, color);
		}

		// corners
		if (R > 0) {
			Identifier tex = corner(R, Math.round(T * 8) / 8f);
			int size = R * 2;
			graphics.blit(RenderPipelines.GUI_TEXTURED, tex, X, Y, 0, 0, R, R, size, size, color);
			graphics.blit(RenderPipelines.GUI_TEXTURED, tex, X + W - R, Y, R, 0, R, R, size, size, color);
			graphics.blit(RenderPipelines.GUI_TEXTURED, tex, X, Y + H - R, 0, R, R, R, size, size, color);
			graphics.blit(RenderPipelines.GUI_TEXTURED, tex, X + W - R, Y + H - R, R, R, R, R, size, size, color);
		}
		pose.popMatrix();
	}
}
