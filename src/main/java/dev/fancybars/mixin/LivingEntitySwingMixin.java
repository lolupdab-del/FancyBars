package dev.fancybars.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;

import dev.fancybars.config.FancyBarsConfig;

/** Viewmodel "Ignore Haste": your swing always takes the normal time, whatever Haste / Mining Fatigue you have. */
@Mixin(LivingEntity.class)
public abstract class LivingEntitySwingMixin {
	private static final int NORMAL_SWING = 6;

	@ModifyReturnValue(method = "getCurrentSwingDuration", at = @At("RETURN"), require = 0)
	private int fancybars$ignoreHaste(int original) {
		FancyBarsConfig c = FancyBarsConfig.get();
		if (c.viewmodelEnabled && c.viewmodelIgnoreHaste && (Object) this == Minecraft.getInstance().player) return NORMAL_SWING;
		return original;
	}
}
