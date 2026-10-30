package dev.fancybars.crosshair;

import org.jspecify.annotations.Nullable;

import com.mojang.blaze3d.platform.cursor.CursorTypes;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import com.mojang.blaze3d.platform.InputConstants;

import dev.fancybars.bars.EditBarColorPopup;
import dev.fancybars.config.FancyBarsConfig;
import dev.fancybars.util.gui.ModernButton;
import dev.fancybars.util.gui.ModernSlider;
import dev.fancybars.util.gui.ModernToggle;
import dev.fancybars.util.gui.Theme;

/**
 * Draw your crosshair: left click / drag paints with the selected colour, right click / drag erases.
 * Presets, any colour (palette or custom picker), grid size and on-screen size.
 */
public class CrosshairEditorScreen extends Screen {
	private static final int[] PALETTE = {0xFFFFFFFF, 0xFF000000, 0xFFFF5555, 0xFFFFAA00, 0xFFFFFF55, 0xFF55FF55, 0xFF00AA00, 0xFF55FFFF,
			0xFF00AAAA, 0xFF5555FF, 0xFFAA00AA, 0xFFFF55FF, 0xFFFF8FC8, 0xFF7CC8FF, 0xFFAAAAAA, 0xFF555555};

	private final @Nullable Screen parent;
	private int color = 0xFFFFFFFF;
	private int customColor = 0xFF7CC8FF;
	private int px;
	private int py;
	private int pw;
	private int ph;
	private int canvasX;
	private int canvasY;
	private int cell;
	private int toolsX;
	private int paletteY;
	private int painting = 0; // 0 none, 1 paint, 2 erase

	public CrosshairEditorScreen(@Nullable Screen parent) {
		super(Component.translatable("fancybars.crosshair.title"));
		this.parent = parent;
	}

	private FancyBarsConfig config() {
		return FancyBarsConfig.get();
	}

	@Override
	protected void init() {
		pw = Math.min(width - 12, 420);
		ph = Math.min(height - 12, 280);
		px = (width - pw) / 2;
		py = (height - ph) / 2;
		int size = config().crosshairSize;
		int canvasMax = Math.min(ph - 56, pw / 2 - 10);
		cell = Math.max(4, canvasMax / size);
		canvasX = px + 12;
		canvasY = py + 30;
		toolsX = canvasX + cell * size + 14;
		int toolsW = px + pw - 12 - toolsX;

		int y = py + 30;
		ModernToggle enabled = new ModernToggle(toolsW, 18, Component.translatable("fancybars.crosshair.enabled"), config().crosshairEnabled, value -> {
			config().crosshairEnabled = value;
			FancyBarsConfig.save();
		});
		enabled.setPosition(toolsX, y);
		addRenderableWidget(enabled);
		y += 22;

		// presets
		int half = (toolsW - 4) / 2;
		for (int i = 0; i < Presets.NAMES.length; i++) {
			String name = Presets.NAMES[i];
			ModernButton b = new ModernButton(i % 2 == 0 ? half : toolsW - half - 4, 14, Component.translatable("fancybars.crosshair.preset." + name), () -> {
				config().crosshairPixels = Presets.make(name, config().crosshairSize);
				FancyBarsConfig.save();
			});
			b.setPosition(toolsX + (i % 2 == 0 ? 0 : half + 4), y + i / 2 * 16);
			addRenderableWidget(b);
		}
		y += (Presets.NAMES.length + 1) / 2 * 16 + 4;

		paletteY = y;
		y += 2 * 13 + 6;

		// grid size
		ModernButton sizeButton = new ModernButton(half, 16, Component.translatable("fancybars.crosshair.grid", size, size), () -> {
			int current = config().crosshairSize;
			int index = 0;
			for (int i = 0; i < Crosshair.SIZES.length; i++) if (Crosshair.SIZES[i] == current) index = i;
			int next = Crosshair.SIZES[(index + 1) % Crosshair.SIZES.length];
			config().crosshairPixels = Presets.resize(Crosshair.pixels(), current, next);
			config().crosshairSize = next;
			FancyBarsConfig.save();
			rebuild();
		});
		sizeButton.setPosition(toolsX, y);
		addRenderableWidget(sizeButton);
		ModernButton clear = new ModernButton(toolsW - half - 4, 16, Component.translatable("fancybars.crosshair.clear"), () -> {
			config().crosshairPixels = new int[config().crosshairSize * config().crosshairSize];
			FancyBarsConfig.save();
		});
		clear.setPosition(toolsX + half + 4, y);
		addRenderableWidget(clear);
		y += 22;

		ModernSlider scale = new ModernSlider(toolsW - 60, 14, 0.5f, 4, 0.25f, config().crosshairScale, value -> config().crosshairScale = value, FancyBarsConfig::save);
		scale.setPosition(toolsX + 60, y);
		addRenderableWidget(scale);

		ModernButton done = new ModernButton(80, 18, Component.translatable("gui.done"), ModernButton.Style.PRIMARY, this::onClose);
		done.setPosition(px + pw - 12 - 80, py + ph - 26);
		addRenderableWidget(done);
	}

	private void rebuild() {
		CrosshairEditorScreen next = new CrosshairEditorScreen(parent);
		next.color = color;
		next.customColor = customColor;
		minecraft.gui.setScreen(next);
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		Theme.backdrop(graphics, width, height);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		Theme.panel(graphics, px, py, pw, ph);
		Theme.titleInBox(graphics, font, title, px + 12, py + 6, 18, Theme.TEXT);
		int size = config().crosshairSize;
		int[] pixels = Crosshair.pixels();

		// canvas: checkerboard + pixels + grid lines + centre marker
		for (int y = 0; y < size; y++) {
			for (int x = 0; x < size; x++) {
				int cx = canvasX + x * cell;
				int cy = canvasY + y * cell;
				graphics.fill(cx, cy, cx + cell, cy + cell, (x + y) % 2 == 0 ? 0xFF2A2F3A : 0xFF232733);
				int c = pixels[y * size + x];
				if ((c >>> 24) != 0) graphics.fill(cx, cy, cx + cell, cy + cell, c);
			}
		}
		int c = size / 2;
		Theme.roundedOutline(graphics, canvasX + c * cell - 1, canvasY + c * cell - 1, cell + 2, cell + 2, 2, 0x88FFFFFF);
		Theme.roundedOutline(graphics, canvasX - 2, canvasY - 2, size * cell + 4, size * cell + 4, 3, Theme.CARD_BORDER);
		int hx = (mouseX - canvasX) / cell;
		int hy = (mouseY - canvasY) / cell;
		if (mouseX >= canvasX && mouseY >= canvasY && hx < size && hy < size) {
			graphics.fill(canvasX + hx * cell, canvasY + hy * cell, canvasX + (hx + 1) * cell, canvasY + (hy + 1) * cell, (color & 0xFFFFFF) | 0x66000000);
			graphics.requestCursor(CursorTypes.CROSSHAIR);
		}
		Theme.textInBox(graphics, font, Component.translatable("fancybars.crosshair.hint"), canvasX, canvasY + size * cell + 4, 12, Theme.TEXT_MUTED);

		// palette
		for (int i = 0; i <= PALETTE.length; i++) {
			int sx = toolsX + i % 9 * 13;
			int sy = paletteY + i / 9 * 13;
			int col = i < PALETTE.length ? PALETTE[i] : customColor;
			Theme.roundedRect(graphics, sx, sy, 11, 11, 2, col);
			if (i == PALETTE.length) Theme.centeredInBox(graphics, font, Component.literal("+"), sx + 5.5f, sy, 11, (col & 0xFFFFFF) > 0x888888 ? 0xFF000000 : 0xFFFFFFFF);
			if (col == color) Theme.roundedOutline(graphics, sx - 1, sy - 1, 13, 13, 3, Theme.ACCENT);
			if (mouseX >= sx && mouseX < sx + 11 && mouseY >= sy && mouseY < sy + 11) {
				graphics.requestCursor(CursorTypes.POINTING_HAND);
				if (i == PALETTE.length) graphics.setTooltipForNextFrame(font, Component.translatable("fancybars.crosshair.customColor"), mouseX, mouseY);
			}
		}

		// scale label + live preview at real size
		int toolsW = px + pw - 12 - toolsX;
		int scaleY = paletteY + 2 * 13 + 6 + 22;
		Theme.textInBox(graphics, font, Component.translatable("fancybars.crosshair.size", Math.round(config().crosshairScale * 100)), toolsX, scaleY, 14, Theme.TEXT_MUTED);
		int prevY = scaleY + 20;
		int prevH = py + ph - 32 - prevY;
		if (prevH > 20) {
			Theme.roundedRect(graphics, toolsX, prevY, toolsW, prevH, 5, 0xFF6A8CB5);
			Theme.roundedRect(graphics, toolsX, prevY + prevH / 2, toolsW, prevH - prevH / 2, 5, 0xFF4F7A3A);
			Crosshair.draw(graphics, toolsX + toolsW / 2f, prevY + prevH / 2f, config().crosshairScale, 1);
		}
		super.extractRenderState(graphics, mouseX, mouseY, a);
	}

	private boolean paintAt(double mouseX, double mouseY) {
		int size = config().crosshairSize;
		int x = (int) Math.floor((mouseX - canvasX) / cell);
		int y = (int) Math.floor((mouseY - canvasY) / cell);
		if (mouseX < canvasX || mouseY < canvasY || x >= size || y >= size) return false;
		Crosshair.pixels()[y * size + x] = painting == 2 ? 0 : color;
		return true;
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
		if (super.mouseClicked(click, doubled)) return true;
		double mx = click.x();
		double my = click.y();
		for (int i = 0; i <= PALETTE.length; i++) {
			int sx = toolsX + i % 9 * 13;
			int sy = paletteY + i / 9 * 13;
			if (mx >= sx && mx < sx + 11 && my >= sy && my < sy + 11) {
				if (i < PALETTE.length) {
					color = PALETTE[i];
				} else if (click.button() == InputConstants.MOUSE_BUTTON_LEFT && color == customColor) {
					minecraft.gui.setScreen(new EditBarColorPopup(Component.translatable("fancybars.crosshair.customColor"), this, picked -> {
						customColor = 0xFF000000 | (picked.getRGB() & 0xFFFFFF);
						color = customColor;
					}, customColor));
				} else {
					color = customColor;
				}
				return true;
			}
		}
		painting = click.button() == InputConstants.MOUSE_BUTTON_RIGHT ? 2 : 1;
		if (paintAt(mx, my)) return true;
		painting = 0;
		return false;
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent click, double dx, double dy) {
		if (painting != 0 && paintAt(click.x(), click.y())) return true;
		return super.mouseDragged(click, dx, dy);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent click) {
		if (painting != 0) {
			painting = 0;
			FancyBarsConfig.save();
			return true;
		}
		return super.mouseReleased(click);
	}

	@Override
	public void onClose() {
		FancyBarsConfig.save();
		minecraft.gui.setScreen(parent);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
