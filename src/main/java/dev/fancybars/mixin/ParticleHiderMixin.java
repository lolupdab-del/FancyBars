package dev.fancybars.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.world.entity.Entity;

import dev.fancybars.config.FancyBarsConfig;

/**
 * Particle Hider: no particles at all. They're stopped before they're even made (so they cost nothing), whether they
 * come from the server, blocks, entities or other mods.
 */
@Mixin(ParticleEngine.class)
public abstract class ParticleHiderMixin {
	@Inject(method = "createParticle", at = @At("HEAD"), cancellable = true, require = 0)
	private void fancybars$hideCreated(ParticleOptions options, double x, double y, double z, double xa, double ya, double za, CallbackInfoReturnable<Particle> cir) {
		if (FancyBarsConfig.get().hideParticles) cir.setReturnValue(null);
	}

	@Inject(method = "add", at = @At("HEAD"), cancellable = true, require = 0)
	private void fancybars$hideAdded(Particle particle, CallbackInfo ci) {
		if (FancyBarsConfig.get().hideParticles) ci.cancel();
	}

	@Inject(method = "createTrackingEmitter(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/core/particles/ParticleOptions;I)V", at = @At("HEAD"), cancellable = true, require = 0)
	private void fancybars$hideEmitter(Entity entity, ParticleOptions options, int lifetime, CallbackInfo ci) {
		if (FancyBarsConfig.get().hideParticles) ci.cancel();
	}
}
