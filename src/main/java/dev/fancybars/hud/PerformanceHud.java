package dev.fancybars.hud;

import org.joml.Matrix3x2fStack;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.Util;

import dev.fancybars.FancyBarsMod;
import dev.fancybars.config.FancyBarsConfig;
import dev.fancybars.util.gui.Theme;

/**
 * FPS and TPS display. TPS is estimated from how often the server's time update arrives: it sends one every
 * 20 ticks, so 20 ticks divided by the real time between two updates is the server's tick rate.
 */
public final class PerformanceHud {
	private static final int PAD = 6;
	private static final int HEIGHT = 14;
	private static long lastTimePacket = -1;
	private static final float[] SAMPLES = new float[5];
	private static int samples;
	private static int sampleIndex;
	private static float tps = 20;

	private PerformanceHud() {}

	public static void init() {
		HudElementRegistry.attachElementAfter(VanillaHudElements.HOTBAR, FancyBarsMod.id("performance"), (graphics, tickCounter) -> {
			Minecraft mc = Minecraft.getInstance();
			if (FancyBarsConfig.get().perfEnabled && mc.player != null && dev.fancybars.util.HudVisibility.allowed()) render(graphics);
		});
	}

	/** Called whenever the server's time update packet arrives. */
	public static void onTimePacket() {
		long now = Util.getMillis();
		if (lastTimePacket > 0) {
			long gap = now - lastTimePacket;
			if (gap > 100 && gap < 20000) {
				SAMPLES[sampleIndex] = Math.min(20, 20_000f / gap);
				sampleIndex = (sampleIndex + 1) % SAMPLES.length;
				samples = Math.min(samples + 1, SAMPLES.length);
				float sum = 0;
				for (int i = 0; i < samples; i++) sum += SAMPLES[i];
				tps = sum / samples;
			}
		}
		lastTimePacket = now;
	}

	/** Forget the old server's timing after switching servers. */
	public static void reset() {
		lastTimePacket = -1;
		samples = 0;
		tps = 20;
	}

	public static float tps() {
		return tps;
	}

	private static int tpsColor(float t) {
		if (t >= 18) return 0x5ED17A;
		if (t >= 15) return 0xFFD24A;
		return 0xFF5A6A;
	}

	public static Component text() {
		FancyBarsConfig c = FancyBarsConfig.get();
		int value = 0xFF000000 | c.perfTextColor;
		int label = Theme.lerpColor(value, 0xFF000000 | (c.perfBackground ? c.perfBackgroundColor : 0x000000), 0.45f);
		MutableComponent out = Component.empty();
		if (c.perfShowFps) {
			out.append(Component.literal(String.valueOf(Minecraft.getInstance().getFps())).withColor(value & 0xFFFFFF));
			out.append(Component.literal(" FPS").withColor(label & 0xFFFFFF));
		}
		if (c.perfShowTps) {
			if (c.perfShowFps) out.append(Component.literal("   "));
			float t = tps();
			int tc = c.perfColorTps ? tpsColor(t) : value & 0xFFFFFF;
			out.append(Component.literal(String.format(java.util.Locale.ROOT, "%.1f", t)).withColor(tc));
			out.append(Component.literal(" TPS").withColor(label & 0xFFFFFF));
		}
		return Theme.bold(out);
	}

	public static int[] size() {
		return new int[]{Minecraft.getInstance().font.width(text()) + PAD * 2, HEIGHT};
	}

	public static float[] position(int screenW, int screenH) {
		FancyBarsConfig c = FancyBarsConfig.get();
		if (c.perfX < 0 || c.perfY < 0) return new float[]{6, 6};
		return new float[]{c.perfX * screenW, c.perfY * screenH};
	}

	public static void render(GuiGraphicsExtractor graphics) {
		float[] pos = position(graphics.guiWidth(), graphics.guiHeight());
		renderAt(graphics, pos[0], pos[1], FancyBarsConfig.get().perfScale, 1);
	}

	public static void renderAt(GuiGraphicsExtractor graphics, float x, float y, float scale, float alpha) {
		FancyBarsConfig c = FancyBarsConfig.get();
		Font font = Minecraft.getInstance().font;
		int a = Math.round(alpha * 255);
		int[] size = size();
		Matrix3x2fStack pose = graphics.pose();
		pose.pushMatrix();
		pose.translate(x, y);
		pose.scale(scale, scale);
		if (c.perfBackground) {
			int bgAlpha = Math.round(a * Math.clamp(c.perfBackgroundOpacity, 0, 1));
			Theme.roundedRect(graphics, 0, 0, size[0], size[1], 4, bgAlpha << 24 | c.perfBackgroundColor & 0xFFFFFF);
			if (c.hudBorders) Theme.border(graphics, 0, 0, size[0], size[1], 4, Theme.hudLine(), a);
		}
		Theme.boldInBox(graphics, font, text(), PAD, 0, size[1], a << 24 | 0xFFFFFF);
		pose.popMatrix();
	}
}
