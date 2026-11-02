package dev.fancybars.items;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.JsonOps;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectMaps;

import net.azureaaron.networth.ItemCalculator;
import net.azureaaron.networth.NetworthResult;
import net.azureaaron.networth.data.SkyblockItemData;
import net.azureaaron.networth.item.ItemMetadataRetriever;
import net.azureaaron.networth.item.SkyblockItemStack;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

import dev.fancybars.FancyBarsMod;
import dev.fancybars.config.FancyBarsConfig;
import dev.fancybars.util.SkyblockLocation;

/**
 * Item value tooltips, ported from Skyblocker (LGPL-3.0): estimated item value (AzureAaron's networth calculator,
 * Apache-2.0), lowest BIN, 3-day average BIN, bazaar buy / sell and NPC sell price.
 */
public final class ItemValue {
	private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
	private static final String API = "https://hysky.de/api/";
	private static final long REFRESH_MS = 5 * 60_000;

	private static volatile Map<String, Double> lowestBins = Map.of();
	private static volatile Map<String, Double> averageBins = Map.of();
	private static volatile Map<String, Double> npcPrices = Map.of();
	private static volatile Map<String, double[]> bazaar = Map.of(); // {buy, sell}, NaN when missing
	private static volatile Object2ObjectMap<String, SkyblockItemData> itemData = Object2ObjectMaps.emptyMap();
	private static long lastRefresh;
	private static boolean loadingItems;

	private ItemValue() {}

	public static void init() {
		ClientTickEvents.END_CLIENT_TICK.register(mc -> {
			if (!FancyBarsConfig.get().itemValueEnabled) return;
			long now = System.currentTimeMillis();
			if (lastRefresh != 0 && (now - lastRefresh < REFRESH_MS || !SkyblockLocation.isOnSkyblock())) return;
			lastRefresh = now;
			refresh();
		});
		ItemTooltipCallback.EVENT.register((stack, context, type, lines) -> {
			try {
				addLines(stack, lines);
			} catch (Exception e) {
				FancyBarsMod.LOGGER.debug("[SBCLIENT] Item value tooltip failed", e);
			}
		});
	}

	/** Starts loading prices now (they normally load on the first tick on SkyBlock). */
	public static void ensureLoaded() {
		if (lastRefresh == 0) {
			lastRefresh = System.currentTimeMillis();
			refresh();
		}
	}

	// ---------------------------------------------------------------- data

	private static void refresh() {
		FancyBarsMod.VIRTUAL_THREAD_EXECUTOR.execute(() -> {
			Map<String, Double> lb = doubles(get(API + "auctions/lowestbins"));
			if (lb != null) lowestBins = lb;
			Map<String, Double> avg = doubles(get(API + "auctions/lowestbins/average?days=3"));
			if (avg != null) averageBins = avg;
			if (npcPrices.isEmpty()) {
				Map<String, Double> npc = doubles(get(API + "npcprice"));
				if (npc != null) npcPrices = npc;
			}
			JsonElement bz = get(API + "bazaar");
			if (bz != null && bz.isJsonObject()) {
				Map<String, double[]> map = new HashMap<>();
				for (Map.Entry<String, JsonElement> e : bz.getAsJsonObject().entrySet()) {
					if (!e.getValue().isJsonObject()) continue;
					JsonObject o = e.getValue().getAsJsonObject();
					map.put(e.getKey(), new double[]{num(o, "buyPrice"), num(o, "sellPrice")});
				}
				bazaar = map;
				version++;
			}
			if (itemData.isEmpty() && !loadingItems) {
				loadingItems = true;
				try {
					JsonElement items = get("https://api.hypixel.net/v2/resources/skyblock/items");
					if (items != null && items.isJsonObject() && items.getAsJsonObject().has("items")) {
						JsonArray array = items.getAsJsonObject().getAsJsonArray("items");
						itemData = SkyblockItemData.MAP_CODEC.parse(JsonOps.INSTANCE, array).getOrThrow();
					}
				} catch (Exception e) {
					FancyBarsMod.LOGGER.warn("[SBCLIENT] Couldn't load SkyBlock item data", e);
				} finally {
					loadingItems = false;
				}
			}
		});
	}

	private static JsonElement get(String url) {
		try {
			HttpResponse<String> response = HTTP.send(HttpRequest.newBuilder(URI.create(url))
					.header("User-Agent", "SBCLIENT/" + FancyBarsMod.VERSION)
					.timeout(Duration.ofSeconds(20)).GET().build(), HttpResponse.BodyHandlers.ofString());
			if (response.statusCode() != 200) return null;
			return JsonParser.parseString(response.body());
		} catch (Exception e) {
			FancyBarsMod.LOGGER.debug("[SBCLIENT] Price fetch failed: {}", url, e);
			return null;
		}
	}

	private static Map<String, Double> doubles(JsonElement json) {
		if (json == null || !json.isJsonObject()) return null;
		Map<String, Double> map = new HashMap<>();
		for (Map.Entry<String, JsonElement> e : json.getAsJsonObject().entrySet()) {
			if (e.getValue().isJsonPrimitive() && e.getValue().getAsJsonPrimitive().isNumber()) map.put(e.getKey(), e.getValue().getAsDouble());
		}
		return map;
	}

	private static double num(JsonObject o, String key) {
		JsonElement e = o.get(key);
		return e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isNumber() ? e.getAsDouble() : Double.NaN;
	}

	/** Bazaar buy price, else 3-day average BIN, else lowest BIN (what Skyblocker's networth uses). */
	/** Bumped whenever new prices arrive (so lists sorted by value re-sort). */
	public static volatile int version;

	/** What an item is worth: bazaar / auction price, else what an NPC pays; -1 when unknown. */
	public static double valueOf(String id) {
		double p = price(id);
		if (p > 0) return p;
		Double npc = npcPrices.get(id);
		return npc != null && npc > 0 ? npc : -1;
	}

	/**
	 * What a whole stack is worth (Chest Value): bazaar price x count for bazaar items, otherwise the estimated value
	 * with upgrades (like the Est. Item Value tooltip line), otherwise the plain price. -1 when unknown.
	 */
	public static double stackValue(ItemStack stack) {
		if (stack.isEmpty()) return -1;
		CompoundTag data = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
		String id = data.getStringOr("id", "");
		if (id.isEmpty()) return -1;
		String apiId = apiId(id, data);
		int count = Math.max(1, stack.getCount());
		double[] bz = bazaar.get(apiId);
		if (bz != null) return Double.isNaN(bz[0]) ? -1 : bz[0] * count;
		if (!itemData.isEmpty()) {
			double est = networth(id, data, count).price();
			if (est > 0) return est;
		}
		double each = valueOf(apiId);
		return each > 0 ? each * count : -1;
	}

	private static double price(String id) {
		double[] bz = bazaar.get(id);
		if (bz != null) return Double.isNaN(bz[0]) ? 0 : bz[0];
		Double avg = averageBins.get(id);
		if (avg != null) return avg;
		Double lb = lowestBins.get(id);
		return lb != null ? lb : 0;
	}

	// ---------------------------------------------------------------- tooltip

	private static void addLines(ItemStack stack, List<Component> lines) {
		FancyBarsConfig config = FancyBarsConfig.get();
		if (!config.itemValueEnabled || !SkyblockLocation.isOnSkyblock() || stack.isEmpty()) return;
		CompoundTag data = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
		String id = data.getStringOr("id", "");
		if (id.isEmpty()) return;
		String apiId = apiId(id, data);
		int count = Math.max(1, stack.getCount());

		double[] bz = bazaar.get(apiId);
		if (config.itemValueBazaar && bz != null) {
			if (!Double.isNaN(bz[0])) lines.add(line("Bazaar Buy: ", bz[0], count));
			if (!Double.isNaN(bz[1])) lines.add(line("Bazaar Sell: ", bz[1], count));
		}
		if (config.itemValueLowestBin && bz == null) {
			Double lb = lowestBins.get(apiId);
			if (lb != null) lines.add(line("Lowest BIN: ", lb, count));
		}
		if (config.itemValueAvgBin && bz == null) {
			Double avg = averageBins.get(apiId);
			if (avg != null) lines.add(line("3 Day Avg BIN: ", avg, count));
		}
		if (config.itemValueNpc) {
			Double npc = npcPrices.get(id);
			if (npc != null && npc > 0) lines.add(line("NPC Sell Price: ", npc, count));
		}
		if (config.itemValueEstimated && bz == null && !itemData.isEmpty()) {
			NetworthResult result = networth(id, data, count);
			if (result.price() > 0) lines.add(line("Est. Item Value: ", result.price() / count, count));
		}
	}

	private static Component line(String label, double each, int count) {
		MutableComponent text = Component.literal(label).withStyle(ChatFormatting.GOLD)
				.append(Component.literal(format(each * count) + " Coins").withStyle(ChatFormatting.DARK_AQUA));
		if (count > 1) text.append(Component.literal(" (" + format(each) + " each)").withStyle(ChatFormatting.GRAY));
		return text;
	}

	private static String format(double coins) {
		return String.format(Locale.ENGLISH, "%,.1f", coins);
	}

	private static NetworthResult networth(String id, CompoundTag data, int count) {
		try {
			Dynamic<Tag> dynamic = new Dynamic<>(NbtOps.INSTANCE, data);
			SkyblockItemStack sbStack = SkyblockItemStack.of(id, count, dynamic, new Metadata(cakeYears(id, data)));
			return ItemCalculator.calculate(sbStack, ItemValue::price, itemData);
		} catch (Exception e) {
			return new NetworthResult(0, 0, List.of());
		}
	}

	private record Metadata(IntList cakeBagCakeYears) implements ItemMetadataRetriever {}

	private static IntList cakeYears(String id, CompoundTag data) {
		if (!id.equals("NEW_YEAR_CAKE_BAG") || !data.contains("new_year_cake_bag_data")) return IntList.of();
		try {
			CompoundTag bag = NbtIo.readCompressed(new java.io.ByteArrayInputStream(data.getByteArray("new_year_cake_bag_data").orElse(new byte[0])), NbtAccounter.unlimitedHeap());
			ListTag items = bag.getListOrEmpty("i");
			IntList years = new IntArrayList();
			for (Tag element : items) {
				if (element instanceof CompoundTag compound && compound.getCompoundOrEmpty("tag").contains("ExtraAttributes")) {
					years.add(compound.getCompoundOrEmpty("tag").getCompoundOrEmpty("ExtraAttributes").getIntOr("new_years_cake", 0));
				}
			}
			return years;
		} catch (Exception e) {
			return IntList.of();
		}
	}

	/** The id the price APIs use (same rules as Skyblocker). */
	private static String apiId(String id, CompoundTag data) {
		if (data.contains("is_shiny")) return "SHINY_" + id;
		switch (id) {
			case "ENCHANTED_BOOK" -> {
				CompoundTag enchants = data.getCompoundOrEmpty("enchantments");
				if (enchants.size() == 1) {
					String enchant = enchants.keySet().iterator().next();
					return "ENCHANTMENT_" + enchant.toUpperCase(Locale.ENGLISH) + "_" + enchants.getIntOr(enchant, 0);
				}
			}
			case "PET" -> {
				try {
					JsonObject pet = JsonParser.parseString(data.getStringOr("petInfo", "{}")).getAsJsonObject();
					if (pet.has("tier") && pet.has("type")) return "LVL_1_" + pet.get("tier").getAsString() + "_" + pet.get("type").getAsString();
				} catch (Exception ignored) {}
			}
			case "POTION" -> {
				if (data.contains("potion") && data.contains("potion_level")) {
					return (data.getStringOr("potion", "") + "_" + id + "_" + data.getIntOr("potion_level", 0)
							+ (data.getBooleanOr("enhanced", false) ? "_ENHANCED" : "")
							+ (data.getBooleanOr("extended", false) ? "_EXTENDED" : "")
							+ (data.getBooleanOr("splash", false) ? "_SPLASH" : "")).toUpperCase(Locale.ENGLISH);
				}
			}
			case "RUNE", "UNIQUE_RUNE" -> {
				CompoundTag runes = data.getCompoundOrEmpty("runes");
				if (!runes.isEmpty()) {
					String rune = runes.keySet().iterator().next();
					return rune.toUpperCase(Locale.ENGLISH) + "_RUNE_" + runes.getIntOr(rune, 0);
				}
			}
			case "NEW_YEAR_CAKE" -> {
				return id + "_" + data.getIntOr("new_years_cake", 0);
			}
			case "PARTY_HAT_CRAB", "PARTY_HAT_CRAB_ANIMATED", "BALLOON_HAT_2024", "BALLOON_HAT_2025", "CAKE_HAT_2026" -> {
				return id + "_" + data.getStringOr("party_hat_color", "").toUpperCase(Locale.ENGLISH);
			}
			case "PARTY_HAT_SLOTH" -> {
				return id + "_" + data.getStringOr("party_hat_emoji", "").toUpperCase(Locale.ENGLISH);
			}
			case "MIDAS_SWORD" -> {
				if (data.getIntOr("winning_bid", 0) >= 50_000_000) return id + "_50M";
			}
			case "MIDAS_STAFF" -> {
				if (data.getIntOr("winning_bid", 0) >= 100_000_000) return id + "_100M";
			}
			default -> {}
		}
		return id;
	}
}
