package dev.fancybars.spotify;

import org.joml.Matrix3x2fStack;

import com.mojang.blaze3d.platform.InputConstants;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

import dev.fancybars.FancyBarsMod;
import dev.fancybars.config.FancyBarsConfig;
import dev.fancybars.protection.ItemProtection;
import dev.fancybars.util.gui.Theme;

/** Now-playing card on the HUD, plus Spotify keybinds (unbound until you set them in Controls). */
public final class SpotifyHud {
	public static final int WIDTH = 170;
	public static final int HEIGHT = 44;

	private static KeyMapping openKey;
	private static KeyMapping playKey;
	private static KeyMapping nextKey;
	private static KeyMapping prevKey;
	private static KeyMapping volUpKey;
	private static KeyMapping volDownKey;

	private SpotifyHud() {}

	public static void init() {
		int none = InputConstants.UNKNOWN.getValue();
		openKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.fancybars.spotify.open", none, ItemProtection.KEY_CATEGORY));
		playKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.fancybars.spotify.play", none, ItemProtection.KEY_CATEGORY));
		nextKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.fancybars.spotify.next", none, ItemProtection.KEY_CATEGORY));
		prevKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.fancybars.spotify.previous", none, ItemProtection.KEY_CATEGORY));
		volUpKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.fancybars.spotify.volumeUp", none, ItemProtection.KEY_CATEGORY));
		volDownKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.fancybars.spotify.volumeDown", none, ItemProtection.KEY_CATEGORY));
		ClientTickEvents.END_CLIENT_TICK.register(mc -> {
			while (openKey.consumeClick()) mc.gui.setScreen(new SpotifyScreen(mc.gui.screen()));
			while (playKey.consumeClick()) Spotify.togglePlay();
			while (nextKey.consumeClick()) Spotify.next();
			while (prevKey.consumeClick()) Spotify.previous();
			while (volUpKey.consumeClick()) Spotify.setVolume(Math.max(0, Spotify.state().volume) + 10);
			while (volDownKey.consumeClick()) Spotify.setVolume(Math.max(0, Spotify.state().volume) - 10);
		});
		HudElementRegistry.attachElementAfter(VanillaHudElements.HOTBAR, FancyBarsMod.id("spotify"), (graphics, tickCounter) -> {
			FancyBarsConfig config = FancyBarsConfig.get();
			if (config.spotifyEnabled && config.spotifyHud && Spotify.connected() && Spotify.state().active && dev.fancybars.util.HudVisibility.allowed()) {
				render(graphics);
			}
		});
	}

	public static float[] position(int screenW, int screenH) {
		FancyBarsConfig config = FancyBarsConfig.get();
		if (config.spotifyX < 0 || config.spotifyY < 0) return new float[]{screenW - WIDTH * config.spotifyScale - 6, 6};
		return new float[]{config.spotifyX * screenW, config.spotifyY * screenH};
	}

	public static void render(GuiGraphicsExtractor graphics) {
		float[] pos = position(graphics.guiWidth(), graphics.guiHeight());
		renderAt(graphics, pos[0], pos[1], FancyBarsConfig.get().spotifyScale, Spotify.state(), 1);
	}

	public static Spotify.State sample() {
		Spotify.State s = new Spotify.State();
		s.active = true;
		s.playing = true;
		s.title = "Song Title";
		s.artist = "Artist";
		s.durationMs = 200_000;
		s.progressMs = 80_000;
		return s;
	}

	public static void renderAt(GuiGraphicsExtractor graphics, float x, float y, float scale, Spotify.State s, float alpha) {
		int a = Math.round(alpha * 255);
		Matrix3x2fStack pose = graphics.pose();
		pose.pushMatrix();
		pose.translate(x, y);
		pose.scale(scale, scale);
		if (FancyBarsConfig.get().spotifyHudBackground) {
			Theme.roundedRect(graphics, 0, 0, WIDTH, HEIGHT, 6, Theme.hudBg(0.88f, a));
			if (FancyBarsConfig.get().hudBorders) Theme.border(graphics, 0, 0, WIDTH, HEIGHT, 6, Theme.hudLine(), a);
		}
		SpotifyUi.art(graphics, s.artUrl, 5, 5, 34);
		int tx = 45;
		int tw = WIDTH - tx - 18;
		SpotifyUi.scrolling(graphics, Component.literal(s.title), tx, 8, tw, Theme.hudText(a), true);
		SpotifyUi.scrolling(graphics, Component.literal(s.artist), tx, 20, tw, Theme.hudTextSoft(a), false);
		if (s.playing) SpotifyUi.pause(graphics, WIDTH - 10, 12, 8, SpotifyUi.green(a));
		else SpotifyUi.play(graphics, WIDTH - 9, 12, 8, SpotifyUi.green(a));
		float fraction = s.durationMs > 0 ? s.progressNow() / (float) s.durationMs : 0;
		int barX = tx;
		int barW = WIDTH - tx - 6;
		int timeMode = FancyBarsConfig.get().spotifyHudTime;
		if (timeMode != 0 && s.durationMs > 0) {
			// time left until the song ends, next to the bar
			net.minecraft.client.gui.Font font = net.minecraft.client.Minecraft.getInstance().font;
			Component left = Theme.small(Component.literal("-" + Spotify.time(Math.max(0, s.durationMs - s.progressNow()))));
			// reserve room for the widest the countdown can get, so the bar doesn't change size as the digits change
			float w = slotWidth(font, s.durationMs);
			float textY = 35.5f - Theme.SMALL_CAP / 2f;
			if (timeMode == 1) {
				Theme.drawAtCapTop(graphics, font, left, tx + w - font.width(left), textY, Theme.SMALL_CAP, Theme.hudTextSoft(a));
				barX += Math.round(w) + 5;
				barW -= Math.round(w) + 5;
			} else {
				Theme.drawAtCapTop(graphics, font, left, WIDTH - 6 - font.width(left), textY, Theme.SMALL_CAP, Theme.hudTextSoft(a));
				barW -= Math.round(w) + 5;
			}
		}
		SpotifyUi.progress(graphics, barX, 34, barW, fraction, SpotifyUi.green(a));
		pose.popMatrix();
	}

	/** Width of the countdown with every digit as the widest digit, for a song of this length. */
	private static float slotWidth(net.minecraft.client.gui.Font font, long durationMs) {
		float widest = 0;
		char widestDigit = '0';
		for (char d = '0'; d <= '9'; d++) {
			float dw = font.width(Theme.small(Component.literal(String.valueOf(d))));
			if (dw > widest) {
				widest = dw;
				widestDigit = d;
			}
		}
		String pattern = ("-" + Spotify.time(durationMs)).replaceAll("[0-9]", String.valueOf(widestDigit));
		return font.width(Theme.small(Component.literal(pattern)));
	}
}
