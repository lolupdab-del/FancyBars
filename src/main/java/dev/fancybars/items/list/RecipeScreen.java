package dev.fancybars.items.list;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Locale;

import org.jspecify.annotations.Nullable;

import com.mojang.blaze3d.platform.cursor.CursorTypes;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import dev.fancybars.util.gui.ModernButton;
import dev.fancybars.util.gui.Theme;

/**
 * Recipes for an item, or what it's used in (Item List). Crafting, forge and NPC shop recipes; click any
 * ingredient to see its own recipes, Backspace / Back to go back.
 */
public class RecipeScreen extends Screen {
	private static final int SLOT = 18;
	private static final int W = 240;
	private static final int H = 170;

	private final @Nullable Screen parent;
	private String id;
	private boolean usages;
	private int index;
	private final Deque<String[]> history = new ArrayDeque<>();
	private int px, py;

	private RecipeScreen(@Nullable Screen parent, String id, boolean usages) {
		super(Component.translatable("fancybars.itemList.recipes"));
		this.parent = parent;
		this.id = id;
		this.usages = usages;
	}

	/** Opens recipes (or usages) for {@code id}. The inventory comes back when you close it; other menus close. */
	public static void open(@Nullable Screen from, String id, boolean usages) {
		Screen parent = from instanceof InventoryScreen ? from : null;
		Minecraft.getInstance().gui.setScreen(new RecipeScreen(parent, id, usages));
	}

	private List<ItemRepo.Recipe> list() {
		return usages ? ItemRepo.usagesOf(id) : ItemRepo.recipesFor(id);
	}

	@Override
	protected void init() {
		px = (width - W) / 2;
		py = (height - H) / 2;
		ModernButton recipes = new ModernButton(70, 16, Component.translatable("fancybars.itemList.tabRecipes"), usages ? ModernButton.Style.SECONDARY : ModernButton.Style.PRIMARY, () -> switchTo(false));
		recipes.setPosition(px + 8, py + 28);
		addRenderableWidget(recipes);
		ModernButton uses = new ModernButton(70, 16, Component.translatable("fancybars.itemList.tabUsages"), usages ? ModernButton.Style.PRIMARY : ModernButton.Style.SECONDARY, () -> switchTo(true));
		uses.setPosition(px + 82, py + 28);
		addRenderableWidget(uses);
		ModernButton prev = new ModernButton(18, 16, Component.literal("‹"), () -> step(-1));
		prev.setPosition(px + 8, py + H - 22);
		addRenderableWidget(prev);
		ModernButton next = new ModernButton(18, 16, Component.literal("›"), () -> step(1));
		next.setPosition(px + 30, py + H - 22);
		addRenderableWidget(next);
		if (!history.isEmpty()) {
			ModernButton back = new ModernButton(50, 16, Component.translatable("fancybars.itemList.back"), this::back);
			back.setPosition(px + W - 8 - 50, py + H - 22);
			addRenderableWidget(back);
		}
	}

	private void switchTo(boolean u) {
		usages = u;
		index = 0;
		rebuildWidgets();
	}

	private void step(int d) {
		int n = list().size();
		if (n > 0) index = Math.floorMod(index + d, n);
	}

	private void go(String newId) {
		if (newId.isEmpty() || newId.equals(id) || ItemRepo.get(newId) == null) return;
		history.push(new String[]{id, usages ? "1" : "0", String.valueOf(index)});
		id = newId;
		usages = false;
		index = 0;
		rebuildWidgets();
	}

	private void back() {
		if (history.isEmpty()) return;
		String[] h = history.pop();
		id = h[0];
		usages = h[1].equals("1");
		index = Integer.parseInt(h[2]);
		rebuildWidgets();
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		Theme.backdrop(graphics, width, height);
	}

	/** Slot boxes drawn this frame: {x, y, ingredient index into a temp list}. */
	private ItemStack hovered;
	private String hoveredId;

	private void slot(GuiGraphicsExtractor graphics, ItemRepo.Ingredient in, int x, int y, int mouseX, int mouseY) {
		Theme.roundedRect(graphics, x, y, SLOT, SLOT, 3, 0x55000000);
		if (in == null || in.id().isEmpty()) return;
		ItemStack stack = ItemRepo.stackFor(in);
		graphics.fakeItem(stack, x + 1, y + 1);
		if (in.count() > 1) {
			String count = in.count() > 999 ? (in.count() / 1000) + "k" : String.valueOf(in.count());
			graphics.nextStratum();
			graphics.text(font, count, x + 17 - font.width(count), y + 9, 0xFFFFFFFF, true);
		}
		if (mouseX >= x && mouseX < x + SLOT && mouseY >= y && mouseY < y + SLOT) {
			hovered = stack;
			hoveredId = in.id();
		}
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		hovered = null;
		hoveredId = null;
		Theme.panel(graphics, px, py, W, H);
		ItemRepo.Entry entry = ItemRepo.get(id);
		if (entry != null) {
			graphics.fakeItem(entry.stack(), px + 8, py + 6);
			// the name gets the whole width (tabs are on the row below)
			Component name = Theme.bold(Component.literal(entry.name()));
			int room = W - 28 - 8;
			if (font.width(name) > room) {
				String n = entry.name();
				while (n.length() > 1 && font.width(Theme.bold(Component.literal(n + "…"))) > room) n = n.substring(0, n.length() - 1);
				name = Theme.bold(Component.literal(n + "…"));
			}
			Theme.textInBox(graphics, font, name, px + 28, py + 6, 16, Theme.TEXT);
		}
		List<ItemRepo.Recipe> list = list();
		if (list.isEmpty()) {
			Theme.textInBox(graphics, font, Component.translatable(usages ? "fancybars.itemList.noUsages" : "fancybars.itemList.noRecipes"), px + 10, py + 62, 14, Theme.TEXT_MUTED);
		} else {
			index = Math.clamp(index, 0, list.size() - 1);
			ItemRepo.Recipe r = list.get(index);
			int top = py + 52;
			String kind = switch (r.type()) {
				case "forge" -> "Forge" + (r.seconds() > 0 ? "  ·  " + duration(r.seconds()) : "");
				case "shop" -> "Sold by " + ItemRepo.prettyNpc(r.npc()) + (r.coins() > 0 ? "  ·  " + String.format(Locale.ROOT, "%,d coins", r.coins()) : "");
				default -> "Crafting Table";
			};
			Theme.textInBox(graphics, font, Component.literal(kind), px + 10, top - 2, 12, Theme.TEXT_SOFT);
			int gy = top + 14;
			if (r.type().equals("crafting")) {
				for (int i = 0; i < 9; i++) slot(graphics, i < r.grid().size() ? r.grid().get(i) : null, px + 20 + i % 3 * SLOT, gy + i / 3 * SLOT, mouseX, mouseY);
			} else {
				List<ItemRepo.Ingredient> ins = r.inputs();
				for (int i = 0; i < ins.size() && i < 12; i++) slot(graphics, ins.get(i), px + 20 + i % 4 * SLOT, gy + i / 4 * SLOT, mouseX, mouseY);
			}
			Theme.centeredInBox(graphics, font, Component.literal("→"), px + 120, gy + SLOT, 18, Theme.TEXT_MUTED);
			slot(graphics, r.result(), px + 140, gy + SLOT, mouseX, mouseY);
			Theme.textInBox(graphics, font, Component.literal((index + 1) + " / " + list.size()), px + 54, py + H - 22, 16, Theme.TEXT_MUTED);
		}
		super.extractRenderState(graphics, mouseX, mouseY, a);
		if (hovered != null) {
			graphics.setTooltipForNextFrame(font, ItemList.tooltip(hovered), java.util.Optional.empty(), mouseX, mouseY);
			graphics.requestCursor(CursorTypes.POINTING_HAND);
		}
	}

	private static String clip(String s, int max) {
		return s.length() > 32 ? s.substring(0, 31) + "…" : s;
	}

	private static String duration(int seconds) {
		if (seconds >= 3600) return (seconds / 3600) + "h" + (seconds % 3600 >= 60 ? " " + (seconds % 3600 / 60) + "m" : "");
		if (seconds >= 60) return (seconds / 60) + "m";
		return seconds + "s";
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
		if (super.mouseClicked(click, doubled)) return true;
		if (hoveredId != null) {
			if (click.button() == 1) {
				history.push(new String[]{id, usages ? "1" : "0", String.valueOf(index)});
				id = hoveredId;
				usages = true;
				index = 0;
				rebuildWidgets();
			} else go(hoveredId);
			return true;
		}
		return false;
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
		if (vertical != 0) step(vertical < 0 ? 1 : -1);
		return true;
	}

	@Override
	public boolean keyPressed(KeyEvent key) {
		if (key.key() == 259 && !history.isEmpty()) { // backspace
			back();
			return true;
		}
		return super.keyPressed(key);
	}

	@Override
	public void onClose() {
		Minecraft mc = Minecraft.getInstance();
		if (parent != null && mc.player != null) mc.gui.setScreen(new InventoryScreen(mc.player));
		else mc.gui.setScreen(null);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
