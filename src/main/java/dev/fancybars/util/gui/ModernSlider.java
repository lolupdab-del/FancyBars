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

/**
 * Smooth rounded slider for a value between {@code min} and {@code max}. Values move in steps of at most 1% of the
 * range, and the knob follows the mouse exactly while dragging and glides when the value changes.
 */
public class ModernSlider extends AbstractWidget {
	private final float min;
	private final float max;
	private final float step;
	private final Consumer<Float> onChange;
	private final Runnable onRelease;
	private float value;
	private float shown = -1;
	private boolean dragging;
	private float dragT;
	private float hover;
	private long lastFrame = Util.getMillis();

	public ModernSlider(int width, int height, float min, float max, float step, float initial, Consumer<Float> onChange, Runnable onRelease) {
		super(0, 0, width, height, Component.empty());
		this.min = min;
		this.max = max;
		this.step = step <= 0 ? (max - min) / 100f : Math.min(step, (max - min) / 100f);
		this.value = Math.clamp(initial, min, max);
		this.onChange = onChange;
		this.onRelease = onRelease;
	}

	public void setValue(float v) {
		this.value = Math.clamp(v, min, max);
	}

	public float getValue() {
		return value;
	}

	private void setFromMouse(double mouseX) {
		float t = (float) Math.clamp((mouseX - getX() - 4) / (getWidth() - 8), 0, 1);
		dragT = t;
		dragging = true;
		float raw = min + (max - min) * t;
		float snapped = Math.clamp(min + Math.round((raw - min) / step) * step, min, max);
		snapped = Math.round(snapped * 10000) / 10000f;
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
		dragging = false;
		onRelease.run();
	}

	@Override
	protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		boolean hovered = isHoveredOrFocused();
		hover = Theme.approach(hover, hovered ? 1 : 0, 8, lastFrame);
		float target = dragging ? dragT : (value - min) / (max - min);
		if (shown < 0 || !Theme.animationsEnabled()) shown = target;
		else {
			float dt = Math.min(0.1f, (Util.getMillis() - lastFrame) / 1000f);
			shown += (target - shown) * Math.min(1, dt * 22);
		}
		float t = shown;
		// Linear fader: hairline track, accent fill, slim light handle (all on exact screen pixels)
		float trackX = getX() + 4;
		float trackW = getWidth() - 8;
		float trackH = 1.25f;
		float trackY = getY() + getHeight() / 2f - trackH / 2f;
		Theme.fillF(graphics, trackX, trackY, trackW, trackH, Theme.lerpColor(0xFF573D48, 0xFF684A57, hover));
		float fillW = trackW * t;
		if (fillW > 0.3f) Theme.fillF(graphics, trackX, trackY, fillW, trackH, Theme.ACCENT);
		float knobW = 2.5f;
		float knobH = 8f;
		Theme.roundedRect(graphics, trackX + fillW - knobW / 2f, getY() + (getHeight() - knobH) / 2f, knobW, knobH, 0.6f, Theme.lerpColor(0xFFC8CCD6, 0xFFFFFFFF, hover));
		if (hovered) graphics.requestCursor(CursorTypes.POINTING_HAND);
		lastFrame = Util.getMillis();
	}

	@Override
	protected void updateWidgetNarration(NarrationElementOutput output) {
		output.add(NarratedElementType.TITLE, Component.literal(String.valueOf(value)));
	}
}
