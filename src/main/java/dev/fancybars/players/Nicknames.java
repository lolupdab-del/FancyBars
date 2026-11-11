package dev.fancybars.players;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;

import dev.fancybars.config.FancyBarsConfig;
import dev.fancybars.util.gui.Chroma;
import dev.fancybars.util.gui.Theme;

/**
 * Names shown instead of players' IGNs in chat, the tab list and above their heads:
 * <ul>
 * <li>your own nicknames (menu or /sgu nick), which only you see, and</li>
 * <li>shared custom names from {@link SharedNames}, which everyone with the mod sees.</li>
 * </ul>
 * Your own nickname for someone wins over their shared name. All of it is drawn on your screen only.
 * <p>
 * Names are swapped when text is displayed (not when chat arrives), so the mod's own chat features, like the
 * shitter list and party detection, still see real IGNs.
 */
public final class Nicknames {
	private Nicknames() {}

	/** Where the text is shown: tab list and name tags are redrawn every frame (so effects can animate), chat isn't. */
	public enum Where {
		CHAT, TAB, WORLD
	}

	/** One name to swap in: a local nickname (text with &amp; codes) or a shared custom name. */
	private record Target(String ign, String local, SharedNames.Entry shared) {}

	/** {@code text} with every renamed IGN replaced, or {@code text} itself when nothing matches. */
	public static Component apply(Component text, Where where) {
		if (text == null || bypass) return text;
		String raw = text.getString();
		if (raw.indexOf('§') >= 0) raw = raw.replaceAll("§[0-9a-fk-orA-FK-OR]", "");
		List<Target> targets = targetsIn(raw.toLowerCase(Locale.ROOT));
		if (targets.isEmpty()) return text;

		// flatten to one style per character so a name split over several parts is still found. Hypixel writes some
		// text (name tags) with old § colour codes inside it, like "§6BigMMan": those become styles here, otherwise
		// the "6" would look like part of the name and it would never match as a whole word.
		List<Style> styles = new ArrayList<>();
		StringBuilder sb = new StringBuilder();
		text.visit((Style style, String part) -> {
			Style current = style;
			for (int k = 0; k < part.length(); k++) {
				char c = part.charAt(k);
				if (c == '§' && k + 1 < part.length()) {
					ChatFormatting format = ChatFormatting.getByCode(Character.toLowerCase(part.charAt(k + 1)));
					if (format != null) {
						current = format == ChatFormatting.RESET ? style : current.applyLegacyFormat(format);
						k++;
						continue;
					}
				}
				styles.add(current);
				sb.append(c);
			}
			return Optional.empty();
		}, Style.EMPTY);
		String flat = sb.toString();
		String flatLower = flat.toLowerCase(Locale.ROOT);

		MutableComponent out = Component.empty();
		boolean changed = false;
		int i = 0;
		int runStart = 0;
		while (i < flat.length()) {
			Target match = matchAt(flat, flatLower, i, targets);
			if (match == null) {
				i++;
				continue;
			}
			String real = flat.substring(i, i + match.ign().length());
			// already swapped by an earlier hook (its letters carry our marker): leave it
			if (isSwappedFor(styles.get(i).getInsertion(), real)) {
				i += match.ign().length();
				continue;
			}
			appendRun(out, flat, styles, runStart, i);
			MutableComponent swapped = Component.empty();
			if (match.local() != null) appendFormatted(swapped, match.local(), styles.get(i));
			else appendShared(swapped, match.shared(), styles.get(i), where, real);
			Component shown = where == Where.TAB && !FancyBarsConfig.get().nicknamesTabSmoothColors ? classic(swapped) : swapped;
			out.append(markSwapped(shown, real));
			i += match.ign().length();
			runStart = i;
			changed = true;
		}
		if (!changed) return text;
		appendRun(out, flat, styles, runStart, flat.length());
		return out;
	}

	/** Every renamed IGN that appears somewhere in {@code lower} (quick check: this runs for every name tag each frame). */
	private static List<Target> targetsIn(String lower) {
		List<Target> out = new ArrayList<>();
		FancyBarsConfig config = FancyBarsConfig.get();
		if (config.nicknamesEnabled) {
			for (FancyBarsConfig.Nickname n : config.nicknames) {
				if (!valid(n)) continue;
				String ign = n.ign.trim().toLowerCase(Locale.ROOT);
				if (lower.contains(ign)) out.add(new Target(ign, n.name, null));
			}
		}
		if (config.sharedNamesEnabled) {
			for (Map.Entry<String, SharedNames.Entry> e : SharedNames.all().entrySet()) {
				if (!lower.contains(e.getKey())) continue;
				// your own nickname for this player wins
				boolean overridden = false;
				for (Target t : out) if (t.ign().equals(e.getKey())) overridden = true;
				if (!overridden) out.add(new Target(e.getKey(), null, e.getValue()));
			}
		}
		return out;
	}

	private static boolean valid(FancyBarsConfig.Nickname n) {
		return n != null && n.ign != null && !n.ign.isBlank() && n.name != null && !n.name.isBlank();
	}

	/** The target whose IGN starts at {@code at} as a whole word, or null. */
	private static Target matchAt(String flat, String flatLower, int at, List<Target> targets) {
		if (at > 0 && isNameChar(flat.charAt(at - 1))) return null;
		for (Target t : targets) {
			int end = at + t.ign().length();
			if (end > flat.length() || !flatLower.startsWith(t.ign(), at)) continue;
			if (end < flat.length() && isNameChar(flat.charAt(end))) continue;
			return t;
		}
		return null;
	}

	private static boolean isNameChar(char c) {
		return c == '_' || Character.isLetterOrDigit(c);
	}

	private static void appendRun(MutableComponent out, String flat, List<Style> styles, int from, int to) {
		int start = from;
		for (int i = from + 1; i <= to; i++) {
			if (i == to || !styles.get(i).equals(styles.get(start))) {
				if (i > start) out.append(Component.literal(flat.substring(start, i)).setStyle(styles.get(start)));
				start = i;
			}
		}
	}

	// ---------------------------------------------------------------- formatting

	/** One character with its style, so effects can be applied letter by letter. */
	private record Glyph(String ch, Style style) {}

	/**
	 * Parses {@code text} with &amp; (or §) codes and &amp;#RRGGBB hex colours on top of {@code base}.
	 * &amp;r goes back to {@code base}.
	 */
	private static List<Glyph> parse(String text, Style base) {
		List<Glyph> out = new ArrayList<>();
		Style style = base;
		for (int i = 0; i < text.length(); i++) {
			char c = text.charAt(i);
			if ((c == '&' || c == '§') && i + 1 < text.length()) {
				// &#RRGGBB
				if (text.charAt(i + 1) == '#' && i + 8 <= text.length() && text.substring(i + 2, i + 8).matches("[0-9A-Fa-f]{6}")) {
					style = style.withColor(TextColor.fromRgb(Integer.parseInt(text.substring(i + 2, i + 8), 16)))
							.withBold(false).withItalic(false).withUnderlined(false).withStrikethrough(false).withObfuscated(false);
					i += 7;
					continue;
				}
				ChatFormatting format = ChatFormatting.getByCode(Character.toLowerCase(text.charAt(i + 1)));
				if (format != null) {
					style = format == ChatFormatting.RESET ? base : style.applyLegacyFormat(format);
					i++;
					continue;
				}
			}
			// keep surrogate pairs (emoji and other symbols) together
			if (Character.isHighSurrogate(c) && i + 1 < text.length()) {
				out.add(new Glyph(text.substring(i, i + 2), style));
				i++;
			} else {
				out.add(new Glyph(String.valueOf(c), style));
			}
		}
		return out;
	}

	private static void appendGlyphs(MutableComponent out, List<Glyph> glyphs) {
		StringBuilder run = new StringBuilder();
		Style runStyle = null;
		for (Glyph g : glyphs) {
			if (runStyle != null && !g.style().equals(runStyle)) {
				out.append(Component.literal(run.toString()).setStyle(runStyle));
				run.setLength(0);
			}
			runStyle = g.style();
			run.append(g.ch());
		}
		if (runStyle != null && !run.isEmpty()) out.append(Component.literal(run.toString()).setStyle(runStyle));
	}

	/** Text with &amp; codes in the original name's style (local nicknames, prefixes, suffixes). */
	private static void appendFormatted(MutableComponent out, String text, Style original) {
		appendGlyphs(out, parse(text, original));
	}

	/** A shared custom name: prefix, the name with its colours and effects, suffix. */
	private static void appendShared(MutableComponent out, SharedNames.Entry e, Style original, Where where, String realIgn) {
		Style base = original;
		if (e.bold() != null) base = base.withBold(e.bold());
		if (e.italic() != null) base = base.withItalic(e.italic());
		if (e.underlined() != null) base = base.withUnderlined(e.underlined());
		if (e.strikethrough() != null) base = base.withStrikethrough(e.strikethrough());
		if (e.obfuscated() != null) base = base.withObfuscated(e.obfuscated());
		if (e.noShadow()) base = base.withoutShadow();
		else if (e.shadow() != null) base = base.withShadowColor(e.shadow());
		FontDescription font = font(e.font());
		if (font != null) base = base.withFont(font);
		if (where == Where.CHAT && !e.hover().isBlank()) {
			MutableComponent hover = Component.empty();
			appendFormatted(hover, e.hover(), Style.EMPTY.withColor(ChatFormatting.WHITE));
			hover.append(Component.literal("\n" + realIgn).withStyle(ChatFormatting.DARK_GRAY));
			base = base.withHoverEvent(new HoverEvent.ShowText(hover));
		}

		if (!e.prefix().isEmpty()) appendFormatted(out, e.prefix(), original);
		List<Glyph> name = parse(e.name(), base);
		if (e.chroma() || !e.colors().isEmpty()) {
			// colours go letter by letter across the whole name
			int n = name.size();
			long now = Util.getMillis();
			// (in chat and the tab list NameColors recolours these again as they're drawn, so they move there too)
			String key = realIgn;
			for (int i = 0; i < n; i++) {
				int rgb = colorOfLetter(e, i, now, where);
				// each name letter carries its own marker (IGN + position), so the final draw step can recolour it
				// for the current moment however old this copy of the text is
				Style style = name.get(i).style().withColor(TextColor.fromRgb(rgb)).withInsertion(NAME_LETTER + key + MARK_SEP + i);
				name.set(i, new Glyph(name.get(i).ch(), style));
			}
		}
		appendGlyphs(out, name);
		if (!e.suffix().isEmpty()) appendFormatted(out, e.suffix(), original);
	}

	private static FontDescription font(String name) {
		return switch (name.trim().toLowerCase(Locale.ROOT)) {
			case "uniform" -> new FontDescription.Resource(Identifier.withDefaultNamespace("uniform"));
			case "alt", "enchanting", "galactic" -> new FontDescription.Resource(Identifier.withDefaultNamespace("alt"));
			case "illageralt", "illager" -> new FontDescription.Resource(Identifier.withDefaultNamespace("illageralt"));
			default -> null;
		};
	}

	/**
	 * Rainbow colour for letter {@code i}. In chat and the tab list the mod's chroma shader draws it (smooth and moving,
	 * like SkyHanni) when that's on; otherwise each letter gets a hue that moves over time where the text is redrawn.
	 */
	private static int chroma(int i, long now, float speed, Where where) {
		// the tab list's colours get rebuilt by other mods, so the shader marker colour would just turn pink there
		if (where == Where.CHAT && Chroma.shaderEnabled()) return Chroma.TEXT_MARK;
		// in doubles: a float can't hold the clock precisely after a few days of PC uptime
		double h = now * (double) speed / 3000.0 + i * 0.06;
		return safe(NameColors.offClassic(Mth.hsvToRgb((float) (h - Math.floor(h)), 0.75f, 1f)));
	}

	/** Colour at {@code t} (0..1) along the stops; animated gradients slide back and forth along the name. */
	private static int gradient(List<Integer> stops, float t, boolean animated, long now) {
		if (stops.size() == 1) return safe(stops.get(0));
		if (animated) {
			float phase = (now % 4000) / 4000f;
			float u = (t * 0.5f + phase) % 1f;
			t = u < 0.5f ? u * 2 : (1 - u) * 2;
		}
		float pos = t * (stops.size() - 1);
		int seg = Math.min((int) pos, stops.size() - 2);
		float f = pos - seg;
		int a = stops.get(seg);
		int b = stops.get(seg + 1);
		int r = Math.round(Mth.lerp(f, a >> 16 & 0xFF, b >> 16 & 0xFF));
		int g = Math.round(Mth.lerp(f, a >> 8 & 0xFF, b >> 8 & 0xFF));
		int bl = Math.round(Mth.lerp(f, a & 0xFF, b & 0xFF));
		return safe(r << 16 | g << 8 | bl);
	}

	/**
	 * Marker on swapped-in names (in the shift-click insertion text, before the real IGN). It must be something
	 * Minecraft never puts there itself: player display names already carry their plain IGN as insertion, so a bare IGN
	 * made every player name look "already swapped" and it was never swapped (your own F5 name tag).
	 */
	public static final String SWAPPED = "⁣sgu⁣";
	/** Marker on each coloured letter of a swapped-in name: NAME_LETTER + ign + MARK_SEP + letter position. */
	public static final String NAME_LETTER = "⁣sgn⁣";
	private static final String MARK_SEP = "⁣";

	/** The IGN behind a swapped name's insertion text (for shift-click), or {@code insertion} unchanged. */
	public static String unmarkInsertion(String insertion) {
		if (insertion == null) return null;
		if (insertion.startsWith(SWAPPED)) return insertion.substring(SWAPPED.length());
		if (insertion.startsWith(NAME_LETTER)) {
			String rest = insertion.substring(NAME_LETTER.length());
			int sep = rest.indexOf(MARK_SEP);
			return sep < 0 ? rest : rest.substring(0, sep);
		}
		return insertion;
	}

	/** Whether {@code insertion} is one of our markers for {@code realIgn} (so that text is already swapped). */
	private static boolean isSwappedFor(String insertion, String realIgn) {
		if (insertion == null) return false;
		if (insertion.equalsIgnoreCase(SWAPPED + realIgn)) return true;
		return insertion.regionMatches(true, 0, NAME_LETTER + realIgn + MARK_SEP, 0, NAME_LETTER.length() + realIgn.length() + MARK_SEP.length());
	}

	/** Colour of letter {@code i} of a shared name at {@code now}. */
	private static int colorOfLetter(SharedNames.Entry e, int i, long now, Where where) {
		return e.chroma() ? chroma(i, now, e.chromaSpeed(), where) : NameColors.flow(e.colors(), i, e.animated(), e.chromaSpeed(), now);
	}

	/**
	 * Recolours the letters of every swapped-in shared name in {@code text} for the current moment, or returns
	 * {@code text} itself when there are none. Name tags can be shown from an older copy of their text (a cached render
	 * state, another mod's cache), which made a letter blink to an earlier colour for a frame; the final draw step runs
	 * this every frame so the colours are always current.
	 */
	public static Component recolor(Component text, Where where) {
		if (text == null) return null;
		boolean[] any = {false};
		text.visit((Style style, String part) -> {
			String ins = style.getInsertion();
			if (ins != null && ins.startsWith(NAME_LETTER)) any[0] = true;
			return any[0] ? Optional.of(Boolean.TRUE) : Optional.empty();
		}, Style.EMPTY);
		if (!any[0]) return text;
		long now = Util.getMillis();
		MutableComponent out = Component.empty();
		text.visit((Style style, String part) -> {
			Style fixed = style;
			String ins = style.getInsertion();
			if (ins != null && ins.startsWith(NAME_LETTER)) {
				String rest = ins.substring(NAME_LETTER.length());
				int sep = rest.indexOf(MARK_SEP);
				SharedNames.Entry e = sep < 0 ? null : SharedNames.all().get(rest.substring(0, sep).toLowerCase(Locale.ROOT));
				if (e != null && (e.chroma() || !e.colors().isEmpty())) {
					try {
						int i = Integer.parseInt(rest.substring(sep + MARK_SEP.length()));
						fixed = style.withColor(TextColor.fromRgb(colorOfLetter(e, i, now, where)));
					} catch (NumberFormatException ignored) {}
				}
			}
			out.append(Component.literal(part).setStyle(fixed));
			return Optional.empty();
		}, Style.EMPTY);
		return out;
	}

	/**
	 * Tags every part of a swapped-in name with the marker + the player's real IGN (name letters keep their own
	 * per-letter marker). Hooks that see the same text again then know it's done, so a suffix like " ❤" is never added
	 * twice; shift-clicking the name types their real IGN.
	 */
	private static Component markSwapped(Component text, String realIgn) {
		MutableComponent out = Component.empty();
		text.visit((Style style, String part) -> {
			String ins = style.getInsertion();
			Style marked = ins != null && ins.startsWith(NAME_LETTER) ? style : style.withInsertion(SWAPPED + realIgn);
			out.append(Component.literal(part).setStyle(marked));
			return Optional.empty();
		}, Style.EMPTY);
		return out;
	}

	private static final TextColor[] CLASSIC = {
			TextColor.BLACK, TextColor.DARK_BLUE, TextColor.DARK_GREEN, TextColor.DARK_AQUA,
			TextColor.DARK_RED, TextColor.DARK_PURPLE, TextColor.GOLD, TextColor.GRAY,
			TextColor.DARK_GRAY, TextColor.BLUE, TextColor.GREEN, TextColor.AQUA,
			TextColor.RED, TextColor.LIGHT_PURPLE, TextColor.YELLOW, TextColor.WHITE};

	/**
	 * The same text with every colour moved to the nearest of Minecraft's 16 classic colours. Tab list mods (SkyHanni's
	 * compact tab list and others) rebuild the tab from classic colour codes, which can't hold hex colours: those came
	 * out as raw "&lt;#5662FF&gt;B" text. Gradients and rainbows still move, just in classic colours.
	 */
	private static Component classic(Component text) {
		MutableComponent out = Component.empty();
		text.visit((Style style, String part) -> {
			TextColor color = style.getColor();
			Style fixed = style;
			// classic colours serialize by name ("aqua"), hex ones as "#5662FF"
			if (color != null && color.serialize().startsWith("#")) fixed = style.withColor(nearestClassic(color.getValue()));
			out.append(Component.literal(part).setStyle(fixed));
			return Optional.empty();
		}, Style.EMPTY);
		return out;
	}

	private static TextColor nearestClassic(int rgb) {
		TextColor best = TextColor.WHITE;
		long bestDist = Long.MAX_VALUE;
		for (TextColor f : CLASSIC) {
			int c = f.getValue();
			long dr = (rgb >> 16 & 0xFF) - (c >> 16 & 0xFF), dg = (rgb >> 8 & 0xFF) - (c >> 8 & 0xFF), db = (rgb & 0xFF) - (c & 0xFF);
			// weighted like the eye sees it, so blues don't snap to black so easily
			long dist = 3 * dr * dr + 4 * dg * dg + 2 * db * db;
			if (dist < bestDist) {
				bestDist = dist;
				best = f;
			}
		}
		return best;
	}

	/** Never land exactly on one of the colours the chroma shader treats as a marker. */
	private static int safe(int rgb) {
		return rgb == Chroma.TEXT_MARK || rgb == Chroma.TEXT_MARK_CUSTOM_ENCHANT || rgb == Chroma.TEXT_MARK_CUSTOM_ULTIMATE ? rgb ^ 1 : rgb;
	}

	// ---------------------------------------------------------------- local list

	/**
	 * Floating name tags (text displays) split their text into lines once and keep that. A tag that appeared before the
	 * names were downloaded or changed (your own appears the moment you join) would keep the old name, so clear every
	 * cache: each tag rebuilds its lines on the next frame, with the current names swapped in.
	 */
	public static void refreshNameTags() {
		Minecraft mc = Minecraft.getInstance();
		mc.execute(() -> {
			if (mc.level == null) return;
			for (net.minecraft.world.entity.Entity entity : mc.level.entitiesForRendering()) {
				if (entity instanceof net.minecraft.world.entity.Display.TextDisplay display) {
					((dev.fancybars.mixin.TextDisplayCacheAccessor) display).fancybars$setClientDisplayCache(null);
				}
			}
		});
	}

	public static FancyBarsConfig.Nickname find(String ign) {
		for (FancyBarsConfig.Nickname n : FancyBarsConfig.get().nicknames) {
			if (n.ign != null && n.ign.trim().equalsIgnoreCase(ign)) return n;
		}
		return null;
	}

	public static void set(String ign, String name) {
		FancyBarsConfig.Nickname existing = find(ign);
		if (existing != null) existing.name = name;
		else FancyBarsConfig.get().nicknames.add(new FancyBarsConfig.Nickname(ign, name));
		FancyBarsConfig.save();
		refreshNameTags();
		MutableComponent shown = Component.empty();
		appendFormatted(shown, name, Style.EMPTY);
		say(Component.translatable("fancybars.nick.set", ign, shown).withStyle(ChatFormatting.GREEN));
	}

	public static void remove(String ign) {
		boolean removed = FancyBarsConfig.get().nicknames.removeIf(n -> n.ign != null && n.ign.trim().equalsIgnoreCase(ign));
		FancyBarsConfig.save();
		refreshNameTags();
		say(Component.translatable(removed ? "fancybars.nick.removed" : "fancybars.nick.notFound", ign).withStyle(removed ? ChatFormatting.GREEN : ChatFormatting.RED));
	}

	/** /sgu names: the shared custom names this game has downloaded, each drawn the way it looks in chat. */
	public static void listShared() {
		Map<String, SharedNames.Entry> all = SharedNames.all();
		FancyBarsConfig config = FancyBarsConfig.get();
		if (all.isEmpty()) {
			say(Component.translatable("fancybars.names.none").withStyle(ChatFormatting.GRAY));
			return;
		}
		say(Component.translatable(config.sharedNamesEnabled ? "fancybars.names.list" : "fancybars.names.listOff", all.size()).withStyle(ChatFormatting.GRAY));
		for (Map.Entry<String, SharedNames.Entry> e : all.entrySet()) {
			MutableComponent line = Component.literal("  " + e.getKey() + " → ").withStyle(ChatFormatting.GRAY);
			appendShared(line, e.getValue(), Style.EMPTY.withColor(ChatFormatting.WHITE), Where.CHAT, e.getKey());
			say(line);
		}
	}

	public static void list() {
		List<FancyBarsConfig.Nickname> list = FancyBarsConfig.get().nicknames;
		if (list.isEmpty()) {
			say(Component.translatable("fancybars.nick.empty").withStyle(ChatFormatting.GRAY));
			return;
		}
		say(Component.translatable("fancybars.nick.list", list.size()).withStyle(ChatFormatting.GRAY));
		for (FancyBarsConfig.Nickname n : list) {
			if (!valid(n)) continue;
			MutableComponent line = Component.literal("  " + n.ign.trim() + " → ").withStyle(ChatFormatting.GRAY);
			appendFormatted(line, n.name, Style.EMPTY.withColor(ChatFormatting.WHITE));
			say(line);
		}
	}

	/** Set while the mod prints its own nickname messages, so "lolupdab → Bob" isn't turned into "Bob → Bob". */
	private static boolean bypass;

	static void say(Component message) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null) return;
		bypass = true;
		try {
			mc.player.sendSystemMessage(Theme.chatPrefix().append(message));
		} finally {
			bypass = false;
		}
	}

	/** Names of real players in the tab list, for command suggestions. */
	public static List<String> onlineNames() {
		List<String> out = new ArrayList<>();
		Minecraft mc = Minecraft.getInstance();
		if (mc.getConnection() == null) return out;
		for (var info : mc.getConnection().getOnlinePlayers()) {
			String name = info.getProfile().name();
			// Hypixel fills the tab list with fake entries like "!A-a"; real IGNs are 3-16 word characters
			if (name != null && name.matches("\\w{3,16}")) out.add(name);
		}
		return out;
	}
}
