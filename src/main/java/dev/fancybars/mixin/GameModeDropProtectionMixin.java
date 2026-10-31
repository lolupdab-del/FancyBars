package dev.fancybars.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;

import dev.fancybars.protection.ItemProtection;
import dev.fancybars.util.SkyblockLocation;

/** Stops protected items being dropped with the drop key (26.3 moved dropping to the game mode). */
@Mixin(MultiPlayerGameMode.class)
public abstract class GameModeDropProtectionMixin {
	@Inject(method = "dropItem", at = @At("HEAD"), cancellable = true, require = 0)
	private void fancybars$preventDrop(LocalPlayer player, boolean fullStack, CallbackInfo ci) {
		if (SkyblockLocation.isOnSkyblock() && !SkyblockLocation.isInDungeons() && ItemProtection.isItemProtected(player.getMainHandItem())) {
			ci.cancel();
		}
	}
}
