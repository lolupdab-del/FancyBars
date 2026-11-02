package dev.fancybars.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

import dev.fancybars.protection.ItemProtection;

/**
 * Real animated dyes: Hypixel animates them by re-sending your armor every few ticks as a bare copy (just the item and
 * its colour, none of the SkyBlock data). That copy replaced your real armor, so anything that knows the item by its
 * SkyBlock id (your custom dye, trims, protection...) lost track of it. The new colour is now copied onto the real
 * item instead.
 */
@Mixin(ClientPacketListener.class)
public abstract class LocalEquipmentMixin {
	@WrapOperation(method = "lambda$handleSetEquipment$0", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;setItemSlot(Lnet/minecraft/world/entity/EquipmentSlot;Lnet/minecraft/world/item/ItemStack;)V"), require = 0)
	private static void fancybars$keepRealItem(LivingEntity entity, EquipmentSlot slot, ItemStack stack, Operation<Void> original) {
		if (entity instanceof LocalPlayer) {
			ItemStack current = entity.getItemBySlot(slot);
			if (!current.isEmpty() && current.is(stack.getItem()) && ItemProtection.getUuid(stack).isEmpty() && !ItemProtection.getUuid(current).isEmpty()) {
				ItemStack merged = current.copy();
				var color = stack.get(DataComponents.DYED_COLOR);
				if (color != null) merged.set(DataComponents.DYED_COLOR, color);
				else merged.remove(DataComponents.DYED_COLOR);
				stack = merged;
			}
		}
		original.call(entity, slot, stack);
	}
}
