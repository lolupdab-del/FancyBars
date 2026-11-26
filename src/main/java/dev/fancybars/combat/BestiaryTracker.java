package dev.fancybars.combat;

import java.io.InputStreamReader;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.joml.Matrix3x2fStack;
import org.jspecify.annotations.Nullable;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.PropertyMap;
import com.mojang.serialization.JsonOps;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;

import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.component.ResolvableProfile;

import dev.fancybars.FancyBarsMod;
import dev.fancybars.config.FancyBarsConfig;
import dev.fancybars.storage.BackpackPreview;
import dev.fancybars.util.SkyblockLocation;
import dev.fancybars.util.gui.Theme;

/**
 * Bestiary Tracker: for the mobs you pick, your bestiary kills, tier and progress to the next tier, kills per hour and
 * a picture of the mob. Kills are read from the /bestiary menu whenever you open it (remembered per profile) and counted
 * up as you kill tracked mobs in between. The mob list, tiers and head pictures come from NEU's repo
 * (constants/bestiary.json), bundled with the mod.
 */
public final class BestiaryTracker {
	private BestiaryTracker() {}

	/** One bestiary mob. {@code tiers} are the total kills needed for each tier, ending at {@code cap}. */
	public record Mob(String name, String color, String island, int cap, int[] tiers, String texture, String item) {
		public ItemStack icon() {
			return ICONS.computeIfAbsent(name, _ -> makeIcon(this));
		}

		public int tier(long kills) {
			int t = 0;
			while (t < tiers.length && kills >= tiers[t]) t++;
			return t;
		}
	}

	private static final Map<String, ItemStack> ICONS = new HashMap<>();
	private static List<Mob> mobs;
	private static Map<String, Mob> byName;
	/** Longest names first, so "Zombie Villager" wins over "Zombie" when matching a name tag. */
	private static List<Mob> byLength;

	/** Bestiary kills per mob, from the menu plus kills counted since. */
	private static final Map<String, Long> KILLS = new LinkedHashMap<>();
	private static @Nullable Path loadedFrom;
	private static boolean dirty;
	private static boolean loggedUnknown;

	/** This session, per mob: kills and time spent killing it. */
	private static final Map<String, long[]> SESSION = new HashMap<>(); // {kills, activeMs, lastKillAt}
	private static @Nullable String current;
	private static final long IDLE_MS = 60_000;

	/** Entity id → bestiary mob it is, read from its name tag while it's alive (the tag goes when it dies). */
	private static final Int2ObjectOpenHashMap<Mob> SEEN = new Int2ObjectOpenHashMap<>();
	private static final IntOpenHashSet COUNTED = new IntOpenHashSet();
	private static final Map<String, Mob> TAG_CACHE = new HashMap<>();
	private static final Mob NONE = new Mob("", "", "", 0, new int[0], "", "");
	private static int tickCount;

	private static final Pattern MENU_NAME = Pattern.compile("^(.+?)(?: [IVXLC]+| \\d+)?$");
	private static final Pattern MENU_KILLS = Pattern.compile("(?i)^kills:?\\s*([\\d,]+)");

	// ---------------------------------------------------------------- the mob list

	public static List<Mob> mobs() {
		if (mobs == null) loadMobs();
		return mobs;
	}

	public static @Nullable Mob mob(String name) {
		if (mobs == null) loadMobs();
		return byName.get(name);
	}

	private static void loadMobs() {
		List<Mob> out = new ArrayList<>();
		try (var in = BestiaryTracker.class.getResourceAsStream("/assets/fancybars/data/bestiary.json")) {
			if (in != null) {
				JsonObject root = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
				JsonObject brackets = root.getAsJsonObject("brackets");
				JsonObject bracketSets = root.has("bracketSets") ? root.getAsJsonObject("bracketSets") : new JsonObject();
				for (Map.Entry<String, JsonElement> island : root.entrySet()) {
					if (island.getKey().startsWith("bracket") || !island.getValue().isJsonObject()) continue;
					JsonObject o = island.getValue().getAsJsonObject();
					String islandName = o.has("name") ? o.get("name").getAsString() : island.getKey();
					if (o.has("mobs")) addMobs(out, o.getAsJsonArray("mobs"), islandName, brackets, bracketSets);
					// fishing and the safari are split into sub-areas
					for (Map.Entry<String, JsonElement> sub : o.entrySet()) {
						if (sub.getValue().isJsonObject() && sub.getValue().getAsJsonObject().has("mobs")) {
							JsonObject s = sub.getValue().getAsJsonObject();
							String subName = s.has("name") ? strip(s.get("name").getAsString()) : islandName;
							addMobs(out, s.getAsJsonArray("mobs"), subName, brackets, bracketSets);
						}
					}
				}
			}
		} catch (Exception e) {
			FancyBarsMod.LOGGER.warn("[SkyblockGuiUtils] Couldn't read the bestiary list", e);
		}
		Map<String, Mob> names = new LinkedHashMap<>();
		for (Mob m : out) names.putIfAbsent(m.name(), m);
		mobs = new ArrayList<>(names.values());
		byName = names;
		byLength = new ArrayList<>(mobs);
		byLength.sort(Comparator.comparingInt((Mob m) -> m.name().length()).reversed());
	}

	private static void addMobs(List<Mob> out, JsonArray list, String island, JsonObject brackets, JsonObject bracketSets) {
		for (JsonElement el : list) {
			JsonObject m = el.getAsJsonObject();
			String raw = m.get("name").getAsString();
			String name = strip(raw);
			String color = raw.length() >= 2 && raw.charAt(0) == '§' ? raw.substring(0, 2) : "§a";
			int cap = m.has("cap") ? m.get("cap").getAsInt() : 0;
			String bracket = m.has("bracket") ? m.get("bracket").getAsString() : "1";
			JsonArray thresholds = null;
			if (m.has("bracketType") && bracketSets.has(m.get("bracketType").getAsString())) {
				JsonObject set = bracketSets.getAsJsonObject(m.get("bracketType").getAsString());
				if (set.has(bracket)) thresholds = set.getAsJsonArray(bracket);
			}
			if (thresholds == null && brackets.has(bracket)) thresholds = brackets.getAsJsonArray(bracket);
			List<Integer> tiers = new ArrayList<>();
			if (thresholds != null) {
				for (JsonElement t : thresholds) {
					int v = t.getAsInt();
					if (cap > 0 && v >= cap) {
						tiers.add(cap);
						break;
					}
					tiers.add(v);
				}
			}
			if (cap <= 0 && !tiers.isEmpty()) cap = tiers.getLast();
			String texture = m.has("texture") ? m.get("texture").getAsString() : "";
			String item = m.has("item") ? m.get("item").getAsString() : "";
			out.add(new Mob(name, color, strip(island), cap, tiers.stream().mapToInt(Integer::intValue).toArray(), texture, item));
		}
	}

	private static String strip(String s) {
		String out = ChatFormatting.stripFormatting(s);
		return out == null ? s : out.trim();
	}

	private static ItemStack makeIcon(Mob mob) {
		if (!mob.item().isEmpty()) {
			var item = BuiltInRegistries.ITEM.getOptional(Identifier.withDefaultNamespace(mob.item().toLowerCase(Locale.ROOT)));
			if (item.isPresent()) return new ItemStack(item.get());
		}
		ItemStack stack = new ItemStack(Items.PLAYER_HEAD);
		if (mob.texture().isEmpty()) return stack;
		try {
			// some textures in the repo have junk after the base64 (a signature pasted in): only the first part is used
			String texture = mob.texture().split("[\"\\s]")[0];
			JsonObject property = new JsonObject();
			property.addProperty("name", "textures");
			property.addProperty("value", texture);
			JsonArray properties = new JsonArray();
			properties.add(property);
			PropertyMap map = ExtraCodecs.PROPERTY_MAP.parse(JsonOps.INSTANCE, properties).getOrThrow();
			java.util.UUID id = java.util.UUID.nameUUIDFromBytes(texture.getBytes(StandardCharsets.UTF_8));
			stack.set(DataComponents.PROFILE, ResolvableProfile.createResolved(new GameProfile(id, "Bestiary", map)));
		} catch (Exception ignored) {
			// plain head
		}
		return stack;
	}

	// ---------------------------------------------------------------- tracking choice

	public static boolean isTracked(String name) {
		return FancyBarsConfig.get().bestiaryTracked.contains(name);
	}

	public static void setTracked(String name, boolean tracked) {
		FancyBarsConfig c = FancyBarsConfig.get();
		if (tracked) {
			c.bestiaryTracked.add(name);
			current = name;
		} else {
			c.bestiaryTracked.remove(name);
			if (name.equals(current)) current = null;
		}
		FancyBarsConfig.save();
	}

	/** Your saved bestiary kills for this mob, or null if its /be page hasn't been opened on this profile yet. */
	public static @Nullable Long kills(Mob mob) {
		return KILLS.get(mob.name());
	}

	/** "1,793 kills · IV" (or "MAX") for the mob list, or null when the kills aren't known. */
	public static @Nullable Component killsText(Mob mob) {
		Long kills = KILLS.get(mob.name());
		if (kills == null) return null;
		int tier = mob.tier(kills);
		return Component.literal(comma(kills) + " kills ").withStyle(ChatFormatting.WHITE)
				.append(Component.literal(tier >= mob.tiers().length ? "MAX" : roman(tier)).withStyle(tier >= mob.tiers().length ? ChatFormatting.GOLD : ChatFormatting.GRAY));
	}

	public static int trackedCount() {
		return FancyBarsConfig.get().bestiaryTracked.size();
	}

	// ---------------------------------------------------------------- init / ticking

	public static void init() {
		ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> {
			if (screen instanceof AbstractContainerScreen<?> container) {
				ScreenEvents.afterExtract(screen).register((s, graphics, mouseX, mouseY, delta) -> readMenu(container));
			}
		});
		dev.fancybars.util.Perf.onTick("Tick Bestiary", mc -> tick());
		dev.fancybars.util.Perf.attachElementAfter(VanillaHudElements.HOTBAR, FancyBarsMod.id("bestiary_tracker"), (graphics, delta) -> {
			if (!shouldShow()) return;
			View view = view();
			if (view == null) return;
			float[] pos = position(graphics.guiWidth(), graphics.guiHeight());
			renderAt(graphics, pos[0], pos[1], FancyBarsConfig.get().bestiaryScale, view, 1);
		});
	}

	public static boolean shouldShow() {
		return FancyBarsConfig.get().bestiaryEnabled && SkyblockLocation.isOnSkyblock() && trackedCount() > 0
				&& dev.fancybars.util.HudVisibility.allowed();
	}

	private static void tick() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || mc.level == null || !SkyblockLocation.isOnSkyblock()) return;
		Path dir = BackpackPreview.profileDir();
		if (dir != null && !dir.equals(loadedFrom)) {
			loadedFrom = dir;
			if (!dirty) load(dir);
		}
		if (dirty && dir != null && mc.player.tickCount % 100 == 0) save(dir);
		long now = System.currentTimeMillis();
		for (long[] s : SESSION.values()) if (now - s[2] < IDLE_MS) s[1] += 50;
		if (trackedCount() == 0 || !FancyBarsConfig.get().bestiaryEnabled) return;
		settlePending();
		if (mc.player.tickCount % 20 == 0) readTab(mc);
		tickCount++;
		boolean name = tickCount % 4 == 0;
		List<ArmorStand> tags = null;
		if (name) {
			tags = new ArrayList<>();
			for (Entity e : mc.level.entitiesForRendering()) {
				if (e instanceof ArmorStand stand && stand.hasCustomName() && e.distanceToSqr(mc.player) < 48 * 48) tags.add(stand);
			}
		}
		for (Entity e : mc.level.entitiesForRendering()) {
			if (!(e instanceof LivingEntity living) || e instanceof ArmorStand || e == mc.player) continue;
			if (e.distanceToSqr(mc.player) > 40 * 40) continue;
			int id = e.getId();
			if (living.isDeadOrDying() || living.deathTime > 0) {
				if (COUNTED.contains(id)) continue;
				Mob mob = SEEN.get(id);
				if (mob == null && tags != null) mob = identify(living, tags);
				// not known yet and its tag can't be looked at this tick: try again next time
				if (mob == null && tags == null) continue;
				COUNTED.add(id);
				if (mob != null && mob != NONE && isTracked(mob.name())) died(mob);
				continue;
			}
			if (name) {
				Mob mob = identify(living, tags);
				if (mob != null) SEEN.put(id, mob);
			}
		}
		if (tickCount % 200 == 0) {
			// forget entities that are gone
			IntOpenHashSet alive = new IntOpenHashSet();
			for (Entity e : mc.level.entitiesForRendering()) alive.add(e.getId());
			SEEN.keySet().removeIf(id -> !alive.contains(id));
			COUNTED.removeIf(id -> !alive.contains(id));
			if (TAG_CACHE.size() > 2000) TAG_CACHE.clear();
		}
	}

	/**
	 * The server removed an entity. Hypixel often removes a mob the moment it dies without playing the death animation
	 * (ghosts, for one), so a tracked mob vanishing close to you counts as a kill.
	 */
	public static void onRemoved(Entity e) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || !(e instanceof LivingEntity) || e instanceof ArmorStand || e == mc.player) return;
		if (!FancyBarsConfig.get().bestiaryEnabled || trackedCount() == 0 || !SkyblockLocation.isOnSkyblock()) return;
		int id = e.getId();
		if (COUNTED.contains(id)) return;
		Mob mob = SEEN.get(id);
		if (mob == null || mob == NONE || e.distanceToSqr(mc.player) > 24 * 24) return;
		COUNTED.add(id);
		if (isTracked(mob.name())) died(mob);
	}

	/** /sgu bestiarydebug: what's around you and what it's recognised as. */
	public static void debug() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || mc.level == null) return;
		List<ArmorStand> tags = new ArrayList<>();
		for (Entity e : mc.level.entitiesForRendering()) {
			if (e instanceof ArmorStand stand && stand.hasCustomName() && e.distanceToSqr(mc.player) < 48 * 48) tags.add(stand);
		}
		StringBuilder log = new StringBuilder("[SkyblockGuiUtils] Bestiary debug. Tracked: " + FancyBarsConfig.get().bestiaryTracked
				+ ", seen " + SEEN.size() + ", counted " + COUNTED.size() + "\nName tags:");
		for (ArmorStand t : tags) log.append("\n  ").append(t.getCustomName().getString()).append(" @ ").append(t.blockPosition().toShortString());
		log.append("\nMobs:");
		int shown = 0;
		for (Entity e : mc.level.entitiesForRendering()) {
			if (!(e instanceof LivingEntity living) || e instanceof ArmorStand || e == mc.player || e.distanceToSqr(mc.player) > 24 * 24) continue;
			Mob mob = identify(living, tags);
			String what = mob == null ? "no tag" : mob == NONE ? "not a bestiary mob" : mob.name();
			log.append("\n  ").append(e.getType().toShortString()).append(" @ ").append(e.blockPosition().toShortString())
					.append(e.hasCustomName() ? " name=" + e.getCustomName().getString() : "").append(" -> ").append(what);
			if (shown++ < 8) {
				mc.player.sendSystemMessage(Theme.chatPrefix().append(Component.literal(e.getType().toShortString() + " → " + what).withStyle(ChatFormatting.GRAY)));
			}
		}
		FancyBarsMod.LOGGER.info(log.toString());
		mc.player.sendSystemMessage(Theme.chatPrefix().append(Component.literal("Full list written to logs/latest.log").withStyle(ChatFormatting.DARK_GRAY)));
	}

	/** Which bestiary mob this is, from its own name or the name tag floating above it; NONE if it isn't one. */
	private static @Nullable Mob identify(LivingEntity e, List<ArmorStand> tags) {
		// ghosts have no name tag at all: in the Mist, every creeper is a ghost
		if (e instanceof net.minecraft.world.entity.monster.Creeper && dev.fancybars.mining.Ghosts.inMist()) {
			Mob ghost = mob("Ghost");
			if (ghost != null) return ghost;
		}
		if (e.hasCustomName()) {
			Mob m = fromTag(e.getCustomName().getString());
			if (m != NONE) return m;
		}
		double half = Math.max(0.6, e.getBbWidth() / 2 + 0.3);
		ArmorStand best = null;
		double bestDy = Double.MAX_VALUE;
		for (ArmorStand tag : tags) {
			double dx = tag.getX() - e.getX(), dz = tag.getZ() - e.getZ(), dy = tag.getY() - e.getY();
			if (Math.abs(dx) > half || Math.abs(dz) > half || dy < -0.5 || dy > e.getBbHeight() + 3) continue;
			String text = tag.getCustomName().getString();
			if (text.indexOf('❤') < 0) continue;
			if (Math.abs(dy - e.getBbHeight()) < bestDy) {
				bestDy = Math.abs(dy - e.getBbHeight());
				best = tag;
			}
		}
		if (best == null) return null;
		return fromTag(best.getCustomName().getString());
	}

	/** "[Lv15] Zombie 100/100❤" → Zombie. */
	private static Mob fromTag(String raw) {
		Mob cached = TAG_CACHE.get(raw);
		if (cached != null) return cached;
		String text = strip(raw);
		Mob found = NONE;
		if (text.indexOf('❤') >= 0) {
			if (mobs == null) loadMobs();
			for (Mob m : byLength) {
				int i = text.indexOf(m.name());
				if (i < 0) continue;
				// a whole word: "Ghost" but not "Ghostly ..."
				int end = i + m.name().length();
				boolean before = i == 0 || !Character.isLetter(text.charAt(i - 1));
				boolean after = end >= text.length() || !Character.isLetter(text.charAt(end));
				if (before && after) {
					found = m;
					break;
				}
			}
		}
		TAG_CACHE.put(raw, found);
		return found;
	}

	// ---------------------------------------------------------------- only your own kills

	/** A tracked mob that died near you, waiting to see if it gave you Combat XP (only your kills do). */
	private record Pending(Mob mob, long at) {}

	private static final List<Pending> PENDING = new ArrayList<>();
	/** When your Combat XP went up, the last few seconds. */
	private static final java.util.ArrayDeque<Long> XP_GAINS = new java.util.ArrayDeque<>();
	private static String lastXpText = "";
	private static final Pattern COMBAT_XP = Pattern.compile("\\+[\\d,.]+ Combat \\([^)]*\\)");
	/** How far apart a death and the XP for it can be. */
	private static final long XP_WINDOW = 2000;

	/** Action bar text: "+12.5 Combat (1,234/5,000)" means you just killed something. */
	public static void onActionBar(String raw) {
		String text = ChatFormatting.stripFormatting(raw);
		if (text == null) return;
		Matcher m = COMBAT_XP.matcher(text);
		if (!m.find()) return;
		// Hypixel shows the same text for a few seconds: only a change is a new kill
		if (m.group().equals(lastXpText)) return;
		lastXpText = m.group();
		XP_GAINS.addLast(System.currentTimeMillis());
		while (XP_GAINS.size() > 20) XP_GAINS.removeFirst();
	}

	private static void died(Mob mob) {
		PENDING.add(new Pending(mob, System.currentTimeMillis()));
	}

	/** Deaths that came with Combat XP were yours; ones that didn't (someone else's kill) are dropped. */
	private static void settlePending() {
		if (PENDING.isEmpty()) return;
		long now = System.currentTimeMillis();
		PENDING.removeIf(p -> {
			for (long gain : XP_GAINS) {
				if (gain >= p.at() - XP_WINDOW && gain <= p.at() + XP_WINDOW) {
					kill(p.mob());
					return true;
				}
			}
			return now - p.at() > XP_WINDOW + 500;
		});
	}

	// ---------------------------------------------------------------- the tab list's Bestiary widget

	/** "Ghost 18: 19,771/21,500" (also "Ghost XVIII: ...", or "MAX"). */
	private static final Pattern WIDGET_LINE = Pattern.compile("^(.+?) (\\d+|[IVXLC]+): (MAX|[\\d,]+)(?:/([\\d,]+))?$");
	/** Mob → when the widget last showed it exactly; while it does, it's the source of truth for that mob. */
	private static final Map<String, Long> WIDGET_SEEN = new HashMap<>();
	private static final Map<String, Long> WIDGET_LAST = new HashMap<>();

	private static boolean widgetActive(Mob mob) {
		Long at = WIDGET_SEEN.get(mob.name());
		return at != null && System.currentTimeMillis() - at < 5000;
	}

	/**
	 * Hypixel's tab list can show a "Bestiary:" widget (turn it on in /tab) with your exact kills for the mobs around
	 * you. When it's there, kills are taken straight from it, so nobody else's kills can count.
	 */
	private static void readTab(Minecraft mc) {
		if (mc.getConnection() == null) return;
		List<net.minecraft.client.multiplayer.PlayerInfo> infos = new ArrayList<>(mc.getConnection().getOnlinePlayers());
		infos.sort(Comparator.comparing(info -> info.getProfile().name()));
		boolean inSection = false;
		long now = System.currentTimeMillis();
		for (var info : infos) {
			if (info.getTabListDisplayName() == null) continue;
			String line = strip(info.getTabListDisplayName().getString());
			if (line.equals("Bestiary:")) {
				inSection = true;
				continue;
			}
			if (!inSection) continue;
			if (line.isEmpty() || line.endsWith(":") && !line.contains(" ")) break;
			Matcher m = WIDGET_LINE.matcher(line);
			if (!m.matches()) continue;
			Mob mob = mob(m.group(1).trim());
			if (mob == null) continue;
			if (m.group(3).equals("MAX")) {
				// maxed: the widget stops counting, so kills are counted here (by your Combat XP) from now on
				maxedFromWidget(mob, mob.cap());
				continue;
			}
			long shown = Long.parseLong(m.group(3).replace(",", ""));
			long kills = shown;
			if (m.group(4) != null) {
				// the numbers may be your total, or just your progress through this tier: tell by what it's out of
				long outOf = Long.parseLong(m.group(4).replace(",", ""));
				int tier = mob.tier(shown);
				boolean total = tier < mob.tiers().length && mob.tiers()[tier] == outOf;
				if (!total) {
					int t = 0;
					while (t < mob.tiers().length && mob.tiers()[t] - (t == 0 ? 0 : mob.tiers()[t - 1]) != outOf) t++;
					if (t >= mob.tiers().length) continue; // can't tell: leave it
					kills = (t == 0 ? 0 : mob.tiers()[t - 1]) + shown;
				}
			}
			if (mob.cap() > 0 && kills >= mob.cap()) {
				// the widget's number stops at the cap: past it, kills are counted here instead
				maxedFromWidget(mob, kills);
				continue;
			}
			WIDGET_SEEN.put(mob.name(), now);
			Long before = WIDGET_LAST.put(mob.name(), kills);
			if (before != null && kills > before && kills - before < 500) {
				long[] s = SESSION.computeIfAbsent(mob.name(), _ -> new long[3]);
				s[0] += kills - before;
				s[2] = now;
				current = mob.name();
			}
			Long old = KILLS.get(mob.name());
			if (old == null || old != kills) {
				KILLS.put(mob.name(), kills);
				dirty = true;
			}
		}
	}

	/** A maxed mob in the widget: never let the widget own it, and never lower kills counted past the cap. */
	private static void maxedFromWidget(Mob mob, long shown) {
		WIDGET_SEEN.remove(mob.name());
		WIDGET_LAST.remove(mob.name());
		Long old = KILLS.get(mob.name());
		if (old == null || old < shown) {
			KILLS.put(mob.name(), shown);
			dirty = true;
		}
	}

	private static void kill(Mob mob) {
		// the tab widget is counting this one exactly
		if (widgetActive(mob)) return;
		long now = System.currentTimeMillis();
		Long old = KILLS.get(mob.name());
		if (old != null) {
			KILLS.put(mob.name(), old + 1);
			dirty = true;
		}
		long[] s = SESSION.computeIfAbsent(mob.name(), _ -> new long[3]);
		s[0]++;
		s[2] = now;
		current = mob.name();
	}

	public static void resetSession() {
		SESSION.clear();
	}

	// ---------------------------------------------------------------- reading /bestiary

	private static void readMenu(AbstractContainerScreen<?> screen) {
		String title = strip(screen.getTitle().getString());
		if (!title.contains("Bestiary")) return;
		boolean anyMob = false, anyKills = false;
		for (Slot slot : screen.getMenu().slots) {
			if (slot.container instanceof Inventory) continue;
			ItemStack stack = slot.getItem();
			if (stack.isEmpty()) continue;
			Matcher nm = MENU_NAME.matcher(strip(stack.getHoverName().getString()));
			if (!nm.matches()) continue;
			Mob mob = mob(nm.group(1).trim());
			if (mob == null) continue;
			anyMob = true;
			ItemLore lore = stack.get(DataComponents.LORE);
			if (lore == null) continue;
			for (Component line : lore.lines()) {
				Matcher m = MENU_KILLS.matcher(strip(line.getString()));
				if (!m.find()) continue;
				long kills = Long.parseLong(m.group(1).replace(",", ""));
				anyKills = true;
				Long old = KILLS.get(mob.name());
				// a maxed mob's count may stop at the cap here too: keep kills counted past it
				boolean cappedCopy = mob.cap() > 0 && kills <= mob.cap() && old != null && old > kills && kills >= mob.cap();
				if ((old == null || old != kills) && !cappedCopy) {
					KILLS.put(mob.name(), kills);
					dirty = true;
				}
				break;
			}
		}
		if (anyMob && !anyKills && !loggedUnknown) {
			loggedUnknown = true;
			StringBuilder sb = new StringBuilder("[SkyblockGuiUtils] Bestiary menu: couldn't find kill counts. Items:");
			for (Slot slot : screen.getMenu().slots) {
				if (slot.container instanceof Inventory || slot.getItem().isEmpty()) continue;
				sb.append("\n  ").append(slot.getItem().getHoverName().getString());
				ItemLore lore = slot.getItem().get(DataComponents.LORE);
				if (lore != null) for (Component l : lore.lines()) sb.append("\n      ").append(l.getString());
			}
			FancyBarsMod.LOGGER.info(sb.toString());
		}
	}

	// ---------------------------------------------------------------- HUD

	/** What the HUD shows: the mob being killed (big, with its picture) and the other tracked mobs underneath. */
	public record View(Mob main, List<Mob> others) {}

	public static @Nullable View view() {
		List<Mob> tracked = new ArrayList<>();
		for (String n : FancyBarsConfig.get().bestiaryTracked) {
			Mob m = mob(n);
			if (m != null) tracked.add(m);
		}
		if (tracked.isEmpty()) return null;
		Mob main = current != null && isTracked(current) ? mob(current) : null;
		if (main == null) main = tracked.getFirst();
		tracked.remove(main);
		return new View(main, tracked);
	}

	/** A sample for the HUD editor. */
	public static View shownView() {
		View v = view();
		if (v != null) return v;
		Mob ghost = mob("Ghost");
		if (ghost == null) ghost = mobs().getFirst();
		return new View(ghost, List.of());
	}

	private static final int PAD = 4;
	private static final int HEAD = 24;
	private static final int BAR_H = 10;
	private static final int LINE = 10;
	private static final int SMALL_ICON = 10;

	private static Component title(Mob mob) {
		Long kills = KILLS.get(mob.name());
		var title = Component.literal(mob.color() + mob.name()).withStyle(ChatFormatting.BOLD);
		if (kills != null) {
			int tier = mob.tier(kills);
			title.append(Component.literal(tier >= mob.tiers().length ? " MAX" : " " + roman(tier)).withStyle(ChatFormatting.GRAY));
		}
		return title;
	}

	private static Component progressText(Mob mob) {
		Long kills = KILLS.get(mob.name());
		if (kills == null) {
			long session = session(mob)[0];
			return Component.literal(comma(session) + " kills ").withStyle(ChatFormatting.WHITE)
					.append(Component.literal("(open its /be page)").withStyle(ChatFormatting.DARK_GRAY));
		}
		int tier = mob.tier(kills);
		if (tier >= mob.tiers().length) return Component.literal(comma(kills) + " kills").withStyle(ChatFormatting.GOLD);
		return Component.literal(comma(kills)).withStyle(ChatFormatting.WHITE)
				.append(Component.literal("/" + comma(mob.tiers()[tier])).withStyle(ChatFormatting.GRAY));
	}

	/** 0..1 through the current tier, or -1 when the kills aren't known. */
	private static float progress(Mob mob) {
		Long kills = KILLS.get(mob.name());
		if (kills == null) return -1;
		int tier = mob.tier(kills);
		if (tier >= mob.tiers().length) return 1;
		int prev = tier == 0 ? 0 : mob.tiers()[tier - 1];
		int next = mob.tiers()[tier];
		return next <= prev ? 1 : Math.clamp((kills - prev) / (float) (next - prev), 0, 1);
	}

	private static long[] session(Mob mob) {
		long[] s = SESSION.get(mob.name());
		return s == null ? new long[3] : s;
	}

	private static long perHour(Mob mob) {
		long[] s = session(mob);
		if (s[0] == 0 || s[1] < 5000) return 0;
		return Math.round(s[0] / (s[1] / 3_600_000.0));
	}

	private static Component rateText(Mob mob) {
		long[] s = session(mob);
		return Component.literal("Kills/hour: ").withStyle(ChatFormatting.YELLOW)
				.append(Component.literal(comma(perHour(mob))).withStyle(ChatFormatting.WHITE))
				.append(Component.literal("  Session: ").withStyle(ChatFormatting.YELLOW))
				.append(Component.literal(comma(s[0])).withStyle(ChatFormatting.WHITE));
	}

	/** "Next tier: 1h 23m  Max: 2d 4h" at the current kills per hour. */
	private static Component etaText(Mob mob) {
		Long kills = KILLS.get(mob.name());
		var line = Component.literal("Next tier: ").withStyle(ChatFormatting.YELLOW);
		if (kills != null && mob.tier(kills) >= mob.tiers().length) {
			return line.append(Component.literal("Maxed!").withStyle(ChatFormatting.GOLD));
		}
		long rate = perHour(mob);
		if (kills == null || rate <= 0) {
			return line.append(Component.literal(kills == null ? "open /be" : "start killing").withStyle(ChatFormatting.DARK_GRAY));
		}
		long toNext = mob.tiers()[mob.tier(kills)] - kills;
		long toMax = mob.cap() - kills;
		line.append(Component.literal(eta(toNext, rate)).withStyle(ChatFormatting.WHITE));
		if (toMax > toNext) {
			line.append(Component.literal("  Max: ").withStyle(ChatFormatting.YELLOW))
					.append(Component.literal(eta(toMax, rate)).withStyle(ChatFormatting.WHITE));
		}
		return line;
	}

	/** How long {@code kills} more take at {@code perHour}: "45s", "12m", "3h 20m", "2d 4h". */
	private static String eta(long kills, long perHour) {
		long s = Math.max(1, Math.round(kills * 3600.0 / perHour));
		long d = s / 86400, h = s % 86400 / 3600, m = s % 3600 / 60;
		if (d > 0) return d + "d " + h + "h";
		if (h > 0) return h + "h " + m + "m";
		if (m > 0) return m + "m";
		return s + "s";
	}

	private static Component otherLine(Mob mob) {
		Long kills = KILLS.get(mob.name());
		var line = Component.literal(mob.color() + mob.name() + " ");
		if (kills != null) {
			float p = progress(mob);
			line.append(Component.literal(mob.tier(kills) >= mob.tiers().length ? "MAX" : Math.round(p * 100) + "%").withStyle(ChatFormatting.GRAY));
		} else {
			line.append(Component.literal(comma(session(mob)[0])).withStyle(ChatFormatting.GRAY));
		}
		long rate = perHour(mob);
		if (rate > 0) line.append(Component.literal(" " + comma(rate) + "/h").withStyle(ChatFormatting.DARK_GRAY));
		return line;
	}

	/**
	 * Everything the HUD draws, worked out at most once a second (or straight away when the mob shown changes) instead
	 * of every frame.
	 */
	private record Layout(View view, Component title, Component progressText, Component rate, Component eta, List<Component> others,
			float progress, boolean maxed, @Nullable String percent, int w, int h) {}

	private static @Nullable Layout layout;
	private static long layoutAt;
	/** The widest the box has been for this mob: it only grows, so changing numbers don't make it jump around. */
	private static int stickyW;
	private static @Nullable String stickyFor;

	private static Layout layout(View view) {
		long now = System.currentTimeMillis();
		Layout l = layout;
		if (l != null && l.view().equals(view) && now - layoutAt < 1000) return l;
		Font font = Minecraft.getInstance().font;
		Mob main = view.main();
		Component title = title(main);
		Component progress = progressText(main);
		Component rate = rateText(main);
		Component eta = etaText(main);
		List<Component> others = new ArrayList<>();
		for (Mob m : view.others()) others.add(otherLine(m));
		// room for numbers to grow (up to 99,999 kills/hour) without the box changing size
		int rateW = Math.max(font.width(rate), font.width("Kills/hour: 00,000  Session: 00,000"));
		int w = HEAD + 4 + Math.max(font.width(title), font.width(progress));
		w = Math.max(w, rateW);
		w = Math.max(w, Math.max(font.width(eta), font.width("Next tier: 00h 00m  Max: 00d 00h")));
		for (Component c : others) w = Math.max(w, SMALL_ICON + 3 + font.width(c) + font.width(" 00,000/h"));
		w += PAD * 2;
		if (!main.name().equals(stickyFor)) {
			stickyFor = main.name();
			stickyW = 0;
		}
		stickyW = Math.max(stickyW, w);
		int h = Math.max(HEAD, LINE * 2) + 3 + BAR_H + 3 + LINE + 2 + LINE + others.size() * (LINE + 2) + PAD * 2 - 1;
		Long kills = KILLS.get(main.name());
		boolean maxed = kills != null && main.tier(kills) >= main.tiers().length;
		float p = progress(main);
		// "62.4%" on the bar (MAX when maxed, nothing when the kills aren't known yet)
		String percent = p < 0 ? null : maxed ? "MAX" : String.format(Locale.ROOT, "%.1f%%", Math.min(p * 100, 99.9f));
		l = new Layout(view, title, progress, rate, eta, others, p, maxed, percent, stickyW, h);
		layout = l;
		layoutAt = now;
		return l;
	}

	public static float[] size(View view) {
		Layout l = layout(view);
		return new float[]{l.w(), l.h()};
	}

	public static float[] position(int screenW, int screenH) {
		FancyBarsConfig c = FancyBarsConfig.get();
		if (c.bestiaryX < 0 || c.bestiaryY < 0) return new float[]{6, Math.round(screenH * 0.12f)};
		return new float[]{c.bestiaryX * screenW, c.bestiaryY * screenH};
	}

	public static void renderAt(GuiGraphicsExtractor graphics, float x, float y, float scale, View view, float alpha) {
		Font font = Minecraft.getInstance().font;
		Layout l = layout(view);
		int a = Math.round(alpha * 255);
		int white = (a << 24) | 0xFFFFFF;
		Matrix3x2fStack pose = graphics.pose();
		pose.pushMatrix();
		pose.translate(x, y);
		pose.scale(scale, scale);
		int w = l.w();
		if (FancyBarsConfig.get().bestiaryBackground) Theme.hudPanel(graphics, 0, 0, w, l.h(), 4, 0.88f, a);
		Mob main = view.main();
		// the mob's picture in the top left
		pose.pushMatrix();
		pose.translate(PAD, PAD);
		pose.scale(HEAD / 16f, HEAD / 16f);
		graphics.item(main.icon(), 0, 0);
		pose.popMatrix();
		int tx = PAD + HEAD + 4;
		int ty = PAD + Math.max(0, (HEAD - LINE * 2) / 2) + 1;
		graphics.text(font, l.title(), tx, ty, white, true);
		graphics.text(font, l.progressText(), tx, ty + LINE, white, true);
		// progress bar across the full width
		int by = PAD + Math.max(HEAD, LINE * 2) + 3;
		int bw = w - PAD * 2;
		Theme.roundedRect(graphics, PAD, by, bw, BAR_H, 3, (Math.round(a * 0.55f) << 24) | 0x101014);
		float p = l.progress();
		if (p > 0) {
			int fill = Math.max(6, Math.round(bw * p));
			Theme.roundedRect(graphics, PAD, by, fill, BAR_H, 3, (a << 24) | (l.maxed() ? 0xFFAA00 : 0x3FBF3F));
		}
		if (l.percent() != null) {
			// centred on the bar, with a shadow so it reads over both the filled and empty parts
			int pw = font.width(l.percent());
			graphics.text(font, l.percent(), PAD + (bw - pw) / 2, by + 1, white, true);
		}
		int ly = by + BAR_H + 3;
		graphics.text(font, l.rate(), PAD, ly, white, true);
		ly += LINE + 2;
		graphics.text(font, l.eta(), PAD, ly, white, true);
		ly += LINE + 2;
		for (int i = 0; i < view.others().size(); i++) {
			pose.pushMatrix();
			pose.translate(PAD, ly - 1);
			pose.scale(SMALL_ICON / 16f, SMALL_ICON / 16f);
			graphics.item(view.others().get(i).icon(), 0, 0);
			pose.popMatrix();
			graphics.text(font, l.others().get(i), PAD + SMALL_ICON + 3, ly, white, true);
			ly += LINE + 2;
		}
		pose.popMatrix();
	}

	private static String comma(long v) {
		return String.format(Locale.ROOT, "%,d", v);
	}

	private static String roman(int n) {
		if (n <= 0) return "0";
		int[] values = {50, 40, 10, 9, 5, 4, 1};
		String[] symbols = {"L", "XL", "X", "IX", "V", "IV", "I"};
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < values.length; i++) while (n >= values[i]) {
			sb.append(symbols[i]);
			n -= values[i];
		}
		return sb.toString();
	}

	// ---------------------------------------------------------------- saving (per profile)

	private static final String FILE = "bestiary.json";

	private static void save(Path dir) {
		dirty = false;
		Map<String, Long> copy = new LinkedHashMap<>(KILLS);
		CompletableFuture.runAsync(() -> {
			try {
				Files.createDirectories(dir);
				try (Writer w = Files.newBufferedWriter(dir.resolve(FILE), StandardCharsets.UTF_8)) {
					FancyBarsMod.GSON.toJson(copy, w);
				}
			} catch (Exception e) {
				FancyBarsMod.LOGGER.warn("[SkyblockGuiUtils] Couldn't save bestiary kills", e);
			}
		}, FancyBarsMod.VIRTUAL_THREAD_EXECUTOR);
	}

	private static void load(Path dir) {
		KILLS.clear();
		Path file = dir.resolve(FILE);
		if (!Files.isRegularFile(file)) return;
		try (Reader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
			Map<String, Long> read = FancyBarsMod.GSON.fromJson(r, new TypeToken<Map<String, Long>>() {}.getType());
			if (read != null) KILLS.putAll(read);
		} catch (Exception e) {
			FancyBarsMod.LOGGER.warn("[SkyblockGuiUtils] Couldn't read saved bestiary kills", e);
		}
	}
}
