package dev.fancybars.items;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

import org.jspecify.annotations.Nullable;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;

import dev.fancybars.config.FancyBarsConfig;
import dev.fancybars.mixin.ContainerScreenAccessor;
import dev.fancybars.util.SkyblockLocation;
import dev.fancybars.util.gui.Theme;

/**
 * Chest Value (from SkyHanni): next to a chest, backpack, ender chest page or minion, a list of what's inside and what
 * it's worth, most valuable first, with the total on top. Hover a line to highlight those items in the chest.
 */
public final class ChestValue {
	private static final Pattern RELEVANT = Pattern.compile("Personal Vault|Chest Storage|Wood Chest\\+?|Chest|Large Chest");
	private static final int PAD = 5;
	private static final int LINE = 10;

	private record Row(String name, int count, double value, List<Slot> slots) {}

	private static List<Row> rows = List.of();
	private static double total;
	private static long builtAt;
	private static @Nullable AbstractContainerScreen<?> builtFor;

	private ChestValue() {}

	private static boolean relevant(String title) {
		if (title.contains("Backpack") && title.contains("Slot #")) return true;
		if (title.startsWith("Ender Chest (")) return true;
		if (title.contains("Minion") && !title.contains("Recipe")) return true;
		return RELEVANT.matcher(title).matches();
	}

	private static boolean shown(AbstractContainerScreen<?> screen) {
		FancyBarsConfig c = FancyBarsConfig.get();
		if (!c.chestValue || !SkyblockLocation.isOnSkyblock()) return false;
		if (SkyblockLocation.isInDungeons() && !c.chestValueInDungeons) return false;
		if (dev.fancybars.storage.StorageOverlay.active(screen)) return false;
		if (screen instanceof InventoryScreen) return c.chestValueOwnInventory;
		return screen instanceof ContainerScreen && relevant(screen.getTitle().getString());
	}

	private static boolean soulbound(ItemStack stack) {
		ItemLore lore = stack.get(DataComponents.LORE);
		if (lore == null) return false;
		for (Component line : lore.lines()) if (line.getString().contains("Soulbound")) return true;
		return false;
	}

	private static void build(AbstractContainerScreen<?> screen) {
		FancyBarsConfig c = FancyBarsConfig.get();
		ItemValue.ensureLoaded();
		boolean own = screen instanceof InventoryScreen;
		Map<String, Row> grouped = new LinkedHashMap<>();
		for (Slot slot : screen.getMenu().slots) {
			// the chest's own items (or, in your inventory, your main inventory + hotbar)
			if (own ? !(slot.container instanceof Inventory) || slot.getContainerSlot() >= 36 : slot.container instanceof Inventory) continue;
			ItemStack stack = slot.getItem();
			if (stack.isEmpty()) continue;
			if (c.chestValueIgnoreSoulbound && soulbound(stack)) continue;
			double value = ItemValue.stackValue(stack);
			if (value <= 0) continue;
			String name = stack.getHoverName().getString();
			String key = dev.fancybars.items.list.ItemRepo.idOf(stack) + "|" + name;
			Row old = grouped.get(key);
			List<Slot> slots = old == null ? new ArrayList<>() : new ArrayList<>(old.slots());
			slots.add(slot);
			grouped.put(key, new Row(name, (old == null ? 0 : old.count()) + stack.getCount(), (old == null ? 0 : old.value()) + value, slots));
		}
		List<Row> list = new ArrayList<>(grouped.values());
		total = 0;
		for (Row r : list) total += r.value();
		list.removeIf(r -> r.value() < c.chestValueHideBelow);
		list.sort((a, b) -> c.chestValueAscending ? Double.compare(a.value(), b.value()) : Double.compare(b.value(), a.value()));
		if (list.size() > c.chestValueItems) list = new ArrayList<>(list.subList(0, Math.max(1, c.chestValueItems)));
		rows = list;
	}

	private static String format(double v) {
		if (FancyBarsConfig.get().chestValueCompact) return dev.fancybars.commands.Calculator.shortForm(v);
		return String.format(Locale.ENGLISH, "%,.0f", v);
	}

	private static String clip(Font font, String s, int max) {
		if (font.width(s) <= max) return s;
		while (s.length() > 1 && font.width(s + "…") > max) s = s.substring(0, s.length() - 1);
		return s + "…";
	}

	public static void render(AbstractContainerScreen<?> screen, GuiGraphicsExtractor graphics, int mx, int my) {
		if (!shown(screen)) return;
		long now = System.currentTimeMillis();
		if (builtFor != screen || now - builtAt > 250) {
			build(screen);
			builtFor = screen;
			builtAt = now;
		}
		if (total <= 0) return;
		FancyBarsConfig c = FancyBarsConfig.get();
		Font font = Minecraft.getInstance().font;
		ContainerScreenAccessor acc = (ContainerScreenAccessor) screen;
		int left = acc.fancybars$getLeftPos();
		int top = acc.fancybars$getTopPos();

		String title = (screen instanceof InventoryScreen ? "Inventory Value: " : "Chest Value: ");
		List<String[]> lines = new ArrayList<>(); // {left text, right text}
		for (Row r : rows) lines.add(new String[]{(r.count() > 1 ? "§7" + r.count() + "x §r" : "") + r.name(), "§6" + format(r.value())});
		int maxName = 150;
		int nameW = 0, valueW = 0;
		for (String[] l : lines) {
			nameW = Math.max(nameW, font.width(clip(font, l[0], maxName)));
			valueW = Math.max(valueW, font.width(l[1]));
		}
		int titleW = font.width(title + format(total));
		int w = Math.max(titleW, nameW + 8 + valueW) + PAD * 2;
		int h = PAD * 2 + LINE + (lines.isEmpty() ? 0 : 3 + lines.size() * LINE);
		int x = Math.max(2, left - w - 6);
		int y = top;

		graphics.pose().pushMatrix();
		graphics.pose().identity();
		try {
			graphics.nextStratum();
			if (c.chestValueBackground) Theme.panel(graphics, x, y, w, h);
			graphics.text(font, Component.literal(title).withStyle(ChatFormatting.YELLOW).append(Component.literal(format(total)).withStyle(ChatFormatting.GOLD)), x + PAD, y + PAD, 0xFFFFFFFF, true);
			int ly = y + PAD + LINE + 3;
			Row hovered = null;
			for (int i = 0; i < lines.size(); i++) {
				String[] l = lines.get(i);
				boolean over = mx >= x && mx < x + w && my >= ly - 1 && my < ly + LINE - 1;
				if (over) {
					hovered = rows.get(i);
					graphics.fill(x + 2, ly - 1, x + w - 2, ly + LINE - 1, 0x30FFFFFF);
				}
				graphics.text(font, clip(font, l[0], maxName), x + PAD, ly, 0xFFFFFFFF, true);
				graphics.text(font, l[1], x + w - PAD - font.width(l[1]), ly, 0xFFFFFFFF, true);
				ly += LINE;
			}
			if (hovered != null && c.chestValueHighlight) {
				for (Slot slot : hovered.slots()) {
					int sx = left + slot.x, sy = top + slot.y;
					graphics.fill(sx, sy, sx + 16, sy + 16, 0x8055FF55);
				}
			}
		} finally {
			graphics.pose().popMatrix();
		}
	}
}
