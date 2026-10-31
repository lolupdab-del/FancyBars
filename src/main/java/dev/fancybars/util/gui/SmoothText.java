package dev.fancybars.util.gui;

import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.font.FontRenderContext;
import java.awt.font.TextLayout;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.joml.Matrix3x2fStack;
import org.jspecify.annotations.Nullable;

import com.mojang.blaze3d.platform.NativeImage;

import dev.fancybars.FancyBarsMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;

/**
 * Browser-quality text for SBCLIENT's own fonts. Minecraft draws TrueType fonts from a glyph atlas that gets
 * resampled onto the screen, which leaves jagged edges. Instead, each line of text is rendered with Java2D's
 * anti-aliasing (fractional metrics, grayscale AA) straight at the exact on-screen pixel size, cached as a
 * texture and drawn 1:1 on the real screen pixel grid. Text in any other font is left to Minecraft.
 */
public final class SmoothText {
	private record Kind(String file, float size) {}

	private static final Map<String, Kind> KINDS = Map.of(
			"ui", new Kind("geist-medium.ttf", 11f),
			"bold", new Kind("geist-semibold.ttf", 11f),
			"title", new Kind("geist-semibold.ttf", 17.5f),
			"bar", new Kind("geist-semibold.ttf", 10.5f),
			"small", new Kind("geist-medium.ttf", 8.25f),
			"mono", new Kind("geistmono-medium.ttf", 10f));
	/** Geist: (ascent - descent) / unitsPerEm. Minecraft's TTF "size" is that height, Java2D's is the em. */
	private static final float EM_RATIO = 1.3f;
	private static final float ASCENT = 1.005f;
	private static final float DESCENT = 0.295f;
	/** Minecraft puts the baseline 7 GUI px below the y text is drawn at. */
	private static final float BASELINE = 7f;
	private static final int PAD = 2;

	private static final Map<String, Font> BASE_FONTS = new HashMap<>();
	private static final LinkedHashMap<String, Entry> CACHE = new LinkedHashMap<>(256, 0.75f, true);
	private static int counter;
	private static boolean failed;

	private record Entry(@Nullable Identifier plain, @Nullable Identifier colored, int width, int height, float ascentPx, long[] lastUsed) {}

	private record Segment(String text, int color, boolean bold) {}

	private SmoothText() {}

	private static @Nullable String kindOf(Style style) {
		FontDescription font = style.getFont();
		if (!(font instanceof FontDescription.Resource(Identifier id))) return null;
		if (!id.getNamespace().equals(FancyBarsMod.NAMESPACE)) return null;
		String path = id.getPath();
		int cut = path.lastIndexOf("_s");
		if (cut < 0) return null;
		String kind = path.substring(0, cut);
		return KINDS.containsKey(kind) ? kind : null;
	}

	private static @Nullable Font baseFont(String file) {
		if (failed) return null;
		Font f = BASE_FONTS.get(file);
		if (f != null) return f;
		try (InputStream in = SmoothText.class.getResourceAsStream("/assets/fancybars/font/" + file)) {
			if (in == null) throw new IllegalStateException("missing " + file);
			f = Font.createFont(Font.TRUETYPE_FONT, in);
			BASE_FONTS.put(file, f);
			return f;
		} catch (Exception e) {
			FancyBarsMod.LOGGER.warn("[SBCLIENT] Smooth text unavailable, using Minecraft's text", e);
			failed = true;
			return null;
		}
	}

	/**
	 * Draws {@code text} like {@code GuiGraphicsExtractor.text} would. Returns false (and draws nothing) when the
	 * text isn't in one of SBCLIENT's fonts, so Minecraft draws it as usual.
	 */
	public static boolean draw(GuiGraphicsExtractor graphics, FormattedCharSequence text, int x, int y, int color, boolean shadow) {
		if (failed) return false;
		List<Segment> segments = new ArrayList<>();
		String[] kind = new String[1];
		StringBuilder current = new StringBuilder();
		Style[] currentStyle = new Style[1];
		boolean[] mixed = new boolean[1];
		text.accept((index, style, codePoint) -> {
			String k = kindOf(style);
			if (kind[0] == null && k != null) kind[0] = k;
			if (k == null) mixed[0] = true;
			if (currentStyle[0] != null && !sameLook(currentStyle[0], style)) {
				segments.add(segment(current.toString(), currentStyle[0]));
				current.setLength(0);
			}
			currentStyle[0] = style;
			current.appendCodePoint(codePoint);
			return true;
		});
		if (kind[0] == null || mixed[0]) return false;
		if (currentStyle[0] != null && current.length() > 0) segments.add(segment(current.toString(), currentStyle[0]));
		if (segments.isEmpty() || (color >>> 24) == 0) return true;

		Matrix3x2fStack pose = graphics.pose();
		float worldScale = (float) Math.hypot(pose.m00(), pose.m01());
		float gs = Theme.guiScale();
		float pxPerUnit = gs * worldScale;
		Kind k = KINDS.get(kind[0]);
		float emPx = Math.round(k.size() / EM_RATIO * pxPerUnit * 4) / 4f;
		if (emPx < 2) return true;

		Entry entry = get(kind[0], k, emPx, segments);
		if (entry == null) return false;

		// top-left of the texture in the pose's units
		float lx = x - PAD / pxPerUnit;
		float ly = y + BASELINE - entry.ascentPx() / pxPerUnit;
		if (shadow) blitAll(graphics, entry, lx + 1, ly + 1, pxPerUnit, (color & 0xFF000000) | ((color & 0xFCFCFC) >> 2), true);
		blitAll(graphics, entry, lx, ly, pxPerUnit, color, false);
		return true;
	}

	private static final Map<String, Float> WIDTHS = new LinkedHashMap<>(512, 0.75f, true) {
		@Override
		protected boolean removeEldestEntry(Map.Entry<String, Float> eldest) {
			return size() > 4000;
		}
	};

	/**
	 * Width in GUI units of text in SBCLIENT's fonts, measured exactly the way it is drawn.
	 * Returns -1 when the text isn't (only) in SBCLIENT's fonts.
	 */
	public static float width(FormattedCharSequence text) {
		if (failed) return -1;
		List<Segment> segments = new ArrayList<>();
		String[] kind = new String[1];
		StringBuilder current = new StringBuilder();
		Style[] currentStyle = new Style[1];
		boolean[] mixed = new boolean[1];
		text.accept((index, style, codePoint) -> {
			String k = kindOf(style);
			if (kind[0] == null && k != null) kind[0] = k;
			if (k == null) mixed[0] = true;
			if (currentStyle[0] != null && currentStyle[0].isBold() != style.isBold()) {
				segments.add(segment(current.toString(), currentStyle[0]));
				current.setLength(0);
			}
			currentStyle[0] = style;
			current.appendCodePoint(codePoint);
			return true;
		});
		if (kind[0] == null || mixed[0]) return -1;
		if (current.length() > 0) segments.add(segment(current.toString(), currentStyle[0]));
		Kind k = KINDS.get(kind[0]);
		StringBuilder key = new StringBuilder(kind[0]);
		for (Segment seg : segments) key.append(seg.bold() ? '\u0001' : '\u0002').append(seg.text());
		String cacheKey = key.toString();
		Float cached = WIDTHS.get(cacheKey);
		if (cached != null) return cached;
		Font base = baseFont(k.file());
		if (base == null) return -1;
		Font regular = base.deriveFont(100f);
		Font boldBase = k.file().contains("medium") ? baseFont("geist-semibold.ttf") : base;
		Font bold = (boldBase == null ? base : boldBase).deriveFont(100f);
		FontRenderContext frc = new FontRenderContext(null, RenderingHints.VALUE_TEXT_ANTIALIAS_ON, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
		double total = 0;
		for (Segment seg : segments) if (!seg.text().isEmpty()) total += (seg.bold() ? bold : regular).getStringBounds(seg.text(), frc).getWidth();
		float w = (float) (total * (k.size() / EM_RATIO) / 100.0);
		WIDTHS.put(cacheKey, w);
		return w;
	}

	private static boolean sameLook(Style a, Style b) {
		return java.util.Objects.equals(a.getColor(), b.getColor()) && a.isBold() == b.isBold() && java.util.Objects.equals(kindOf(a), kindOf(b));
	}

	private static Segment segment(String text, Style style) {
		TextColor c = style.getColor();
		return new Segment(text, c == null ? -1 : c.getValue() & 0xFFFFFF, style.isBold());
	}

	private static void blitAll(GuiGraphicsExtractor graphics, Entry e, float lx, float ly, float pxPerUnit, int color, boolean shadow) {
		Matrix3x2fStack pose = graphics.pose();
		pose.pushMatrix();
		boolean axisAligned = Math.abs(pose.m01()) < 1e-4f && Math.abs(pose.m10()) < 1e-4f;
		if (axisAligned) {
			// snap to the real screen pixel grid so every texel lands on exactly one pixel
			float gs = Theme.guiScale();
			float sx = pose.m00() * lx + pose.m20();
			float sy = pose.m11() * ly + pose.m21();
			pose.identity();
			pose.translate(Math.round(sx * gs) / gs, Math.round(sy * gs) / gs);
			pose.scale(1 / gs, 1 / gs);
		} else {
			pose.translate(lx, ly);
			pose.scale(1 / pxPerUnit, 1 / pxPerUnit);
		}
		if (e.plain() != null) graphics.blit(RenderPipelines.GUI_TEXTURED, e.plain(), 0, 0, 0, 0, e.width(), e.height(), e.width(), e.height(), color);
		if (e.colored() != null) {
			int tint = shadow ? color : (color & 0xFF000000) | 0xFFFFFF;
			graphics.blit(RenderPipelines.GUI_TEXTURED, e.colored(), 0, 0, 0, 0, e.width(), e.height(), e.width(), e.height(), tint);
		}
		pose.popMatrix();
	}

	private static @Nullable Entry get(String kindName, Kind kind, float emPx, List<Segment> segments) {
		StringBuilder key = new StringBuilder(kindName).append('|').append(emPx);
		for (Segment s : segments) key.append('|').append(s.color()).append(s.bold() ? 'b' : 'n').append(s.text());
		String k = key.toString();
		long now = net.minecraft.util.Util.getMillis();
		Entry e = CACHE.get(k);
		if (e != null) {
			e.lastUsed()[0] = now;
			return e;
		}
		e = render(kind, emPx, segments, now);
		if (e == null) return null;
		CACHE.put(k, e);
		evict(now);
		return e;
	}

	private static void evict(long now) {
		if (CACHE.size() < 600) return;
		Iterator<Map.Entry<String, Entry>> it = CACHE.entrySet().iterator();
		while (it.hasNext() && CACHE.size() > 400) {
			Entry old = it.next().getValue();
			if (now - old.lastUsed()[0] < 3000) break; // still on screen recently; never free a texture in use
			it.remove();
			if (old.plain() != null) Minecraft.getInstance().getTextureManager().release(old.plain());
			if (old.colored() != null) Minecraft.getInstance().getTextureManager().release(old.colored());
		}
	}

	private static @Nullable Entry render(Kind kind, float emPx, List<Segment> segments, long now) {
		Font base = baseFont(kind.file());
		if (base == null) return null;
		Font regular = base.deriveFont(emPx);
		Font boldFont = kind.file().contains("medium") ? baseFont("geist-semibold.ttf") : base;
		Font bold = (boldFont == null ? base : boldFont).deriveFont(emPx);
		FontRenderContext frc = new FontRenderContext(null, RenderingHints.VALUE_TEXT_ANTIALIAS_ON, RenderingHints.VALUE_FRACTIONALMETRICS_ON);

		float[] advances = new float[segments.size()];
		float total = 0;
		boolean anyPlain = false;
		boolean anyColored = false;
		for (int i = 0; i < segments.size(); i++) {
			Segment s = segments.get(i);
			if (s.text().isEmpty()) continue;
			advances[i] = (float) (s.bold() ? bold : regular).getStringBounds(s.text(), frc).getWidth();
			total += advances[i];
			if (s.color() < 0) anyPlain = true;
			else anyColored = true;
		}
		float ascentPx = (float) Math.ceil(emPx * ASCENT) + PAD;
		int width = Math.max(1, (int) Math.ceil(total) + PAD * 2);
		int height = Math.max(1, (int) Math.ceil(emPx * (ASCENT + DESCENT)) + PAD * 2);

		BufferedImage plain = anyPlain ? new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB) : null;
		BufferedImage colored = anyColored ? new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB) : null;
		Graphics2D gp = plain == null ? null : setup(plain);
		Graphics2D gc = colored == null ? null : setup(colored);
		float pen = PAD;
		for (int i = 0; i < segments.size(); i++) {
			Segment s = segments.get(i);
			if (s.text().isEmpty()) continue;
			Graphics2D g = s.color() < 0 ? gp : gc;
			g.setFont(s.bold() ? bold : regular);
			g.setColor(new java.awt.Color(s.color() < 0 ? 0xFFFFFF : s.color()));
			new TextLayout(s.text(), g.getFont(), g.getFontRenderContext()).draw(g, pen, ascentPx);
			pen += advances[i];
		}
		if (gp != null) gp.dispose();
		if (gc != null) gc.dispose();
		Identifier p = plain == null ? null : upload(plain);
		Identifier c = colored == null ? null : upload(colored);
		return new Entry(p, c, width, height, ascentPx, new long[]{now});
	}

	private static Graphics2D setup(BufferedImage img) {
		Graphics2D g = img.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
		g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
		g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
		return g;
	}

	private static Identifier upload(BufferedImage img) {
		NativeImage n = new NativeImage(img.getWidth(), img.getHeight(), false);
		for (int y = 0; y < img.getHeight(); y++) for (int x = 0; x < img.getWidth(); x++) n.setPixel(x, y, img.getRGB(x, y));
		Identifier id = FancyBarsMod.id("text/t" + counter++);
		DynamicTexture texture = new DynamicTexture(() -> "SBCLIENT text", n);
		Minecraft.getInstance().getTextureManager().register(id, texture);
		texture.upload();
		return id;
	}
}
