package dev.fancybars.hud;

import org.joml.Matrix3x2fStack;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import dev.fancybars.config.FancyBarsConfig;

/** Lets the vanilla title / subtitle and the hotbar be moved and scaled (and titles hidden) from the editor. */
public final class TitlesAndHotbar {
	public static final Identifier HOTBAR_SPRITE = Identifier.withDefaultNamespace("hud/hotbar");
	public static final int HOTBAR_W = 182;
	public static final int HOTBAR_H = 22;

	private TitlesAndHotbar() {}

	public static void init() {
		HudElementRegistry.replaceElement(VanillaHudElements.TITLE_AND_SUBTITLE, original -> (graphics, tickCounter) -> {
			FancyBarsConfig config = FancyBarsConfig.get();
			if (!config.titlesShown) return;
			if (config.titleX == 0 && config.titleY == 0 && config.titleScale == 1) {
				original.extractRenderState(graphics, tickCounter);
				return;
			}
			Matrix3x2fStack pose = graphics.pose();
			pose.pushMatrix();
			transformAround(pose, graphics.guiWidth() / 2f, graphics.guiHeight() / 2f, config.titleX, config.titleY, config.titleScale);
			original.extractRenderState(graphics, tickCounter);
			pose.popMatrix();
		});
		HudElementRegistry.replaceElement(VanillaHudElements.BOSS_BAR, original -> (graphics, tickCounter) -> {
			FancyBarsConfig config = FancyBarsConfig.get();
			if (!config.bossBarShown) return;
			if (config.bossBarX == 0 && config.bossBarY == 0 && config.bossBarScale == 1) {
				original.extractRenderState(graphics, tickCounter);
				return;
			}
			Matrix3x2fStack pose = graphics.pose();
			pose.pushMatrix();
			transformAround(pose, graphics.guiWidth() / 2f, 0, config.bossBarX, config.bossBarY, config.bossBarScale);
			original.extractRenderState(graphics, tickCounter);
			pose.popMatrix();
		});
		HudElementRegistry.replaceElement(VanillaHudElements.HOTBAR, original -> (graphics, tickCounter) -> {
			FancyBarsConfig config = FancyBarsConfig.get();
			if (config.hotbarX == 0 && config.hotbarY == 0 && config.hotbarScale == 1) {
				original.extractRenderState(graphics, tickCounter);
				return;
			}
			Matrix3x2fStack pose = graphics.pose();
			pose.pushMatrix();
			transformAround(pose, graphics.guiWidth() / 2f, graphics.guiHeight() - HOTBAR_H / 2f, config.hotbarX, config.hotbarY, config.hotbarScale);
			original.extractRenderState(graphics, tickCounter);
			pose.popMatrix();
		});
	}

	/** Moves by (dx, dy) and scales around (cx, cy). */
	public static void transformAround(Matrix3x2fStack pose, float cx, float cy, float dx, float dy, float scale) {
		pose.translate(cx + dx, cy + dy);
		pose.scale(scale, scale);
		pose.translate(-cx, -cy);
	}

	// ---------------------------------------------------------------- Title geometry (same as vanilla)

	public static Component sampleTitle() {
		return Component.translatable("fancybars.titles.sampleTitle");
	}

	public static Component sampleSubtitle() {
		return Component.translatable("fancybars.titles.sampleSubtitle");
	}

	/** Unscaled title block {x0, y0, x1, y1} relative to the screen centre (title at 4x, subtitle at 2x). */
	public static float[] titleBox() {
		Font font = Minecraft.getInstance().font;
		float tw = font.width(sampleTitle()) * 4;
		float sw = font.width(sampleSubtitle()) * 2;
		float w = Math.max(tw, sw);
		return new float[]{-w / 2, -40, w / 2, 28};
	}

	public static void drawTitlePreview(GuiGraphicsExtractor graphics, float alpha) {
		FancyBarsConfig config = FancyBarsConfig.get();
		Font font = Minecraft.getInstance().font;
		float cx = graphics.guiWidth() / 2f;
		float cy = graphics.guiHeight() / 2f;
		int a = Math.round(alpha * 255) << 24;
		Matrix3x2fStack pose = graphics.pose();
		pose.pushMatrix();
		transformAround(pose, cx, cy, config.titleX, config.titleY, config.titleScale);
		pose.translate(cx, cy);
		pose.pushMatrix();
		pose.scale(4, 4);
		graphics.text(font, sampleTitle(), -font.width(sampleTitle()) / 2, -10, a | 0xFFFFFF, true);
		pose.popMatrix();
		pose.pushMatrix();
		pose.scale(2, 2);
		graphics.text(font, sampleSubtitle(), -font.width(sampleSubtitle()) / 2, 5, a | 0xFFFFFF, true);
		pose.popMatrix();
		pose.popMatrix();
	}

	// ---------------------------------------------------------------- Boss bar geometry (same as vanilla)

	public static final int BOSS_W = 182;
	private static final Identifier BOSS_BG = Identifier.withDefaultNamespace("boss_bar/purple_background");
	private static final Identifier BOSS_FILL = Identifier.withDefaultNamespace("boss_bar/purple_progress");

	/** Unscaled boss bar block {x0, y0, x1, y1} relative to the top centre of the screen (name above, bar below). */
	public static float[] bossBox() {
		return new float[]{-BOSS_W / 2f, 2, BOSS_W / 2f, 17};
	}

	public static void drawBossBarPreview(GuiGraphicsExtractor graphics, float alpha) {
		FancyBarsConfig config = FancyBarsConfig.get();
		Font font = Minecraft.getInstance().font;
		int w = graphics.guiWidth();
		int a = Math.round(alpha * 255) << 24;
		Matrix3x2fStack pose = graphics.pose();
		pose.pushMatrix();
		transformAround(pose, w / 2f, 0, config.bossBarX, config.bossBarY, config.bossBarScale);
		int x = w / 2 - BOSS_W / 2;
		graphics.blitSprite(RenderPipelines.GUI_TEXTURED, BOSS_BG, x, 12, BOSS_W, 5, a | 0xFFFFFF);
		graphics.blitSprite(RenderPipelines.GUI_TEXTURED, BOSS_FILL, BOSS_W, 5, 0, 0, x, 12, BOSS_W * 2 / 3, 5);
		Component name = Component.translatable("fancybars.bossBar.sample");
		graphics.text(font, name, w / 2 - font.width(name) / 2, 3, a | 0xFFFFFF, true);
		pose.popMatrix();
	}

	public static void drawHotbarPreview(GuiGraphicsExtractor graphics, float alpha) {
		FancyBarsConfig config = FancyBarsConfig.get();
		int w = graphics.guiWidth();
		int h = graphics.guiHeight();
		Matrix3x2fStack pose = graphics.pose();
		pose.pushMatrix();
		transformAround(pose, w / 2f, h - HOTBAR_H / 2f, config.hotbarX, config.hotbarY, config.hotbarScale);
		graphics.blitSprite(RenderPipelines.GUI_TEXTURED, HOTBAR_SPRITE, w / 2 - HOTBAR_W / 2, h - HOTBAR_H, HOTBAR_W, HOTBAR_H, (Math.round(alpha * 255) << 24) | 0xFFFFFF);
		Minecraft mc = Minecraft.getInstance();
		if (mc.player != null) {
			for (int i = 0; i < 9; i++) {
				graphics.item(mc.player.getInventory().getItem(i), w / 2 - 88 + i * 20, h - 19);
			}
		}
		pose.popMatrix();
	}
}
