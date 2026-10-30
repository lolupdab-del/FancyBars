package dev.fancybars.hud;

import org.joml.Matrix3x2fStack;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import dev.fancybars.FancyBarsMod;
import dev.fancybars.config.FancyBarsConfig;
import dev.fancybars.util.gui.Theme;

/** SBCLIENT / ShikiClient logo in the top-left corner of the HUD. Movable / scalable in the editor. */
public final class Watermark {
	private static final int ICON_SIZE = 12;
	private static final int ICON_GAP = 4;

	private Watermark() {}

	/** Draws the ShikiClient thumbnail as a size × size square. */
	public static void drawIcon(GuiGraphicsExtractor graphics, Identifier icon, float x, float y, float size, int alpha) {
		Matrix3x2fStack pose = graphics.pose();
		pose.pushMatrix();
		pose.translate(x, y);
		pose.scale(size / 64f, size / 64f);
		graphics.blit(RenderPipelines.GUI_TEXTURED, icon, 0, 0, 0, 0, 64, 64, 64, 64, (alpha << 24) | 0xFFFFFF);
		pose.popMatrix();
	}

	public static Component text() {
		return Theme.title(Theme.brand());
	}

	/** Unscaled {width, height}. */
	public static int[] size() {
		return new int[]{ICON_SIZE + ICON_GAP + Minecraft.getInstance().font.width(text()), ICON_SIZE};
	}

	public static void render(GuiGraphicsExtractor graphics, float alpha) {
		FancyBarsConfig config = FancyBarsConfig.get();
		Font font = Minecraft.getInstance().font;
		Matrix3x2fStack pose = graphics.pose();
		pose.pushMatrix();
		pose.translate(config.watermarkX, config.watermarkY);
		pose.scale(config.watermarkScale, config.watermarkScale);
		int a = Math.round(alpha * 255);
		drawIcon(graphics, Theme.brandIcon(), 0, 0, ICON_SIZE, a);
		Theme.drawAtCapTop(graphics, font, text(), ICON_SIZE + ICON_GAP, (ICON_SIZE - Theme.TITLE_CAP) / 2f, Theme.TITLE_CAP, (a << 24) | 0xFFFFFF, true);
		pose.popMatrix();
	}
}
