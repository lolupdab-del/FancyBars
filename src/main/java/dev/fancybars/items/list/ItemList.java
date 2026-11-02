package dev.fancybars.items.list;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.jspecify.annotations.Nullable;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.cursor.CursorTypes;

import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import dev.fancybars.config.FancyBarsConfig;
import dev.fancybars.mixin.ContainerScreenAccessor;
import dev.fancybars.util.SkyblockLocation;
import dev.fancybars.util.gui.SearchField;
import dev.fancybars.util.gui.Theme;

/**
 * Item List, modelled on Skysoft's (https://github.com/akinsoft/Skysoft, LGPL-3.0): every SkyBlock item in a panel
 * beside your inventory and menus, with search and pages. Hover for the tooltip (prices included), click or press R
 * for recipes, U for what it's used in.
 */
public final class ItemList {
	private static final int SLOT = 18;
	private static final int PAD = 6;
	private static final int HEADER = 16;
	private static final int SEARCH_H = 16;

	private static String query = "";
	private static int page;
	private static @Nullable SearchField search;
	private static List<ItemRepo.Entry> filtered = List.of();
	private static String filteredFor = null;
	private static int filteredSize = -1;

	private ItemList() {}

	public static void init() {
		ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> {
			if (!(screen instanceof AbstractContainerScreen<?> container) || !shown(container)) return;
			ItemRepo.ensureLoaded();
			Layout l = layout(container);
			if (l == null) return;
			search = new SearchField(l.w - PAD * 2, SEARCH_H, query, Component.translatable("fancybars.itemList.search"), text -> {
				query = text.trim();
				page = 0;
			});
			search.setPosition(l.x + PAD, l.y + l.h - PAD - SEARCH_H);
			Screens.getWidgets(screen).add(search);
			ScreenEvents.afterExtract(screen).register((s, graphics, mx, my, a) -> render(container, graphics, mx, my));
		});
	}

	public static boolean shown(Screen screen) {
		if (!(screen instanceof AbstractContainerScreen<?>)) return false;
		if (!FancyBarsConfig.get().itemListEnabled || !SkyblockLocation.isOnSkyblock()) return false;
		return !dev.fancybars.storage.StorageOverlay.active(screen);
	}

	// ---------------------------------------------------------------- layout

	private static final class Layout {
		int x, y, w, h;
		int gridX, gridY, cols, rows;
	}

	private static @Nullable Layout layout(AbstractContainerScreen<?> screen) {
		ContainerScreenAccessor acc = (ContainerScreenAccessor) screen;
		int right = acc.fancybars$getLeftPos() + acc.fancybars$getImageWidth();
		Layout l = new Layout();
		l.x = right + 8;
		l.y = 6;
		l.w = screen.width - l.x - 6;
		l.h = screen.height - 12;
		l.cols = (l.w - PAD * 2) / SLOT;
		l.rows = (l.h - PAD * 2 - HEADER - SEARCH_H - 6) / SLOT;
		if (l.cols < 3 || l.rows < 2) return null;
		l.w = l.cols * SLOT + PAD * 2;
		l.gridX = l.x + PAD;
		l.gridY = l.y + PAD + HEADER;
		return l;
	}

	private static List<ItemRepo.Entry> results() {
		List<ItemRepo.Entry> all = ItemRepo.entries();
		if (query.equals(filteredFor) && all.size() == filteredSize) return filtered;
		String[] words = query.toLowerCase(Locale.ROOT).split("\\s+");
		List<ItemRepo.Entry> out = new ArrayList<>();
		for (ItemRepo.Entry e : all) {
			boolean ok = true;
			for (String word : words) {
				if (!word.isEmpty() && !e.search().contains(word)) {
					ok = false;
					break;
				}
			}
			if (ok) out.add(e);
		}
		filtered = out;
		filteredFor = query;
		filteredSize = all.size();
		return out;
	}

	private static int pages(Layout l) {
		int per = l.cols * l.rows;
		return Math.max(1, (results().size() + per - 1) / per);
	}

	// ---------------------------------------------------------------- drawing

	private static void render(AbstractContainerScreen<?> screen, GuiGraphicsExtractor graphics, int mx, int my) {
		if (!shown(screen)) return;
		Layout l = layout(screen);
		if (l == null) return;
		Font font = Minecraft.getInstance().font;
		graphics.nextStratum();
		Theme.panel(graphics, l.x, l.y, l.w, l.h);
		if (search != null) search.setPosition(l.x + PAD, l.y + l.h - PAD - SEARCH_H);

		List<ItemRepo.Entry> results = results();
		int pages = pages(l);
		page = Math.clamp(page, 0, pages - 1);
		// header: < page/pages >
		Component label = Component.literal((page + 1) + " / " + pages);
		Theme.centeredInBox(graphics, font, label, l.x + l.w / 2f, l.y + PAD, 12, Theme.TEXT_SOFT);
		boolean overPrev = over(mx, my, l.x + PAD, l.y + PAD - 2, 16, 16);
		boolean overNext = over(mx, my, l.x + l.w - PAD - 16, l.y + PAD - 2, 16, 16);
		Theme.centeredInBox(graphics, font, Component.literal("‹"), l.x + PAD + 8, l.y + PAD, 12, overPrev ? Theme.TEXT : Theme.TEXT_MUTED);
		Theme.centeredInBox(graphics, font, Component.literal("›"), l.x + l.w - PAD - 8, l.y + PAD, 12, overNext ? Theme.TEXT : Theme.TEXT_MUTED);
		if (overPrev || overNext) graphics.requestCursor(CursorTypes.POINTING_HAND);

		if (results.isEmpty()) {
			String status = ItemRepo.status();
			Component msg = !status.isEmpty() ? Component.literal(status)
					: Component.translatable(ItemRepo.entries().isEmpty() ? "fancybars.itemList.loading" : "fancybars.itemList.none");
			Theme.textInBox(graphics, font, msg, l.gridX, l.gridY, 14, Theme.TEXT_MUTED);
			return;
		}
		int per = l.cols * l.rows;
		ItemStack hovered = null;
		for (int i = 0; i < per; i++) {
			int idx = page * per + i;
			if (idx >= results.size()) break;
			int sx = l.gridX + i % l.cols * SLOT;
			int sy = l.gridY + i / l.cols * SLOT;
			ItemStack stack = results.get(idx).stack();
			boolean over = over(mx, my, sx, sy, SLOT, SLOT);
			if (over) {
				Theme.roundedRect(graphics, sx, sy, SLOT, SLOT, 3, Theme.SURFACE_HOVER);
				hovered = stack;
			}
			graphics.fakeItem(stack, sx + 1, sy + 1);
		}
		if (hovered != null) {
			graphics.setTooltipForNextFrame(font, hovered, mx, my);
			graphics.requestCursor(CursorTypes.POINTING_HAND);
		}
	}

	private static boolean over(double mx, double my, int x, int y, int w, int h) {
		return mx >= x && mx < x + w && my >= y && my < y + h;
	}

	private static ItemRepo.@Nullable Entry entryAt(AbstractContainerScreen<?> screen, double mx, double my) {
		Layout l = layout(screen);
		if (l == null) return null;
		List<ItemRepo.Entry> results = results();
		int per = l.cols * l.rows;
		if (mx < l.gridX || my < l.gridY) return null;
		int c = (int) ((mx - l.gridX) / SLOT);
		int r = (int) ((my - l.gridY) / SLOT);
		if (c >= l.cols || r >= l.rows) return null;
		int idx = page * per + r * l.cols + c;
		return idx < results.size() ? results.get(idx) : null;
	}

	// ---------------------------------------------------------------- input

	public static boolean mouseClicked(AbstractContainerScreen<?> screen, MouseButtonEvent click) {
		if (!shown(screen)) return false;
		Layout l = layout(screen);
		if (l == null || !over(click.x(), click.y(), l.x, l.y, l.w, l.h)) return false;
		if (over(click.x(), click.y(), l.x + PAD, l.y + PAD - 2, 16, 16)) {
			page = Math.max(0, page - 1);
			return true;
		}
		if (over(click.x(), click.y(), l.x + l.w - PAD - 16, l.y + PAD - 2, 16, 16)) {
			page = Math.min(pages(l) - 1, page + 1);
			return true;
		}
		if (search != null && search.isMouseOver(click.x(), click.y())) return false;
		ItemRepo.Entry e = entryAt(screen, click.x(), click.y());
		if (e != null) {
			RecipeScreen.open(screen, e.id(), click.button() == InputConstants.MOUSE_BUTTON_RIGHT);
		}
		return true; // the panel is never a click "outside" the menu
	}

	public static boolean mouseScrolled(AbstractContainerScreen<?> screen, double mx, double my, double vertical) {
		if (!shown(screen) || vertical == 0) return false;
		Layout l = layout(screen);
		if (l == null || !over(mx, my, l.x, l.y, l.w, l.h)) return false;
		page = Math.clamp(page + (vertical < 0 ? 1 : -1), 0, pages(l) - 1);
		return true;
	}

	/** Typing in the search box; R / U over an item (in the list or your inventory) for recipes / usages. */
	public static boolean keyPressed(AbstractContainerScreen<?> screen, KeyEvent key) {
		if (!shown(screen)) return false;
		if (search != null && search.isFocused()) {
			if (key.isEscape()) {
				search.setFocused(false);
				screen.setFocused(null);
				return true;
			}
			search.keyPressed(key);
			return true;
		}
		boolean recipe = key.key() == InputConstants.KEY_R;
		boolean usage = key.key() == InputConstants.KEY_U;
		if (!recipe && !usage) return false;
		Minecraft mc = Minecraft.getInstance();
		double mx = mc.mouseHandler.xpos() * screen.width / mc.getWindow().getScreenWidth();
		double my = mc.mouseHandler.ypos() * screen.height / mc.getWindow().getScreenHeight();
		String id = null;
		ItemRepo.Entry e = entryAt(screen, mx, my);
		if (e != null) id = e.id();
		else {
			Slot hovered = ((ContainerScreenAccessor) screen).fancybars$getHoveredSlot();
			if (hovered != null && !hovered.getItem().isEmpty()) id = ItemRepo.idOf(hovered.getItem());
		}
		if (id == null || id.isEmpty() || ItemRepo.get(id) == null) return false;
		RecipeScreen.open(screen, id, usage);
		return true;
	}
}
