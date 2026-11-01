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
	@WrapOperation(method = "addElementToMesh", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/state/gui/GuiElementRenderState;pipeline()Lcom/mojang/blaze3d/pipeline/RenderPipeline;"))
	private RenderPipeline fancybars$chromaText(GuiElementRenderState state, Operation<RenderPipeline> original) {
		if (state instanceof GlyphRenderState glyph && glyph.renderable() instanceof BakedSheetGlyph.GlyphInstance instance) {
			TextColor color = instance.style().getColor();
			if (color != null && (color.getValue() & 0xFFFFFF) == Chroma.TEXT_MARK && Chroma.shaderEnabled()) return Chroma.textPipeline();
		}
		return original.call(state);
	}
}
