package dev.fancybars.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;

import dev.fancybars.items.GlowingDroppedItems;

/** Glowing Dropped Items (from SkyHanni): dropped items get a glow outline. */
@Mixin(Minecraft.class)
public abstract class GlowingItemsMinecraftMixin {
	@ModifyReturnValue(method = "shouldEntityAppearGlowing", at = @At("RETURN"))
	private boolean fancybars$glowDroppedItems(boolean original, Entity entity) {
		return original || GlowingDroppedItems.color(entity) != -1;
	}
}
