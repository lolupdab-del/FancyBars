package dev.fancybars.util;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;

import dev.fancybars.config.FancyBarsConfig;

/** Whether SBCLIENT's on-screen HUDs should draw right now. */
public final class HudVisibility {
	private HudVisibility() {}

	/** No screen open, or (with "Keep HUDs in Inventory and Chat") the open screen is chat or an inventory / chest. */
	public static boolean allowed() {
		Screen screen = Minecraft.getInstance().gui.screen();
		if (screen == null) return true;
		return FancyBarsConfig.get().hudInScreens && (screen instanceof ChatScreen || screen instanceof AbstractContainerScreen);
	}
}
