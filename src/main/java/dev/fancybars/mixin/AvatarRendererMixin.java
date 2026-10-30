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
		float scale = PlayerSizes.scaleFor(state.id);
		if (scale != 1) poseStack.scale(scale, scale, scale);
	}

	/** Show your own nametag in third person (F5). */
	@Inject(method = "shouldShowName(Lnet/minecraft/world/entity/Avatar;D)Z", at = @At("HEAD"), cancellable = true)
	private void fancybars$ownNametag(Avatar entity, double distance, CallbackInfoReturnable<Boolean> cir) {
		Minecraft mc = Minecraft.getInstance();
		if (!FancyBarsConfig.get().ownNametagInF5 || entity != mc.player || mc.options.getCameraType() == CameraType.FIRST_PERSON) return;
		if (!mc.options.hideGui && !entity.isInvisibleTo(mc.player)) cir.setReturnValue(true);
	}
}
