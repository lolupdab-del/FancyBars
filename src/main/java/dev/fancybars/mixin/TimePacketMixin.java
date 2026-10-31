package dev.fancybars.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.multiplayer.ClientPacketListener;

import dev.fancybars.hud.PerformanceHud;

/** Times the server's time updates (one every 20 ticks) to estimate TPS. */
@Mixin(ClientPacketListener.class)
public abstract class TimePacketMixin {
	@Inject(method = "handleSetTime", at = @At("HEAD"), require = 0)
	private void fancybars$timePacket(CallbackInfo ci) {
		PerformanceHud.onTimePacket();
	}
}
