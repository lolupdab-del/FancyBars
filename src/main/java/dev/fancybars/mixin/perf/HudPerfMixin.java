package dev.fancybars.mixin.perf;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import dev.fancybars.util.Perf;

/** /sgu perf: overall time for comparison with the mod's own parts. */
@Mixin(net.minecraft.client.gui.Hud.class)
public abstract class HudPerfMixin {
	@Unique
	private static long fancybars$perfHudPerfMixin;

	@Inject(method = "extractRenderState", at = @At("HEAD"), require = 0)
	private void fancybars$startHudPerfMixin(CallbackInfo ci) {
		fancybars$perfHudPerfMixin = Perf.begin();
	}

	@Inject(method = "extractRenderState", at = @At("RETURN"), require = 0)
	private void fancybars$endHudPerfMixin(CallbackInfo ci) {
		Perf.end("[HUD: every mod + vanilla]", fancybars$perfHudPerfMixin);
	}
}
