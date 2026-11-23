package dev.fancybars.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.resources.Identifier;

import dev.fancybars.items.GauntletHand;

/** Worn Gemstone Gauntlet: after the first-person arm is drawn, the gauntlet goes around its fist. */
@Mixin(AvatarRenderer.class)
public abstract class GauntletArmMixin {
	@Inject(method = "renderHand", at = @At("TAIL"), require = 0)
	private void fancybars$gauntletOnFist(PoseStack pose, SubmitNodeCollector collector, int light, Identifier skin, ModelPart arm, boolean sleeve, CallbackInfo ci) {
		// the gauntlet is only ever in your main hand
		var player = net.minecraft.client.Minecraft.getInstance().player;
		boolean right = player == null || player.getMainArm() == net.minecraft.world.entity.HumanoidArm.RIGHT;
		GauntletHand.drawOnFist(pose, collector, light, arm, right);
	}
}
