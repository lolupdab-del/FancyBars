package dev.fancybars.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.client.player.FirstPersonHandsAndItems;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;

import dev.fancybars.config.FancyBarsConfig;

/** "No Re-equip Animation": a newly held item is swapped in straight away instead of after the dip. */
@Mixin(FirstPersonHandsAndItems.class)
public abstract class FirstPersonHandsMixin {
	@Inject(method = "shouldInstantlyReplaceVisibleItem", at = @At("HEAD"), cancellable = true, require = 0)
	private void fancybars$noReequip(ItemStack oldStack, ItemStack newStack, LocalPlayer player, CallbackInfoReturnable<Boolean> cir) {
		FancyBarsConfig c = FancyBarsConfig.get();
		if (c.viewmodelEnabled && c.viewmodelNoReequip) cir.setReturnValue(true);
	}
}
