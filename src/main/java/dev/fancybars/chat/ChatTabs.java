package dev.fancybars.chat;

import java.util.Locale;

import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.network.chat.Component;

import dev.fancybars.config.FancyBarsConfig;
import dev.fancybars.util.SkyblockLocation;
import dev.fancybars.util.gui.ModernButton;

/**
 * Chat tabs from Skytils: buttons in the chat screen to switch between All, Party, Guild, DMs and Co-op chat.
 * Picking a tab shows only that kind of message, switches your Hypixel chat channel (/chat ...) and can pre-fill the
 * matching command (/pc, /gc, /r, /cc) when you open chat.
 */
public final class ChatTabs {
	public enum Tab {
		ALL("a", "/ac "),
		PARTY("p", "/pc "),
		GUILD("g", "/gc "),
		DM(null, "/r "),
		COOP("coop", "/cc ");

		final String channel;
		final String prefix;

		Tab(String channel, String prefix) {
			this.channel = channel;
			this.prefix = prefix;
		}

		boolean matches(String s) {
			return switch (this) {
				case ALL -> true;
				case PARTY -> s.startsWith("Party > ") || s.contains("has invited you to join their party") || s.contains("joined the party")
						|| s.contains("left the party") || s.startsWith("The party was disbanded") || s.startsWith("You have joined") && s.contains("party");
				case GUILD -> s.startsWith("Guild > ") || s.startsWith("Officer > ");
				case DM -> s.startsWith("From ") || s.startsWith("To ");
				case COOP -> s.startsWith("Co-op > ");
			};
		}
	}

	private static Tab selected = Tab.ALL;

	private ChatTabs() {}

	public static void init() {
		ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> {
			FancyBarsConfig config = FancyBarsConfig.get();
			if (!config.chatTabsEnabled || !(screen instanceof ChatScreen chat) || !SkyblockLocation.isOnSkyblock()) return;
			Tab[] tabs = Tab.values();
			int bw = 40;
			int x = w - tabs.length * (bw + 2) - 2;
			int y = h - 14 - 16;
			for (Tab tab : tabs) {
				ModernButton button = new ModernButton(bw, 14, Component.translatable("fancybars.chatTabs." + tab.name().toLowerCase(Locale.ROOT)),
						tab == selected ? ModernButton.Style.PRIMARY : ModernButton.Style.SECONDARY, () -> select(tab, chat));
				button.setPosition(x, y);
				Screens.getWidgets(chat).add(button);
				x += bw + 2;
			}
			if (config.chatTabsPrefill) {
				for (GuiEventListener child : chat.children()) {
					if (child instanceof EditBox input && input.getValue().isEmpty()) {
						input.setValue(selected.prefix);
						input.moveCursorToEnd(false);
					}
				}
			}
		});
	}

	private static void select(Tab tab, ChatScreen screen) {
		selected = tab;
		Minecraft mc = Minecraft.getInstance();
		applyFilter();
		if (FancyBarsConfig.get().chatTabsSwitchChannel && tab.channel != null && mc.getConnection() != null) {
			mc.getConnection().sendCommand("chat " + tab.channel);
		}
		// rebuild the screen so the selected tab is highlighted (keeps what you typed)
		String typed = "";
		for (GuiEventListener child : screen.children()) if (child instanceof EditBox input) typed = input.getValue();
		mc.gui.setScreen(new ChatScreen(typed, false));
	}

	public static void applyFilter() {
		Minecraft mc = Minecraft.getInstance();
		Tab tab = FancyBarsConfig.get().chatTabsEnabled ? selected : Tab.ALL;
		mc.gui.hud.getChat().setVisibleMessageFilter(message -> {
			if (tab == Tab.ALL) return true;
			String s = ChatFormatting.stripFormatting(message.content().getString());
			return s != null && tab.matches(s.trim());
		});
		mc.gui.hud.getChat().rescaleChat();
	}
}
