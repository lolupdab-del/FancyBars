package dev.fancybars.bars;

import org.joml.Matrix3x2fStack;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import dev.fancybars.config.FancyBarsConfig;

/**
 * Makes the vanilla held item name (the tooltip that pops up above the hotbar when you switch items)
 * toggleable, movable and scalable. Positions are offsets from where vanilla draws it.
 */
public class HeldItemName {
	/** Vanilla draws the name with its top at guiHeight - 59. */
	public static final int VANILLA_OFFSET_FROM_BOTTOM = 59;

	public static void init() {
		HudElementRegistry.replaceElement(VanillaHudElements.HELD_ITEM_TOOLTIP, original -> (graphics, tickCounter) -> {
			FancyBarsConfig config = FancyBarsConfig.get();
			if (!config.heldItemNameEnabled) return;
			if (config.heldItemNameX == 0 && config.heldItemNameY == 0 && config.heldItemNameScale == 1) {
				original.extractRenderState(graphics, tickCounter);
				return;
			}
			Matrix3x2fStack pose = graphics.pose();
			pose.pushMatrix();
			applyTransform(graphics, pose);
			original.extractRenderState(graphics, tickCounter);
			pose.popMatrix();
		});
	}

	/**
	 * Moves by the configured offset and scales around the centre of the vanilla text line.
	 */
	public static void applyTransform(GuiGraphicsExtractor graphics, Matrix3x2fStack pose) {
		FancyBarsConfig config = FancyBarsConfig.get();
		float cx = graphics.guiWidth() / 2f;
		float cy = graphics.guiHeight() - VANILLA_OFFSET_FROM_BOTTOM + 4.5f;
		pose.translate(cx + config.heldItemNameX, cy + config.heldItemNameY);
		pose.scale(config.heldItemNameScale, config.heldItemNameScale);
		pose.translate(-cx, -cy);
	}

	public static void reset() {
		FancyBarsConfig config = FancyBarsConfig.get();
		config.heldItemNameX = 0;
		config.heldItemNameY = 0;
		config.heldItemNameScale = 1;
		FancyBarsConfig.save();
	}
}
