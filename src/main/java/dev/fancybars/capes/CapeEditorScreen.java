package dev.fancybars.capes;

import java.awt.image.BufferedImage;

import org.joml.Matrix3x2fStack;
import org.jspecify.annotations.Nullable;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.platform.cursor.CursorTypes;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import dev.fancybars.FancyBarsMod;
import dev.fancybars.bars.EditBarColorPopup;
import dev.fancybars.config.FancyBarsConfig;
import dev.fancybars.util.gui.ModernButton;
import dev.fancybars.util.gui.ModernSlider;
import dev.fancybars.util.gui.ModernToggle;
import dev.fancybars.util.gui.Theme;

/**
 * Custom cape editor: shows the back of your cape and lets you move (drag), resize (scroll / slider), rotate and
 * flip the picture from your custom cape folder, pick a background colour, and put it on the inside too.
 */
public class CapeEditorScreen extends Screen {
	private static final int ROW = 18;

	private final @Nullable Screen parent;
	private @Nullable BufferedImage source;
	private @Nullable Identifier preview;
	private int previewCounter;
	private boolean dirty = true;
	private boolean rebuild;
	private boolean changed;

	private int px;
	private int py;
	private int pw;
	private int ph;
	private int boxX;
	private int boxY;
	private int boxW;
	private int boxH;
	private int toolsX;
	private int toolsW;
	private boolean dragging;
	private double dragX;
	private double dragY;
	private float startX;
	private float startY;

	public CapeEditorScreen(@Nullable Screen parent) {
		super(Component.translatable("fancybars.capeEditor.title"));
		this.parent = parent;
		loadSource();
	}

	private static FancyBarsConfig c() {
		return FancyBarsConfig.get();
	}

	private void loadSource() {
		source = CapeComposer.readSource(Capes.customFile());
		dirty = true;
	}

	@Override
	protected void init() {
		pw = Math.min(width - 12, 460);
		ph = Math.min(height - 12, 300);
		px = (width - pw) / 2;
		py = (height - ph) / 2;
		boxH = ph - 62;
		boxW = Math.round(boxH * CapeComposer.PANEL_W / (float) CapeComposer.PANEL_H);
		boxX = px + 14;
		boxY = py + 30;
		toolsX = boxX + boxW + 16;
		toolsW = px + pw - 12 - toolsX;
		int y = py + 30;
		int half = (toolsW - 4) / 2;
		int third = (toolsW - 8) / 3;
		int labelW = 46;

		addRenderableWidget(at(new ModernToggle(toolsW, ROW, Component.translatable("fancybars.capeEditor.place"), c().capeCustomMode == 1, v -> {
			c().capeCustomMode = v ? 1 : 0;
			change();
		}), toolsX, y));
		y += ROW + 6;

		addRenderableWidget(at(slider(toolsW - labelW, -1, 1, c().capeImageX, v -> c().capeImageX = v), toolsX + labelW, y));
		y += ROW;
		addRenderableWidget(at(slider(toolsW - labelW, -1, 1, c().capeImageY, v -> c().capeImageY = v), toolsX + labelW, y));
		y += ROW;
		addRenderableWidget(at(slider(toolsW - labelW, 0.1f, 4, c().capeImageScale, v -> c().capeImageScale = v), toolsX + labelW, y));
		y += ROW + 4;

		addRenderableWidget(at(button(third, "fit", () -> c().capeImageScale = 1), toolsX, y));
		addRenderableWidget(at(button(third, "fill", () -> {
			if (source != null) c().capeImageScale = CapeComposer.fillScale(source, c().capeImageRotation);
		}), toolsX + third + 4, y));
		addRenderableWidget(at(button(toolsW - third * 2 - 8, "center", () -> {
			c().capeImageX = 0;
			c().capeImageY = 0;
		}), toolsX + third * 2 + 8, y));
		y += ROW + 2;
		addRenderableWidget(at(button(half, "rotate", () -> c().capeImageRotation = (c().capeImageRotation + 90) % 360), toolsX, y));
		addRenderableWidget(at(button(toolsW - half - 4, "flip", () -> c().capeImageFlip = !c().capeImageFlip), toolsX + half + 4, y));
		y += ROW + 6;

		addRenderableWidget(at(new ModernToggle(toolsW, ROW, Component.translatable("fancybars.capeEditor.inside"), c().capeImageInside, v -> {
			c().capeImageInside = v;
			change();
		}), toolsX, y));
		y += ROW + 2;
		addRenderableWidget(at(new ModernToggle(toolsW, ROW, Component.translatable("fancybars.capeEditor.smooth"), c().capeImageSmooth, v -> {
			c().capeImageSmooth = v;
			change();
		}), toolsX, y));
		y += ROW + 4;
		swatchY = y;
		addRenderableWidget(at(new ModernButton(toolsW, ROW, Component.translatable("fancybars.capeEditor.background"), () ->
				minecraft.gui.setScreen(new EditBarColorPopup(Component.translatable("fancybars.capeEditor.background"), this, color -> {
					c().capeBackground = color.getRGB() & 0xFFFFFF;
					change();
				}, 0xFF000000 | c().capeBackground))), toolsX, y));

		int by = py + ph - 26;
		addRenderableWidget(at(new ModernButton(110, 18, Component.translatable("fancybars.capes.openFolder"), Capes::openCustomFolder), px + 14, by));
		addRenderableWidget(at(new ModernButton(110, 18, Component.translatable("fancybars.capeEditor.reload"), () -> {
			loadSource();
			change();
		}), px + 128, by));
		addRenderableWidget(at(new ModernButton(80, 18, Component.translatable("gui.done"), ModernButton.Style.PRIMARY, this::onClose), px + pw - 94, by));
	}

	private static <T extends net.minecraft.client.gui.components.AbstractWidget> T at(T widget, int x, int y) {
		widget.setPosition(x, y);
		return widget;
	}

	private ModernSlider slider(int w, float min, float max, float value, java.util.function.Consumer<Float> set) {
		return new ModernSlider(w, ROW, min, max, 0, value, v -> {
			set.accept(v);
			dirty = true;
			changed = true;
		}, this::apply);
	}

	private ModernButton button(int w, String key, Runnable action) {
		return new ModernButton(w, ROW - 2, Component.translatable("fancybars.capeEditor." + key), () -> {
			action.run();
			change();
			rebuild = true;
		});
	}

	private void change() {
		dirty = true;
		changed = true;
		apply();
	}

	/** Save and rebuild the in-game cape. */
	private void apply() {
		if (!changed) return;
		changed = false;
		FancyBarsConfig.save();
		Capes.reloadCustom();
	}

	@Override
	public void tick() {
		super.tick();
		if (rebuild) {
			rebuild = false;
			rebuildWidgets();
		}
	}

	private void refreshPreview() {
		dirty = false;
		BufferedImage src = source;
		if (src == null) return;
		NativeImage image;
		if (c().capeCustomMode == 1) {
			BufferedImage composed = CapeComposer.compose(src);
			int k = CapeComposer.K;
			image = CapeComposer.toNative(composed, CapeComposer.PANEL_X * k, CapeComposer.PANEL_Y * k, CapeComposer.PANEL_W * k, CapeComposer.PANEL_H * k);
		} else {
			// raw texture: show the back panel area of the file as-is
			int sx = src.getWidth() / 64;
			int sy = src.getHeight() / 32;
			if (sx < 1 || sy < 1) return;
			image = CapeComposer.toNative(src, CapeComposer.PANEL_X * sx, CapeComposer.PANEL_Y * sy, CapeComposer.PANEL_W * sx, CapeComposer.PANEL_H * sy);
		}
		Identifier id = FancyBarsMod.id("capes/editor_" + previewCounter++);
		DynamicTexture texture = new DynamicTexture(() -> "SkyblockGuiUtils cape editor", image);
		minecraft.getTextureManager().register(id, texture);
		texture.upload();
		Identifier old = preview;
		preview = id;
		if (old != null) minecraft.getTextureManager().release(old);
		previewW = image.getWidth();
		previewH = image.getHeight();
	}

	private int swatchY;
	private int previewW;
	private int previewH;

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		Theme.backdrop(graphics, width, height);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		if (dirty) refreshPreview();
		Theme.panel(graphics, px, py, pw, ph);
		Theme.titleInBox(graphics, font, title, px + 14, py + 5, 18, Theme.TEXT);

		// preview of the back of the cape
		Theme.roundedRect(graphics, boxX - 3, boxY - 3, boxW + 6, boxH + 6, 4, Theme.SURFACE);
		if (source == null) {
			Theme.centeredInBox(graphics, font, Component.translatable("fancybars.capeEditor.noImage1"), boxX + boxW / 2f, boxY + boxH / 2f - 12, 12, Theme.TEXT_MUTED);
			Theme.centeredInBox(graphics, font, Component.translatable("fancybars.capeEditor.noImage2"), boxX + boxW / 2f, boxY + boxH / 2f + 2, 12, Theme.TEXT_MUTED);
		} else if (preview != null) {
			Matrix3x2fStack pose = graphics.pose();
			pose.pushMatrix();
			pose.translate(boxX, boxY);
			pose.scale(boxW / (float) previewW, boxH / (float) previewH);
			graphics.blit(RenderPipelines.GUI_TEXTURED, preview, 0, 0, 0, 0, previewW, previewH, previewW, previewH);
			pose.popMatrix();
		}
		Theme.roundedOutline(graphics, boxX - 1, boxY - 1, boxW + 2, boxH + 2, 2, Theme.SELECTED_BORDER);
		boolean overBox = inBox(mouseX, mouseY);
		if (overBox && source != null && c().capeCustomMode == 1) graphics.requestCursor(dragging ? CursorTypes.RESIZE_ALL : CursorTypes.POINTING_HAND);
		Theme.centeredInBox(graphics, font, Component.translatable(c().capeCustomMode == 1 ? "fancybars.capeEditor.hint" : "fancybars.capeEditor.rawHint"),
				boxX + boxW / 2f, boxY + boxH + 4, 12, Theme.TEXT_MUTED);

		// slider labels + values
		int y = py + 30 + ROW + 6;
		label(graphics, "x", Math.round(c().capeImageX * 100) + "%", y);
		label(graphics, "y", Math.round(c().capeImageY * 100) + "%", y + ROW);
		label(graphics, "size", Math.round(c().capeImageScale * 100) + "%", y + ROW * 2);

		// background swatch next to its button
		super.extractRenderState(graphics, mouseX, mouseY, a);
		Theme.roundedRect(graphics, toolsX + toolsW - 16, swatchY + 4, 10, 10, 3, 0xFF000000 | c().capeBackground);
	}

	private void label(GuiGraphicsExtractor graphics, String key, String value, int y) {
		Theme.textInBox(graphics, font, Component.translatable("fancybars.capeEditor." + key), toolsX, y, ROW / 2f + 1, Theme.TEXT_SOFT);
		Theme.textInBox(graphics, font, Component.literal(value), toolsX, y + ROW / 2f - 1, ROW / 2f + 1, Theme.TEXT_MUTED);
	}

	private boolean inBox(double mx, double my) {
		return mx >= boxX && mx < boxX + boxW && my >= boxY && my < boxY + boxH;
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
		if (super.mouseClicked(click, doubled)) return true;
		if (inBox(click.x(), click.y()) && source != null && c().capeCustomMode == 1) {
			dragging = true;
			dragX = click.x();
			dragY = click.y();
			startX = c().capeImageX;
			startY = c().capeImageY;
			return true;
		}
		return false;
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent click, double dx, double dy) {
		if (dragging) {
			c().capeImageX = Math.clamp(startX + (float) ((click.x() - dragX) / (boxW / 2.0)), -2, 2);
			c().capeImageY = Math.clamp(startY + (float) ((click.y() - dragY) / (boxH / 2.0)), -2, 2);
			dirty = true;
			changed = true;
			return true;
		}
		return super.mouseDragged(click, dx, dy);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent click) {
		if (dragging) {
			dragging = false;
			apply();
			rebuild = true;
			return true;
		}
		return super.mouseReleased(click);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
		if (inBox(mouseX, mouseY) && source != null && c().capeCustomMode == 1 && vertical != 0) {
			c().capeImageScale = Math.clamp(c().capeImageScale * (float) Math.pow(1.05, vertical), 0.1f, 4f);
			change();
			rebuild = true;
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, horizontal, vertical);
	}

	@Override
	public void onClose() {
		apply();
		if (preview != null) minecraft.getTextureManager().release(preview);
		preview = null;
		minecraft.gui.setScreen(parent);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
