package dev.fancybars.util.gui;

import com.mojang.blaze3d.platform.cursor.CursorTypes;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;

/**
 * Smooth rounded button. {@link Style#SECONDARY} is a dark card with a thin border that lights up on hover;
 * {@link Style#PRIMARY} has a light-blue tint, border and glow.
 */
public class ModernButton extends AbstractWidget {
	private final Runnable onPress;
	private final Style style;
	private float hover = 0;
	private long lastFrame = Util.getMillis();
	private boolean selected = false;
	private int radius = Theme.RADIUS;

	public ModernButton(int width, int height, Component label, Style style, Runnable onPress) {
		super(0, 0, width, height, label);
		this.onPress = onPress;
		this.style = style;
	}

	public ModernButton(int width, int height, Component label, Runnable onPress) {
		this(width, height, label, Style.SECONDARY, onPress);
	}

	public ModernButton selected(boolean selected) {
		this.selected = selected;
		return this;
	}

	public ModernButton radius(int radius) {
		this.radius = radius;
		return this;
	}

	@Override
	protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		boolean hovered = active && isHoveredOrFocused();
		hover = Theme.approach(hover, hovered ? 1 : 0, 8, lastFrame);
		lastFrame = Util.getMillis();

		int x = getX();
		int y = getY();
		int w = getWidth();
		int h = getHeight();
		int textColor;

		if (!active) {
			Theme.card(graphics, x, y, w, h, radius, Theme.SURFACE_DISABLED, Theme.DIVIDER);
			textColor = Theme.TEXT_DISABLED;
		} else if (style == Style.PRIMARY) {
			Theme.glow(graphics, x, y, w, h, radius, 0.25f + hover * 0.45f);
			Theme.card(graphics, x, y, w, h, radius,
					Theme.lerpColor(0xF0233A52, 0xF02B4C6C, hover),
					Theme.lerpColor(Theme.ACCENT_DIM, Theme.ACCENT, hover));
			textColor = Theme.lerpColor(Theme.ACCENT_LIGHT, 0xFFFFFFFF, hover);
		} else if (selected) {
			Theme.card(graphics, x, y, w, h, radius, Theme.SELECTED, Theme.SELECTED_BORDER);
			textColor = Theme.TEXT;
		} else {
			Theme.card(graphics, x, y, w, h, radius,
					Theme.lerpColor(Theme.SURFACE, Theme.SURFACE_HOVER, hover),
					Theme.lerpColor(Theme.CARD_BORDER, Theme.SELECTED_BORDER, hover));
			textColor = Theme.lerpColor(Theme.TEXT_SOFT, Theme.TEXT, hover);
		}

		Font font = Theme.font();
		Theme.centeredInBox(graphics, font, getMessage(), x + w / 2f, y, h, textColor);
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
