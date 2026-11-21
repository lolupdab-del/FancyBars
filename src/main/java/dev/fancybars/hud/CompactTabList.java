package dev.fancybars.hud;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.jspecify.annotations.Nullable;

import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.PlayerFaceExtractor;
import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.util.Util;

import dev.fancybars.config.FancyBarsConfig;
import dev.fancybars.mixin.PlayerTabOverlayAccessor;
import dev.fancybars.util.SkyblockLocation;
import dev.fancybars.util.gui.Theme;

/**
 * Compact Tab List, laid out like SkyHanni's (which follows SkyblockAddons / Skytils): Hypixel's four fixed columns of
 * 20 lines are read into their sections, empty lines are dropped, and the sections are laid out again in as few columns
 * as fit, each kept in one piece:
 * <ul>
 * <li>centred column headings ("Players (21)", "Info", "Other"),</li>
 * <li>bold section titles ("Area: Hub", "Skills:") with their lines under them, an empty line between sections,</li>
 * <li>player heads in the player list,</li>
 * <li>the footer's Active Effects and Cookie Buff moved in under "Other", and the server / store lines at the top and
 * bottom.</li>
 * </ul>
 */
public final class CompactTabList {
	private CompactTabList() {}

	private record Line(Component text, @Nullable PlayerInfo player, int width) {}

	/** A column heading (centred, no lines), or a section: an optional title and its lines. */
	private record Section(@Nullable Line title, List<Line> lines, boolean players, boolean heading) {
		int height() {
			return (title == null ? 0 : 1) + lines.size();
		}
	}

	private static final int LINE_H = 9;
	private static final int COLUMN_GAP = 12;
	/** An empty line between sections, like SkyHanni. */
	private static final int SECTION_GAP = LINE_H;
	private static final int PAD = 4;
	private static final int HEAD = 8;
	private static final long REBUILD_MS = 100;

	private static List<List<Section>> columns = List.of();
	private static List<Line> header = List.of();
	private static List<Line> footer = List.of();
	private static long builtAt;
	private static int builtFor;
	/** Toggle Tab: whether the list is open (the Tab key flips it instead of holding it). */
	private static boolean toggledOpen;
	/** "You have a God Potion active! 5 hours" in the footer. */
	private static final java.util.regex.Pattern GOD_POTION = java.util.regex.Pattern.compile("You have a God Potion active! (.+)");
	private static boolean tabWasDown;

	public static void init() {
		dev.fancybars.util.Perf.replaceElement(VanillaHudElements.PLAYER_LIST, original -> (graphics, delta) -> {
			if (!active()) {
				original.extractRenderState(graphics, delta);
				return;
			}
			Minecraft mc = Minecraft.getInstance();
			boolean show = FancyBarsConfig.get().compactTabToggle ? toggledOpen : mc.options.keyPlayerList.isDown();
			if (show && mc.getConnection() != null) render(graphics);
		});
		dev.fancybars.util.Perf.onTick("Tick CompactTabList", mc -> {
			boolean down = mc.options.keyPlayerList.isDown();
			if (down && !tabWasDown && mc.gui.screen() == null) toggledOpen = !toggledOpen;
			tabWasDown = down;
			if (mc.getConnection() == null) toggledOpen = false;
		});
	}

	public static boolean active() {
		return FancyBarsConfig.get().compactTabEnabled && SkyblockLocation.isOnSkyblock();
	}

	// ---------------------------------------------------------------- reading Hypixel's tab list

	private static void rebuild(int maxLines) {
		Minecraft mc = Minecraft.getInstance();
		PlayerTabOverlay tab = mc.gui.hud.getTabList();
		PlayerTabOverlayAccessor access = (PlayerTabOverlayAccessor) tab;
		Font font = mc.font;
		FancyBarsConfig c = FancyBarsConfig.get();

		// Hypixel fills the list column by column, 20 lines each, in the order of the entries' (made-up) names
		List<PlayerInfo> infos = new ArrayList<>(access.fancybars$getPlayerInfos());
		infos.sort(java.util.Comparator.comparing(info -> info.getProfile().name()));
		List<Section> sections = new ArrayList<>();
		java.util.Set<String> headings = new java.util.HashSet<>();
		Section current = null;
		Section playerList = null;
		for (PlayerInfo info : infos) {
			Component text = tab.getNameForDisplay(info);
			String plain = plain(text);
			if (plain.isBlank()) {
				current = null; // an empty line ends a section
				continue;
			}
			int indent = leadingSpaces(text);
			if (isBold(text)) {
				if (indent >= 3 || plain.trim().startsWith("Players (")) {
					// a centred column heading ("Players (22)", "Info"). Hypixel repeats it at the top of every column
					// the group carries on into: only the first one is kept, and the player list just continues
					boolean players = plain.trim().startsWith("Players");
					if (!headings.add(plain.trim())) {
						current = players ? playerList : null;
						continue;
					}
					sections.add(new Section(line(font, trimLeading(text), null), List.of(), false, true));
					if (players) {
						playerList = new Section(null, new ArrayList<>(), true, false);
						sections.add(playerList);
					}
					current = playerList != null && players ? playerList : null;
					continue;
				}
				if (indent == 0) {
					// a section title: "Area: Hub", "Skills:" (bold lines indented under one, like "MAX LEVEL", are lines)
					current = new Section(line(font, text, null), new ArrayList<>(), false, false);
					sections.add(current);
					continue;
				}
			}
			if (current == null) {
				current = new Section(null, new ArrayList<>(), false, false);
				sections.add(current);
			}
			current.lines().add(line(font, text, current.players() && c.compactTabHeads ? info : null));
		}
		// headings / titles left with nothing under them (an emptied widget) aren't shown
		sections.removeIf(s -> !s.heading() && s.title() == null && s.lines().isEmpty());

		// the footer: Active Effects, Cookie Buff and the like go in under "Other"; the store line stays at the bottom
		List<Line> footerLines = lines(font, access.fancybars$getFooter());
		List<Line> bottom = new ArrayList<>();
		List<Section> other = new ArrayList<>();
		Section otherCurrent = null;
		for (Line l : footerLines) {
			String plain = plain(l.text()).trim();
			if (isAdvert(plain)) {
				if (!c.compactTabHideAds) bottom.add(l);
				continue;
			}
			// Active Effects condensed like SkyHanni: the God Potion and its time, without the "/effects" hints
			if (plain.matches("You have \\d+ (non-god )?effects?.*") || plain.startsWith("Use \"/effects\"") || plain.startsWith("Use /effects")) continue;
			java.util.regex.Matcher god = GOD_POTION.matcher(plain);
			if (god.matches()) {
				if (otherCurrent == null) {
					otherCurrent = new Section(null, new ArrayList<>(), false, false);
					other.add(otherCurrent);
				}
				otherCurrent.lines().add(line(font, Component.literal(" ").append(Component.literal("God Potion").withStyle(ChatFormatting.RED))
						.append(Component.literal(": ").withStyle(ChatFormatting.WHITE)).append(Component.literal(god.group(1)).withStyle(ChatFormatting.YELLOW)), null));
				continue;
			}
			if (isBold(l.text())) {
				otherCurrent = new Section(l, new ArrayList<>(), false, false);
				other.add(otherCurrent);
			} else {
				if (otherCurrent == null) {
					otherCurrent = new Section(null, new ArrayList<>(), false, false);
					other.add(otherCurrent);
				}
				// indented like the tab list's own lines
				Component text = plain.startsWith(" ") ? l.text() : Component.literal(" ").append(l.text());
				otherCurrent.lines().add(line(font, text, null));
			}
		}
		if (!other.isEmpty()) {
			sections.add(new Section(line(font, Component.literal("Other").withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD), null), List.of(), false, true));
			sections.addAll(other);
		}
		footer = bottom;
		List<Line> top = new ArrayList<>();
		for (Line l : lines(font, access.fancybars$getHeader())) if (!c.compactTabHideAds || !isAdvert(plain(l.text()))) top.add(l);
		header = top;

		// columns as tall as the player list (at least 20 lines), like SkyHanni, as long as that fits on screen
		int target = 20;
		if (playerList != null) target = Math.max(target, playerList.lines().size() + 1);
		columns = pack(sections, Math.min(maxLines, target));
	}

	/**
	 * As few columns as fit on screen. A section stays in one piece unless it's taller than a whole column (a full
	 * lobby's player list), and a heading always stays with what follows it.
	 */
	private static List<List<Section>> pack(List<Section> sections, int maxLines) {
		List<List<Section>> cols = new ArrayList<>();
		List<Section> col = new ArrayList<>();
		int used = 0;
		for (int i = 0; i < sections.size(); i++) {
			Section s = sections.get(i);
			int gap = col.isEmpty() || col.getLast().heading() ? 0 : SECTION_GAP / LINE_H;
			int need = gap + s.height();
			// keep a heading together with the section after it
			if (s.heading() && i + 1 < sections.size()) need += sections.get(i + 1).height();
			if (!col.isEmpty() && used + need > maxLines) {
				cols.add(col);
				col = new ArrayList<>();
				used = 0;
				gap = 0;
			}
			if (s.height() > maxLines - used && !s.heading()) {
				// split a section that's too tall for a column
				List<Line> rest = new ArrayList<>(s.lines());
				Line title = s.title();
				boolean first = true;
				while (!rest.isEmpty()) {
					int room = Math.max(1, maxLines - used - (title == null ? 0 : 1) - (first ? gap : 0));
					List<Line> part = new ArrayList<>(rest.subList(0, Math.min(room, rest.size())));
					rest.subList(0, part.size()).clear();
					col.add(new Section(title, part, s.players(), false));
					used += (first ? gap : 0) + part.size() + (title == null ? 0 : 1);
					title = null;
					first = false;
					if (!rest.isEmpty()) {
						cols.add(col);
						col = new ArrayList<>();
						used = 0;
					}
				}
				continue;
			}
			col.add(s);
			used += gap + s.height();
		}
		if (!col.isEmpty()) cols.add(col);
		return cols;
	}

	private static Line line(Font font, Component text, @Nullable PlayerInfo player) {
		return new Line(text, player, font.width(text) + (player != null ? HEAD + 2 : 0));
	}

	/** Header / footer text split at line breaks (keeping each part's style), without blank lines. */
	private static List<Line> lines(Font font, @Nullable Component text) {
		List<Line> out = new ArrayList<>();
		if (text == null) return out;
		List<MutableComponent> split = new ArrayList<>();
		split.add(Component.empty());
		text.visit((Style style, String part) -> {
			String[] pieces = part.split("\n", -1);
			for (int i = 0; i < pieces.length; i++) {
				if (i > 0) split.add(Component.empty());
				if (!pieces[i].isEmpty()) split.getLast().append(Component.literal(pieces[i]).setStyle(style));
			}
			return Optional.empty();
		}, Style.EMPTY);
		for (Component c : split) if (!plain(c).isBlank()) out.add(new Line(c, null, font.width(c)));
		return out;
	}

	private static boolean isAdvert(String plain) {
		String l = plain.toLowerCase(Locale.ROOT);
		return l.contains("hypixel.net") || l.contains("ranks, boosters");
	}

	/** Whether the line is bold: headings ("Players (22)", "Info") and section titles ("Area: Hub", "Skills:") are. */
	private static boolean isBold(Component text) {
		String raw = text.getString();
		int i = 0;
		// skip the centring spaces and old § codes at the start: "     §b§lInfo"
		while (i < raw.length()) {
			char ch = raw.charAt(i);
			if (ch == ' ') {
				i++;
			} else if (ch == '§' && i + 1 < raw.length()) {
				if (Character.toLowerCase(raw.charAt(i + 1)) == 'l') return true;
				i += 2;
			} else {
				break;
			}
		}
		// or a proper bold style on the first part with text
		return text.visit((Style style, String part) -> part.isBlank() ? Optional.<Boolean>empty() : Optional.of(style.isBold()), Style.EMPTY).orElse(false);
	}

	/** The text without any § codes (Hypixel also sends "§s", which isn't a colour, on its spacer lines). */
	private static String plain(Component text) {
		return text.getString().replaceAll("§.", "");
	}

	private static int leadingSpaces(Component text) {
		String s = plain(text);
		int n = 0;
		while (n < s.length() && s.charAt(n) == ' ') n++;
		return n;
	}

	/** A heading without the spaces Hypixel puts in front to centre it (it's centred over its column instead). */
	private static Component trimLeading(Component text) {
		String raw = text.getString();
		StringBuilder codes = new StringBuilder();
		int i = 0;
		while (i < raw.length()) {
			char ch = raw.charAt(i);
			if (ch == '§' && i + 1 < raw.length()) {
				codes.append(ch).append(raw.charAt(i + 1));
				i += 2;
			} else if (ch == ' ') {
				i++;
			} else {
				break;
			}
		}
		if (i == 0) return text;
		// the first part's style (for headings made of styled parts rather than § codes)
		Style first = text.visit((Style style, String part) -> part.isBlank() ? Optional.<Style>empty() : Optional.of(style), Style.EMPTY).orElse(Style.EMPTY);
		return Component.literal(codes + raw.substring(i).stripTrailing()).setStyle(first);
	}

	// ---------------------------------------------------------------- drawing

	private static void render(GuiGraphicsExtractor graphics) {
		Minecraft mc = Minecraft.getInstance();
		Font font = mc.font;
		FancyBarsConfig c = FancyBarsConfig.get();
		int screenW = graphics.guiWidth();
		int screenH = graphics.guiHeight();
		long now = Util.getMillis();
		int maxLines = Math.max(10, (screenH - 24 - (header.size() + footer.size() + 2) * LINE_H - PAD * 2) / LINE_H);
		if (now - builtAt > REBUILD_MS || builtFor != maxLines) {
			builtAt = now;
			builtFor = maxLines;
			rebuild(maxLines);
		}
		if (columns.isEmpty()) return;

		int[] widths = new int[columns.size()];
		int bodyH = 0;
		for (int ci = 0; ci < columns.size(); ci++) {
			int h = 0;
			Section prev = null;
			for (Section s : columns.get(ci)) {
				if (s.title() != null) widths[ci] = Math.max(widths[ci], s.title().width());
				for (Line l : s.lines()) widths[ci] = Math.max(widths[ci], l.width());
				if (prev != null && !prev.heading()) h += SECTION_GAP;
				h += s.height() * LINE_H;
				prev = s;
			}
			bodyH = Math.max(bodyH, h);
		}
		int bodyW = -COLUMN_GAP;
		for (int w : widths) bodyW += w + COLUMN_GAP;
		int innerW = bodyW;
		for (Line l : header) innerW = Math.max(innerW, l.width());
		for (Line l : footer) innerW = Math.max(innerW, l.width());
		int headerH = header.size() * LINE_H + (header.isEmpty() ? 0 : 2);
		int footerH = footer.size() * LINE_H + (footer.isEmpty() ? 0 : 2);
		int panelW = innerW + PAD * 2;
		int panelH = headerH + bodyH + footerH + PAD * 2;
		int x = (screenW - panelW) / 2;
		int y = 10;

		// a plain dark backdrop, like the vanilla / SkyHanni tab list
		if (c.compactTabBackground) graphics.fill(x, y, x + panelW, y + panelH, 0x90000000);

		int ty = y + PAD;
		for (Line l : header) {
			graphics.text(font, l.text(), x + (panelW - l.width()) / 2, ty, 0xFFFFFFFF, true);
			ty += LINE_H;
		}
		if (!header.isEmpty()) ty += 2;

		int cx = x + PAD + (innerW - bodyW) / 2;
		for (int ci = 0; ci < columns.size(); ci++) {
			int cy = ty;
			Section prev = null;
			for (Section s : columns.get(ci)) {
				if (prev != null && !prev.heading()) cy += SECTION_GAP;
				if (s.title() != null) {
					// headings centred over their column, section titles on the left
					int tx = s.heading() ? cx + (widths[ci] - s.title().width()) / 2 : cx;
					graphics.text(font, s.title().text(), tx, cy, 0xFFFFFFFF, true);
					cy += LINE_H;
				}
				for (Line l : s.lines()) {
					int lx = cx;
					if (l.player() != null) {
						PlayerFaceExtractor.extractRenderState(graphics, l.player().getSkin(), lx, cy, HEAD);
						lx += HEAD + 2;
					}
					graphics.text(font, l.text(), lx, cy, 0xFFFFFFFF, true);
					cy += LINE_H;
				}
				prev = s;
			}
			cx += widths[ci] + COLUMN_GAP;
		}

		int fy = ty + bodyH + (footer.isEmpty() ? 0 : 2);
		for (Line l : footer) {
			graphics.text(font, l.text(), x + (panelW - l.width()) / 2, fy, 0xFFFFFFFF, true);
			fy += LINE_H;
		}
	}
}
