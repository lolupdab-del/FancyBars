package dev.fancybars.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;

import dev.fancybars.players.Nicknames;

/** Nicknames in the tab list (Hypixel's tab entries hold the names in their display text). */
@Mixin(PlayerTabOverlay.class)
public abstract class TabNicknameMixin {
	@Inject(method = "getNameForDisplay", at = @At("RETURN"), cancellable = true, require = 0)
	private void fancybars$nicknames(PlayerInfo info, CallbackInfoReturnable<Component> cir) {
		Component name = cir.getReturnValue();
		Component replaced = Nicknames.apply(name, Nicknames.Where.TAB);
		if (replaced != name) cir.setReturnValue(replaced);
	}
}
