package dev.fancybars.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.systems.RenderPass;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.gui.render.GuiRenderer;

import dev.fancybars.util.gui.Chroma;

/** Gives SBCLIENT's chroma shader its settings for every GUI draw (same injection point as SkyHanni). */
@Mixin(GuiRenderer.class)
public abstract class ChromaGuiRendererMixin {
	@Inject(method = "executeDrawRange", at = @At("HEAD"))
	private void fancybars$prepareChroma(CallbackInfo ci) {
		Chroma.prepare();
	}

	@Inject(method = "executeDrawRange", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/RenderPass;setUniform(Ljava/lang/String;Lcom/mojang/blaze3d/buffers/GpuBufferSlice;)V", shift = At.Shift.AFTER))
	private void fancybars$bindChroma(CallbackInfo ci, @Local RenderPass renderPass) {
		Chroma.bind(renderPass);
	}
}
