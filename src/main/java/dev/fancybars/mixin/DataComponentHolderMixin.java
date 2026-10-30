package dev.fancybars.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;

import net.minecraft.core.component.DataComponentHolder;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;

import dev.fancybars.items.Customization;

/** Armour trims, head textures and enchantment glint (item customisation). */
@Mixin(DataComponentHolder.class)
public interface DataComponentHolderMixin {
	@SuppressWarnings("unchecked")
	@ModifyReturnValue(method = "get", at = @At("RETURN"))
	private <T> T fancybars$customComponents(T original, DataComponentType<? extends T> type) {
		// cheap checks first: this runs for every component lookup of every item
		if (type != DataComponents.TRIM && type != DataComponents.PROFILE && type != DataComponents.ENCHANTMENT_GLINT_OVERRIDE) return original;
		if (!((Object) this instanceof ItemStack stack)) return original;
		Object custom;
		if (type == DataComponents.TRIM) custom = Customization.trim(stack);
		else if (type == DataComponents.PROFILE) custom = Customization.headProfile(stack);
		else custom = Customization.glint(stack);
		return custom != null ? (T) custom : original;
	}
}
