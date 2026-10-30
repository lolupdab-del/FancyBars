package dev.fancybars.util.gui;

import org.joml.Matrix3x2fStack;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;

import dev.fancybars.FancyBarsMod;

/**
 * Fancy Bars UI theme. Everything is drawn from high-resolution anti-aliased textures (rounded rectangles,
 * outlines, circles, shadows) and the Inter TrueType font, so nothing is aligned to the chunky GUI pixel grid.
 */
public final class Theme {
	// Surfaces (dark navy-grey, like modern mod menus)
	public static final int BACKDROP = 0x66000000;
	public static final int PANEL = 0xEE181A27;
	public static final int PANEL_TOP = 0xEE1C1E2D;
	public static final int PANEL_BOTTOM = 0xEE161824;
	public static final int PANEL_BORDER = 0xFF2E3149;
	public static final int CARD = 0xCC202233;
	public static final int CARD_BORDER = 0xFF33364F;
	public static final int CARD_HOVER = 0xE0272A3F;
	public static final int SURFACE = 0xFF252839;
	public static final int SURFACE_HOVER = 0xFF2E3247;
	public static final int SURFACE_DISABLED = 0xFF1D1F2B;
	public static final int SELECTED = 0xFF363A5A;
	public static final int SELECTED_BORDER = 0xFF7C82C4;
	public static final int DIVIDER = 0xFF2A2D42;

	// Accent (light blue)
	public static final int ACCENT = 0xFF7CC8FF;
	public static final int ACCENT_LIGHT = 0xFFB4E1FF;
	public static final int ACCENT_DEEP = 0xFF3E8FD6;
	public static final int ACCENT_HOVER = 0xFF9DD6FF;
	public static final int ACCENT_DIM = 0xFF3D7FAF;

	// Text
	public static final int TEXT = 0xFFEDEEF7;
	public static final int TEXT_SOFT = 0xFFD6D8EE;
	public static final int TEXT_MUTED = 0xFF9EA2C2;
	public static final int TEXT_DISABLED = 0xFF5C6079;
	public static final int TEXT_ON_ACCENT = 0xFF0B1B29;

	public static final int RADIUS = 4;
	public static final int PANEL_RADIUS = 6;

	public static final FontDescription FONT = new FontDescription.Resource(FancyBarsMod.id("ui"));
	public static final FontDescription FONT_BOLD = new FontDescription.Resource(FancyBarsMod.id("ui_bold"));
	public static final FontDescription FONT_TITLE = new FontDescription.Resource(FancyBarsMod.id("title"));

	private static final int[] RADII = {2, 3, 4, 5, 6, 8, 10};
	private static final Identifier[] RECTS = new Identifier[RADII.length];
	private static final Identifier[] OUTLINES = new Identifier[RADII.length];
	private static final Identifier CIRCLE = FancyBarsMod.id("ui/circle");
	private static final Identifier SHADOW = FancyBarsMod.id("ui/shadow");
	private static final Identifier SHEEN = FancyBarsMod.id("ui/sheen");

	static {
		for (int i = 0; i < RADII.length; i++) {
			RECTS[i] = FancyBarsMod.id("ui/rect_r" + RADII[i]);
			OUTLINES[i] = FancyBarsMod.id("ui/outline_r" + RADII[i]);
		}
	}

	private Theme() {}

	// ------------------------------------------------------------ Text

	public static MutableComponent styled(Component text) {
		return text.copy().withStyle(style -> style.withFont(FONT));
	}

	public static MutableComponent bold(Component text) {
		return text.copy().withStyle(style -> style.withFont(FONT_BOLD));
	}

	public static MutableComponent title(Component text) {
		return text.copy().withStyle(style -> style.withFont(FONT_TITLE));
	}

	public static int width(Font font, Component text) {
		return font.width(styled(text));
	}

	public static void text(GuiGraphicsExtractor graphics, Font font, Component text, int x, int y, int color) {
		graphics.text(font, styled(text), x, y, color, false);
	}

	public static void boldText(GuiGraphicsExtractor graphics, Font font, Component text, int x, int y, int color) {
		graphics.text(font, bold(text), x, y, color, false);
	}

	public static void centeredText(GuiGraphicsExtractor graphics, Font font, Component text, int centerX, int y, int color) {
		Component s = styled(text);
		graphics.text(font, s, centerX - font.width(s) / 2, y, color, false);
	}

	public static void centeredBoldText(GuiGraphicsExtractor graphics, Font font, Component text, int centerX, int y, int color) {
		Component s = bold(text);
		graphics.text(font, s, centerX - font.width(s) / 2, y, color, false);
	}

	/**
	 * Large bold title text (uses the large Inter Bold font, not scaling, so it stays sharp).
	 */
	public static void bigText(GuiGraphicsExtractor graphics, Font font, Component text, int x, int y, float scale, int color) {
		graphics.text(font, title(text), x, y, color, false);
	}

	public static int titleWidth(Font font, Component text) {
		return font.width(title(text));
	}

	// ------------------------------------------------------------ Shapes

	private static int radiusIndex(int radius, int width, int height) {
		int max = Math.min(width, height) / 2;
		int r = Math.min(radius, max);
		int best = 0;
		for (int i = 0; i < RADII.length; i++) {
			if (RADII[i] <= r) best = i;
		}
		return best;
	}

	/**
	 * Smooth anti-aliased rounded rectangle.
	 */
	public static void roundedRect(GuiGraphicsExtractor graphics, int x, int y, int width, int height, int radius, int color) {
		if (width <= 0 || height <= 0 || (color >>> 24) == 0) return;
		if (radius <= 1 || Math.min(width, height) < 4) {
			graphics.fill(x, y, x + width, y + height, color);
			return;
		}
		graphics.blitSprite(RenderPipelines.GUI_TEXTURED, RECTS[radiusIndex(radius, width, height)], x, y, width, height, color);
	}

	/**
	 * Rounded rectangle with a soft top-to-bottom gradient.
	 */
	public static void roundedGradient(GuiGraphicsExtractor graphics, int x, int y, int width, int height, int radius, int top, int bottom) {
		roundedRect(graphics, x, y, width, height, radius, bottom);
		int r = RADII[radiusIndex(radius, width, height)];
		// sheen overlay (kept inside the straight part so it never pokes out of the corners)
		int sheenColor = top;
		if (r * 2 < width) {
			graphics.blitSprite(RenderPipelines.GUI_TEXTURED, SHEEN, x + r, y, width - r * 2, height, sheenColor);
			graphics.blitSprite(RenderPipelines.GUI_TEXTURED, SHEEN, x, y + r, r, height - r, sheenColor);
			graphics.blitSprite(RenderPipelines.GUI_TEXTURED, SHEEN, x + width - r, y + r, r, height - r, sheenColor);
		}
	}

	/**
	 * Smooth thin rounded outline.
	 */
	public static void roundedOutline(GuiGraphicsExtractor graphics, int x, int y, int width, int height, int radius, int color) {
		if (width <= 0 || height <= 0 || (color >>> 24) == 0) return;
		graphics.blitSprite(RenderPipelines.GUI_TEXTURED, OUTLINES[radiusIndex(radius, width, height)], x, y, width, height, color);
	}

	/**
	 * Card: filled rounded rectangle with a thin border.
	 */
	public static void card(GuiGraphicsExtractor graphics, int x, int y, int width, int height, int radius, int fill, int border) {
		roundedRect(graphics, x, y, width, height, radius, fill);
		roundedOutline(graphics, x, y, width, height, radius, border);
	}

	public static void circle(GuiGraphicsExtractor graphics, int x, int y, int size, int color) {
		graphics.blitSprite(RenderPipelines.GUI_TEXTURED, CIRCLE, x, y, size, size, color);
	}

	/**
	 * Soft blurred drop shadow.
	 */
	public static void shadow(GuiGraphicsExtractor graphics, int x, int y, int width, int height, int radius, int size, int offsetY) {
		int pad = 16;
		graphics.blitSprite(RenderPipelines.GUI_TEXTURED, SHADOW, x - pad, y - pad + offsetY, width + pad * 2, height + pad * 2, 0x99000000);
	}

	/**
	 * Soft light-blue glow around a rectangle, strength 0..1.
	 */
	public static void glow(GuiGraphicsExtractor graphics, int x, int y, int width, int height, int radius, float strength) {
		if (strength <= 0.01f) return;
		int pad = 14;
		int alpha = Math.round(0xAA * Math.clamp(strength, 0, 1));
		graphics.blitSprite(RenderPipelines.GUI_TEXTURED, SHADOW, x - pad, y - pad, width + pad * 2, height + pad * 2, (alpha << 24) | (ACCENT & 0xFFFFFF));
	}

	/**
	 * Main panel: shadow, translucent navy body and thin border.
	 */
	public static void panel(GuiGraphicsExtractor graphics, int x, int y, int width, int height) {
		shadow(graphics, x, y, width, height, PANEL_RADIUS, 0, 2);
		roundedRect(graphics, x, y, width, height, PANEL_RADIUS, PANEL);
		roundedOutline(graphics, x, y, width, height, PANEL_RADIUS, PANEL_BORDER);
	}

	public static void backdrop(GuiGraphicsExtractor graphics, int width, int height) {
		graphics.fill(0, 0, width, height, BACKDROP);
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
	 * Slide-in offset for screens opening, in GUI pixels (fractional so the motion is smooth).
	 */
	public static float openOffset(long openedAt) {
		float t = (Util.getMillis() - openedAt) / 260f;
		return (1 - easeOutCubic(t)) * 12;
	}

	public static float openAlpha(long openedAt) {
		return easeOutCubic((Util.getMillis() - openedAt) / 200f);
	}

	public static void pushOffset(GuiGraphicsExtractor graphics, float dy) {
		Matrix3x2fStack pose = graphics.pose();
		pose.pushMatrix();
		pose.translate(0, dy);
	}

	public static void popOffset(GuiGraphicsExtractor graphics) {
		graphics.pose().popMatrix();
	}

	public static Font font() {
		return Minecraft.getInstance().font;
	}
}
