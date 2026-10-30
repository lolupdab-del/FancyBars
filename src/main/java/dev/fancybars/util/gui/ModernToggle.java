package dev.fancybars.util.gui;

import java.util.function.Consumer;

import com.mojang.blaze3d.platform.cursor.CursorTypes;
import org.jspecify.annotations.Nullable;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;

/**
 * Animated pill switch, optionally inside a labelled card.
 */
public class ModernToggle extends AbstractWidget {
	public static final int SWITCH_WIDTH = 24;
	public static final int SWITCH_HEIGHT = 13;

	private final @Nullable Component label;
	private final Consumer<Boolean> onChange;
	private boolean value;
	private float position;
	private float hover = 0;
	private long lastFrame = Util.getMillis();

	/**
	 * @param label shown to the left of the switch (the whole widget becomes a card), or {@code null} for a bare switch
	 */
	public ModernToggle(int width, int height, @Nullable Component label, boolean initial, Consumer<Boolean> onChange) {
		super(0, 0, Math.max(width, SWITCH_WIDTH), Math.max(height, SWITCH_HEIGHT), label == null ? Component.empty() : label);
		this.label = label;
		this.value = initial;
		this.position = initial ? 1 : 0;
		this.onChange = onChange;
	}

	public boolean getValue() {
		return value;
	}

	@Override
	protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		boolean hovered = active && isHoveredOrFocused();
		position = Theme.approach(position, value ? 1 : 0, 8, lastFrame);
		hover = Theme.approach(hover, hovered ? 1 : 0, 7, lastFrame);
		lastFrame = Util.getMillis();
		float eased = Theme.easeOutCubic(position);
		Font font = Theme.font();

		int switchX = getRight() - SWITCH_WIDTH - (label != null ? 4 : 0);
		int switchY = getY() + (getHeight() - SWITCH_HEIGHT) / 2;

		if (label != null) {
			Theme.roundedRect(graphics, getX(), getY(), getWidth(), getHeight(), Theme.RADIUS, Theme.lerpColor(Theme.CARD, Theme.CARD_HOVER, hover * 0.6f));
			Theme.text(graphics, font, label, getX() + 6, getY() + (getHeight() - 8) / 2, active ? Theme.lerpColor(Theme.TEXT, Theme.ACCENT_LIGHT, hover) : Theme.TEXT_DISABLED);
		}

		// Track
		if (!active) {
			Theme.roundedRect(graphics, switchX, switchY, SWITCH_WIDTH, SWITCH_HEIGHT, SWITCH_HEIGHT / 2, Theme.SURFACE_DISABLED);
		} else {
			Theme.glow(graphics, switchX, switchY, SWITCH_WIDTH, SWITCH_HEIGHT, SWITCH_HEIGHT / 2, eased * (0.5f + hover * 0.5f));
			int offTop = Theme.lerpColor(0xFF4A525D, 0xFF56606C, hover);
			int offBottom = Theme.lerpColor(0xFF363C45, 0xFF404853, hover);
			Theme.roundedGradient(graphics, switchX, switchY, SWITCH_WIDTH, SWITCH_HEIGHT, SWITCH_HEIGHT / 2,
					Theme.lerpColor(offTop, Theme.ACCENT_LIGHT, eased), Theme.lerpColor(offBottom, Theme.ACCENT_DEEP, eased));
		}

		// Knob with a tiny shadow
		int knobSize = SWITCH_HEIGHT - 4;
		int knobX = switchX + 2 + Math.round((SWITCH_WIDTH - 4 - knobSize) * eased);
		int knobY = switchY + 2;
		Theme.roundedRect(graphics, knobX, knobY + 1, knobSize, knobSize, knobSize / 2, 0x50000000);
		int knobColor = active ? Theme.lerpColor(0xFFD5DCE3, 0xFFFFFFFF, eased) : Theme.TEXT_DISABLED;
		Theme.roundedRect(graphics, knobX, knobY, knobSize, knobSize, knobSize / 2, knobColor);

		if (hovered) graphics.requestCursor(CursorTypes.POINTING_HAND);
	}

	@Override
	public void onClick(MouseButtonEvent click, boolean doubled) {
		value = !value;
		onChange.accept(value);
	}

	@Override
	protected void updateWidgetNarration(NarrationElementOutput output) {
		output.add(NarratedElementType.TITLE, getMessage().copy().append(": ").append(Component.translatable(value ? "options.on" : "options.off")));
	}
}
