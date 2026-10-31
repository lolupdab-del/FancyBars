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
			if (config.spotifyEnabled && config.spotifyHud && Spotify.connected() && Spotify.state().active && Minecraft.getInstance().gui.screen() == null) {
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
		Theme.roundedRect(graphics, 0, 0, WIDTH, HEIGHT, 6, Theme.hudBg(0.7f, a));
		if (dev.fancybars.config.FancyBarsConfig.get().hudBorders) Theme.border(graphics, 0, 0, WIDTH, HEIGHT, 6, SpotifyUi.green(0x66), a);
		SpotifyUi.art(graphics, s.artUrl, 5, 5, 34);
		int tx = 45;
		int tw = WIDTH - tx - 18;
		SpotifyUi.scrolling(graphics, Component.literal(s.title), tx, 8, tw, Theme.hudText(a), true);
		SpotifyUi.scrolling(graphics, Component.literal(s.artist), tx, 20, tw, Theme.hudTextSoft(a), false);
		if (s.playing) SpotifyUi.pause(graphics, WIDTH - 10, 12, 8, SpotifyUi.green(a));
		else SpotifyUi.play(graphics, WIDTH - 9, 12, 8, SpotifyUi.green(a));
		float fraction = s.durationMs > 0 ? s.progressNow() / (float) s.durationMs : 0;
		SpotifyUi.progress(graphics, tx, 34, WIDTH - tx - 6, fraction, SpotifyUi.green(a));
		pose.popMatrix();
	}
}
