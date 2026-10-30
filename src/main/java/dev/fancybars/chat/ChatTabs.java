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

/**
 * Chat tabs from Skytils: buttons in the chat screen to switch between All, Party, Guild, DMs and Co-op chat.
 * Picking a tab shows only that kind of message, switches your Hypixel chat channel (/chat ...) and can pre-fill the
 * matching command (/pc, /gc, /r, /cc) when you open chat.
 */
public final class ChatTabs {
	public enum Tab {
		ALL("a", "/ac ", 0xFFFF55),
		PARTY("p", "/pc ", 0x5555FF),
		GUILD("g", "/gc ", 0x55FF55),
		DM(null, "/r ", 0xFF55FF),
		COOP("coop", "/cc ", 0x55FFFF);

		final String channel;
		final String prefix;
		/** Hypixel's colour for that chat (All yellow, Party blue, Guild green, DMs pink, Co-op aqua). */
		final int color;

		Tab(String channel, String prefix, int color) {
			this.channel = channel;
			this.prefix = prefix;
			this.color = color;
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
			// left side, just above the chat box, styled like it
			int x = 2;
			int y = h - 14 - 13;
			for (Tab tab : Tab.values()) {
				TabButton button = new TabButton(tab, x, y, () -> select(tab, chat));
				Screens.getWidgets(chat).add(button);
				x += button.getWidth() + 2;
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

	/** A tab drawn like the chat input box: translucent black, text in that chat's colour, underlined when selected. */
	private static final class TabButton extends net.minecraft.client.gui.components.AbstractWidget {
		private final Tab tab;
		private final Runnable onPress;

		TabButton(Tab tab, int x, int y, Runnable onPress) {
			super(x, y, Minecraft.getInstance().font.width(Component.translatable("fancybars.chatTabs." + tab.name().toLowerCase(Locale.ROOT))) + 10, 12,
					Component.translatable("fancybars.chatTabs." + tab.name().toLowerCase(Locale.ROOT)));
			this.tab = tab;
			this.onPress = onPress;
		}

		@Override
		protected void extractWidgetRenderState(net.minecraft.client.gui.GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
			boolean active = tab == selected;
			int bg = active ? 0xC0000000 : isHovered() ? 0xA0000000 : 0x80000000;
			graphics.fill(getX(), getY(), getRight(), getBottom(), bg);
			if (active) graphics.fill(getX(), getBottom() - 1, getRight(), getBottom(), 0xFF000000 | tab.color);
			int text = active || isHovered() ? tab.color : dim(tab.color);
			graphics.text(Minecraft.getInstance().font, getMessage(), getX() + 5, getY() + 2, 0xFF000000 | text, true);
			if (isHovered()) graphics.requestCursor(com.mojang.blaze3d.platform.cursor.CursorTypes.POINTING_HAND);
		}

		private static int dim(int rgb) {
			int r = (rgb >> 16 & 0xFF) * 3 / 4;
			int g = (rgb >> 8 & 0xFF) * 3 / 4;
			int b = (rgb & 0xFF) * 3 / 4;
			return r << 16 | g << 8 | b;
		}

		@Override
		public void onClick(net.minecraft.client.input.MouseButtonEvent click, boolean doubled) {
			onPress.run();
		}

		@Override
		protected void updateWidgetNarration(net.minecraft.client.gui.narration.NarrationElementOutput output) {}
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
