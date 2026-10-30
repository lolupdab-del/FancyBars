package dev.fancybars.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.Minecraft;

import dev.fancybars.protection.ItemProtection;
import dev.fancybars.util.SkyblockLocation;

/**
 * Handles the protect keybind while no screen is open (protects the held item).
 */
@Mixin(Minecraft.class)
public abstract class MinecraftProtectionMixin {
	@Inject(method = "handleKeybinds", at = @At("HEAD"))
	private void fancybars$handleProtectKey(CallbackInfo ci) {
		Minecraft self = (Minecraft) (Object) this;
		if (self.player != null && self.gui.screen() == null && SkyblockLocation.isOnSkyblock()) {
			ItemProtection.handleHotbarKey(self.player);
		}
	}
}
