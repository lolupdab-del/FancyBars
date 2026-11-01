package dev.fancybars.hud;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.joml.Matrix3x2fStack;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.Identifier;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;

import dev.fancybars.FancyBarsMod;
import dev.fancybars.config.FancyBarsConfig;
import dev.fancybars.util.SkyblockLocation;
import dev.fancybars.util.gui.Theme;

/**
 * Current pet display, modelled on SkyHanni's Pet Display (https://github.com/hannibal002/SkyHanni): your pet's icon
 * on a rarity-coloured circle with an XP progress ring around it, plus its name, level, XP and held item.
 * <p>
 * Data comes from the Pet widget in the tab list (turn it on in Hypixel's /widget menu), from chat (summon, despawn,
 * Autopet, level ups, held items) and from the /pets menu, which is also where the pet's head icon is learned.
 */
public final class PetHud {
	private static final Pattern TAB_NAME = Pattern.compile("^\\[Lvl (?<level>[\\d,]+)] (?<pet>.+?)(?: [✦⚘].*)?$");
	private static final Pattern TAB_XP = Pattern.compile("^\\+?(?<current>[\\d,.kKmMbB]+)(?:/(?<next>[\\d,.kKmMbB]+))? XP(?: \\((?<percent>[\\d.]+)%\\))?$");
	private static final Pattern SUMMON = Pattern.compile("^You summoned your (?<pet>.+?)!$");
	private static final Pattern DESPAWN = Pattern.compile("^You despawned your (?<pet>.+?)!$");
	private static final Pattern AUTOPET = Pattern.compile("^Autopet equipped your \\[Lvl (?<level>\\d+)] (?<pet>.+?)! VIEW RULE.*$");
	private static final Pattern LEVEL_UP = Pattern.compile("^Your (?<pet>.+?) leveled up to level (?<level>\\d+)!$");
	private static final Pattern HOLDING = Pattern.compile("^Your pet is now holding (?<item>.+?)\\.$");
	private static final Pattern REMOVED = Pattern.compile("^You removed (?<item>.+?) from your pet!$");
	private static final Pattern MENU_TITLE = Pattern.compile("^(?:\\(\\d+/\\d+\\) )?Pets(?:: \".*\")?(?: \\(\\d+/\\d+\\))?.*$");
	private static final Pattern MENU_NAME = Pattern.compile("^\\[Lvl (?<level>\\d+)] (?:\\[\\d+✦] )?(?<pet>.+?)(?: ✦)?$");
	private static final Pattern MENU_HELD = Pattern.compile("^Held Item: (?<item>.+)$");
	private static final Pattern MENU_PROGRESS = Pattern.compile("^Progress to Level (?<next>\\d+): (?<percent>[\\d.]+)%$");

	private static final int PAD = 5;
	private static final int ICON = 30;

	/** What we know about the equipped pet. */
	private static String name = "";
	private static int level = -1;
	private static int rarityColor = 0xFFAA00;
	private static double xpCurrent = -1;
	private static double xpNext = -1;
	private static float progress = -1;
	private static boolean maxLevel;
	private static String heldItem = "";
	private static long lastTabRead;

	/** Pet name (lowercase) → its head from the /pets menu. */
	private static final Map<String, ItemStack> ICONS = new HashMap<>();
	private static boolean iconsLoaded;
	private static boolean iconsDirty;

	private PetHud() {}

	public static void init() {
		HudElementRegistry.attachElementAfter(VanillaHudElements.HOTBAR, FancyBarsMod.id("pet_display"), (graphics, tickCounter) -> {
			Minecraft mc = Minecraft.getInstance();
			if (FancyBarsConfig.get().petEnabled && mc.player != null && SkyblockLocation.isOnSkyblock()
					&& !name.isEmpty() && dev.fancybars.util.HudVisibility.allowed()) {
				float[] pos = position(graphics.guiWidth(), graphics.guiHeight());
				renderAt(graphics, pos[0], pos[1], FancyBarsConfig.get().petScale, 1, false);
			}
		});
		ClientTickEvents.END_CLIENT_TICK.register(mc -> {
			if (!FancyBarsConfig.get().petEnabled || mc.player == null) return;
			if (!iconsLoaded && mc.level != null) loadIcons();
			long now = System.currentTimeMillis();
			if (now - lastTabRead > 1000 && SkyblockLocation.isOnSkyblock()) {
				lastTabRead = now;
				readTabList();
			}
			if (mc.gui.screen() instanceof AbstractContainerScreen<?> screen && MENU_TITLE.matcher(screen.getTitle().getString()).matches()) {
				readPetsMenu(screen);
				Slot hovered = ((dev.fancybars.mixin.ContainerScreenAccessor) screen).fancybars$getHoveredSlot();
				if (hovered != null && hovered.getItem().is(Items.PLAYER_HEAD)) {
					String hover = ChatFormatting.stripFormatting(hovered.getItem().getHoverName().getString());
					Matcher hm = hover == null ? null : MENU_NAME.matcher(hover.trim());
					if (hm != null && hm.matches()) {
						hoveredPet = hm.group("pet").trim();
						hoveredHead = headOf(hovered.getItem());
						hoveredSkin = skinColorOf(hovered.getItem().getHoverName());
						hoveredAt = System.currentTimeMillis();
					}
				}
			}
			if (iconsDirty && now % 5000 < 60) saveIcons();
		});
		ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
			if (overlay) return;
			String text = ChatFormatting.stripFormatting(message.getString());
			if (text != null) onChat(text.trim(), message);
		});
	}

	// ---------------------------------------------------------------- data

	private static void setPet(String pet, int lvl, int color) {
		if (!pet.equalsIgnoreCase(name)) {
			xpCurrent = -1;
			xpNext = -1;
			progress = -1;
			maxLevel = false;
		}
		name = pet;
		if (lvl > 0) level = lvl;
		if (color != -1) rarityColor = color;
	}

	/**
	 * Chat says which pet you switched to instantly, but the tab list keeps showing the old one for a few seconds;
	 * hold the new pet until the tab list catches up so the display doesn't flick between the two.
	 */
	private static final dev.fancybars.util.Held<String> HELD_PET = new dev.fancybars.util.Held<>(15_000, String::equalsIgnoreCase);

	private static void onChat(String text, Component message) {
		Matcher m;
		if ((m = AUTOPET.matcher(text)).matches()) {
			setPet(m.group("pet"), Integer.parseInt(m.group("level")), colorOf(message, m.group("pet")));
			HELD_PET.set(m.group("pet"));
			heldItem = "";
			switchedTo(m.group("pet"));
			if (skinColorOf(message) != -1) skinColor = skinColorOf(message);
		} else if ((m = SUMMON.matcher(text)).matches()) {
			setPet(m.group("pet"), -1, colorOf(message, m.group("pet")));
			HELD_PET.set(m.group("pet"));
			heldItem = "";
			switchedTo(m.group("pet"));
		} else if ((m = DESPAWN.matcher(text)).matches()) {
			name = "";
			level = -1;
			HELD_PET.set("");
		} else if ((m = LEVEL_UP.matcher(text)).matches() && m.group("pet").equalsIgnoreCase(name)) {
			level = Integer.parseInt(m.group("level"));
		} else if ((m = HOLDING.matcher(text)).matches()) {
			heldItem = m.group("item");
		} else if (REMOVED.matcher(text).matches()) {
			heldItem = "";
		}
	}

	private static void readTabList() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.getConnection() == null) return;
		List<PlayerInfo> infos = new ArrayList<>(mc.getConnection().getOnlinePlayers());
		infos.sort(Comparator.comparing(info -> info.getProfile().name()));
		boolean inSection = false;
		int line = 0;
		for (PlayerInfo info : infos) {
			Component display = info.getTabListDisplayName();
			if (display == null) continue;
			String raw = ChatFormatting.stripFormatting(display.getString());
			if (raw == null) continue;
			String text = raw.trim();
			if (text.equals("Pet:")) {
				inSection = true;
				line = 0;
				continue;
			}
			if (!inSection) continue;
			if (text.isEmpty() || text.endsWith(":") && !text.contains(" ")) break;
			line++;
			Matcher m;
			if (line == 1) {
				boolean none = text.equalsIgnoreCase("No pet selected") || text.equalsIgnoreCase("None");
				m = TAB_NAME.matcher(text);
				String tabPet = none ? "" : m.matches() ? m.group("pet").trim() : null;
				if (tabPet == null) continue;
				// still showing the pet from before you switched: ignore the whole widget until it updates
				if (!HELD_PET.filter(tabPet).equalsIgnoreCase(tabPet)) return;
				if (none) {
					name = "";
					return;
				}
				setPet(tabPet, parseInt(m.group("level")), colorOf(display, tabPet));
				skinColor = skinColorOf(display);
			} else if (text.equals("MAX LEVEL")) {
				maxLevel = true;
				progress = 1;
			} else if ((m = TAB_XP.matcher(text)).matches()) {
				maxLevel = false;
				xpCurrent = parseNumber(m.group("current"));
				xpNext = m.group("next") != null ? parseNumber(m.group("next")) : -1;
				if (m.group("percent") != null) progress = Float.parseFloat(m.group("percent")) / 100f;
				else if (xpNext > 0) progress = (float) (xpCurrent / xpNext);
			} else if (text.startsWith("Held Item:") || text.startsWith("Item:")) {
				heldItem = text.substring(text.indexOf(':') + 1).trim();
			}
			if (line >= 4) break;
		}
	}

	private static void readPetsMenu(AbstractContainerScreen<?> screen) {
		for (Slot slot : screen.getMenu().slots) {
			ItemStack stack = slot.getItem();
			if (stack.isEmpty() || !stack.is(Items.PLAYER_HEAD)) continue;
			String hover = ChatFormatting.stripFormatting(stack.getHoverName().getString());
			if (hover == null) continue;
			Matcher m = MENU_NAME.matcher(hover.trim());
			if (!m.matches()) continue;
			String pet = m.group("pet").trim();
			String key = pet.toLowerCase(Locale.ROOT);
			int petColor = colorOf(stack.getHoverName(), pet);
			ItemStack icon = headOf(stack);
			// always take the latest look, so skins you apply later show up
			ICONS.put(key + "|" + tierOf(petColor), icon);
			ICONS.put(key, icon);
			iconsDirty = true;
			ItemLore lore = stack.get(DataComponents.LORE);
			if (lore == null) continue;
			boolean active = false;
			String held = "";
			float percent = -1;
			for (Component lineC : lore.lines()) {
				String line = ChatFormatting.stripFormatting(lineC.getString());
				if (line == null) continue;
				line = line.trim();
				if (line.equals("Click to despawn!")) active = true;
				Matcher h = MENU_HELD.matcher(line);
				if (h.matches()) held = h.group("item");
				Matcher p = MENU_PROGRESS.matcher(line);
				if (p.matches()) percent = Float.parseFloat(p.group("percent")) / 100f;
				if (line.equals("MAX LEVEL")) percent = 1;
			}
			// (a menu still open from before you switched can show the old pet as active: the hold skips it)
			if (active && !HELD_PET.holding() || active && HELD_PET.value().equalsIgnoreCase(pet)) {
				setCurrentIcon(pet, icon);
				skinColor = skinColorOf(stack.getHoverName());
				setPet(pet, Integer.parseInt(m.group("level")), colorOf(stack.getHoverName(), pet));
				heldItem = held;
				if (percent >= 0) progress = percent;
				maxLevel = percent >= 1 && xpNext < 0;
			}
		}
	}

	/** The colour the pet's name is written in (its rarity). */
	private static int colorOf(Component component, String pet) {
		int[] found = {-1};
		String raw = component.getString();
		int idx = raw.indexOf(pet);
		if (idx > 0 && raw.indexOf('§') >= 0) {
			// legacy colour codes inside the text
			for (int i = idx - 2; i >= 0; i--) {
				if (raw.charAt(i) == '§') {
					int idx2 = "0123456789abcdef".indexOf(Character.toLowerCase(raw.charAt(i + 1)));
					if (idx2 >= 0) return LEGACY[idx2];
				}
			}
		}
		component.visit((Style style, String text) -> {
			if (found[0] == -1 && text.contains(pet.split(" ")[0])) {
				TextColor color = style.getColor();
				if (color != null) found[0] = color.getValue();
			}
			return Optional.empty();
		}, Style.EMPTY);
		return found[0];
	}

	private static final int[] LEGACY = {0x000000, 0x0000AA, 0x00AA00, 0x00AAAA, 0xAA0000, 0xAA00AA, 0xFFAA00, 0xAAAAAA,
			0x555555, 0x5555FF, 0x55FF55, 0x55FFFF, 0xFF5555, 0xFF55FF, 0xFFFF55, 0xFFFFFF};

	private static int parseInt(String s) {
		try {
			return Integer.parseInt(s.replace(",", ""));
		} catch (NumberFormatException e) {
			return -1;
		}
	}

	private static double parseNumber(String s) {
		s = s.replace(",", "").trim();
		double mult = 1;
		char last = Character.toLowerCase(s.charAt(s.length() - 1));
		if (last == 'k') mult = 1e3;
		else if (last == 'm') mult = 1e6;
		else if (last == 'b') mult = 1e9;
		if (mult != 1) s = s.substring(0, s.length() - 1);
		try {
			return Double.parseDouble(s) * mult;
		} catch (NumberFormatException e) {
			return -1;
		}
	}

	private static String shortNumber(double v) {
		if (v >= 1e9) return trim(v / 1e9) + "B";
		if (v >= 1e6) return trim(v / 1e6) + "M";
		if (v >= 1e3) return trim(v / 1e3) + "k";
		return String.valueOf(Math.round(v));
	}

	private static String trim(double v) {
		String s = String.format(Locale.ROOT, "%.1f", v);
		return s.endsWith(".0") ? s.substring(0, s.length() - 2) : s;
	}

	// ---------------------------------------------------------------- icon cache

	private static Path iconFile() {
		return FancyBarsMod.CONFIG_DIR.resolve("pet_icons.json");
	}

	private static DynamicOps<JsonElement> ops() {
		Minecraft mc = Minecraft.getInstance();
		return mc.level == null ? null : mc.level.registryAccess().createSerializationContext(JsonOps.INSTANCE);
	}

	private static void loadIcons() {
		iconsLoaded = true;
		DynamicOps<JsonElement> ops = ops();
		if (ops == null) return;
		try {
			if (!Files.exists(iconFile())) return;
			JsonObject root = JsonParser.parseString(Files.readString(iconFile())).getAsJsonObject();
			for (Map.Entry<String, JsonElement> e : root.entrySet()) {
				ItemStack.CODEC.parse(ops, e.getValue()).result().ifPresent(stack -> {
					ICONS.putIfAbsent(e.getKey(), stack);
					if (e.getKey().startsWith("current:") && currentIcon == null) {
						currentIcon = stack;
						currentIconPet = e.getKey().substring("current:".length());
					}
				});
			}
		} catch (Exception e) {
			FancyBarsMod.LOGGER.warn("[SBCLIENT] Couldn't read pet icons", e);
		}
	}

	private static void saveIcons() {
		DynamicOps<JsonElement> ops = ops();
		if (ops == null) return;
		iconsDirty = false;
		JsonObject root = new JsonObject();
		for (Map.Entry<String, ItemStack> e : new LinkedHashMap<>(ICONS).entrySet()) {
			ItemStack.CODEC.encodeStart(ops, e.getValue()).result().ifPresent(json -> root.add(e.getKey(), json));
		}
		try {
			Files.createDirectories(iconFile().getParent());
			Files.writeString(iconFile(), root.toString());
		} catch (Exception e) {
			FancyBarsMod.LOGGER.warn("[SBCLIENT] Couldn't save pet icons", e);
		}
	}

	/** The head of the pet you have out right now (with its skin), from the /pets menu or the pet you clicked there. */
	private static ItemStack currentIcon;
	private static String currentIconPet = "";
	private static ItemStack hoveredHead;
	private static String hoveredPet = "";
	private static long hoveredAt;

	private static ItemStack headOf(ItemStack stack) {
		ItemStack icon = new ItemStack(Items.PLAYER_HEAD);
		var profile = stack.get(DataComponents.PROFILE);
		if (profile != null) icon.set(DataComponents.PROFILE, profile);
		return icon;
	}

	private static void setCurrentIcon(String pet, ItemStack head) {
		currentIcon = head;
		currentIconPet = pet;
		ICONS.put("current:" + pet.toLowerCase(Locale.ROOT), head);
		ICONS.keySet().removeIf(k -> k.startsWith("current:") && !k.equals("current:" + pet.toLowerCase(Locale.ROOT)));
		iconsDirty = true;
	}

	/** You just summoned / Autopet equipped {@code pet}: use the head you clicked in /pets if that's the one. */
	private static void switchedTo(String pet) {
		if (hoveredHead != null && hoveredPet.equalsIgnoreCase(pet) && System.currentTimeMillis() - hoveredAt < 15_000) {
			setCurrentIcon(pet, hoveredHead);
			skinColor = hoveredSkin;
		} else if (!currentIconPet.equalsIgnoreCase(pet)) {
			currentIcon = null;
			currentIconPet = "";
			skinColor = -1;
		}
	}

	/** Colour of the pet's skin star (✦), or -1 when it has no skin. */
	private static int skinColor = -1;
	private static int hoveredSkin = -1;

	/** Finds the skin star in a pet name and returns its colour (-1 if there's none). */
	private static int skinColorOf(Component component) {
		String raw = component.getString();
		int star = raw.indexOf('✦');
		if (star < 0) return -1;
		if (raw.indexOf('§') >= 0) {
			for (int i = star - 2; i >= 0; i--) {
				if (raw.charAt(i) == '§') {
					int idx = "0123456789abcdef".indexOf(Character.toLowerCase(raw.charAt(i + 1)));
					if (idx >= 0) return LEGACY[idx];
				}
			}
		}
		int[] found = {-1};
		component.visit((Style style, String text) -> {
			if (found[0] == -1 && text.indexOf('✦') >= 0) {
				TextColor color = style.getColor();
				found[0] = color != null ? color.getValue() : 0xFF55FF;
			}
			return Optional.empty();
		}, Style.EMPTY);
		return found[0] == -1 ? 0xFF55FF : found[0];
	}

	/** Pet heads bundled from the NEU repo: lowercase name -> pet id, pet id -> {d: default hash, "0".."5": per-rarity hash}. */
	private static JsonObject bundled;
	private static final Map<String, ItemStack> HEADS = new HashMap<>();
	private static final java.util.Set<String> FETCHING = java.util.concurrent.ConcurrentHashMap.newKeySet();
	private static final Map<String, String> FETCHED = new java.util.concurrent.ConcurrentHashMap<>();

	private static JsonObject bundled() {
		if (bundled == null) {
			bundled = new JsonObject();
			try (var in = PetHud.class.getResourceAsStream("/assets/fancybars/data/pet_heads.json")) {
				if (in != null) bundled = JsonParser.parseString(new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
			} catch (Exception e) {
				FancyBarsMod.LOGGER.warn("[SBCLIENT] Couldn't read bundled pet heads", e);
			}
		}
		return bundled;
	}

	/** 0 common .. 5 mythic, from the rarity colour. */
	private static int tierOf(int color) {
		return switch (color & 0xFFFFFF) {
			case 0x55FF55 -> 1;
			case 0x5555FF -> 2;
			case 0xAA00AA -> 3;
			case 0xFFAA00 -> 4;
			case 0xFF55FF -> 5;
			default -> 0;
		};
	}

	private static String petId(String pet) {
		JsonObject names = bundled().getAsJsonObject("names");
		String key = pet.toLowerCase(Locale.ROOT);
		if (names != null && names.has(key)) return names.get(key).getAsString();
		return pet.toUpperCase(Locale.ROOT).replace(' ', '_').replace("-", "_");
	}

	/** Texture hash of the pet's head: bundled, or fetched from the NEU repo for pets added since. */
	private static String textureHash(String pet, int tier) {
		String id = petId(pet);
		JsonObject pets = bundled().getAsJsonObject("pets");
		if (pets != null && pets.has(id)) {
			JsonObject e = pets.getAsJsonObject(id);
			return e.has(String.valueOf(tier)) ? e.get(String.valueOf(tier)).getAsString() : e.get("d").getAsString();
		}
		String key = id + ";" + tier;
		String hash = FETCHED.get(key);
		if (hash != null) return hash.isEmpty() ? null : hash;
		if (FETCHING.add(key)) {
			FancyBarsMod.VIRTUAL_THREAD_EXECUTOR.execute(() -> {
				String found = "";
				try {
					java.net.http.HttpClient http = java.net.http.HttpClient.newHttpClient();
					String url = "https://raw.githubusercontent.com/NotEnoughUpdates/NotEnoughUpdates-REPO/master/items/" + id + "%3B" + tier + ".json";
					var res = http.send(java.net.http.HttpRequest.newBuilder(java.net.URI.create(url)).timeout(java.time.Duration.ofSeconds(15)).GET().build(),
							java.net.http.HttpResponse.BodyHandlers.ofString());
					if (res.statusCode() == 200) {
						Matcher v = Pattern.compile("Value:\\\\?\"([A-Za-z0-9+/=]+)").matcher(res.body());
						if (v.find()) {
							String json = new String(java.util.Base64.getDecoder().decode(v.group(1)), java.nio.charset.StandardCharsets.UTF_8);
							Matcher u = Pattern.compile("texture/([0-9a-f]+)").matcher(json);
							if (u.find()) found = u.group(1);
						}
					}
				} catch (Exception e) {
					FancyBarsMod.LOGGER.debug("[SBCLIENT] Pet head fetch failed for {}", key, e);
				}
				FETCHED.put(key, found);
			});
		}
		return null;
	}

	private static ItemStack headFromHash(String hash) {
		return HEADS.computeIfAbsent(hash, h -> {
			ItemStack head = new ItemStack(Items.PLAYER_HEAD);
			try {
				String value = java.util.Base64.getEncoder().encodeToString(("{\"textures\":{\"SKIN\":{\"url\":\"http://textures.minecraft.net/texture/" + h + "\"}}}")
						.getBytes(java.nio.charset.StandardCharsets.UTF_8));
				com.mojang.authlib.properties.PropertyMap map = net.minecraft.util.ExtraCodecs.PROPERTY_MAP.parse(JsonOps.INSTANCE,
						JsonParser.parseString("[{\"name\":\"textures\",\"value\":\"" + value + "\"}]")).getOrThrow();
				head.set(DataComponents.PROFILE, net.minecraft.world.item.component.ResolvableProfile.createResolved(
						new com.mojang.authlib.GameProfile(java.util.UUID.nameUUIDFromBytes(h.getBytes(java.nio.charset.StandardCharsets.UTF_8)), "pet", map)));
			} catch (Exception e) {
				FancyBarsMod.LOGGER.debug("[SBCLIENT] Couldn't build pet head", e);
			}
			return head;
		});
	}

	/** The pet's real head: the one seen in /pets (includes skins), else its default look. */
	private static ItemStack icon(String pet, int color) {
		if (currentIcon != null && currentIconPet.equalsIgnoreCase(pet)) return currentIcon;
		String key = pet.toLowerCase(Locale.ROOT);
		ItemStack stack = ICONS.get(key + "|" + tierOf(color));
		if (stack == null) stack = ICONS.get(key);
		if (stack != null) return stack;
		String hash = textureHash(pet, tierOf(color));
		return hash != null ? headFromHash(hash) : new ItemStack(Items.BONE);
	}

	// ---------------------------------------------------------------- XP ring texture

	private static final Map<Long, Identifier> ARCS = new LinkedHashMap<>(16, 0.75f, true);
	private static int arcCounter;

	/** A ring (outer diameter {@code size}, thickness {@code t} screen pixels) filled clockwise from the top up to {@code steps}/200. */
	private static Identifier arc(int size, int t, int steps) {
		long key = (long) size << 40 | (long) t << 20 | steps;
		Identifier id = ARCS.get(key);
		if (id != null) return id;
		NativeImage image = new NativeImage(size, size, false);
		float r = size / 2f;
		float inner = r - t;
		double limit = steps / 200.0 * Math.PI * 2;
		for (int y = 0; y < size; y++) {
			for (int x = 0; x < size; x++) {
				int hits = 0;
				for (int sy = 0; sy < 4; sy++) {
					for (int sx = 0; sx < 4; sx++) {
						float dx = x + (sx + 0.5f) / 4f - r;
						float dy = y + (sy + 0.5f) / 4f - r;
						float d = (float) Math.sqrt(dx * dx + dy * dy);
						if (d > r || d < inner) continue;
						double angle = Math.atan2(dx, -dy);
						if (angle < 0) angle += Math.PI * 2;
						if (angle <= limit) hits++;
					}
				}
				image.setPixel(x, y, Math.round(hits / 16f * 255) << 24 | 0xFFFFFF);
			}
		}
		id = FancyBarsMod.id("hud/pet_arc_" + arcCounter++);
		DynamicTexture texture = new DynamicTexture(() -> "SBCLIENT pet XP ring", image);
		Minecraft.getInstance().getTextureManager().register(id, texture);
		texture.upload();
		ARCS.put(key, id);
		if (ARCS.size() > 64) {
			var it = ARCS.entrySet().iterator();
			Identifier old = it.next().getValue();
			it.remove();
			Minecraft.getInstance().getTextureManager().release(old);
		}
		return id;
	}

	/** Draws a ring arc in GUI units at (x, y) with diameter {@code d} and thickness {@code t}, lined up with screen pixels. */
	private static void drawArc(GuiGraphicsExtractor graphics, float x, float y, float d, float t, float fraction, int color) {
		Matrix3x2fStack pose = graphics.pose();
		float pxPerUnit = Math.abs(pose.m00()) * Theme.guiScale();
		int size = Math.max(4, Math.round(d * pxPerUnit));
		int thick = Math.max(1, Math.round(t * pxPerUnit));
		int steps = Math.clamp(Math.round(fraction * 200), 0, 200);
		if (steps == 0) return;
		Identifier tex = arc(size, thick, steps);
		pose.pushMatrix();
		pose.translate(x, y);
		pose.scale(d / size, d / size);
		graphics.blit(dev.fancybars.util.gui.Chroma.textured(color), tex, 0, 0, 0, 0, size, size, size, size, color);
		pose.popMatrix();
	}

	// ---------------------------------------------------------------- drawing (matches SkyHanni's defaults)

	/** Pet icon: 16 px item at 2.5x. */
	private static final float ICON_SIZE = 40;
	/** Rarity circle padding around the icon. */
	private static final float BG_PAD = 4;
	private static final float XP_RING = 3;
	/** Full diameter of the icon with its XP ring (fixed, SkyHanni's size). */
	private static final float CIRCLE_SIZE = ICON_SIZE + BG_PAD * 2 + 2 + XP_RING * 2;
	/** Space inside the XP ring: the biggest the rarity background can get. */
	private static final float INSIDE = CIRCLE_SIZE - XP_RING * 2;

	private static float circle() {
		return CIRCLE_SIZE;
	}

	/** Rarity background diameter from the Rarity Background Size setting (100% fills the inside of the ring). */
	private static float backgroundSize() {
		return INSIDE * Math.clamp(FancyBarsConfig.get().petBackgroundSize, 0, 1);
	}
	private static final int TEXT_GAP = 4;
	private static final int LINE_H = 10;

	private static List<Component> lines(boolean sample) {
		FancyBarsConfig c = FancyBarsConfig.get();
		boolean useSample = sample && name.isEmpty();
		String pet = useSample ? "Golden Dragon" : name;
		int lvl = useSample ? 200 : level;
		int color = useSample ? 0xFFAA00 : rarityColor;
		List<Component> out = new ArrayList<>();
		// level in grey, name in the rarity colour (like Hypixel's own pet names)
		net.minecraft.network.chat.MutableComponent title = Component.empty();
		if (c.petShowLevel && lvl > 0) title.append(Component.literal("[Lvl " + lvl + "] ").withColor(0xAAAAAA));
		title.append(Component.literal(pet).withColor(color & 0xFFFFFF));
		int skin = useSample ? 0xFF55FF : skinColor;
		if (c.petShowSkin && skin != -1) title.append(Component.literal(" ✦").withColor(skin & 0xFFFFFF));
		out.add(title);
		if (c.petShowXp) {
			String xp;
			if (useSample) xp = "12.4M/25.4M XP (48.8%)";
			else if (maxLevel) xp = "MAX LEVEL";
			else if (xpCurrent >= 0 && xpNext > 0) xp = shortNumber(xpCurrent) + "/" + shortNumber(xpNext) + " XP" + (progress >= 0 ? String.format(Locale.ROOT, " (%.1f%%)", progress * 100) : "");
			else if (progress >= 0) xp = String.format(Locale.ROOT, "%.1f%%", progress * 100);
			else xp = "";
			if (!xp.isEmpty()) out.add(Component.literal(xp).withColor(0xAAAAAA));
		}
		if (c.petShowHeldItem) {
			String held = useSample ? "Dwarf Turtle Shelmet" : heldItem;
			if (!held.isEmpty()) out.add(Component.literal(held).withColor(0xAAAAAA));
		}
		return out;
	}

	public static int[] size(boolean sample) {
		FancyBarsConfig c = FancyBarsConfig.get();
		Font font = Minecraft.getInstance().font;
		List<Component> l = lines(sample);
		int textW = 0;
		for (Component line : l) textW = Math.max(textW, font.width(line));
		int circle = c.petShowIcon ? Math.round(circle()) : 0;
		int w = circle + (textW > 0 ? (circle > 0 ? TEXT_GAP : 0) + textW : 0);
		int h = Math.max(circle, l.size() * LINE_H);
		if (c.petBackground) {
			w += 8;
			h += 8;
		}
		return new int[]{w, h};
	}

	public static float[] position(int screenW, int screenH) {
		FancyBarsConfig c = FancyBarsConfig.get();
		if (c.petX < 0 || c.petY < 0) return new float[]{6, screenH * 0.25f};
		return new float[]{c.petX * screenW, c.petY * screenH};
	}

	private static void disc(GuiGraphicsExtractor graphics, float cx, float cy, float d, int color) {
		dev.fancybars.util.gui.SmoothShapes.draw(graphics, dev.fancybars.util.gui.SmoothShapes.Shape.CIRCLE, cx - d / 2, cy - d / 2, d, color);
	}

	public static void renderAt(GuiGraphicsExtractor graphics, float x, float y, float scale, float alpha, boolean sample) {
		FancyBarsConfig c = FancyBarsConfig.get();
		Font font = Minecraft.getInstance().font;
		int a = Math.round(alpha * 255);
		int[] size = size(sample);
		List<Component> l = lines(sample);
		boolean useSample = sample && name.isEmpty();
		int color = useSample ? 0xFFAA00 : rarityColor;
		float prog = useSample ? 0.488f : maxLevel ? 1 : progress;

		Matrix3x2fStack pose = graphics.pose();
		pose.pushMatrix();
		pose.translate(x, y);
		pose.scale(scale, scale);
		float ox = 0;
		float oy = 0;
		if (c.petBackground) {
			Theme.hudPanel(graphics, 0, 0, size[0], size[1], 6, 0.85f, a);
			ox = 4;
			oy = 4;
		}
		float innerH = size[1] - oy * 2;
		float tx = ox;
		if (c.petShowIcon) {
			final float CIRCLE = circle();
			float cx = ox + CIRCLE / 2f;
			float cy = oy + innerH / 2f;
			if (c.petXpRing) {
				// unfilled ring, then the filled part (full when the XP isn't known yet)
				drawArc(graphics, cx - CIRCLE / 2f, cy - CIRCLE / 2f, CIRCLE, XP_RING, 1, a << 24 | c.petRingUnfilled & 0xFFFFFF);
				float filled = prog < 0 ? 1 : Math.min(1, prog);
				int ring = c.petRingRarity ? color : c.petRingColor;
				int ringColor = (a << 24) | ring & 0xFFFFFF;
				if (c.chromaAccent && !c.petRingRarity) ringColor = dev.fancybars.util.gui.Chroma.mark(ringColor);
				drawArc(graphics, cx - CIRCLE / 2f, cy - CIRCLE / 2f, CIRCLE, XP_RING, filled, ringColor);
			}
			// whatever the rarity background doesn't cover inside the ring
			if (c.petSeparator) disc(graphics, cx, cy, INSIDE, a << 24 | c.petSeparatorColor & 0xFFFFFF);
			if (c.petRarityBackground && backgroundSize() > 0.5f) disc(graphics, cx, cy, backgroundSize(), a << 24 | color & 0xFFFFFF);
			ItemStack stack = icon(useSample ? "golden dragon" : name, color);
			// rendered at full screen resolution, so the head is as smooth as SkyHanni's
			dev.fancybars.util.gui.SharpItem.draw(graphics, stack, cx - ICON_SIZE / 2f, cy - ICON_SIZE / 2f, ICON_SIZE);
			tx += CIRCLE + TEXT_GAP;
		}
		// Minecraft's own font with shadow, like SkyHanni
		float ty = oy + (innerH - l.size() * LINE_H) / 2f + 1;
		for (Component line : l) {
			pose.pushMatrix();
			pose.translate(tx, ty);
			graphics.text(font, line, 0, 0, a << 24 | 0xFFFFFF, true);
			pose.popMatrix();
			ty += LINE_H;
		}
		pose.popMatrix();
	}
}
