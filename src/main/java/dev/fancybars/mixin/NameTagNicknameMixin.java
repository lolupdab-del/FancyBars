package dev.fancybars.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;

import dev.fancybars.players.Nicknames;

/**
 * Nicknames above players' heads, when the tag's text is worked out. Other mods (SkyHanni, NoFrills) change name
 * tags after this, so {@link NameTagSubmitNicknameMixin} swaps again at the very end; swapped text is marked, so
 * the second pass never doubles it. Optional (require = 0): if another mod changes this method, the game still loads.
 */
@Mixin(EntityRenderer.class)
public abstract class NameTagNicknameMixin {
	@Inject(method = "getNameTag(Lnet/minecraft/world/entity/Entity;)Lnet/minecraft/network/chat/Component;", at = @At("RETURN"), cancellable = true, require = 0)
	private void fancybars$nicknames(Entity entity, CallbackInfoReturnable<Component> cir) {
		Component name = cir.getReturnValue();
		Component replaced = Nicknames.apply(name, Nicknames.Where.WORLD);
		dev.fancybars.players.NameTagDebug.hit("getNameTag", name, replaced);
		if (replaced != name) cir.setReturnValue(replaced);
	}
}
