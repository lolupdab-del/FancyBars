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

	// ---------------------------------------------------------------- Inventory HUD / keystrokes

	MovableElement INVENTORY = new MovableElement() {
		@Override
		public float[] rect(int screenW, int screenH) {
			FancyBarsConfig config = FancyBarsConfig.get();
			float[] pos = dev.fancybars.hud.InventoryHud.position(screenW, screenH);
			float[] size = new float[]{dev.fancybars.hud.InventoryHud.WIDTH, dev.fancybars.hud.InventoryHud.HEIGHT};
			return new float[]{pos[0], pos[1], size[0] * config.invHudScale, size[1] * config.invHudScale};
		}

		@Override
		public void drawPreview(GuiGraphicsExtractor graphics, int screenW, int screenH) {
			FancyBarsConfig config = FancyBarsConfig.get();
			float[] pos = dev.fancybars.hud.InventoryHud.position(screenW, screenH);
			dev.fancybars.hud.InventoryHud.renderAt(graphics, pos[0], pos[1], config.invHudScale, enabled() ? 1 : 0.35f);
		}

		@Override
		public void moveTo(float x, float y, int screenW, int screenH) {
			FancyBarsConfig config = FancyBarsConfig.get();
			float[] r = rect(screenW, screenH);
			config.invHudX = Math.clamp(x, 0, Math.max(0, screenW - r[2])) / screenW;
			config.invHudY = Math.clamp(y, 0, Math.max(0, screenH - r[3])) / screenH;
		}

		@Override
		public float scale() {
			return FancyBarsConfig.get().invHudScale;
		}

		@Override
		public void setScale(float scale) {
			FancyBarsConfig.get().invHudScale = scale;
		}

		@Override
		public boolean enabled() {
			return FancyBarsConfig.get().invHudEnabled;
		}

		@Override
		public void setEnabled(boolean enabled) {
			FancyBarsConfig.get().invHudEnabled = enabled;
		}

		@Override
		public void reset() {
			FancyBarsConfig config = FancyBarsConfig.get();
			config.invHudX = -1;
			config.invHudY = -1;
			config.invHudScale = 1;
			FancyBarsConfig.save();
		}

		@Override
		public Component name() {
			return Component.translatable("fancybars.editor.element.inventory");
		}

		@Override
		public boolean snapsToCenter() {
			return false;
		}
	};

	MovableElement KEYSTROKES = new MovableElement() {
		@Override
		public float[] rect(int screenW, int screenH) {
			FancyBarsConfig config = FancyBarsConfig.get();
			float[] pos = dev.fancybars.hud.Keystrokes.position(screenW, screenH);
			float[] size = dev.fancybars.hud.Keystrokes.size(config.keystrokesMode);
			return new float[]{pos[0], pos[1], size[0] * config.keystrokesScale, size[1] * config.keystrokesScale};
		}

		@Override
		public void drawPreview(GuiGraphicsExtractor graphics, int screenW, int screenH) {
			FancyBarsConfig config = FancyBarsConfig.get();
			float[] pos = dev.fancybars.hud.Keystrokes.position(screenW, screenH);
			dev.fancybars.hud.Keystrokes.renderAt(graphics, pos[0], pos[1], config.keystrokesScale, config.keystrokesMode, enabled() ? 1 : 0.35f, true);
		}

		@Override
		public void moveTo(float x, float y, int screenW, int screenH) {
			FancyBarsConfig config = FancyBarsConfig.get();
			float[] r = rect(screenW, screenH);
			config.keystrokesX = Math.clamp(x, 0, Math.max(0, screenW - r[2])) / screenW;
			config.keystrokesY = Math.clamp(y, 0, Math.max(0, screenH - r[3])) / screenH;
		}

		@Override
		public float scale() {
			return FancyBarsConfig.get().keystrokesScale;
		}

		@Override
		public void setScale(float scale) {
			FancyBarsConfig.get().keystrokesScale = scale;
		}

		@Override
		public boolean enabled() {
			return FancyBarsConfig.get().keystrokesEnabled;
		}

		@Override
		public void setEnabled(boolean enabled) {
			FancyBarsConfig.get().keystrokesEnabled = enabled;
		}

		@Override
		public void reset() {
			FancyBarsConfig config = FancyBarsConfig.get();
			config.keystrokesX = -1;
			config.keystrokesY = -1;
			config.keystrokesScale = 1;
			FancyBarsConfig.save();
		}

		@Override
		public Component name() {
			return Component.translatable("fancybars.editor.element.keystrokes");
		}

		@Override
		public boolean snapsToCenter() {
			return false;
		}
	};

	// ---------------------------------------------------------------- Commissions

	MovableElement COMMISSIONS = new MovableElement() {
		private java.util.List<dev.fancybars.mining.Commissions.Commission> list() {
			java.util.List<dev.fancybars.mining.Commissions.Commission> current = dev.fancybars.mining.Commissions.current();
			return current.isEmpty() ? dev.fancybars.mining.Commissions.sample() : current;
		}

		@Override
		public float[] rect(int screenW, int screenH) {
			FancyBarsConfig config = FancyBarsConfig.get();
			float[] pos = dev.fancybars.mining.Commissions.position(screenW, screenH, list());
			return new float[]{pos[0], pos[1], dev.fancybars.mining.Commissions.WIDTH * config.commissionsScale, dev.fancybars.mining.Commissions.height(list()) * config.commissionsScale};
		}

		@Override
		public void drawPreview(GuiGraphicsExtractor graphics, int screenW, int screenH) {
			FancyBarsConfig config = FancyBarsConfig.get();
			float[] pos = dev.fancybars.mining.Commissions.position(screenW, screenH, list());
			dev.fancybars.mining.Commissions.renderAt(graphics, pos[0], pos[1], config.commissionsScale, list(), enabled() ? 1 : 0.35f);
		}

		@Override
		public void moveTo(float x, float y, int screenW, int screenH) {
			FancyBarsConfig config = FancyBarsConfig.get();
			float[] r = rect(screenW, screenH);
			config.commissionsX = Math.clamp(x, 0, Math.max(0, screenW - r[2])) / screenW;
			config.commissionsY = Math.clamp(y, 0, Math.max(0, screenH - r[3])) / screenH;
		}

		@Override
		public float scale() {
			return FancyBarsConfig.get().commissionsScale;
		}

		@Override
		public void setScale(float scale) {
			FancyBarsConfig.get().commissionsScale = scale;
		}

		@Override
		public boolean enabled() {
			return FancyBarsConfig.get().commissionsEnabled;
		}

		@Override
		public void setEnabled(boolean enabled) {
			FancyBarsConfig.get().commissionsEnabled = enabled;
		}

		@Override
		public void reset() {
			FancyBarsConfig config = FancyBarsConfig.get();
			config.commissionsX = -1;
			config.commissionsY = -1;
			config.commissionsScale = 1;
			FancyBarsConfig.save();
		}

		@Override
		public Component name() {
			return Component.translatable("fancybars.editor.element.commissions");
		}

		@Override
		public boolean snapsToCenter() {
			return false;
		}
	};

	// ---------------------------------------------------------------- Wall Licker

	MovableElement WALL_LICKER = new MovableElement() {
		@Override
		public float[] rect(int screenW, int screenH) {
			FancyBarsConfig config = FancyBarsConfig.get();
			float[] pos = dev.fancybars.fun.WallLicker.position(screenW, screenH);
			int[] size = dev.fancybars.fun.WallLicker.size();
			return new float[]{pos[0], pos[1], size[0] * config.wallLickerScale, size[1] * config.wallLickerScale};
		}

		@Override
		public void drawPreview(GuiGraphicsExtractor graphics, int screenW, int screenH) {
			float[] pos = dev.fancybars.fun.WallLicker.position(screenW, screenH);
			dev.fancybars.fun.WallLicker.renderAt(graphics, pos[0], pos[1], scale(), enabled() ? 1 : 0.35f);
		}

		@Override
		public void moveTo(float x, float y, int screenW, int screenH) {
			FancyBarsConfig config = FancyBarsConfig.get();
			float[] r = rect(screenW, screenH);
			config.wallLickerX = Math.clamp(x, 0, Math.max(0, screenW - r[2])) / screenW;
			config.wallLickerY = Math.clamp(y, 0, Math.max(0, screenH - r[3])) / screenH;
		}

		@Override
		public float scale() {
			return FancyBarsConfig.get().wallLickerScale;
		}

		@Override
		public void setScale(float scale) {
			FancyBarsConfig.get().wallLickerScale = scale;
		}

		@Override
		public boolean enabled() {
			return FancyBarsConfig.get().wallLickerEnabled;
		}

		@Override
		public void setEnabled(boolean enabled) {
			FancyBarsConfig.get().wallLickerEnabled = enabled;
		}

		@Override
		public void reset() {
			FancyBarsConfig config = FancyBarsConfig.get();
			config.wallLickerX = -1;
			config.wallLickerY = -1;
			config.wallLickerScale = 1;
			FancyBarsConfig.save();
		}

		@Override
		public Component name() {
			return Component.translatable("fancybars.editor.element.wallLicker");
		}

		@Override
		public boolean snapsToCenter() {
			return false;
		}
	};

	// ---------------------------------------------------------------- Titles / hotbar

	MovableElement TITLE = new MovableElement() {
		@Override
		public float[] rect(int screenW, int screenH) {
			FancyBarsConfig c = FancyBarsConfig.get();
			float[] b = dev.fancybars.hud.TitlesAndHotbar.titleBox();
			float cx = screenW / 2f + c.titleX;
			float cy = screenH / 2f + c.titleY;
			return new float[]{cx + b[0] * c.titleScale, cy + b[1] * c.titleScale, (b[2] - b[0]) * c.titleScale, (b[3] - b[1]) * c.titleScale};
		}

		@Override
		public void drawPreview(GuiGraphicsExtractor graphics, int screenW, int screenH) {
			dev.fancybars.hud.TitlesAndHotbar.drawTitlePreview(graphics, enabled() ? 0.85f : 0.3f);
		}

		@Override
		public void moveTo(float x, float y, int screenW, int screenH) {
			FancyBarsConfig c = FancyBarsConfig.get();
			float[] b = dev.fancybars.hud.TitlesAndHotbar.titleBox();
			c.titleX = Math.round(x - b[0] * c.titleScale - screenW / 2f);
			c.titleY = Math.round(y - b[1] * c.titleScale - screenH / 2f);
		}

		@Override
		public float scale() {
			return FancyBarsConfig.get().titleScale;
		}

		@Override
		public void setScale(float scale) {
			FancyBarsConfig.get().titleScale = scale;
		}

		@Override
		public boolean enabled() {
			return FancyBarsConfig.get().titlesShown;
		}

		@Override
		public void setEnabled(boolean enabled) {
			FancyBarsConfig.get().titlesShown = enabled;
		}

		@Override
		public void reset() {
			FancyBarsConfig c = FancyBarsConfig.get();
			c.titleX = 0;
			c.titleY = 0;
			c.titleScale = 1;
			FancyBarsConfig.save();
		}

		@Override
		public Component name() {
			return Component.translatable("fancybars.editor.element.titles");
		}
	};

	MovableElement HOTBAR = new MovableElement() {
		@Override
		public float[] rect(int screenW, int screenH) {
			FancyBarsConfig c = FancyBarsConfig.get();
			float w = dev.fancybars.hud.TitlesAndHotbar.HOTBAR_W * c.hotbarScale;
			float h = dev.fancybars.hud.TitlesAndHotbar.HOTBAR_H * c.hotbarScale;
			float cx = screenW / 2f + c.hotbarX;
			float cy = screenH - dev.fancybars.hud.TitlesAndHotbar.HOTBAR_H / 2f + c.hotbarY;
			return new float[]{cx - w / 2, cy - h / 2, w, h};
		}

		@Override
		public void drawPreview(GuiGraphicsExtractor graphics, int screenW, int screenH) {
			dev.fancybars.hud.TitlesAndHotbar.drawHotbarPreview(graphics, 1);
		}

		@Override
		public void moveTo(float x, float y, int screenW, int screenH) {
			FancyBarsConfig c = FancyBarsConfig.get();
			float[] r = rect(screenW, screenH);
			c.hotbarX = Math.round(x + r[2] / 2 - screenW / 2f);
			c.hotbarY = Math.round(y + r[3] / 2 - (screenH - dev.fancybars.hud.TitlesAndHotbar.HOTBAR_H / 2f));
		}

		@Override
		public float scale() {
			return FancyBarsConfig.get().hotbarScale;
		}

		@Override
		public void setScale(float scale) {
			FancyBarsConfig.get().hotbarScale = scale;
		}

		@Override
		public boolean enabled() {
			return true;
		}

		@Override
		public void setEnabled(boolean enabled) {}

		@Override
		public void reset() {
			FancyBarsConfig c = FancyBarsConfig.get();
			c.hotbarX = 0;
			c.hotbarY = 0;
			c.hotbarScale = 1;
			FancyBarsConfig.save();
		}

		@Override
		public Component name() {
			return Component.translatable("fancybars.editor.element.hotbar");
		}
	};

	/** A picture from the screen images folder. */
	static MovableElement image(dev.fancybars.gifs.ScreenImages.Image image) {
		return new MovableElement() {
			@Override
			public float[] rect(int screenW, int screenH) {
				float[] pos = dev.fancybars.gifs.ScreenImages.position(image, screenW, screenH);
				int[] base = image.baseSize();
				float s = image.settings().scale;
				return new float[]{pos[0], pos[1], base[0] * s, base[1] * s};
			}

			@Override
			public void drawPreview(GuiGraphicsExtractor graphics, int screenW, int screenH) {
				dev.fancybars.gifs.ScreenImages.drawOnScreen(graphics, image, enabled() ? 1 : 0.3f);
			}

			@Override
			public void moveTo(float x, float y, int screenW, int screenH) {
				float[] r = rect(screenW, screenH);
				image.settings().x = Math.clamp(x, 0, Math.max(0, screenW - r[2])) / screenW;
				image.settings().y = Math.clamp(y, 0, Math.max(0, screenH - r[3])) / screenH;
			}

			@Override
			public float scale() {
				return image.settings().scale;
			}

			@Override
			public void setScale(float scale) {
				image.settings().scale = scale;
			}

			@Override
			public boolean enabled() {
				return image.settings().enabled;
			}

			@Override
			public void setEnabled(boolean enabled) {
				image.settings().enabled = enabled;
			}

			@Override
			public void reset() {
				image.settings().x = -1;
				image.settings().y = -1;
				image.settings().scale = 1;
				FancyBarsConfig.save();
			}

			@Override
			public Component name() {
				return Component.literal(image.name);
			}

			@Override
			public boolean snapsToCenter() {
				return false;
			}
		};
	}

	/** One element per image, kept so the editor can compare them by identity. */
	java.util.Map<dev.fancybars.gifs.ScreenImages.Image, MovableElement> IMAGE_ELEMENTS = new java.util.WeakHashMap<>();

	/** Every movable element: the fixed ones plus one per screen image. */
	static java.util.List<MovableElement> all() {
		java.util.List<MovableElement> list = new java.util.ArrayList<>(java.util.Arrays.asList(ALL));
		for (dev.fancybars.gifs.ScreenImages.Image image : dev.fancybars.gifs.ScreenImages.all()) {
			if (image.ready) list.add(IMAGE_ELEMENTS.computeIfAbsent(image, MovableElement::image));
		}
		return list;
	}

	// ---------------------------------------------------------------- Spotify

	MovableElement SPOTIFY = new MovableElement() {
		@Override
		public float[] rect(int screenW, int screenH) {
			float[] pos = dev.fancybars.spotify.SpotifyHud.position(screenW, screenH);
			float s = scale();
			return new float[]{pos[0], pos[1], dev.fancybars.spotify.SpotifyHud.WIDTH * s, dev.fancybars.spotify.SpotifyHud.HEIGHT * s};
		}

		@Override
		public void drawPreview(GuiGraphicsExtractor graphics, int screenW, int screenH) {
			float[] pos = dev.fancybars.spotify.SpotifyHud.position(screenW, screenH);
			dev.fancybars.spotify.Spotify.State s = dev.fancybars.spotify.Spotify.state().active ? dev.fancybars.spotify.Spotify.state() : dev.fancybars.spotify.SpotifyHud.sample();
			dev.fancybars.spotify.SpotifyHud.renderAt(graphics, pos[0], pos[1], scale(), s, enabled() ? 1 : 0.35f);
		}

		@Override
		public void moveTo(float x, float y, int screenW, int screenH) {
			FancyBarsConfig c = FancyBarsConfig.get();
			float[] r = rect(screenW, screenH);
			c.spotifyX = Math.clamp(x, 0, Math.max(0, screenW - r[2])) / screenW;
			c.spotifyY = Math.clamp(y, 0, Math.max(0, screenH - r[3])) / screenH;
		}

		@Override
		public float scale() {
			return FancyBarsConfig.get().spotifyScale;
		}

		@Override
		public void setScale(float scale) {
			FancyBarsConfig.get().spotifyScale = scale;
		}

		@Override
		public boolean enabled() {
			return FancyBarsConfig.get().spotifyHud;
		}

		@Override
		public void setEnabled(boolean enabled) {
			FancyBarsConfig.get().spotifyHud = enabled;
		}

		@Override
		public void reset() {
			FancyBarsConfig c = FancyBarsConfig.get();
			c.spotifyX = -1;
			c.spotifyY = -1;
			c.spotifyScale = 1;
			FancyBarsConfig.save();
		}

		@Override
		public Component name() {
			return Component.translatable("fancybars.editor.element.spotify");
		}

		@Override
		public boolean snapsToCenter() {
			return false;
		}
	};

	// ---------------------------------------------------------------- Melody / terminals (Odin)

	MovableElement MELODY = new MovableElement() {
		@Override
		public float[] rect(int screenW, int screenH) {
			float[] p = dev.fancybars.dungeons.MelodyMessage.position(screenW, screenH);
			int[] s = dev.fancybars.dungeons.MelodyMessage.size(true);
			return new float[]{p[0], p[1], s[0] * scale(), s[1] * scale()};
		}

		@Override
		public void drawPreview(GuiGraphicsExtractor graphics, int screenW, int screenH) {
			float[] p = dev.fancybars.dungeons.MelodyMessage.position(screenW, screenH);
			dev.fancybars.dungeons.MelodyMessage.renderAt(graphics, p[0], p[1], scale(), true);
		}

		@Override
		public void moveTo(float x, float y, int screenW, int screenH) {
			FancyBarsConfig c = FancyBarsConfig.get();
			float[] r = rect(screenW, screenH);
			c.melodyX = Math.clamp(x, 0, Math.max(0, screenW - r[2])) / screenW;
			c.melodyY = Math.clamp(y, 0, Math.max(0, screenH - r[3])) / screenH;
		}

		@Override
		public float scale() {
			return FancyBarsConfig.get().melodyScale;
		}

		@Override
		public void setScale(float scale) {
			FancyBarsConfig.get().melodyScale = scale;
		}

		@Override
		public boolean enabled() {
			return FancyBarsConfig.get().melodyGui;
		}

		@Override
		public void setEnabled(boolean enabled) {
			FancyBarsConfig.get().melodyGui = enabled;
		}

		@Override
		public void reset() {
			FancyBarsConfig c = FancyBarsConfig.get();
			c.melodyX = -1;
			c.melodyY = -1;
			c.melodyScale = 1;
			FancyBarsConfig.save();
		}

		@Override
		public Component name() {
			return Component.translatable("fancybars.editor.element.melody");
		}

		@Override
		public boolean snapsToCenter() {
			return false;
		}
	};

	MovableElement TERM_INFO = new MovableElement() {
		@Override
		public float[] rect(int screenW, int screenH) {
			float[] p = dev.fancybars.dungeons.TerminalsStatus.position(screenW, screenH);
			int[] s = dev.fancybars.dungeons.TerminalsStatus.size();
			return new float[]{p[0], p[1], s[0] * scale(), s[1] * scale()};
		}

		@Override
		public void drawPreview(GuiGraphicsExtractor graphics, int screenW, int screenH) {
			float[] p = dev.fancybars.dungeons.TerminalsStatus.position(screenW, screenH);
			dev.fancybars.dungeons.TerminalsStatus.renderAt(graphics, p[0], p[1], scale());
		}

		@Override
		public void moveTo(float x, float y, int screenW, int screenH) {
			FancyBarsConfig c = FancyBarsConfig.get();
			float[] r = rect(screenW, screenH);
			c.termX = Math.clamp(x, 0, Math.max(0, screenW - r[2])) / screenW;
			c.termY = Math.clamp(y, 0, Math.max(0, screenH - r[3])) / screenH;
		}

		@Override
		public float scale() {
			return FancyBarsConfig.get().termScale;
		}

		@Override
		public void setScale(float scale) {
			FancyBarsConfig.get().termScale = scale;
		}

		@Override
		public boolean enabled() {
			return FancyBarsConfig.get().termHud;
		}

		@Override
		public void setEnabled(boolean enabled) {
			FancyBarsConfig.get().termHud = enabled;
		}

		@Override
		public void reset() {
			FancyBarsConfig c = FancyBarsConfig.get();
			c.termX = -1;
			c.termY = -1;
			c.termScale = 1;
			FancyBarsConfig.save();
		}

		@Override
		public Component name() {
			return Component.translatable("fancybars.editor.element.termInfo");
		}

		@Override
		public boolean snapsToCenter() {
			return false;
		}
	};

	MovableElement[] ALL = {HELD_ITEM, SECRETS, ACTION_BAR, RARE_DROPS, INVENTORY, KEYSTROKES, COMMISSIONS, WALL_LICKER, TITLE, HOTBAR, SPOTIFY, MELODY, TERM_INFO};
}
