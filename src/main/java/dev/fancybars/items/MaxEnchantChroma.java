package dev.fancybars.items;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

import dev.fancybars.FancyBarsMod;
import dev.fancybars.config.FancyBarsConfig;
import dev.fancybars.util.SkyblockLocation;
import dev.fancybars.util.gui.Chroma;

/**
 * Draws maxed enchants in item tooltips with the SkyHanni-style chroma. Max levels come from Devonian's enchant list
 * (https://github.com/Synnerz/devonian, GPL-3.0); the item's own enchant levels come from its SkyBlock data.
 */
public final class MaxEnchantChroma {
	/** nbt name -> {lore name, max level}. */
	private static Map<String, Object[]> enchants;
	private static final String[] ROMAN = {"", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"};

	private MaxEnchantChroma() {}

	public static void init() {
		ItemTooltipCallback.EVENT.register((stack, context, type, lines) -> {
			try {
				apply(stack, lines);
			} catch (Exception e) {
				FancyBarsMod.LOGGER.debug("[SkyblockGuiUtils] Max enchant chroma failed", e);
			}
		});
	}

	private static Map<String, Object[]> enchants() {
		if (enchants == null) {
			enchants = new HashMap<>();
			try (var in = MaxEnchantChroma.class.getResourceAsStream("/assets/fancybars/data/enchants.json")) {
				if (in != null) {
					JsonObject root = JsonParser.parseString(new String(in.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
					for (Map.Entry<String, JsonElement> e : root.entrySet()) {
						JsonArray a = e.getValue().getAsJsonArray();
						enchants.put(e.getKey().toLowerCase(java.util.Locale.ROOT), new Object[]{a.get(0).getAsString(), a.get(1).getAsInt()});
					}
				}
			} catch (Exception e) {
				FancyBarsMod.LOGGER.warn("[SkyblockGuiUtils] Couldn't read enchant list", e);
			}
		}
		return enchants;
	}

	private static void apply(ItemStack stack, List<Component> lines) {
		FancyBarsConfig c = FancyBarsConfig.get();
		if (!c.chromaMaxEnchants || !SkyblockLocation.isOnSkyblock() || stack.isEmpty()) return;
		CompoundTag data = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
		CompoundTag ench = data.getCompoundOrEmpty("enchantments");
		if (ench.isEmpty()) return;
		// "Sharpness VII" / "Sharpness 7" for every maxed enchant, and every ultimate enchant, with the colour to use
		Map<String, Integer> targets = new HashMap<>();
		for (String key : ench.keySet()) {
			// Hypixel isn't consistent with case: Prosecute is stored as "PROSECUTE", Karma as "Karma"
			String id = key.toLowerCase(java.util.Locale.ROOT);
			Object[] info = enchants().get(id);
			if (info == null) continue;
			int level = ench.getIntOr(key, 0);
			boolean ultimate = id.startsWith("ultimate_");
			if (!ultimate && level < (Integer) info[1]) continue;
			int color = ultimate ? colorFor(c.enchantUltStyle, c.enchantUltColor, Chroma.TEXT_MARK_CUSTOM_ULTIMATE)
					: colorFor(c.enchantMaxStyle, c.enchantMaxColor, Chroma.TEXT_MARK_CUSTOM_ENCHANT);
			if (color == -1) continue;
			String lore = (String) info[0];
			if (level < ROMAN.length) targets.put(lore + " " + ROMAN[level], color);
			targets.put(lore + " " + level, color);
		}
		if (targets.isEmpty()) return;
		for (int i = 0; i < lines.size(); i++) {
			Component recoloured = recolour(lines.get(i), targets);
			if (recoloured != null) lines.set(i, recoloured);
		}
	}

	/** Text colour for a style: 0 Chroma, 1 Custom Chroma, 2 Solid Colour, 3 Off (-1 = leave it alone). */
	private static int colorFor(int style, int solid, int customMark) {
		return switch (style) {
			case 0 -> Chroma.shaderEnabled() ? Chroma.TEXT_MARK : -1;
			case 1 -> customMark;
			case 2 -> solid & 0xFFFFFF;
			default -> -1;
		};
	}

	/** The line with every target enchant recoloured, or null when there's nothing to change. */
	private static Component recolour(Component line, Map<String, Integer> targets) {
		List<Style> styles = new ArrayList<>();
		StringBuilder text = new StringBuilder();
		line.visit((Style style, String part) -> {
			for (int i = 0; i < part.length(); i++) styles.add(style);
			text.append(part);
			return Optional.empty();
		}, Style.EMPTY);
		String plain = text.toString();
		boolean changed = false;
		for (Map.Entry<String, Integer> entry : targets.entrySet()) {
			String target = entry.getKey();
			int from = 0;
			int at;
			while ((at = plain.indexOf(target, from)) >= 0) {
				int end = at + target.length();
				// whole words only ("Sharpness V" shouldn't match inside "Sharpness VI")
				boolean wordEnd = end >= plain.length() || !Character.isLetterOrDigit(plain.charAt(end));
				boolean wordStart = at == 0 || !Character.isLetterOrDigit(plain.charAt(at - 1));
				if (wordStart && wordEnd) {
					for (int i = at; i < end; i++) styles.set(i, styles.get(i).withColor(entry.getValue()));
					changed = true;
				}
				from = end;
			}
		}
		if (!changed) return null;
		MutableComponent out = Component.empty();
		int start = 0;
		for (int i = 1; i <= plain.length(); i++) {
			if (i == plain.length() || !styles.get(i).equals(styles.get(start))) {
				out.append(Component.literal(plain.substring(start, i)).setStyle(styles.get(start)));
				start = i;
			}
		}
		return out;
	}
}
