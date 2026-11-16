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
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;

/**
 * Browser-quality text for SkyblockGuiUtils's own fonts. Minecraft draws TrueType fonts from a glyph atlas that gets
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

	static {
		// the shared atlas was cleared: text drawn into it has to be drawn again (its own-texture pieces stay)
		UiAtlas.onReset(() -> CACHE.values().removeIf(e -> e.plain() != null && !e.plain().valid() || e.colored() != null && !e.colored().valid()));
	}
	private static int counter;
	private static boolean failed;

	private record Entry(UiAtlas.@Nullable Region plain, UiAtlas.@Nullable Region colored, int width, int height, float ascentPx, long[] lastUsed, List<Run> fallback) {}

	/** A piece of text Geist has no glyphs for (✪, ⚚, ✕ ...), drawn by Minecraft at {@code offsetPx} from the start. */
	private record Run(float offsetPx, Segment segment) {}

	private record Segment(String text, int color, boolean bold, boolean fallback) {}

	private record Collected(String kind, List<Segment> segments) {}

	/** Set while handing symbols back to Minecraft, so the mixin doesn't send them straight back here. */
	private static boolean bypass;

	/** Splits text into runs of the same look; returns null if it isn't (only) in SkyblockGuiUtils's fonts. */
	/**
	 * Quick check whether this text could be ours: only text whose every letter is in a smooth font is drawn by us, so
	 * the first letter decides it. Every piece of text in the game is drawn and measured through here, so the full
	 * pass (and its allocations) only runs for text that passes this.
	 */
	public static boolean mightBeSmooth(FormattedCharSequence text) {
		if (failed || bypass || dev.fancybars.util.Perf.smoothTextOff) return false;
		boolean[] smooth = {false};
		text.accept((index, style, codePoint) -> {
			smooth[0] = kindOf(style) != null;
			return false;
		});
		return smooth[0];
	}

	/** {@link #mightBeSmooth(FormattedCharSequence)} for unsplit text, without building its visual order first. */
	public static boolean mightBeSmooth(net.minecraft.network.chat.FormattedText text) {
		if (failed || bypass || dev.fancybars.util.Perf.smoothTextOff) return false;
		return text.visit((Style style, String part) -> part.isEmpty() ? java.util.Optional.<Boolean>empty() : java.util.Optional.of(kindOf(style) != null),
				Style.EMPTY).orElse(false);
	}

	private static @Nullable Collected collect(FormattedCharSequence text) {
		if (!mightBeSmooth(text)) return null;
		List<Segment> segments = new ArrayList<>();
		String[] kind = new String[1];
		StringBuilder current = new StringBuilder();
		Style[] currentStyle = new Style[1];
		boolean[] currentFallback = new boolean[1];
		boolean[] mixed = new boolean[1];
		text.accept((index, style, codePoint) -> {
			String k = kindOf(style);
			if (kind[0] == null && k != null) kind[0] = k;
			if (k == null) {
				mixed[0] = true;
				return false;
			}
			Font f = baseFont(KINDS.get(k).file());
			boolean fallback = f != null && !f.canDisplay(codePoint);
			if (currentStyle[0] != null && (!sameLook(currentStyle[0], style) || fallback != currentFallback[0])) {
				segments.add(segment(current.toString(), currentStyle[0], currentFallback[0]));
				current.setLength(0);
			}
			currentStyle[0] = style;
			currentFallback[0] = fallback;
			current.appendCodePoint(codePoint);
			return true;
		});
		if (kind[0] == null || mixed[0]) return null;
		if (currentStyle[0] != null && current.length() > 0) segments.add(segment(current.toString(), currentStyle[0], currentFallback[0]));
		return new Collected(kind[0], segments);
	}

	private static Component fallbackComponent(Segment seg, int color) {
		int rgb = seg.color() < 0 ? color & 0xFFFFFF : seg.color();
		return Component.literal(seg.text()).withStyle(st -> st.withColor(rgb).withBold(seg.bold()));
	}

	/** Width in GUI units of a fallback run, as Minecraft draws it. */
	private static float fallbackWidth(Segment seg) {
		return Minecraft.getInstance().font.width(fallbackComponent(seg, 0xFFFFFF));
	}

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
			FancyBarsMod.LOGGER.warn("[SkyblockGuiUtils] Smooth text unavailable, using Minecraft's text", e);
			failed = true;
			return null;
		}
	}

	/**
	 * Draws {@code text} like {@code GuiGraphicsExtractor.text} would. Returns false (and draws nothing) when the
	 * text isn't in one of SkyblockGuiUtils's fonts, so Minecraft draws it as usual.
	 */
	public static boolean draw(GuiGraphicsExtractor graphics, FormattedCharSequence text, int x, int y, int color, boolean shadow) {
		if (failed || bypass || dev.fancybars.util.Perf.smoothTextOff) return false;
		if (!mightBeSmooth(text)) return false;
		return draw(graphics, memo(text, text, null), x, y, color, shadow);
	}

	/** {@link #draw(GuiGraphicsExtractor, FormattedCharSequence, int, int, int, boolean)} for unsplit text. */
	public static boolean draw(GuiGraphicsExtractor graphics, Component text, int x, int y, int color, boolean shadow) {
		if (failed || bypass || dev.fancybars.util.Perf.smoothTextOff) return false;
		return draw(graphics, memo(text, null, text), x, y, color, shadow);
	}

	/**
	 * What's been worked out for one text object: its runs, its width and which texture it was last drawn with. HUDs
	 * draw the same text objects every frame, and splitting them into runs and building the cache keys again each
	 * time was most of the cost.
	 */
	private static final class Memo {
		@Nullable Collected collected;
		float width = Float.NaN;
		float emPx = -1;
		@Nullable String drawKey;
	}

	private static final Map<Object, Memo> MEMO = new com.google.common.collect.MapMaker().weakKeys().makeMap();

	private static @Nullable Memo memo(Object key, @Nullable FormattedCharSequence sequence, net.minecraft.network.chat.@Nullable FormattedText text) {
		Memo m = MEMO.get(key);
		if (m == null) {
			m = new Memo();
			m.collected = collect(sequence != null ? sequence : net.minecraft.locale.Language.getInstance().getVisualOrder(text));
			MEMO.put(key, m);
		}
		return m.collected == null ? null : m;
	}

	private static boolean draw(GuiGraphicsExtractor graphics, @Nullable Memo memo, int x, int y, int color, boolean shadow) {
		if (memo == null) return false;
		Collected collected = memo.collected;
		List<Segment> segments = collected.segments();
		String[] kind = {collected.kind()};
		if (segments.isEmpty() || (color >>> 24) == 0) return true;

		Matrix3x2fStack pose = graphics.pose();
		float worldScale = (float) Math.hypot(pose.m00(), pose.m01());
		float gs = Theme.guiScale();
		float pxPerUnit = gs * worldScale;
		Kind k = KINDS.get(kind[0]);
		float emPx = Math.round(k.size() / EM_RATIO * pxPerUnit * 4) / 4f;
		if (emPx < 2) return true;

		Entry entry = null;
		if (memo.drawKey != null && memo.emPx == emPx) {
			// same text at the same size as last time: its texture (if it's still cached) without rebuilding the key
			entry = CACHE.get(memo.drawKey);
			if (entry != null) entry.lastUsed()[0] = net.minecraft.util.Util.getMillis();
		}
		if (entry == null) {
			String key = drawKey(kind[0], emPx, segments);
			entry = get(key, k, emPx, segments);
			memo.drawKey = key;
			memo.emPx = emPx;
		}
		if (entry == null) return false;

		// top-left of the texture in the pose's units
		float lx = x - PAD / pxPerUnit;
		float ly = y + BASELINE - entry.ascentPx() / pxPerUnit;
		if (shadow) blitAll(graphics, entry, lx + 1, ly + 1, pxPerUnit, (color & 0xFF000000) | ((color & 0xFCFCFC) >> 2), true);
		blitAll(graphics, entry, lx, ly, pxPerUnit, color, false);
		if (!entry.fallback().isEmpty()) {
			// symbols Geist doesn't have: let Minecraft draw them in its own font, in place
			bypass = true;
			try {
				net.minecraft.client.gui.Font mcFont = Minecraft.getInstance().font;
				for (Run run : entry.fallback()) {
					pose.pushMatrix();
					pose.translate(x + run.offsetPx() / pxPerUnit, y);
					graphics.text(mcFont, fallbackComponent(run.segment(), color), 0, 0, (color & 0xFF000000) | 0xFFFFFF, shadow);
					pose.popMatrix();
				}
			} finally {
				bypass = false;
			}
		}
		return true;
	}

	private static final Map<String, Float> WIDTHS = new LinkedHashMap<>(512, 0.75f, true) {
		@Override
		protected boolean removeEldestEntry(Map.Entry<String, Float> eldest) {
			return size() > 4000;
		}
	};

	/**
	 * Width in GUI units of text in SkyblockGuiUtils's fonts, measured exactly the way it is drawn.
	 * Returns -1 when the text isn't (only) in SkyblockGuiUtils's fonts.
	 */
	public static float width(FormattedCharSequence text) {
		if (failed || bypass || dev.fancybars.util.Perf.smoothTextOff) return -1;
		if (!mightBeSmooth(text)) return -1;
		return width(memo(text, text, null));
	}

	/** {@link #width(FormattedCharSequence)} for unsplit text. */
	public static float width(net.minecraft.network.chat.FormattedText text) {
		if (failed || bypass || dev.fancybars.util.Perf.smoothTextOff) return -1;
		return width(memo(text, null, text));
	}

	private static float width(@Nullable Memo memo) {
		if (memo == null) return -1;
		if (Float.isNaN(memo.width)) memo.width = measure(memo.collected);
		return memo.width;
	}

	private static float measure(Collected collected) {
		List<Segment> segments = collected.segments();
		String[] kind = {collected.kind()};
		Kind k = KINDS.get(kind[0]);
		StringBuilder key = new StringBuilder(kind[0]);
		for (Segment seg : segments) key.append(seg.fallback() ? '\u0003' : seg.bold() ? '\u0001' : '\u0002').append(seg.text());
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
		float fallback = 0;
		for (Segment seg : segments) {
			if (seg.text().isEmpty()) continue;
			if (seg.fallback()) fallback += fallbackWidth(seg);
			else total += (seg.bold() ? bold : regular).getStringBounds(seg.text(), frc).getWidth();
		}
		float w = (float) (total * (k.size() / EM_RATIO) / 100.0) + fallback;
		WIDTHS.put(cacheKey, w);
		return w;
	}

	private static boolean sameLook(Style a, Style b) {
		return java.util.Objects.equals(a.getColor(), b.getColor()) && a.isBold() == b.isBold() && java.util.Objects.equals(kindOf(a), kindOf(b));
	}

	private static Segment segment(String text, Style style, boolean fallback) {
		TextColor c = style.getColor();
		return new Segment(text, c == null ? -1 : c.getValue() & 0xFFFFFF, style.isBold(), fallback);
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
		if (e.plain() != null) UiAtlas.blit(graphics, Chroma.textured(color), e.plain(), 0, 0, 0, 0, e.width(), e.height(), color);
		if (e.colored() != null) {
			int tint = shadow ? color : (color & 0xFF000000) | 0xFFFFFF;
			UiAtlas.blit(graphics, RenderPipelines.GUI_TEXTURED, e.colored(), 0, 0, 0, 0, e.width(), e.height(), tint);
		}
		pose.popMatrix();
	}

	private static String drawKey(String kindName, float emPx, List<Segment> segments) {
		StringBuilder key = new StringBuilder(kindName).append('|').append(emPx);
		for (Segment s : segments) key.append('|').append(s.color()).append(s.fallback() ? 'f' : s.bold() ? 'b' : 'n').append(s.text());
		return key.toString();
	}

	private static @Nullable Entry get(String k, Kind kind, float emPx, List<Segment> segments) {
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
			UiAtlas.release(old.plain());
			UiAtlas.release(old.colored());
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
		float pxPerGui = emPx * EM_RATIO / kind.size();
		List<Run> fallback = new ArrayList<>();
		boolean anyPlain = false;
		boolean anyColored = false;
		for (int i = 0; i < segments.size(); i++) {
			Segment s = segments.get(i);
			if (s.text().isEmpty()) continue;
			if (s.fallback()) {
				fallback.add(new Run(total, s));
				advances[i] = fallbackWidth(s) * pxPerGui;
				total += advances[i];
				continue;
			}
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
			if (s.fallback()) {
				pen += advances[i];
				continue;
			}
			Graphics2D g = s.color() < 0 ? gp : gc;
			g.setFont(s.bold() ? bold : regular);
			g.setColor(new java.awt.Color(s.color() < 0 ? 0xFFFFFF : s.color()));
			new TextLayout(s.text(), g.getFont(), g.getFontRenderContext()).draw(g, pen, ascentPx);
			pen += advances[i];
		}
		if (gp != null) gp.dispose();
		if (gc != null) gc.dispose();
		UiAtlas.Region p = plain == null ? null : upload(plain);
		UiAtlas.Region c = colored == null ? null : upload(colored);
		return new Entry(p, c, width, height, ascentPx, new long[]{now}, fallback);
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

	private static UiAtlas.Region upload(BufferedImage img) {
		NativeImage n = new NativeImage(img.getWidth(), img.getHeight(), false);
		for (int y = 0; y < img.getHeight(); y++) for (int x = 0; x < img.getWidth(); x++) n.setPixel(x, y, img.getRGB(x, y));
		return UiAtlas.add(n);
	}
}
