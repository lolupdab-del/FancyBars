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
import dev.fancybars.bars.MovableElement;
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

	private static void onChat(String text, Component message) {
		Matcher m;
		if ((m = AUTOPET.matcher(text)).matches()) {
			setPet(m.group("pet"), Integer.parseInt(m.group("level")), colorOf(message, m.group("pet")));
			heldItem = "";
		} else if ((m = SUMMON.matcher(text)).matches()) {
			setPet(m.group("pet"), -1, colorOf(message, m.group("pet")));
		} else if ((m = DESPAWN.matcher(text)).matches()) {
			name = "";
			level = -1;
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
				if (text.equalsIgnoreCase("No pet selected") || text.equalsIgnoreCase("None")) {
					name = "";
					return;
				}
				if ((m = TAB_NAME.matcher(text)).matches()) {
					setPet(m.group("pet").trim(), parseInt(m.group("level")), colorOf(display, m.group("pet").trim()));
				}
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
			if (!ICONS.containsKey(key)) {
				ItemStack icon = new ItemStack(Items.PLAYER_HEAD);
				var profile = stack.get(DataComponents.PROFILE);
				if (profile != null) icon.set(DataComponents.PROFILE, profile);
				ICONS.put(key, icon);
				iconsDirty = true;
			}
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
			if (active) {
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
				ItemStack.CODEC.parse(ops, e.getValue()).result().ifPresent(stack -> ICONS.putIfAbsent(e.getKey(), stack));
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

	private static ItemStack icon(String pet) {
		ItemStack stack = ICONS.get(pet.toLowerCase(Locale.ROOT));
		return stack != null ? stack : new ItemStack(Items.BONE);
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

	// ---------------------------------------------------------------- drawing

	private record Lines(List<Component> lines, int width) {}

	private static Lines lines(boolean sample) {
		FancyBarsConfig c = FancyBarsConfig.get();
		Font font = Minecraft.getInstance().font;
		String pet = sample && name.isEmpty() ? "Golden Dragon" : name;
		int lvl = sample && name.isEmpty() ? 200 : level;
		int color = sample && name.isEmpty() ? 0xFFAA00 : rarityColor;
		List<Component> out = new ArrayList<>();
		String title = (c.petShowLevel && lvl > 0 ? "[Lvl " + lvl + "] " : "") + pet;
		out.add(Theme.bold(Component.literal(title).withColor(color & 0xFFFFFF)));
		if (c.petShowXp) {
			String xp;
			if (sample && name.isEmpty()) xp = "12.4M/25.4M XP (48.8%)";
			else if (maxLevel) xp = "MAX LEVEL";
			else if (xpCurrent >= 0 && xpNext > 0) xp = shortNumber(xpCurrent) + "/" + shortNumber(xpNext) + " XP" + (progress >= 0 ? String.format(Locale.ROOT, " (%.1f%%)", progress * 100) : "");
			else if (progress >= 0) xp = String.format(Locale.ROOT, "%.1f%% to next level", progress * 100);
			else xp = "";
			if (!xp.isEmpty()) out.add(Theme.styled(Component.literal(xp).withColor(Theme.hudTextSoft(255) & 0xFFFFFF)));
		}
		if (c.petShowHeldItem) {
			String held = sample && name.isEmpty() ? "Dwarf Turtle Shelmet" : heldItem;
			if (!held.isEmpty()) out.add(Theme.styled(Component.literal(held).withColor(Theme.hudTextSoft(255) & 0xFFFFFF)));
		}
		int w = 0;
		for (Component line : out) w = Math.max(w, font.width(line));
		return new Lines(out, w);
	}

	private static final int LINE_H = 11;

	public static int[] size(boolean sample) {
		FancyBarsConfig c = FancyBarsConfig.get();
		Lines l = lines(sample);
		int iconW = c.petShowIcon ? ICON + 6 : 0;
		int textH = l.lines().size() * LINE_H;
		int h = Math.max(c.petShowIcon ? ICON : 0, textH) + PAD * 2;
		return new int[]{PAD * 2 + iconW + l.width(), h};
	}

	public static float[] position(int screenW, int screenH) {
		FancyBarsConfig c = FancyBarsConfig.get();
		if (c.petX < 0 || c.petY < 0) return new float[]{6, screenH * 0.25f};
		return new float[]{c.petX * screenW, c.petY * screenH};
	}

	public static void renderAt(GuiGraphicsExtractor graphics, float x, float y, float scale, float alpha, boolean sample) {
		FancyBarsConfig c = FancyBarsConfig.get();
		Font font = Minecraft.getInstance().font;
		int a = Math.round(alpha * 255);
		int[] size = size(sample);
		Lines l = lines(sample);
		boolean useSample = sample && name.isEmpty();
		int color = useSample ? 0xFFAA00 : rarityColor;
		float prog = useSample ? 0.488f : maxLevel ? 1 : progress;

		Matrix3x2fStack pose = graphics.pose();
		pose.pushMatrix();
		pose.translate(x, y);
		pose.scale(scale, scale);
		if (c.petBackground) Theme.hudPanel(graphics, 0, 0, size[0], size[1], 6, 0.85f, a);
		int tx = PAD;
		if (c.petShowIcon) {
			float ix = PAD;
			float iy = (size[1] - ICON) / 2f;
			float cx = ix + ICON / 2f;
			float cy = iy + ICON / 2f;
			if (c.petRarityBackground) Theme.circle(graphics, cx, cy, ICON - 6, Math.round(a * 0.35f) << 24 | color & 0xFFFFFF);
			if (c.petXpRing) {
				drawArc(graphics, ix, iy, ICON, 2.2f, 1, Math.round(a * 0.18f) << 24 | 0xFFFFFF);
				if (prog > 0) {
					int ring = c.petRingRarity ? color : c.petRingColor;
					int ringColor = (a << 24) | ring & 0xFFFFFF;
					if (c.chromaAccent && !c.petRingRarity) ringColor = dev.fancybars.util.gui.Chroma.mark(ringColor);
					drawArc(graphics, ix, iy, ICON, 2.2f, Math.min(1, prog), ringColor);
				}
			}
			ItemStack stack = icon(useSample ? "golden dragon" : name);
			pose.pushMatrix();
			pose.translate(cx - 9.6f, cy - 9.6f);
			pose.scale(1.2f, 1.2f);
			graphics.fakeItem(stack, 0, 0);
			pose.popMatrix();
			tx += ICON + 6;
		}
		int textH = l.lines().size() * LINE_H;
		float ty = (size[1] - textH) / 2f;
		for (Component line : l.lines()) {
			Theme.drawAtCapTop(graphics, font, line, tx, ty + (LINE_H - Theme.UI_CAP) / 2f, Theme.UI_CAP, a << 24 | 0xFFFFFF);
			ty += LINE_H;
		}
		pose.popMatrix();
	}

	// ---------------------------------------------------------------- editor

	public static final MovableElement ELEMENT = new MovableElement() {
		@Override
		public float[] rect(int screenW, int screenH) {
			float[] pos = position(screenW, screenH);
			int[] size = size(true);
			float s = scale();
			return new float[]{pos[0], pos[1], size[0] * s, size[1] * s};
		}

		@Override
		public void drawPreview(GuiGraphicsExtractor graphics, int screenW, int screenH) {
			float[] pos = position(screenW, screenH);
			renderAt(graphics, pos[0], pos[1], scale(), enabled() ? 1 : 0.35f, true);
		}

		@Override
		public void moveTo(float x, float y, int screenW, int screenH) {
			FancyBarsConfig c = FancyBarsConfig.get();
			float[] r = rect(screenW, screenH);
			c.petX = Math.clamp(x, 0, Math.max(0, screenW - r[2])) / screenW;
			c.petY = Math.clamp(y, 0, Math.max(0, screenH - r[3])) / screenH;
		}

		@Override
		public float scale() {
			return FancyBarsConfig.get().petScale;
		}

		@Override
		public void setScale(float scale) {
			FancyBarsConfig.get().petScale = scale;
		}

		@Override
		public boolean enabled() {
			return FancyBarsConfig.get().petEnabled;
		}

		@Override
		public void setEnabled(boolean enabled) {
			FancyBarsConfig.get().petEnabled = enabled;
		}

		@Override
		public void reset() {
			FancyBarsConfig c = FancyBarsConfig.get();
			c.petX = -1;
			c.petY = -1;
			c.petScale = 1;
			FancyBarsConfig.save();
		}

		@Override
		public Component name() {
			return Component.translatable("fancybars.editor.element.pet");
		}

		@Override
		public boolean snapsToCenter() {
			return false;
		}
	};
}
