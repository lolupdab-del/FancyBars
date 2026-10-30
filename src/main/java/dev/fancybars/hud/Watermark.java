package dev.fancybars.hud;

import org.joml.Matrix3x2fStack;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

import dev.fancybars.FancyBarsMod;
import dev.fancybars.config.FancyBarsConfig;
import dev.fancybars.util.gui.Theme;

/** "ShikiClient" in the top-left corner of the HUD. Movable / scalable in the editor. */
public final class Watermark {
	private Watermark() {}

	public static void init() {
		HudElementRegistry.attachElementAfter(VanillaHudElements.HOTBAR, FancyBarsMod.id("watermark"), (graphics, tickCounter) -> {
			if (FancyBarsConfig.get().watermarkEnabled && Minecraft.getInstance().screen == null) render(graphics, 1);
		});
	}

	public static Component text() {
		return Theme.title(Component.empty()
				.append(Component.literal("Shiki").withColor(Theme.ACCENT & 0xFFFFFF))
				.append(Component.literal("Client").withColor(Theme.TEXT & 0xFFFFFF)));
	}

	/** Unscaled {width, height}. */
	public static int[] size() {
		return new int[]{Minecraft.getInstance().font.width(text()), Math.round(Theme.TITLE_CAP) + 2};
	}

	public static void render(GuiGraphicsExtractor graphics, float alpha) {
		FancyBarsConfig config = FancyBarsConfig.get();
		Font font = Minecraft.getInstance().font;
		Matrix3x2fStack pose = graphics.pose();
		pose.pushMatrix();
		pose.translate(config.watermarkX, config.watermarkY);
		pose.scale(config.watermarkScale, config.watermarkScale);
		int a = Math.round(alpha * 255) << 24;
		Theme.drawAtCapTop(graphics, font, text(), 0, 1, Theme.TITLE_CAP, a | 0xFFFFFF, true);
		pose.popMatrix();
	}
}
