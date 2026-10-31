package dev.fancybars.mining;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.joml.Matrix3x2fStack;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;

import dev.fancybars.FancyBarsMod;
import dev.fancybars.config.FancyBarsConfig;
import dev.fancybars.util.SkyblockLocation;
import dev.fancybars.util.gui.Theme;

/**
 * Commission overlay for the Dwarven Mines, Crystal Hollows and Glacite Mineshafts.
 * Reads the "Commissions:" widget from the tab list (Hypixel sends it there), refreshed once a second.
 */
public final class Commissions {
	private static final Pattern ENTRY = Pattern.compile("^(?<name>[^:]+):\\s*(?<progress>DONE|[\\d.]+%)$");
	public record Commission(String name, float progress, boolean done) {}

	private static List<Commission> current = List.of();
	private static int ticks;

	public static final int WIDTH = 150;
	private static final int PAD = 6;
	private static final int TITLE_H = 13;
	private static final int ROW_H = 17;

	private Commissions() {}

	public static void init() {
		ClientTickEvents.END_CLIENT_TICK.register(_ -> {
			if (++ticks % 20 != 0) return;
			current = SkyblockLocation.isInMiningIsland() ? readTabList() : List.of();
		});
		HudElementRegistry.attachElementAfter(VanillaHudElements.HOTBAR, FancyBarsMod.id("commissions"), (graphics, tickCounter) -> {
			if (shouldShow()) render(graphics);
		});
	}

	public static boolean shouldShow() {
		return FancyBarsConfig.get().commissionsEnabled && SkyblockLocation.isInMiningIsland() && !current.isEmpty()
				&& Minecraft.getInstance().gui.screen() == null;
	}

	private static List<Commission> readTabList() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.getConnection() == null) return List.of();
		List<PlayerInfo> infos = new ArrayList<>(mc.getConnection().getOnlinePlayers());
		// Hypixel orders its tab widgets by the (fake) player names
		infos.sort(Comparator.comparing(info -> info.getProfile().name()));
		List<Commission> list = new ArrayList<>();
		boolean inSection = false;
		for (PlayerInfo info : infos) {
			if (info.getTabListDisplayName() == null) continue;
			String raw = ChatFormatting.stripFormatting(info.getTabListDisplayName().getString());
			if (raw == null) continue;
			String line = raw.trim();
			if (line.equals("Commissions:")) {
				inSection = true;
				continue;
			}
			if (!inSection) continue;
			Matcher m = ENTRY.matcher(line);
			if (line.isEmpty() || !m.matches()) {
				if (!list.isEmpty() || line.isEmpty()) break;
				continue;
			}
			String progress = m.group("progress");
			boolean done = progress.equals("DONE");
			float value = done ? 1 : Math.clamp(Float.parseFloat(progress.substring(0, progress.length() - 1)) / 100f, 0, 1);
			list.add(new Commission(m.group("name").trim(), value, done));
		}
		return list;
	}

	public static List<Commission> sample() {
		return List.of(new Commission("Mithril Miner", 0.46f, false), new Commission("Goblin Slayer", 1, true),
				new Commission("Glacite Collector", 0.12f, false), new Commission("Lava Springs Titanium", 0.8f, false));
	}

	public static int height(List<Commission> list) {
		return PAD * 2 + TITLE_H + list.size() * ROW_H;
	}

	public static float[] position(int screenW, int screenH, List<Commission> list) {
		FancyBarsConfig config = FancyBarsConfig.get();
		if (config.commissionsX < 0 || config.commissionsY < 0) return new float[]{screenW - WIDTH * config.commissionsScale - 6, Math.round(screenH * 0.25f)};
		return new float[]{config.commissionsX * screenW, config.commissionsY * screenH};
	}

	public static List<Commission> current() {
		return current;
	}

	public static void render(GuiGraphicsExtractor graphics) {
		float[] pos = position(graphics.guiWidth(), graphics.guiHeight(), current);
		renderAt(graphics, pos[0], pos[1], FancyBarsConfig.get().commissionsScale, current, 1);
	}

	public static void renderAt(GuiGraphicsExtractor graphics, float x, float y, float scale, List<Commission> list, float alpha) {
		Font font = Minecraft.getInstance().font;
		int a = Math.round(alpha * 255);
		Matrix3x2fStack pose = graphics.pose();
		pose.pushMatrix();
		pose.translate(x, y);
		pose.scale(scale, scale);
		int h = height(list);
		Theme.roundedRect(graphics, 0, 0, WIDTH, h, 5, Theme.hudBg(0.6f, a));
		Theme.roundedOutline(graphics, 0, 0, WIDTH, h, 5, Theme.hudAccent(0.45f, a));
		Theme.boldInBox(graphics, font, Component.translatable("fancybars.commissions.title"), PAD, PAD - 2, TITLE_H, (a << 24) | (Theme.colorAt(graphics, Theme.ACCENT, PAD) & 0xFFFFFF));
		int ry = PAD + TITLE_H;
		for (Commission c : list) {
			Component name = Component.literal(c.name());
			Component value = Component.literal(c.done() ? "DONE" : Math.round(c.progress() * 100) + "%");
			int valueColor = c.done() ? 0x55FF77 : 0xFFFFFF;
			Theme.textInBox(graphics, font, name, PAD, ry, 10, Theme.hudText(a));
			Theme.boldInBox(graphics, font, value, WIDTH - PAD - Theme.boldWidth(font, value), ry, 10, (a << 24) | valueColor);
			// progress bar
			int barY = ry + 11;
			int barW = WIDTH - PAD * 2;
			Theme.roundedRect(graphics, PAD, barY, barW, 3, 2, (Math.round(a * 0.25f) << 24) | 0xFFFFFF);
			int fill = Math.round(barW * c.progress());
			if (fill > 0) {
				int color = c.done() ? 0x55FF77 : Theme.colorAt(graphics, Theme.ACCENT, PAD + fill / 2f) & 0xFFFFFF;
				Theme.roundedRect(graphics, PAD, barY, Math.max(3, fill), 3, 2, (a << 24) | color);
			}
			ry += ROW_H;
		}
		pose.popMatrix();
	}
}
