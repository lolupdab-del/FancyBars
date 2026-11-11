package dev.fancybars.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import net.minecraft.client.renderer.SubmitNodeCollection;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.network.chat.Component;

import dev.fancybars.players.Nicknames;

/**
 * The last stop of every name tag (including your own in F5), after SkyHanni, NoFrills and any other mod have
 * changed it: the custom name is swapped in here, and since this runs every frame its colours keep flowing.
 * Optional (require = 0) so a clash with another mod can never stop the game from loading.
 */
@Mixin({SubmitNodeStorage.class, SubmitNodeCollection.class})
public abstract class NameTagSubmitNicknameMixin {
	@ModifyVariable(method = "submitNameTag(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/world/phys/Vec3;ILnet/minecraft/network/chat/Component;ZILnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
			at = @At("HEAD"), argsOnly = true, require = 0)
	private Component fancybars$nicknames(Component name) {
		Component replaced = Nicknames.apply(name, Nicknames.Where.WORLD);
		dev.fancybars.players.NameTagDebug.hit("submitNameTag (" + getClass().getSimpleName() + ")", name, replaced);
		return replaced;
	}
}
