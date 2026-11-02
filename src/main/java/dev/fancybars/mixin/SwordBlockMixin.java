package dev.fancybars.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;

import dev.fancybars.config.FancyBarsConfig;

/**
 * 1.8 Sword Blocking: holding right click with a sword puts it in the old 1.8 blocking pose in first person.
 * Only the look changes; the right click still goes to the server as normal (abilities still work).
 */
@Mixin(ItemInHandRenderer.class)
public abstract class SwordBlockMixin {
	@Unique
	private static final String SUBMIT = "submitArmWithItem(Lnet/minecraft/client/player/AbstractClientPlayer;FFLnet/minecraft/world/InteractionHand;FLnet/minecraft/world/item/ItemStack;FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;I)V";

	/** Whether the hand being drawn right now is blocking. */
	@Unique
	private static boolean fancybars$blocking;

	@Inject(method = SUBMIT, at = @At("HEAD"))
	private void fancybars$checkBlocking(AbstractClientPlayer player, float frameInterp, float xRot, InteractionHand hand, float swingProgress, ItemStack stack,
			float equipProgress, PoseStack poseStack, SubmitNodeCollector collector, int light, CallbackInfo ci) {
		Minecraft mc = Minecraft.getInstance();
		fancybars$blocking = FancyBarsConfig.get().swordBlock && hand == InteractionHand.MAIN_HAND && mc.gui.screen() == null
				&& mc.options.keyUse.isDown() && !player.isUsingItem() && stack.is(ItemTags.SWORDS);
	}

	/** 1.8 doesn't swing the sword while blocking (unless Swing While Blocking is on, like 1.7 block-hitting). */
	@WrapWithCondition(method = SUBMIT, at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/ItemInHandRenderer;swingArm(FLcom/mojang/blaze3d/vertex/PoseStack;ILnet/minecraft/world/entity/HumanoidArm;)V", ordinal = 2))
	private boolean fancybars$noSwingWhileBlocking(ItemInHandRenderer self, float swing, PoseStack poseStack, int side, HumanoidArm arm) {
		return !fancybars$blocking || FancyBarsConfig.get().swordBlockSwing;
	}

	@Inject(method = SUBMIT, at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/ItemInHandRenderer;renderItem(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;I)V", ordinal = 1))
	private void fancybars$blockPose(AbstractClientPlayer player, float frameInterp, float xRot, InteractionHand hand, float swingProgress, ItemStack stack,
			float equipProgress, PoseStack poseStack, SubmitNodeCollector collector, int light, CallbackInfo ci) {
		if (!fancybars$blocking) return;
		float side = player.getMainArm() == HumanoidArm.RIGHT ? 1 : -1;
		poseStack.translate(side * -0.14142136f, 0.08f, 0.14142136f);
		poseStack.mulPose(Axis.XP.rotationDegrees(-102.25f));
		poseStack.mulPose(Axis.YP.rotationDegrees(side * 13.365f));
		poseStack.mulPose(Axis.ZP.rotationDegrees(side * 78.05f));
	}
}
