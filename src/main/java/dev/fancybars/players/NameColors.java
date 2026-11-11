package dev.fancybars.players;

import java.util.Map;

import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;

import dev.fancybars.config.FancyBarsConfig;
import dev.fancybars.util.gui.Chroma;

/**
 * Colours custom names at the moment GUI text is drawn, every frame.
 * <p>
 * Doing it this late means it works however the text got there: chat lines built once, and tab lists that other
 * mods rebuild from classic colour codes (which can't hold exact colours). Gradients are drawn with the Custom Chroma
 * shader using the name's own colours, so they're smooth per pixel and flow with your Chroma settings; rainbow names
 * use the normal chroma shader.
 */
public final class NameColors {
	private NameColors() {}

	/** GUI text: {@code text} with any custom names in it recoloured, or {@code text} itself when there are none. */
	public static FormattedCharSequence wrap(FormattedCharSequence text) {
		return wrap(text, true);
	}

	/**
	 * {@code gui}: drawn in the GUI, where the chroma shaders run. In the world (floating name tags) the colours are
	 * worked out letter by letter instead, each frame.
	 */
	public static FormattedCharSequence wrap(FormattedCharSequence text, boolean gui) {
		if (text == null) return null;
		FancyBarsConfig config = FancyBarsConfig.get();
		Map<String, SharedNames.Entry> all = SharedNames.all();
		if (!config.sharedNamesEnabled || all.isEmpty()) return text;

		// one char per code point so indexes line up with the glyphs
		int[] count = {0};
		text.accept((pos, style, cp) -> {
			count[0]++;
			return true;
		});
		int n = count[0];
		if (n < 3) return text;
		int[] positions = new int[n];
		Style[] styles = new Style[n];
		int[] codepoints = new int[n];
		char[] lower = new char[n];
		int[] at = {0};
		text.accept((pos, style, cp) -> {
			int i = at[0]++;
			positions[i] = pos;
			styles[i] = style;
			codepoints[i] = cp;
			lower[i] = cp > 0xFFFF ? '�' : Character.toLowerCase((char) cp);
			return true;
		});
		String flat = new String(lower);

		Style[] out = null;
		long now = Util.getMillis();
		for (Map.Entry<String, SharedNames.Entry> e : all.entrySet()) {
			SharedNames.Entry entry = e.getValue();
			if (!entry.chroma() && entry.colors().isEmpty()) continue;
			// your own nickname for this player wins over their shared name
			if (config.nicknamesEnabled && Nicknames.find(e.getKey()) != null) continue;
			String shown = SharedNames.shownText(e.getKey());
			if (shown.length() < 2) continue;
			int from = 0;
			int hit;
			while ((hit = flat.indexOf(shown, from)) >= 0) {
				int end = hit + shown.length();
				from = end;
				if (hit > 0 && isNameChar(lower[hit - 1]) || end < n && isNameChar(lower[end])) continue;
				if (out == null) out = styles.clone();
				int slot = SharedNames.slot(e.getKey());
				for (int i = hit; i < end; i++) {
					int rgb = colorFor(entry, gui ? slot : -1, i - hit, end - hit, now, gui);
					if (rgb != -1) out[i] = out[i].withColor(TextColor.fromRgb(rgb));
				}
			}
		}
		if (out == null) return text;
		Style[] finalStyles = out;
		return sink -> {
			for (int i = 0; i < n; i++) {
				if (!sink.accept(positions[i], finalStyles[i], codepoints[i])) return false;
			}
			return true;
		};
	}

	/** Colour for letter {@code i} of {@code len}, or -1 to leave it as it is. */
	private static int colorFor(SharedNames.Entry e, int slot, int i, int len, long now, boolean gui) {
		if (e.chroma()) {
			if (gui && Chroma.shaderEnabled()) return Chroma.TEXT_MARK;
			double h = now * (double) e.chromaSpeed() / 3000.0 + i * 0.06;
			return offClassic(Mth.hsvToRgb((float) (h - Math.floor(h)), 0.75f, 1f));
		}
		if (e.colors().size() == 1) return offClassic(e.colors().get(0));
		// the Custom Chroma shader draws it with this name's palette
		if (slot >= 0) return Chroma.TEXT_MARK_NAME + slot;
		// more gradient names than shader slots: the same flow, one colour per letter
		return flow(e.colors(), i, e.animated(), e.chromaSpeed(), now);
	}

	/**
	 * The shader's flow worked out per letter (for name tags in the world, where the GUI shader can't run): colours go
	 * A → B → … → last → … → A, a letter is about 6 GUI pixels, and it moves at your Chroma speed times the name's speed.
	 */
	/** Minecraft's 16 classic colour values. */
	private static final java.util.Set<Integer> CLASSIC = java.util.Set.of(0x000000, 0x0000AA, 0x00AA00, 0x00AAAA, 0xAA0000, 0xAA00AA,
			0xFFAA00, 0xAAAAAA, 0x555555, 0x5555FF, 0x55FF55, 0x55FFFF, 0xFF5555, 0xFF55FF, 0xFFFF55, 0xFFFFFF);

	/**
	 * Moves a computed colour off Minecraft's 16 classic colour values by one invisible step of blue. In world text
	 * (name tags, holograms) a hex colour that's exactly a classic one, like #5555FF (classic blue), is handled
	 * differently from the real classic colour and drew as a light-blue blink each time a flowing letter passed it.
	 */
	public static int offClassic(int rgb) {
		rgb &= 0xFFFFFF;
		return CLASSIC.contains(rgb) ? rgb ^ 1 : rgb;
	}

	public static int flow(java.util.List<Integer> stops, int letter, boolean animated, float speed, long now) {
		return offClassic(flowRaw(stops, letter, animated, speed, now));
	}

	private static int flowRaw(java.util.List<Integer> stops, int letter, boolean animated, float speed, long now) {
		if (stops.size() == 1) return stops.get(0);
		FancyBarsConfig c = FancyBarsConfig.get();
		// how many letters one loop of colours spans, matching the shader at a typical 1920 px / GUI scale 3 screen
		double lettersPerLoop = Math.max(2, c.nameFlowSize / 100.0 * 640.0 / 6.0);
		// in doubles: the clock counts milliseconds since the PC started, and a float can't hold that precisely after
		// a few days of uptime, so letters jumped between colours from one frame to the next
		double time = animated ? now / 50.0 * (c.chromaShaderSpeed / 360.0) * speed : 0;
		if (c.chromaShaderDirection >= 2) time = -time;
		double phase = letter / lettersPerLoop - time;
		float t = (float) (phase - Math.floor(phase));
		// first → last → back to first, same as the shader (no jump from the last colour straight to the first)
		float u = t < 0.5f ? t * 2 : (1 - t) * 2;
		float s = u * (stops.size() - 1);
		int i = Math.min((int) Math.floor(s), stops.size() - 2);
		float f = Math.clamp(s - i, 0f, 1f);
		f = f * f * (3 - 2 * f);
		int a = stops.get(i);
		int b = stops.get(i + 1);
		return Math.round(Mth.lerp(f, a >> 16 & 0xFF, b >> 16 & 0xFF)) << 16
				| Math.round(Mth.lerp(f, a >> 8 & 0xFF, b >> 8 & 0xFF)) << 8
				| Math.round(Mth.lerp(f, a & 0xFF, b & 0xFF));
	}

	private static boolean isNameChar(char c) {
		return c == '_' || Character.isLetterOrDigit(c);
	}
}
