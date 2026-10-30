package dev.fancybars.util.gui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/**
 * Flat light-blue / grey theme used by all Fancy Bars screens.
 */
public final class Theme {
	// Surfaces
	public static final int BACKDROP = 0x99000000;
	public static final int PANEL = 0xF0202429;
	public static final int PANEL_BORDER = 0xFF343A42;
	public static final int SURFACE = 0xFF2C3138;
	public static final int SURFACE_HOVER = 0xFF383F48;
	public static final int SURFACE_DISABLED = 0xFF25292E;
	public static final int DIVIDER = 0xFF343A42;

	// Accent
	public static final int ACCENT = 0xFF7CC8FF;
	public static final int ACCENT_HOVER = 0xFF9DD6FF;
	public static final int ACCENT_DIM = 0xFF3D7FAF;
	public static final int ACCENT_TRANSLUCENT = 0x337CC8FF;

	// Text
	public static final int TEXT = 0xFFE8EDF2;
	public static final int TEXT_MUTED = 0xFF98A2AD;
	public static final int TEXT_DISABLED = 0xFF5E6670;
	public static final int TEXT_ON_ACCENT = 0xFF10202C;

	public static final int RADIUS = 4;

	private Theme() {}

	/**
	 * Fills a rectangle with rounded corners. Corners are approximated per pixel row so they stay crisp at any GUI scale.
	 */
	public static void roundedRect(GuiGraphicsExtractor graphics, int x, int y, int width, int height, int radius, int color) {
		if (width <= 0 || height <= 0) return;
		int r = Math.min(radius, Math.min(width, height) / 2);
		if (r <= 0) {
			graphics.fill(x, y, x + width, y + height, color);
			return;
		}
		// Middle block
		graphics.fill(x, y + r, x + width, y + height - r, color);
		// Top and bottom rows with corner insets
		for (int i = 0; i < r; i++) {
			int inset = cornerInset(r, i);
			graphics.fill(x + inset, y + i, x + width - inset, y + i + 1, color);
			graphics.fill(x + inset, y + height - i - 1, x + width - inset, y + height - i, color);
		}
	}

	/**
	 * Draws a rounded rectangle with a 1px border.
	 */
	public static void roundedRectBordered(GuiGraphicsExtractor graphics, int x, int y, int width, int height, int radius, int fill, int border) {
		roundedRect(graphics, x, y, width, height, radius, border);
		roundedRect(graphics, x + 1, y + 1, width - 2, height - 2, Math.max(0, radius - 1), fill);
	}

	private static int cornerInset(int r, int row) {
		double dy = r - row - 0.5;
		return (int) Math.round(r - Math.sqrt(Math.max(0, r * r - dy * dy)));
	}

	public static void panel(GuiGraphicsExtractor graphics, int x, int y, int width, int height) {
		roundedRectBordered(graphics, x, y, width, height, RADIUS + 2, PANEL, PANEL_BORDER);
	}

	public static void centeredText(GuiGraphicsExtractor graphics, Font font, Component text, int centerX, int y, int color) {
		graphics.text(font, text, centerX - font.width(text) / 2, y, color, false);
	}

	public static void text(GuiGraphicsExtractor graphics, Font font, Component text, int x, int y, int color) {
		graphics.text(font, text, x, y, color, false);
	}

	public static int lerpColor(int from, int to, float t) {
		t = Math.clamp(t, 0, 1);
		int a = (int) (((from >>> 24) & 0xFF) + (((to >>> 24) & 0xFF) - ((from >>> 24) & 0xFF)) * t);
		int r = (int) (((from >> 16) & 0xFF) + (((to >> 16) & 0xFF) - ((from >> 16) & 0xFF)) * t);
		int g = (int) (((from >> 8) & 0xFF) + (((to >> 8) & 0xFF) - ((from >> 8) & 0xFF)) * t);
		int b = (int) ((from & 0xFF) + ((to & 0xFF) - (from & 0xFF)) * t);
		return (a << 24) | (r << 16) | (g << 8) | b;
	}
}
