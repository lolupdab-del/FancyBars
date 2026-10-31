package dev.fancybars.dungeons;

import dev.fancybars.util.gui.Theme;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.joml.Matrix3x2fStack;
import org.jspecify.annotations.Nullable;

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
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import dev.fancybars.FancyBarsMod;
import dev.fancybars.config.FancyBarsConfig;

/**
 * Melody Message (ported from Odin): helpful messages for the floor 7 melody terminal.
 * - Melody Progress: tells your party "Melody 33%" / "Melody 67%" as you get through it
 * - Send Coords: sends your coordinates to party chat when you open melody
 * - Progress GUI: a HUD showing melody progress, yours (with where the pane and target are) and your party's
 *   (read from their "Melody ..%" party messages, which Odin and most other mods send)
 */
public final class MelodyMessage {
	public static final String MELODY_TITLE = "Click the button on time!";
	private static final int[][] RANGES = {{1, 5}, {10, 14}, {19, 23}, {28, 32}};
	private static final Map<Integer, String> CLAY_PROGRESS = Map.of(2, "Melody 33%", 3, "Melody 67%");
	private static final Pattern PARTY_MELODY = Pattern.compile("^Party > (?:\\[[^]]+] )?(\\w{1,16}): .*?melody\\s*(?:\\(?(\\d)/4\\)?|(\\d{1,3})\\s?%)", Pattern.CASE_INSENSITIVE);
	private static final Pattern TAB_CLASS = Pattern.compile("(\\w{1,16})\\b[^(]*\\((Archer|Berserk|Healer|Mage|Tank)\\b");

	/** Progress of one player: clay row (0-4), where the moving pane is and where the target is (0-4). */
	public static final class Data {
		public @Nullable Integer clay;
		public @Nullable Integer pane;
		public @Nullable Integer purple;
		public String dungeonClass = "";
		long updated;
	}

	private static final Map<String, Data> PLAYERS = new LinkedHashMap<>();
	private static final Data own = new Data();
	private static boolean inMelody;

	private MelodyMessage() {}

	public static void init() {
		ClientTickEvents.END_CLIENT_TICK.register(MelodyMessage::tick);
		ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
			if (overlay || !dev.fancybars.util.SkyblockLocation.isInDungeons()) return;
			String text = ChatFormatting.stripFormatting(message.getString());
			if (text == null) return;
			Matcher m = PARTY_MELODY.matcher(text.trim());
			if (!m.find()) return;
			String name = m.group(1);
			Minecraft mc = Minecraft.getInstance();
			if (mc.player != null && name.equalsIgnoreCase(mc.player.getGameProfile().name())) return;
			int clay;
			if (m.group(2) != null) clay = Integer.parseInt(m.group(2));
			else {
				int percent = Integer.parseInt(m.group(3));
				clay = percent >= 100 ? 4 : percent >= 67 ? 3 : percent >= 33 ? 2 : percent >= 25 ? 1 : 0;
			}
			Data data = PLAYERS.computeIfAbsent(name, n -> new Data());
			data.clay = clay;
			data.updated = System.currentTimeMillis();
		});
		F7Phase.onEnd(MelodyMessage::reset);
		dev.fancybars.util.SkyblockLocation.onLocationChange(MelodyMessage::reset);
		net.fabricmc.fabric.api.client.screen.v1.ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> {
			FancyBarsConfig config = FancyBarsConfig.get();
			if (config.melodyEnabled && config.melodyGui && screen instanceof AbstractContainerScreen<?> container
					&& MELODY_TITLE.equals(container.getTitle().getString())) {
				net.fabricmc.fabric.api.client.screen.v1.Screens.getWidgets(screen).add(new Overlay());
			}
		});
		HudElementRegistry.attachElementAfter(VanillaHudElements.HOTBAR, FancyBarsMod.id("melody_progress"), (graphics, tickCounter) -> {
			FancyBarsConfig config = FancyBarsConfig.get();
			if (config.melodyEnabled && config.melodyGui && (F7Phase.inP3() || inMelody || !PLAYERS.isEmpty()) && hasRows()) render(graphics);
		});
	}

	private static void reset() {
		PLAYERS.clear();
		own.clay = own.pane = own.purple = null;
		inMelody = false;
	}

	private static void tick(Minecraft mc) {
		FancyBarsConfig config = FancyBarsConfig.get();
		if (!config.melodyEnabled || mc.player == null) {
			inMelody = false;
			return;
		}
		boolean open = mc.gui.screen() instanceof AbstractContainerScreen<?> screen && MELODY_TITLE.equals(screen.getTitle().getString());
		if (open && !inMelody) {
			inMelody = true;
			if (config.melodySendCoords && mc.getConnection() != null) {
				mc.getConnection().sendCommand("pc x: " + mc.player.getBlockX() + ", y: " + mc.player.getBlockY() + ", z: " + mc.player.getBlockZ());
			}
		} else if (!open && inMelody) {
			// closed (or finished): your row empties again
			inMelody = false;
			own.clay = own.pane = own.purple = null;
			return;
		}
		if (!open) return;

		AbstractContainerScreen<?> screen = (AbstractContainerScreen<?>) mc.gui.screen();
		Item limeClay = Items.DYED_TERRACOTTA.pick(DyeColor.LIME);
		Item magentaPane = Items.STAINED_GLASS_PANE.pick(DyeColor.MAGENTA);
		Item limePane = Items.STAINED_GLASS_PANE.pick(DyeColor.LIME);
		Integer clay = null;
		Integer purple = null;
		Integer pane = null;
		for (Slot slot : screen.getMenu().slots) {
			if (slot.container == mc.player.getInventory()) continue;
			int index = slot.index;
			ItemStack stack = slot.getItem();
			if (stack.isEmpty()) continue;
			Item item = stack.getItem();
			if (item == limeClay) clay = index / 9;
			else if (item == magentaPane) {
				Integer column = column(index);
				if (column != null) purple = column;
			} else if (item == limePane) {
				Integer column = column(index);
				if (column != null) pane = column;
			}
		}
		if (clay != null && !clay.equals(own.clay)) {
			own.clay = clay;
			String progress = CLAY_PROGRESS.get(clay);
			if (config.melodyProgress && progress != null && mc.getConnection() != null) mc.getConnection().sendCommand("pc " + progress);
		}
		own.purple = purple;
		own.pane = pane;
		own.updated = System.currentTimeMillis();
	}

	/** Column (0-4) of a slot in one of the four 5-wide pane rows, or null. */
	private static @Nullable Integer column(int slot) {
		for (int[] r : RANGES) if (slot >= r[0] && slot <= r[1]) return (slot - r[0]) % 5;
		return null;
	}

	/** Draws the progress GUI over the melody terminal screen (the HUD is hidden behind open screens). */
	private static final class Overlay extends net.minecraft.client.gui.components.AbstractWidget {
		Overlay() {
			super(0, 0, 0, 0, Component.empty());
			this.active = false;
		}

		@Override
		protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
			graphics.nextStratum();
			render(graphics);
		}

		@Override
		protected void updateWidgetNarration(net.minecraft.client.gui.narration.NarrationElementOutput output) {}

		@Override
		public net.minecraft.client.gui.@Nullable ComponentPath nextFocusPath(net.minecraft.client.gui.navigation.FocusNavigationEvent event) {
			return null;
		}
	}

	// ---------------------------------------------------------------- Progress GUI

	private static final int CELL = 9;

	private static boolean hasRows() {
		return !PLAYERS.isEmpty() || own.clay != null || inMelody;
	}

	private static String classOf(String name) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.getConnection() == null) return "";
		for (PlayerInfo info : mc.getConnection().getOnlinePlayers()) {
			if (info.getTabListDisplayName() == null) continue;
			String line = ChatFormatting.stripFormatting(info.getTabListDisplayName().getString());
			if (line == null) continue;
			Matcher m = TAB_CLASS.matcher(line);
			if (m.find() && m.group(1).equalsIgnoreCase(name)) return m.group(2);
		}
		return "";
	}

	private static String classColor(String c) {
		return switch (c) {
			case "Archer" -> "§6";
			case "Mage" -> "§b";
			case "Berserk" -> "§4";
			case "Healer" -> "§d";
			case "Tank" -> "§2";
			default -> "§7";
		};
	}

	private static @Nullable String label(String name) {
		String c = classOf(name);
		return switch (FancyBarsConfig.get().melodyShowPlayer) {
			case 1 -> c.isEmpty() ? "§7?" : classColor(c) + c.toLowerCase();
			case 2 -> "§6" + name;
			case 3 -> "§6" + name + (c.isEmpty() ? "" : " §8(" + classColor(c) + c.toLowerCase() + "§8)");
			default -> null;
		};
	}

	private static void drawRow(GuiGraphicsExtractor graphics, Font font, Data data, int row, String name) {
		int y = row * (CELL + 1);
		for (int i = 0; i < 5; i++) {
			String color = data.pane != null && data.pane == i ? "§a" : data.purple != null && data.purple == i ? "§d" : "§f";
			graphics.text(font, Component.literal(color + "■"), i * CELL, y, 0xFFFFFFFF, true);
		}
		String label = label(name);
		String progress = data.clay != null ? "§e" + data.clay + "/4" : "§70/4";
		graphics.text(font, Component.literal(progress + (label == null ? "" : " " + label)), 5 * CELL + 3, y, Theme.hudText(255), true);
	}

	public static int[] size(boolean sample) {
		Font font = Minecraft.getInstance().font;
		int rows = sample ? 2 : PLAYERS.size() + (own.clay != null || inMelody ? 1 : 0);
		int labelW = font.width("4/4 BigMMan (archer)");
		return new int[]{5 * CELL + 3 + labelW, Math.max(1, rows) * (CELL + 1)};
	}

	public static float[] position(int screenW, int screenH) {
		FancyBarsConfig config = FancyBarsConfig.get();
		if (config.melodyX < 0 || config.melodyY < 0) return new float[]{screenW / 2f + 20, screenH / 2f + 20};
		return new float[]{config.melodyX * screenW, config.melodyY * screenH};
	}

	public static void render(GuiGraphicsExtractor graphics) {
		float[] pos = position(graphics.guiWidth(), graphics.guiHeight());
		renderAt(graphics, pos[0], pos[1], FancyBarsConfig.get().melodyScale, false);
	}

	public static void renderAt(GuiGraphicsExtractor graphics, float x, float y, float scale, boolean sample) {
		Font font = Minecraft.getInstance().font;
		Matrix3x2fStack pose = graphics.pose();
		pose.pushMatrix();
		pose.translate(x, y);
		pose.scale(scale, scale);
		if (FancyBarsConfig.get().textHudBackgrounds) {
			int[] s = size(sample);
			Theme.hudPanel(graphics, -4, -4, s[0] + 8, s[1] + 7, 4, 0.55f, 255);
		}
		Minecraft mc = Minecraft.getInstance();
		String me = mc.player != null ? mc.player.getGameProfile().name() : "You";
		int row = 0;
		if (sample) {
			Data a = new Data();
			a.clay = 3;
			a.pane = 1;
			a.purple = 2;
			drawRow(graphics, font, a, row++, me);
			Data b = new Data();
			b.clay = 2;
			drawRow(graphics, font, b, row, "Teammate");
		} else {
			if (own.clay != null || inMelody) drawRow(graphics, font, own, row++, me);
			for (Map.Entry<String, Data> e : PLAYERS.entrySet()) drawRow(graphics, font, e.getValue(), row++, e.getKey());
		}
		pose.popMatrix();
	}
}
