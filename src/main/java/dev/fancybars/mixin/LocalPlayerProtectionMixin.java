package dev.fancybars.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.client.player.LocalPlayer;

import dev.fancybars.protection.ItemProtection;
import dev.fancybars.util.SkyblockLocation;

/**
 * Stops protected items being dropped with the drop key.
 */
@Mixin(LocalPlayer.class)
public abstract class LocalPlayerProtectionMixin {
	@Inject(method = "drop(Z)Z", at = @At("HEAD"), cancellable = true)
	private void fancybars$preventDrop(CallbackInfoReturnable<Boolean> cir) {
		LocalPlayer self = (LocalPlayer) (Object) this;
		if (SkyblockLocation.isOnSkyblock() && !SkyblockLocation.isInDungeons() && ItemProtection.isItemProtected(self.getMainHandItem())) {
			cir.setReturnValue(false);
		}
	}
}
