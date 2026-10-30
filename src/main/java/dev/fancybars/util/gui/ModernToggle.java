package dev.fancybars.util.gui;

import java.util.function.Consumer;

import com.mojang.blaze3d.platform.cursor.CursorTypes;
import org.jspecify.annotations.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;

/**
 * Pill-shaped on/off switch with an optional label to its left.
 */
public class ModernToggle extends AbstractWidget {
	public static final int SWITCH_WIDTH = 22;
	public static final int SWITCH_HEIGHT = 12;

	private final @Nullable Component label;
	private final Consumer<Boolean> onChange;
	private boolean value;
	private long changedAt = 0;

	/**
	 * @param label shown to the left of the switch, or {@code null} for a bare switch
	 */
	public ModernToggle(int width, int height, @Nullable Component label, boolean initial, Consumer<Boolean> onChange) {
		super(0, 0, Math.max(width, SWITCH_WIDTH), Math.max(height, SWITCH_HEIGHT), label == null ? Component.empty() : label);
		this.label = label;
		this.value = initial;
		this.onChange = onChange;
	}

	public boolean getValue() {
		return value;
	}

	@Override
	protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		boolean hovered = active && isHoveredOrFocused();
		Font font = Minecraft.getInstance().font;

		int switchX = getRight() - SWITCH_WIDTH;
		int switchY = getY() + (getHeight() - SWITCH_HEIGHT) / 2;

		if (label != null) {
			if (hovered) Theme.roundedRect(graphics, getX(), getY(), getWidth(), getHeight(), Theme.RADIUS, Theme.SURFACE);
			Theme.text(graphics, font, label, getX() + 5, getY() + (getHeight() - 8) / 2, active ? Theme.TEXT : Theme.TEXT_DISABLED);
		}

		// Animate the knob over ~120ms
		float t = Math.min(1, (Util.getMillis() - changedAt) / 120f);
		float pos = value ? t : 1 - t;

		int track;
		if (!active) track = Theme.SURFACE_DISABLED;
		else track = Theme.lerpColor(hovered ? Theme.SURFACE_HOVER : 0xFF464D57, hovered ? Theme.ACCENT_HOVER : Theme.ACCENT, pos);
		Theme.roundedRect(graphics, switchX, switchY, SWITCH_WIDTH, SWITCH_HEIGHT, SWITCH_HEIGHT / 2, track);

		int knobSize = SWITCH_HEIGHT - 4;
		int knobX = switchX + 2 + Math.round((SWITCH_WIDTH - 4 - knobSize) * pos);
		int knobColor = active ? (value ? 0xFFFFFFFF : 0xFFC9D1D9) : Theme.TEXT_DISABLED;
		Theme.roundedRect(graphics, knobX, switchY + 2, knobSize, knobSize, knobSize / 2, knobColor);

		if (hovered) graphics.requestCursor(CursorTypes.POINTING_HAND);
	}

	@Override
	public void onClick(MouseButtonEvent click, boolean doubled) {
		value = !value;
		changedAt = Util.getMillis();
		onChange.accept(value);
	}

	@Override
	protected void updateWidgetNarration(NarrationElementOutput output) {
		output.add(NarratedElementType.TITLE, getMessage().copy().append(": ").append(Component.translatable(value ? "options.on" : "options.off")));
	}
}
