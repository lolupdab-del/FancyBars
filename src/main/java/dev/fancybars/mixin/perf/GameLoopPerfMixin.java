package dev.fancybars.mixin.perf;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import dev.fancybars.util.Perf;

/** /sgu perf: overall time for comparison with the mod's own parts. */
@Mixin(net.minecraft.client.Minecraft.class)
public abstract class GameLoopPerfMixin {
	@Unique
	private static long fancybars$perfGameLoopPerfMixin;

	@Inject(method = "runTick", at = @At("HEAD"), require = 0)
	private void fancybars$startGameLoopPerfMixin(CallbackInfo ci) {
		fancybars$perfGameLoopPerfMixin = Perf.begin();
	}

	@Inject(method = "runTick", at = @At("RETURN"), require = 0)
	private void fancybars$endGameLoopPerfMixin(CallbackInfo ci) {
		Perf.loop(fancybars$perfGameLoopPerfMixin);
	}
}
