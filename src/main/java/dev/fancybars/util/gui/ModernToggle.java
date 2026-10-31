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
 * Smooth animated pill switch, optionally inside a labelled card.
 */
public class ModernToggle extends AbstractWidget {
	public static final int SWITCH_WIDTH = 24;
	public static final int SWITCH_HEIGHT = 12;

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
		hover = Theme.approach(hover, hovered ? 1 : 0, 8, lastFrame);
		lastFrame = Util.getMillis();
		float eased = Theme.easeOutCubic(position);
		Font font = Theme.font();

		int switchX = getRight() - SWITCH_WIDTH - (label != null ? 6 : 0);
		int switchY = getY() + (getHeight() - SWITCH_HEIGHT) / 2;

		if (label != null) {
			Theme.card(graphics, getX(), getY(), getWidth(), getHeight(), Theme.RADIUS,
					Theme.lerpColor(Theme.SURFACE, Theme.SURFACE_HOVER, hover),
					Theme.lerpColor(Theme.CARD_BORDER, Theme.SELECTED_BORDER, hover));
			Theme.textInBox(graphics, font, label, getX() + 8, getY(), getHeight(), active ? Theme.TEXT_SOFT : Theme.TEXT_DISABLED);
		}

		// Track: a small rounded rectangle, accent when on, outlined grey when off
		int r = 3;
		int accent = Theme.colorAt(graphics, Theme.ACCENT, switchX + SWITCH_WIDTH / 2f);
		if (!active) {
			Theme.roundedRect(graphics, switchX, switchY, SWITCH_WIDTH, SWITCH_HEIGHT, r, Theme.SURFACE_DISABLED);
		} else {
			int off = Theme.lerpColor(Theme.SURFACE, Theme.SURFACE_HOVER, hover);
			Theme.roundedRect(graphics, switchX, switchY, SWITCH_WIDTH, SWITCH_HEIGHT, r, Theme.lerpColor(off, accent, eased));
			if (eased < 0.99f) Theme.roundedOutline(graphics, switchX, switchY, SWITCH_WIDTH, SWITCH_HEIGHT, r,
					Theme.withAlpha(Theme.lerpColor(0xFF3A3D46, 0xFF4B4F59, hover), 1 - eased));
		}

		// Knob: a square that slides across, grey when off and dark on the accent when on
		int knobSize = SWITCH_HEIGHT - 4;
		int knobX = switchX + 2 + Math.round((SWITCH_WIDTH - 4 - knobSize) * eased);
		int knob = active ? Theme.lerpColor(Theme.lerpColor(0xFF4B4F59, 0xFF8A8F9C, hover), Theme.TEXT_ON_ACCENT, eased) : Theme.TEXT_DISABLED;
		Theme.roundedRect(graphics, knobX, switchY + 2, knobSize, knobSize, 2, knob);

		if (hovered) graphics.requestCursor(CursorTypes.POINTING_HAND);
	}

	@Override
	public void onClick(MouseButtonEvent click, boolean doubled) {
		toggle();
	}

	public void toggle() {
		if (!active) return;
		value = !value;
		onChange.accept(value);
	}

	@Override
	protected void updateWidgetNarration(NarrationElementOutput output) {
		output.add(NarratedElementType.TITLE, getMessage().copy().append(": ").append(Component.translatable(value ? "options.on" : "options.off")));
	}
}
