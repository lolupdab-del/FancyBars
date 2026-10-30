package dev.fancybars.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import dev.fancybars.config.FancyBarsConfig;

/**
 * Viewmodel: move / rotate / scale the item in your hand and your empty hand in first person, plus No Swing and
 * Swing in Place. The transform is applied right after the hand's pose is pushed, around where the hand normally
 * sits, so the item and the bare hand react to the sliders the same way.
 */
@Mixin(ItemInHandRenderer.class)
public abstract class ItemInHandRendererMixin {
	private static final String SUBMIT = "submitArmWithItem(Lnet/minecraft/client/player/AbstractClientPlayer;FFLnet/minecraft/world/InteractionHand;FLnet/minecraft/world/item/ItemStack;FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;I)V";

	/** "No Swing": first-person swing animation stays at rest. */
	@ModifyVariable(method = SUBMIT, at = @At("HEAD"), argsOnly = true, ordinal = 2, require = 0)
	private float fancybars$noSwing(float swingProgress) {
		FancyBarsConfig c = FancyBarsConfig.get();
		return c.viewmodelEnabled && c.viewmodelNoSwing ? 0 : swingProgress;
	}

	@Inject(method = SUBMIT, at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;pushPose()V", shift = At.Shift.AFTER, ordinal = 0), require = 0)
	private void fancybars$viewmodel(AbstractClientPlayer player, float frameInterp, float xRot, InteractionHand hand, float swingProgress, ItemStack stack,
			float equipProgress, PoseStack poseStack, SubmitNodeCollector collector, int light, CallbackInfo ci) {
		FancyBarsConfig c = FancyBarsConfig.get();
		if (!c.viewmodelEnabled) return;
		HumanoidArm arm = hand == InteractionHand.MAIN_HAND ? player.getMainArm() : player.getMainArm().getOpposite();
		float side = arm == HumanoidArm.RIGHT ? 1 : -1;
		boolean empty = stack.isEmpty();
		boolean itemSettings = !empty || c.viewmodelApplyToHand;
		if (!empty || c.viewmodelApplyToHand || hasArmSettings(c)) {
			float x = itemSettings ? c.viewmodelItemX : c.viewmodelArmX;
			float y = itemSettings ? c.viewmodelItemY : c.viewmodelArmY;
			float z = itemSettings ? c.viewmodelItemZ : c.viewmodelArmZ;
			float rx = itemSettings ? c.viewmodelItemRotX : c.viewmodelArmRotX;
			float ry = itemSettings ? c.viewmodelItemRotY : c.viewmodelArmRotY;
			float rz = itemSettings ? c.viewmodelItemRotZ : c.viewmodelArmRotZ;
			float scale = itemSettings ? c.viewmodelItemScale : c.viewmodelArmScale;
			// pivot = where vanilla puts the hand, so rotating / scaling happens in place
			float px = side * (empty ? 0.64f : 0.56f);
			float py = empty ? -0.6f : -0.52f;
			float pz = -0.72f;
			poseStack.translate(x * side, y, z);
			poseStack.translate(px, py, pz);
			poseStack.mulPose(Axis.XP.rotationDegrees(rx));
			poseStack.mulPose(Axis.YP.rotationDegrees(ry * side));
			poseStack.mulPose(Axis.ZP.rotationDegrees(rz * side));
			poseStack.scale(scale, scale, scale);
			poseStack.translate(-px, -py, -pz);
		}

		// "Swing in Place": undo the swing's sideways / up / forward movement, keep the rotation
		if (c.viewmodelSwingInPlace && !(empty && c.viewmodelSwingInPlaceItemsOnly) && !c.viewmodelNoSwing && swingProgress > 0 && !player.isUsingItem() && !stack.is(Items.CROSSBOW) && !stack.is(Items.FILLED_MAP)) {
			float root = (float) Math.sqrt(swingProgress);
			if (empty) {
				float dx = -0.3f * (float) Math.sin(root * Math.PI);
				float dy = 0.4f * (float) Math.sin(root * Math.PI * 2);
				float dz = -0.4f * (float) Math.sin(swingProgress * Math.PI);
				poseStack.translate(-side * dx, -dy, -dz);
			} else {
				float dx = -0.4f * (float) Math.sin(root * Math.PI);
				float dy = 0.2f * (float) Math.sin(root * Math.PI * 2);
				float dz = -0.2f * (float) Math.sin(swingProgress * Math.PI);
				poseStack.translate(-side * dx, -dy, -dz);
			}
		}
	}

	private static boolean hasArmSettings(FancyBarsConfig c) {
		return c.viewmodelArmX != 0 || c.viewmodelArmY != 0 || c.viewmodelArmZ != 0 || c.viewmodelArmRotX != 0 || c.viewmodelArmRotY != 0
				|| c.viewmodelArmRotZ != 0 || c.viewmodelArmScale != 1;
	}
}
