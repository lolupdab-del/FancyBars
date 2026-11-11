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
			// right-click copy in a filtered tab (copy-chat mods lose track of the hovered message there)
			net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents.beforeMouseClick(chat).register((s, click) -> {
				if (click.button() == 1 && selected != Tab.ALL && FancyBarsConfig.get().chatTabsEnabled) copyAt(click.x(), click.y());
			});
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

		/** Never take keyboard focus: arrow keys must stay with the chat box / inventory (chat history, editing). */
		@Override
		public net.minecraft.client.gui.@org.jspecify.annotations.Nullable ComponentPath nextFocusPath(net.minecraft.client.gui.navigation.FocusNavigationEvent event) {
			return null;
		}

		@Override
		protected void updateWidgetNarration(net.minecraft.client.gui.narration.NarrationElementOutput output) {}
	}

	private static void select(Tab tab, ChatScreen screen) {
		Minecraft mc = Minecraft.getInstance();
		boolean changed = tab != selected;
		selected = tab;
		// re-filtering rebuilds the whole chat, so only when the tab actually changes
		if (changed) applyFilter();
		if (FancyBarsConfig.get().chatTabsSwitchChannel && tab.channel != null && mc.getConnection() != null) {
			mc.getConnection().sendCommand("chat " + tab.channel);
		}
		// rebuild the screen so the selected tab is highlighted (keeps what you typed)
		String typed = "";
		for (GuiEventListener child : screen.children()) if (child instanceof EditBox input) typed = input.getValue();
		mc.gui.setScreen(new ChatScreen(typed, false));
	}

	/** Copies the chat message under the mouse (text only, no colour codes) and says so in the action bar. */
	private static void copyAt(double mouseX, double mouseY) {
		Minecraft mc = Minecraft.getInstance();
		var chat = mc.gui.hud.getChat();
		LineUnderMouse finder = new LineUnderMouse(mc.font, mouseX, mouseY);
		chat.captureClickableText(finder, (int) mouseX, (int) mouseY, net.minecraft.client.gui.components.ChatComponent.DisplayMode.FOREGROUND);
		if (finder.hovered == null) return;
		for (var line : ((dev.fancybars.mixin.ChatComponentLinesAccessor) chat).fancybars$getTrimmedMessages()) {
			if (line.content() != finder.hovered) continue;
			String text = ChatFormatting.stripFormatting(line.parent().content().getString());
			if (text == null || text.isBlank()) return;
			mc.keyboardHandler.setClipboard(text.trim());
			if (mc.player != null) mc.player.sendOverlayMessage(Component.translatable("fancybars.chatTabs.copied").withStyle(ChatFormatting.GREEN));
			return;
		}
	}

	/** Collects the chat line drawn under the mouse (taking the chat's scale into account). */
	private static final class LineUnderMouse implements net.minecraft.client.gui.ActiveTextCollector {
		private final net.minecraft.client.gui.Font font;
		private final double mouseX;
		private final double mouseY;
		private Parameters defaults = new Parameters(new org.joml.Matrix3x2f());
		net.minecraft.util.FormattedCharSequence hovered;

		LineUnderMouse(net.minecraft.client.gui.Font font, double mouseX, double mouseY) {
			this.font = font;
			this.mouseX = mouseX;
			this.mouseY = mouseY;
		}

		@Override
		public Parameters defaultParameters() {
			return defaults;
		}

		@Override
		public void defaultParameters(Parameters parameters) {
			defaults = parameters;
		}

		@Override
		public void accept(net.minecraft.client.gui.TextAlignment alignment, int x, int y, Parameters parameters, net.minecraft.util.FormattedCharSequence text) {
			int left = alignment.calculateLeft(x, font, text);
			org.joml.Vector2f local = new org.joml.Matrix3x2f(parameters.pose()).invert().transformPosition((float) mouseX, (float) mouseY, new org.joml.Vector2f());
			if (local.y >= y && local.y < y + font.lineHeight && local.x >= left && local.x < left + Math.max(1, font.width(text))) hovered = text;
		}

		@Override
		public void acceptScrolling(Component text, int x, int left, int right, int top, int bottom, Parameters parameters) {}
	}

	public static void applyFilter() {
		Minecraft mc = Minecraft.getInstance();
		Tab tab = FancyBarsConfig.get().chatTabsEnabled ? selected : Tab.ALL;
		// (setting the filter already rebuilds the chat; a second rebuild here doubled the freeze when switching tabs)
		mc.gui.hud.getChat().setVisibleMessageFilter(message -> {
			if (tab == Tab.ALL) return true;
			String s = ChatFormatting.stripFormatting(message.content().getString());
			return s != null && tab.matches(s.trim());
		});
	}
}
