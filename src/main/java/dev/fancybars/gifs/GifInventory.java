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

	private static volatile boolean patching;

	/**
	 * Rebuilds the inventory background from your resource pack with the hidden slots removed (see
	 * {@link InventoryPatcher}). Runs once per pack / setting change on a background thread; until it's ready the
	 * inventory just shows normally.
	 */
	public static void preparePatchedBackground() {
		FancyBarsConfig c = FancyBarsConfig.get();
		if (!c.gifsHideCraftingGrid && !c.hideOffhandSlot) return;
		net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
		try {
			var resource = mc.getResourceManager().getResource(INVENTORY_TEXTURE);
			if (resource.isEmpty()) return;
			String key = resource.get().sourcePackId() + "|" + c.gifsHideCraftingGrid + "|" + c.hideOffhandSlot;
			if (key.equals(patchedKey) || patching) return;
			patching = true;
			patchedOk = false;
			int[][] target = {null};
			int[] size = new int[2];
			try (var in = resource.get().open(); var image = com.mojang.blaze3d.platform.NativeImage.read(in)) {
				size[0] = image.getWidth();
				size[1] = image.getHeight();
				target[0] = pixels(image);
			}
			List<InventoryPatcher.Img> others = new java.util.ArrayList<>();
			for (String name : SIBLINGS) {
				var res = mc.getResourceManager().getResource(net.minecraft.resources.Identifier.withDefaultNamespace("textures/gui/container/" + name + ".png"));
				if (res.isEmpty()) continue;
				try (var in = res.get().open(); var img = com.mojang.blaze3d.platform.NativeImage.read(in)) {
					if (img.getWidth() == size[0] && img.getHeight() == size[1]) others.add(new InventoryPatcher.Img(pixels(img), size[0], size[1]));
				} catch (Exception ignored) {
				}
			}
			boolean craft = c.gifsHideCraftingGrid, offhand = c.hideOffhandSlot;
			java.util.concurrent.CompletableFuture.runAsync(() -> {
				float scale = size[0] / 256f;
				int S = Math.max(1, Math.round(scale));
				List<InventoryPatcher.Hole> holes = new java.util.ArrayList<>();
				// open sides = sides that border plain background in the vanilla layout
				if (offhand) holes.add(new InventoryPatcher.Hole(76 * S, 61 * S, 94 * S, 79 * S, true, true, false, true));
				if (craft) holes.add(new InventoryPatcher.Hole(96 * S, 16 * S, 172 * S, 54 * S, true, true, true, false));
				InventoryPatcher.Img img = new InventoryPatcher.Img(target[0], size[0], size[1]);
				InventoryPatcher.patch(img, others, holes.toArray(InventoryPatcher.Hole[]::new), new int[]{4 * S, 4 * S, 172 * S, 162 * S}, scale);
				mc.execute(() -> {
					com.mojang.blaze3d.platform.NativeImage out = new com.mojang.blaze3d.platform.NativeImage(size[0], size[1], false);
					for (int y = 0; y < size[1]; y++) for (int x = 0; x < size[0]; x++) out.setPixel(x, y, img.px()[y * size[0] + x]);
					net.minecraft.client.renderer.texture.DynamicTexture texture = new net.minecraft.client.renderer.texture.DynamicTexture(() -> "SBCLIENT inventory", out);
					mc.getTextureManager().register(PATCHED, texture);
					texture.upload();
					patchedW = size[0];
					patchedH = size[1];
					patchedKey = key;
					patchedOk = true;
					patching = false;
				});
			}, dev.fancybars.FancyBarsMod.VIRTUAL_THREAD_EXECUTOR).exceptionally(e -> {
				dev.fancybars.FancyBarsMod.LOGGER.warn("[SBCLIENT] Couldn't remove the inventory slots", e);
				patching = false;
				return null;
			});
		} catch (Exception e) {
			patching = false;
			dev.fancybars.FancyBarsMod.LOGGER.debug("[SBCLIENT] Couldn't patch the inventory background", e);
		}
	}

	private static int[] pixels(com.mojang.blaze3d.platform.NativeImage img) {
		int w = img.getWidth(), h = img.getHeight();
		int[] px = new int[w * h];
		for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) px[y * w + x] = img.getPixel(x, y);
		return px;
	}

	private static final String[] SIBLINGS = {"generic_54", "shulker_box", "furnace", "blast_furnace", "smoker", "crafting_table", "dispenser",
			"hopper", "brewing_stand", "enchanting_table", "anvil", "grindstone", "loom", "cartography_table", "smithing", "stonecutter",
			"beacon", "horse", "villager", "crafter"};

	/** Draws the patched background over the normal one (before GIFs and the player, so those stay on top). */
	public static void coverHiddenSlots(InventoryScreen screen, GuiGraphicsExtractor graphics) {
		if (!hideCraftingGrid() && !hideOffhand()) return;
		preparePatchedBackground();
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
