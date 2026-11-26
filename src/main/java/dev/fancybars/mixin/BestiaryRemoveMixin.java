package dev.fancybars.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;

import dev.fancybars.combat.BestiaryTracker;

/** Tells the Bestiary Tracker when the server removes an entity (a mob that died, for most of Hypixel's mobs). */
@Mixin(ClientLevel.class)
public abstract class BestiaryRemoveMixin {
	@Inject(method = "removeEntity", at = @At("HEAD"))
	private void fancybars$onRemove(int id, Entity.RemovalReason reason, CallbackInfo ci) {
		Entity entity = ((ClientLevel) (Object) this).getEntity(id);
		if (entity != null) BestiaryTracker.onRemoved(entity);
	}
}
