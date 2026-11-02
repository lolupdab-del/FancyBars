package dev.fancybars.items;

import java.util.Locale;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;

import dev.fancybars.config.FancyBarsConfig;
import dev.fancybars.util.SkyblockLocation;
import dev.fancybars.util.gui.Theme;

/**
 * Item Rarity Background: a coloured background behind SkyBlock items in their rarity's colour, read from the
 * rarity line at the bottom of the lore ("LEGENDARY SWORD").
 */
public final class RarityBackground {
	private static final Pattern RARITY = Pattern.compile("(?:^|\\s)(VERY SPECIAL|UNCOMMON|COMMON|RARE|EPIC|LEGENDARY|MYTHIC|DIVINE|SPECIAL|ULTIMATE|ADMIN)(?:\\s|$)");
	private static final Map<String, Integer> COLORS = Map.ofEntries(
			Map.entry("COMMON", 0xFFFFFF), Map.entry("UNCOMMON", 0x55FF55), Map.entry("RARE", 0x5555FF),
			Map.entry("EPIC", 0xAA00AA), Map.entry("LEGENDARY", 0xFFAA00), Map.entry("MYTHIC", 0xFF55FF),
			Map.entry("DIVINE", 0x55FFFF), Map.entry("SPECIAL", 0xFF5555), Map.entry("VERY SPECIAL", 0xFF5555),
			Map.entry("ULTIMATE", 0xAA0000), Map.entry("ADMIN", 0xAA0000));
	/** Stack -> rarity colour (-1 none). ItemStacks compare by identity, so this is per stack and lets go with it. */
	private static final Map<ItemStack, Integer> CACHE = new WeakHashMap<>();

	private RarityBackground() {}

	/** The item's rarity colour (RGB), or -1 if it has no rarity line. */
	public static int colorOf(ItemStack stack) {
		if (stack.isEmpty()) return -1;
		Integer cached = CACHE.get(stack);
		if (cached != null) return cached;
		int color = -1;
		ItemLore lore = stack.get(DataComponents.LORE);
		if (lore != null) {
			var lines = lore.lines();
			for (int i = lines.size() - 1; i >= 0; i--) {
				String line = ChatFormatting.stripFormatting(lines.get(i).getString());
				if (line == null || line.isBlank()) continue;
				Matcher m = RARITY.matcher(line.trim().toUpperCase(Locale.ROOT));
				if (m.find()) color = COLORS.get(m.group(1));
				break;
			}
		}
		if (CACHE.size() > 4000) CACHE.clear();
		CACHE.put(stack, color);
		return color;
	}

	/** Draws the background behind the 16x16 item at x, y if it's turned on and the item has a rarity. */
	public static void draw(GuiGraphicsExtractor graphics, ItemStack stack, int x, int y, boolean hotbar) {
		FancyBarsConfig c = FancyBarsConfig.get();
		if (!c.rarityBackground || hotbar && !c.rarityBackgroundHotbar || !SkyblockLocation.isOnSkyblock()) return;
		int rgb = colorOf(stack);
		if (rgb == -1) return;
		int alpha = Math.round(Math.clamp(c.rarityBackgroundOpacity, 0f, 1f) * 255);
		int color = alpha << 24 | rgb;
		switch (c.rarityBackgroundStyle) {
			case 1 -> Theme.roundedRect(graphics, x, y, 16, 16, 3, color);
			case 2 -> {
				graphics.fill(x, y, x + 16, y + 1, color);
				graphics.fill(x, y + 15, x + 16, y + 16, color);
				graphics.fill(x, y + 1, x + 1, y + 15, color);
				graphics.fill(x + 15, y + 1, x + 16, y + 15, color);
			}
			case 3 -> {
				// fades from the colour at the bottom to clear at the top
				graphics.fillGradient(x, y, x + 16, y + 16, rgb, color);
			}
			default -> graphics.fill(x, y, x + 16, y + 16, color);
		}
	}

	public static Component styleName(int style) {
		return Component.translatable("fancybars.rarityBackground.style." + style);
	}
}
