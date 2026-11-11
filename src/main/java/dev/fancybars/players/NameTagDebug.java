package dev.fancybars.players;

import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;

import dev.fancybars.config.FancyBarsConfig;

/**
 * /sgu nametagdebug: which name tag hooks have run (and how often they swapped a name) since the last time, and
 * what's floating above you. Used to find out what draws a name tag when a custom name doesn't show on it.
 */
public final class NameTagDebug {
	private static final Map<String, AtomicInteger> CALLS = new ConcurrentHashMap<>();
	private static final Map<String, AtomicInteger> SWAPS = new ConcurrentHashMap<>();
	private static final Map<String, Component> LAST_TEXT = new ConcurrentHashMap<>();

	private NameTagDebug() {}

	/** Called by each hook: {@code before} → {@code after} for that hook. */
	public static void hit(String hook, Component before, Component after) {
		CALLS.computeIfAbsent(hook, k -> new AtomicInteger()).incrementAndGet();
		if (before != after) SWAPS.computeIfAbsent(hook, k -> new AtomicInteger()).incrementAndGet();
		if (before != null) LAST_TEXT.put(hook, before);
	}


	public static void report() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || mc.level == null) return;
		FancyBarsConfig c = FancyBarsConfig.get();
		say(Component.literal("Name tag debug").withStyle(ChatFormatting.GOLD));
		say(Component.literal("Custom names loaded: " + SharedNames.all().keySet() + ", shown: " + c.sharedNamesEnabled
				+ ", own tag in F5: " + c.ownNametagInF5 + ", camera: " + mc.options.getCameraType()).withStyle(ChatFormatting.GRAY));
		say(Component.literal("Your display name: \"" + mc.player.getDisplayName().getString() + "\"").withStyle(ChatFormatting.GRAY));
		Component swapped = Nicknames.apply(mc.player.getDisplayName(), Nicknames.Where.WORLD);
		say(Component.literal("  swapped would be: ").withStyle(ChatFormatting.GRAY).append(swapped));

		Map<String, AtomicInteger> calls = new TreeMap<>(CALLS);
		if (calls.isEmpty()) say(Component.literal("No name tag hooks have run since last time.").withStyle(ChatFormatting.RED));
		for (Map.Entry<String, AtomicInteger> e : calls.entrySet()) {
			int s = SWAPS.getOrDefault(e.getKey(), new AtomicInteger()).get();
			say(Component.literal("Hook " + e.getKey() + ": ran " + e.getValue().get() + "x, swapped " + s + "x, last: \""
					+ (LAST_TEXT.containsKey(e.getKey()) ? LAST_TEXT.get(e.getKey()).getString() : "") + "\"").withStyle(s > 0 ? ChatFormatting.GREEN : ChatFormatting.YELLOW));
		}
		CALLS.clear();
		SWAPS.clear();
		LAST_TEXT.clear();

		// everything with text within 4 blocks of you
		int found = 0;
		for (Entity entity : mc.level.entitiesForRendering()) {
			if (entity == mc.player || entity.distanceTo(mc.player) > 4) continue;
			String text = null;
			if (entity instanceof net.minecraft.world.entity.Display.TextDisplay) text = entity.getDisplayName().getString();
			if (entity.hasCustomName() && entity.getCustomName() != null) text = entity.getCustomName().getString();
			String type = entity.getType().toShortString();
			say(Component.literal("Near you: " + type + (text != null ? " \"" + text + "\"" : "")
					+ (entity.getVehicle() == mc.player ? " (riding you)" : "")).withStyle(ChatFormatting.AQUA));
			if (++found >= 12) break;
		}
		if (found == 0) say(Component.literal("Nothing else within 4 blocks.").withStyle(ChatFormatting.GRAY));
		say(Component.literal("Stand still in F5 for a few seconds, then run this again for fresh hook counts.").withStyle(ChatFormatting.DARK_GRAY));
	}

	/** Printed without names being swapped, so the report shows what's really there. */
	private static void say(Component message) {
		Nicknames.say(message);
	}
}
