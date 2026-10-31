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
	public static final int PANEL = 0xEE271A1F;
	public static final int PANEL_TOP = 0xEE2D1E24;
	public static final int PANEL_BOTTOM = 0xEE24181D;
	public static final int PANEL_BORDER = 0xFF49313B;
	public static final int CARD = 0xCC332229;
	public static final int CARD_BORDER = 0xFF4F3641;
	public static final int CARD_HOVER = 0xE03F2A33;
	public static final int SURFACE = 0xFF39282F;
	public static final int SURFACE_HOVER = 0xFF47323A;
	public static final int SURFACE_DISABLED = 0xFF2B1F24;
	public static final int SELECTED = 0xFF5A3A48;
	public static final int SELECTED_BORDER = 0xFFC47CA6;
	public static final int DIVIDER = 0xFF422D36;

	// Accent (light blue)
	public static final int ACCENT = 0xFFFF8FC8;
	public static final int ACCENT_LIGHT = 0xFFFFC4E1;
	public static final int ACCENT_DEEP = 0xFFD6448F;
	public static final int ACCENT_HOVER = 0xFFFFAED6;
	public static final int ACCENT_DIM = 0xFFAF3D78;

	// Text
	public static final int TEXT = 0xFFF7EEF2;
	public static final int TEXT_SOFT = 0xFFEED8E2;
	public static final int TEXT_MUTED = 0xFFC2A2B0;
	public static final int TEXT_DISABLED = 0xFF79606A;
	public static final int TEXT_ON_ACCENT = 0xFF2A0B1B;

	public static final int RADIUS = 4;
	public static final int PANEL_RADIUS = 6;

	/** Inter cap height as a fraction of the TrueType "size" (stb scales so ascent - descent = size). */
	private static final float CAP_RATIO = 0.6013f;
	/** Minecraft puts the text baseline 7 GUI px below the y you draw at. */
	private static final float BASELINE = 7f;

	public static final float UI_SIZE = 10f;
	public static final float TITLE_SIZE = 16f;
	public static final float BAR_SIZE = 9.5f;
	public static final float UI_CAP = UI_SIZE * CAP_RATIO;
	public static final float TITLE_CAP = TITLE_SIZE * CAP_RATIO;
	public static final float BAR_CAP = BAR_SIZE * CAP_RATIO;

	private static final FontDescription[] UI_FONTS = fonts("ui");
	private static final FontDescription[] BOLD_FONTS = fonts("bold");
	private static final FontDescription[] TITLE_FONTS = fonts("title");
	private static final FontDescription[] BAR_FONTS = fonts("bar");

	/** Kept for code that wants "the" UI font; resolves to the variant for the current GUI scale. */
	public static final FontDescription FONT = UI_FONTS[3];

	private static FontDescription[] fonts(String kind) {
		FontDescription[] out = new FontDescription[7];
		for (int i = 1; i <= 6; i++) out[i] = new FontDescription.Resource(FancyBarsMod.id(kind + "_s" + i));
		out[0] = out[1];
		return out;
	}

	/**
	 * Each font is rasterised once per GUI scale (oversample = scale) so glyph pixels map 1:1 to screen pixels.
	 */
	public static int guiScale() {
		int scale = Minecraft.getInstance().getWindow().getGuiScale();
		return Math.clamp(scale, 1, 6);
	}

	public static FontDescription uiFont() {
		return UI_FONTS[guiScale()];
	}

	public static FontDescription boldFont() {
		return BOLD_FONTS[guiScale()];
	}

	public static FontDescription titleFont() {
		return TITLE_FONTS[guiScale()];
	}

	public static FontDescription barFont() {
		return BAR_FONTS[guiScale()];
	}

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
		FontDescription font = uiFont();
		return text.copy().withStyle(style -> style.withFont(font));
	}

	public static MutableComponent bold(Component text) {
		FontDescription font = boldFont();
		return text.copy().withStyle(style -> style.withFont(font));
	}

	public static MutableComponent title(Component text) {
		FontDescription font = titleFont();
		return text.copy().withStyle(style -> style.withFont(font));
	}

	public static int width(Font font, Component text) {
		return font.width(styled(text));
	}

	public static int boldWidth(Font font, Component text) {
		return font.width(bold(text));
	}

	public static int titleWidth(Font font, Component text) {
		return font.width(title(text));
	}

	/**
	 * Draws already-styled text so its capital letters start exactly at {@code capTop},
	 * snapped to the physical pixel grid so it stays crisp.
	 */
	public static void drawAtCapTop(GuiGraphicsExtractor graphics, Font font, Component styledText, float x, float capTop, float capHeight, int color) {
		drawAtCapTop(graphics, font, styledText, x, capTop, capHeight, color, false);
	}

	public static void drawAtCapTop(GuiGraphicsExtractor graphics, Font font, Component styledText, float x, float capTop, float capHeight, int color, boolean shadow) {
		float scale = guiScale();
		float originY = capTop + capHeight - BASELINE;
		float sx = Math.round(x * scale) / scale;
		float sy = Math.round(originY * scale) / scale;
		Matrix3x2fStack pose = graphics.pose();
		pose.pushMatrix();
		pose.translate(sx, sy);
		graphics.text(font, styledText, 0, 0, night(color), shadow);
		pose.popMatrix();
	}

	/** A pre-split line of (already styled) text vertically centred in a box. */
	public static void drawSeqInBox(GuiGraphicsExtractor graphics, Font font, net.minecraft.util.FormattedCharSequence line, float x, float boxY, float boxHeight, int color) {
		float scale = guiScale();
		float originY = boxY + (boxHeight - UI_CAP) / 2f + UI_CAP - BASELINE;
		Matrix3x2fStack pose = graphics.pose();
		pose.pushMatrix();
		pose.translate(Math.round(x * scale) / scale, Math.round(originY * scale) / scale);
		graphics.text(font, line, 0, 0, color, false);
		pose.popMatrix();
	}

	/** Regular text vertically centred in a box. */
	public static void textInBox(GuiGraphicsExtractor graphics, Font font, Component text, float x, float boxY, float boxHeight, int color) {
		drawAtCapTop(graphics, font, styled(text), x, boxY + (boxHeight - UI_CAP) / 2f, UI_CAP, color);
	}

	public static void boldInBox(GuiGraphicsExtractor graphics, Font font, Component text, float x, float boxY, float boxHeight, int color) {
		drawAtCapTop(graphics, font, bold(text), x, boxY + (boxHeight - UI_CAP) / 2f, UI_CAP, color);
	}

	public static void titleInBox(GuiGraphicsExtractor graphics, Font font, Component text, float x, float boxY, float boxHeight, int color) {
		drawAtCapTop(graphics, font, title(text), x, boxY + (boxHeight - TITLE_CAP) / 2f, TITLE_CAP, color);
	}

	/** Regular text centred horizontally on {@code centerX} and vertically in a box. */
	public static void centeredInBox(GuiGraphicsExtractor graphics, Font font, Component text, float centerX, float boxY, float boxHeight, int color) {
		MutableComponent s = styled(text);
		drawAtCapTop(graphics, font, s, centerX - font.width(s) / 2f, boxY + (boxHeight - UI_CAP) / 2f, UI_CAP, color);
	}

	/**
	 * Regular text on an 8px line starting at {@code y} (the same slot Minecraft's own text would use), centred in that line.
	 */
	public static void text(GuiGraphicsExtractor graphics, Font font, Component text, int x, int y, int color) {
		textInBox(graphics, font, text, x, y, 8, color);
	}

	public static void boldText(GuiGraphicsExtractor graphics, Font font, Component text, int x, int y, int color) {
		boldInBox(graphics, font, text, x, y, 8, color);
	}

	public static void centeredText(GuiGraphicsExtractor graphics, Font font, Component text, int centerX, int y, int color) {
		centeredInBox(graphics, font, text, centerX, y, 8, color);
	}

	public static void centeredBoldText(GuiGraphicsExtractor graphics, Font font, Component text, int centerX, int y, int color) {
		MutableComponent s = bold(text);
		drawAtCapTop(graphics, font, s, centerX - font.width(s) / 2f, y + (8 - UI_CAP) / 2f, UI_CAP, color);
	}

	/**
	 * Title text whose capitals start at {@code y}.
	 */
	public static void bigText(GuiGraphicsExtractor graphics, Font font, Component text, int x, int y, float unused, int color) {
		drawAtCapTop(graphics, font, title(text), x, y, TITLE_CAP, color);
	}

	// ------------------------------------------------------------ Shapes

	private static int radiusIndex(int radius, int width, int height) {
		radius = radius(radius);
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
		if (radius(radius) <= 1 || Math.min(width, height) < 4) {
			graphics.fill(x, y, x + width, y + height, night(color));
			return;
		}
		graphics.blitSprite(RenderPipelines.GUI_TEXTURED, RECTS[radiusIndex(radius, width, height)], x, y, width, height, night(color));
	}

	/**
	 * Rounded rectangle with a soft top-to-bottom gradient.
	 */
	public static void roundedGradient(GuiGraphicsExtractor graphics, int x, int y, int width, int height, int radius, int top, int bottom) {
		roundedRect(graphics, x, y, width, height, radius, bottom);
		int r = RADII[radiusIndex(radius, width, height)];
		// sheen overlay (kept inside the straight part so it never pokes out of the corners)
		int sheenColor = night(top);
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
		graphics.blitSprite(RenderPipelines.GUI_TEXTURED, OUTLINES[radiusIndex(radius, width, height)], x, y, width, height, night(color));
	}

	/**
	 * Card: filled rounded rectangle with a thin border.
	 */
	/** Outline drawn exactly in the given colour (no theme mapping). */
	public static void rawOutline(GuiGraphicsExtractor graphics, int x, int y, int width, int height, int radius, int color) {
		if (width <= 0 || height <= 0 || (color >>> 24) == 0) return;
		graphics.blitSprite(RenderPipelines.GUI_TEXTURED, OUTLINES[radiusIndex(Math.max(0, radius), width, height)], x, y, width, height, color);
	}

	/**
	 * Border from the Appearance settings (thickness + colour). {@code fallback} is the element's normal border
	 * colour, used when Custom Border Color is off. {@code alpha} fades the whole border (HUD fade-outs).
	 */
	public static void border(GuiGraphicsExtractor graphics, int x, int y, int width, int height, int radius, int fallback, int alpha) {
		dev.fancybars.config.FancyBarsConfig c = cfg();
		float thickness = Math.max(0, c.borderThickness);
		if (thickness <= 0.01f) return;
		int color;
		if (c.chromaBorder) {
			float baseAlpha = c.customBorder ? Math.clamp(c.borderOpacity, 0, 1) : 0.8f;
			color = Math.round(baseAlpha * alpha) << 24 | rgb(chromaHue(0.5f), 0.85f, 0.62f);
		} else if (c.customBorder) color = Math.round(Math.clamp(c.borderOpacity, 0, 1) * alpha) << 24 | c.borderColor & 0xFFFFFF;
		else {
			int f = night(fallback);
			color = Math.round((f >>> 24) * alpha / 255f) << 24 | f & 0xFFFFFF;
		}
		int rings = (int) Math.ceil(thickness);
		for (int i = 0; i < rings && width - i * 2 > 2 && height - i * 2 > 2; i++) {
			// the last ring is faded by the fractional part so thickness changes smoothly
			float part = Math.min(1, thickness - i);
			int ringColor = part >= 1 ? color : Math.round((color >>> 24) * part) << 24 | color & 0xFFFFFF;
			rawOutline(graphics, x + i, y + i, width - i * 2, height - i * 2, radius - i, ringColor);
		}
	}

	/** A HUD box: themed background (HUD opacity) and the Appearance border. */
	public static void hudPanel(GuiGraphicsExtractor graphics, int x, int y, int width, int height, int radius, float bgAlpha, int alpha) {
		roundedRect(graphics, x, y, width, height, radius, hudBg(bgAlpha, alpha));
		if (cfg().hudBorders) border(graphics, x, y, width, height, radius, hudAccent(0.4f, 255), alpha);
	}

	/** Secondary HUD text (artists, labels). */
	public static int hudTextSoft(int alpha) {
		return lerpColor(hudText(alpha), alpha << 24, 0.3f);
	}

	public static void card(GuiGraphicsExtractor graphics, int x, int y, int width, int height, int radius, int fill, int border) {
		roundedRect(graphics, x, y, width, height, radius, fill);
		roundedOutline(graphics, x, y, width, height, radius, border);
	}

	public static void circle(GuiGraphicsExtractor graphics, int x, int y, int size, int color) {
		graphics.blitSprite(RenderPipelines.GUI_TEXTURED, CIRCLE, x, y, size, size, colorAt(graphics, color, x + size / 2f));
	}

	/**
	 * Soft blurred drop shadow.
	 */
	public static void shadow(GuiGraphicsExtractor graphics, int x, int y, int width, int height, int radius, int size, int offsetY) {
		float strength = Math.clamp(cfg().shadowStrength, 0, 1);
		if (strength <= 0.01f) return;
		int pad = 16;
		graphics.blitSprite(RenderPipelines.GUI_TEXTURED, SHADOW, x - pad, y - pad + offsetY, width + pad * 2, height + pad * 2, Math.round(0x99 * strength) << 24);
	}

	/**
	 * Soft light-blue glow around a rectangle, strength 0..1.
	 */
	public static void glow(GuiGraphicsExtractor graphics, int x, int y, int width, int height, int radius, float strength) {
		strength *= cfg().glowStrength;
		if (strength <= 0.01f) return;
		int pad = 14;
		int alpha = Math.round(0xAA * Math.clamp(strength, 0, 1));
		graphics.blitSprite(RenderPipelines.GUI_TEXTURED, SHADOW, x - pad, y - pad, width + pad * 2, height + pad * 2, (alpha << 24) | (colorAt(graphics, ACCENT, x + width / 2f) & 0xFFFFFF));
	}

	/**
	 * Main panel: shadow, translucent navy body and thin border.
	 */
	public static void panel(GuiGraphicsExtractor graphics, int x, int y, int width, int height) {
		shadow(graphics, x, y, width, height, PANEL_RADIUS, 0, 2);
		roundedRect(graphics, x, y, width, height, PANEL_RADIUS, PANEL);
		border(graphics, x, y, width, height, PANEL_RADIUS, PANEL_BORDER, 255);
	}

	public static void backdrop(GuiGraphicsExtractor graphics, int width, int height) {
		graphics.fill(0, 0, width, height, Math.round(Math.clamp(cfg().backdropDarkness, 0, 1) * 255) << 24);
	}

	// ------------------------------------------------------------ Night blue (default) / SHIKIMODE (pink)

	/** Pink-theme colour (RGB) → its night-blue counterpart, used whenever SHIKIMODE is off. */
	private static final java.util.Map<Integer, Integer> NIGHT = new java.util.HashMap<>();
	public static final int BRAND_PINK = 0xFFFF8FC8;
	public static final int BRAND_BLUE = 0xFF7CC8FF;

	static {
		NIGHT.put(0x271A1F, 0x0C102F);
		NIGHT.put(0x2D1E24, 0x0E1335);
		NIGHT.put(0x24181D, 0x0B0F2C);
		NIGHT.put(0x49313B, 0x171E53);
		NIGHT.put(0x332229, 0x10153C);
		NIGHT.put(0x4F3641, 0x1A2159);
		NIGHT.put(0x3F2A33, 0x141A48);
		NIGHT.put(0x39282F, 0x121942);
		NIGHT.put(0x47323A, 0x171F51);
		NIGHT.put(0x2B1F24, 0x0E1333);
		NIGHT.put(0x5A3A48, 0x1B2464);
		NIGHT.put(0x422D36, 0x151C4B);
		NIGHT.put(0x312128, 0x101439);
		NIGHT.put(0x3A272F, 0x121843);
		NIGHT.put(0x573D48, 0x1D2661);
		NIGHT.put(0x684A57, 0x232E73);
		NIGHT.put(0x36232B, 0x10163F);
		NIGHT.put(0x22161B, 0x0A0E2A);
		NIGHT.put(0x201318, 0x080C28);
		NIGHT.put(0x6B4E5A, 0x253076);
		NIGHT.put(0x1C1216, 0x080B23);
		NIGHT.put(0xFF8FC8, 0x6FA8FF);
		NIGHT.put(0xFFC4E1, 0xB8D4FF);
		NIGHT.put(0xD6448F, 0x2F64D6);
		NIGHT.put(0xFFAED6, 0x93BCFF);
		NIGHT.put(0xAF3D78, 0x2F4FAF);
		NIGHT.put(0xC47CA6, 0x5F7FD0);
		NIGHT.put(0x52233A, 0x16244A);
		NIGHT.put(0x6C2B4C, 0x1F3670);
		NIGHT.put(0xF7EEF2, 0xE8EEFA);
		NIGHT.put(0xEED8E2, 0xD4DCF0);
		NIGHT.put(0xC2A2B0, 0x98A6C8);
		NIGHT.put(0x79606A, 0x56607E);
		NIGHT.put(0x2A0B1B, 0x081430);
		NIGHT.put(0xC9CCE0, 0xC9D3EC);
	}

	public static boolean shikiMode() {
		return dev.fancybars.config.FancyBarsConfig.get().shikiMode;
	}

	// ------------------------------------------------------------ Custom colours (Appearance settings)

	private static final java.util.Set<Integer> ACCENT_KEYS = java.util.Set.of(0xFF8FC8, 0xFFC4E1, 0xD6448F, 0xFFAED6, 0xAF3D78, 0xC47CA6, 0x52233A, 0x6C2B4C);
	private static final java.util.Set<Integer> TEXT_KEYS = java.util.Set.of(0xF7EEF2, 0xEED8E2, 0xC2A2B0, 0x79606A, 0xC9CCE0);
	private static final java.util.Map<Integer, Integer> CUSTOM = new java.util.HashMap<>();
	private static long customKey = Long.MIN_VALUE;

	private static dev.fancybars.config.FancyBarsConfig cfg() {
		return dev.fancybars.config.FancyBarsConfig.get();
	}

	public static boolean customColors() {
		return cfg().customColors || cfg().chromaAccent;
	}

	/** Hue (0..1) of the rainbow effect right now. */
	public static float chromaHue(float offset) {
		float h = (float) ((Util.getMillis() / 1000.0 * cfg().chromaSpeed * 0.1 + offset) % 1.0);
		return Math.round(h * 500) / 500f;
	}

	/** Accent the theme is built from (your accent, or the rainbow). */
	public static int effectiveAccent() {
		dev.fancybars.config.FancyBarsConfig c = cfg();
		if (!c.chromaAccent) return c.themeAccent;
		float[] base = hsl(c.themeAccent);
		return rgb(chromaHue(0), Math.max(0.6f, base[1]), Math.clamp(base[2], 0.45f, 0.7f));
	}

	public static boolean animationsEnabled() {
		return cfg().menuAnimations;
	}

	/** RGB → {h, s, l} (0..1). */
	public static float[] hsl(int rgb) {
		float r = (rgb >> 16 & 0xFF) / 255f, g = (rgb >> 8 & 0xFF) / 255f, b = (rgb & 0xFF) / 255f;
		float max = Math.max(r, Math.max(g, b)), min = Math.min(r, Math.min(g, b));
		float l = (max + min) / 2, h = 0, sat = 0;
		if (max != min) {
			float d = max - min;
			sat = l > 0.5f ? d / (2 - max - min) : d / (max + min);
			if (max == r) h = (g - b) / d + (g < b ? 6 : 0);
			else if (max == g) h = (b - r) / d + 2;
			else h = (r - g) / d + 4;
			h /= 6;
		}
		return new float[]{h, sat, l};
	}

	public static int rgb(float h, float sat, float l) {
		sat = Math.clamp(sat, 0, 1);
		l = Math.clamp(l, 0, 1);
		if (sat == 0) {
			int v = Math.round(l * 255);
			return v << 16 | v << 8 | v;
		}
		float q = l < 0.5f ? l * (1 + sat) : l + sat - l * sat;
		float p = 2 * l - q;
		return Math.round(hue(p, q, h + 1 / 3f) * 255) << 16 | Math.round(hue(p, q, h) * 255) << 8 | Math.round(hue(p, q, h - 1 / 3f) * 255);
	}

	private static float hue(float p, float q, float t) {
		if (t < 0) t += 1;
		if (t > 1) t -= 1;
		if (t < 1 / 6f) return p + (q - p) * 6 * t;
		if (t < 1 / 2f) return q;
		if (t < 2 / 3f) return p + (q - p) * (2 / 3f - t) * 6;
		return p;
	}

	/** Re-tints a night-theme colour onto a user colour, keeping its relative brightness. */
	private static int retint(int nightRgb, int referenceRgb, int userRgb) {
		float[] n = hsl(nightRgb), ref = hsl(referenceRgb), user = hsl(userRgb);
		float light = ref[2] < 0.001f ? n[2] : n[2] * (user[2] / ref[2]);
		float sat = ref[1] < 0.001f ? user[1] : user[1] * Math.min(1.5f, n[1] / ref[1]);
		return rgb(user[0], sat, light);
	}

	private static void rebuildCustom() {
		dev.fancybars.config.FancyBarsConfig c = cfg();
		int accentNow = effectiveAccent();
		long key = ((long) accentNow << 40) ^ ((long) c.themeBackground << 20) ^ c.themeText;
		if (key == customKey) return;
		customKey = key;
		CUSTOM.clear();
		int accentRef = NIGHT.get(0xFF8FC8);
		int surfaceRef = NIGHT.get(PANEL & 0xFFFFFF);
		int textRef = NIGHT.get(TEXT & 0xFFFFFF);
		for (java.util.Map.Entry<Integer, Integer> e : NIGHT.entrySet()) {
			int k = e.getKey();
			int v = e.getValue();
			int out;
			if (ACCENT_KEYS.contains(k)) out = retint(v, accentRef, accentNow);
			else if (TEXT_KEYS.contains(k)) out = retint(v, textRef, c.themeText);
			else if (k == 0x2A0B1B) out = rgb(hsl(accentNow)[0], 0.6f, 0.08f); // text on accent buttons
			else out = retint(v, surfaceRef, c.themeBackground);
			CUSTOM.put(k, out);
		}
	}

	/** Theme corner radius scaled by the Corner Roundness setting. */
	public static int radius(int r) {
		return Math.round(r * cfg().cornerRoundness);
	}

	/** The theme accent as it's drawn right now (night / pink / custom). */
	public static int accent() {
		return night(ACCENT);
	}

	/** HUD panel background: the element's base opacity times the HUD Opacity setting. */
	public static int hudBg(float baseAlpha, int alpha) {
		dev.fancybars.config.FancyBarsConfig c = cfg();
		int bg = c.customColors ? c.themeBackground : 0x101014;
		int a = Math.round(Math.clamp(alpha * baseAlpha * c.hudOpacity, 0, 255));
		return a << 24 | bg & 0xFFFFFF;
	}

	/** HUD accent (outlines, highlights) with an alpha. */
	public static int hudAccent(float baseAlpha, int alpha) {
		return Math.round(Math.clamp(alpha * baseAlpha, 0, 255)) << 24 | accent() & 0xFFFFFF;
	}

	/** HUD main text colour. */
	public static int hudText(int alpha) {
		dev.fancybars.config.FancyBarsConfig c = cfg();
		return alpha << 24 | (c.customColors ? c.themeText : 0xFFFFFF) & 0xFFFFFF;
	}

	/** The colour to actually draw: theme colours turn night blue (or your custom colours) unless SHIKIMODE is on. Alpha is kept. */
	public static int night(int color) {
		if (customColors()) {
			rebuildCustom();
			Integer mapped = CUSTOM.get(color & 0xFFFFFF);
			if (mapped == null) return color;
			int alpha = color >>> 24;
			// panel opacity applies to the see-through surfaces only
			if (alpha < 255 && !ACCENT_KEYS.contains(color & 0xFFFFFF) && !TEXT_KEYS.contains(color & 0xFFFFFF)) {
				alpha = Math.round(Math.clamp(alpha * cfg().panelOpacity, 0, 255));
			}
			return alpha << 24 | mapped;
		}
		if (shikiMode()) return color;
		Integer mapped = NIGHT.get(color & 0xFFFFFF);
		return mapped == null ? color : (color & 0xFF000000) | mapped;
	}

	/** Kept for callers that pass a position; the theme has no gradient any more. */
	public static int colorAt(GuiGraphicsExtractor graphics, int color, float localX) {
		return night(color);
	}

	/** Pink → blue at {@code fraction}, opaque (used for the SBCLIENT title letters). */
	public static int brandGradient(float fraction) {
		return lerpColor(BRAND_PINK, BRAND_BLUE, fraction);
	}

	/** SBCLIENT / ShikiClient name, coloured for the current mode (vanilla font). */
	public static MutableComponent brand() {
		if (shikiMode()) {
			return Component.empty().append(Component.literal("Shiki").withColor(ACCENT & 0xFFFFFF)).append(Component.literal("Client").withColor(TEXT & 0xFFFFFF));
		}
		if (customColors()) {
			int acc = night(ACCENT) & 0xFFFFFF;
			int light = night(ACCENT_LIGHT) & 0xFFFFFF;
			MutableComponent custom = Component.empty().append(Component.literal("S").withColor(light)).append(Component.literal("B").withColor(acc));
			String rest = "CLIENT";
			for (int i = 0; i < rest.length(); i++) {
				custom.append(Component.literal(String.valueOf(rest.charAt(i))).withColor(lerpColor(0xFF000000 | light, 0xFF000000 | acc, i / (float) (rest.length() - 1)) & 0xFFFFFF));
			}
			return custom;
		}
		// night blue: S and B in the two theme blues, CLIENT fading from pale to deep blue
		MutableComponent text = Component.empty()
				.append(Component.literal("S").withColor(0xB8D4FF))
				.append(Component.literal("B").withColor(0x6FA8FF));
		String rest = "CLIENT";
		for (int i = 0; i < rest.length(); i++) {
			text.append(Component.literal(String.valueOf(rest.charAt(i))).withColor(lerpColor(0xFFD4E4FF, 0xFF4F86F0, i / (float) (rest.length() - 1)) & 0xFFFFFF));
		}
		return text;
	}

	public static String brandName() {
		return shikiMode() ? "ShikiClient" : "SBCLIENT";
	}

	/** "[SBCLIENT] " chat prefix. */
	public static MutableComponent chatPrefix() {
		return Component.literal("[").withStyle(net.minecraft.ChatFormatting.DARK_GRAY)
				.append(brand())
				.append(Component.literal("] ").withStyle(net.minecraft.ChatFormatting.DARK_GRAY));
	}

	public static Identifier brandIcon() {
		return FancyBarsMod.id(shikiMode() ? "textures/gui/shiki_icon.png" : "textures/gui/sb_icon.png");
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
		if (!cfg().menuAnimations) return target;
		float dt = Math.min(0.1f, (Util.getMillis() - lastFrameMillis) / 1000f);
		float step = speedPerSecond * dt * Math.max(0.1f, cfg().animationSpeed);
		if (current < target) return Math.min(target, current + step);
		return Math.max(target, current - step);
	}

	/**
	 * Slide-in offset for screens opening, in GUI pixels (fractional so the motion is smooth).
	 */
	public static float openOffset(long openedAt) {
		if (!cfg().menuAnimations) return 0;
		float t = (Util.getMillis() - openedAt) / (260f / Math.max(0.1f, cfg().animationSpeed));
		return (1 - easeOutCubic(t)) * 12;
	}

	public static float openAlpha(long openedAt) {
		if (!cfg().menuAnimations) return 1;
		return easeOutCubic((Util.getMillis() - openedAt) / (200f / Math.max(0.1f, cfg().animationSpeed)));
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
