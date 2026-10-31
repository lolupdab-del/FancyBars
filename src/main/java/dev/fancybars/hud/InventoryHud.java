package dev.fancybars.hud;

import org.joml.Matrix3x2fStack;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import dev.fancybars.FancyBarsMod;
import dev.fancybars.config.FancyBarsConfig;
import dev.fancybars.util.gui.Theme;

/** Your main inventory (the 27 slots above the hotbar) as a small clean grid on the HUD. */
public final class InventoryHud {
	public static final int SLOT = 18;
	public static final int PAD = 4;
	public static final int WIDTH = 9 * SLOT + PAD * 2;
	public static final int HEIGHT = 3 * SLOT + PAD * 2;

	private InventoryHud() {}

	public static void init() {
		HudElementRegistry.attachElementAfter(VanillaHudElements.HOTBAR, FancyBarsMod.id("inventory_hud"), (graphics, tickCounter) -> {
			Minecraft mc = Minecraft.getInstance();
			if (FancyBarsConfig.get().invHudEnabled && mc.player != null && mc.gui.screen() == null) render(graphics);
		});
	}

	public static float[] position(int screenW, int screenH) {
		FancyBarsConfig config = FancyBarsConfig.get();
		if (config.invHudX < 0 || config.invHudY < 0) return new float[]{6, Math.round(screenH * 0.30f)};
		return new float[]{config.invHudX * screenW, config.invHudY * screenH};
	}

	public static void render(GuiGraphicsExtractor graphics) {
		float[] pos = position(graphics.guiWidth(), graphics.guiHeight());
		renderAt(graphics, pos[0], pos[1], FancyBarsConfig.get().invHudScale, 1);
	}

	public static void renderAt(GuiGraphicsExtractor graphics, float x, float y, float scale, float alpha) {
		FancyBarsConfig config = FancyBarsConfig.get();
		Minecraft mc = Minecraft.getInstance();
		Font font = mc.font;
		Player player = mc.player;
		Matrix3x2fStack pose = graphics.pose();
		pose.pushMatrix();
		pose.translate(x, y);
		pose.scale(scale, scale);
		int a = Math.round(alpha * 255);
		if (config.invHudBackground) {
			Theme.roundedRect(graphics, 0, 0, WIDTH, HEIGHT, 5, Theme.hudBg(0.55f, a));
			Theme.roundedOutline(graphics, 0, 0, WIDTH, HEIGHT, 5, Theme.hudAccent(0.35f, a));
		}
		for (int i = 0; i < 27; i++) {
			int sx = PAD + (i % 9) * SLOT;
			int sy = PAD + (i / 9) * SLOT;
			if (config.invHudSlots) Theme.roundedRect(graphics, sx + 1, sy + 1, SLOT - 2, SLOT - 2, 3, (Math.round(a * 0.18f) << 24) | 0xFFFFFF);
			if (player == null) continue;
			ItemStack stack = player.getInventory().getItem(9 + i);
			if (stack.isEmpty()) continue;
			graphics.item(stack, sx + 1, sy + 1);
			graphics.itemDecorations(font, stack, sx + 1, sy + 1);
		}
		pose.popMatrix();
	}
}
