package dev.fancybars.mixin.perf;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import dev.fancybars.util.Perf;

/** /sgu perf: overall time for comparison with the mod's own parts. */
@Mixin(net.minecraft.client.gui.render.GuiRenderer.class)
public abstract class GuiRendererPerfMixin {
	@Unique
	private static long fancybars$perfGuiRendererPerfMixin;

	@Inject(method = "render", at = @At("HEAD"), require = 0)
	private void fancybars$startGuiRendererPerfMixin(CallbackInfo ci) {
		fancybars$perfGuiRendererPerfMixin = Perf.begin();
	}

	@Inject(method = "render", at = @At("RETURN"), require = 0)
	private void fancybars$endGuiRendererPerfMixin(CallbackInfo ci) {
		Perf.end("[GUI drawing on the GPU side]", fancybars$perfGuiRendererPerfMixin);
	}
}
