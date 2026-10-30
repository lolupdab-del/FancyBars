package dev.fancybars.util.gui;

import java.util.function.Consumer;

import com.mojang.blaze3d.platform.cursor.CursorTypes;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;

/** Smooth rounded slider for a value between {@code min} and {@code max}, snapped to {@code step}. */
public class ModernSlider extends AbstractWidget {
	private final float min;
	private final float max;
	private final float step;
	private final Consumer<Float> onChange;
	private final Runnable onRelease;
	private float value;
	private float hover;
	private long lastFrame = Util.getMillis();

	public ModernSlider(int width, int height, float min, float max, float step, float initial, Consumer<Float> onChange, Runnable onRelease) {
		super(0, 0, width, height, Component.empty());
		this.min = min;
		this.max = max;
		this.step = step;
		this.value = Math.clamp(initial, min, max);
		this.onChange = onChange;
		this.onRelease = onRelease;
	}

	public float getValue() {
		return value;
	}

	private void setFromMouse(double mouseX) {
		float t = (float) Math.clamp((mouseX - getX() - 4) / (getWidth() - 8), 0, 1);
		float raw = min + (max - min) * t;
		float snapped = Math.clamp(Math.round(raw / step) * step, min, max);
		if (snapped != value) {
			value = snapped;
			onChange.accept(value);
		}
	}

	@Override
	public void onClick(MouseButtonEvent click, boolean doubled) {
		setFromMouse(click.x());
	}

	@Override
	protected void onDrag(MouseButtonEvent click, double deltaX, double deltaY) {
		setFromMouse(click.x());
	}

	@Override
	public void onRelease(MouseButtonEvent click) {
		super.onRelease(click);
		onRelease.run();
	}

	@Override
	protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		boolean hovered = isHoveredOrFocused();
		hover = Theme.approach(hover, hovered ? 1 : 0, 8, lastFrame);
		lastFrame = Util.getMillis();
		float t = (value - min) / (max - min);
		int trackY = getY() + getHeight() / 2 - 2;
		int trackX = getX() + 4;
		int trackW = getWidth() - 8;
		Theme.roundedRect(graphics, trackX, trackY, trackW, 4, 2, Theme.lerpColor(0xFF573D48, 0xFF684A57, hover));
		int fill = Math.round(trackW * t);
		if (fill > 0) Theme.roundedRect(graphics, trackX, trackY, Math.max(4, fill), 4, 2, Theme.ACCENT);
		int knob = 10;
		int knobX = trackX + fill - knob / 2;
		Theme.glow(graphics, knobX, getY() + (getHeight() - knob) / 2, knob, knob, knob / 2, 0.2f + hover * 0.3f);
		Theme.circle(graphics, knobX, getY() + (getHeight() - knob) / 2, knob, 0xFFFFFFFF);
		if (hovered) graphics.requestCursor(CursorTypes.POINTING_HAND);
	}

	@Override
	protected void updateWidgetNarration(NarrationElementOutput output) {
		output.add(NarratedElementType.TITLE, Component.literal(String.valueOf(value)));
	}
}
