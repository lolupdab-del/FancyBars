package dev.fancybars.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.llamalad7.mixinextras.sugar.Local;

import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

import dev.fancybars.items.GauntletHand;

/** Worn Gemstone Gauntlet: the first-person hand holding it is drawn as an empty hand (the gauntlet goes on the fist). */
@Mixin(ItemInHandRenderer.class)
public abstract class GauntletHandMixin {
	private static final String SUBMIT = "submitArmWithItem(Lnet/minecraft/client/player/AbstractClientPlayer;FFLnet/minecraft/world/InteractionHand;FLnet/minecraft/world/item/ItemStack;FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;I)V";

	@ModifyVariable(method = SUBMIT, at = @At("HEAD"), argsOnly = true, require = 0)
	private ItemStack fancybars$wearGauntlet(ItemStack stack, @Local(argsOnly = true) InteractionHand hand) {
		return GauntletHand.swap(stack, hand);
	}

	@Inject(method = SUBMIT, at = @At("RETURN"), require = 0)
	private void fancybars$gauntletDone(CallbackInfo ci) {
		GauntletHand.done();
	}
}
