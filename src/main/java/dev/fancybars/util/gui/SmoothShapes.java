package dev.fancybars.util.gui;

import java.util.HashMap;
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
 * Shapes (circle, play, pause, skip) rendered into a texture at the exact on-screen pixel size with 4x4
 * supersampling, so they're perfectly smooth at any GUI scale instead of being a downscaled sprite.
 */
public final class SmoothShapes {
	public enum Shape {
		CIRCLE, PLAY, PAUSE, NEXT, PREVIOUS;

		boolean inside(float u, float v) {
			return switch (this) {
				case CIRCLE -> (u - 0.5f) * (u - 0.5f) + (v - 0.5f) * (v - 0.5f) <= 0.25f;
				case PLAY -> roundedTriangle(u, v, 0.2f, 0.1f, 0.2f, 0.9f, 0.9f, 0.5f);
				case PAUSE -> roundRect(u, v, 0.18f, 0.1f, 0.42f, 0.9f, 0.07f) || roundRect(u, v, 0.58f, 0.1f, 0.82f, 0.9f, 0.07f);
				case NEXT -> roundedTriangle(u, v, 0.1f, 0.12f, 0.1f, 0.88f, 0.72f, 0.5f) || roundRect(u, v, 0.72f, 0.12f, 0.88f, 0.88f, 0.05f);
				case PREVIOUS -> NEXT.inside(1 - u, v);
			};
		}
	}

	private static final Map<Long, Identifier> CACHE = new HashMap<>();
	private static int counter;

	private SmoothShapes() {}

	private static boolean roundRect(float u, float v, float x0, float y0, float x1, float y1, float r) {
		if (u < x0 || u > x1 || v < y0 || v > y1) return false;
		float cx = Math.clamp(u, x0 + r, x1 - r);
		float cy = Math.clamp(v, y0 + r, y1 - r);
		return (u - cx) * (u - cx) + (v - cy) * (v - cy) <= r * r;
	}

	private static float edge(float ax, float ay, float bx, float by, float px, float py) {
		return (bx - ax) * (py - ay) - (by - ay) * (px - ax);
	}

	private static boolean roundedTriangle(float u, float v, float ax, float ay, float bx, float by, float cx, float cy) {
		float e1 = edge(ax, ay, bx, by, u, v);
		float e2 = edge(bx, by, cx, cy, u, v);
		float e3 = edge(cx, cy, ax, ay, u, v);
		return (e1 >= 0 && e2 >= 0 && e3 >= 0) || (e1 <= 0 && e2 <= 0 && e3 <= 0);
	}

	private static Identifier texture(Shape shape, int pixels) {
		long key = (long) shape.ordinal() << 32 | pixels;
		Identifier id = CACHE.get(key);
		if (id != null) return id;
		NativeImage image = new NativeImage(pixels, pixels, false);
		for (int y = 0; y < pixels; y++) {
			for (int x = 0; x < pixels; x++) {
				int hits = 0;
				for (int sy = 0; sy < 4; sy++) {
					for (int sx = 0; sx < 4; sx++) {
						if (shape.inside((x + (sx + 0.5f) / 4f) / pixels, (y + (sy + 0.5f) / 4f) / pixels)) hits++;
					}
				}
				image.setPixel(x, y, Math.round(hits / 16f * 255) << 24 | 0xFFFFFF);
			}
		}
		id = FancyBarsMod.id("ui/shape_" + counter++);
		DynamicTexture texture = new DynamicTexture(() -> "SBCLIENT shape", image);
		Minecraft.getInstance().getTextureManager().register(id, texture);
		texture.upload();
		CACHE.put(key, id);
		return id;
	}

	/** Draws {@code shape} filling the square at {@code x, y} (GUI units, fractional allowed) of side {@code size}. */
	public static void draw(GuiGraphicsExtractor graphics, Shape shape, float x, float y, float size, int color) {
		if (size <= 0 || (color >>> 24) == 0) return;
		Matrix3x2fStack pose = graphics.pose();
		float worldScale = Math.max(0.05f, (float) Math.hypot(pose.m00(), pose.m01()));
		int pixels = Math.clamp(Math.round(size * Theme.guiScale() * worldScale), 2, 512);
		Identifier tex = texture(shape, pixels);
		if (Math.abs(worldScale - 1) < 0.001f) {
			// line the texture up with real screen pixels so nothing gets resampled
			float gs = Theme.guiScale();
			x = Math.round(x * gs) / gs;
			y = Math.round(y * gs) / gs;
		}
		pose.pushMatrix();
		pose.translate(x, y);
		pose.scale(size / pixels, size / pixels);
		graphics.blit(RenderPipelines.GUI_TEXTURED, tex, 0, 0, 0, 0, pixels, pixels, pixels, pixels, color);
		pose.popMatrix();
	}
}
