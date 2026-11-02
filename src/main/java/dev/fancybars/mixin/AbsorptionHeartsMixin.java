package dev.fancybars.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import net.minecraft.client.gui.Hud;

import dev.fancybars.config.FancyBarsConfig;

/** Hide Absorption Hearts: the hearts are drawn as if you had no absorption (so the rows stay laid out right). */
@Mixin(Hud.class)
public abstract class AbsorptionHeartsMixin {
	@ModifyExpressionValue(method = "extractPlayerHealth", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;getAbsorptionAmount()F"), require = 0)
	private float fancybars$hideAbsorption(float absorption) {
		return FancyBarsConfig.get().hideAbsorptionHearts ? 0 : absorption;
	}
}
