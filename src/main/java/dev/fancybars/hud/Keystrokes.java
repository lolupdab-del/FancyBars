package dev.fancybars.hud;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.joml.Matrix3x2fStack;

import com.mojang.blaze3d.platform.InputConstants;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;

import dev.fancybars.FancyBarsMod;
import dev.fancybars.config.FancyBarsConfig;
import dev.fancybars.util.gui.Theme;

/**
 * Keystrokes display with three layouts:
 * 0 = mouse buttons, 1 = WASD + space + mouse, 2 = full (Tab, Caps, Shift, Q E R, Z X C V, F, WASD, space, mouse).
 */
public final class Keystrokes {
	public static final int MODE_MOUSE = 0;
	public static final int MODE_WASD = 1;
	public static final int MODE_FULL = 2;

	private static final int U = 18;
	private static final int GAP = 2;
	private static final int SPACE_H = 10;
	private static final int MOUSE_H = 16;
	private static final int MOUSE_H_CPS = 22;
	private static final int MOUSE_LEFT = -1;
	private static final int MOUSE_RIGHT = -2;

	private record Key(String label, int code, float x, float y, float w, float h) {}

	private static final Map<String, Float> PRESS = new HashMap<>();
	/** Click timestamps of the last second, for CPS. */
	private static final java.util.ArrayDeque<Long> LEFT_CLICKS = new java.util.ArrayDeque<>();
	private static final java.util.ArrayDeque<Long> RIGHT_CLICKS = new java.util.ArrayDeque<>();
	private static boolean leftWasDown;
	private static boolean rightWasDown;
	private static long lastFrame = Util.getMillis();

	private Keystrokes() {}

	public static void init() {
		HudElementRegistry.attachElementAfter(VanillaHudElements.HOTBAR, FancyBarsMod.id("keystrokes"), (graphics, tickCounter) -> {
			Minecraft mc = Minecraft.getInstance();
			pollClicks();
			if (FancyBarsConfig.get().keystrokesEnabled && mc.player != null && dev.fancybars.util.HudVisibility.allowed()) render(graphics);
		});
	}

	public static Component modeName(int mode) {
		return Component.translatable("fancybars.keystrokes.mode." + Math.clamp(mode, 0, 2));
	}

	private static List<Key> layout(int mode) {
		List<Key> keys = new ArrayList<>();
		float y = 0;
		float width;
		if (mode == MODE_FULL) {
			float wide = U * 1.5f + GAP / 2f;
			String[][] rows = {{"TAB", "Q", "W", "E", "R"}, {"CAPS", "A", "S", "D", "F"}, {"SHIFT", "Z", "X", "C", "V"}};
			int[][] codes = {
					{InputConstants.KEY_TAB, InputConstants.KEY_Q, InputConstants.KEY_W, InputConstants.KEY_E, InputConstants.KEY_R},
					{InputConstants.KEY_CAPSLOCK, InputConstants.KEY_A, InputConstants.KEY_S, InputConstants.KEY_D, InputConstants.KEY_F},
					{InputConstants.KEY_LSHIFT, InputConstants.KEY_Z, InputConstants.KEY_X, InputConstants.KEY_C, InputConstants.KEY_V}};
			for (int r = 0; r < 3; r++) {
				float x = 0;
				for (int c = 0; c < 5; c++) {
					float w = c == 0 ? wide : U;
					keys.add(new Key(rows[r][c], codes[r][c], x, y, w, U));
					x += w + GAP;
				}
				y += U + GAP;
			}
			width = wide + 4 * (U + GAP);
		} else if (mode == MODE_WASD) {
			keys.add(new Key("W", InputConstants.KEY_W, U + GAP, 0, U, U));
			y += U + GAP;
			keys.add(new Key("A", InputConstants.KEY_A, 0, y, U, U));
			keys.add(new Key("S", InputConstants.KEY_S, U + GAP, y, U, U));
			keys.add(new Key("D", InputConstants.KEY_D, (U + GAP) * 2, y, U, U));
			y += U + GAP;
			width = U * 3 + GAP * 2;
		} else {
			width = U * 3 + GAP * 2;
		}
		if (mode != MODE_MOUSE) {
			keys.add(new Key("———", InputConstants.KEY_SPACE, 0, y, width, SPACE_H));
			y += SPACE_H + GAP;
		}
		float half = (width - GAP) / 2f;
		FancyBarsConfig config = FancyBarsConfig.get();
		float mouseH = config.keystrokesLeftCps || config.keystrokesRightCps ? MOUSE_H_CPS : MOUSE_H;
		keys.add(new Key("LMB", MOUSE_LEFT, 0, y, half, mouseH));
		keys.add(new Key("RMB", MOUSE_RIGHT, half + GAP, y, half, mouseH));
		return keys;
	}

	/** Unscaled {width, height} for a mode. */
	public static float[] size(int mode) {
		float w = 0;
		float h = 0;
		for (Key key : layout(mode)) {
			w = Math.max(w, key.x() + key.w());
			h = Math.max(h, key.y() + key.h());
		}
		return new float[]{w, h};
	}

	public static float[] position(int screenW, int screenH) {
		FancyBarsConfig config = FancyBarsConfig.get();
		if (config.keystrokesX < 0 || config.keystrokesY < 0) {
			float[] size = size(config.keystrokesMode);
			return new float[]{screenW - size[0] * config.keystrokesScale - 6, screenH - size[1] * config.keystrokesScale - 40};
		}
		return new float[]{config.keystrokesX * screenW, config.keystrokesY * screenH};
	}

	/** Counts new presses every frame (frames are far shorter than a click, so none are missed). */
	private static void pollClicks() {
		long now = Util.getMillis();
		boolean left = isDown(MOUSE_LEFT);
		boolean right = isDown(MOUSE_RIGHT);
		if (left && !leftWasDown) LEFT_CLICKS.addLast(now);
		if (right && !rightWasDown) RIGHT_CLICKS.addLast(now);
		leftWasDown = left;
		rightWasDown = right;
		while (!LEFT_CLICKS.isEmpty() && now - LEFT_CLICKS.peekFirst() > 1000) LEFT_CLICKS.removeFirst();
		while (!RIGHT_CLICKS.isEmpty() && now - RIGHT_CLICKS.peekFirst() > 1000) RIGHT_CLICKS.removeFirst();
	}

	public static int leftCps() {
		return LEFT_CLICKS.size();
	}

	public static int rightCps() {
		return RIGHT_CLICKS.size();
	}

	private static boolean showCps(int code) {
		FancyBarsConfig config = FancyBarsConfig.get();
		return code == MOUSE_LEFT ? config.keystrokesLeftCps : code == MOUSE_RIGHT && config.keystrokesRightCps;
	}

	private static boolean isDown(int code) {
		Minecraft mc = Minecraft.getInstance();
		if (code == MOUSE_LEFT) return mc.mouseHandler.isLeftPressed();
		if (code == MOUSE_RIGHT) return mc.mouseHandler.isRightPressed();
		return InputConstants.isKeyDown(code);
	}

	public static void render(GuiGraphicsExtractor graphics) {
		float[] pos = position(graphics.guiWidth(), graphics.guiHeight());
		renderAt(graphics, pos[0], pos[1], FancyBarsConfig.get().keystrokesScale, FancyBarsConfig.get().keystrokesMode, 1, true);
	}

	public static void renderAt(GuiGraphicsExtractor graphics, float x, float y, float scale, int mode, float alpha, boolean live) {
		Font font = Minecraft.getInstance().font;
		long now = Util.getMillis();
		float dt = Math.min(0.1f, (now - lastFrame) / 1000f);
		lastFrame = now;
		int a = Math.round(alpha * 255);
		Matrix3x2fStack pose = graphics.pose();
		pose.pushMatrix();
		pose.translate(x, y);
		pose.scale(scale, scale);
		for (Key key : layout(mode)) {
			// smooth press / release
			float target = live && isDown(key.code()) ? 1 : 0;
			float p = PRESS.getOrDefault(key.label(), 0f);
			p += (target - p) * Math.min(1, dt * 22);
			PRESS.put(key.label(), p);

			int kx = Math.round(key.x());
			int ky = Math.round(key.y());
			int kw = Math.round(key.x() + key.w()) - kx;
			int kh = Math.round(key.h());
			int idle = Theme.hudBg(0.88f, a);
			// idle background, then the accent faded in on top (no colour blending, so nothing off-theme shows mid-press)
			Theme.roundedRect(graphics, kx, ky, kw, kh, 4, idle);
			int pressedAlpha = Math.round(a * 0.85f * Math.clamp(p, 0, 1));
			if (pressedAlpha > 0) Theme.roundedRect(graphics, kx, ky, kw, kh, 4, pressedAlpha << 24 | Theme.accent() & 0xFFFFFF);
			if (FancyBarsConfig.get().hudBorders) Theme.border(graphics, kx, ky, kw, kh, 4, Theme.hudLine(), a);
			int textColor = Theme.lerpColor(Theme.hudText(255), Theme.night(Theme.TEXT_ON_ACCENT), p);
			Component label = Theme.bold(Component.literal(key.label()));
			float lw = font.width(label);
			float scaleText = Math.min(1, (kw - 3) / lw);
			boolean cps = showCps(key.code());
			float labelCenterY = cps ? ky + kh / 2f - 3.5f : ky + kh / 2f;
			pose.pushMatrix();
			pose.translate(kx + kw / 2f, labelCenterY);
			pose.scale(scaleText, scaleText);
			Theme.drawAtCapTop(graphics, font, label, -lw / 2f, -Theme.UI_CAP / 2f, Theme.UI_CAP, (a << 24) | (textColor & 0xFFFFFF));
			pose.popMatrix();
			if (cps) {
				int value = !live ? 0 : key.code() == MOUSE_LEFT ? leftCps() : rightCps();
				Component cpsText = Theme.styled(Component.literal(value + " CPS"));
				float cw = font.width(cpsText);
				float cpsScale = Math.min(0.75f, (kw - 3) / cw);
				pose.pushMatrix();
				pose.translate(kx + kw / 2f, ky + kh / 2f + 5f);
				pose.scale(cpsScale, cpsScale);
				int cpsColor = Theme.lerpColor(Theme.hudTextSoft(255), Theme.night(Theme.TEXT_ON_ACCENT), p);
				Theme.drawAtCapTop(graphics, font, cpsText, -cw / 2f, -Theme.UI_CAP / 2f, Theme.UI_CAP, (a << 24) | (cpsColor & 0xFFFFFF));
				pose.popMatrix();
			}
		}
		pose.popMatrix();
	}
}
