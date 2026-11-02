package dev.fancybars.util;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;

import dev.fancybars.config.FancyBarsConfig;

/** Whether SkyblockGuiUtils's on-screen HUDs should draw right now. */
public final class HudVisibility {
	private HudVisibility() {}

	/**
	 * No screen open, or "Show HUDs Always" is on: then they stay up behind every screen (chat, inventories,
	 * /sgu, other mods' menus). The Edit GUI screen draws its own copies, so the real ones hide there.
	 */
	public static boolean allowed() {
		Screen screen = Minecraft.getInstance().gui.screen();
		if (screen == null) return true;
		if (screen instanceof dev.fancybars.bars.BarEditorScreen) return false;
		return FancyBarsConfig.get().hudAlways;
	}
}
