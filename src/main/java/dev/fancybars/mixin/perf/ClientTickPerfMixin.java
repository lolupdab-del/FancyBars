package dev.fancybars.mixin.perf;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import dev.fancybars.util.Perf;

/** /sgu perf: overall time for comparison with the mod's own parts. */
@Mixin(net.minecraft.client.Minecraft.class)
public abstract class ClientTickPerfMixin {
	@Unique
	private static long fancybars$perfClientTickPerfMixin;

	@Inject(method = "tick", at = @At("HEAD"), require = 0)
	private void fancybars$startClientTickPerfMixin(CallbackInfo ci) {
		fancybars$perfClientTickPerfMixin = Perf.begin();
	}

	@Inject(method = "tick", at = @At("RETURN"), require = 0)
	private void fancybars$endClientTickPerfMixin(CallbackInfo ci) {
		Perf.end("[Client ticks: everything]", fancybars$perfClientTickPerfMixin);
	}
}
