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
 * Rounded button with smooth hover animation. {@link Style#PRIMARY} is a glowing light-blue gradient,
 * {@link Style#SECONDARY} is a grey glass button that lights up blue on hover.
 */
public class ModernButton extends AbstractWidget {
	private final Runnable onPress;
	private final Style style;
	private float hover = 0;
	private long lastFrame = Util.getMillis();
	private long pressedAt = 0;

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
		hover = Theme.approach(hover, hovered ? 1 : 0, 7, lastFrame);
		lastFrame = Util.getMillis();

		// Little "press" dip
		float press = Math.max(0, 1 - (Util.getMillis() - pressedAt) / 140f);
		int dip = press > 0.3f ? 1 : 0;
		int x = getX();
		int y = getY() + dip;
		int w = getWidth();
		int h = getHeight();

		int textColor;
		if (!active) {
			Theme.roundedRect(graphics, x, y, w, h, Theme.RADIUS, Theme.SURFACE_DISABLED);
			textColor = Theme.TEXT_DISABLED;
		} else if (style == Style.PRIMARY) {
			Theme.glow(graphics, x, y, w, h, Theme.RADIUS, 0.45f + hover * 0.55f);
			int top = Theme.lerpColor(Theme.ACCENT_LIGHT, 0xFFD2EEFF, hover);
			int bottom = Theme.lerpColor(Theme.ACCENT_DEEP, Theme.ACCENT, hover);
			Theme.roundedGradient(graphics, x, y, w, h, Theme.RADIUS, top, bottom);
			graphics.fill(x + Theme.RADIUS, y + 1, x + w - Theme.RADIUS, y + 2, 0x55FFFFFF);
			textColor = Theme.TEXT_ON_ACCENT;
		} else {
			Theme.glow(graphics, x, y, w, h, Theme.RADIUS, hover * 0.6f);
			Theme.roundedRect(graphics, x, y, w, h, Theme.RADIUS, Theme.lerpColor(0x30FFFFFF, Theme.ACCENT_DIM, hover));
			Theme.roundedGradient(graphics, x + 1, y + 1, w - 2, h - 2, Theme.RADIUS - 1,
					Theme.lerpColor(Theme.SURFACE_HOVER, 0xFF3F5568, hover), Theme.lerpColor(Theme.SURFACE, 0xFF2D3E4E, hover));
			textColor = Theme.lerpColor(Theme.TEXT, Theme.ACCENT_LIGHT, hover);
		}

		Font font = Theme.font();
		Theme.centeredText(graphics, font, getMessage(), x + w / 2, y + (h - 8) / 2, textColor);
		if (hovered) graphics.requestCursor(CursorTypes.POINTING_HAND);
	}

	@Override
	public void onClick(MouseButtonEvent click, boolean doubled) {
		pressedAt = Util.getMillis();
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
