package dev.fancybars.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import com.llamalad7.mixinextras.sugar.Local;

import net.minecraft.client.renderer.entity.layers.EquipmentLayerRenderer;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.equipment.EquipmentAsset;
import net.minecraft.world.item.equipment.EquipmentAssets;

import dev.fancybars.items.Customization;

/** What armour an item looks like when worn (item customisation). */
@Mixin(EquipmentLayerRenderer.class)
public abstract class EquipmentLayerRendererMixin {
	@ModifyVariable(method = "renderLayers(Lnet/minecraft/client/resources/model/EquipmentClientInfo$LayerType;Lnet/minecraft/resources/ResourceKey;Lnet/minecraft/client/model/Model;Ljava/lang/Object;Lnet/minecraft/world/item/ItemStack;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/resources/Identifier;II)V",
			at = @At("HEAD"), argsOnly = true, require = 0)
	private ResourceKey<EquipmentAsset> fancybars$armorModel(ResourceKey<EquipmentAsset> asset, @Local(argsOnly = true) ItemStack stack) {
		Identifier custom = Customization.armorModel(stack);
		if (custom != null) return ResourceKey.create(EquipmentAssets.ROOT_ID, custom);
		// your dye on armor that has its own look (like a real animated dye): use plain leather so the dye shows
		if (Customization.forcesPlainLeather(stack) && asset != null && !asset.identifier().getNamespace().equals("minecraft")) {
			return ResourceKey.create(EquipmentAssets.ROOT_ID, Identifier.withDefaultNamespace("leather"));
		}
		return asset;
	}
}
