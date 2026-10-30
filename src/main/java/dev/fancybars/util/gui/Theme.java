package dev.fancybars.util.gui;

import org.joml.Matrix3x2fStack;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.Util;

import dev.fancybars.FancyBarsMod;

/**
 * Fancy Bars UI theme: frosted dark-grey panels, light-blue accents, soft shadows and glow,
 * and the rounded Jua font for smooth bold text.
 */
public final class Theme {
	// Surfaces
	public static final int BACKDROP_TOP = 0xB0081018;
	public static final int BACKDROP_BOTTOM = 0xC0000306;
	public static final int PANEL_TOP = 0xF52A3038;
	public static final int PANEL_BOTTOM = 0xF5191C21;
	public static final int PANEL_BORDER = 0x40FFFFFF;
	public static final int SURFACE = 0xFF313842;
	public static final int SURFACE_HOVER = 0xFF3C4552;
	public static final int SURFACE_DISABLED = 0xFF23272D;
	public static final int CARD = 0x26FFFFFF;
	public static final int CARD_HOVER = 0x407CC8FF;
	public static final int DIVIDER = 0x22FFFFFF;

	// Accent (light blue)
	public static final int ACCENT = 0xFF7CC8FF;
	public static final int ACCENT_LIGHT = 0xFFB4E1FF;
	public static final int ACCENT_DEEP = 0xFF3E8FD6;
	public static final int ACCENT_HOVER = 0xFF9DD6FF;
	public static final int ACCENT_DIM = 0xFF3D7FAF;
	public static final int ACCENT_GLOW = 0x557CC8FF;

	// Text
	public static final int TEXT = 0xFFF1F5F9;
	public static final int TEXT_MUTED = 0xFF9AA5B1;
	public static final int TEXT_DISABLED = 0xFF5E6670;
	public static final int TEXT_ON_ACCENT = 0xFF0B1B29;

	public static final int RADIUS = 5;
	public static final int PANEL_RADIUS = 9;

	/** Rounded bold font used for all Fancy Bars UI text. */
	public static final FontDescription FONT = new FontDescription.Resource(FancyBarsMod.id("jua"));

	private Theme() {}

	// ------------------------------------------------------------ Text

	public static MutableComponent styled(Component text) {
		return text.copy().withStyle(style -> style.withFont(FONT));
	}

	public static int width(Font font, Component text) {
		return font.width(styled(text));
	}

	public static void text(GuiGraphicsExtractor graphics, Font font, Component text, int x, int y, int color) {
		graphics.text(font, styled(text), x, y, color, false);
	}

	public static void textShadowed(GuiGraphicsExtractor graphics, Font font, Component text, int x, int y, int color) {
		graphics.text(font, styled(text), x + 1, y + 1, 0x60000000, false);
		graphics.text(font, styled(text), x, y, color, false);
	}

	public static void centeredText(GuiGraphicsExtractor graphics, Font font, Component text, int centerX, int y, int color) {
		Component s = styled(text);
		graphics.text(font, s, centerX - font.width(s) / 2, y, color, false);
	}

	/**
	 * Draws large text (e.g. screen titles) scaled up around its top left corner.
	 */
	public static void bigText(GuiGraphicsExtractor graphics, Font font, Component text, int x, int y, float scale, int color) {
		Matrix3x2fStack pose = graphics.pose();
		pose.pushMatrix();
		pose.translate(x, y);
		pose.scale(scale, scale);
		graphics.text(font, styled(text), 1, 1, 0x70000000, false);
		graphics.text(font, styled(text), 0, 0, color, false);
		pose.popMatrix();
	}

	// ------------------------------------------------------------ Shapes

	/**
	 * Fills a rectangle with rounded corners. Corners are computed per pixel row so they stay crisp at any GUI scale.
	 */
	public static void roundedRect(GuiGraphicsExtractor graphics, int x, int y, int width, int height, int radius, int color) {
		if (width <= 0 || height <= 0 || (color >>> 24) == 0) return;
		int r = Math.min(radius, Math.min(width, height) / 2);
		if (r <= 0) {
			graphics.fill(x, y, x + width, y + height, color);
			return;
		}
		graphics.fill(x, y + r, x + width, y + height - r, color);
		for (int i = 0; i < r; i++) {
			int inset = cornerInset(r, i);
			graphics.fill(x + inset, y + i, x + width - inset, y + i + 1, color);
			graphics.fill(x + inset, y + height - i - 1, x + width - inset, y + height - i, color);
		}
	}

	/**
	 * Rounded rectangle with a vertical gradient.
	 */
	public static void roundedGradient(GuiGraphicsExtractor graphics, int x, int y, int width, int height, int radius, int top, int bottom) {
		if (width <= 0 || height <= 0) return;
		int r = Math.min(radius, Math.min(width, height) / 2);
		for (int i = 0; i < height; i++) {
			int inset = 0;
			if (i < r) inset = cornerInset(r, i);
			else if (i >= height - r) inset = cornerInset(r, height - 1 - i);
			int color = lerpColor(top, bottom, height <= 1 ? 0 : (float) i / (height - 1));
			graphics.fill(x + inset, y + i, x + width - inset, y + i + 1, color);
		}
	}

	/**
	 * 1px rounded outline.
	 */
	public static void roundedOutline(GuiGraphicsExtractor graphics, int x, int y, int width, int height, int radius, int color) {
		if (width <= 0 || height <= 0) return;
		int r = Math.min(radius, Math.min(width, height) / 2);
		int top = r > 0 ? cornerInset(r, 0) : 0;
		// top and bottom edges
		graphics.fill(x + top, y, x + width - top, y + 1, color);
		graphics.fill(x + top, y + height - 1, x + width - top, y + height, color);
		// left and right edges
		graphics.fill(x, y + r, x + 1, y + height - r, color);
		graphics.fill(x + width - 1, y + r, x + width, y + height - r, color);
		// corner arcs: each row connects to the previous row's inset
		for (int i = 1; i < r; i++) {
			int inset = cornerInset(r, i);
			int prev = cornerInset(r, i - 1);
			int end = Math.max(inset + 1, prev);
			graphics.fill(x + inset, y + i, x + end, y + i + 1, color);
			graphics.fill(x + width - end, y + i, x + width - inset, y + i + 1, color);
			graphics.fill(x + inset, y + height - i - 1, x + end, y + height - i, color);
			graphics.fill(x + width - end, y + height - i - 1, x + width - inset, y + height - i, color);
		}
		if (r > 0) {
			// last arc row joins the straight side edges
			int last = cornerInset(r, r - 1);
			if (last > 0) {
				graphics.fill(x, y + r - 1, x + last, y + r, color);
				graphics.fill(x + width - last, y + r - 1, x + width, y + r, color);
				graphics.fill(x, y + height - r, x + last, y + height - r + 1, color);
				graphics.fill(x + width - last, y + height - r, x + width, y + height - r + 1, color);
			}
		}
	}

	/**
	 * Soft drop shadow made of stacked translucent rounded rectangles.
	 */
	public static void shadow(GuiGraphicsExtractor graphics, int x, int y, int width, int height, int radius, int size, int offsetY) {
		for (int i = size; i >= 1; i--) {
			int alpha = (int) (0x30 * (1 - (float) i / (size + 1)) * 0.6f);
			roundedRect(graphics, x - i, y - i + offsetY, width + i * 2, height + i * 2, radius + i, alpha << 24);
		}
	}

	/**
	 * Light-blue glow around a rectangle, strength 0..1.
	 */
	public static void glow(GuiGraphicsExtractor graphics, int x, int y, int width, int height, int radius, float strength) {
		if (strength <= 0.01f) return;
		for (int i = 3; i >= 1; i--) {
			int alpha = (int) (0x38 * strength * (1 - (i - 1) / 3f));
			roundedRect(graphics, x - i, y - i, width + i * 2, height + i * 2, radius + i, (alpha << 24) | (ACCENT & 0xFFFFFF));
		}
	}

	/**
	 * The main frosted panel: shadow, gradient body, subtle top highlight and a thin border.
	 */
	public static void panel(GuiGraphicsExtractor graphics, int x, int y, int width, int height) {
		shadow(graphics, x, y, width, height, PANEL_RADIUS, 8, 3);
		roundedRect(graphics, x - 1, y - 1, width + 2, height + 2, PANEL_RADIUS + 1, PANEL_BORDER);
		roundedGradient(graphics, x, y, width, height, PANEL_RADIUS, PANEL_TOP, PANEL_BOTTOM);
		// inner top highlight
		graphics.fill(x + PANEL_RADIUS, y + 1, x + width - PANEL_RADIUS, y + 2, 0x18FFFFFF);
	}

	/**
	 * Full-screen dimmed gradient backdrop with a faint light-blue vignette at the top.
	 */
	public static void backdrop(GuiGraphicsExtractor graphics, int width, int height) {
		graphics.fillGradient(0, 0, width, height, BACKDROP_TOP, BACKDROP_BOTTOM);
		graphics.fillGradient(0, 0, width, height / 3, 0x147CC8FF, 0x007CC8FF);
	}

	private static int cornerInset(int r, int row) {
		double dy = r - row - 0.5;
		return (int) Math.round(r - Math.sqrt(Math.max(0, r * r - dy * dy)));
	}

	// ------------------------------------------------------------ Colour / animation helpers

	public static int lerpColor(int from, int to, float t) {
		t = Math.clamp(t, 0, 1);
		int a = (int) (((from >>> 24) & 0xFF) + (((to >>> 24) & 0xFF) - ((from >>> 24) & 0xFF)) * t);
		int r = (int) (((from >> 16) & 0xFF) + (((to >> 16) & 0xFF) - ((from >> 16) & 0xFF)) * t);
		int g = (int) (((from >> 8) & 0xFF) + (((to >> 8) & 0xFF) - ((from >> 8) & 0xFF)) * t);
		int b = (int) ((from & 0xFF) + ((to & 0xFF) - (from & 0xFF)) * t);
		return (a << 24) | (r << 16) | (g << 8) | b;
	}

	public static int withAlpha(int color, float alpha) {
		int a = (int) (((color >>> 24) & 0xFF) * Math.clamp(alpha, 0, 1));
		return (a << 24) | (color & 0xFFFFFF);
	}

	public static float easeOutCubic(float t) {
		t = Math.clamp(t, 0, 1);
		float f = 1 - t;
		return 1 - f * f * f;
	}

	/**
	 * Moves {@code current} towards {@code target} at a frame-rate independent speed.
	 */
	public static float approach(float current, float target, float speedPerSecond, long lastFrameMillis) {
		float dt = Math.min(0.1f, (Util.getMillis() - lastFrameMillis) / 1000f);
		float step = speedPerSecond * dt;
		if (current < target) return Math.min(target, current + step);
		return Math.max(target, current - step);
	}

	/**
	 * Slide-in offset for screens opening, in GUI pixels.
	 */
	public static float openOffset(long openedAt) {
		float t = (Util.getMillis() - openedAt) / 260f;
		return (1 - easeOutCubic(t)) * 14;
	}

	public static Font font() {
		return Minecraft.getInstance().font;
	}
}
