package dev.fancybars.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.sugar.Local;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.DyedItemColor;

import dev.fancybars.items.Customization;

/** Custom (and animated) leather dye colours (item customisation). */
@Mixin(DyedItemColor.class)
public abstract class DyedItemColorMixin {
	@ModifyReturnValue(method = "getOrDefault", at = @At("RETURN"))
	private static int fancybars$customDye(int original, @Local(argsOnly = true) ItemStack stack) {
		Integer custom = Customization.dye(stack);
		return custom != null ? 0xFF000000 | custom : original;
	}
}
