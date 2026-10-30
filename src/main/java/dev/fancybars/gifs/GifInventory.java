package dev.fancybars.gifs;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
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
	private static ModernButton toggleButton;
	private static final List<ModernButton> editButtons = new ArrayList<>();

	private GifInventory() {}

	public static void init() {
		ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
			if (screen instanceof InventoryScreen inventory && enabled()) setupInventory(inventory);
		});
	}

	public static boolean enabled() {
		return FancyBarsConfig.get().gifsEnabled;
	}

	public static boolean hideCraftingGrid() {
		return enabled() && FancyBarsConfig.get().gifsHideCraftingGrid;
	}

	public static boolean isHiddenCraftSlot(Object screen, Slot slot) {
		return hideCraftingGrid() && screen instanceof InventoryScreen && slot != null && slot.index >= 0 && slot.index <= 4;
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

		int x = panel[0] + panel[2] + 4;
		int y = panel[1];
		toggleButton = new ModernButton(64, 18, Component.translatable("fancybars.gifs.button"), () -> setEditing(!editing));
		toggleButton.setPosition(x, y);
		editButtons.clear();
		editButtons.add(button(x, y + 22, "fancybars.gifs.openFolder", GifInventory::openFolder));
		editButtons.add(button(x, y + 44, "fancybars.gifs.reload", GifStore::reload));
		editButtons.add(button(x, y + 66, "fancybars.gifs.showAll", GifInventory::showAll));
		widgets.add(toggleButton);
		widgets.addAll(editButtons);
		setEditing(false);

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
				setEditing(false);
				return false;
			}
			return true;
		});
	}

	private static ModernButton button(int x, int y, String key, Runnable action) {
		ModernButton button = new ModernButton(64, 18, Component.translatable(key), action);
		button.setPosition(x, y);
		return button;
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
		if (toggleButton != null) toggleButton.setMessage(Component.translatable(value ? "gui.done" : "fancybars.gifs.button"));
		for (ModernButton button : editButtons) {
			button.visible = value;
			button.active = value;
		}
	}

	private static boolean overOurButtons(double mouseX, double mouseY) {
		if (toggleButton != null && toggleButton.isMouseOver(mouseX, mouseY)) return true;
		for (ModernButton button : editButtons) {
			if (button.visible && button.isMouseOver(mouseX, mouseY)) return true;
		}
		return false;
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
					Theme.card(graphics, x, y - 17, lw, 14, 7, Theme.PANEL, 0x887CC8FF);
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
