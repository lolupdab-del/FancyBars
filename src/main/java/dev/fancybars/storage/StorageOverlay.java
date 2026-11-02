package dev.fancybars.storage;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

import org.jspecify.annotations.Nullable;

import com.mojang.blaze3d.platform.cursor.CursorTypes;

import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;

import dev.fancybars.config.FancyBarsConfig;
import dev.fancybars.mixin.ContainerScreenAccessor;
import dev.fancybars.protection.ItemProtection;
import dev.fancybars.util.SkyblockLocation;
import dev.fancybars.util.gui.SearchField;
import dev.fancybars.util.gui.Theme;

/**
 * Storage Overlay, modelled on Skysoft's (https://github.com/akinsoft/Skysoft, LGPL-3.0): opening /storage, an ender
 * chest page or a backpack shows every page at once in one scrollable, searchable panel. The page you have open is
 * the real menu (its slots are moved into the panel, so everything works as normal); the others are drawn from the
 * cache the backpack preview keeps, and clicking one opens it.
 */
public final class StorageOverlay {
	private static final Pattern OVERVIEW = Pattern.compile("^Storage$");
	private static final int OFFSCREEN = -10000;
	private static final int SLOT = 18;
	private static final int PAGE_W = 9 * SLOT + 8;
	private static final int TITLE_H = 13;
	private static final int GAP = 6;
	private static final int PAD = 8;
	private static final int SEARCH_H = 16;
	private static final int PLAYER_H = 4 * SLOT + 4;

	private static float scroll;
	private static float scrollTarget;
	private static String query = "";
	private static @Nullable SearchField search;
	private static @Nullable AbstractContainerScreen<?> current;
	/** Page you asked to open; scrolled into view when it arrives. */
	private static int scrollToPage = -1;
	private static int lastActive = -2;

	private StorageOverlay() {}

	public static void init() {
		ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> {
			if (!(screen instanceof AbstractContainerScreen<?> container) || !active(container)) return;
			current = container;
			Layout l = layout(container);
			search = new SearchField(l.searchW, SEARCH_H, query, Component.translatable("fancybars.storage.search"), text -> query = text.trim());
			search.setPosition(l.searchX, l.searchY);
			Screens.getWidgets(screen).add(search);
			ScreenEvents.remove(screen).register(s -> {
				if (current == s) current = null;
			});
		});
	}

	// ---------------------------------------------------------------- when

	public static boolean active(@Nullable Screen screen) {
		if (!(screen instanceof ContainerScreen container)) return false;
		if (!FancyBarsConfig.get().storageOverlay || !SkyblockLocation.isOnSkyblock()) return false;
		String title = container.getTitle().getString();
		return OVERVIEW.matcher(title).matches() || BackpackPreview.getStorageIndexFromTitle(title) >= 0;
	}

	/** Index (0-8 ender chest, 9-26 backpacks) of the page that's open, or -1 on the Storage overview. */
	private static int activePage(AbstractContainerScreen<?> screen) {
		return BackpackPreview.getStorageIndexFromTitle(screen.getTitle().getString());
	}

	// ---------------------------------------------------------------- layout

	private record PageBox(int index, int x, int y, int rows) {
		int h() {
			return TITLE_H + rows * SLOT + 6;
		}
	}

	private static final class Layout {
		int panelX, panelY, panelW, panelH;
		int viewX, viewY, viewW, viewH;
		int columns;
		int searchX, searchY, searchW;
		int playerX, playerY;
		int contentH;
		final List<PageBox> pages = new ArrayList<>();
	}

	private static int rowsOf(int index, AbstractContainerScreen<?> screen) {
		if (index == activePage(screen)) {
			int contentSlots = 0;
			for (Slot slot : screen.getMenu().slots) {
				if (!(slot.container instanceof Inventory)) contentSlots++;
			}
			return Math.max(1, (contentSlots - 9) / 9);
		}
		BackpackPreview.Storage s = BackpackPreview.storage(index);
		return s == null ? 0 : Math.max(1, (s.size() - 9) / 9);
	}

	private static boolean pageExists(int index, AbstractContainerScreen<?> screen) {
		return index == activePage(screen) || BackpackPreview.storage(index) != null;
	}

	/** The layout is worked out once per frame (it was being rebuilt several times a frame). */
	private static @Nullable Layout cachedLayout;
	private static @Nullable AbstractContainerScreen<?> cachedFor;
	private static long cachedAt = -1;

	private static Layout layout(AbstractContainerScreen<?> screen) {
		long frame = net.minecraft.util.Util.getMillis() / 8;
		if (cachedLayout != null && cachedFor == screen && cachedAt == frame) return cachedLayout;
		Layout l = computeLayout(screen);
		cachedLayout = l;
		cachedFor = screen;
		cachedAt = frame;
		return l;
	}

	private static Layout computeLayout(AbstractContainerScreen<?> screen) {
		Layout l = new Layout();
		int maxColumns = Math.max(1, (screen.width - PAD * 4 - 8) / (PAGE_W + GAP));
		l.columns = Math.clamp(FancyBarsConfig.get().storageOverlayColumns, 1, maxColumns);
		l.panelW = l.columns * PAGE_W + (l.columns - 1) * GAP + PAD * 2 + 6;
		l.panelX = (screen.width - l.panelW) / 2;
		l.panelY = 6;
		int bottom = PLAYER_H + SEARCH_H + 4 + 10;
		l.panelH = Math.max(60, screen.height - l.panelY - bottom - 6);
		l.viewX = l.panelX + PAD;
		l.viewY = l.panelY + PAD;
		l.viewW = l.panelW - PAD * 2 - 6;
		l.viewH = l.panelH - PAD * 2;
		l.searchW = 9 * SLOT;
		l.searchX = (screen.width - l.searchW) / 2;
		l.searchY = l.panelY + l.panelH + 5;
		l.playerX = (screen.width - 9 * SLOT) / 2;
		l.playerY = l.searchY + SEARCH_H + 5;

		int active = activePage(screen);
		int col = 0;
		int y = 0;
		int rowH = 0;
		for (int i = 0; i < 27; i++) {
			if (!pageExists(i, screen)) continue;
			if (i != active && !matchesPage(i)) continue;
			int rows = rowsOf(i, screen);
			PageBox box = new PageBox(i, l.viewX + col * (PAGE_W + GAP), y, rows);
			l.pages.add(box);
			rowH = Math.max(rowH, box.h());
			if (++col >= l.columns) {
				col = 0;
				y += rowH + GAP;
				rowH = 0;
			}
		}
		l.contentH = y + rowH;
		return l;
	}

	private static float maxScroll(Layout l) {
		return Math.max(0, l.contentH - l.viewH);
	}

	/** Runs every frame before the screen draws: scrolling and moving the real slots into place. */
	public static void beforeRender(AbstractContainerScreen<?> screen) {
		if (!active(screen)) return;
		Layout l = layout(screen);
		int active = activePage(screen);
		if (active != lastActive) {
			lastActive = active;
			if (active >= 0) scrollToPage = active;
		}
		if (scrollToPage >= 0) {
			for (PageBox box : l.pages) {
				if (box.index() == scrollToPage) {
					scrollTarget = Math.clamp(box.y() - (l.viewH - box.h()) / 2f, 0, maxScroll(l));
					break;
				}
			}
			scrollToPage = -1;
		}
		scrollTarget = Math.clamp(scrollTarget, 0, maxScroll(l));
		scroll += (scrollTarget - scroll) * 0.35f;
		if (Math.abs(scrollTarget - scroll) < 0.5f) scroll = scrollTarget;
		if (search != null) search.setPosition(l.searchX, l.searchY);

		ContainerScreenAccessor acc = (ContainerScreenAccessor) screen;
		int left = acc.fancybars$getLeftPos();
		int top = acc.fancybars$getTopPos();
		PageBox activeBox = null;
		for (PageBox box : l.pages) if (box.index() == active) activeBox = box;
		int contentIndex = 0;
		for (Slot slot : screen.getMenu().slots) {
			int ax = OFFSCREEN;
			int ay = OFFSCREEN;
			if (slot.container instanceof Inventory) {
				int i = slot.getContainerSlot();
				if (i >= 0 && i < 9) {
					ax = l.playerX + i * SLOT + 1;
					ay = l.playerY + 3 * SLOT + 4 + 1;
				} else if (i < 36) {
					ax = l.playerX + (i - 9) % 9 * SLOT + 1;
					ay = l.playerY + (i - 9) / 9 * SLOT + 1;
				}
			} else {
				int c = contentIndex++;
				// row 0 of every page menu is Hypixel's navigation bar: the overlay replaces it
				if (activeBox != null && c >= 9) {
					int px = activeBox.x() + 4 + (c - 9) % 9 * SLOT + 1;
					int py = l.viewY + activeBox.y() - Math.round(scroll) + TITLE_H + (c - 9) / 9 * SLOT + 1;
					// only while the slot is inside the scrolling area
					if (py >= l.viewY && py + 16 <= l.viewY + l.viewH) {
						ax = px;
						ay = py;
					}
				}
			}
			slot.x = ax == OFFSCREEN ? OFFSCREEN : ax - left;
			slot.y = ay == OFFSCREEN ? OFFSCREEN : ay - top;
		}
	}

	// ---------------------------------------------------------------- search

	/** Search results per item, worked out once per search instead of every frame. */
	private static final java.util.Map<ItemStack, Boolean> MATCHES = new java.util.IdentityHashMap<>();
	private static final java.util.Map<Integer, Boolean> PAGE_MATCHES = new java.util.HashMap<>();
	private static String matchesFor = "";
	/** Protection stars per item (checking means reading the item's data). */
	private static final java.util.Map<ItemStack, Boolean> PROTECTED = new java.util.IdentityHashMap<>();

	private static void checkQuery() {
		if (!query.equals(matchesFor)) {
			matchesFor = query;
			MATCHES.clear();
			PAGE_MATCHES.clear();
		}
		if (MATCHES.size() > 4000) MATCHES.clear();
		if (PROTECTED.size() > 4000) PROTECTED.clear();
	}

	private static boolean isProtected(ItemStack stack) {
		return PROTECTED.computeIfAbsent(stack, ItemProtection::isItemProtected);
	}

	private static boolean matches(ItemStack stack) {
		if (query.isEmpty()) return true;
		if (stack.isEmpty()) return false;
		checkQuery();
		return MATCHES.computeIfAbsent(stack, StorageOverlay::computeMatches);
	}

	private static boolean computeMatches(ItemStack stack) {
		String q = query.toLowerCase(Locale.ROOT);
		String name = ChatFormatting.stripFormatting(stack.getHoverName().getString());
		if (name != null && name.toLowerCase(Locale.ROOT).contains(q)) return true;
		ItemLore lore = stack.get(DataComponents.LORE);
		if (lore != null) {
			for (Component line : lore.lines()) {
				String s = ChatFormatting.stripFormatting(line.getString());
				if (s != null && s.toLowerCase(Locale.ROOT).contains(q)) return true;
			}
		}
		return false;
	}

	private static boolean matchesPage(int index) {
		if (query.isEmpty()) return true;
		checkQuery();
		return PAGE_MATCHES.computeIfAbsent(index, i -> {
			BackpackPreview.Storage s = BackpackPreview.storage(i);
			if (s == null) return false;
			for (int j = 9; j < s.size(); j++) if (matches(s.getStack(j))) return true;
			return false;
		});
	}

	private static Component pageTitle(int index) {
		if (index < 9) return Component.translatable("fancybars.storage.enderChest", index + 1);
		return Component.translatable("fancybars.storage.backpack", index - 8);
	}

	// ---------------------------------------------------------------- drawing

	/** Replaces the chest background: the panel, every page and the player inventory frame. */
	public static void renderBackground(AbstractContainerScreen<?> screen, GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		Layout l = layout(screen);
		Font font = Minecraft.getInstance().font;
		int active = activePage(screen);
		graphics.fill(0, 0, screen.width, screen.height, Math.round(Math.clamp(FancyBarsConfig.get().backdropDarkness, 0, 1) * 255) << 24);
		Theme.panel(graphics, l.panelX, l.panelY, l.panelW, l.panelH);

		graphics.enableScissor(l.viewX - 2, l.viewY, l.viewX + l.viewW + 2, l.viewY + l.viewH);
		int sc = Math.round(scroll);
		ItemStack hovered = null;
		for (PageBox box : l.pages) {
			int x = box.x();
			int y = l.viewY + box.y() - sc;
			if (y + box.h() < l.viewY || y > l.viewY + l.viewH) continue;
			boolean isActive = box.index() == active;
			boolean overPage = !isActive && mouseX >= x && mouseX < x + PAGE_W && mouseY >= y && mouseY < y + box.h()
					&& mouseY >= l.viewY && mouseY < l.viewY + l.viewH;
			Theme.roundedRect(graphics, x, y, PAGE_W, box.h(), 4, isActive ? Theme.SELECTED : overPage ? Theme.SURFACE_HOVER : Theme.SURFACE);
			if (isActive) Theme.roundedOutline(graphics, x, y, PAGE_W, box.h(), 4, Theme.ACCENT);
			Theme.textInBox(graphics, font, pageTitle(box.index()), x + 5, y + 1, TITLE_H, isActive ? Theme.ACCENT_LIGHT : Theme.TEXT_SOFT);
			BackpackPreview.Storage s = BackpackPreview.storage(box.index());
			for (int r = 0; r < box.rows(); r++) {
				for (int c = 0; c < 9; c++) {
					int sx = x + 4 + c * SLOT;
					int sy = y + TITLE_H + r * SLOT;
					graphics.fill(sx + 1, sy + 1, sx + SLOT - 1, sy + SLOT - 1, 0x55000000);
					if (isActive || s == null) continue;
					int i = 9 + r * 9 + c;
					if (i >= s.size()) continue;
					ItemStack stack = s.getStack(i);
					if (stack.isEmpty()) continue;
					graphics.item(stack, sx + 1, sy + 1);
					if (stack.getCount() > 1 || stack.isBarVisible()) graphics.itemDecorations(font, stack, sx + 1, sy + 1);
					if (isProtected(stack)) ItemProtection.drawStar(graphics, sx + 1, sy + 1);
					if (!matches(stack)) graphics.fill(sx + 1, sy + 1, sx + 17, sy + 17, 0xB0101014);
					if (mouseX >= sx && mouseX < sx + SLOT && mouseY >= sy && mouseY < sy + SLOT && mouseY >= l.viewY && mouseY < l.viewY + l.viewH) {
						hovered = stack;
						graphics.fill(sx + 1, sy + 1, sx + 17, sy + 17, 0x40FFFFFF);
					}
				}
			}
			if (overPage) graphics.requestCursor(CursorTypes.POINTING_HAND);
		}
		if (l.pages.isEmpty()) {
			Theme.textInBox(graphics, font, Component.translatable(query.isEmpty() ? "fancybars.storage.empty" : "fancybars.storage.noMatches"),
					l.viewX, l.viewY + 4, 14, Theme.TEXT_MUTED);
		}
		graphics.disableScissor();

		// scrollbar
		float max = maxScroll(l);
		if (max > 0) {
			int trackX = l.panelX + l.panelW - PAD - 3;
			int thumbH = Math.max(16, Math.round(l.viewH * l.viewH / (float) l.contentH));
			int thumbY = l.viewY + Math.round((l.viewH - thumbH) * (scroll / max));
			Theme.roundedRect(graphics, trackX, l.viewY, 3, l.viewH, 1, 0x30FFFFFF);
			Theme.roundedRect(graphics, trackX, thumbY, 3, thumbH, 1, Theme.TEXT_MUTED);
		}

		// dim the live page's items that don't match the search
		if (!query.isEmpty() && active >= 0) {
			ContainerScreenAccessor acc = (ContainerScreenAccessor) screen;
			for (Slot slot : screen.getMenu().slots) {
				if (slot.container instanceof Inventory || slot.x == OFFSCREEN || matches(slot.getItem())) continue;
				int sx = acc.fancybars$getLeftPos() + slot.x;
				int sy = acc.fancybars$getTopPos() + slot.y;
				graphics.nextStratum();
				graphics.fill(sx, sy, sx + 16, sy + 16, 0xB0101014);
			}
		}

		// player inventory frame
		Theme.panel(graphics, l.playerX - 6, l.playerY - 6, 9 * SLOT + 12, PLAYER_H + 12);
		for (int r = 0; r < 4; r++) {
			for (int c = 0; c < 9; c++) {
				int sx = l.playerX + c * SLOT;
				int sy = l.playerY + r * SLOT + (r == 3 ? 4 : 0);
				graphics.fill(sx + 1, sy + 1, sx + SLOT - 1, sy + SLOT - 1, 0x55000000);
			}
		}
		if (hovered != null) graphics.setTooltipForNextFrame(font, hovered, mouseX, mouseY);
	}

	// ---------------------------------------------------------------- input

	/** Clicks on the overlay itself (other pages); returns true when handled. */
	public static boolean mouseClicked(AbstractContainerScreen<?> screen, MouseButtonEvent click) {
		if (!active(screen)) return false;
		Layout l = layout(screen);
		double mx = click.x();
		double my = click.y();
		if (my < l.viewY || my >= l.viewY + l.viewH || mx < l.panelX || mx >= l.panelX + l.panelW) return false;
		int active = activePage(screen);
		int sc = Math.round(scroll);
		for (PageBox box : l.pages) {
			int y = l.viewY + box.y() - sc;
			if (box.index() == active) continue;
			if (mx >= box.x() && mx < box.x() + PAGE_W && my >= y && my < y + box.h()) {
				openPage(screen, box.index());
				return true;
			}
		}
		// the open page is the real menu: let Minecraft handle it; empty panel space just swallows the click
		return !insideActive(l, active, mx, my, sc);
	}

	private static boolean insideActive(Layout l, int active, double mx, double my, int sc) {
		for (PageBox box : l.pages) {
			if (box.index() != active) continue;
			int y = l.viewY + box.y() - sc;
			return mx >= box.x() && mx < box.x() + PAGE_W && my >= y && my < y + box.h();
		}
		return false;
	}

	/** Opens ender chest page / backpack {@code index}: through the Storage menu if it's open, else by command. */
	private static void openPage(AbstractContainerScreen<?> screen, int index) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || mc.getConnection() == null) return;
		scrollToPage = index;
		if (activePage(screen) < 0 && mc.gameMode != null) {
			int slot = index < 9 ? 9 + index : 27 + (index - 9);
			if (slot < screen.getMenu().slots.size()) {
				mc.gameMode.handleContainerInput(screen.getMenu().containerId, slot, 0, ContainerInput.PICKUP, mc.player);
				return;
			}
		}
		mc.getConnection().sendCommand(index < 9 ? "ec " + (index + 1) : "backpack " + (index - 8));
	}

	public static boolean mouseScrolled(AbstractContainerScreen<?> screen, double mouseX, double mouseY, double vertical) {
		if (!active(screen) || vertical == 0) return false;
		Layout l = layout(screen);
		if (mouseY > l.panelY + l.panelH) return false;
		scrollTarget = Math.clamp(scrollTarget - (float) vertical * 24, 0, maxScroll(l));
		return true;
	}

	/** While typing in the search box, keys go to it instead of closing the menu / moving items. */
	public static boolean keyPressed(AbstractContainerScreen<?> screen, KeyEvent key) {
		if (!active(screen) || search == null || !search.isFocused()) return false;
		if (key.isEscape()) {
			search.setFocused(false);
			screen.setFocused(null);
			return true;
		}
		search.keyPressed(key);
		return true;
	}

	/** Clicking outside the panel and inventory still drops the held item, like normal. */
	public static boolean isOutside(AbstractContainerScreen<?> screen, double mx, double my) {
		Layout l = layout(screen);
		boolean inPanel = mx >= l.panelX && mx < l.panelX + l.panelW && my >= l.panelY && my < l.panelY + l.panelH;
		boolean inSearch = my >= l.searchY && my < l.searchY + SEARCH_H && mx >= l.playerX - 6 && mx < l.playerX + 9 * SLOT + 6;
		boolean inPlayer = mx >= l.playerX - 6 && mx < l.playerX + 9 * SLOT + 6 && my >= l.playerY - 6 && my < l.playerY + PLAYER_H + 6;
		return !inPanel && !inSearch && !inPlayer;
	}
}
