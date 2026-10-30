package dev.fancybars.spotify;

import org.joml.Matrix3x2fStack;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;

import dev.fancybars.util.gui.Theme;

/** Shared drawing: album art, icons, scrolling text, progress bar. */
public final class SpotifyUi {
	public static final int GREEN = 0xFF1DB954;

	private SpotifyUi() {}

	/** Album art drawn pixel-for-pixel on screen (smooth, rounded corners). */
	public static void art(GuiGraphicsExtractor graphics, String url, float x, float y, float size) {
		float gs = Theme.guiScale();
		Matrix3x2fStack pose = graphics.pose();
		float worldScale = pose.m00();
		int pixels = Math.max(8, Math.round(size * gs * Math.max(0.25f, worldScale)));
		Identifier tex = Spotify.art(url, pixels, 0.12f);
		if (tex == null) {
			Theme.roundedRect(graphics, Math.round(x), Math.round(y), Math.round(size), Math.round(size), Math.max(2, Math.round(size * 0.12f)), 0xFF1A2236);
			icon(graphics, "note", x + size * 0.3f, y + size * 0.3f, size * 0.4f, 0x66FFFFFF);
			return;
		}
		pose.pushMatrix();
		pose.translate(x, y);
		pose.scale(size / pixels, size / pixels);
		graphics.blit(RenderPipelines.GUI_TEXTURED, tex, 0, 0, 0, 0, pixels, pixels, pixels, pixels);
		pose.popMatrix();
	}

	/** One of the smooth Spotify icons (play, pause, next, previous, speaker, note, dot). */
	public static void icon(GuiGraphicsExtractor graphics, String name, float x, float y, float size, int color) {
		Matrix3x2fStack pose = graphics.pose();
		pose.pushMatrix();
		pose.translate(x, y);
		pose.scale(size / 64f, size / 64f);
		graphics.blitSprite(RenderPipelines.GUI_TEXTURED, dev.fancybars.FancyBarsMod.id("spotify/" + name), 0, 0, 64, 64, color);
		pose.popMatrix();
	}

	/** Text clipped to a width, scrolling back and forth if it's too long. */
	public static void scrolling(GuiGraphicsExtractor graphics, Component text, int x, int y, int width, int color, boolean bold) {
		Font font = Minecraft.getInstance().font;
		Component styled = bold ? Theme.bold(text) : Theme.styled(text);
		int w = font.width(styled);
		if (w <= width) {
			Theme.drawAtCapTop(graphics, font, styled, x, y, Theme.UI_CAP, color);
			return;
		}
		int overflow = w - width;
		double t = (Util.getMillis() % 8000) / 8000.0;
		double phase = t < 0.15 ? 0 : t < 0.5 ? (t - 0.15) / 0.35 : t < 0.65 ? 1 : 1 - (t - 0.65) / 0.35;
		graphics.enableScissor(x, y - 3, x + width, y + 12);
		Theme.drawAtCapTop(graphics, font, styled, x - (float) (overflow * phase), y, Theme.UI_CAP, color);
		graphics.disableScissor();
	}

	public static void progress(GuiGraphicsExtractor graphics, int x, int y, int width, float fraction, int color) {
		Theme.roundedRect(graphics, x, y, width, 3, 1, 0x40FFFFFF);
		int fill = Math.round(width * Math.clamp(fraction, 0, 1));
		if (fill > 0) Theme.roundedRect(graphics, x, y, Math.max(2, fill), 3, 1, color);
	}

	// ---------------------------------------------------------------- Icons

	public static void play(GuiGraphicsExtractor graphics, float cx, float cy, float size, int color) {
		icon(graphics, "play", cx - size / 2, cy - size / 2, size, color);
	}

	public static void pause(GuiGraphicsExtractor graphics, float cx, float cy, float size, int color) {
		icon(graphics, "pause", cx - size / 2, cy - size / 2, size, color);
	}

	public static void skip(GuiGraphicsExtractor graphics, float cx, float cy, float size, int color, boolean next) {
		icon(graphics, next ? "next" : "previous", cx - size / 2, cy - size / 2, size, color);
	}

	public static void speaker(GuiGraphicsExtractor graphics, float x, float y, int color) {
		icon(graphics, "speaker", x - 1, y - 1, 11, color);
	}
}
