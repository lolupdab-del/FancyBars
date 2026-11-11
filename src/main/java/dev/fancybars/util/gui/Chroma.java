package dev.fancybars.util.gui;

import java.nio.ByteBuffer;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.buffers.Std140SizeCalculator;
import com.mojang.blaze3d.pipeline.BindGroupLayout;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.systems.RenderPass;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.DynamicUniformStorage;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.util.Util;

import dev.fancybars.FancyBarsMod;
import dev.fancybars.config.FancyBarsConfig;

/**
 * SkyHanni-style chroma (ported from SkyHanni, https://github.com/hannibal002/SkyHanni): a smooth rainbow that
 * flows diagonally across the screen, done in a shader so every pixel gets its own hue.
 * <p>
 * Colours that should be drawn with it are "marked" (two low bits per channel set to a fixed pattern, which is
 * invisible). SkyblockGuiUtils's own drawing helpers check for the mark and switch to the chroma pipeline.
 */
public final class Chroma {
	public static final String UNIFORM = "SbclientChromaUniforms";

	private static final BindGroupLayout LAYOUT = BindGroupLayout.builder()
			.withUniform(UNIFORM, UniformType.UNIFORM_BUFFER)
			.build();

	public static final RenderPipeline FILL = RenderPipelines.register(RenderPipeline.builder(RenderPipelines.GUI_SNIPPET)
			.withLocation(FancyBarsMod.id("pipeline/chroma_fill"))
			.withFragmentShader(FancyBarsMod.id("core/chroma_standard"))
			.withBindGroupLayout(LAYOUT)
			.build());

	public static final RenderPipeline TEXTURED = RenderPipelines.register(RenderPipeline.builder(RenderPipelines.GUI_TEXTURED_SNIPPET)
			.withLocation(FancyBarsMod.id("pipeline/chroma_textured"))
			.withFragmentShader(FancyBarsMod.id("core/chroma_textured"))
			.withBindGroupLayout(LAYOUT)
			.build());

	/** Your own colours flowing like the chroma (Custom Chroma for maxed / ultimate enchants). */
	public static final RenderPipeline CUSTOM_ENCHANT = RenderPipelines.register(RenderPipeline.builder(RenderPipelines.GUI_TEXTURED_SNIPPET)
			.withLocation(FancyBarsMod.id("pipeline/chroma_custom_enchant"))
			.withFragmentShader(FancyBarsMod.id("core/chroma_custom_enchant"))
			.withBindGroupLayout(LAYOUT)
			.build());

	public static final RenderPipeline CUSTOM_ULTIMATE = RenderPipelines.register(RenderPipeline.builder(RenderPipelines.GUI_TEXTURED_SNIPPET)
			.withLocation(FancyBarsMod.id("pipeline/chroma_custom_ultimate"))
			.withFragmentShader(FancyBarsMod.id("core/chroma_custom_ultimate"))
			.withBindGroupLayout(LAYOUT)
			.build());

	/**
	 * Custom names flowing with their own colours, one pipeline per palette slot (the slot is baked in as a define, so
	 * the shader knows which palette to read).
	 */
	private static final RenderPipeline[] NAME_PIPELINES = new RenderPipeline[dev.fancybars.players.SharedNames.SLOTS];

	static {
		for (int i = 0; i < NAME_PIPELINES.length; i++) {
			NAME_PIPELINES[i] = RenderPipelines.register(RenderPipeline.builder(RenderPipelines.GUI_TEXTURED_SNIPPET)
					.withLocation(FancyBarsMod.id("pipeline/chroma_custom_name_" + i))
					.withFragmentShader(FancyBarsMod.id("core/chroma_custom_name"))
					.withShaderDefine("SLOT", i)
					.withBindGroupLayout(LAYOUT)
					.build());
		}
	}

	/** Text in colour {@code TEXT_MARK_NAME + slot} flows with that custom name's palette. */
	public static final int TEXT_MARK_NAME = 0xFF5780;

	/**
	 * 4 settings + 6 enchant colours, then the custom name palettes: {@code SLOTS * MAX_STOPS} colours and one info
	 * vec4 per slot (stop count, moving, flow size in pixels, speed). Every entry is a std140 vec4.
	 */
	private static final int NAME_COLORS = dev.fancybars.players.SharedNames.SLOTS * dev.fancybars.players.SharedNames.MAX_STOPS;
	private static final int SIZE;

	static {
		Std140SizeCalculator calc = new Std140SizeCalculator().putFloat().putFloat().putFloat().putInt();
		for (int i = 0; i < 24; i++) calc.putFloat();
		for (int i = 0; i < (NAME_COLORS + dev.fancybars.players.SharedNames.SLOTS) * 4; i++) calc.putFloat();
		SIZE = calc.get();
	}
	private static DynamicUniformStorage<Value> storage;
	private static GpuBufferSlice slice;
	private static boolean usedThisFrame;

	private Chroma() {}

	public static void init() {
		// touching the class registers the pipelines early
	}

	private record Value(float size, float time, float saturation, int forward, int[] colors, float nameSize) implements DynamicUniformStorage.DynamicUniform {
		@Override
		public void write(ByteBuffer buffer) {
			Std140Builder b = Std140Builder.intoBuffer(buffer).putFloat(size).putFloat(time).putFloat(saturation).putInt(forward);
			for (int c : colors) b.putFloat((c >> 16 & 0xFF) / 255f).putFloat((c >> 8 & 0xFF) / 255f).putFloat((c & 0xFF) / 255f).putFloat(1);
			dev.fancybars.players.SharedNames.Entry[] names = dev.fancybars.players.SharedNames.slotEntries();
			int stops = dev.fancybars.players.SharedNames.MAX_STOPS;
			for (dev.fancybars.players.SharedNames.Entry e : names) {
				for (int k = 0; k < stops; k++) {
					int c = e == null || e.colors().isEmpty() ? 0xFFFFFF : e.colors().get(Math.min(k, e.colors().size() - 1));
					b.putFloat((c >> 16 & 0xFF) / 255f).putFloat((c >> 8 & 0xFF) / 255f).putFloat((c & 0xFF) / 255f).putFloat(1);
				}
			}
			for (dev.fancybars.players.SharedNames.Entry e : names) {
				int count = e == null ? 1 : Math.clamp(e.colors().size(), 1, stops);
				b.putFloat(count).putFloat(e != null && e.animated() ? 1 : 0).putFloat(nameSize).putFloat(e == null ? 1 : e.chromaSpeed());
			}
		}
	}

	/** Text in these exact colours uses your Custom Chroma for enchants / ultimate enchants. */
	public static final int TEXT_MARK_CUSTOM_ENCHANT = 0xFF56FC;
	public static final int TEXT_MARK_CUSTOM_ULTIMATE = 0xFF56FA;

	/** Pipeline for glyphs in one of the chroma marker colours, or null for normal text. */
	public static RenderPipeline textPipelineFor(int rgb) {
		rgb &= 0xFFFFFF;
		if (rgb == TEXT_MARK && shaderEnabled()) return textPipeline();
		if (rgb == TEXT_MARK_CUSTOM_ENCHANT) {
			usedThisFrame = true;
			return CUSTOM_ENCHANT;
		}
		if (rgb == TEXT_MARK_CUSTOM_ULTIMATE) {
			usedThisFrame = true;
			return CUSTOM_ULTIMATE;
		}
		if (rgb >= TEXT_MARK_NAME && rgb < TEXT_MARK_NAME + NAME_PIPELINES.length) {
			usedThisFrame = true;
			return NAME_PIPELINES[rgb - TEXT_MARK_NAME];
		}
		return null;
	}

	// ---------------------------------------------------------------- marking

	private static final int MARK_MASK = 0x030303;
	private static final int MARK = 0x010203;

	/** Whether the shader chroma is on at all. */
	public static boolean shaderEnabled() {
		return FancyBarsConfig.get().chromaShader;
	}

	/** Marks a colour so SkyblockGuiUtils draws it with the shader chroma (no visible change otherwise). */
	public static int mark(int argb) {
		if (!shaderEnabled()) return argb;
		return argb & ~MARK_MASK | MARK;
	}

	public static boolean marked(int argb) {
		return (argb & MARK_MASK) == MARK && shaderEnabled();
	}

	/**
	 * A computed colour (a blend, a fade) that happens to land on the mark pattern by chance (1 in 64) would suddenly be
	 * drawn as chroma for that frame. This nudges such a colour by one step of blue so it can't be mistaken for a mark.
	 */
	public static int unmark(int argb) {
		return (argb & MARK_MASK) == MARK ? argb ^ 0x000001 : argb;
	}

	/** Text in this exact colour is drawn with the chroma shader (an odd pink nothing else uses). */
	public static final int TEXT_MARK = 0xFF56FE;

	/** Pipeline for chroma text glyphs (called from the GuiRenderer mixin). */
	public static RenderPipeline textPipeline() {
		usedThisFrame = true;
		return TEXTURED;
	}

	/** The pipeline to fill a rectangle of this colour with. */
	public static RenderPipeline fill(int argb) {
		if (!marked(argb)) return RenderPipelines.GUI;
		usedThisFrame = true;
		return FILL;
	}

	/** The pipeline to blit a texture tinted with this colour with. */
	public static RenderPipeline textured(int argb) {
		if (!marked(argb)) return RenderPipelines.GUI_TEXTURED;
		usedThisFrame = true;
		return TEXTURED;
	}

	// ---------------------------------------------------------------- uniforms (called from GuiRenderer mixin)

	/** Writes this frame's settings before a draw starts (writing them mid-draw leaves the shader with zeros). */
	public static void prepare() {
		if (slice == null) {
			if (storage == null) storage = new DynamicUniformStorage<>("SkyblockGuiUtils Chroma UBO", SIZE, 2);
			FancyBarsConfig c = FancyBarsConfig.get();
			float size = Math.max(1, c.chromaShaderSize) * Minecraft.getInstance().getWindow().getWidth() / 100f;
			// worked out in doubles and kept small: the clock counts milliseconds since the PC started, and as a float
			// that loses precision after a few days of uptime, so chroma (and custom name colours) jumped around.
			// The shaders only use it modulo whole loops; wrapping every 3600 loops keeps it precise and the speed
			// multiplier per name only sees a tiny step once in a long while.
			double ticks = Util.getMillis() / 50.0;
			boolean backward = c.chromaShaderDirection >= 2;
			double loops = (backward ? -ticks : ticks) * (c.chromaShaderSpeed / 360.0);
			float time = (float) (loops - Math.floor(loops / 3600.0) * 3600.0);
			int forward = c.chromaShaderDirection % 2 == 0 ? 1 : 0;
			int[] colors = {c.enchantMaxCustom1, c.enchantMaxCustom2, c.enchantMaxCustom3, c.enchantUltCustom1, c.enchantUltCustom2, c.enchantUltCustom3};
			// names are short, so their palette repeats over a smaller stretch than enchant chroma does
			float nameSize = Math.max(1, c.nameFlowSize) * Minecraft.getInstance().getWindow().getWidth() / 100f;
			slice = storage.writeUniform(new Value(size, time, Math.clamp(c.chromaShaderSaturation, 0, 1), forward, colors, nameSize));
		}
	}

	public static void bind(RenderPass pass) {
		if (slice == null) return;
		pass.setUniform(UNIFORM, slice);
	}

	/** After the GUI finished drawing this frame. */
	public static void endFrame() {
		if (storage != null) storage.endFrame();
		slice = null;
		usedThisFrame = false;
	}
}
