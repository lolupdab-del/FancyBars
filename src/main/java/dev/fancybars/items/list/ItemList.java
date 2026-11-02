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
		});
	}

	public static boolean shown(Screen screen) {
		if (!(screen instanceof AbstractContainerScreen<?>)) return false;
		if (!FancyBarsConfig.get().itemListEnabled || !SkyblockLocation.isOnSkyblock()) return false;
		if (FancyBarsConfig.get().itemListHideInDungeons && SkyblockLocation.isInDungeons()) return false;
		return !dev.fancybars.storage.StorageOverlay.active(screen);
	}

	// ---------------------------------------------------------------- layout

	private static final class Layout {
		int x, y, w, h;
		int gridX, gridY, cols, rows;
	}

	private static @Nullable Layout layout(AbstractContainerScreen<?> screen) {
		ContainerScreenAccessor acc = (ContainerScreenAccessor) screen;
		return layout(screen.width, screen.height, acc.fancybars$getLeftPos() + acc.fancybars$getImageWidth());
	}

	/** Where the list goes: right of the menu, or where you put it in its Edit GUI. */
	private static @Nullable Layout layout(int screenW, int screenH, int containerRight) {
		FancyBarsConfig cfg = FancyBarsConfig.get();
		boolean custom = cfg.itemListX >= 0 && cfg.itemListY >= 0;
		Layout l = new Layout();
		l.x = custom ? Math.round(cfg.itemListX * screenW) : containerRight + 8;
		l.y = custom ? Math.round(cfg.itemListY * screenH) : 6;
		l.w = screenW - l.x - 6;
		l.h = screenH - l.y - 6;
		int maxCols = (l.w - PAD * 2) / SLOT;
		int maxRows = (l.h - PAD * 2 - HEADER - SEARCH_H - 6) / SLOT;
		FancyBarsConfig c = FancyBarsConfig.get();
		// Columns / Rows settings (0 = as many as fit)
		l.cols = c.itemListColumns > 0 ? Math.min(c.itemListColumns, maxCols) : maxCols;
		l.rows = c.itemListRows > 0 ? Math.min(c.itemListRows, maxRows) : maxRows;
		if (l.cols < 1 || l.rows < 1) return null;
		l.w = l.cols * SLOT + PAD * 2;
		l.h = PAD * 2 + HEADER + l.rows * SLOT + 6 + SEARCH_H;
		// keep it vertically centred when it's shorter than the screen (unless you placed it)
		if (!custom) l.y = Math.max(6, (screenH - l.h) / 2);
		l.gridX = l.x + PAD;
		l.gridY = l.y + PAD + HEADER;
		return l;
	}

	// ---------------------------------------------------------------- sorting

	/** 0 Name A-Z, 1 Name Z-A, 2 Value high-low, 3 Value low-high, 4 Rarity high-low, 5 Rarity low-high. */
	public static final int SORTS = 6;
	private static int sortedBy = -1;
	private static int sortedPrices = -1;
	private static final String[] RARITIES = {"COMMON", "UNCOMMON", "RARE", "EPIC", "LEGENDARY", "MYTHIC", "DIVINE", "SPECIAL", "VERY SPECIAL", "ULTIMATE", "ADMIN"};
	private static final java.util.Map<String, Integer> RARITY = new java.util.HashMap<>();

	/** Rarity rank from the item's last lore line ("LEGENDARY SWORD"), -1 if it has none. */
	private static int rarity(ItemRepo.Entry e) {
		return RARITY.computeIfAbsent(e.id(), id -> {
			for (int i = e.lore().size() - 1; i >= 0; i--) {
				String line = net.minecraft.ChatFormatting.stripFormatting(e.lore().get(i).getString());
				if (line == null || line.isBlank()) continue;
				line = line.trim().toUpperCase(Locale.ROOT);
				int best = -1;
				for (int r = 0; r < RARITIES.length; r++) if (line.startsWith(RARITIES[r] + " ") || line.equals(RARITIES[r])) best = r;
				return best;
			}
			return -1;
		});
	}

	private static java.util.Comparator<ItemRepo.Entry> comparator(int sort) {
		java.util.Comparator<ItemRepo.Entry> byName = java.util.Comparator.comparing(ItemRepo.Entry::name, String.CASE_INSENSITIVE_ORDER);
		return switch (sort) {
			case 1 -> byName.reversed();
			case 2, 3 -> (a, b) -> {
				double va = dev.fancybars.items.ItemValue.valueOf(a.id());
				double vb = dev.fancybars.items.ItemValue.valueOf(b.id());
				// items without a price always go last
				if (va < 0 || vb < 0) return va < 0 && vb < 0 ? byName.compare(a, b) : va < 0 ? 1 : -1;
				int c = sort == 2 ? Double.compare(vb, va) : Double.compare(va, vb);
				return c != 0 ? c : byName.compare(a, b);
			};
			case 4, 5 -> (a, b) -> {
				int ra = rarity(a), rb = rarity(b);
				if (ra < 0 || rb < 0) return ra < 0 && rb < 0 ? byName.compare(a, b) : ra < 0 ? 1 : -1;
				int c = sort == 4 ? Integer.compare(rb, ra) : Integer.compare(ra, rb);
				return c != 0 ? c : byName.compare(a, b);
			};
			default -> byName;
		};
	}

	private static List<ItemRepo.Entry> results() {
		List<ItemRepo.Entry> all = ItemRepo.entries();
		int sort = Math.floorMod(FancyBarsConfig.get().itemListSort, SORTS);
		int prices = dev.fancybars.items.ItemValue.version;
		boolean resort = sort != sortedBy || (sort == 2 || sort == 3) && prices != sortedPrices;
		if (!resort && query.equals(filteredFor) && all.size() == filteredSize) return filtered;
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
		if (sort == 2 || sort == 3) dev.fancybars.items.ItemValue.ensureLoaded();
		out.sort(comparator(sort));
		sortedBy = sort;
		sortedPrices = prices;
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

	/** Drawn from inside the menu's own drawing (so its tooltip isn't thrown away). */
	public static void render(AbstractContainerScreen<?> screen, GuiGraphicsExtractor graphics, int mx, int my) {
		if (!shown(screen)) return;
		Layout l = layout(screen);
		if (l == null) return;
		// draw in plain screen coordinates whatever the menu's drawing left the pose at
		graphics.pose().pushMatrix();
		graphics.pose().identity();
		try {
			draw(screen, graphics, mx, my, l);
		} finally {
			graphics.pose().popMatrix();
		}
	}

	private static void draw(AbstractContainerScreen<?> screen, GuiGraphicsExtractor graphics, int mx, int my, Layout l) {
		Font font = Minecraft.getInstance().font;
		graphics.nextStratum();
		if (FancyBarsConfig.get().itemListBackground) Theme.panel(graphics, l.x, l.y, l.w, l.h);
		if (search != null) search.setPosition(l.x + PAD, l.y + l.h - PAD - SEARCH_H);

		List<ItemRepo.Entry> results = results();
		int pages = pages(l);
		page = Math.clamp(page, 0, pages - 1);
		// header: < sort · page/pages >   (click the middle to change the sort)
		Component label = Component.translatable("fancybars.itemList.sortShort." + Math.floorMod(FancyBarsConfig.get().itemListSort, SORTS))
				.append("  " + (page + 1) + "/" + pages);
		boolean overSort = over(mx, my, l.x + PAD + 16, l.y + PAD - 2, l.w - PAD * 2 - 32, 16);
		Theme.centeredInBox(graphics, font, label, l.x + l.w / 2f, l.y + PAD, 12, overSort ? Theme.TEXT : Theme.TEXT_SOFT);
		if (overSort) {
			graphics.setTooltipForNextFrame(font, Component.translatable("fancybars.itemList.sortHint",
					Component.translatable("fancybars.itemList.sort." + Math.floorMod(FancyBarsConfig.get().itemListSort, SORTS))), mx, my);
			graphics.requestCursor(CursorTypes.POINTING_HAND);
		}
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
			dev.fancybars.util.gui.CachedItems.draw(graphics, stack, sx + 1, sy + 1);
		}
		if (hovered != null) {
			graphics.setTooltipForNextFrame(font, tooltip(hovered), java.util.Optional.empty(), mx, my);
			graphics.requestCursor(CursorTypes.POINTING_HAND);
		}
	}

	/** The full tooltip: name, lore (from the item, or straight from the item data if the item lost it) and prices. */
	static List<Component> tooltip(ItemStack stack) {
		Minecraft mc = Minecraft.getInstance();
		List<Component> lines = new ArrayList<>(Screen.getTooltipFromItem(mc, stack));
		if (stack.get(net.minecraft.core.component.DataComponents.LORE) == null) {
			ItemRepo.Entry e = ItemRepo.get(ItemRepo.idOf(stack));
			if (e != null && !e.lore().isEmpty()) lines.addAll(1, e.lore());
		}
		return lines;
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
		if (over(click.x(), click.y(), l.x + PAD + 16, l.y + PAD - 2, l.w - PAD * 2 - 32, 16)) {
			// cycle the sort (right-click goes backwards)
			FancyBarsConfig c = FancyBarsConfig.get();
			c.itemListSort = Math.floorMod(c.itemListSort + (click.button() == InputConstants.MOUSE_BUTTON_RIGHT ? -1 : 1), SORTS);
			FancyBarsConfig.save();
			page = 0;
			return true;
		}
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

	// ---------------------------------------------------------------- its own Edit GUI

	/** {x, y, w, h} of the list for an ordinary inventory on a screen this size. */
	public static float[] previewRect(int screenW, int screenH) {
		Layout l = layout(screenW, screenH, screenW / 2 + 88);
		if (l == null) return new float[]{screenW - 60, 6, 54, 54};
		return new float[]{l.x, l.y, l.w, l.h};
	}

	public static void drawPreview(GuiGraphicsExtractor graphics, int screenW, int screenH) {
		Layout l = layout(screenW, screenH, screenW / 2 + 88);
		if (l == null) return;
		ItemRepo.ensureLoaded();
		Theme.panel(graphics, l.x, l.y, l.w, l.h);
		List<ItemRepo.Entry> all = ItemRepo.entries();
		for (int i = 0; i < l.cols * l.rows; i++) {
			int sx = l.gridX + i % l.cols * SLOT;
			int sy = l.gridY + i / l.cols * SLOT;
			if (i < all.size()) dev.fancybars.util.gui.CachedItems.draw(graphics, all.get(i).stack(), sx + 1, sy + 1);
			else graphics.fill(sx + 1, sy + 1, sx + SLOT - 1, sy + SLOT - 1, 0x33FFFFFF);
		}
		Theme.textInBox(graphics, Minecraft.getInstance().font, Component.translatable("fancybars.itemList.search"), l.x + PAD + 4, l.y + l.h - PAD - SEARCH_H, SEARCH_H, Theme.TEXT_MUTED);
	}
}
