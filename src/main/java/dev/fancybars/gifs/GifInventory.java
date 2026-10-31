package dev.fancybars.gifs;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import net.minecraft.world.inventory.Slot;

import dev.fancybars.FancyBarsMod;
import dev.fancybars.config.FancyBarsConfig;
import dev.fancybars.mixin.ContainerScreenAccessor;
import dev.fancybars.util.gui.ModernButton;
import dev.fancybars.util.gui.Theme;

/**
 * Animated GIFs in the player inventory, with in-inventory editing (drag, resize, scroll, opacity).
 * Ported from Inventory GIFs by lolupdab (MIT) and hooked up to Fancy Bars' settings.
 */
public final class GifInventory {
	private static boolean editing = false;
	private static GifStore.Gif dragging = null;
	private static boolean resizing = false;
	private static double offX;
	private static double offY;
	private static boolean drawnThisFrame = false;
	private static ModernButton doneButton;
	/** Set by {@link #openEditor}: the next inventory opens straight into edit mode. */
	private static boolean editOnNextOpen = false;
	private static Screen returnScreen = null;

	private GifInventory() {}

	public static void init() {
		ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
			if (screen instanceof InventoryScreen inventory) {
				preparePatchedBackground();
				if (enabled()) setupInventory(inventory);
				else if (FancyBarsConfig.get().gifsHideRecipeBook && hideCraftingGrid()) {
					int[] panel = panel(inventory);
					int bookX = panel[0] + 104;
					int bookY = inventory.height / 2 - 22;
					Screens.getWidgets(inventory).removeIf(widget -> widget.getX() == bookX && widget.getY() == bookY);
				}
			}
		});
	}

	public static boolean enabled() {
		return FancyBarsConfig.get().gifsEnabled;
	}

	/** Works on its own now (it used to need Inventory GIFs switched on). */
	public static boolean hideCraftingGrid() {
		return FancyBarsConfig.get().gifsHideCraftingGrid;
	}

	public static boolean hideOffhand() {
		return FancyBarsConfig.get().hideOffhandSlot;
	}

	/** Crafting result + 2x2 grid are menu slots 0-4, the offhand is 45. */
	public static boolean isHiddenCraftSlot(Object screen, Slot slot) {
		if (!(screen instanceof InventoryScreen) || slot == null) return false;
		return hideCraftingGrid() && slot.index >= 0 && slot.index <= 4 || hideOffhand() && slot.index == 45;
	}

	private static final net.minecraft.resources.Identifier INVENTORY_TEXTURE = net.minecraft.resources.Identifier.withDefaultNamespace("textures/gui/container/inventory.png");
	private static final net.minecraft.resources.Identifier PATCHED = dev.fancybars.FancyBarsMod.id("gifs/inventory_patched");
	private static String patchedKey = "";
	private static int patchedW;
	private static int patchedH;
	private static boolean patchedOk;

	/**
	 * Rebuilds the inventory background from your resource pack with the hidden slots filled in. Each hidden area is
	 * filled by blending the colours around its edges (a Coons patch), so gradients and artwork flow through it
	 * instead of a mismatched block.
	 */
	public static void preparePatchedBackground() {
		FancyBarsConfig c = FancyBarsConfig.get();
		if (!c.gifsHideCraftingGrid && !c.hideOffhandSlot) return;
		net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
		try {
			var resource = mc.getResourceManager().getResource(INVENTORY_TEXTURE);
			if (resource.isEmpty()) {
				patchedOk = false;
				return;
			}
			String key = resource.get().sourcePackId() + "|" + c.gifsHideCraftingGrid + "|" + c.hideOffhandSlot;
			if (patchedOk && key.equals(patchedKey)) return;
			com.mojang.blaze3d.platform.NativeImage image;
			try (var in = resource.get().open()) {
				image = com.mojang.blaze3d.platform.NativeImage.read(in);
			}
			float scale = image.getWidth() / 256f;
			if (c.gifsHideCraftingGrid) fill(image, scale, 96, 15, 172, 57);
			if (c.hideOffhandSlot) fill(image, scale, 75, 60, 95, 80);
			patchedW = image.getWidth();
			patchedH = image.getHeight();
			net.minecraft.client.renderer.texture.DynamicTexture texture = new net.minecraft.client.renderer.texture.DynamicTexture(() -> "SBCLIENT inventory", image);
			mc.getTextureManager().register(PATCHED, texture);
			texture.upload();
			patchedKey = key;
			patchedOk = true;
		} catch (Exception e) {
			patchedOk = false;
			dev.fancybars.FancyBarsMod.LOGGER.debug("[SBCLIENT] Couldn't patch the inventory background", e);
		}
	}

	/** Fills the box (in vanilla 256-px texture units, end exclusive) from its surrounding pixels. */
	private static void fill(com.mojang.blaze3d.platform.NativeImage img, float scale, int x0, int y0, int x1, int y1) {
		int ax = Math.max(1, Math.round(x0 * scale));
		int ay = Math.max(1, Math.round(y0 * scale));
		int bx = Math.min(img.getWidth() - 1, Math.round(x1 * scale));
		int by = Math.min(img.getHeight() - 1, Math.round(y1 * scale));
		if (bx - ax < 2 || by - ay < 2) return;
		int w = bx - ax;
		int h = by - ay;
		int[] top = new int[w];
		int[] bottom = new int[w];
		int[] left = new int[h];
		int[] right = new int[h];
		for (int i = 0; i < w; i++) {
			top[i] = img.getPixel(ax + i, ay - 1);
			bottom[i] = img.getPixel(ax + i, by);
		}
		for (int j = 0; j < h; j++) {
			left[j] = img.getPixel(ax - 1, ay + j);
			right[j] = img.getPixel(bx, ay + j);
		}
		int c00 = img.getPixel(ax - 1, ay - 1);
		int c10 = img.getPixel(bx, ay - 1);
		int c01 = img.getPixel(ax - 1, by);
		int c11 = img.getPixel(bx, by);
		for (int j = 0; j < h; j++) {
			float v = (j + 1f) / (h + 1f);
			for (int i = 0; i < w; i++) {
				float u = (i + 1f) / (w + 1f);
				int out = 0;
				for (int shift = 0; shift <= 24; shift += 8) {
					float l = left[j] >>> shift & 0xFF, r = right[j] >>> shift & 0xFF, t = top[i] >>> shift & 0xFF, b = bottom[i] >>> shift & 0xFF;
					float k00 = c00 >>> shift & 0xFF, k10 = c10 >>> shift & 0xFF, k01 = c01 >>> shift & 0xFF, k11 = c11 >>> shift & 0xFF;
					// Coons patch: edge blends minus the corner blend
					float value = (1 - u) * l + u * r + (1 - v) * t + v * b
							- ((1 - u) * (1 - v) * k00 + u * (1 - v) * k10 + (1 - u) * v * k01 + u * v * k11);
					out |= Math.clamp(Math.round(value), 0, 255) << shift;
				}
				img.setPixel(ax + i, ay + j, out);
			}
		}
	}

	/** Draws the patched background over the normal one (before GIFs and the player, so those stay on top). */
	public static void coverHiddenSlots(InventoryScreen screen, GuiGraphicsExtractor graphics) {
		if (!hideCraftingGrid() && !hideOffhand()) return;
		if (!patchedOk) preparePatchedBackground();
		if (!patchedOk) return;
		int[] p = panel(screen);
		float s = patchedW / 256f;
		org.joml.Matrix3x2fStack pose = graphics.pose();
		pose.pushMatrix();
		pose.translate(p[0], p[1]);
		pose.scale(1 / s, 1 / s);
		graphics.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, PATCHED, 0, 0, 0, 0, Math.round(p[2] * s), Math.round(p[3] * s), patchedW, patchedH);
		pose.popMatrix();
	}


	public static void drawGifs(InventoryScreen screen, GuiGraphicsExtractor graphics, boolean fallback) {
		if (!enabled()) return;
		if (fallback && drawnThisFrame) return;
		drawnThisFrame = true;
		try {
			GifStore.ensureLoaded();
			int[] panel = panel(screen);
			GifStore.drawAll(graphics, panel[0], panel[1]);
		} catch (LinkageError | RuntimeException ignored) {
		}
	}

	private static int[] panel(Screen screen) {
		ContainerScreenAccessor accessor = (ContainerScreenAccessor) screen;
		return new int[]{accessor.fancybars$getLeftPos(), accessor.fancybars$getTopPos(), accessor.fancybars$getImageWidth(), accessor.fancybars$getImageHeight()};
	}

	private static void setupInventory(InventoryScreen screen) {
		GifStore.ensureLoaded();
		editing = false;
		dragging = null;
		int[] panel = panel(screen);
		List<AbstractWidget> widgets = Screens.getWidgets(screen);

		if (FancyBarsConfig.get().gifsHideRecipeBook && hideCraftingGrid()) {
			int bookX = panel[0] + 104;
			int bookY = screen.height / 2 - 22;
			widgets.removeIf(widget -> widget.getX() == bookX && widget.getY() == bookY);
		}

		// No buttons in the normal inventory: editing is started from the Fancy Bars menu (GIFs page)
		doneButton = null;
		if (editOnNextOpen) {
			editOnNextOpen = false;
			doneButton = new ModernButton(64, 18, Component.translatable("gui.done"), ModernButton.Style.PRIMARY, () -> finishEditing(screen));
			doneButton.setPosition(panel[0] + panel[2] + 4, panel[1]);
			widgets.add(doneButton);
			setEditing(true);
		} else {
			setEditing(false);
		}

		ScreenEvents.beforeExtract(screen).register((s, graphics, mouseX, mouseY, delta) -> drawnThisFrame = false);
		ScreenEvents.afterExtract(screen).register((s, graphics, mouseX, mouseY, delta) -> drawEditOverlay(s, graphics, mouseX, mouseY));
		ScreenEvents.remove(screen).register(s -> {
			if (editing) GifStore.save();
			editing = false;
			dragging = null;
		});
		ScreenMouseEvents.allowMouseClick(screen).register((s, click) -> onClick(s, click));
		ScreenMouseEvents.allowMouseDrag(screen).register((s, click, dx, dy) -> onDrag(s, click));
		ScreenMouseEvents.allowMouseRelease(screen).register((s, click) -> onRelease());
		ScreenMouseEvents.allowMouseScroll(screen).register((s, mouseX, mouseY, horizontal, vertical) -> onScroll(s, mouseX, mouseY, vertical));
		ScreenKeyboardEvents.allowKeyPress(screen).register((s, key) -> {
			if (editing && key.key() == 256) {
				finishEditing(s);
				return false;
			}
			return true;
		});
	}

	/**
	 * Opens the player inventory in GIF edit mode; when done, goes back to {@code returnTo}.
	 */
	public static void openEditor(Screen returnTo) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.player == null || !enabled()) return;
		editOnNextOpen = true;
		returnScreen = returnTo;
		minecraft.gui.setScreen(new InventoryScreen(minecraft.player));
	}

	private static void finishEditing(Screen screen) {
		setEditing(false);
		Screen back = returnScreen;
		returnScreen = null;
		if (back != null) Minecraft.getInstance().gui.setScreen(back);
		else screen.onClose();
	}

	public static void showAll() {
		for (GifStore.Gif gif : GifStore.all()) gif.hidden = false;
		GifStore.save();
	}

	public static void openFolder() {
		try {
			Path folder = GifStore.folder();
			Files.createDirectories(folder);
			Util.getPlatform().openPath(folder);
		} catch (Exception e) {
			FancyBarsMod.LOGGER.error("[FancyBars] Could not open GIF folder", e);
		}
	}

	private static void setEditing(boolean value) {
		if (editing && !value) GifStore.save();
		editing = value;
		dragging = null;

	}

	private static boolean overOurButtons(double mouseX, double mouseY) {
		return doneButton != null && doneButton.isMouseOver(mouseX, mouseY);
	}

	private static GifStore.Gif gifAt(Screen screen, double mouseX, double mouseY) {
		int[] panel = panel(screen);
		List<GifStore.Gif> gifs = GifStore.all();
		for (int i = gifs.size() - 1; i >= 0; i--) {
			GifStore.Gif gif = gifs.get(i);
			if (!gif.ready || gif.hidden) continue;
			int x = panel[0] + gif.x;
			int y = panel[1] + gif.y;
			if (mouseX >= x && mouseX <= x + gif.w && mouseY >= y && mouseY <= y + gif.h) return gif;
		}
		return null;
	}

	private static boolean inHandle(Screen screen, GifStore.Gif gif, double mouseX, double mouseY) {
		int[] panel = panel(screen);
		int right = panel[0] + gif.x + gif.w;
		int bottom = panel[1] + gif.y + gif.h;
		return mouseX >= right - 6 && mouseX <= right + 2 && mouseY >= bottom - 6 && mouseY <= bottom + 2;
	}

	private static boolean onClick(Screen screen, MouseButtonEvent click) {
		if (!editing || overOurButtons(click.x(), click.y())) return true;
		GifStore.Gif gif = gifAt(screen, click.x(), click.y());
		if (gif != null) {
			if (click.button() == 0) {
				int[] panel = panel(screen);
				dragging = gif;
				resizing = inHandle(screen, gif, click.x(), click.y());
				offX = click.x() - (panel[0] + gif.x);
				offY = click.y() - (panel[1] + gif.y);
			} else if (click.button() == 1) {
				gif.hidden = true;
				GifStore.save();
			}
		}
		return false;
	}

	private static boolean onDrag(Screen screen, MouseButtonEvent click) {
		if (!editing) return true;
		if (dragging == null) return false;
		int[] panel = panel(screen);
		if (resizing) {
			int w = Math.max(8, (int) Math.round(click.x() - (panel[0] + dragging.x)));
			int h = Minecraft.getInstance().hasShiftDown()
					? Math.max(8, (int) Math.round(click.y() - (panel[1] + dragging.y)))
					: Math.max(8, Math.round((float) (w * dragging.srcH) / Math.max(1, dragging.srcW)));
			dragging.w = Math.min(w, 2000);
			dragging.h = Math.min(h, 2000);
		} else {
			dragging.x = (int) Math.round(click.x() - offX) - panel[0];
			dragging.y = (int) Math.round(click.y() - offY) - panel[1];
		}
		return false;
	}

	private static boolean onRelease() {
		if (!editing) return true;
		if (dragging != null) {
			dragging = null;
			GifStore.save();
		}
		return false;
	}

	private static boolean onScroll(Screen screen, double mouseX, double mouseY, double vertical) {
		if (!editing || vertical == 0) return true;
		GifStore.Gif gif = gifAt(screen, mouseX, mouseY);
		if (gif == null) return false;
		if (Minecraft.getInstance().hasShiftDown()) {
			gif.opacity = Math.clamp(Math.round((gif.opacity + (vertical > 0 ? 0.1f : -0.1f)) * 10) / 10f, 0.1f, 1f);
		} else {
			float factor = vertical > 0 ? 1.1f : 1 / 1.1f;
			int cx = gif.x + gif.w / 2;
			int cy = gif.y + gif.h / 2;
			gif.w = Math.clamp(Math.round(gif.w * factor), 8, 2000);
			gif.h = Math.clamp(Math.round(gif.h * factor), 8, 2000);
			gif.x = cx - gif.w / 2;
			gif.y = cy - gif.h / 2;
		}
		GifStore.save();
		return false;
	}

	private static void drawEditOverlay(Screen screen, GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		if (!editing) return;
		try {
			int[] panel = panel(screen);
			Font font = Minecraft.getInstance().font;
			GifStore.Gif hovered = dragging != null ? dragging : gifAt(screen, mouseX, mouseY);
			for (GifStore.Gif gif : GifStore.all()) {
				if (!gif.ready || gif.hidden || (dragging != null && gif != dragging)) continue;
				int x = panel[0] + gif.x;
				int y = panel[1] + gif.y;
				boolean active = gif == hovered;
				Theme.roundedOutline(graphics, x - 2, y - 2, gif.w + 4, gif.h + 4, 3, active ? Theme.ACCENT : 0x88FFFFFF);
				Theme.roundedRect(graphics, x + gif.w - 4, y + gif.h - 4, 6, 6, 2, active ? Theme.ACCENT : 0xCCFFFFFF);
				if (active) {
					Component label = Component.literal(gif.name + "  " + gif.w + "×" + gif.h + "  " + Math.round(gif.opacity * 100) + "%");
					int lw = Theme.width(font, label) + 12;
					Theme.card(graphics, x, y - 17, lw, 14, 7, Theme.PANEL, 0x88FF8FC8);
					Theme.textInBox(graphics, font, label, x + 6, y - 17, 14, Theme.TEXT);
				}
			}
			if (dragging == null) {
				int ly = panel[1] + panel[3] + 6;
				for (String key : new String[]{"fancybars.gifs.hint1", "fancybars.gifs.hint2", "fancybars.gifs.hint3"}) {
					Theme.centeredText(graphics, font, Component.translatable(key), screen.width / 2, ly, Theme.TEXT);
					ly += 11;
				}
				int errors = 0;
				for (GifStore.Gif gif : GifStore.all()) if (gif.error != null) errors++;
				if (errors > 0) Theme.centeredText(graphics, font, Component.translatable("fancybars.gifs.errors", errors), screen.width / 2, ly, 0xFFFF7070);
			}
		} catch (LinkageError | RuntimeException ignored) {
		}
	}
}
