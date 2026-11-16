package dev.fancybars.mixin.perf;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import dev.fancybars.util.Perf;

/** /sgu perf: overall time for comparison with the mod's own parts. */
@Mixin(net.minecraft.client.renderer.GameRenderer.class)
public abstract class LevelPerfMixin {
	@Unique
	private static long fancybars$perfLevelPerfMixin;

	@Inject(method = "renderLevel", at = @At("HEAD"), require = 0)
	private void fancybars$startLevelPerfMixin(CallbackInfo ci) {
		fancybars$perfLevelPerfMixin = Perf.begin();
	}

	@Inject(method = "renderLevel", at = @At("RETURN"), require = 0)
	private void fancybars$endLevelPerfMixin(CallbackInfo ci) {
		Perf.end("[World: everything]", fancybars$perfLevelPerfMixin);
	}
}
