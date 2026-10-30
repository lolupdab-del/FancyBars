package dev.fancybars.dungeons;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.joml.Matrix3x2fStack;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import dev.fancybars.FancyBarsMod;
import dev.fancybars.config.FancyBarsConfig;
import dev.fancybars.util.SkyblockLocation;
import dev.fancybars.util.gui.Theme;

/**
 * Counts rare dungeon chest drops (Necron's Handle, master stars, scrolls, ...) and the runs between them.
 * Drops are read from the loot lines Hypixel prints in chat when a reward chest is claimed (the same lines Skyblocker
 * uses for its rare drop effects); runs are counted from the end-of-run "> EXTRA STATS <" line.
 */
public final class RareDropTracker {
	private static final Logger LOGGER = LogUtils.getLogger();
	private static final Path FILE = FancyBarsMod.CONFIG_DIR.resolve("rare-drops.json");
	/** Loot lines from claimed dungeon reward chests, e.g. "   RARE REWARD! Necron's Handle". */
	private static final Pattern CHEST_LINE = Pattern.compile("^\\s{3,}(?!.*:)(?<rare>RARE REWARD!\\s+)?(?<item>.+?)(?:\\s+x\\d+)?\\s*$");
	private static final Pattern RUN_END = Pattern.compile("^\\s*> EXTRA STATS <\\s*$");
	private static final Pattern FLOOR = Pattern.compile("(?<master>Master Mode )?(?:The )?Catacombs - (?<floor>Entrance|Floor [IVX]+)");

	public record RareItem(String name, String color, Supplier<ItemStack> icon) {}

	/** The drops that count as rare, roughly in floor order. */
	public static final List<RareItem> ITEMS = List.of(
			new RareItem("Recombobulator 3000", "§6", () -> new ItemStack(Items.COMPARATOR)),
			new RareItem("Spirit Wing", "§5", () -> new ItemStack(Items.FEATHER)),
			new RareItem("Spirit Bone", "§5", () -> new ItemStack(Items.BONE)),
			new RareItem("Spirit Mask", "§5", () -> new ItemStack(Items.SKELETON_SKULL)),
			new RareItem("Shadow Fury", "§6", () -> new ItemStack(Items.DIAMOND_SWORD)),
			new RareItem("Last Breath", "§6", () -> new ItemStack(Items.BOW)),
			new RareItem("Giant's Sword", "§6", () -> new ItemStack(Items.IRON_SWORD)),
			new RareItem("Precursor Eye", "§5", () -> new ItemStack(Items.ENDER_EYE)),
			new RareItem("Necromancer Lord Chestplate", "§6", () -> new ItemStack(Items.DIAMOND_CHESTPLATE)),
			new RareItem("Necron's Handle", "§5", () -> new ItemStack(Items.STICK)),
			new RareItem("Shiny Necron's Handle", "§5", () -> new ItemStack(Items.BLAZE_ROD)),
			new RareItem("Implosion", "§6", () -> new ItemStack(Items.PAPER)),
			new RareItem("Shadow Warp", "§6", () -> new ItemStack(Items.PAPER)),
			new RareItem("Wither Shield", "§6", () -> new ItemStack(Items.PAPER)),
			new RareItem("Auto Recombobulator", "§6", () -> new ItemStack(Items.REPEATER)),
			new RareItem("Dark Claymore", "§6", () -> new ItemStack(Items.STONE_SWORD)),
			new RareItem("Necron Dye", "§d", () -> new ItemStack(Items.RED_DYE)),
			new RareItem("Master Skull - Tier 5", "§d", () -> new ItemStack(Items.WITHER_SKELETON_SKULL)),
			new RareItem("First Master Star", "§5", () -> new ItemStack(Items.NETHER_STAR)),
			new RareItem("Second Master Star", "§5", () -> new ItemStack(Items.NETHER_STAR)),
			new RareItem("Third Master Star", "§5", () -> new ItemStack(Items.NETHER_STAR)),
			new RareItem("Fourth Master Star", "§5", () -> new ItemStack(Items.NETHER_STAR)),
			new RareItem("Fifth Master Star", "§5", () -> new ItemStack(Items.NETHER_STAR)));

	private static final Map<String, ItemStack> ICONS = new LinkedHashMap<>();

	public static final class Drop {
		public String item = "";
		public String floor = "";
		/** Runs since the previous rare drop. */
		public int runs;
		public long time;
	}

	public static final class Data {
		public int runs = 0;
		public int runsSinceLast = 0;
		public Map<String, Integer> counts = new LinkedHashMap<>();
		public List<Drop> history = new ArrayList<>();
	}

	private static Data data = new Data();
	private static String currentFloor = "";

	private RareDropTracker() {}

	public static void init() {
		load();
		ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
			if (!overlay) onChat(message);
		});
		SkyblockLocation.onLocationChange(() -> {
			if (!SkyblockLocation.isInDungeons()) currentFloor = "";
		});
		HudElementRegistry.attachElementAfter(VanillaHudElements.HOTBAR, FancyBarsMod.id("rare_drops"), (graphics, tickCounter) -> {
			if (shouldShowHud()) render(graphics);
		});
	}

	public static Data data() {
		return data;
	}

	public static boolean shouldShowHud() {
		FancyBarsConfig config = FancyBarsConfig.get();
		if (!config.rareDropsEnabled || !config.rareDropsHud) return false;
		if (config.rareDropsHudEverywhere) return SkyblockLocation.isOnSkyblock();
		return SkyblockLocation.isInDungeons() || SkyblockLocation.isInDungeonHub();
	}

	public static boolean isTracked(String item) {
		return !FancyBarsConfig.get().rareDropsUntracked.contains(item);
	}

	private static void onChat(Component message) {
		if (!FancyBarsConfig.get().rareDropsEnabled || !SkyblockLocation.isOnSkyblock()) return;
		try {
			String text = DungeonSecrets.stripped(message.getString());
			Matcher floor = FLOOR.matcher(text);
			if (floor.find()) currentFloor = shortFloor(floor.group("master") != null, floor.group("floor"));

			if (SkyblockLocation.isInDungeons() && RUN_END.matcher(text).matches()) {
				data.runs++;
				data.runsSinceLast++;
				save();
				return;
			}

			Matcher line = CHEST_LINE.matcher(text);
			if (!line.matches()) return;
			String item = line.group("item").trim();
			RareItem known = find(item);
			// Hypixel marks some drops "RARE REWARD!"; anything else only counts when it's on our list
			if (known == null && line.group("rare") == null) return;
			if (known != null) item = known.name();
			if (!isTracked(item)) return;
			record(item);
		} catch (Exception e) {
			LOGGER.error("[FancyBars Rare Drops] Failed to read chat message", e);
		}
	}

	private static String shortFloor(boolean master, String floor) {
		if (floor.equals("Entrance")) return "E";
		String roman = floor.substring("Floor ".length());
		int n = switch (roman) {
			case "I" -> 1;
			case "II" -> 2;
			case "III" -> 3;
			case "IV" -> 4;
			case "V" -> 5;
			case "VI" -> 6;
			case "VII" -> 7;
			default -> 0;
		};
		return (master ? "M" : "F") + n;
	}

	public static @Nullable RareItem find(String name) {
		for (RareItem item : ITEMS) {
			if (item.name().equalsIgnoreCase(name)) return item;
		}
		return null;
	}

	private static void record(String item) {
		Drop drop = new Drop();
		drop.item = item;
		drop.floor = currentFloor;
		drop.runs = data.runsSinceLast;
		drop.time = System.currentTimeMillis();
		data.history.add(drop);
		if (data.history.size() > 200) data.history.removeFirst();
		data.counts.merge(item, 1, Integer::sum);
		int sinceLast = data.runsSinceLast;
		data.runsSinceLast = 0;
		save();

		if (FancyBarsConfig.get().rareDropsChat && Minecraft.getInstance().player != null) {
			RareItem known = find(item);
			String color = known != null ? known.color() : "§d";
			Minecraft.getInstance().player.sendSystemMessage(Component.literal("§b[Fancy Bars] §d§lRARE DROP! §r" + color + item
					+ " §7(#" + data.counts.get(item) + (sinceLast > 0 ? ", " + sinceLast + " runs since last rare drop" : "") + ")"));
		}
	}

	public static void reset() {
		data = new Data();
		save();
	}

	public static ItemStack icon(String item) {
		return ICONS.computeIfAbsent(item, name -> {
			RareItem known = find(name);
			return known != null ? known.icon().get() : new ItemStack(Items.CHEST);
		});
	}

	public static String color(String item) {
		RareItem known = find(item);
		return known != null ? known.color() : "§d";
	}

	// ---------------------------------------------------------------- Storage

	private static void load() {
		if (!Files.exists(FILE)) return;
		try (BufferedReader reader = Files.newBufferedReader(FILE)) {
			Data loaded = FancyBarsMod.GSON.fromJson(reader, Data.class);
			if (loaded != null) {
				if (loaded.counts == null) loaded.counts = new LinkedHashMap<>();
				if (loaded.history == null) loaded.history = new ArrayList<>();
				data = loaded;
			}
		} catch (Exception e) {
			LOGGER.error("[FancyBars Rare Drops] Failed to load {}", FILE, e);
		}
	}

	private static void save() {
		try {
			Files.createDirectories(FILE.getParent());
			try (BufferedWriter writer = Files.newBufferedWriter(FILE)) {
				FancyBarsMod.GSON.toJson(data, writer);
			}
		} catch (Exception e) {
			LOGGER.error("[FancyBars Rare Drops] Failed to save {}", FILE, e);
		}
	}

	// ---------------------------------------------------------------- HUD

	private static final int ROW = 12;
	private static final int PAD = 5;

	private static MutableComponent styled(MutableComponent text) {
		if (!FancyBarsConfig.get().smoothBarText) return text;
		FontDescription font = Theme.barFont();
		return text.withStyle(style -> style.withFont(font));
	}

	private record Line(@Nullable ItemStack icon, Component text) {}

	private static List<Line> lines(boolean sample) {
		List<Line> lines = new ArrayList<>();
		lines.add(new Line(null, styled(Component.translatable("fancybars.rareDrops.hudTitle").withColor(0xFF7DD3FC))));
		int runs = sample ? 142 : data.runs;
		int since = sample ? 17 : data.runsSinceLast;
		lines.add(new Line(null, styled(Component.translatable("fancybars.rareDrops.hudRuns", runs, since).withColor(0xAAAAAA))));
		Map<String, Integer> counts = sample ? Map.of("Necron's Handle", 1, "Fifth Master Star", 2, "Recombobulator 3000", 9) : data.counts;
		boolean any = false;
		List<String> order = new ArrayList<>();
		for (RareItem item : ITEMS) order.add(item.name());
		for (String name : counts.keySet()) if (!order.contains(name)) order.add(name);
		for (String name : order) {
			Integer count = counts.get(name);
			if (count == null || count <= 0 || !isTracked(name)) continue;
			any = true;
			lines.add(new Line(icon(name), styled(Component.literal(color(name) + name + " §7×" + count))));
		}
		if (!any) lines.add(new Line(null, styled(Component.translatable("fancybars.rareDrops.none").withColor(0x888888))));
		return lines;
	}

	/** Unscaled size {width, height}. */
	public static int[] size(boolean sample) {
		Font font = Minecraft.getInstance().font;
		int w = 0;
		List<Line> lines = lines(sample);
		for (Line line : lines) w = Math.max(w, font.width(line.text()) + (line.icon() != null ? 14 : 0));
		return new int[]{w + PAD * 2, lines.size() * ROW + PAD * 2 - 3};
	}

	public static float[] position(int screenW, int screenH, boolean sample) {
		FancyBarsConfig config = FancyBarsConfig.get();
		if (config.rareDropsX < 0 || config.rareDropsY < 0) {
			int[] size = size(sample);
			return new float[]{screenW - size[0] * config.rareDropsScale - 6, Math.round(screenH * 0.18f)};
		}
		return new float[]{config.rareDropsX * screenW, config.rareDropsY * screenH};
	}

	public static void render(GuiGraphicsExtractor graphics) {
		float[] pos = position(graphics.guiWidth(), graphics.guiHeight(), false);
		renderAt(graphics, pos[0], pos[1], FancyBarsConfig.get().rareDropsScale, false);
	}

	public static void renderAt(GuiGraphicsExtractor graphics, float x, float y, float scale, boolean sample) {
		FancyBarsConfig config = FancyBarsConfig.get();
		Font font = Minecraft.getInstance().font;
		Matrix3x2fStack pose = graphics.pose();
		pose.pushMatrix();
		pose.translate(x, y);
		pose.scale(scale, scale);
		int[] size = size(sample);
		if (config.rareDropsBackground) {
			Theme.roundedRect(graphics, 0, 0, size[0], size[1], 4, 0x88101218);
			Theme.roundedOutline(graphics, 0, 0, size[0], size[1], 4, 0x447C82C4);
		}
		int ly = PAD;
		for (Line line : lines(sample)) {
			int tx = PAD;
			if (line.icon() != null) {
				pose.pushMatrix();
				pose.translate(PAD, ly - 2);
				pose.scale(0.75f, 0.75f);
				graphics.item(line.icon(), 0, 0);
				pose.popMatrix();
				tx += 14;
			}
			graphics.text(font, line.text(), tx, ly, 0xFFFFFFFF, true);
			ly += ROW;
		}
		pose.popMatrix();
	}
}
