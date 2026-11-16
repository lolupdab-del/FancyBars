package dev.fancybars.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import net.minecraft.client.gui.font.glyphs.BakedSheetGlyph;
import net.minecraft.client.gui.render.GuiRenderer;
import net.minecraft.client.renderer.state.gui.GlyphRenderState;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;
import net.minecraft.network.chat.TextColor;

import dev.fancybars.util.gui.Chroma;

/**
 * Text coloured with {@link Chroma#TEXT_MARK} (maxed enchants in tooltips) is drawn with the chroma shader
 * (same injection point as SkyHanni's chroma text).
 */
@Mixin(GuiRenderer.class)
public abstract class ChromaTextMixin {
	@org.spongepowered.asm.mixin.Unique
	private static final java.util.Map<TextColor, Boolean> fancybars$chromaColors = new com.google.common.collect.MapMaker().weakKeys().makeMap();

	@org.spongepowered.asm.mixin.Unique
	private static boolean fancybars$isSkyHanniChroma(TextColor color) {
		Boolean known = fancybars$chromaColors.get(color);
		if (known == null) {
			known = "chroma".equals(color.serialize());
			fancybars$chromaColors.put(color, known);
		}
		return known;
	}

	@WrapOperation(method = "addElementToMesh", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/state/gui/GuiElementRenderState;pipeline()Lcom/mojang/blaze3d/pipeline/RenderPipeline;"))
	private RenderPipeline fancybars$chromaText(GuiElementRenderState state, Operation<RenderPipeline> original) {
		long fancybars$perf = dev.fancybars.util.Perf.begin();
		try {
			if (state instanceof GlyphRenderState glyph && glyph.renderable() instanceof BakedSheetGlyph.GlyphInstance instance) {
				TextColor color = instance.style().getColor();
				if (color != null) {
					RenderPipeline chroma = Chroma.textPipelineFor(color.getValue());
					if (chroma != null) return chroma;
					// SkyHanni's "chroma" colour (its Enchant Parsing uses it for perfect enchants)
					// serialize() formats a "#RRGGBB" string for hex colours, which was done for every coloured letter on
					// screen every frame: work it out once per colour object instead
					if (Chroma.shaderEnabled() && fancybars$isSkyHanniChroma(color)) return Chroma.textPipeline();
				}
			}
			return original.call(state);
		} finally {
			if (fancybars$perf != 0) dev.fancybars.util.Perf.end("ChromaTextMixin chromaText", fancybars$perf);
		}
	}
}
