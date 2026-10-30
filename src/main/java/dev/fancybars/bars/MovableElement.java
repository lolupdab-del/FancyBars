package dev.fancybars.bars;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

import org.joml.Matrix3x2fStack;

import dev.fancybars.config.FancyBarsConfig;
import dev.fancybars.dungeons.DungeonSecrets;

/**
 * A HUD element (other than the bars) that can be moved, scaled and toggled in the editor.
 */
public interface MovableElement {
	float MIN_SCALE = 0.5f;
	float MAX_SCALE = 3f;

	/** {x, y, w, h} on screen. */
	float[] rect(int screenW, int screenH);

	void drawPreview(GuiGraphicsExtractor graphics, int screenW, int screenH);

	/** Moves the element so its top-left corner is at (x, y). */
	void moveTo(float x, float y, int screenW, int screenH);

	float scale();

	void setScale(float scale);

	boolean enabled();

	void setEnabled(boolean enabled);

	void reset();

	Component name();

	/** Whether dragging should snap the element's centre to the screen's vertical centre line. */
	default boolean snapsToCenter() {
		return true;
	}

	// ---------------------------------------------------------------- Held item name

	MovableElement HELD_ITEM = new MovableElement() {
		private Component sampleText() {
			Minecraft mc = Minecraft.getInstance();
			if (mc.player != null && !mc.player.getMainHandItem().isEmpty()) return mc.player.getMainHandItem().getHoverName();
			return Component.translatable("fancybars.editor.heldItemSample");
		}

		@Override
		public float[] rect(int screenW, int screenH) {
			FancyBarsConfig config = FancyBarsConfig.get();
			float w = Minecraft.getInstance().font.width(sampleText());
			float s = config.heldItemNameScale;
			float cx = screenW / 2f + config.heldItemNameX;
			float cy = screenH - HeldItemName.VANILLA_OFFSET_FROM_BOTTOM + 4.5f + config.heldItemNameY;
			return new float[]{cx - w * s / 2f, cy - 4.5f * s, w * s, 9 * s};
		}

		@Override
		public void drawPreview(GuiGraphicsExtractor graphics, int screenW, int screenH) {
			Font font = Minecraft.getInstance().font;
			Component text = sampleText();
			Matrix3x2fStack pose = graphics.pose();
			pose.pushMatrix();
			HeldItemName.applyTransform(graphics, pose);
			int alpha = enabled() ? 0xFF000000 : 0x55000000;
			graphics.text(font, text, (screenW - font.width(text)) / 2, screenH - HeldItemName.VANILLA_OFFSET_FROM_BOTTOM, alpha | 0xFFFFFF, true);
			pose.popMatrix();
		}

		@Override
		public void moveTo(float x, float y, int screenW, int screenH) {
			float[] r = rect(screenW, screenH);
			FancyBarsConfig config = FancyBarsConfig.get();
			config.heldItemNameX = Math.round(x + r[2] / 2f - screenW / 2f);
			config.heldItemNameY = Math.round(y + r[3] / 2f - (screenH - HeldItemName.VANILLA_OFFSET_FROM_BOTTOM + 4.5f));
		}

		@Override
		public float scale() {
			return FancyBarsConfig.get().heldItemNameScale;
		}

		@Override
		public void setScale(float scale) {
			FancyBarsConfig.get().heldItemNameScale = scale;
		}

		@Override
		public boolean enabled() {
			return FancyBarsConfig.get().heldItemNameEnabled;
		}

		@Override
		public void setEnabled(boolean enabled) {
			FancyBarsConfig.get().heldItemNameEnabled = enabled;
		}

		@Override
		public void reset() {
			HeldItemName.reset();
		}

		@Override
		public Component name() {
			return Component.translatable("fancybars.editor.element.heldItem");
		}
	};

	// ---------------------------------------------------------------- Dungeon secrets display

	MovableElement SECRETS = new MovableElement() {
		@Override
		public float[] rect(int screenW, int screenH) {
			FancyBarsConfig config = FancyBarsConfig.get();
			float[] pos = DungeonSecrets.position(screenW, screenH, 5, 10);
			int[] size = DungeonSecrets.size(5, 10);
			return new float[]{pos[0], pos[1], size[0] * config.secretsScale, size[1] * config.secretsScale};
		}

		@Override
		public void drawPreview(GuiGraphicsExtractor graphics, int screenW, int screenH) {
			DungeonSecrets.renderSample(graphics);
			if (!enabled()) {
				float[] r = rect(screenW, screenH);
				graphics.fill(Math.round(r[0]) - 1, Math.round(r[1]) - 1, Math.round(r[0] + r[2]) + 1, Math.round(r[1] + r[3]) + 1, 0xAA000000);
			}
		}

		@Override
		public void moveTo(float x, float y, int screenW, int screenH) {
			FancyBarsConfig config = FancyBarsConfig.get();
			float[] r = rect(screenW, screenH);
			config.secretsX = Math.clamp(x, 0, Math.max(0, screenW - r[2])) / screenW;
			config.secretsY = Math.clamp(y, 0, Math.max(0, screenH - r[3])) / screenH;
		}

		@Override
		public float scale() {
			return FancyBarsConfig.get().secretsScale;
		}

		@Override
		public void setScale(float scale) {
			FancyBarsConfig.get().secretsScale = scale;
		}

		@Override
		public boolean enabled() {
			return FancyBarsConfig.get().secretsEnabled;
		}

		@Override
		public void setEnabled(boolean enabled) {
			FancyBarsConfig.get().secretsEnabled = enabled;
		}

		@Override
		public void reset() {
			FancyBarsConfig config = FancyBarsConfig.get();
			config.secretsX = -1;
			config.secretsY = -1;
			config.secretsScale = 1;
			FancyBarsConfig.save();
		}

		@Override
		public Component name() {
			return Component.translatable("fancybars.editor.element.secrets");
		}

		@Override
		public boolean snapsToCenter() {
			return false;
		}
	};

	// ---------------------------------------------------------------- Action bar

	MovableElement ACTION_BAR = new MovableElement() {
		private Component text() {
			Component current = dev.fancybars.actionbar.ActionBar.current();
			return current != null && !current.getString().isBlank() ? current : dev.fancybars.actionbar.ActionBar.sample();
		}

		@Override
		public float[] rect(int screenW, int screenH) {
			int[] center = dev.fancybars.actionbar.ActionBar.center(screenW, screenH);
			int[] size = dev.fancybars.actionbar.ActionBar.size(text());
			float s = scale();
			return new float[]{center[0] - size[0] * s / 2f, center[1] - 8.5f * s, size[0] * s, size[1] * s};
		}

		@Override
		public void drawPreview(GuiGraphicsExtractor graphics, int screenW, int screenH) {
			int[] center = dev.fancybars.actionbar.ActionBar.center(screenW, screenH);
			dev.fancybars.actionbar.ActionBar.draw(graphics, text(), center[0], center[1], scale(), enabled() ? 1 : 0.35f);
		}

		@Override
		public void moveTo(float x, float y, int screenW, int screenH) {
			float[] r = rect(screenW, screenH);
			FancyBarsConfig config = FancyBarsConfig.get();
			config.actionBarX = Math.round(x + r[2] / 2f - screenW / 2f);
			config.actionBarY = Math.round(y + r[3] / 2f - (screenH - dev.fancybars.actionbar.ActionBar.VANILLA_FROM_BOTTOM));
		}

		@Override
		public float scale() {
			return FancyBarsConfig.get().actionBarScale;
		}

		@Override
		public void setScale(float scale) {
			FancyBarsConfig.get().actionBarScale = scale;
		}

		@Override
		public boolean enabled() {
			return FancyBarsConfig.get().actionBarShown;
		}

		@Override
		public void setEnabled(boolean enabled) {
			FancyBarsConfig.get().actionBarShown = enabled;
		}

		@Override
		public void reset() {
			FancyBarsConfig config = FancyBarsConfig.get();
			config.actionBarX = 0;
			config.actionBarY = 0;
			config.actionBarScale = 1;
			FancyBarsConfig.save();
		}

		@Override
		public Component name() {
			return Component.translatable("fancybars.editor.element.actionBar");
		}
	};

	// ---------------------------------------------------------------- Rare drop tracker

	MovableElement RARE_DROPS = new MovableElement() {
		@Override
		public float[] rect(int screenW, int screenH) {
			FancyBarsConfig config = FancyBarsConfig.get();
			float[] pos = dev.fancybars.dungeons.RareDropTracker.position(screenW, screenH, true);
			int[] size = dev.fancybars.dungeons.RareDropTracker.size(true);
			return new float[]{pos[0], pos[1], size[0] * config.rareDropsScale, size[1] * config.rareDropsScale};
		}

		@Override
		public void drawPreview(GuiGraphicsExtractor graphics, int screenW, int screenH) {
			float[] pos = dev.fancybars.dungeons.RareDropTracker.position(screenW, screenH, true);
			dev.fancybars.dungeons.RareDropTracker.renderAt(graphics, pos[0], pos[1], scale(), true);
			if (!enabled()) {
				float[] r = rect(screenW, screenH);
				graphics.fill(Math.round(r[0]) - 1, Math.round(r[1]) - 1, Math.round(r[0] + r[2]) + 1, Math.round(r[1] + r[3]) + 1, 0xAA000000);
			}
		}

		@Override
		public void moveTo(float x, float y, int screenW, int screenH) {
			FancyBarsConfig config = FancyBarsConfig.get();
			float[] r = rect(screenW, screenH);
			config.rareDropsX = Math.clamp(x, 0, Math.max(0, screenW - r[2])) / screenW;
			config.rareDropsY = Math.clamp(y, 0, Math.max(0, screenH - r[3])) / screenH;
		}

		@Override
		public float scale() {
			return FancyBarsConfig.get().rareDropsScale;
		}

		@Override
		public void setScale(float scale) {
			FancyBarsConfig.get().rareDropsScale = scale;
		}

		@Override
		public boolean enabled() {
			return FancyBarsConfig.get().rareDropsHud;
		}

		@Override
		public void setEnabled(boolean enabled) {
			FancyBarsConfig.get().rareDropsHud = enabled;
		}

		@Override
		public void reset() {
			FancyBarsConfig config = FancyBarsConfig.get();
			config.rareDropsX = -1;
			config.rareDropsY = -1;
			config.rareDropsScale = 1;
			FancyBarsConfig.save();
		}

		@Override
		public Component name() {
			return Component.translatable("fancybars.editor.element.rareDrops");
		}

		@Override
		public boolean snapsToCenter() {
			return false;
		}
	};

	MovableElement[] ALL = {HELD_ITEM, SECRETS, ACTION_BAR, RARE_DROPS};
}
