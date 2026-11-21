package dev.fancybars.mixin;

import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;

/** The tab list's entries (in Hypixel's order), header and footer, for the Compact Tab List. */
@Mixin(PlayerTabOverlay.class)
public interface PlayerTabOverlayAccessor {
	@Accessor("header")
	Component fancybars$getHeader();

	@Accessor("footer")
	Component fancybars$getFooter();

	@Invoker("getPlayerInfos")
	List<PlayerInfo> fancybars$getPlayerInfos();
}
