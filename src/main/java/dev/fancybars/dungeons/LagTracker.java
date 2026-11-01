package dev.fancybars.dungeons;

import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;

import dev.fancybars.config.FancyBarsConfig;
import dev.fancybars.util.gui.Theme;

/**
 * Seconds lost to lag in a dungeon run. The server sends a time update every 20 ticks, which is exactly one second
 * when it isn't lagging; the real time those updates span minus one second per update is time lost to server lag.
 * (Using the whole span instead of each gap means normal network jitter cancels out.)
 */
public final class LagTracker {
	private static boolean running;
	private static long firstPacket = -1;
	private static long lastPacket = -1;
	private static int intervals;

	private LagTracker() {}

	public static void init() {
		ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
			if (overlay) return;
			String text = ChatFormatting.stripFormatting(message.getString());
			if (text == null) return;
			text = text.trim();
			// run start: Mort hands you the map
			if (text.startsWith("[NPC] Mort: Here, I found this map")) start();
			// run end: the score summary
			else if (running && (text.contains("> EXTRA STATS <") || text.startsWith("Team Score:"))) finish();
		});
		dev.fancybars.util.SkyblockLocation.onLocationChange(() -> running = false);
	}

	private static void start() {
		running = true;
		firstPacket = -1;
		lastPacket = -1;
		intervals = 0;
	}

	/** Called for every server time update (once per 20 server ticks). */
	public static void onTimePacket() {
		if (!running) return;
		long now = Util.getMillis();
		if (lastPacket > 0 && now - lastPacket < 100) return; // the same packet seen twice (network + main thread)
		if (firstPacket < 0) firstPacket = now;
		else intervals++;
		lastPacket = now;
	}

	private static void finish() {
		running = false;
		FancyBarsConfig config = FancyBarsConfig.get();
		if (!config.lagReportEnabled || firstPacket < 0 || intervals < 5) return;
		double real = (lastPacket - firstPacket) / 1000.0;
		double lost = Math.max(0, real - intervals);
		double tps = 20.0 * intervals / Math.max(1e-3, real);
		String lostText = String.format(java.util.Locale.ROOT, "%.1f", lost);
		String tpsText = String.format(java.util.Locale.ROOT, "%.1f", Math.min(20, tps));
		String summary = "Lost " + lostText + "s to lag this run (avg " + tpsText + " TPS)";
		if (config.lagReportCustom && config.lagReportFormat != null && !config.lagReportFormat.isBlank()) {
			summary = config.lagReportFormat.replace("{lost}", lostText).replace("{tps}", tpsText).trim();
		}
		Minecraft mc = Minecraft.getInstance();
		if (mc.player != null) mc.player.sendSystemMessage(Theme.chatPrefix().copy().append(Component.literal(summary)));
		if (config.lagReportParty) dev.fancybars.commands.Commands.send("pc " + summary);
	}
}
