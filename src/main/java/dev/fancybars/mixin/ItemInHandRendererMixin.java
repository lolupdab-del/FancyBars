package dev.fancybars.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;

import dev.fancybars.config.FancyBarsConfig;

/** Viewmodel: move / rotate / scale the item in your hand and your empty arm in first person. */
@Mixin(ItemInHandRenderer.class)
public abstract class ItemInHandRendererMixin {
	private static final String SUBMIT = "submitArmWithItem(Lnet/minecraft/client/player/AbstractClientPlayer;FFLnet/minecraft/world/InteractionHand;FLnet/minecraft/world/item/ItemStack;FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;I)V";

	/** "No Swing": first-person swing animation stays at rest. */
	@org.spongepowered.asm.mixin.injection.ModifyVariable(method = SUBMIT, at = @At("HEAD"), argsOnly = true, ordinal = 2, require = 0)
	private float fancybars$noSwing(float swingProgress) {
		FancyBarsConfig c = FancyBarsConfig.get();
		return c.viewmodelEnabled && c.viewmodelNoSwing ? 0 : swingProgress;
	}

	@Inject(method = SUBMIT, at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/ItemInHandRenderer;renderItem(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;I)V"), require = 0)
	private void fancybars$itemViewmodel(AbstractClientPlayer player, float frameInterp, float xRot, InteractionHand hand, float swingProgress, ItemStack stack,
			float equipProgress, PoseStack poseStack, SubmitNodeCollector collector, int light, CallbackInfo ci) {
		FancyBarsConfig c = FancyBarsConfig.get();
		if (!c.viewmodelEnabled) return;
		float m = side(player, hand);
		poseStack.translate(c.viewmodelItemX * m, c.viewmodelItemY, c.viewmodelItemZ);
		poseStack.mulPose(Axis.XP.rotationDegrees(c.viewmodelItemRotX));
		poseStack.mulPose(Axis.YP.rotationDegrees(c.viewmodelItemRotY * m));
		poseStack.mulPose(Axis.ZP.rotationDegrees(c.viewmodelItemRotZ * m));
		poseStack.scale(c.viewmodelItemScale, c.viewmodelItemScale, c.viewmodelItemScale);
	}

	@Inject(method = SUBMIT, at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/ItemInHandRenderer;renderPlayerArm(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;IFFLnet/minecraft/world/entity/HumanoidArm;)V"), require = 0)
	private void fancybars$armViewmodel(AbstractClientPlayer player, float frameInterp, float xRot, InteractionHand hand, float swingProgress, ItemStack stack,
			float equipProgress, PoseStack poseStack, SubmitNodeCollector collector, int light, CallbackInfo ci) {
		FancyBarsConfig c = FancyBarsConfig.get();
		if (!c.viewmodelEnabled) return;
		float m = side(player, hand);
		// "Apply to Hand": the empty hand uses the held item settings
		boolean item = c.viewmodelApplyToHand;
		poseStack.translate((item ? c.viewmodelItemX : c.viewmodelArmX) * m, item ? c.viewmodelItemY : c.viewmodelArmY, item ? c.viewmodelItemZ : c.viewmodelArmZ);
		poseStack.mulPose(Axis.XP.rotationDegrees(item ? c.viewmodelItemRotX : c.viewmodelArmRotX));
		poseStack.mulPose(Axis.YP.rotationDegrees((item ? c.viewmodelItemRotY : c.viewmodelArmRotY) * m));
		poseStack.mulPose(Axis.ZP.rotationDegrees((item ? c.viewmodelItemRotZ : c.viewmodelArmRotZ) * m));
		float scale = item ? c.viewmodelItemScale : c.viewmodelArmScale;
		poseStack.scale(scale, scale, scale);
	}

	/** 1 for the right hand, -1 for the left (settings are mirrored for the off hand). */
	private static float side(AbstractClientPlayer player, InteractionHand hand) {
		HumanoidArm arm = hand == InteractionHand.MAIN_HAND ? player.getMainArm() : player.getMainArm().getOpposite();
		return arm == HumanoidArm.RIGHT ? 1 : -1;
	}
}
