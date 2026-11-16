package dev.fancybars.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import dev.fancybars.items.Customization;

/** Custom item names (item customisation). */
@Mixin(ItemStack.class)
public abstract class ItemStackNameMixin {
	@ModifyReturnValue(method = "getHoverName", at = @At("RETURN"))
	private Component fancybars$customName(Component original) {
		long fancybars$perf = dev.fancybars.util.Perf.begin();
		try {
			Component custom = Customization.name((ItemStack) (Object) this);
			return custom != null ? custom : original;
		} finally {
			if (fancybars$perf != 0) dev.fancybars.util.Perf.end("ItemStackNameMixin customName", fancybars$perf);
		}
	}
}
