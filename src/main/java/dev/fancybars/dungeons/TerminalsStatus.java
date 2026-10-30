package dev.fancybars.dungeons;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.joml.Matrix3x2fStack;

import com.mojang.blaze3d.vertex.PoseStack;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.blockentity.BeaconRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;

import dev.fancybars.FancyBarsMod;
import dev.fancybars.config.FancyBarsConfig;
import dev.fancybars.util.SkyblockLocation;

/**
 * Terminals Status (ported from Odin): during floor 7 phase 3, shows which terminals, devices and levers are still
 * inactive (box / text / beacon in the world, optional through walls, can hide Hypixel's own floating names), plus a
 * "Term Info" HUD: Levers x/2, Terms x/4 (x/5 in section 2), Device ✔/✘, Gate ✔/✘.
 */
public final class TerminalsStatus {
	public enum Kind {
		TERMINAL("Terminal"), DEVICE("Device"), LEVER("Lever"), OTHER(null);

		final String label;

		Kind(String label) {
			this.label = label;
		}

		static Kind of(String name) {
			return switch (name) {
				case "Not Activated" -> LEVER;
				case "Inactive Terminal" -> TERMINAL;
				case "Inactive" -> DEVICE;
				default -> {
					String lower = name.toLowerCase();
					yield lower.contains("inactive") || lower.contains("not activated") || lower.contains("click here") ? OTHER : null;
				}
			};
		}

		boolean shown() {
			int show = FancyBarsConfig.get().termShow;
			return switch (this) {
				case TERMINAL -> show == 0 || show == 1 || show == 2;
				case DEVICE -> show == 0 || show == 3;
				case LEVER -> show == 0 || show == 1 || show == 4;
				case OTHER -> false;
			};
		}
	}

	private static final Pattern COMPLETED = Pattern.compile("^(.{1,16}) (activated|completed) a (terminal|lever|device)! \\((\\d)/(\\d)\\)$");
	private static final Pattern GATE = Pattern.compile("^The gate has been destroyed!$");

	private static final Map<ArmorStand, Kind> INACTIVE = new HashMap<>();
	private static boolean shouldRender;
	private static boolean firstInSection;
	private static boolean complete;
	private static int lastCompleted;
	private static boolean device;
	private static int terminals;
	private static boolean gate;
	private static int section = 1;
	private static int levers;

	private TerminalsStatus() {}

	private static void resetState() {
		firstInSection = false;
		lastCompleted = 0;
		complete = false;
		device = false;
		terminals = 0;
		gate = false;
		section = 1;
		levers = 0;
	}

	private static void newSection() {
		firstInSection = false;
		complete = false;
		device = false;
		terminals = 0;
		gate = false;
		levers = 0;
		section++;
	}

	public static void init() {
		F7Phase.onStart(() -> {
			shouldRender = true;
			resetState();
		});
		F7Phase.onEnd(() -> {
			INACTIVE.clear();
			shouldRender = false;
			resetState();
		});
		ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
			if (overlay || !SkyblockLocation.isInDungeons()) return;
			String text = ChatFormatting.stripFormatting(message.getString());
			if (text == null) return;
			text = text.trim();
			Matcher m = COMPLETED.matcher(text);
			if (m.matches()) {
				int done = Integer.parseInt(m.group(4));
				int total = Integer.parseInt(m.group(5));
				if (done == 1) firstInSection = true;
				if (done == total) {
					if (gate) newSection();
					else complete = true;
					return;
				}
				switch (m.group(3)) {
					case "device" -> {
						if (!firstInSection || lastCompleted != done) device = true;
					}
					case "terminal" -> terminals++;
					case "lever" -> levers++;
					default -> {}
				}
				lastCompleted = done;
			} else if (GATE.matcher(text).matches()) {
				gate = true;
				if (complete) newSection();
			}
		});
		// find the inactive terminals / devices / levers (Hypixel labels them with named armour stands)
		ClientTickEvents.END_CLIENT_TICK.register(mc -> {
			if (!FancyBarsConfig.get().termStatusEnabled || mc.level == null || !F7Phase.inP3()) {
				if (!INACTIVE.isEmpty()) INACTIVE.clear();
				return;
			}
			INACTIVE.clear();
			for (Entity entity : mc.level.entitiesForRendering()) {
				if (!(entity instanceof ArmorStand stand) || stand.getCustomName() == null) continue;
				Kind kind = Kind.of(stand.getCustomName().getString());
				if (kind == null) continue;
				INACTIVE.put(stand, kind);
				if (kind.shown()) stand.setCustomNameVisible(!FancyBarsConfig.get().termHideDefault);
			}
		});
		LevelRenderEvents.COLLECT_SUBMITS.register(context -> {
			FancyBarsConfig config = FancyBarsConfig.get();
			if (!config.termStatusEnabled || INACTIVE.isEmpty() || !F7Phase.inP3()) return;
			Vec3 cam = context.levelState().cameraRenderState.pos;
			PoseStack pose = context.poseStack();
			int color = 0xFF000000 | config.termColor;
			int style = config.termStyle; // 0 box+text+beacon, 1 box+text, 2 box, 3 text
			boolean box = style <= 2;
			boolean text = style <= 1 || style == 3;
			boolean beacon = style == 0;
			float time = (Minecraft.getInstance().level.getGameTime() % 40) + Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false);
			for (Map.Entry<ArmorStand, Kind> e : INACTIVE.entrySet()) {
				Kind kind = e.getValue();
				if (kind.label == null || !kind.shown()) continue;
				ArmorStand stand = e.getKey();
				Vec3 p = stand.position();
				pose.pushPose();
				pose.translate(p.x - cam.x, p.y - cam.y, p.z - cam.z);
				if (box) {
					AABB bb = stand.getBoundingBox().move(-p.x, -p.y, -p.z);
					context.submitNodeCollector().submitShapeOutline(pose, Shapes.create(bb), RenderTypes.lines(), color, 2.5f, false);
				}
				if (beacon) {
					pose.pushPose();
					pose.translate(-0.5, 0, -0.5);
					BeaconRenderer.submitBeaconBeam(pose, context.submitNodeCollector(), BeaconRenderer.BEAM_LOCATION, 1f, time, 0, 256, color, 0.2f, 0.25f);
					pose.popPose();
				}
				if (text) {
					context.submitNodeCollector().submitNameTag(pose, new Vec3(0, 2.5, 0), 0, Component.literal(kind.label).withColor(config.termColor),
							config.termThroughWalls, 0xF000F0, context.levelState().cameraRenderState);
				}
				pose.popPose();
			}
		});
		HudElementRegistry.attachElementAfter(VanillaHudElements.HOTBAR, FancyBarsMod.id("term_info"), (graphics, tickCounter) -> {
			FancyBarsConfig config = FancyBarsConfig.get();
			if (config.termStatusEnabled && config.termHud && shouldRender && SkyblockLocation.isInDungeons()) render(graphics);
		});
	}

	// ---------------------------------------------------------------- Term Info HUD

	private static String[] lines() {
		int termTotal = section == 2 ? 5 : 4;
		return new String[]{
				"§6Levers " + (levers == 2 ? "§a" : "§c") + levers + "§8/§a2",
				"§6Terms " + (terminals == termTotal ? "§a" : "§c") + terminals + "§8/§a" + termTotal,
				"§6Device " + (device ? "§a✔" : "§c✘"),
				"§6Gate " + (gate ? "§a✔" : "§c✘")};
	}

	public static int[] size() {
		Font font = Minecraft.getInstance().font;
		int w = 0;
		for (String line : lines()) w = Math.max(w, font.width(line));
		return new int[]{Math.max(w, font.width("Levers 0/2")), 36};
	}

	public static float[] position(int screenW, int screenH) {
		FancyBarsConfig config = FancyBarsConfig.get();
		if (config.termX < 0 || config.termY < 0) return new float[]{8, screenH / 2f - 18};
		return new float[]{config.termX * screenW, config.termY * screenH};
	}

	public static void render(GuiGraphicsExtractor graphics) {
		float[] pos = position(graphics.guiWidth(), graphics.guiHeight());
		renderAt(graphics, pos[0], pos[1], FancyBarsConfig.get().termScale);
	}

	public static void renderAt(GuiGraphicsExtractor graphics, float x, float y, float scale) {
		Font font = Minecraft.getInstance().font;
		Matrix3x2fStack pose = graphics.pose();
		pose.pushMatrix();
		pose.translate(x, y);
		pose.scale(scale, scale);
		String[] lines = lines();
		for (int i = 0; i < lines.length; i++) graphics.text(font, Component.literal(lines[i]), 0, i * 9, 0xFFFFFFFF, true);
		pose.popMatrix();
	}
}
