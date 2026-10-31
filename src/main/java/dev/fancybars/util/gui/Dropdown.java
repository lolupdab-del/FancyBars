package dev.fancybars.util.gui;

import java.util.List;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

import org.jspecify.annotations.Nullable;

import com.mojang.blaze3d.platform.cursor.CursorTypes;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;

/**
 * A dropdown menu: shows the selected option, click to open a list of every option, click one to pick it.
 * Screens that use dropdowns call {@link #renderOverlay}, {@link #clickOverlay} and {@link #scrollOverlay}
 * so the open list is drawn on top of everything and catches clicks first.
 */
public class Dropdown extends AbstractWidget {
	private static final int ROW = 15;
	private static final int MAX_ROWS = 9;

	private static @Nullable Dropdown open;
	private static long openedAt;
	private static float scroll;

	private final List<Component> options;
	private final IntSupplier selected;
	private final IntConsumer onSelect;
	private final @Nullable Component prefix;
	private float hover;
	private long lastFrame = Util.getMillis();

	public Dropdown(int width, int height, @Nullable Component prefix, List<Component> options, IntSupplier selected, IntConsumer onSelect) {
		super(0, 0, width, height, Component.empty());
		this.prefix = prefix;
		this.options = options;
		this.selected = selected;
		this.onSelect = onSelect;
	}

	/** Width that fits the longest option. */
	public static int fitWidth(List<Component> options, int min) {
		int w = min;
		for (Component c : options) w = Math.max(w, Theme.width(Theme.font(), c) + 26);
		return w;
	}

	private Component current() {
		int i = selected.getAsInt();
		return i >= 0 && i < options.size() ? options.get(i) : Component.literal("?");
	}

	@Override
	public void onClick(MouseButtonEvent click, boolean doubled) {
		if (open == this) open = null;
		else {
			open = this;
			openedAt = Util.getMillis();
			int rows = Math.min(MAX_ROWS, options.size());
			scroll = Mth.clamp(selected.getAsInt() - rows / 2, 0, Math.max(0, options.size() - rows));
		}
	}

	@Override
	protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		boolean hovered = isHoveredOrFocused() || open == this;
		hover = Theme.approach(hover, hovered ? 1 : 0, 8, lastFrame);
		lastFrame = Util.getMillis();
		int x = getX();
		int y = getY();
		int w = getWidth();
		int h = getHeight();
		Theme.card(graphics, x, y, w, h, Theme.RADIUS,
				Theme.lerpColor(Theme.SURFACE, Theme.SURFACE_HOVER, hover),
				Theme.lerpColor(Theme.CARD_BORDER, Theme.SELECTED_BORDER, hover));
		Font font = Theme.font();
		Component label = prefix == null ? current() : prefix.copy().append(": ").append(current());
		Theme.textInBox(graphics, font, label, x + 7, y, h, Theme.lerpColor(Theme.TEXT_SOFT, Theme.TEXT, hover));
		chevron(graphics, x + w - 10, y + h / 2f, open == this);
		if (isHovered()) graphics.requestCursor(CursorTypes.POINTING_HAND);
	}

	private static void chevron(GuiGraphicsExtractor graphics, float cx, float cy, boolean up) {
		int color = Theme.night(Theme.TEXT_MUTED);
		for (int i = 0; i < 3; i++) {
			int dy = up ? i - 1 : 1 - i;
			graphics.fill(Math.round(cx) - i - 1, Math.round(cy) + dy, Math.round(cx) + i + 1, Math.round(cy) + dy + 1, color);
		}
	}

	// ---------------------------------------------------------------- open list

	private static int[] listBounds(Dropdown d) {
		int rows = Math.min(MAX_ROWS, d.options.size());
		int w = Math.max(d.getWidth(), fitWidth(d.options, 0));
		int h = rows * ROW + 4;
		int screenH = Minecraft.getInstance().getWindow().getGuiScaledHeight();
		int screenW = Minecraft.getInstance().getWindow().getGuiScaledWidth();
		int x = Math.min(d.getX(), screenW - w - 2);
		int y = d.getY() + d.getHeight() + 2;
		if (y + h > screenH - 2) y = d.getY() - h - 2;
		return new int[]{x, Math.max(2, y), w, h, rows};
	}

	public static boolean isOpen() {
		return open != null;
	}

	public static void close() {
		open = null;
	}

	/** Draw the open list on top. Call at the very end of a screen's render. */
	public static void renderOverlay(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		Dropdown d = open;
		if (d == null) return;
		if (!d.visible) {
			open = null;
			return;
		}
		int[] b = listBounds(d);
		float t = Theme.animationsEnabled() ? Theme.easeOutCubic((Util.getMillis() - openedAt) / 140f) : 1;
		graphics.nextStratum();
		Theme.shadow(graphics, b[0], b[1], b[2], b[3], Theme.RADIUS, 0, 2);
		int shownH = Math.max(6, Math.round(b[3] * t));
		Theme.card(graphics, b[0], b[1], b[2], shownH, Theme.RADIUS, 0xFF000000 | Theme.PANEL, Theme.SELECTED_BORDER);
		graphics.enableScissor(b[0], b[1] + 2, b[0] + b[2], b[1] + shownH - 2);
		Font font = Theme.font();
		int first = Math.round(scroll);
		int sel = d.selected.getAsInt();
		for (int r = 0; r < b[4]; r++) {
			int i = first + r;
			if (i >= d.options.size()) break;
			int ry = b[1] + 2 + r * ROW;
			boolean over = mouseX >= b[0] && mouseX < b[0] + b[2] && mouseY >= ry && mouseY < ry + ROW;
			if (i == sel) Theme.roundedRect(graphics, b[0] + 2, ry, b[2] - 4, ROW, Theme.RADIUS, Theme.SELECTED);
			else if (over) Theme.roundedRect(graphics, b[0] + 2, ry, b[2] - 4, ROW, Theme.RADIUS, Theme.CARD_HOVER);
			if (i == sel) Theme.roundedRect(graphics, b[0] + 4, ry + 4, 2, ROW - 8, 1, Theme.ACCENT);
			Theme.textInBox(graphics, font, d.options.get(i), b[0] + 10, ry, ROW, i == sel ? Theme.ACCENT_LIGHT : over ? Theme.TEXT : Theme.TEXT_SOFT);
			if (over) graphics.requestCursor(CursorTypes.POINTING_HAND);
		}
		if (d.options.size() > b[4]) {
			float frac = scroll / (d.options.size() - b[4]);
			int barH = Math.max(10, b[3] * b[4] / d.options.size());
			int barY = b[1] + 2 + Math.round((b[3] - 4 - barH) * frac);
			Theme.roundedRect(graphics, b[0] + b[2] - 4, barY, 2, barH, 1, Theme.TEXT_MUTED);
		}
		graphics.disableScissor();
	}

	/** Returns true if the click was used by an open dropdown (also closes it when clicking outside). */
	public static boolean clickOverlay(double mouseX, double mouseY) {
		Dropdown d = open;
		if (d == null) return false;
		int[] b = listBounds(d);
		if (mouseX >= b[0] && mouseX < b[0] + b[2] && mouseY >= b[1] && mouseY < b[1] + b[3]) {
			int r = (int) ((mouseY - b[1] - 2) / ROW);
			int i = Math.round(scroll) + r;
			if (r >= 0 && r < b[4] && i < d.options.size()) {
				open = null;
				d.onSelect.accept(i);
				Minecraft.getInstance().getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1));
			}
			return true;
		}
		open = null;
		// clicking the dropdown's own button just closes it
		return d.isMouseOver(mouseX, mouseY);
	}

	public static boolean scrollOverlay(double mouseX, double mouseY, double amount) {
		Dropdown d = open;
		if (d == null) return false;
		int[] b = listBounds(d);
		if (mouseX >= b[0] && mouseX < b[0] + b[2] && mouseY >= b[1] && mouseY < b[1] + b[3]) {
			scroll = Mth.clamp(scroll - (float) amount, 0, Math.max(0, d.options.size() - b[4]));
			return true;
		}
		open = null;
		return false;
	}

	@Override
	protected void updateWidgetNarration(NarrationElementOutput output) {
		output.add(NarratedElementType.TITLE, current());
	}
}
