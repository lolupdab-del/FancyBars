package dev.fancybars.mixin.perf;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import dev.fancybars.util.Perf;

/** /sgu perf: overall time for comparison with the mod's own parts. */
@Mixin(net.minecraft.client.renderer.GameRenderer.class)
public abstract class GameRendererPerfMixin {
	@Unique
	private static long fancybars$perfGameRendererPerfMixin;

	@Inject(method = "render", at = @At("HEAD"), require = 0)
	private void fancybars$startGameRendererPerfMixin(CallbackInfo ci) {
		fancybars$perfGameRendererPerfMixin = Perf.begin();
	}

	@Inject(method = "render", at = @At("RETURN"), require = 0)
	private void fancybars$endGameRendererPerfMixin(CallbackInfo ci) {
		Perf.end("[Whole frame: everything]", fancybars$perfGameRendererPerfMixin);
		Perf.frame();
	}
}
