package dev.fancybars.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import net.minecraft.world.entity.player.Player;

import dev.fancybars.protection.ItemProtection;
import dev.fancybars.util.SkyblockLocation;

/**
 * Draws the protection star on protected hotbar items (same injection point as Skyblocker).
 */
@Mixin(Hud.class)
public abstract class HudProtectionMixin {
	@Inject(method = "extractItemHotbar", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/Hud;extractSlot(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IILnet/minecraft/client/DeltaTracker;Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/item/ItemStack;I)V", ordinal = 0))
	private void fancybars$drawProtectionStar(CallbackInfo ci, @Local(name = "graphics") GuiGraphicsExtractor graphics, @Local(name = "i") int index, @Local(name = "x") int x, @Local(name = "y") int y, @Local(name = "player") Player player) {
		if (SkyblockLocation.isOnSkyblock() && ItemProtection.isItemProtected(player.getInventory().getNonEquipmentItems().get(index))) {
			ItemProtection.drawStar(graphics, x, y);
		}
	}
}
