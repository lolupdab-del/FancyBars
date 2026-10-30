package dev.fancybars.items;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.jspecify.annotations.Nullable;

import com.mojang.blaze3d.platform.cursor.CursorTypes;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import dev.fancybars.config.FancyBarsConfig;
import dev.fancybars.protection.ItemProtection;
import dev.fancybars.util.gui.ModernButton;
import dev.fancybars.util.gui.SearchField;
import dev.fancybars.util.gui.Theme;

/** Pick one of your items on the left, then the item it should look like on the right. */
public class ItemChangerScreen extends Screen {
	private static final int SLOT = 18;
	private static final int LEFT_COLS = 9;
	private static final int RIGHT_COLS = 9;
	private static final int[] SLOT_ORDER = buildOrder();
	private static @Nullable List<Entry> allItems;

	private record Entry(Item item, ItemStack stack, String search) {}

	private final @Nullable Screen parent;
	private String query = "";
	private final List<Entry> filtered = new ArrayList<>();
	private int selectedSlot = -1;
	private float scroll;
	private int px;
	private int py;
	private int pw;
	private int ph;
	private int leftX;
	private int gridY;
	private int rightX;
	private int rightRows;

	public ItemChangerScreen(@Nullable Screen parent) {
		super(Component.translatable("fancybars.items.title"));
		this.parent = parent;
	}

	private static int[] buildOrder() {
		int[] order = new int[41];
		int i = 0;
		for (int s = 9; s < 36; s++) order[i++] = s; // main inventory
		for (int s = 0; s < 9; s++) order[i++] = s; // hotbar
		for (int s = 39; s >= 36; s--) order[i++] = s; // armour, helmet first
		order[i] = 40; // offhand
		return order;
	}

	private static List<Entry> items() {
		if (allItems == null) {
			List<Entry> list = new ArrayList<>();
			for (Item item : BuiltInRegistries.ITEM) {
				if (item == Items.AIR) continue;
				ItemStack stack = new ItemStack(item);
				list.add(new Entry(item, stack, (stack.getHoverName().getString() + " " + BuiltInRegistries.ITEM.getKey(item)).toLowerCase(Locale.ROOT)));
			}
			allItems = list;
		}
		return allItems;
	}

	private void refilter() {
		filtered.clear();
		String q = query.trim().toLowerCase(Locale.ROOT);
		for (Entry entry : items()) if (q.isEmpty() || entry.search().contains(q)) filtered.add(entry);
		scroll = 0;
	}

	@Override
	protected void init() {
		pw = Math.min(width - 16, LEFT_COLS * SLOT + RIGHT_COLS * SLOT + 48);
		ph = Math.min(height - 16, 236);
		px = (width - pw) / 2;
		py = (height - ph) / 2;
		leftX = px + 12;
		gridY = py + 44;
		rightX = px + pw - 12 - RIGHT_COLS * SLOT;
		rightRows = Math.max(1, (py + ph - 40 - gridY) / SLOT);

		SearchField search = new SearchField(RIGHT_COLS * SLOT, 16, query, Component.translatable("fancybars.items.search"), text -> {
			query = text;
			refilter();
		});
		search.setPosition(rightX, py + 24);
		addRenderableWidget(search);

		ModernButton reset = new ModernButton(70, 18, Component.translatable("fancybars.items.reset"), () -> {
			String uuid = selectedUuid();
			if (!uuid.isEmpty()) ItemChanger.reset(uuid);
		});
		reset.setPosition(px + pw / 2 - 74, py + ph - 26);
		addRenderableWidget(reset);
		ModernButton done = new ModernButton(70, 18, Component.translatable("gui.done"), ModernButton.Style.PRIMARY, this::onClose);
		done.setPosition(px + pw / 2 + 4, py + ph - 26);
		addRenderableWidget(done);
		refilter();
	}

	private ItemStack slotStack(int slot) {
		return minecraft.player == null ? ItemStack.EMPTY : minecraft.player.getInventory().getItem(slot);
	}

	private String selectedUuid() {
		return selectedSlot < 0 ? "" : ItemProtection.getUuid(slotStack(selectedSlot));
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		Theme.backdrop(graphics, width, height);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		Theme.panel(graphics, px, py, pw, ph);
		Theme.titleInBox(graphics, font, Component.translatable("fancybars.items.title"), px + 12, py + 4, 18, Theme.TEXT);
		Theme.textInBox(graphics, font, Component.translatable("fancybars.items.yourItems"), leftX, py + 24, 16, Theme.TEXT_MUTED);

		// Your items
		for (int i = 0; i < SLOT_ORDER.length; i++) {
			int slot = SLOT_ORDER[i];
			int sx = leftX + (i % LEFT_COLS) * SLOT;
			int sy = gridY + (i / LEFT_COLS) * SLOT + (i >= 36 ? 4 : i >= 27 ? 2 : 0);
			ItemStack stack = slotStack(slot);
			String uuid = ItemProtection.getUuid(stack);
			boolean usable = !uuid.isEmpty();
			boolean selected = slot == selectedSlot;
			boolean hovered = mouseX >= sx && mouseX < sx + SLOT && mouseY >= sy && mouseY < sy + SLOT;
			Theme.roundedRect(graphics, sx + 1, sy + 1, SLOT - 2, SLOT - 2, 3,
					selected ? Theme.SELECTED : hovered && usable ? Theme.SURFACE_HOVER : usable ? Theme.SURFACE : Theme.SURFACE_DISABLED);
			if (selected) Theme.roundedOutline(graphics, sx, sy, SLOT, SLOT, 3, Theme.ACCENT);
			if (!stack.isEmpty()) {
				graphics.item(stack, sx + 1, sy + 1);
				if (usable && FancyBarsConfig.get().customItemModels.containsKey(uuid)) {
					graphics.nextStratum();
					Theme.circle(graphics, sx + SLOT - 6, sy + 1, 5, Theme.ACCENT);
				}
				if (hovered) {
					graphics.setTooltipForNextFrame(font, usable ? stack.getHoverName() : Component.translatable("fancybars.items.noUuid"), mouseX, mouseY);
					if (usable) graphics.requestCursor(CursorTypes.POINTING_HAND);
				}
			}
		}

		// Selected item info
		int infoY = gridY + 5 * SLOT + 12;
		ItemStack selected = selectedSlot >= 0 ? slotStack(selectedSlot) : ItemStack.EMPTY;
		Component info = selected.isEmpty() || ItemProtection.getUuid(selected).isEmpty()
				? Component.translatable("fancybars.items.pick")
				: Component.translatable("fancybars.items.selected", selected.getHoverName());
		Theme.textInBox(graphics, font, info, leftX, infoY, 14, Theme.TEXT_SOFT);

		// Every item in the game
		graphics.enableScissor(rightX - 2, gridY - 2, rightX + RIGHT_COLS * SLOT + 2, gridY + rightRows * SLOT + 2);
		int first = Math.round(scroll) * RIGHT_COLS;
		for (int i = first; i < Math.min(filtered.size(), first + rightRows * RIGHT_COLS); i++) {
			int idx = i - first;
			int sx = rightX + (idx % RIGHT_COLS) * SLOT;
			int sy = gridY + (idx / RIGHT_COLS) * SLOT;
			boolean hovered = mouseX >= sx && mouseX < sx + SLOT && mouseY >= sy && mouseY < sy + SLOT;
			Theme.roundedRect(graphics, sx + 1, sy + 1, SLOT - 2, SLOT - 2, 3, hovered ? Theme.SURFACE_HOVER : Theme.SURFACE);
			Entry entry = filtered.get(i);
			graphics.item(entry.stack(), sx + 1, sy + 1);
			if (hovered) {
				graphics.setTooltipForNextFrame(font, entry.stack().getHoverName(), mouseX, mouseY);
				graphics.requestCursor(CursorTypes.POINTING_HAND);
			}
		}
		graphics.disableScissor();
		int maxScroll = maxScroll();
		if (maxScroll > 0) {
			int trackH = rightRows * SLOT;
			int thumbH = Math.max(12, trackH * rightRows / (rightRows + maxScroll));
			int thumbY = gridY + Math.round((trackH - thumbH) * (scroll / maxScroll));
			Theme.roundedRect(graphics, rightX + RIGHT_COLS * SLOT + 3, thumbY, 3, thumbH, 1, Theme.ACCENT);
		}
		super.extractRenderState(graphics, mouseX, mouseY, a);
	}

	private int maxScroll() {
		int rows = (filtered.size() + RIGHT_COLS - 1) / RIGHT_COLS;
		return Math.max(0, rows - rightRows);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
		scroll = Math.clamp(scroll - (float) vertical, 0, maxScroll());
		return true;
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
		if (super.mouseClicked(click, doubled)) return true;
		double mx = click.x();
		double my = click.y();
		for (int i = 0; i < SLOT_ORDER.length; i++) {
			int sx = leftX + (i % LEFT_COLS) * SLOT;
			int sy = gridY + (i / LEFT_COLS) * SLOT + (i >= 36 ? 4 : i >= 27 ? 2 : 0);
			if (mx >= sx && mx < sx + SLOT && my >= sy && my < sy + SLOT) {
				if (!ItemProtection.getUuid(slotStack(SLOT_ORDER[i])).isEmpty()) selectedSlot = SLOT_ORDER[i];
				return true;
			}
		}
		if (mx >= rightX && mx < rightX + RIGHT_COLS * SLOT && my >= gridY && my < gridY + rightRows * SLOT) {
			int idx = Math.round(scroll) * RIGHT_COLS + (int) ((my - gridY) / SLOT) * RIGHT_COLS + (int) ((mx - rightX) / SLOT);
			String uuid = selectedUuid();
			if (idx < filtered.size() && !uuid.isEmpty()) {
				ItemChanger.set(uuid, filtered.get(idx).item());
				return true;
			}
		}
		return false;
	}

	@Override
	public void onClose() {
		minecraft.gui.setScreen(parent);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
