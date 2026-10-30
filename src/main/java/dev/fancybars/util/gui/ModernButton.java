package dev.fancybars.util.gui;

import com.mojang.blaze3d.platform.cursor.CursorTypes;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/**
 * Flat rounded button. {@link Style#PRIMARY} is filled light blue, {@link Style#SECONDARY} is grey.
 */
public class ModernButton extends AbstractWidget {
	private final Runnable onPress;
	private final Style style;

	public ModernButton(int width, int height, Component label, Style style, Runnable onPress) {
		super(0, 0, width, height, label);
		this.onPress = onPress;
		this.style = style;
	}

	public ModernButton(int width, int height, Component label, Runnable onPress) {
		this(width, height, label, Style.SECONDARY, onPress);
	}

	@Override
	protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		boolean hovered = active && isHoveredOrFocused();
		int fill;
		int textColor;
		if (!active) {
			fill = Theme.SURFACE_DISABLED;
			textColor = Theme.TEXT_DISABLED;
		} else if (style == Style.PRIMARY) {
			fill = hovered ? Theme.ACCENT_HOVER : Theme.ACCENT;
			textColor = Theme.TEXT_ON_ACCENT;
		} else {
			fill = hovered ? Theme.SURFACE_HOVER : Theme.SURFACE;
			textColor = Theme.TEXT;
		}
		Theme.roundedRect(graphics, getX(), getY(), getWidth(), getHeight(), Theme.RADIUS, fill);
		if (hovered && style == Style.SECONDARY) {
			// thin accent underline on hover
			graphics.fill(getX() + Theme.RADIUS, getBottom() - 1, getRight() - Theme.RADIUS, getBottom(), Theme.ACCENT);
		}
		Font font = Minecraft.getInstance().font;
		Theme.centeredText(graphics, font, getMessage(), getX() + getWidth() / 2, getY() + (getHeight() - 8) / 2, textColor);
		if (hovered) graphics.requestCursor(CursorTypes.POINTING_HAND);
	}

	@Override
	public void onClick(MouseButtonEvent click, boolean doubled) {
		onPress.run();
	}

	@Override
	protected void updateWidgetNarration(NarrationElementOutput output) {
		output.add(NarratedElementType.TITLE, getMessage());
	}

	public enum Style {
		PRIMARY,
		SECONDARY
	}
}
