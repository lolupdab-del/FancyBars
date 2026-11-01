package dev.fancybars.hud;

import org.joml.Matrix3x2fStack;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import dev.fancybars.FancyBarsMod;
import dev.fancybars.bars.MovableElement;
import dev.fancybars.config.FancyBarsConfig;
import dev.fancybars.util.gui.Theme;

/**
 * Shows the armor you're wearing (helmet to boots) in a movable HUD. Idea from Devonian's Armor Display
 * (https://github.com/Synnerz/devonian, GPL-3.0): slots with optional background, border and a barrier for empty slots.
 */
public final class ArmorHud {
	private static final int SLOT = 18;
	private static final EquipmentSlot[] SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
	private static final ItemStack BARRIER = new ItemStack(Items.BARRIER);
	private static final ItemStack[] SAMPLE = {
			new ItemStack(Items.DIAMOND_HELMET), new ItemStack(Items.DIAMOND_CHESTPLATE),
			new ItemStack(Items.DIAMOND_LEGGINGS), new ItemStack(Items.DIAMOND_BOOTS)};

	private ArmorHud() {}

	public static void init() {
		HudElementRegistry.attachElementAfter(VanillaHudElements.HOTBAR, FancyBarsMod.id("armor_display"), (graphics, tickCounter) -> {
			Minecraft mc = Minecraft.getInstance();
			if (FancyBarsConfig.get().armorEnabled && mc.player != null && dev.fancybars.util.HudVisibility.allowed()) {
				float[] pos = position(graphics.guiWidth(), graphics.guiHeight());
				renderAt(graphics, pos[0], pos[1], FancyBarsConfig.get().armorScale, 1, false);
			}
		});
	}

	public static int[] size() {
		boolean horizontal = FancyBarsConfig.get().armorHorizontal;
		int gap = gap();
		int length = SLOT * 4 + gap * 3;
		return horizontal ? new int[]{length, SLOT} : new int[]{SLOT, length};
	}

	private static int gap() {
		return FancyBarsConfig.get().armorBackground ? 1 : 0;
	}

	public static float[] position(int screenW, int screenH) {
		FancyBarsConfig c = FancyBarsConfig.get();
		if (c.armorX < 0 || c.armorY < 0) return new float[]{6, screenH / 2f - size()[1] * c.armorScale / 2f};
		return new float[]{c.armorX * screenW, c.armorY * screenH};
	}

	public static void renderAt(GuiGraphicsExtractor graphics, float x, float y, float scale, float alpha, boolean sample) {
		FancyBarsConfig c = FancyBarsConfig.get();
		Minecraft mc = Minecraft.getInstance();
		int a = Math.round(alpha * 255);
		Matrix3x2fStack pose = graphics.pose();
		pose.pushMatrix();
		pose.translate(x, y);
		pose.scale(scale, scale);
		int step = SLOT + gap();
		for (int i = 0; i < 4; i++) {
			int sx = c.armorHorizontal ? i * step : 0;
			int sy = c.armorHorizontal ? 0 : i * step;
			if (c.armorBackground) Theme.roundedRect(graphics, sx, sy, SLOT, SLOT, 3, Theme.hudBg(0.55f, a));
			if (c.armorBorder) Theme.border(graphics, sx, sy, SLOT, SLOT, 3, Theme.hudLine(), a);
			ItemStack stack = sample || mc.player == null ? sampleOrReal(i) : mc.player.getItemBySlot(SLOTS[i]);
			if (stack.isEmpty()) {
				if (!c.armorBarrier) continue;
				stack = BARRIER;
			}
			graphics.fakeItem(stack, sx + 1, sy + 1);
		}
		pose.popMatrix();
	}

	private static ItemStack sampleOrReal(int i) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player != null) {
			ItemStack real = mc.player.getItemBySlot(SLOTS[i]);
			if (!real.isEmpty()) return real;
		}
		return SAMPLE[i];
	}

	// ---------------------------------------------------------------- editor

	public static final MovableElement ELEMENT = new MovableElement() {
		@Override
		public float[] rect(int screenW, int screenH) {
			float[] pos = position(screenW, screenH);
			int[] size = size();
			float s = scale();
			return new float[]{pos[0], pos[1], size[0] * s, size[1] * s};
		}

		@Override
		public void drawPreview(GuiGraphicsExtractor graphics, int screenW, int screenH) {
			float[] pos = position(screenW, screenH);
			renderAt(graphics, pos[0], pos[1], scale(), enabled() ? 1 : 0.35f, true);
		}

		@Override
		public void moveTo(float x, float y, int screenW, int screenH) {
			FancyBarsConfig c = FancyBarsConfig.get();
			float[] r = rect(screenW, screenH);
			c.armorX = Math.clamp(x, 0, Math.max(0, screenW - r[2])) / screenW;
			c.armorY = Math.clamp(y, 0, Math.max(0, screenH - r[3])) / screenH;
		}

		@Override
		public float scale() {
			return FancyBarsConfig.get().armorScale;
		}

		@Override
		public void setScale(float scale) {
			FancyBarsConfig.get().armorScale = scale;
		}

		@Override
		public boolean enabled() {
			return FancyBarsConfig.get().armorEnabled;
		}

		@Override
		public void setEnabled(boolean enabled) {
			FancyBarsConfig.get().armorEnabled = enabled;
		}

		@Override
		public void reset() {
			FancyBarsConfig c = FancyBarsConfig.get();
			c.armorX = -1;
			c.armorY = -1;
			c.armorScale = 1;
			FancyBarsConfig.save();
		}

		@Override
		public Component name() {
			return Component.translatable("fancybars.editor.element.armor");
		}

		@Override
		public boolean snapsToCenter() {
			return false;
		}
	};
}
