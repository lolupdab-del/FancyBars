package dev.fancybars.util.gui;

import com.mojang.renderpearl.api.pipeline.RenderPipeline;

import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.util.Util;

import dev.fancybars.config.FancyBarsConfig;

/**
 * Chroma on Minecraft 26.3. The 26.2 version draws SkyHanni's chroma with a custom shader; 26.3's new renderer
 * doesn't let mods add GUI shaders that way yet, so here chroma colours are worked out per frame instead:
 * borders and accents cycle through the rainbow, and enchant text gets a colour per letter (see {@link #textColor}).
 */
public final class Chroma {
	public static final int TEXT_MARK = 0xFF56FE;
	public static final int TEXT_MARK_CUSTOM_ENCHANT = 0xFF56FC;
	public static final int TEXT_MARK_CUSTOM_ULTIMATE = 0xFF56FA;

	private Chroma() {}

	public static void init() {}

	public static boolean shaderEnabled() {
		return FancyBarsConfig.get().chromaShader;
	}

	/** No shader on 26.3: colours stay as they are (they already cycle through the rainbow). */
	public static int mark(int argb) {
		return argb;
	}

	public static boolean marked(int argb) {
		return false;
	}

	public static RenderPipeline fill(int argb) {
		return RenderPipelines.GUI;
	}

	public static RenderPipeline textured(int argb) {
		return RenderPipelines.GUI_TEXTURED;
	}

	/**
	 * The colour for letter {@code index} of chroma text in the marker colour {@code mark}, flowing over time like
	 * SkyHanni's chroma (size, speed, saturation and direction settings apply).
	 */
	public static int textColor(int mark, int index) {
		FancyBarsConfig c = FancyBarsConfig.get();
		float size = Math.max(1, c.chromaShaderSize) / 3f; // letters per rainbow
		float time = Util.getMillis() / 50f * (c.chromaShaderSpeed / 360f);
		if (c.chromaShaderDirection >= 2) time = -time;
		float t = index / size - time;
		if (c.chromaShaderDirection % 2 == 1) t = -index / size - time;
		t = t - (float) Math.floor(t);
		if (mark == TEXT_MARK_CUSTOM_ENCHANT) return palette(t, c.enchantMaxCustom1, c.enchantMaxCustom2, c.enchantMaxCustom3);
		if (mark == TEXT_MARK_CUSTOM_ULTIMATE) return palette(t, c.enchantUltCustom1, c.enchantUltCustom2, c.enchantUltCustom3);
		return java.awt.Color.HSBtoRGB(t, Math.clamp(c.chromaShaderSaturation, 0, 1), 1) & 0xFFFFFF;
	}

	private static int palette(float t, int a, int b, int c) {
		float s = t * 3;
		int i = Math.min(2, (int) s);
		float f = s - i;
		f = f * f * (3 - 2 * f);
		int from = i == 0 ? a : i == 1 ? b : c;
		int to = i == 0 ? b : i == 1 ? c : a;
		return Theme.lerpColor(0xFF000000 | from, 0xFF000000 | to, f) & 0xFFFFFF;
	}
}
