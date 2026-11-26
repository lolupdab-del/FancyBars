package dev.fancybars.hud;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.numbers.NumberFormat;
import net.minecraft.network.chat.numbers.StyledFormat;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerScoreEntry;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;

import dev.fancybars.config.FancyBarsConfig;
import dev.fancybars.util.SkyblockLocation;

/**
 * Island Scoreboard: draws the sidebar on a backdrop made from the island's own blocks, with colours and a few slow
 * pixel particles to match (snow in Jerry's Workshop, embers on the Crimson Isle, gem sparkles in the Crystal Hollows).
 * When you change island it fades across to the new theme. The lines themselves are Hypixel's, drawn as Minecraft would.
 */
public final class IslandScoreboard {
	private IslandScoreboard() {}

	/** How the little particles behave. */
	enum Particles { NONE, MOTES, LEAVES, SNOW, EMBERS, ASH, RAIN, SPARKLE, PORTAL, DRIPS, BUBBLES, FIREFLIES, PETALS, GLITCH, DUST }

	/**
	 * One island's look. Colours are RGB; {@code block} is a vanilla block texture tiled behind everything (darkened by
	 * {@code tint}), {@code top}/{@code bottom} the see-through gradient over it, {@code header} the title band,
	 * {@code border} the frame and {@code accent} the line under the title and the shimmer.
	 */
	record Theme(String block, int tint, int top, int bottom, int header, int border, int accent, Particles particles, int count, int... colors) {
		Identifier texture() {
			return Identifier.withDefaultNamespace("textures/block/" + block + ".png");
		}
	}

	private static final Theme DEFAULT = new Theme("oak_planks", 0x5A5A5A, 0x1C1A14, 0x100E0A, 0x3A2E12, 0x6B5520, 0xFFC94A, Particles.MOTES, 6, 0xFFE08A);

	/** Hypixel's island ids (the mode in its location packet) to their look. */
	private static final Map<String, Theme> THEMES = Map.ofEntries(
			// two floating islands of grass, dirt and an oak tree
			Map.entry("dynamic", new Theme("moss_block", 0x707070, 0x16240F, 0x0E1408, 0x2C4A1A, 0x4F7A2C, 0x9BE05A, Particles.MOTES, 8, 0xC8F08A, 0xFFF3A0)),
			// the village, stone and oak, SkyBlock gold
			Map.entry("hub", new Theme("stone_bricks", 0x606060, 0x1E1C17, 0x12110D, 0x4A3A14, 0x7A6228, 0xFFCF4D, Particles.MOTES, 6, 0xFFE59A, 0xFFFFFF)),
			// the Barn and Mushroom Desert: hay and wheat
			Map.entry("farming_1", new Theme("hay_block_side", 0x5C5C5C, 0x262010, 0x161208, 0x5A4612, 0x8C6E1E, 0xF2D25C, Particles.DUST, 9, 0xE8CF7A, 0xC9A94A)),
			// greenhouses and barns, crops everywhere
			Map.entry("garden", new Theme("moss_block", 0x6A6A6A, 0x14220E, 0x0C1408, 0x2E5218, 0x5C8A2E, 0xB6F06A, Particles.LEAVES, 8, 0x7CC24A, 0xA8D85A, 0xE0F08A)),
			// birch, spruce, dark oak, acacia and jungle woods
			Map.entry("foraging_1", new Theme("spruce_log", 0x5C5C5C, 0x18200F, 0x0E140A, 0x2E4418, 0x4E6E2A, 0x8FD45A, Particles.LEAVES, 10, 0x5E9E3A, 0x8BC34A, 0xC9A23A, 0xD98A2B)),
			// Moonglade Marsh: mangroves and moonlight over the water
			Map.entry("foraging_2", new Theme("mangrove_planks", 0x585858, 0x101C1E, 0x0A1012, 0x1E3A3C, 0x35686A, 0x7FE3D4, Particles.FIREFLIES, 9, 0xC8FF8A, 0xA6F0FF)),
			// Torrhus Canyon: red rock and dry wind
			Map.entry("foraging_3", new Theme("red_sandstone", 0x5A5A5A, 0x261410, 0x160C08, 0x5A2A16, 0x8C4A24, 0xFF9A4A, Particles.DUST, 10, 0xE0A070, 0xC07A4A)),
			// grass and gravel, cobwebs and hourly rain
			Map.entry("combat_1", new Theme("cobblestone", 0x505050, 0x16141C, 0x0C0B10, 0x2E2238, 0x4E3A5C, 0xE0404A, Particles.RAIN, 12, 0x8AA0C8, 0xA8B8D8)),
			// end stone, obsidian and endermen
			Map.entry("combat_3", new Theme("end_stone_bricks", 0x505050, 0x1A1224, 0x0E0A14, 0x3A1E52, 0x6A3A8A, 0xD58CFF, Particles.PORTAL, 12, 0xC25BFF, 0xE6A8FF, 0x8A3AD8)),
			// the Nether: netherrack, blackstone, ashfall
			Map.entry("crimson_isle", new Theme("netherrack", 0x5A5A5A, 0x2A0E0A, 0x160606, 0x5A1610, 0x8C2A18, 0xFF7A2E, Particles.ASH, 12, 0x8A8480, 0xFF8A2E, 0xFFC04A)),
			// Kuudra's Hollow: a lava arena
			Map.entry("kuudra", new Theme("blackstone", 0x6A6A6A, 0x2A0C06, 0x180404, 0x6A1A08, 0xA03A10, 0xFFA03A, Particles.EMBERS, 14, 0xFF6A1A, 0xFFAA2E, 0xFFE06A)),
			// gold ore in stone, with a yellow roof
			Map.entry("mining_1", new Theme("stone", 0x5A5A5A, 0x1E1C14, 0x12100A, 0x4A3C10, 0x8A7020, 0xFFE04A, Particles.SPARKLE, 9, 0xFFE04A, 0xFFF6B0)),
			// lapis, slime, diamonds and obsidian, deeper and darker
			Map.entry("mining_2", new Theme("deepslate", 0x6A6A6A, 0x101624, 0x080A12, 0x1A2A4E, 0x2E4A80, 0x6AB4FF, Particles.DRIPS, 8, 0x6AA8E0, 0x9FD0FF)),
			// mithril and titanium, a breeze in the tunnels
			Map.entry("mining_3", new Theme("cyan_terracotta", 0x585858, 0x0E1C20, 0x081014, 0x16404A, 0x2A6A76, 0x5FE8E0, Particles.MOTES, 10, 0x7FE6E0, 0xC8FFF8, 0xB0B8C8)),
			// hard stone veined with every gemstone colour
			Map.entry("crystal_hollows", new Theme("amethyst_block", 0x4E4E4E, 0x1A1222, 0x0C0812, 0x3A2050, 0x6A3E8A, 0xE58CFF, Particles.SPARKLE, 12, 0xFF5A6A, 0xFFB04A, 0xC66AFF, 0x6AE06A, 0x5AAEFF, 0xFFE85A)),
			// Glacite Mineshafts: packed ice and cold
			Map.entry("mineshaft", new Theme("packed_ice", 0x5A5A5A, 0x101C26, 0x081016, 0x1E3A52, 0x3A6A8E, 0xA8E4FF, Particles.SNOW, 12, 0xFFFFFF, 0xD8F0FF)),
			// Jerry's Workshop: snow, ice and Christmas red
			Map.entry("winter", new Theme("snow", 0x484848, 0x121C26, 0x0A1018, 0x1E3A5A, 0x8C2A2A, 0xFF5A5A, Particles.SNOW, 16, 0xFFFFFF, 0xE0F2FF)),
			// statues, prismarine roofs and a purple crystal cave
			Map.entry("dungeon_hub", new Theme("mossy_stone_bricks", 0x585858, 0x0E1A1A, 0x080E0E, 0x1A3A3A, 0x2E6A64, 0x6AE0C8, Particles.DUST, 7, 0xA0D8C8, 0xC8A0FF)),
			// the Catacombs: old stone, bones and dark rooms
			Map.entry("dungeon", new Theme("cracked_stone_bricks", 0x4A4A4A, 0x1A1212, 0x0C0808, 0x3A1414, 0x5A2222, 0xE04A3A, Particles.MOTES, 7, 0xD8D0C0, 0x8A7A6A)),
			// the Rift: purple and lime, things not quite right
			Map.entry("rift", new Theme("crying_obsidian", 0x5A5A5A, 0x1A0E24, 0x0C0614, 0x3A1658, 0x6A2A9A, 0xB4FF5A, Particles.GLITCH, 10, 0xB4FF5A, 0xE07AFF, 0x5AF0FF)),
			// Backwater Bayou: swamp water, mud and smog
			Map.entry("fishing_1", new Theme("mud_bricks", 0x505050, 0x121A12, 0x0A0E0A, 0x2A3A1E, 0x4A5E30, 0x9CC85A, Particles.BUBBLES, 9, 0x9FD8C0, 0xC8F0E0)),
			// a dim auction hall
			Map.entry("dark_auction", new Theme("polished_blackstone_bricks", 0x6A6A6A, 0x160E1A, 0x0A060C, 0x2E1838, 0x52305E, 0xC88AFF, Particles.SPARKLE, 5, 0xC88AFF, 0xFFFFFF)),
			// the Safari: savanna grass and acacia
			Map.entry("safari", new Theme("acacia_planks", 0x585858, 0x22180E, 0x140E08, 0x523214, 0x8A5A22, 0xFFB45A, Particles.DUST, 9, 0xE8C080, 0xC8A060)),
			// Lotus Atoll: lagoon water and lotus petals
			Map.entry("lotus_atoll", new Theme("prismarine_bricks", 0x585858, 0x0E1A20, 0x081014, 0x1A4048, 0x3A7A80, 0xFF9AD0, Particles.PETALS, 10, 0xFFA8D8, 0xFFD0E8, 0xFFFFFF)));

	/** How long the fade to a new island's theme takes. */
	private static final long FADE_MS = 900;

	private static Theme current = DEFAULT;
	private static @Nullable Theme previous;
	private static long changedAt;
	private static String lastMode = "";

	public static void init() {
		dev.fancybars.util.Perf.replaceElement(VanillaHudElements.SCOREBOARD, original -> (graphics, delta) -> {
			FancyBarsConfig c = FancyBarsConfig.get();
			if (!c.scoreboardShown) return;
			// left where Minecraft puts it and not themed: Minecraft draws it
			if (!themed() && c.scoreboardX < 0 && c.scoreboardY < 0 && c.scoreboardScale == 1) {
				original.extractRenderState(graphics, delta);
				return;
			}
			Objective objective = sidebar();
			if (objective == null) return;
			graphics.nextStratum();
			Layout l = layout(objective.getDisplayName(), lines(objective));
			float[] pos = position(graphics.guiWidth(), graphics.guiHeight(), l);
			renderAt(graphics, pos[0], pos[1], c.scoreboardScale, l, 1);
		});
	}

	private static boolean themed() {
		return FancyBarsConfig.get().islandScoreboard && SkyblockLocation.isOnSkyblock();
	}

	/** The objective the sidebar would show (a team-coloured one first, like Minecraft). */
	private static @Nullable Objective sidebar() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null || mc.player == null) return null;
		Scoreboard board = mc.level.getScoreboard();
		PlayerTeam team = board.getPlayersTeam(mc.player.getScoreboardName());
		if (team != null && team.getColor().isPresent()) {
			Objective o = board.getDisplayObjective(team.getColor().get().displaySlot());
			if (o != null) return o;
		}
		return board.getDisplayObjective(DisplaySlot.SIDEBAR);
	}

	private static Theme themeFor(String mode) {
		return THEMES.getOrDefault(mode, DEFAULT);
	}

	public record Line(Component name, Component score, int scoreWidth) {}

	/** What gets drawn, and how big it is before scaling. */
	public record Layout(Component title, List<Line> lines, boolean themed, int w, int h, int headerH, int pad) {}

	private static final Comparator<PlayerScoreEntry> ORDER = Comparator.comparingInt(PlayerScoreEntry::value).reversed()
			.thenComparing(PlayerScoreEntry::owner, String.CASE_INSENSITIVE_ORDER);

	private static List<Line> lines(Objective objective) {
		Font font = Minecraft.getInstance().font;
		Scoreboard board = objective.getScoreboard();
		NumberFormat format = objective.numberFormatOrDefault(StyledFormat.SIDEBAR_DEFAULT);
		List<Line> lines = new ArrayList<>();
		board.listPlayerScores(objective).stream().filter(e -> !e.isHidden()).sorted(ORDER).limit(15).forEach(e -> {
			Component name = PlayerTeam.formatNameForTeam(board.getPlayersTeam(e.owner()), e.ownerName());
			Component score = e.formatValue(format);
			lines.add(new Line(name, score, font.width(score)));
		});
		return lines;
	}

	private static Layout layout(Component title, List<Line> lines) {
		Font font = Minecraft.getInstance().font;
		boolean themed = themed();
		int spacer = font.width(": ");
		int textW = font.width(title);
		for (Line l : lines) textW = Math.max(textW, font.width(l.name()) + (l.scoreWidth() > 0 ? spacer + l.scoreWidth() : 0));
		int lineH = font.lineHeight;
		if (!themed) {
			// Minecraft's own sizes
			return new Layout(title, lines, false, textW + 4, lineH + 1 + lines.size() * lineH, lineH + 1, 2);
		}
		int pad = 4;
		int headerH = lineH + 4;
		return new Layout(title, lines, true, textW + pad * 2, headerH + 2 + lines.size() * lineH + pad, headerH, pad);
	}

	// ---------------------------------------------------------------- where it goes

	/**
	 * Top-left corner on screen. Left alone, it sits where Minecraft puts it: against the right edge, around the middle.
	 * Once moved it keeps to the side it was dropped nearest (its right edge stays put on the right half of the screen),
	 * so lines getting longer or shorter don't push it off the edge.
	 */
	public static float[] position(int screenW, int screenH, Layout l) {
		FancyBarsConfig c = FancyBarsConfig.get();
		float s = c.scoreboardScale;
		float w = l.w() * s, h = l.h() * s;
		if (c.scoreboardX < 0 || c.scoreboardY < 0) {
			int lineH = Minecraft.getInstance().font.lineHeight;
			float bottom = screenH / 2f + l.lines().size() * lineH / 3f + (l.themed() ? l.pad() : 0) * s;
			return new float[]{screenW - 2 - w, bottom - h};
		}
		float x = c.scoreboardRight ? c.scoreboardX * screenW - w : c.scoreboardX * screenW;
		float y = c.scoreboardY * screenH;
		return new float[]{Math.clamp(x, 0, Math.max(0, screenW - w)), Math.clamp(y, 0, Math.max(0, screenH - h))};
	}

	public static void moveTo(float x, float y, int screenW, int screenH) {
		FancyBarsConfig c = FancyBarsConfig.get();
		Layout l = shownLayout();
		float w = l.w() * c.scoreboardScale, h = l.h() * c.scoreboardScale;
		x = Math.clamp(x, 0, Math.max(0, screenW - w));
		y = Math.clamp(y, 0, Math.max(0, screenH - h));
		c.scoreboardRight = x + w / 2 > screenW / 2f;
		c.scoreboardX = (c.scoreboardRight ? x + w : x) / screenW;
		c.scoreboardY = y / screenH;
	}

	public static void reset() {
		FancyBarsConfig c = FancyBarsConfig.get();
		c.scoreboardX = -1;
		c.scoreboardY = -1;
		c.scoreboardRight = true;
		c.scoreboardScale = 1;
		FancyBarsConfig.save();
	}

	/** The real scoreboard if there is one, otherwise a SkyBlock-style sample for the editor. */
	public static Layout shownLayout() {
		Objective objective = sidebar();
		if (objective != null) return layout(objective.getDisplayName(), lines(objective));
		Font font = Minecraft.getInstance().font;
		String[] sample = {"§711/26/26 §8m12A", "", " Late Winter §e3rd", " §77:40pm §e☀", " §7⏣ §bDwarven Village", "", "Purse: §61,234,567", "Bits: §b1,200", "", "§ewww.hypixel.net"};
		List<Line> lines = new ArrayList<>();
		for (String s : sample) lines.add(new Line(Component.literal(s), Component.empty(), 0));
		Component title = Component.literal("§e§lSKYBLOCK");
		return layout(title, lines);
	}

	// ---------------------------------------------------------------- drawing

	public static void renderAt(GuiGraphicsExtractor graphics, float x, float y, float scale, Layout l, float alpha) {
		Font font = Minecraft.getInstance().font;
		var pose = graphics.pose();
		pose.pushMatrix();
		pose.translate(x, y);
		pose.scale(scale, scale);
		int w = l.w(), h = l.h(), pad = l.pad();
		int a = Math.round(Mth.clamp(alpha, 0, 1) * 255);
		int white = a << 24 | 0xFFFFFF;
		if (!l.themed()) {
			Minecraft mc = Minecraft.getInstance();
			int body = mc.options.getBackgroundColor(0.3f), head = mc.options.getBackgroundColor(0.4f);
			graphics.fill(0, 0, w, l.headerH(), scaleAlpha(head, alpha));
			graphics.fill(0, l.headerH(), w, h, scaleAlpha(body, alpha));
			graphics.text(font, l.title(), (w - font.width(l.title())) / 2, 1, white, false);
			int ly = l.headerH();
			for (Line line : l.lines()) {
				graphics.text(font, line.name(), pad, ly, white, false);
				if (line.scoreWidth() > 0) graphics.text(font, line.score(), w - pad - line.scoreWidth(), ly, white, false);
				ly += font.lineHeight;
			}
			pose.popMatrix();
			return;
		}

		long now = System.currentTimeMillis();
		String mode = SkyblockLocation.mode();
		if (!mode.equals(lastMode)) {
			Theme next = themeFor(mode);
			if (next != current) {
				// the first island after joining just appears; later ones fade across
				previous = lastMode.isEmpty() ? null : current;
				current = next;
				changedAt = now;
			}
			lastMode = mode;
		}
		float fade = previous == null ? 1 : Mth.clamp((now - changedAt) / (float) FADE_MS, 0, 1);
		if (fade >= 1) previous = null;

		int headerH = l.headerH();
		boolean animate = FancyBarsConfig.get().islandScoreboardAnimations;
		backdrop(graphics, current, 0, 0, w, h, headerH, alpha, now, animate);
		if (previous != null) backdrop(graphics, previous, 0, 0, w, h, headerH, alpha * (1 - fade), now, animate);
		if (animate) {
			graphics.enableScissor(1, headerH + 1, w - 1, h - 1);
			particles(graphics, current, 1, headerH + 1, w - 2, h - headerH - 2, now, alpha * fade);
			if (previous != null) particles(graphics, previous, 1, headerH + 1, w - 2, h - headerH - 2, now, alpha * (1 - fade));
			graphics.disableScissor();
		}

		graphics.text(font, l.title(), (w - font.width(l.title())) / 2, 3, white, true);
		int ly = headerH + 2;
		for (Line line : l.lines()) {
			graphics.text(font, line.name(), pad, ly, white, true);
			if (line.scoreWidth() > 0) graphics.text(font, line.score(), w - pad - line.scoreWidth(), ly, white, true);
			ly += font.lineHeight;
		}
		pose.popMatrix();
	}

	private static int scaleAlpha(int argb, float alpha) {
		return Math.round((argb >>> 24) * Mth.clamp(alpha, 0, 1)) << 24 | (argb & 0xFFFFFF);
	}

	// ---------------------------------------------------------------- the panel

	private static int argb(float alpha, int rgb) {
		return (Math.round(Mth.clamp(alpha, 0, 1) * 255) << 24) | (rgb & 0xFFFFFF);
	}

	private static int lerp(int a, int b, float t) {
		int r = Math.round(Mth.lerp(t, a >> 16 & 255, b >> 16 & 255));
		int g = Math.round(Mth.lerp(t, a >> 8 & 255, b >> 8 & 255));
		int bl = Math.round(Mth.lerp(t, a & 255, b & 255));
		return r << 16 | g << 8 | bl;
	}

	private static void backdrop(GuiGraphicsExtractor graphics, Theme t, int x, int y, int w, int h, int headerH, float alpha, long now, boolean animate) {
		if (alpha <= 0.01f) return;
		int right = x + w, bottom = y + h;
		// the island's block, tiled and darkened
		Identifier tex = t.texture();
		int tint = argb(alpha, t.tint());
		for (int ty = y + 1; ty < bottom - 1; ty += 16) {
			for (int tx = x + 1; tx < right - 1; tx += 16) {
				int cw = Math.min(16, right - 1 - tx), ch = Math.min(16, bottom - 1 - ty);
				graphics.blit(RenderPipelines.GUI_TEXTURED, tex, tx, ty, 0, 0, cw, ch, 16, 16, tint);
			}
		}
		// colour wash over it, so the text always reads
		graphics.fillGradient(x + 1, y + 1, right - 1, bottom - 1, argb(alpha * 0.80f, t.top()), argb(alpha * 0.90f, t.bottom()));
		// title band
		graphics.fillGradient(x + 1, y + 1, right - 1, y + headerH, argb(alpha * 0.85f, lerp(t.header(), 0xFFFFFF, 0.08f)), argb(alpha * 0.85f, t.header()));
		// a slow glow on the line under the title
		float pulse = animate ? 0.75f + 0.25f * (float) Math.sin(now / 900.0) : 1;
		graphics.fill(x + 1, y + headerH, right - 1, y + headerH + 1, argb(alpha * pulse, t.accent()));
		if (animate) shimmer(graphics, t, x + 1, y + 1, w - 2, headerH - 1, alpha, now);
		// a pixel frame, lighter on top like a lit block edge
		int border = argb(alpha, t.border());
		graphics.fill(x, y, right, y + 1, argb(alpha, lerp(t.border(), 0xFFFFFF, 0.25f)));
		graphics.fill(x, bottom - 1, right, bottom, argb(alpha, lerp(t.border(), 0x000000, 0.35f)));
		graphics.fill(x, y + 1, x + 1, bottom - 1, border);
		graphics.fill(right - 1, y + 1, right, bottom - 1, border);
	}

	/** A soft band of light that crosses the title every few seconds. */
	private static void shimmer(GuiGraphicsExtractor graphics, Theme t, int x, int y, int w, int h, float alpha, long now) {
		long period = 7000;
		float p = (now % period) / 1400f; // crosses in the first 1.4 s, then rests
		if (p > 1) return;
		int band = 14;
		int cx = x - band + Math.round(p * (w + band * 2));
		graphics.enableScissor(x, y, x + w, y + h);
		for (int i = -band; i <= band; i++) {
			float a = 1 - Math.abs(i) / (float) band;
			graphics.fill(cx + i, y, cx + i + 1, y + h, argb(alpha * a * a * 0.18f, lerp(t.accent(), 0xFFFFFF, 0.5f)));
		}
		graphics.disableScissor();
	}

	// ---------------------------------------------------------------- particles

	/** A repeatable random number in 0..1 for particle {@code i}, salt {@code k}. */
	private static float rand(int i, int k) {
		int v = i * 0x9E3779B1 + k * 0x85EBCA77;
		v ^= v >>> 15;
		v *= 0x2C1B3C6D;
		v ^= v >>> 12;
		v *= 0x297A2D39;
		v ^= v >>> 15;
		return (v & 0xFFFFFF) / (float) 0x1000000;
	}

	/**
	 * Each particle's place is worked out from the time alone (no state), so they keep going smoothly whatever the
	 * panel's size, and stop costing anything when the option is off.
	 */
	private static void particles(GuiGraphicsExtractor graphics, Theme t, int x, int y, int w, int h, long now, float alpha) {
		if (t.particles() == Particles.NONE || alpha <= 0.01f || w <= 0 || h <= 0) return;
		double sec = now / 1000.0;
		for (int i = 0; i < t.count(); i++) {
			int color = t.colors()[i % t.colors().length];
			float r1 = rand(i, 1), r2 = rand(i, 2), r3 = rand(i, 3);
			switch (t.particles()) {
				case MOTES -> {
					// drifting up slowly, swaying
					double f = frac(sec / (9 + r2 * 6) + r1);
					float px = x + r3 * w + (float) Math.sin(sec * 0.8 + i) * 3;
					float py = y + h - (float) f * (h + 2);
					dot(graphics, px, py, 1, color, alpha * 0.55f * life(f));
				}
				case DUST -> {
					// carried sideways on the wind
					double f = frac(sec / (8 + r2 * 5) + r1);
					float px = x + (float) f * (w + 4) - 2;
					float py = y + r3 * h + (float) Math.sin(sec * 1.3 + i * 2) * 2;
					dot(graphics, px, py, 1, color, alpha * 0.5f * life(f));
				}
				case LEAVES, PETALS -> {
					// falling, rocking side to side, a 2x2 pixel leaf
					double f = frac(sec / (7 + r2 * 5) + r1);
					float px = x + r3 * w + (float) Math.sin(sec * 1.6 + i * 3) * 4;
					float py = y + (float) f * (h + 4) - 2;
					dot(graphics, px, py, 2, color, alpha * 0.7f * life(f));
				}
				case SNOW -> {
					double f = frac(sec / (6 + r2 * 6) + r1);
					float px = x + r3 * w + (float) Math.sin(sec + i) * 2.5f;
					float py = y + (float) f * (h + 3) - 2;
					dot(graphics, px, py, r2 > 0.6f ? 2 : 1, color, alpha * 0.8f * life(f));
				}
				case RAIN -> {
					// quick streaks, slanting a little
					double f = frac(sec / (0.9 + r2 * 0.5) + r1);
					float px = x + r3 * w - (float) f * 3;
					float py = y + (float) f * (h + 6) - 4;
					graphics.fill(Math.round(px), Math.round(py), Math.round(px) + 1, Math.round(py) + 3, argb(alpha * 0.35f, color));
				}
				case EMBERS, ASH -> {
					// embers float up and fade; ash (every other particle on the Crimson Isle) drifts down
					boolean ash = t.particles() == Particles.ASH && i % 3 != 0;
					double f = frac(sec / (ash ? 8 + r2 * 4 : 4 + r2 * 4) + r1);
					float px = x + r3 * w + (float) Math.sin(sec * 1.4 + i) * 2;
					float py = ash ? y + (float) f * (h + 2) - 1 : y + h - (float) f * (h + 2);
					int c = ash ? t.colors()[0] : t.particles() == Particles.ASH ? t.colors()[1 + i % (t.colors().length - 1)] : color;
					float a = ash ? 0.5f : 0.85f * (float) (1 - f);
					dot(graphics, px, py, 1, c, alpha * a * life(f));
				}
				case PORTAL -> {
					// End portal specks rising and curling
					double f = frac(sec / (5 + r2 * 4) + r1);
					float px = x + r3 * w + (float) Math.sin(f * Math.PI * 2 + i) * 4;
					float py = y + h - (float) f * (h + 2);
					dot(graphics, px, py, r2 > 0.7f ? 2 : 1, color, alpha * 0.7f * life(f));
				}
				case SPARKLE -> {
					// twinkle in place, then appear somewhere else
					double cycleLen = 2.5 + r2 * 2;
					double c = sec / cycleLen + r1;
					int cycle = (int) Math.floor(c);
					double f = c - cycle;
					float px = x + 2 + rand(i, 10 + cycle) * (w - 4);
					float py = y + 2 + rand(i, 20 + cycle) * (h - 4);
					float a = (float) Math.max(0, Math.sin(f * Math.PI));
					int col = t.colors()[(i + cycle) % t.colors().length];
					dot(graphics, px, py, 1, col, alpha * a * 0.9f);
					if (a > 0.6f) {
						// a little plus shape at its brightest
						float b = alpha * (a - 0.6f) * 1.5f;
						dot(graphics, px - 1, py, 1, col, b);
						dot(graphics, px + 1, py, 1, col, b);
						dot(graphics, px, py - 1, 1, col, b);
						dot(graphics, px, py + 1, 1, col, b);
					}
				}
				case DRIPS -> {
					// a drop forms, then falls faster and faster
					double f = frac(sec / (3.5 + r2 * 3) + r1);
					float px = x + 2 + r3 * (w - 4);
					float py = f < 0.4 ? y : y + (float) Math.pow((f - 0.4) / 0.6, 2) * (h + 2);
					dot(graphics, px, py, 1, color, alpha * (f < 0.4 ? (float) f * 1.5f : 0.75f));
				}
				case BUBBLES -> {
					double f = frac(sec / (5 + r2 * 4) + r1);
					float px = x + r3 * w + (float) Math.sin(sec * 2 + i) * 1.5f;
					float py = y + h - (float) f * (h + 2);
					dot(graphics, px, py, r2 > 0.5f ? 2 : 1, color, alpha * 0.45f * life(f));
				}
				case FIREFLIES -> {
					// wander about and glow on and off
					float px = x + w * (0.5f + 0.45f * (float) Math.sin(sec * (0.15 + r1 * 0.1) + r2 * 6.28));
					float py = y + h * (0.5f + 0.45f * (float) Math.sin(sec * (0.12 + r3 * 0.1) + r1 * 6.28));
					float glow = 0.5f + 0.5f * (float) Math.sin(sec * (1.2 + r2) + i);
					dot(graphics, px, py, 1, color, alpha * glow * 0.9f);
					if (glow > 0.7f) dot(graphics, px - 0.5f, py - 0.5f, 2, color, alpha * (glow - 0.7f) * 0.6f);
				}
				case GLITCH -> {
					// specks rising, and now and then a short flickering bar
					double f = frac(sec / (6 + r2 * 4) + r1);
					float px = x + r3 * w;
					float py = y + h - (float) f * (h + 2);
					dot(graphics, px, py, 1, color, alpha * 0.6f * life(f));
					int tick = (int) (sec * 8);
					if (i < 2 && rand(i, tick) > 0.93f) {
						int gy = y + Math.round(rand(i, tick + 1) * (h - 2));
						int gx = x + Math.round(rand(i, tick + 2) * (w * 0.6f));
						int gw = 6 + Math.round(rand(i, tick + 3) * w * 0.35f);
						graphics.fill(gx, gy, Math.min(x + w, gx + gw), gy + 1, argb(alpha * 0.35f, color));
					}
				}
				default -> {}
			}
		}
	}

	/** Fades in at the start and out at the end of a particle's trip. */
	private static float life(double f) {
		return (float) Math.min(1, Math.min(f, 1 - f) * 6);
	}

	private static double frac(double v) {
		return v - Math.floor(v);
	}

	private static void dot(GuiGraphicsExtractor graphics, float x, float y, int size, int rgb, float alpha) {
		if (alpha <= 0.02f) return;
		int px = Math.round(x), py = Math.round(y);
		graphics.fill(px, py, px + size, py + size, argb(alpha, rgb));
	}
}
