package dev.fancybars.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.renderer.GameRenderer;

import dev.fancybars.util.gui.Chroma;

/** Frees the chroma uniforms once the GUI has drawn (same injection point as SkyHanni). */
@Mixin(GameRenderer.class)
public abstract class ChromaGameRendererMixin {
	@Inject(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/render/GuiRenderer;endFrame()V", shift = At.Shift.AFTER))
	private void fancybars$endChromaFrame(CallbackInfo ci) {
		Chroma.endFrame();
	}
}
