package dev.fancybars.mining;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.joml.Matrix3x2fStack;
import org.jspecify.annotations.Nullable;

import com.mojang.blaze3d.vertex.PoseStack;

import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerScoreEntry;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;

import dev.fancybars.FancyBarsMod;
import dev.fancybars.config.FancyBarsConfig;
import dev.fancybars.items.ItemValue;
import dev.fancybars.items.list.ItemRepo;
import dev.fancybars.util.SkyblockLocation;
import dev.fancybars.util.gui.Theme;

/**
 * Ghosts (The Mist, Dwarven Mines):
 * <ul>
 * <li>Ghost Hitboxes: an outline around every ghost (they're invisible creepers), so you can see what you're hitting.</li>
 * <li>Ghost Profit Tracker: what this session's ghost killing has made, item by item (sack items from the "[Sacks]"
 * message, rare drops like Sorrow and Volta, coins picked up), with Combat XP, magic find, kill combo, profit per
 * hour and time spent.</li>
 * </ul>
 */
public final class Ghosts {
	private Ghosts() {}

	private static final Pattern SACK_LINE = Pattern.compile("^\\s*\\+([\\d,]+) (.+?) \\((.+?)\\)$");
	/** "RARE DROP! Sorrow (+174✯ Magic Find)" (the % and the space before ✯ aren't always there). */
	// the ✯ can be any symbol (resource packs swap it for their own), and chat mods add " (2)" to repeated messages
	private static final Pattern RARE_DROP = Pattern.compile("^(?:RARE|VERY RARE|CRAZY RARE|PRAY RNGESUS|INSANE) DROP! (?:\\(?(\\d+)x\\)? )?(.+?)(?: \\(\\+(\\d+)%?\\s*\\S?\\s*Magic Find\\))?!?(?: \\(\\d+\\))?$");
	/** Not worth listing (the Mist's floor gets mined too). */
	private static final java.util.Set<String> IGNORED = java.util.Set.of("cobblestone", "stone", "dirt", "gravel");
	private static final int TOP = 5;
	/** Items that come with a RARE DROP message: the sack message for them is the same drop again, so it's skipped. */
	private static final java.util.Set<String> RARE_ITEMS = new java.util.HashSet<>();
	private static String lastRareDrop = "";
	private static long lastRareDropAt;
	/** Shards: "CHARM! You charmed the Ghost and received 3 Ghost Shards!". */
	private static final Pattern CHARM = Pattern.compile("^CHARM! You charmed the .+? and received (\\d+) (.+?)!");
	private static final Pattern KILL_COMBO =Pattern.compile("^\\+(\\d+) Kill Combo");
	/** "+12.5 Combat (1,234/5,000)" or "+12.5 Combat (45.2%)" in the action bar. */
	private static final Pattern COMBAT_XP = Pattern.compile("\\+([\\d,.]+) Combat \\(([\\d,.]+)(?:/[\\d,.kKmM]+)?(%)?\\)");
	private static final Pattern PURSE = Pattern.compile("^(?:Purse|Piggy): ([\\d,]+)");
	private static final long IDLE_MS = 60_000;

	/** Item (display name) → how many this session, in the order they first dropped. */
	private static final Map<String, long[]> ITEMS = new LinkedHashMap<>();
	private static long coins;
	private static int coinPickups;
	private static double combatXp;
	private static double magicFindTotal;
	private static int magicFindDrops;
	private static int maxCombo;
	private static long uptimeMs;
	private static long lastActivity;
	private static double lastXpTotal = -1;
	private static String lastXpText = "";
	private static long lastPurse = -1;
	private static int tickCount;

	/** HUD lines, rebuilt a few times a second rather than every frame. */
	private static List<Component> lines = List.of();

	public static void init() {
		// early, so other mods hiding the sack message doesn't stop us seeing it
		net.minecraft.resources.Identifier early = FancyBarsMod.id("early");
		ClientReceiveMessageEvents.ALLOW_GAME.register(early, (message, overlay) -> {
			if (!overlay) onChat(message);
			return true;
		});
		dev.fancybars.util.Perf.onTick("Tick Ghosts", mc -> tick());
		// with chat open, clicking the tracker switches between Show less and Show more
		net.fabricmc.fabric.api.client.screen.v1.ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> {
			if (!(screen instanceof net.minecraft.client.gui.screens.ChatScreen)) return;
			net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents.allowMouseClick(screen).register((s, click) -> {
				if (click.button() != 0 || !shouldShow() || lines.isEmpty()) return true;
				float[] pos = position(s.width, s.height, lines);
				float[] size = size(lines);
				float scale = FancyBarsConfig.get().ghostTrackerScale;
				if (click.x() < pos[0] || click.y() < pos[1] || click.x() > pos[0] + size[0] * scale || click.y() > pos[1] + size[1] * scale) return true;
				toggleShowAll();
				return false;
			});
		});
		dev.fancybars.util.Perf.attachElementAfter(VanillaHudElements.HOTBAR, FancyBarsMod.id("ghost_tracker"), (graphics, delta) -> {
			if (shouldShow()) render(graphics);
		});
		LevelRenderEvents.COLLECT_SUBMITS.register(context -> {
			FancyBarsConfig c = FancyBarsConfig.get();
			Minecraft mc = Minecraft.getInstance();
			if (!c.ghostHitboxes || mc.level == null || mc.player == null || !inMist()) return;
			Vec3 cam = context.levelState().cameraRenderState.pos;
			float pt = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
			PoseStack pose = context.poseStack();
			int color = 0xFF000000 | c.ghostHitboxColor;
			for (Entity e : mc.level.entitiesForRendering()) {
				if (!(e instanceof Creeper) || !e.isAlive() || e.distanceToSqr(mc.player) > 64 * 64) continue;
				double x = Mth.lerp(pt, e.xOld, e.getX());
				double y = Mth.lerp(pt, e.yOld, e.getY());
				double z = Mth.lerp(pt, e.zOld, e.getZ());
				AABB box = e.getBoundingBox().move(x - e.getX() - cam.x, y - e.getY() - cam.y, z - e.getZ() - cam.z);
				context.submitNodeCollector().submitShapeOutline(pose, Shapes.create(box), RenderTypes.lines(), color, 2f, false);
			}
		});
	}

	/** In the Mist: the bottom of the Dwarven Mines, where the ghosts are. */
	public static boolean inMist() {
		Minecraft mc = Minecraft.getInstance();
		return mc.player != null && SkyblockLocation.isOnSkyblock() && "mining_3".equals(SkyblockLocation.mode()) && mc.player.getY() < 100;
	}

	public static boolean hasData() {
		return !ITEMS.isEmpty() || coins > 0 || combatXp > 0;
	}

	public static boolean shouldShow() {
		return FancyBarsConfig.get().ghostTrackerEnabled && (inMist() || hasData() && "mining_3".equals(SkyblockLocation.mode()))
				&& dev.fancybars.util.HudVisibility.allowed();
	}

	public static void toggleShowAll() {
		FancyBarsConfig c = FancyBarsConfig.get();
		c.ghostTrackerShowAll = !c.ghostTrackerShowAll;
		FancyBarsConfig.save();
		rebuild();
	}

	public static void reset() {
		RARE_ITEMS.clear();
		ITEMS.clear();
		coins = 0;
		coinPickups = 0;
		combatXp = 0;
		magicFindTotal = 0;
		magicFindDrops = 0;
		maxCombo = 0;
		uptimeMs = 0;
		lastXpTotal = -1;
		lastPurse = -1;
		rebuild();
	}

	// ---------------------------------------------------------------- tracking

	private static void activity() {
		lastActivity = System.currentTimeMillis();
	}

	private static void add(String name, long count) {
		ITEMS.computeIfAbsent(name, _ -> new long[1])[0] += count;
		activity();
	}

	private static void onChat(Component message) {
		if (!inMist()) return;
		String text = ChatFormatting.stripFormatting(message.getString());
		if (text == null) return;
		text = text.trim();
		if (text.startsWith("[Sacks] +")) {
			// the items are listed in the message's hover text: "+12 Flawed Amethyst Gemstone (Gemstone Sack)"
			Component hover = hoverOf(message);
			if (hover != null) {
				String hoverText = ChatFormatting.stripFormatting(hover.getString());
				if (hoverText != null) {
					for (String line : hoverText.split("\n")) {
						if (line.trim().startsWith("Removed")) break; // items taken out (like /gfs) aren't drops
						Matcher m = SACK_LINE.matcher(line);
						if (!m.matches()) continue;
						String name = m.group(2).trim();
						// already counted from its RARE DROP message; and things like cobblestone aren't ghost drops
						if (RARE_ITEMS.contains(name.toLowerCase(Locale.ROOT)) || IGNORED.contains(name.toLowerCase(Locale.ROOT))) continue;
						add(name, Long.parseLong(m.group(1).replace(",", "")));
					}
				}
			}
			return;
		}
		Matcher m = RARE_DROP.matcher(text);
		if (m.matches()) {
			// the same message twice in a moment (another mod repeating it) is one drop
			long now = System.currentTimeMillis();
			if (text.equals(lastRareDrop) && now - lastRareDropAt < 500) return;
			lastRareDrop = text;
			lastRareDropAt = now;
			String name = m.group(2).trim();
			// the first time: anything the sack message already counted for this item was this same drop
			if (RARE_ITEMS.add(name.toLowerCase(Locale.ROOT))) ITEMS.remove(name);
			add(name, m.group(1) == null ? 1 : Long.parseLong(m.group(1)));
			if (m.group(3) != null) {
				magicFindTotal += Integer.parseInt(m.group(3));
				magicFindDrops++;
			}
			return;
		}
		m = CHARM.matcher(text);
		if (m.find()) {
			// "CHARM! You charmed the Ghost and received 3 Ghost Shards!" → Ghost Shard x3
			String name = m.group(2).trim();
			if (name.endsWith("s")) name = name.substring(0, name.length() - 1);
			add(name, Long.parseLong(m.group(1)));
			return;
		}
		m = KILL_COMBO.matcher(text);
		if (m.find()) {
			maxCombo = Math.max(maxCombo, Integer.parseInt(m.group(1)));
			activity();
		}
	}

	private static @Nullable Component hoverOf(Component message) {
		return message.visit((Style style, String part) -> style.getHoverEvent() instanceof HoverEvent.ShowText(Component value)
				? Optional.of(value) : Optional.<Component>empty(), Style.EMPTY).orElse(null);
	}

	/** Action bar text (from the status bar tracker, before anything is hidden from it). */
	public static void onActionBar(String raw) {
		if (!inMist()) return;
		String text = ChatFormatting.stripFormatting(raw);
		if (text == null) return;
		Matcher m = COMBAT_XP.matcher(text);
		if (!m.find()) return;
		double gain = parse(m.group(1));
		if (m.group(3) == null) {
			// "(1,234/5,000)": count the change in the total, so the same message shown again isn't counted twice
			double total = parse(m.group(2));
			if (lastXpTotal >= 0 && total > lastXpTotal) combatXp += total - lastXpTotal;
			else if (lastXpTotal >= 0 && total < lastXpTotal) combatXp += gain; // levelled up
			if (total != lastXpTotal) activity();
			lastXpTotal = total;
		} else if (!m.group().equals(lastXpText)) {
			combatXp += gain;
			activity();
		}
		lastXpText = m.group();
	}

	private static double parse(String s) {
		try {
			return Double.parseDouble(s.replace(",", ""));
		} catch (NumberFormatException e) {
			return 0;
		}
	}

	private static void tick() {
		Minecraft mc = Minecraft.getInstance();
		boolean mist = inMist();
		if (mist && System.currentTimeMillis() - lastActivity < IDLE_MS) uptimeMs += 50;
		if (++tickCount % 10 != 0) return;
		// coins picked up: the purse going up while out here (not while a menu is open, so selling things doesn't count)
		long purse = mist ? purse(mc) : -1;
		if (purse >= 0) {
			if (lastPurse >= 0 && purse > lastPurse && purse - lastPurse < 1_000_000 && !(mc.gui.screen() instanceof AbstractContainerScreen<?>)) {
				coins += purse - lastPurse;
				coinPickups++;
				activity();
			}
			lastPurse = purse;
		} else if (!mist) {
			lastPurse = -1;
		}
		if (FancyBarsConfig.get().ghostTrackerEnabled && (mist || hasData())) rebuild();
	}

	/** The purse from the sidebar ("Purse: 1,234,567"), or -1. */
	private static long purse(Minecraft mc) {
		if (mc.level == null) return -1;
		Scoreboard board = mc.level.getScoreboard();
		Objective objective = board.getDisplayObjective(DisplaySlot.SIDEBAR);
		if (objective == null) return -1;
		for (PlayerScoreEntry entry : board.listPlayerScores(objective)) {
			PlayerTeam team = board.getPlayersTeam(entry.owner());
			String line = ChatFormatting.stripFormatting(PlayerTeam.formatNameForTeam(team, Component.literal(entry.owner())).getString());
			if (line == null) continue;
			Matcher m = PURSE.matcher(line.trim());
			if (m.find()) return Long.parseLong(m.group(1).replace(",", ""));
		}
		return -1;
	}

	// ---------------------------------------------------------------- values

	private static Map<String, ItemRepo.Entry> byName = Map.of();
	private static int byNameSize = -1;

	/** The item list entry for a display name ("Flawed Amethyst Gemstone"), or null. */
	private static ItemRepo.@Nullable Entry entry(String name) {
		List<ItemRepo.Entry> entries = ItemRepo.entries();
		if (entries.size() != byNameSize) {
			Map<String, ItemRepo.Entry> map = new HashMap<>();
			for (ItemRepo.Entry e : entries) {
				String plain = ChatFormatting.stripFormatting(e.name());
				if (plain != null) map.putIfAbsent(plain.trim().toLowerCase(Locale.ROOT), e);
			}
			byName = map;
			byNameSize = entries.size();
		}
		return byName.get(name.toLowerCase(Locale.ROOT));
	}

	private static double priceOf(String name) {
		ItemRepo.Entry e = entry(name);
		String id = e != null ? e.id() : name.toUpperCase(Locale.ROOT).replace(' ', '_');
		double v = ItemValue.valueOf(id);
		return v > 0 ? v : 0;
	}

	private static int colorOf(String name) {
		ItemRepo.Entry e = entry(name);
		if (e != null) {
			TextColor c = e.stack().getHoverName().getStyle().getColor();
			if (c == null) {
				int rarity = dev.fancybars.items.RarityBackground.colorOf(e.stack());
				if (rarity != -1) return rarity;
			} else {
				return c.getValue();
			}
		}
		return 0xFFFFFF;
	}

	static String shortNumber(double v) {
		if (v >= 1e9) return trim(v / 1e9) + "b";
		if (v >= 1e6) return trim(v / 1e6) + "m";
		if (v >= 1e3) return trim(v / 1e3) + "k";
		return String.valueOf(Math.round(v));
	}

	private static String trim(double v) {
		return v >= 100 ? String.valueOf(Math.round(v)) : String.format(Locale.ROOT, "%.1f", v).replace(".0", "");
	}

	private static String comma(double v) {
		return String.format(Locale.ENGLISH, "%,d", Math.round(v));
	}

	private static String duration(long ms) {
		long s = ms / 1000;
		long h = s / 3600;
		long m = s / 60 % 60;
		return h > 0 ? h + "h " + m + "m " + s % 60 + "s" : m > 0 ? m + "m " + s % 60 + "s" : s % 60 + "s";
	}

	// ---------------------------------------------------------------- HUD

	private record Row(String name, long count, double value) {}

	private static void rebuild() {
		ItemValue.ensureLoaded();
		ItemRepo.ensureLoaded();
		List<Row> rows = new ArrayList<>();
		double profit = 0;
		for (Map.Entry<String, long[]> e : ITEMS.entrySet()) {
			double value = priceOf(e.getKey()) * e.getValue()[0];
			// drops without a known price aren't listed (they add nothing to the profit anyway)
			if (value <= 0) continue;
			profit += value;
			rows.add(new Row(e.getKey(), e.getValue()[0], value));
		}
		profit += coins;
		rows.sort((a, b) -> Double.compare(b.value(), a.value()));
		List<Component> out = new ArrayList<>();
		out.add(Component.literal("Ghost Profit Tracker").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
		List<Row> withCoins = new ArrayList<>(rows);
		if (coins > 0) withCoins.add(new Row("Dropped Coins", coinPickups, coins));
		withCoins.sort((a, b) -> Double.compare(b.value(), a.value()));
		// Show less: only the 5 most valuable drops; Show more: all of them
		boolean all = FancyBarsConfig.get().ghostTrackerShowAll;
		int shown = all ? withCoins.size() : Math.min(TOP, withCoins.size());
		for (Row r : withCoins.subList(0, shown)) {
			boolean isCoins = r.name().equals("Dropped Coins");
			MutableComponent line = Component.literal(comma(r.count()) + "x ").withStyle(ChatFormatting.GRAY);
			line.append(Component.literal(r.name().replace("Gemstone", "Gem")).withColor(isCoins ? 0xFFAA00 : colorOf(r.name())));
			line.append(Component.literal(" " + shortNumber(r.value())).withStyle(ChatFormatting.GOLD));
			out.add(line);
		}
		if (withCoins.size() > TOP) {
			out.add(Component.literal(all ? "▲ Show less" : "▼ Show more (" + (withCoins.size() - TOP) + ")").withStyle(ChatFormatting.DARK_GRAY));
		}
		out.add(stat("Combat XP Gained: ", comma(combatXp), ChatFormatting.DARK_AQUA));
		out.add(stat("Average Magic Find: ", magicFindDrops == 0 ? "0" : String.format(Locale.ROOT, "%.1f", magicFindTotal / magicFindDrops), ChatFormatting.AQUA));
		out.add(stat("Max Kill Combo: ", String.valueOf(maxCombo), ChatFormatting.DARK_PURPLE));
		out.add(stat("Session Profit: ", comma(profit) + " coins", ChatFormatting.GOLD));
		double hours = uptimeMs / 3_600_000.0;
		out.add(stat("Profit Per Hour: ", comma(hours > 0.0003 ? profit / hours : 0) + " coins", ChatFormatting.GOLD));
		out.add(stat("Total Uptime: ", duration(uptimeMs), ChatFormatting.GREEN));
		lines = out;
	}

	private static Component stat(String label, String value, ChatFormatting valueColor) {
		return Component.literal(label).withStyle(ChatFormatting.YELLOW).append(Component.literal(value).withStyle(valueColor));
	}

	/** A sample for the HUD editor when there's nothing tracked yet. */
	private static List<Component> sample() {
		List<Component> out = new ArrayList<>();
		out.add(Component.literal("Ghost Profit Tracker").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
		out.add(Component.literal("210x ").withStyle(ChatFormatting.GRAY).append(Component.literal("Flawed Amethyst Gem").withStyle(ChatFormatting.GREEN)).append(Component.literal(" 125k").withStyle(ChatFormatting.GOLD)));
		out.add(Component.literal("3x ").withStyle(ChatFormatting.GRAY).append(Component.literal("Volta").withStyle(ChatFormatting.BLUE)).append(Component.literal(" 3.5k").withStyle(ChatFormatting.GOLD)));
		out.add(stat("Session Profit: ", "543,822 coins", ChatFormatting.GOLD));
		out.add(stat("Profit Per Hour: ", "26,170,085 coins", ChatFormatting.GOLD));
		out.add(stat("Total Uptime: ", "1m 14s", ChatFormatting.GREEN));
		return out;
	}

	public static List<Component> shownLines() {
		return lines.isEmpty() ? sample() : lines;
	}

	private static final int LINE_H = 10;
	private static final int PAD = 3;

	public static float[] size(List<Component> list) {
		Font font = Minecraft.getInstance().font;
		int w = 0;
		for (Component c : list) w = Math.max(w, font.width(c));
		return new float[]{w + PAD * 2, list.size() * LINE_H + PAD * 2 - 1};
	}

	public static float[] position(int screenW, int screenH, List<Component> list) {
		FancyBarsConfig c = FancyBarsConfig.get();
		if (c.ghostTrackerX < 0 || c.ghostTrackerY < 0) return new float[]{6, Math.round(screenH * 0.3f)};
		return new float[]{c.ghostTrackerX * screenW, c.ghostTrackerY * screenH};
	}

	private static void render(GuiGraphicsExtractor graphics) {
		List<Component> list = lines.isEmpty() ? (inMist() ? sampleEmpty() : List.of()) : lines;
		if (list.isEmpty()) return;
		float[] pos = position(graphics.guiWidth(), graphics.guiHeight(), list);
		renderAt(graphics, pos[0], pos[1], FancyBarsConfig.get().ghostTrackerScale, list, 1);
	}

	/** Before anything has dropped: just the title and the stats at zero. */
	private static List<Component> sampleEmpty() {
		rebuild();
		return lines;
	}

	public static void renderAt(GuiGraphicsExtractor graphics, float x, float y, float scale, List<Component> list, float alpha) {
		Font font = Minecraft.getInstance().font;
		int a = Math.round(alpha * 255);
		Matrix3x2fStack pose = graphics.pose();
		pose.pushMatrix();
		pose.translate(x, y);
		pose.scale(scale, scale);
		float[] size = size(list);
		if (FancyBarsConfig.get().ghostTrackerBackground) Theme.hudPanel(graphics, 0, 0, Math.round(size[0]), Math.round(size[1]), 4, 0.88f, a);
		int ty = PAD;
		for (Component c : list) {
			graphics.text(font, c, PAD, ty, (a << 24) | 0xFFFFFF, true);
			ty += LINE_H;
		}
		pose.popMatrix();
	}
}
