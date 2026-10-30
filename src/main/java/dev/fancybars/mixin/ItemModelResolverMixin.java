package dev.fancybars.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;

import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.world.item.ItemStack;

import dev.fancybars.items.ItemChanger;

/** Item customisation (from Skyblocker): draws an item with another item's model. */
@Mixin(ItemModelResolver.class)
public class ItemModelResolverMixin {
	@ModifyExpressionValue(method = {"appendItemLayers", "shouldPlaySwapAnimation"}, at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/item/ItemStack;get(Lnet/minecraft/core/component/DataComponentType;)Ljava/lang/Object;"), order = 900, require = 0)
	private Object fancybars$customModel(Object original, @Local(argsOnly = true) ItemStack stack) {
		if (!(original instanceof net.minecraft.resources.Identifier)) return original;
		net.minecraft.resources.Identifier custom = ItemChanger.modelFor(stack);
		return custom != null ? custom : original;
	}
}
