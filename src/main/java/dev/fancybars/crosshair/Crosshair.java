package dev.fancybars.crosshair;

import org.joml.Matrix3x2fStack;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import dev.fancybars.config.FancyBarsConfig;

/** A crosshair you draw yourself on a pixel grid (any colours), replacing the vanilla one. */
public final class Crosshair {
	public static final int[] SIZES = {7, 9, 11, 13, 15, 17, 19, 21, 25};
	private static final net.minecraft.resources.Identifier VANILLA = net.minecraft.resources.Identifier.withDefaultNamespace("hud/crosshair");

	private Crosshair() {}

	public static void init() {
		HudElementRegistry.replaceElement(VanillaHudElements.CROSSHAIR, original -> (graphics, tickCounter) -> {
			FancyBarsConfig config = FancyBarsConfig.get();
			Minecraft mc = Minecraft.getInstance();
			boolean firstPerson = mc.options.getCameraType().isFirstPerson();
			if (!config.crosshairEnabled) {
				if (firstPerson || !config.crosshairInThirdPerson) {
					original.extractRenderState(graphics, tickCounter);
				} else {
					// vanilla only draws it in first person: draw the normal crosshair ourselves in F5
					graphics.blitSprite(net.minecraft.client.renderer.RenderPipelines.CROSSHAIR, VANILLA, (graphics.guiWidth() - 15) / 2, (graphics.guiHeight() - 15) / 2, 15, 15);
				}
				return;
			}
			if (!firstPerson && !config.crosshairInThirdPerson) return;
			draw(graphics, graphics.guiWidth() / 2f, graphics.guiHeight() / 2f, config.crosshairScale, 1);
		});
	}

	public static int[] pixels() {
		FancyBarsConfig config = FancyBarsConfig.get();
		int n = config.crosshairSize * config.crosshairSize;
		if (config.crosshairPixels == null || config.crosshairPixels.length != n) config.crosshairPixels = Presets.make("plus", config.crosshairSize);
		return config.crosshairPixels;
	}

	/** Draws the crosshair centred on (cx, cy); one grid cell = {@code scale} GUI pixels. */
	public static void draw(GuiGraphicsExtractor graphics, float cx, float cy, float scale, float alpha) {
		FancyBarsConfig config = FancyBarsConfig.get();
		int size = config.crosshairSize;
		int[] pixels = pixels();
		Matrix3x2fStack pose = graphics.pose();
		pose.pushMatrix();
		pose.translate(cx - size * scale / 2f, cy - size * scale / 2f);
		pose.scale(scale, scale);
		for (int y = 0; y < size; y++) {
			for (int x = 0; x < size; x++) {
				int c = pixels[y * size + x];
				if ((c >>> 24) == 0) continue;
				int a = Math.round((c >>> 24) * alpha);
				graphics.fill(x, y, x + 1, y + 1, (a << 24) | (c & 0xFFFFFF));
			}
		}
		pose.popMatrix();
	}
}
