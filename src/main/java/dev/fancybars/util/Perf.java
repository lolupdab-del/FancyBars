package dev.fancybars.util;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;

/**
 * /sgu perf: times the mod's own HUD elements and hooks for a few seconds in your real game and lists what each costs
 * per frame, next to the whole frame. Costs nothing while it isn't measuring (one boolean check per hook).
 */
public final class Perf {
	private Perf() {}

	private static final long DURATION_MS = 10_000;
	/** Only the render thread writes these. */
	private static volatile boolean on;
	private static final Map<String, long[]> TIMES = new HashMap<>();
	private static long frames;
	private static long startedAt;

	public static void init() {
		ClientTickEvents.END_CLIENT_TICK.register(_ -> {
			if (on && !hudTest && Util.getMillis() - startedAt >= DURATION_MS) {
				on = false;
				report();
			}
		});
	}

	public static void start() {
		if (on) return;
		TIMES.clear();
		frames = 0;
		startedAt = Util.getMillis();
		on = true;
		say(Component.literal("Measuring for 10 seconds: play normally (same spot as usual).").withStyle(ChatFormatting.YELLOW));
	}

	/** Start of a timed section, or 0 when not measuring. */
	public static long begin() {
		return on || hudTest ? System.nanoTime() : 0;
	}

	// ---------------------------------------------------------------- /sgu perf hud

	/**
	 * /sgu perf hud: the real cost of each HUD element, GPU included. Timers only see the CPU, but most of a HUD's cost
	 * can be the graphics card drawing it. This hides each element in turn for a few seconds and compares the whole
	 * game loop (which includes waiting on the GPU) with everything shown.
	 */
	private static volatile boolean hudTest;
	/** HUD element being hidden for the test ("*" = all of ours), or null. */
	private static volatile String hidden;
	public static volatile boolean smoothTextOff;
	private static final List<String> HUD_KEYS = new ArrayList<>();
	private static final String ALL = "*";
	private static final String SMOOTH = "smooth text (crisp font) off";
	private static final long SETTLE_MS = 600;
	private static final long PHASE_MS = 3500;
	private static List<String> phases;
	private static int phase;
	private static long phaseStart;
	private static long phaseNanos;
	private static long phaseFrames;
	private static final Map<String, Double> RESULTS = new java.util.LinkedHashMap<>();

	public static void startHudTest() {
		if (on || hudTest) return;
		// baseline first; the elements to try are the ones that draw something (picked from the baseline's timings)
		TIMES.clear();
		frames = 0;
		RESULTS.clear();
		// the first round only finds which elements draw anything (its code timers slow the frame a little, so its
		// time isn't used: that made every element look ~0.2 ms more expensive than it is)
		phases = new ArrayList<>(List.of("probe"));
		phase = 0;
		startPhase();
		on = true;
		hudTest = true;
		say(Component.literal("Testing each HUD element for about 3 seconds each: stand still in the same spot, don't open menus.").withStyle(ChatFormatting.YELLOW));
	}

	private static void startPhase() {
		String p = phases.get(phase);
		hidden = p.equals("all") ? ALL : p.startsWith("HUD ") ? p : null;
		smoothTextOff = p.equals(SMOOTH);
		phaseStart = Util.getMillis();
		phaseNanos = 0;
		phaseFrames = 0;
	}

	/** End of one game loop (the GameLoop timer). */
	public static void loop(long begin) {
		if (begin == 0) return;
		long now = System.nanoTime();
		end("[Game loop: frame + ticks + GPU wait]", begin);
		if (!hudTest) return;
		long elapsed = Util.getMillis() - phaseStart;
		if (elapsed < SETTLE_MS) return;
		phaseNanos += now - begin;
		phaseFrames++;
		if (elapsed < SETTLE_MS + PHASE_MS) return;
		double ms = phaseNanos / 1e6 / Math.max(1, phaseFrames);
		String p = phases.get(phase);
		if (!p.equals("probe")) RESULTS.merge(p, ms, (a, b) -> (a + b) / 2); // baseline runs twice: averaged
		if (phase == 0 && phases.size() == 1) {
			// pick what to try from the probe: our HUD elements that took any time
			on = false;
			phases.add("baseline");
			for (String key : HUD_KEYS) {
				long[] t = TIMES.get(key);
				if (t != null && t[0] / 1e6 / Math.max(1, frames) > 0.003) phases.add(key);
			}
			phases.add(SMOOTH);
			phases.add("all");
			phases.add("baseline");
		}
		phase++;
		if (phase >= phases.size()) {
			finishHudTest();
			return;
		}
		startPhase();
	}

	private static void finishHudTest() {
		hudTest = false;
		hidden = null;
		smoothTextOff = false;
		double base = RESULTS.getOrDefault("baseline", 0.0);
		say(Component.literal(String.format(Locale.ROOT, "HUD test: %.2f ms per frame with everything shown (%.0f FPS). Saved by hiding:", base, 1000 / Math.max(0.01, base)))
				.withStyle(ChatFormatting.GOLD));
		List<Map.Entry<String, Double>> list = new ArrayList<>(RESULTS.entrySet());
		list.removeIf(e -> e.getKey().equals("baseline"));
		list.sort((a, b) -> Double.compare(a.getValue(), b.getValue()));
		for (Map.Entry<String, Double> e : list) {
			double saved = base - e.getValue();
			String name = e.getKey().equals("all") ? "ALL of SkyblockGuiUtils' HUD" : e.getKey().startsWith("HUD (replaced) ") ? e.getKey().substring(15) + " (incl. Minecraft's own)" : e.getKey().replace("HUD ", "");
			say(Component.literal(String.format(Locale.ROOT, "  %+.2f ms  %s", saved, name))
					.withStyle(saved >= 0.3 ? ChatFormatting.RED : saved >= 0.1 ? ChatFormatting.YELLOW : ChatFormatting.GRAY));
		}
	}

	public static void end(String key, long begin) {
		if (begin == 0 || !on) return;
		long[] t = TIMES.computeIfAbsent(key, _ -> new long[2]);
		t[0] += System.nanoTime() - begin;
		t[1]++;
	}

	public static void frame() {
		if (on) frames++;
	}

	private static void report() {
		long n = Math.max(1, frames);
		say(Component.literal("Performance (" + frames + " frames, ms per frame):").withStyle(ChatFormatting.GOLD));
		List<Map.Entry<String, long[]>> list = new ArrayList<>(TIMES.entrySet());
		list.sort((a, b) -> Long.compare(b.getValue()[0], a.getValue()[0]));
		for (Map.Entry<String, long[]> e : list) {
			double ms = e.getValue()[0] / 1e6 / n;
			if (ms < 0.005) continue;
			boolean whole = e.getKey().startsWith("[");
			say(Component.literal(String.format(Locale.ROOT, "  %.3f ms  %s  (%.0fx/frame)", ms, e.getKey(), e.getValue()[1] / (double) n))
					.withStyle(whole ? ChatFormatting.AQUA : ms >= 0.1 ? ChatFormatting.RED : ms >= 0.03 ? ChatFormatting.YELLOW : ChatFormatting.GRAY));
		}
	}

	private static void say(Component message) {
		Minecraft.getInstance().execute(() -> {
			if (Minecraft.getInstance().player != null) Minecraft.getInstance().player.sendSystemMessage(message);
		});
	}

	/** A client tick handler, timed (ticks run 20 times a second; shown per frame like the rest). */
	public static void onTick(String key, ClientTickEvents.EndTick handler) {
		ClientTickEvents.END_CLIENT_TICK.register(mc -> {
			long t = begin();
			handler.onEndTick(mc);
			end(key, t);
		});
	}

	// ---------------------------------------------------------------- HUD element wrappers

	private static HudElement timed(String key, HudElement element) {
		if (key.startsWith("HUD ") && !HUD_KEYS.contains(key)) HUD_KEYS.add(key);
		return (graphics, delta) -> {
			String h = hidden;
			if (h != null && (h == ALL && !key.startsWith("HUD (replaced)") || h.equals(key))) return;
			long t = begin();
			element.extractRenderState(graphics, delta);
			end(key, t);
		};
	}

	public static void attachElementAfter(Identifier anchor, Identifier id, HudElement element) {
		HudElementRegistry.attachElementAfter(anchor, id, timed("HUD " + id.getPath(), element));
	}

	public static void attachElementBefore(Identifier anchor, Identifier id, HudElement element) {
		HudElementRegistry.attachElementBefore(anchor, id, timed("HUD " + id.getPath(), element));
	}

	/** Our replacement of a vanilla HUD part (includes the vanilla drawing when it still runs). */
	public static void replaceElement(Identifier id, Function<HudElement, HudElement> replacer) {
		HudElementRegistry.replaceElement(id, original -> timed("HUD (replaced) " + id.getPath(), replacer.apply(original)));
	}
}
