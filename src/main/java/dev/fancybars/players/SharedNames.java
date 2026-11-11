package dev.fancybars.players;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

import dev.fancybars.FancyBarsMod;

/**
 * Custom names everyone with the mod sees, like the shared player sizes: they come from {@code names.json} in the
 * mod's GitHub repo, downloaded every few minutes. Only people who can push to that repo can hand them out.
 * <p>
 * An entry is either a plain string ({@code "&6&lBob"}, with &amp; codes and &amp;#RRGGBB hex colours) or an object:
 * <pre>
 * "ign": {
 *   "name": "Bob",                       // text (also takes &amp; codes)
 *   "colors": ["#FF5555", "#FFAA00"],    // one colour, or two or more for a gradient
 *   "animated": true,                    // gradient slides along the name
 *   "chroma": true,                      // rainbow (overrides colors)
 *   "chromaSpeed": 1.0,                  // rainbow speed (bigger is faster)
 *   "bold": true, "italic": false, "underlined": false, "strikethrough": false, "obfuscated": false,
 *   "shadow": "#3F0000",                 // shadow colour, or false for no shadow
 *   "font": "default",                   // default, uniform, alt (enchanting table), illageralt
 *   "prefix": "&amp;c[★] ", "suffix": " &amp;e✦",  // extra text around the name (&amp; codes)
 *   "hover": "Owner of SkyblockGuiUtils" // shown when hovering their name in chat
 * }
 * </pre>
 */
public final class SharedNames {
	public static final String LIST_URL = "https://raw.githubusercontent.com/lolupdab-del/FancyBars/main/names.json";
	private static final long REFRESH_TICKS = 20 * 60 * 5;

	/** One custom name, already parsed. Colours are RGB; empty {@code colors} means "keep their usual colour". */
	public record Entry(String name, List<Integer> colors, boolean animated, boolean chroma, float chromaSpeed,
			Boolean bold, Boolean italic, Boolean underlined, Boolean strikethrough, Boolean obfuscated,
			Integer shadow, boolean noShadow, String font, String prefix, String suffix, String hover) {}

	private static volatile Map<String, Entry> names = Map.of();

	/** How many names can flow with their own palette in the chroma shader at once (one pipeline each). */
	public static final int SLOTS = 16;
	/** Most colours one name's palette can have in the shader. */
	public static final int MAX_STOPS = 6;
	/** IGN → shader palette slot, for names with a gradient. */
	private static volatile Map<String, Integer> slots = Map.of();
	/** Slot → its name (null when unused). */
	private static volatile Entry[] slotEntries = new Entry[SLOTS];
	/** IGN → the name's text as it shows on screen (lowercase, colour codes removed). */
	private static volatile Map<String, String> shown = Map.of();
	private static long ticks = REFRESH_TICKS - 60;
	private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

	private SharedNames() {}

	public static void init() {
		ClientTickEvents.END_CLIENT_TICK.register(mc -> {
			if (mc.player == null) return;
			if (++ticks >= REFRESH_TICKS) {
				ticks = 0;
				refresh();
			}
		});
	}

	/** Lowercase IGN → custom name. */
	public static Map<String, Entry> all() {
		return names;
	}

	/** The shader palette slot of a name with a gradient, or -1. */
	public static int slot(String ign) {
		return slots.getOrDefault(ign, -1);
	}

	/** Slot → name, for writing the palettes into the chroma uniforms. */
	public static Entry[] slotEntries() {
		return slotEntries;
	}

	/** The name's text as it shows on screen (lowercase, no colour codes). */
	public static String shownText(String ign) {
		return shown.getOrDefault(ign, "");
	}

	private static void use(Map<String, Entry> fresh) {
		Map<String, Integer> newSlots = new HashMap<>();
		Entry[] newEntries = new Entry[SLOTS];
		Map<String, String> newShown = new HashMap<>();
		// sorted so a name keeps its slot between refreshes
		List<String> keys = new ArrayList<>(fresh.keySet());
		keys.sort(null);
		for (String key : keys) {
			Entry e = fresh.get(key);
			String text = e.name().replaceAll("(?i)[&§](#[0-9a-f]{6}|[0-9a-fk-or])", "").trim().toLowerCase(Locale.ROOT);
			if (!text.isEmpty()) newShown.put(key, text);
			if (!e.chroma() && e.colors().size() > 1 && newSlots.size() < SLOTS) {
				newEntries[newSlots.size()] = e;
				newSlots.put(key, newSlots.size());
			}
		}
		slots = newSlots;
		slotEntries = newEntries;
		shown = newShown;
		boolean changed = !fresh.equals(names);
		names = fresh;
		// floating name tags that appeared before this (yours, the moment you joined) rebuild with the new names
		if (changed) Nicknames.refreshNameTags();
	}

	public static void refresh() {
		HttpRequest request = HttpRequest.newBuilder(URI.create(LIST_URL)).timeout(Duration.ofSeconds(15)).GET().build();
		HTTP.sendAsync(request, HttpResponse.BodyHandlers.ofString()).thenAccept(response -> {
			if (response.statusCode() != 200) return;
			try {
				use(parse(response.body()));
			} catch (Exception e) {
				FancyBarsMod.LOGGER.warn("[SkyblockGuiUtils] Couldn't read the shared custom name list", e);
			}
		}).exceptionally(e -> null);
	}

	static Map<String, Entry> parse(String json) {
		JsonObject players = JsonParser.parseString(json).getAsJsonObject().getAsJsonObject("players");
		Map<String, Entry> out = new HashMap<>();
		if (players == null) return out;
		for (Map.Entry<String, JsonElement> e : players.entrySet()) {
			try {
				Entry entry = entry(e.getValue());
				if (entry != null) out.put(e.getKey().trim().toLowerCase(Locale.ROOT), entry);
			} catch (Exception ex) {
				// one broken entry shouldn't take the whole list down
				FancyBarsMod.LOGGER.warn("[SkyblockGuiUtils] Skipping custom name for {}: {}", e.getKey(), ex.toString());
			}
		}
		return out;
	}

	private static Entry entry(JsonElement value) {
		if (value.isJsonPrimitive()) {
			String name = value.getAsString();
			return name.isBlank() ? null : new Entry(name, List.of(), false, false, 1, null, null, null, null, null, null, false, "", "", "", "");
		}
		JsonObject o = value.getAsJsonObject();
		String name = str(o, "name");
		if (name.isBlank()) return null;
		List<Integer> colors = new ArrayList<>();
		if (o.has("colors") && o.get("colors").isJsonArray()) {
			JsonArray a = o.getAsJsonArray("colors");
			for (JsonElement c : a) colors.add(hex(c.getAsString()));
		} else if (o.has("color")) {
			colors.add(hex(o.get("color").getAsString()));
		}
		Integer shadow = null;
		boolean noShadow = false;
		if (o.has("shadow")) {
			JsonElement s = o.get("shadow");
			if (s.isJsonPrimitive() && s.getAsJsonPrimitive().isBoolean()) noShadow = !s.getAsBoolean();
			else shadow = 0xFF000000 | hex(s.getAsString());
		}
		float speed = o.has("chromaSpeed") ? Math.clamp(o.get("chromaSpeed").getAsFloat(), 0.05f, 10f) : 1;
		return new Entry(name, List.copyOf(colors), bool(o, "animated") == Boolean.TRUE, bool(o, "chroma") == Boolean.TRUE, speed,
				bool(o, "bold"), bool(o, "italic"), bool(o, "underlined"), bool(o, "strikethrough"), bool(o, "obfuscated"),
				shadow, noShadow, str(o, "font"), str(o, "prefix"), str(o, "suffix"), str(o, "hover"));
	}

	private static String str(JsonObject o, String key) {
		return o.has(key) && o.get(key).isJsonPrimitive() ? o.get(key).getAsString() : "";
	}

	private static Boolean bool(JsonObject o, String key) {
		return o.has(key) && o.get(key).isJsonPrimitive() ? o.get(key).getAsBoolean() : null;
	}

	/** "#FFAA00" / "FFAA00" → 0xFFAA00. */
	static int hex(String s) {
		s = s.trim();
		if (s.startsWith("#")) s = s.substring(1);
		return Integer.parseInt(s, 16) & 0xFFFFFF;
	}
}
