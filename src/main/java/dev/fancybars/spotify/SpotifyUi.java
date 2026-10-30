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

	public static void art(GuiGraphicsExtractor graphics, String url, int x, int y, int size) {
		Identifier tex = Spotify.art(url);
		Theme.roundedRect(graphics, x, y, size, size, 4, 0xFF1A2236);
		if (tex == null) {
			Theme.centeredInBox(graphics, Minecraft.getInstance().font, Component.literal("♪"), x + size / 2f, y, size, Theme.TEXT_MUTED);
			return;
		}
		graphics.blit(RenderPipelines.GUI_TEXTURED, tex, x, y, 0, 0, size, size, size, size);
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

	// ---------------------------------------------------------------- Icons (drawn, so no font glyphs are needed)

	public static void play(GuiGraphicsExtractor graphics, float cx, float cy, float size, int color) {
		Matrix3x2fStack pose = graphics.pose();
		pose.pushMatrix();
		pose.translate(cx - size * 0.35f, cy - size / 2);
		pose.scale(size / 16f, size / 16f);
		for (int row = 0; row < 16; row++) {
			int len = (int) Math.round((8 - Math.abs(row + 0.5 - 8)) * 1.6);
			graphics.fill(0, row, Math.max(1, len), row + 1, color);
		}
		pose.popMatrix();
	}

	public static void pause(GuiGraphicsExtractor graphics, float cx, float cy, float size, int color) {
		Matrix3x2fStack pose = graphics.pose();
		pose.pushMatrix();
		pose.translate(cx - size / 2, cy - size / 2);
		pose.scale(size / 16f, size / 16f);
		graphics.fill(3, 1, 7, 15, color);
		graphics.fill(9, 1, 13, 15, color);
		pose.popMatrix();
	}

	/** Skip icon: triangle + bar, pointing right (next) or left (previous). */
	public static void skip(GuiGraphicsExtractor graphics, float cx, float cy, float size, int color, boolean next) {
		Matrix3x2fStack pose = graphics.pose();
		pose.pushMatrix();
		pose.translate(cx, cy);
		if (!next) pose.scale(-1, 1);
		pose.translate(-size / 2, -size / 2);
		pose.scale(size / 16f, size / 16f);
		for (int row = 2; row < 14; row++) {
			int len = (int) Math.round((6 - Math.abs(row + 0.5 - 8)) * 1.6);
			graphics.fill(2, row, 2 + Math.max(1, len), row + 1, color);
		}
		graphics.fill(12, 2, 14, 14, color);
		pose.popMatrix();
	}

	public static void speaker(GuiGraphicsExtractor graphics, float x, float y, int color) {
		Matrix3x2fStack pose = graphics.pose();
		pose.pushMatrix();
		pose.translate(x, y);
		graphics.fill(0, 3, 3, 7, color);
		for (int i = 0; i < 4; i++) graphics.fill(3 + i, 3 - i, 4 + i, 7 + i, color);
		pose.popMatrix();
	}
}
