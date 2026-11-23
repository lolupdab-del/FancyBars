package dev.fancybars.commands;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

import org.jspecify.annotations.Nullable;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;

import net.minecraft.network.chat.Component;

import dev.fancybars.FancyBarsMod;
import dev.fancybars.config.FancyBarsConfig;
import dev.fancybars.items.list.ItemRepo;

/**
 * Suggestions for /gfs (and /getfromsacks): as you type, every item that can be stored in sacks, then common amounts.
 * <p>
 * The command itself is Hypixel's and is left completely alone; only the chat box's suggestion list is filled in (the
 * server's own suggestions for it are just player names). The item list comes from NEU's repo (constants/sacks.json),
 * bundled with the mod.
 */
public final class GfsCommand {
	private GfsCommand() {}

	private static final String[] COMMANDS = {"gfs", "getfromsacks"};
	private static final String[] AMOUNTS = {"1", "16", "32", "64", "128", "160", "320", "640", "1280"};
	private static List<String> items;

	private static List<String> items() {
		if (items == null) {
			List<String> out = new ArrayList<>();
			try (var in = GfsCommand.class.getResourceAsStream("/assets/fancybars/data/sack_items.json")) {
				if (in != null) for (JsonElement e : JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonArray()) out.add(e.getAsString());
			} catch (Exception e) {
				FancyBarsMod.LOGGER.warn("[SkyblockGuiUtils] Couldn't read the sack item list", e);
			}
			items = out;
		}
		return items;
	}

	/**
	 * Suggestions for the chat box's text {@code input} (cursor at {@code cursor}), or null when it isn't a /gfs
	 * command (then the normal suggestions are used).
	 */
	public static @Nullable CompletableFuture<Suggestions> suggest(String input, int cursor) {
		if (!FancyBarsConfig.get().gfsSuggestions || !input.startsWith("/")) return null;
		String text = input.substring(0, Math.min(cursor, input.length()));
		int afterName = -1;
		for (String name : COMMANDS) {
			if (text.regionMatches(true, 1, name + " ", 0, name.length() + 1)) afterName = name.length() + 2;
		}
		if (afterName < 0) return null;
		SuggestionsBuilder builder = new SuggestionsBuilder(text, afterName);
		String typed = builder.getRemaining();
		int space = typed.indexOf(' ');
		if (space < 0) {
			// the item: names starting with what's typed first, then names containing it
			String q = typed.toUpperCase(Locale.ROOT).replace(' ', '_');
			List<String> starts = new ArrayList<>();
			List<String> contains = new ArrayList<>();
			for (String id : items()) {
				if (id.startsWith(q)) starts.add(id);
				else if (q.length() >= 2 && id.contains(q)) contains.add(id);
			}
			for (String id : starts) add(builder, id);
			for (String id : contains) add(builder, id);
			return builder.buildFuture();
		}
		// the amount
		String amount = typed.substring(space + 1).trim();
		if (amount.contains(" ")) return Suggestions.empty();
		SuggestionsBuilder next = builder.createOffset(afterName + space + 1);
		for (String a : AMOUNTS) if (a.startsWith(amount)) next.suggest(a);
		return next.buildFuture();
	}

	/** Suggests the item's id, with its name as the tooltip when the item list knows it. */
	private static void add(SuggestionsBuilder builder, String id) {
		ItemRepo.Entry entry = ItemRepo.get(id.replace(':', '-'));
		if (entry == null) entry = ItemRepo.get(id);
		if (entry != null) builder.suggest(id, Component.literal(entry.name()));
		else builder.suggest(id);
	}
}
