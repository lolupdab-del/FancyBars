package dev.fancybars.util;

import org.joml.Matrix3x2f;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;

import dev.fancybars.util.render.GuiHorizontalGradientRenderState;
import dev.fancybars.util.render.GuiOutlinedTextRenderState;

/**
 * The subset of Skyblocker's GuiHelper used by the status bars.
 */
public class GuiHelper {
	private static final Minecraft CLIENT = Minecraft.getInstance();

	public static void nineSliceColored(GuiGraphicsExtractor graphics, Identifier texture, int x, int y, int width, int height, int argb) {
		graphics.blitSprite(RenderPipelines.GUI_TEXTURED, texture, x, y, width, height, argb);
	}

	public static void horizontalGradient(GuiGraphicsExtractor graphics, float startX, float startY, float endX, float endY, int colorStart, int colorEnd) {
		graphics.guiRenderState.addGuiElement(new GuiHorizontalGradientRenderState(RenderPipelines.GUI, TextureSetup.noTexture(), new Matrix3x2f(graphics.pose()), (int) startX, (int) startY, (int) endX, (int) endY, colorStart, colorEnd, graphics.scissorStack.peek()));
	}

	public static void border(GuiGraphicsExtractor context, int x, int y, int width, int height, int color) {
		context.fill(x, y, x + width, y + 1, color);
		context.fill(x, y + height - 1, x + width, y + height, color);
		context.fill(x, y + 1, x + 1, y + height - 1, color);
		context.fill(x + width - 1, y + 1, x + width, y + height - 1, color);
	}

	public static void outlinedText(GuiGraphicsExtractor graphics, Component text, int x, int y, int color, int outlineColor) {
		outlinedText(graphics, text.getVisualOrderText(), x, y, color, outlineColor);
	}

	public static void outlinedText(GuiGraphicsExtractor graphics, FormattedCharSequence text, int x, int y, int color, int outlineColor) {
		GuiOutlinedTextRenderState renderState = new GuiOutlinedTextRenderState(CLIENT.font, text, new Matrix3x2f(graphics.pose()), x, y, color, outlineColor, false, false, graphics.scissorStack.peek());
		graphics.guiRenderState.addText(renderState);
	}
}
