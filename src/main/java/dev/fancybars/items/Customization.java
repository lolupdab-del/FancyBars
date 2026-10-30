package dev.fancybars.items;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.PropertyMap;
import com.mojang.serialization.JsonOps;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.Util;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.item.equipment.trim.ArmorTrim;
import net.minecraft.world.item.equipment.trim.TrimMaterial;
import net.minecraft.world.item.equipment.trim.TrimPattern;

import dev.fancybars.FancyBarsMod;
import dev.fancybars.config.FancyBarsConfig;
import dev.fancybars.protection.ItemProtection;
import dev.fancybars.util.SkyblockLocation;

/**
 * Everything Skyblocker's item customisation can do, keyed by item UUID: custom names, enchantment glint, item models,
 * leather dye colours (static and animated), armour trims, what armour it looks like when worn, and head textures
 * (static and animated). SkyBlock heads, dyes and animated heads come from the NEU repo (bundled with the mod).
 */
public final class Customization {
	public record Head(String name, String id, String hash) {}

	public record AnimatedHead(String id, int ticks, List<String> frames) {}

	public static final List<Head> HEADS = new ArrayList<>();
	public static final Map<String, AnimatedHead> ANIMATED_HEADS = new LinkedHashMap<>();
	public static final Map<String, Integer> REPO_DYES = new LinkedHashMap<>();
	public static final Map<String, List<Integer>> REPO_ANIMATED_DYES = new LinkedHashMap<>();

	private static final Map<String, ResolvableProfile> PROFILES = new HashMap<>();
	private static final Map<String, Component> NAME_CACHE = new HashMap<>();
	private static final Map<String, ArmorTrim> TRIMS = new HashMap<>();
	private static int ticks;
	/** RGB of the 16 "&" colour codes, 0-f. */
	public static final int[] LEGACY_COLORS = {0x000000, 0x0000AA, 0x00AA00, 0x00AAAA, 0xAA0000, 0xAA00AA, 0xFFAA00, 0xAAAAAA,
			0x555555, 0x5555FF, 0x55FF55, 0x55FFFF, 0xFF5555, 0xFF55FF, 0xFFFF55, 0xFFFFFF};

	private Customization() {}

	public static void init() {
		loadRepoData();
		ClientTickEvents.END_CLIENT_TICK.register(_ -> ticks++);
	}

	private static JsonElement resource(String path) throws Exception {
		try (InputStream in = Customization.class.getResourceAsStream("/assets/fancybars/skyblock/" + path)) {
			if (in == null) throw new IllegalStateException("missing " + path);
			return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8));
		}
	}

	private static void loadRepoData() {
		try {
			for (JsonElement e : resource("heads.json").getAsJsonArray()) {
				JsonArray a = e.getAsJsonArray();
				HEADS.add(new Head(a.get(0).getAsString(), a.get(1).getAsString(), a.get(2).getAsString()));
			}
			for (Map.Entry<String, JsonElement> e : resource("animated_heads.json").getAsJsonObject().entrySet()) {
				JsonObject o = e.getValue().getAsJsonObject();
				List<String> frames = new ArrayList<>();
				for (JsonElement f : o.getAsJsonArray("frames")) frames.add(f.getAsString());
				ANIMATED_HEADS.put(e.getKey(), new AnimatedHead(e.getKey(), Math.max(1, o.get("ticks").getAsInt()), frames));
			}
			JsonObject dyes = resource("dyes.json").getAsJsonObject();
			for (Map.Entry<String, JsonElement> e : dyes.getAsJsonObject("static").entrySet()) REPO_DYES.put(e.getKey(), parseHex(e.getValue().getAsString()));
			for (Map.Entry<String, JsonElement> e : dyes.getAsJsonObject("animated").entrySet()) {
				List<Integer> colors = new ArrayList<>();
				for (JsonElement c : e.getValue().getAsJsonArray()) colors.add(parseHex(c.getAsString()));
				REPO_ANIMATED_DYES.put(e.getKey(), colors);
			}
			FancyBarsMod.LOGGER.info("[SBCLIENT] Loaded {} heads, {} animated heads, {} dyes", HEADS.size(), ANIMATED_HEADS.size(), REPO_DYES.size() + REPO_ANIMATED_DYES.size());
		} catch (Exception e) {
			FancyBarsMod.LOGGER.error("[SBCLIENT] Couldn't load bundled SkyBlock data", e);
		}
	}

	private static int parseHex(String hex) {
		return Integer.parseInt(hex.replace("#", ""), 16) & 0xFFFFFF;
	}

	public static String prettyId(String id) {
		String s = id.replace("DYE_", "").replace('_', ' ').toLowerCase(java.util.Locale.ROOT);
		StringBuilder out = new StringBuilder();
		for (String word : s.split(" ")) {
			if (word.isEmpty()) continue;
			if (!out.isEmpty()) out.append(' ');
			out.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
		}
		return out.toString();
	}

	// ---------------------------------------------------------------- Lookups (called from the mixins)

	private static boolean active() {
		return FancyBarsConfig.get().itemCustomizationEnabled && SkyblockLocation.isOnSkyblock();
	}

	public static @Nullable Component name(ItemStack stack) {
		FancyBarsConfig config = FancyBarsConfig.get();
		if (config.customItemNames.isEmpty() || !active()) return null;
		String raw = config.customItemNames.get(ItemProtection.getUuid(stack));
		if (raw == null) return null;
		return NAME_CACHE.computeIfAbsent(raw, Customization::parseName);
	}

	public static @Nullable Boolean glint(ItemStack stack) {
		FancyBarsConfig config = FancyBarsConfig.get();
		if (config.customGlint.isEmpty() || !active()) return null;
		return config.customGlint.get(ItemProtection.getUuid(stack));
	}

	public static @Nullable Integer dye(ItemStack stack) {
		FancyBarsConfig config = FancyBarsConfig.get();
		if (config.customDyeColors.isEmpty() && config.customAnimatedDyes.isEmpty() || !active()) return null;
		String uuid = ItemProtection.getUuid(stack);
		if (uuid.isEmpty()) return null;
		FancyBarsConfig.AnimatedDye animated = config.customAnimatedDyes.get(uuid);
		if (animated != null && !animated.colors.isEmpty()) return animate(animated);
		return config.customDyeColors.get(uuid);
	}

	public static @Nullable ArmorTrim trim(ItemStack stack) {
		FancyBarsConfig config = FancyBarsConfig.get();
		if (config.customArmorTrims.isEmpty() || !active()) return null;
		String[] key = config.customArmorTrims.get(ItemProtection.getUuid(stack));
		if (key == null || key.length < 2) return null;
		return TRIMS.computeIfAbsent(key[0] + "|" + key[1], k -> buildTrim(key[0], key[1]));
	}

	public static @Nullable Identifier armorModel(ItemStack stack) {
		FancyBarsConfig config = FancyBarsConfig.get();
		if (config.customArmorModel.isEmpty() || !active()) return null;
		String id = config.customArmorModel.get(ItemProtection.getUuid(stack));
		return id == null ? null : Identifier.tryParse(id);
	}

	public static @Nullable ResolvableProfile headProfile(ItemStack stack) {
		FancyBarsConfig config = FancyBarsConfig.get();
		if (config.customHelmetTextures.isEmpty() && config.customAnimatedHelmetTextures.isEmpty() || !active()) return null;
		String uuid = ItemProtection.getUuid(stack);
		if (uuid.isEmpty()) return null;
		String hash = config.customHelmetTextures.get(uuid);
		if (hash != null) return profile(hash);
		String animated = config.customAnimatedHelmetTextures.get(uuid);
		if (animated != null) {
			AnimatedHead head = ANIMATED_HEADS.get(animated);
			if (head != null && !head.frames().isEmpty()) return profile(head.frames().get(ticks / head.ticks() % head.frames().size()));
		}
		return null;
	}

	// ---------------------------------------------------------------- Helpers

	/** A player head profile for a skin texture hash. */
	public static ResolvableProfile profile(String hash) {
		return PROFILES.computeIfAbsent(hash, h -> {
			String json = "{\"textures\":{\"SKIN\":{\"url\":\"http://textures.minecraft.net/texture/" + h + "\"}}}";
			String value = Base64.getEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8));
			JsonObject property = new JsonObject();
			property.addProperty("name", "textures");
			property.addProperty("value", value);
			JsonArray properties = new JsonArray();
			properties.add(property);
			PropertyMap map = ExtraCodecs.PROPERTY_MAP.parse(JsonOps.INSTANCE, properties).getOrThrow();
			return ResolvableProfile.createResolved(new GameProfile(UUID.nameUUIDFromBytes(h.getBytes(StandardCharsets.UTF_8)), "custom", map));
		});
	}

	public static ItemStack headStack(String hash) {
		ItemStack stack = new ItemStack(net.minecraft.world.item.Items.PLAYER_HEAD);
		stack.set(net.minecraft.core.component.DataComponents.PROFILE, profile(hash));
		return stack;
	}

	/** Colour of an animated dye right now. */
	public static int animate(FancyBarsConfig.AnimatedDye dye) {
		List<Integer> colors = dye.colors;
		if (colors.size() == 1) return colors.getFirst();
		float duration = Math.max(0.1f, dye.duration);
		float delay = Math.max(0, dye.delay);
		float period = dye.cycleBack ? duration * 2 + delay * 2 : duration + delay;
		float t = (Util.getMillis() / 1000f) % period;
		float progress;
		if (dye.cycleBack) {
			if (t < duration) progress = t / duration;
			else if (t < duration + delay) progress = 1;
			else if (t < duration * 2 + delay) progress = 1 - (t - duration - delay) / duration;
			else progress = 0;
		} else {
			progress = t < duration ? t / duration : 1;
		}
		float pos = progress * (colors.size() - 1);
		int i = Math.min(colors.size() - 2, (int) Math.floor(pos));
		float f = pos - i;
		return lerpRgb(colors.get(i), colors.get(i + 1), f);
	}

	private static int lerpRgb(int a, int b, float t) {
		int r = Math.round(((a >> 16) & 0xFF) + (((b >> 16) & 0xFF) - ((a >> 16) & 0xFF)) * t);
		int g = Math.round(((a >> 8) & 0xFF) + (((b >> 8) & 0xFF) - ((a >> 8) & 0xFF)) * t);
		int bl = Math.round((a & 0xFF) + ((b & 0xFF) - (a & 0xFF)) * t);
		return (r << 16) | (g << 8) | bl;
	}

	private static @Nullable ArmorTrim buildTrim(String material, String pattern) {
		try {
			HolderLookup.Provider lookup = Minecraft.getInstance().getConnection() != null ? Minecraft.getInstance().getConnection().registryAccess() : null;
			if (lookup == null) return null;
			Holder<TrimMaterial> m = lookup.lookupOrThrow(Registries.TRIM_MATERIAL).listElements()
					.filter(h -> h.key().identifier().toString().equals(material)).findFirst().orElse(null);
			Holder<TrimPattern> p = lookup.lookupOrThrow(Registries.TRIM_PATTERN).listElements()
					.filter(h -> h.key().identifier().toString().equals(pattern)).findFirst().orElse(null);
			return m == null || p == null ? null : new ArmorTrim(m, p);
		} catch (Exception e) {
			return null;
		}
	}

	public static List<String> trimIds(boolean materials) {
		List<String> ids = new ArrayList<>();
		try {
			HolderLookup.Provider lookup = Minecraft.getInstance().getConnection().registryAccess();
			if (materials) lookup.lookupOrThrow(Registries.TRIM_MATERIAL).listElements().forEach(h -> ids.add(h.key().identifier().toString()));
			else lookup.lookupOrThrow(Registries.TRIM_PATTERN).listElements().forEach(h -> ids.add(h.key().identifier().toString()));
		} catch (Exception ignored) {
		}
		ids.sort(String::compareTo);
		return ids;
	}

	/** Builds a name from "&" codes: &0-&f colours, &l &o &n &m &k formats, &r reset, &#RRGGBB hex colours. */
	public static Component parseName(String raw) {
		MutableComponent out = Component.empty();
		Style style = Style.EMPTY.withItalic(false);
		StringBuilder run = new StringBuilder();
		for (int i = 0; i < raw.length(); i++) {
			char c = raw.charAt(i);
			if (c == '&' && i + 1 < raw.length()) {
				char code = Character.toLowerCase(raw.charAt(i + 1));
				Style next = null;
				int skip = 1;
				if (code == '#' && i + 7 < raw.length() && raw.substring(i + 2, i + 8).matches("[0-9a-fA-F]{6}")) {
					next = Style.EMPTY.withItalic(false).withColor(Integer.parseInt(raw.substring(i + 2, i + 8), 16));
					skip = 7;
				} else {
					int colorIndex = "0123456789abcdef".indexOf(code);
					if (colorIndex >= 0) {
						next = Style.EMPTY.withItalic(false).withColor(LEGACY_COLORS[colorIndex]);
					} else {
						ChatFormatting f = ChatFormatting.getByCode(code);
						if (f == ChatFormatting.RESET) next = Style.EMPTY.withItalic(false);
						else if (f != null) next = style.applyFormat(f);
					}
				}
				if (next != null) {
					if (!run.isEmpty()) out.append(Component.literal(run.toString()).setStyle(style));
					run.setLength(0);
					style = next;
					i += skip;
					continue;
				}
			}
			run.append(c);
		}
		if (!run.isEmpty()) out.append(Component.literal(run.toString()).setStyle(style));
		return out;
	}

	/** "&#RRGGBB" before every character, fading from one colour to the other. */
	public static String gradient(String text, int from, int to, String formats) {
		String plain = text.replaceAll("&#[0-9a-fA-F]{6}", "").replaceAll("&[0-9a-fk-orA-FK-OR]", "");
		StringBuilder out = new StringBuilder();
		int n = plain.length();
		for (int i = 0; i < n; i++) {
			int c = lerpRgb(from, to, n <= 1 ? 0 : i / (float) (n - 1));
			out.append(String.format("&#%06X", c)).append(formats).append(plain.charAt(i));
		}
		return out.toString();
	}

	public static void clearCaches() {
		NAME_CACHE.clear();
		TRIMS.clear();
	}
}
