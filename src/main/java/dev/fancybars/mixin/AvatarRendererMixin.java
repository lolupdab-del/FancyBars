package dev.fancybars.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.Avatar;

import dev.fancybars.config.FancyBarsConfig;
import dev.fancybars.players.PlayerSizes;

@Mixin(AvatarRenderer.class)
public abstract class AvatarRendererMixin {
	/** Custom player sizes. */
	@Inject(method = "scale(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;)V", at = @At("TAIL"))
	private void fancybars$scale(AvatarRenderState state, PoseStack poseStack, CallbackInfo ci) {
		float[] size = PlayerSizes.scaleFor(state.id);
		if (size[0] != 1 || size[1] != 1 || size[2] != 1) poseStack.scale(size[0], size[1], size[2]);
	}

	/** Keep the nametag right above the head of resized players. */
	@Inject(method = "extractRenderState(Lnet/minecraft/world/entity/Avatar;Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;F)V", at = @At("TAIL"))
	private void fancybars$nametagHeight(Avatar entity, AvatarRenderState state, float partialTick, CallbackInfo ci) {
		float[] size = PlayerSizes.scaleFor(entity);
		if (size[1] != 1 && state.nameTagAttachment != null) {
			state.nameTagAttachment = new net.minecraft.world.phys.Vec3(state.nameTagAttachment.x, state.nameTagAttachment.y * size[1], state.nameTagAttachment.z);
		}
	}

	/** Show your own nametag in third person (F5). */
	@Inject(method = "shouldShowName(Lnet/minecraft/world/entity/Avatar;D)Z", at = @At("HEAD"), cancellable = true)
	private void fancybars$ownNametag(Avatar entity, double distance, CallbackInfoReturnable<Boolean> cir) {
		Minecraft mc = Minecraft.getInstance();
		if (!FancyBarsConfig.get().ownNametagInF5 || entity != mc.player || mc.options.getCameraType() == CameraType.FIRST_PERSON) return;
		if (!entity.isInvisibleTo(mc.player)) cir.setReturnValue(true);
	}
}
