package dev.fancybars.actionbar;

import org.joml.Matrix3x2fStack;
import org.jspecify.annotations.Nullable;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.Util;

import dev.fancybars.config.FancyBarsConfig;
import dev.fancybars.mixin.HudAccessor;
import dev.fancybars.util.gui.Theme;

/**
 * Customisable action bar: hide it, move / scale it (editor), and optionally draw it with Fancy Bars' renderer
 * (smooth font, shadow, rounded background, custom colour, fade).
 * Which parts of the text are shown is handled by the action bar parser in {@code StatusBarTracker}.
 */
public final class ActionBar {
	/** Vanilla draws the action bar centred on (width / 2, height - 68), with the text at y - 4. */
	public static final int VANILLA_FROM_BOTTOM = 68;
	private static final long SHOW_MS = 3000;
	private static final long FADE_MS = 1000;

	private static @Nullable Component lastSeen;
	private static long lastChange;

	private ActionBar() {}

	public static void init() {
		HudElementRegistry.replaceElement(VanillaHudElements.OVERLAY_MESSAGE, original -> (graphics, tickCounter) -> {
			FancyBarsConfig config = FancyBarsConfig.get();
			if (!config.actionBarShown) return;

			if (!config.actionBarCustomize) {
				// Vanilla rendering, just moved / scaled
				if (config.actionBarX == 0 && config.actionBarY == 0 && config.actionBarScale == 1) {
					original.extractRenderState(graphics, tickCounter);
					return;
				}
				Matrix3x2fStack pose = graphics.pose();
				pose.pushMatrix();
				applyTransform(graphics.guiWidth(), graphics.guiHeight(), pose);
				original.extractRenderState(graphics, tickCounter);
				pose.popMatrix();
				return;
			}

			Component message = current();
			if (message == null || message.getString().isBlank()) return;
			long now = Util.getMillis();
			if (message != lastSeen) {
				lastSeen = message;
				lastChange = now;
			}
			long age = now - lastChange;
			if (age >= SHOW_MS) return;
			float alpha = config.actionBarFade ? Math.clamp((SHOW_MS - age) / (float) FADE_MS, 0, 1) : 1;
			if (alpha < 0.02f) return;
			int[] center = center(graphics.guiWidth(), graphics.guiHeight());
			draw(graphics, message, center[0], center[1], config.actionBarScale, alpha);
		});
	}

	public static @Nullable Component current() {
		return ((HudAccessor) Minecraft.getInstance().gui.hud).getOverlayMessageString();
	}

	public static Component sample() {
		return Component.literal("§c1,240/1,240❤     §a512§a❈ Defense     §b688/688✎ Mana");
	}

	/** Centre of the action bar on screen with the configured offset. */
	public static int[] center(int screenW, int screenH) {
		FancyBarsConfig config = FancyBarsConfig.get();
		return new int[]{screenW / 2 + config.actionBarX, screenH - VANILLA_FROM_BOTTOM + config.actionBarY};
	}

	public static void applyTransform(int screenW, int screenH, Matrix3x2fStack pose) {
		FancyBarsConfig config = FancyBarsConfig.get();
		float cx = screenW / 2f;
		float cy = screenH - VANILLA_FROM_BOTTOM;
		pose.translate(cx + config.actionBarX, cy + config.actionBarY);
		pose.scale(config.actionBarScale, config.actionBarScale);
		pose.translate(-cx, -cy);
	}

	/** The text as it will be drawn with the current settings (colour + font). */
	public static Component styledText(Component message) {
		FancyBarsConfig config = FancyBarsConfig.get();
		if (!config.actionBarCustomize) return message;
		MutableComponent text;
		if (config.actionBarUseCustomColor) {
			String plain = ChatFormatting.stripFormatting(message.getString());
			text = Component.literal(plain == null ? "" : plain).withColor(config.actionBarColor & 0xFFFFFF);
		} else {
			text = message.copy();
		}
		if (config.actionBarSmoothFont) {
			FontDescription font = Theme.boldFont();
			text = text.withStyle(style -> style.withFont(font));
		}
		return text;
	}

	/** Size of the drawn action bar (text plus background padding), unscaled: {width, height}. */
	public static int[] size(Component message) {
		Font font = Minecraft.getInstance().font;
		return new int[]{font.width(styledText(message)) + 14, 17};
	}

	/**
	 * Draws an action bar centred on (cx, cy) like vanilla does, with the configured style.
	 */
	public static void draw(GuiGraphicsExtractor graphics, Component message, float cx, float cy, float scale, float alpha) {
		FancyBarsConfig config = FancyBarsConfig.get();
		Font font = Minecraft.getInstance().font;
		Component text = styledText(message);
		int w = font.width(text);
		int a = Math.round(alpha * 255);
		Matrix3x2fStack pose = graphics.pose();
		pose.pushMatrix();
		pose.translate(cx, cy);
		pose.scale(scale, scale);

		boolean custom = config.actionBarCustomize;
		if (custom && config.actionBarBackground) {
			int bgAlpha = Math.round(a * Math.clamp(config.actionBarBackgroundOpacity / 100f, 0, 1));
			int bgRgb = Theme.customColors() ? config.themeBackground : 0x10121C;
			Theme.roundedRect(graphics, -w / 2 - 7, -8, w + 14, 17, 5, (bgAlpha << 24) | bgRgb & 0xFFFFFF);
			if (FancyBarsConfig.get().hudBorders) Theme.border(graphics, -w / 2 - 7, -8, w + 14, 17, 5, ((bgAlpha / 2) << 24) | Theme.accent() & 0xFFFFFF, 255);
		}
		int color = (a << 24) | 0xFFFFFF;
		boolean shadow = !custom || config.actionBarShadow;
		if (custom && config.actionBarSmoothFont) {
			// centre the Inter capitals on the same line as the pixel font's (y -4 .. 3)
			Theme.drawAtCapTop(graphics, font, text, -w / 2f, -4 + (7 - Theme.UI_CAP) / 2f, Theme.UI_CAP, color, shadow);
		} else {
			graphics.text(font, text, -w / 2, -4, color, shadow);
		}
		pose.popMatrix();
	}
}
