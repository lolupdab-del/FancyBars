package dev.fancybars.items.list;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

import dev.fancybars.FancyBarsMod;

/**
 * Every SkyBlock item and recipe, from the SkyblockAPI repo (the same data Skysoft's item list uses,
 * https://github.com/SkyblockAPI/Repo). Downloaded once a day and kept in the config folder.
 */
public final class ItemRepo {
	private static final String BASE = "https://raw.githubusercontent.com/SkyblockAPI/Repo/main/cloudflare/1_21_5/";
	private static final Duration REFRESH = Duration.ofHours(24);

	public record Entry(String id, ItemStack stack, String name, String search) {}

	/** A recipe: ingredients (id, count) laid out for its type, and the result. */
	public record Recipe(String type, List<Ingredient> grid, List<Ingredient> inputs, Ingredient result, String npc, int coins, int seconds) {}

	public record Ingredient(String id, int count) {
		public static final Ingredient EMPTY = new Ingredient("", 0);
	}

	private static volatile List<Entry> entries = List.of();
	private static volatile Map<String, Entry> byId = Map.of();
	private static volatile Map<String, List<Recipe>> recipesFor = Map.of();
	private static volatile Map<String, List<Recipe>> usagesOf = Map.of();
	private static volatile boolean loading;
	private static volatile String status = "";

	private ItemRepo() {}

	public static List<Entry> entries() {
		return entries;
	}

	public static @Nullable Entry get(String id) {
		return byId.get(id);
	}

	public static List<Recipe> recipesFor(String id) {
		return recipesFor.getOrDefault(id, List.of());
	}

	public static List<Recipe> usagesOf(String id) {
		return usagesOf.getOrDefault(id, List.of());
	}

	public static String status() {
		return status;
	}

	/** SkyBlock id of a stack ("" if it has none). */
	public static String idOf(ItemStack stack) {
		return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getStringOr("id", "");
	}

	/** Loads (and if needed downloads) the data in the background, once. */
	public static void ensureLoaded() {
		if (loading || !entries.isEmpty()) return;
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null) return;
		DynamicOps<JsonElement> ops = mc.level.registryAccess().createSerializationContext(JsonOps.INSTANCE);
		loading = true;
		status = "Loading items…";
		FancyBarsMod.VIRTUAL_THREAD_EXECUTOR.execute(() -> {
			try {
				Path dir = FancyBarsMod.CONFIG_DIR.resolve("item-repo");
				Files.createDirectories(dir);
				String items = fetch(dir, "items.min.json");
				String recipes = fetch(dir, "recipes.min.json");
				if (items == null) {
					status = "Couldn't download the item list";
					return;
				}
				parseItems(JsonParser.parseString(items).getAsJsonArray(), ops);
				if (recipes != null) parseRecipes(JsonParser.parseString(recipes).getAsJsonArray());
				status = "";
			} catch (Exception e) {
				FancyBarsMod.LOGGER.warn("[SBCLIENT] Item list failed to load", e);
				status = "Couldn't load the item list";
			} finally {
				loading = false;
			}
		});
	}

	/** Cached file if it's fresh, else downloaded (falls back to the old copy when offline). */
	private static @Nullable String fetch(Path dir, String name) {
		Path file = dir.resolve(name);
		try {
			if (Files.exists(file) && Files.getLastModifiedTime(file).toInstant().isAfter(java.time.Instant.now().minus(REFRESH))) {
				return Files.readString(file);
			}
		} catch (Exception ignored) {}
		try {
			HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();
			HttpResponse<String> res = http.send(HttpRequest.newBuilder(URI.create(BASE + name))
					.header("User-Agent", "SBCLIENT/" + FancyBarsMod.VERSION).timeout(Duration.ofSeconds(90)).GET().build(),
					HttpResponse.BodyHandlers.ofString());
			if (res.statusCode() == 200) {
				Files.writeString(file, res.body());
				return res.body();
			}
		} catch (Exception e) {
			FancyBarsMod.LOGGER.debug("[SBCLIENT] Item repo download failed: {}", name, e);
		}
		try {
			return Files.exists(file) ? Files.readString(file) : null;
		} catch (Exception e) {
			return null;
		}
	}

	private static void parseItems(JsonArray array, DynamicOps<JsonElement> ops) {
		List<Entry> list = new ArrayList<>();
		Map<String, Entry> map = new HashMap<>();
		for (JsonElement el : array) {
			ItemStack stack = decode(el, ops);
			if (stack == null || stack.isEmpty()) continue;
			String id = idOf(stack);
			if (id.isEmpty() || map.containsKey(id)) continue;
			String name = ChatFormatting.stripFormatting(stack.getHoverName().getString());
			if (name == null) name = id;
			Entry entry = new Entry(id, stack, name, (name + " " + id.replace('_', ' ')).toLowerCase(Locale.ROOT));
			list.add(entry);
			map.put(id, entry);
		}
		list.sort(Comparator.comparing(Entry::name, String.CASE_INSENSITIVE_ORDER));
		entries = Collections.unmodifiableList(list);
		byId = map;
	}

	private static @Nullable ItemStack decode(JsonElement el, DynamicOps<JsonElement> ops) {
		var result = ItemStack.CODEC.parse(ops, el).result();
		if (result.isPresent()) return result.get();
		// a component this Minecraft doesn't understand: keep only the basics
		if (!el.isJsonObject()) return null;
		JsonObject copy = el.getAsJsonObject().deepCopy();
		JsonObject components = copy.getAsJsonObject("components");
		if (components != null) {
			JsonObject kept = new JsonObject();
			for (String key : new String[]{"minecraft:custom_data", "minecraft:custom_name", "minecraft:lore", "minecraft:profile", "minecraft:dyed_color"}) {
				if (components.has(key)) kept.add(key, components.get(key));
			}
			copy.add("components", kept);
		}
		return ItemStack.CODEC.parse(ops, copy).result().orElse(null);
	}

	private static Ingredient ingredient(JsonObject o) {
		if (o == null) return Ingredient.EMPTY;
		if (o.has("id")) return new Ingredient(o.get("id").getAsString(), o.has("count") ? o.get("count").getAsInt() : 1);
		return Ingredient.EMPTY;
	}

	private static void parseRecipes(JsonArray array) {
		Map<String, List<Recipe>> forMap = new HashMap<>();
		Map<String, List<Recipe>> useMap = new HashMap<>();
		for (JsonElement el : array) {
			try {
				JsonObject o = el.getAsJsonObject();
				String type = o.get("type").getAsString();
				Recipe recipe;
				switch (type) {
					case "crafting" -> {
						JsonArray keys = o.getAsJsonArray("keys");
						JsonArray pattern = o.getAsJsonArray("pattern");
						List<Ingredient> grid = new ArrayList<>();
						for (JsonElement p : pattern) {
							int k = p.getAsInt();
							grid.add(k < 0 || k >= keys.size() ? Ingredient.EMPTY : ingredient(keys.get(k).getAsJsonObject()));
						}
						List<Ingredient> inputs = new ArrayList<>();
						for (JsonElement k : keys) inputs.add(ingredient(k.getAsJsonObject()));
						recipe = new Recipe(type, grid, inputs, ingredient(o.getAsJsonObject("result")), "", 0, 0);
					}
					case "forge" -> {
						List<Ingredient> inputs = new ArrayList<>();
						for (JsonElement k : o.getAsJsonArray("inputs")) inputs.add(ingredient(k.getAsJsonObject()));
						recipe = new Recipe(type, List.of(), inputs, ingredient(o.getAsJsonObject("result")), "",
								o.has("coins") ? o.get("coins").getAsInt() : 0, o.has("time") ? o.get("time").getAsInt() : 0);
					}
					case "shop" -> {
						List<Ingredient> inputs = new ArrayList<>();
						int coins = 0;
						for (JsonElement k : o.getAsJsonArray("inputs")) {
							JsonObject in = k.getAsJsonObject();
							if (in.has("currency")) coins += in.get("count").getAsInt();
							else inputs.add(ingredient(in));
						}
						recipe = new Recipe(type, List.of(), inputs, ingredient(o.getAsJsonObject("result")), o.has("npc") ? o.get("npc").getAsString() : "", coins, 0);
					}
					default -> {
						continue;
					}
				}
				if (recipe.result().id().isEmpty()) continue;
				forMap.computeIfAbsent(recipe.result().id(), k -> new ArrayList<>()).add(recipe);
				java.util.Set<String> used = new java.util.HashSet<>();
				for (Ingredient in : recipe.inputs()) if (!in.id().isEmpty() && used.add(in.id())) useMap.computeIfAbsent(in.id(), k -> new ArrayList<>()).add(recipe);
			} catch (Exception ignored) {}
		}
		recipesFor = forMap;
		usagesOf = useMap;
	}

	/** "ADVENTURER_NPC" -> "Adventurer". */
	public static String prettyNpc(String id) {
		String s = id.replaceAll("_NPC$", "").replace('_', ' ').toLowerCase(Locale.ROOT);
		StringBuilder out = new StringBuilder();
		for (String w : s.split(" ")) {
			if (w.isEmpty()) continue;
			out.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1)).append(' ');
		}
		return out.toString().trim();
	}

	/** A stack for an ingredient (with its count), or a barrier for unknown ids. */
	public static ItemStack stackFor(Ingredient in) {
		if (in == null || in.id().isEmpty()) return ItemStack.EMPTY;
		Entry e = byId.get(in.id());
		ItemStack s = e != null ? e.stack().copy() : new ItemStack(net.minecraft.world.item.Items.BARRIER);
		s.setCount(Math.max(1, Math.min(in.count(), 64)));
		return s;
	}

	public static JsonObject debug() {
		JsonObject o = new JsonObject();
		o.addProperty("items", entries.size());
		o.addProperty("recipes", recipesFor.size());
		return o;
	}
}
