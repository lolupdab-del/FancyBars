package dev.fancybars.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.client.renderer.entity.layers.CreeperPowerLayer;
import net.minecraft.client.renderer.entity.state.CreeperRenderState;

import dev.fancybars.config.FancyBarsConfig;

/**
 * Hide Ghost Aura: ghosts in the Mist are invisible charged creepers, so all you see of them is the charged swirl. With
 * this on, the swirl isn't drawn on invisible creepers (normal charged creepers keep it).
 */
@Mixin(CreeperPowerLayer.class)
public abstract class GhostAuraMixin {
	@Inject(method = "isPowered(Lnet/minecraft/client/renderer/entity/state/CreeperRenderState;)Z", at = @At("HEAD"), cancellable = true)
	private void fancybars$hideGhostAura(CreeperRenderState state, CallbackInfoReturnable<Boolean> cir) {
		if (state.isInvisible && FancyBarsConfig.get().hideGhostAura) cir.setReturnValue(false);
	}
}
