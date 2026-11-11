package dev.fancybars.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

import dev.fancybars.protection.ItemProtection;

/**
 * Real animated dyes: Hypixel animates them by re-sending your armor many times a second, and while you move it sends
 * a bare copy (just the item and its colour, none of the SkyBlock data). That copy replaced your real armor, so
 * anything that knows the item by its SkyBlock id (your custom dye, trims, protection...) lost track of it whenever
 * you moved. The new colour is now copied onto the real item instead.
 * <p>
 * Hypixel sends these both as equipment updates and as updates to your inventory's armor slots; both are handled.
 */
@Mixin(ClientPacketListener.class)
public abstract class LocalEquipmentMixin {
	@WrapOperation(method = "lambda$handleSetEquipment$0", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;setItemSlot(Lnet/minecraft/world/entity/EquipmentSlot;Lnet/minecraft/world/item/ItemStack;)V"), require = 0)
	private static void fancybars$keepRealItem(LivingEntity entity, EquipmentSlot slot, ItemStack stack, Operation<Void> original) {
		if (entity instanceof LocalPlayer) stack = fancybars$merge(entity.getItemBySlot(slot), stack);
		original.call(entity, slot, stack);
	}

	/** Inventory slot updates for your armor (slots 5-8 of your inventory: helmet, chestplate, leggings, boots). */
	@ModifyExpressionValue(method = "handleContainerSetSlot", at = @At(value = "INVOKE", target = "Lnet/minecraft/network/protocol/game/ClientboundContainerSetSlotPacket;getItem()Lnet/minecraft/world/item/ItemStack;"), require = 0)
	private ItemStack fancybars$keepRealArmorSlot(ItemStack stack, @Local(argsOnly = true) ClientboundContainerSetSlotPacket packet) {
		LocalPlayer player = Minecraft.getInstance().player;
		int slot = packet.getSlot();
		if (player == null || packet.getContainerId() != 0 || slot < 5 || slot > 8) return stack;
		return fancybars$merge(player.inventoryMenu.getSlot(slot).getItem(), stack);
	}

	/** {@code incoming}, or your real {@code current} item with just the incoming colour when incoming is a bare copy of it. */
	@Unique
	private static ItemStack fancybars$merge(ItemStack current, ItemStack incoming) {
		if (current.isEmpty() || !current.is(incoming.getItem())) return incoming;
		if (!ItemProtection.getUuid(incoming).isEmpty() || ItemProtection.getUuid(current).isEmpty()) return incoming;
		ItemStack merged = current.copy();
		var color = incoming.get(DataComponents.DYED_COLOR);
		if (color != null) merged.set(DataComponents.DYED_COLOR, color);
		else merged.remove(DataComponents.DYED_COLOR);
		return merged;
	}
}
