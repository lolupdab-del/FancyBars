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
 * invisible). SBCLIENT's own drawing helpers check for the mark and switch to the chroma pipeline.
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

	private static final int SIZE = new Std140SizeCalculator().putFloat().putFloat().putFloat().putInt().get();
	private static DynamicUniformStorage<Value> storage;
	private static GpuBufferSlice slice;
	private static boolean usedThisFrame;

	private Chroma() {}

	public static void init() {
		// touching the class registers the pipelines early
	}

	private record Value(float size, float time, float saturation, int forward) implements DynamicUniformStorage.DynamicUniform {
		@Override
		public void write(ByteBuffer buffer) {
			Std140Builder.intoBuffer(buffer).putFloat(size).putFloat(time).putFloat(saturation).putInt(forward);
		}
	}

	// ---------------------------------------------------------------- marking

	private static final int MARK_MASK = 0x030303;
	private static final int MARK = 0x010203;

	/** Whether the shader chroma is on at all. */
	public static boolean shaderEnabled() {
		return FancyBarsConfig.get().chromaShader;
	}

	/** Marks a colour so SBCLIENT draws it with the shader chroma (no visible change otherwise). */
	public static int mark(int argb) {
		if (!shaderEnabled()) return argb;
		return argb & ~MARK_MASK | MARK;
	}

	public static boolean marked(int argb) {
		return (argb & MARK_MASK) == MARK && shaderEnabled();
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

	public static void bind(RenderPass pass) {
		if (!usedThisFrame) return;
		if (slice == null) {
			if (storage == null) storage = new DynamicUniformStorage<>("SBCLIENT Chroma UBO", SIZE, 2);
			FancyBarsConfig c = FancyBarsConfig.get();
			float size = Math.max(1, c.chromaShaderSize) * Minecraft.getInstance().getWindow().getWidth() / 100f;
			float ticks = Util.getMillis() / 50f;
			boolean backward = c.chromaShaderDirection >= 2;
			float time = (backward ? -ticks : ticks) * (c.chromaShaderSpeed / 360f);
			int forward = c.chromaShaderDirection % 2 == 0 ? 1 : 0;
			slice = storage.writeUniform(new Value(size, time, Math.clamp(c.chromaShaderSaturation, 0, 1), forward));
		}
		pass.setUniform(UNIFORM, slice);
	}

	/** After the GUI finished drawing this frame. */
	public static void endFrame() {
		if (storage != null) storage.endFrame();
		slice = null;
		usedThisFrame = false;
	}
}
