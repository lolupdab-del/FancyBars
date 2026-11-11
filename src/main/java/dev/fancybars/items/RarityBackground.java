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

/**
 * Item Rarity Background: a coloured background behind SkyBlock items in their rarity's colour, read from the
 * rarity line at the bottom of the lore ("LEGENDARY SWORD").
 */
public final class RarityBackground {
	/**
	 * A rarity line, e.g. "LEGENDARY DUNGEON SWORD", "SHINY EPIC", or "a MYTHIC SWORD a" when recombobulated (the a's are
	 * the obfuscated marks). Case-sensitive and the whole line, so description text like "RARE REWARD!" doesn't count.
	 */
	private static final Pattern RARITY = Pattern.compile("^(?:[a-z] )?(?:SHINY )?(VERY SPECIAL|UNCOMMON|COMMON|RARE|EPIC|LEGENDARY|MYTHIC|DIVINE|SPECIAL|ULTIMATE|ADMIN)(?: [A-Z][A-Z ]*)?(?: [a-z])?$");
	/** Pets have no rarity line: their rarity is the colour of the name after "[Lvl N]". */
	private static final Pattern PET_NAME = Pattern.compile("^[^\\[]*\\[Lvl \\d+] .+");
	private static final int[] LEGACY = {0x000000, 0x0000AA, 0x00AA00, 0x00AAAA, 0xAA0000, 0xAA00AA, 0xFFAA00, 0xAAAAAA,
			0x555555, 0x5555FF, 0x55FF55, 0x55FFFF, 0xFF5555, 0xFF55FF, 0xFFFF55, 0xFFFFFF};
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
			// menus (Auction House, Bazaar, shops, ...) add lines below the rarity line, so search up from the bottom
			var lines = lore.lines();
			for (int i = lines.size() - 1; i >= 0; i--) {
				String line = ChatFormatting.stripFormatting(lines.get(i).getString());
				if (line == null || line.isBlank()) continue;
				Matcher m = RARITY.matcher(line.trim());
				if (m.matches()) {
					color = COLORS.get(m.group(1));
					break;
				}
			}
		}
		if (color == -1) color = petColor(stack.getHoverName());
		if (CACHE.size() > 4000) CACHE.clear();
		CACHE.put(stack, color);
		return color;
	}

	/** For "[Lvl 100] Golden Dragon" style names: the colour the pet's name is written in, else -1. */
	private static int petColor(Component name) {
		String raw = name.getString();
		String plain = ChatFormatting.stripFormatting(raw);
		if (plain == null || !PET_NAME.matcher(plain.trim()).matches()) return -1;
		int after = raw.indexOf("] ");
		// colour codes written into the text
		int code = raw.indexOf('§', after);
		if (code >= 0 && code + 1 < raw.length()) {
			int idx = "0123456789abcdef".indexOf(Character.toLowerCase(raw.charAt(code + 1)));
			if (idx >= 0) return LEGACY[idx];
		}
		// styled parts: the first coloured part after the grey "[Lvl N]"
		int[] found = {-1};
		boolean[] pastLevel = {false};
		name.visit((net.minecraft.network.chat.Style style, String text) -> {
			if (found[0] != -1 || text.isBlank()) return java.util.Optional.empty();
			if (!pastLevel[0]) {
				int close = text.indexOf(']');
				if (close < 0) return java.util.Optional.empty();
				pastLevel[0] = true;
				if (text.substring(close + 1).isBlank()) return java.util.Optional.empty();
			}
			var color = style.getColor();
			if (color != null && (color.getValue() & 0xFFFFFF) != 0xAAAAAA) found[0] = color.getValue() & 0xFFFFFF;
			return java.util.Optional.empty();
		}, net.minecraft.network.chat.Style.EMPTY);
		return found[0];
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
			// drawn directly: the theme's Corner Roundness and colour mapping shouldn't change rarity backgrounds
			case 1 -> dev.fancybars.util.gui.Ring.fillRounded(graphics, x, y, 16, 16, 3, color);
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
			case 4 -> dev.fancybars.util.gui.SmoothShapes.draw(graphics, dev.fancybars.util.gui.SmoothShapes.Shape.CIRCLE, x, y, 16, color);
			default -> graphics.fill(x, y, x + 16, y + 16, color);
		}
	}

	public static Component styleName(int style) {
		return Component.translatable("fancybars.rarityBackground.style." + style);
	}
}
