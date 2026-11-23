package dev.fancybars.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;

import dev.fancybars.config.FancyBarsConfig;

/**
 * Show Arm While Invisible: Minecraft doesn't draw your first-person arm while you're invisible (Sorrow armor makes you
 * invisible), so the hand vanishes. With this on, the arm is drawn anyway; only your own first-person view changes.
 */
@Mixin(ItemInHandRenderer.class)
public abstract class InvisibleArmMixin {
	@WrapOperation(method = "submitArmWithItem", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/AbstractClientPlayer;isInvisible()Z"), require = 0)
	private boolean fancybars$armWhileInvisible(AbstractClientPlayer player, Operation<Boolean> original) {
		return !FancyBarsConfig.get().showArmWhileInvisible && original.call(player);
	}

	@WrapOperation(method = {"renderOneHandedMap", "renderTwoHandedMap"}, at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;isInvisible()Z"), require = 0)
	private boolean fancybars$mapHandsWhileInvisible(LocalPlayer player, Operation<Boolean> original) {
		return !FancyBarsConfig.get().showArmWhileInvisible && original.call(player);
	}
}
