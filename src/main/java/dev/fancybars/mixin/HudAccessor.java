package dev.fancybars.mixin;

import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.client.gui.Hud;
import net.minecraft.network.chat.Component;

@Mixin(Hud.class)
public interface HudAccessor {
	/** The action bar text currently held by the HUD (after every mod has edited it). */
	@Accessor
	@Nullable Component getOverlayMessageString();
}
