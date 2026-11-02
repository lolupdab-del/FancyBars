package dev.fancybars.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;

import net.minecraft.world.entity.Entity;

import dev.fancybars.items.GlowingDroppedItems;

/** Glowing Dropped Items: the glow is in the item's rarity colour. */
@Mixin(Entity.class)
public abstract class GlowingItemsEntityMixin {
	@ModifyReturnValue(method = "getTeamColor", at = @At("RETURN"))
	private int fancybars$rarityGlow(int original) {
		int color = GlowingDroppedItems.color((Entity) (Object) this);
		return color != -1 ? color : original;
	}
}
