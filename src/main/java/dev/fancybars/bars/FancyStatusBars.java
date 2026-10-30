package dev.fancybars.bars;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

import com.google.gson.JsonObject;
import org.jetbrains.annotations.VisibleForTesting;
import org.joml.Matrix3x2fStack;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenPosition;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

import dev.fancybars.FancyBarsMod;
import dev.fancybars.config.FancyBarsConfig;
import dev.fancybars.util.SkyblockLocation;

public class FancyStatusBars {
	private static final Identifier HUD_LAYER = FancyBarsMod.id("fancy_status_bars");
	private static final Path FILE = FancyBarsMod.CONFIG_DIR.resolve("status_bars.json");
	private static final Logger LOGGER = LoggerFactory.getLogger(FancyStatusBars.class);
	public static final FancyStatusBars INSTANCE = new FancyStatusBars();

	public BarPositioner barPositioner = new BarPositioner();
	public Map<StatusBarType, StatusBar> statusBars = new EnumMap<>(StatusBarType.class);
	private boolean updatePositionsNextFrame;

	public boolean isHealthFancyBarEnabled() {
		return isBarEnabled(StatusBarType.HEALTH);
	}

	public boolean isExperienceFancyBarEnabled() {
		return isBarEnabled(StatusBarType.EXPERIENCE);
	}

	public boolean isBarEnabled(StatusBarType type) {
		StatusBar statusBar = statusBars.get(type);
		return statusBar.enabled || statusBar.inMouse;
	}

	/**
	 * Called when vitality is first discovered.
	 */
	public void makeVitalityVisible() {
		statusBars.get(StatusBarType.VITALITY).visible = true;
		updatePositionsNextFrame = true;
	}

	public static void initStatic() {
		INSTANCE.init();
	}

	@SuppressWarnings("deprecation")
	public void init() {
		// All checks happen inside the returned elements, so they're evaluated every frame
		// (on/off SkyBlock, settings changes) no matter how often Fabric applies the replacement.
		Function<HudElement, HudElement> hideIfFancyStatusBarsEnabled = hudElement -> (context, tickCounter) -> {
			if (!shouldProcess()) hudElement.extractRenderState(context, tickCounter);
		};

		// The vanilla XP bar is always hidden while fancy bars are active (even if the fancy XP bar is turned off),
		// so vanilla hearts (when the fancy health bar is off) move down into its space.
		HudElementRegistry.replaceElement(VanillaHudElements.HEALTH_BAR, hudElement -> (context, tickCounter) -> {
			if (!shouldProcess()) {
				hudElement.extractRenderState(context, tickCounter);
			} else if (!isHealthFancyBarEnabled()) {
				Matrix3x2fStack pose = context.pose();
				pose.pushMatrix();
				pose.translate(0, 6);
				hudElement.extractRenderState(context, tickCounter);
				pose.popMatrix();
			}
		});
		HudElementRegistry.replaceElement(VanillaHudElements.EXPERIENCE_LEVEL, hideIfFancyStatusBarsEnabled);
		HudElementRegistry.replaceElement(VanillaHudElements.INFO_BAR, hideIfFancyStatusBarsEnabled);
		HudElementRegistry.replaceElement(VanillaHudElements.ARMOR_BAR, hideIfFancyStatusBarsEnabled);
		HudElementRegistry.replaceElement(VanillaHudElements.MOUNT_HEALTH, hideIfFancyStatusBarsEnabled);
		HudElementRegistry.replaceElement(VanillaHudElements.FOOD_BAR, hideIfFancyStatusBarsEnabled);
		HudElementRegistry.replaceElement(VanillaHudElements.AIR_BAR, hideIfFancyStatusBarsEnabled);

		HudElementRegistry.attachElementAfter(VanillaHudElements.HOTBAR, HUD_LAYER, (context, _) -> {
			if (shouldProcess()) extractRenderState(context, Minecraft.getInstance());
		});

		for (StatusBarType type : StatusBarType.values()) {
			statusBars.put(type, type.newStatusBar());
		}
		statusBars.get(StatusBarType.VITALITY).visible = FancyBarsConfig.get().hasSeenVitalityAtLeastOnce;
		// Fill defaults
		resetBarPositions();
		// Default layout matching Skyblocker's defaults: health + mana on the first row, xp on the second, defense on the right
		int[] counts = new int[2];
		for (StatusBarType type : new StatusBarType[]{StatusBarType.HEALTH, StatusBarType.INTELLIGENCE, StatusBarType.EXPERIENCE}) {
			StatusBar bar = statusBars.get(type);
			bar.gridX = counts[bar.gridY]++;
		}

		CompletableFuture.supplyAsync(FancyStatusBars::loadBarConfig, FancyBarsMod.VIRTUAL_THREAD_EXECUTOR).thenAcceptAsync(object -> {
			if (object != null) {
				for (String s : object.keySet()) {
					StatusBarType type = StatusBarType.from(s);
					if (statusBars.containsKey(type)) {
						try {
							statusBars.get(type).loadFromJson(object.get(s).getAsJsonObject());
						} catch (Exception e) {
							LOGGER.error("[FancyBars] Failed to load {} status bar", s, e);
						}
					} else {
						LOGGER.warn("[FancyBars] Unknown status bar: {}", s);
					}
				}
			}
			placeBarsInPositioner();
			configLoaded = true;
		}, Minecraft.getInstance()).exceptionally(throwable -> {
			LOGGER.error("[FancyBars] Failed reading status bars config", throwable);
			return null;
		});
		ClientLifecycleEvents.CLIENT_STOPPING.register(_ -> saveBarConfig());

		SkyblockLocation.onLocationChange(() -> updatePositionsNextFrame = true);
	}

	private boolean configLoaded = false;

	@VisibleForTesting
	public void placeBarsInPositioner() {
		barPositioner.clear();
		for (BarPositioner.BarAnchor barAnchor : BarPositioner.BarAnchor.allAnchors()) {
			List<StatusBar> barList = statusBars.values().stream().filter(bar -> bar.anchor == barAnchor)
					.sorted(Comparator.<StatusBar>comparingInt(bar -> bar.gridY).thenComparingInt(bar -> bar.gridX)).toList();
			if (barList.isEmpty()) continue;

			int y = -1;
			int rowNum = -1;
			for (StatusBar statusBar : barList) {
				if (statusBar.gridY > y) {
					barPositioner.addRow(barAnchor);
					rowNum++;
					y = statusBar.gridY;
				}
				barPositioner.addBar(barAnchor, rowNum, statusBar);
			}
		}
	}

	/**
	 * Turns a bar on or off. Turning a bar off removes it from its anchor (like the editor's "Hide" option);
	 * turning it back on puts it at the end of its default row.
	 */
	public void setBarEnabled(StatusBar bar, boolean enabled) {
		if (bar.enabled == enabled) return;
		if (!enabled) {
			if (bar.anchor != null) barPositioner.removeBar(bar.anchor, bar.gridY, bar);
			bar.enabled = false;
		} else {
			bar.enabled = true;
			if (bar.anchor == null && bar.width <= 0) {
				// Never placed anywhere: snap to the default anchor
				placeAtDefault(bar);
			} else if (bar.anchor == null && bar.x == 0 && bar.y == 0) {
				placeAtDefault(bar);
			}
		}
		placeBarsInPositioner();
		updatePositions(false);
	}

	/**
	 * Unsnaps a bar from its anchor so it can be freely moved, keeping it exactly where it currently is on screen.
	 */
	public void detach(StatusBar bar) {
		if (bar.anchor == null) return;
		int screenWidth = Minecraft.getInstance().getWindow().getGuiScaledWidth();
		int screenHeight = Minecraft.getInstance().getWindow().getGuiScaledHeight();
		int x = bar.getX();
		int y = bar.getY();
		int w = bar.getWidth();
		barPositioner.removeBar(bar.anchor, bar.gridY, bar); // also clears the anchor
		bar.scale = 1;
		bar.x = (float) x / screenWidth;
		bar.y = (float) y / screenHeight;
		bar.width = (float) w / screenWidth;
		updatePositions(true);
	}

	/**
	 * Puts a bar back into its default spot next to the hotbar.
	 */
	public void snapToDefault(StatusBar bar) {
		if (bar.anchor != null) barPositioner.removeBar(bar.anchor, bar.gridY, bar);
		bar.enabled = true;
		bar.scale = 1;
		placeAtDefault(bar);
		placeBarsInPositioner();
		updatePositions(true);
	}

	/**
	 * Sets the on-screen position and size of a free (unanchored) bar, in GUI pixels.
	 */
	public void setFreeBounds(StatusBar bar, int x, int y, int width) {
		int screenWidth = Minecraft.getInstance().getWindow().getGuiScaledWidth();
		int screenHeight = Minecraft.getInstance().getWindow().getGuiScaledHeight();
		bar.x = (float) x / screenWidth;
		bar.y = (float) y / screenHeight;
		bar.width = (float) width / screenWidth;
		updatePositions(true);
	}

	private void placeAtDefault(StatusBar bar) {
		StatusBarType type = bar.getType();
		bar.anchor = type.getDefaultAnchor();
		bar.gridY = type.getDefaultGridY();
		bar.gridX = Integer.MAX_VALUE; // end of the row; normalised by placeBarsInPositioner
		bar.size = type.getDefaultAnchor().getSizeRule().minSize();
	}

	/**
	 * @return whether the bar can currently be made bigger (delta > 0) or smaller (delta < 0)
	 */
	public boolean canResize(StatusBar bar, int delta) {
		if (!bar.enabled) return false;
		if (bar.anchor == null) {
			int screenWidth = Minecraft.getInstance().getWindow().getGuiScaledWidth();
			int newWidth = bar.getWidth() + delta * 10;
			return delta > 0 ? bar.x + (float) newWidth / screenWidth <= 1.0001f : newWidth >= 30;
		}
		BarPositioner.SizeRule rule = bar.anchor.getSizeRule();
		int newSize = bar.size + delta;
		if (newSize < rule.minSize() || newSize > rule.maxSize()) return false;
		if (rule.isTargetSize()) {
			StatusBar neighbour = getResizeNeighbour(bar);
			if (neighbour == null) return false; // a bar alone in a fixed-width row always fills it
			int neighbourSize = neighbour.size - delta;
			return neighbourSize >= rule.minSize() && neighbourSize <= rule.maxSize();
		}
		return true;
	}

	/**
	 * Grows or shrinks a bar by one step. Bars in the row above the hotbar share a fixed width,
	 * so the neighbouring bar shrinks/grows to compensate.
	 */
	public void resizeBar(StatusBar bar, int delta) {
		if (!canResize(bar, delta)) return;
		if (bar.anchor == null) {
			int screenWidth = Minecraft.getInstance().getWindow().getGuiScaledWidth();
			bar.width = (float) (bar.getWidth() + delta * 10) / screenWidth;
		} else {
			if (bar.anchor.getSizeRule().isTargetSize()) {
				StatusBar neighbour = getResizeNeighbour(bar);
				if (neighbour != null) neighbour.size -= delta;
			}
			bar.size += delta;
		}
		updatePositions(false);
	}

	private @Nullable StatusBar getResizeNeighbour(StatusBar bar) {
		if (bar.anchor == null || bar.gridY >= barPositioner.getRowCount(bar.anchor)) return null;
		LinkedList<StatusBar> row = barPositioner.getRow(bar.anchor, bar.gridY);
		int index = row.indexOf(bar);
		if (index < 0 || row.size() < 2) return null;
		return index + 1 < row.size() ? row.get(index + 1) : row.get(index - 1);
	}

	public void resetBarPositions() {
		statusBars.forEach((type, bar) -> {
			bar.anchor = type.getDefaultAnchor();
			bar.gridY = type.getDefaultGridY();
			bar.size = type.getDefaultAnchor().getSizeRule().minSize();
		});
		placeBarsInPositioner();
	}

	public static @Nullable JsonObject loadBarConfig() {
		try (BufferedReader reader = Files.newBufferedReader(FILE)) {
			return FancyBarsMod.GSON.fromJson(reader, JsonObject.class);
		} catch (NoSuchFileException _) {
			LOGGER.warn("[FancyBars] No status bar config file found, using defaults");
		} catch (Exception e) {
			LOGGER.error("[FancyBars] Failed to load status bars config", e);
		}
		return null;
	}

	public void saveBarConfig() {
		JsonObject output = new JsonObject();
		statusBars.forEach((s, statusBar) -> output.add(s.getSerializedName(), statusBar.toJson()));
		try (BufferedWriter writer = Files.newBufferedWriter(FILE)) {
			FancyBarsMod.GSON.toJson(output, writer);
			LOGGER.info("[FancyBars] Saved status bars config");
		} catch (IOException e) {
			LOGGER.error("[FancyBars] Failed to save status bars config", e);
		}
	}

	public void updatePositions(boolean ignoreVisibility) {
		if (!configLoaded) return;
		final int width = Minecraft.getInstance().getWindow().getGuiScaledWidth();
		final int height = Minecraft.getInstance().getWindow().getGuiScaledHeight();

		// Put these in the corner for the config screen
		int offset = 0;
		for (StatusBar statusBar : statusBars.values()) {
			if (!statusBar.enabled) {
				statusBar.setX(5);
				statusBar.setY(50 + offset);
				statusBar.setWidth(30);
				offset += statusBar.getHeight();
			} else if (statusBar.anchor == null) {
				statusBar.width = Math.clamp(statusBar.width, 30f / width, 1);
				statusBar.x = Math.clamp(statusBar.x, 0, 1 - statusBar.width);
				statusBar.y = Math.clamp(statusBar.y, 0, 1 - (float) statusBar.getHeight() / height);
				statusBar.setX((int) (statusBar.x * width));
				statusBar.setY((int) (statusBar.y * height));
				statusBar.setWidth((int) (statusBar.width * width));
			}
		}

		for (BarPositioner.BarAnchor barAnchor : BarPositioner.BarAnchor.allAnchors()) {
			ScreenPosition anchorPosition = barAnchor.getAnchorPosition(width, height);
			BarPositioner.SizeRule sizeRule = barAnchor.getSizeRule();

			int targetSize = sizeRule.targetSize();
			boolean visibleHealthMove = barAnchor == BarPositioner.BarAnchor.HOTBAR_TOP && !isHealthFancyBarEnabled();
			if (visibleHealthMove) {
				targetSize /= 2;
			}

			if (sizeRule.isTargetSize()) {
				for (int row = 0; row < barPositioner.getRowCount(barAnchor); row++) {
					LinkedList<StatusBar> barRow = barPositioner.getRow(barAnchor, row);
					if (barRow.isEmpty()) continue;

					// FIX SIZES
					int totalSize = 0;
					for (StatusBar statusBar : barRow)
						totalSize += (statusBar.size = Math.clamp(statusBar.size, sizeRule.minSize(), sizeRule.maxSize()));

					whileLoop:
					while (totalSize != targetSize) {
						if (totalSize > targetSize) {
							for (StatusBar statusBar : barRow) {
								if (statusBar.size > sizeRule.minSize()) {
									statusBar.size--;
									totalSize--;
									if (totalSize == targetSize) break whileLoop;
								}
							}
						} else {
							for (StatusBar statusBar : barRow) {
								if (statusBar.size < sizeRule.maxSize()) {
									statusBar.size++;
									totalSize++;
									if (totalSize == targetSize) break whileLoop;
								}
							}
						}
					}

				}
			}

			int row = 0;
			for (int i = 0; i < barPositioner.getRowCount(barAnchor); i++) {
				List<StatusBar> barRow = new ArrayList<>(barPositioner.getRow(barAnchor, i));
				barRow.removeIf(statusBar -> !statusBar.visible && !ignoreVisibility);
				if (barRow.isEmpty()) continue;


				// Update the positions
				float widthPerSize;
				if (sizeRule.isTargetSize()) {
					int size = 0;
					for (StatusBar bar : barRow) size += bar.size;
					widthPerSize = (float) sizeRule.totalWidth() / size;

				}
				else
					widthPerSize = sizeRule.widthPerSize();

				if (visibleHealthMove) widthPerSize /= 2;

				int currSize = 0;
				int rowSize = barRow.size();
				for (int j = 0; j < rowSize; j++) {
					// A bit of a padding
					int offsetX = 0;
					int lessWidth = 0;
					if (!sizeRule.isTargetSize()) {
						offsetX = 1;
						lessWidth = 2;
					} else if (rowSize > 1) { // Technically bars in the middle of 3+ bars will be smaller than the 2 side ones but shh
						if (j == 0) lessWidth = 1;
						else if (j == rowSize - 1) {
							lessWidth = 1;
							offsetX = 1;
						} else {
							lessWidth = 2;
							offsetX = 1;
						}
					}
					StatusBar statusBar = barRow.get(j);
					statusBar.size = Math.clamp(statusBar.size, sizeRule.minSize(), sizeRule.maxSize());

					float x = barAnchor.isRight() ?
							anchorPosition.x() + (visibleHealthMove ? sizeRule.totalWidth() / 2.f : 0) + currSize * widthPerSize :
							anchorPosition.x() - currSize * widthPerSize - statusBar.size * widthPerSize;
					statusBar.setX(Mth.ceil(x) + offsetX);

					int y = barAnchor.isUp() ?
							anchorPosition.y() - (row + 1) * (statusBar.getHeight() + 1) :
							anchorPosition.y() + row * (statusBar.getHeight() + 1);
					statusBar.setY(y);

					statusBar.setWidth(Mth.floor(statusBar.size * widthPerSize) - lessWidth);
					currSize += statusBar.size;
				}
				if (currSize > 0) row++;
			}

		}
	}

	public boolean shouldProcess() {
		return SkyblockLocation.isOnSkyblock() && isEnabled();
	}

	public boolean isEnabled() {
		return FancyBarsConfig.get().enableBars && (!SkyblockLocation.isInTheRift() || FancyBarsConfig.get().enableBarsRift);
	}

	public boolean extractRenderState(GuiGraphicsExtractor graphics, Minecraft client) {
		LocalPlayer player = client.player;
		if (player == null) return false;

		Collection<StatusBar> barCollection = statusBars.values();
		for (StatusBar statusBar : barCollection) {
			if (!statusBar.enabled || !statusBar.visible) continue;
			statusBar.extractBar(graphics);
		}
		for (StatusBar statusBar : barCollection) {
			if (!statusBar.enabled || !statusBar.visible) continue;
			statusBar.extractText(graphics);
		}

		StatusBar defenseBar = statusBars.get(StatusBarType.DEFENSE);
		StatusBar vitalityBar = statusBars.get(StatusBarType.VITALITY);
		if (SkyblockLocation.isInTheRift()) {
			final int div = FancyBarsConfig.get().riftHealthHP ? 1 : 2;
			statusBars.get(StatusBarType.HEALTH).updateValues(Math.round(player.getHealth()) / player.getMaxHealth(), 0, Math.round(player.getHealth()) / div, Math.round(player.getMaxHealth()) / div, null);
			defenseBar.visible = false;
			vitalityBar.visible = false;
		} else {
			StatusBarTracker.Resource health = StatusBarTracker.getHealth();
			statusBars.get(StatusBarType.HEALTH).updateWithResource(health);

			int defense = StatusBarTracker.getDefense();
			defenseBar.visible = true;
			defenseBar.updateValues(defense / (defense + 100.f), 0, defense, null, null);

			StatusBarTracker.EstimatedResource vitality = StatusBarTracker.getVitality();
			vitalityBar.visible = true;
			vitalityBar.updateWithResource(vitality.resource());
		}

		StatusBarTracker.EstimatedResource intelligence = StatusBarTracker.getMana();
		if (FancyBarsConfig.get().intelligenceDisplay == FancyBarsConfig.IntelligenceDisplay.ACCURATE) {
			float totalIntelligence = (float) intelligence.max() + intelligence.overflow();
			statusBars.get(StatusBarType.INTELLIGENCE).updateValues(intelligence.value() / totalIntelligence + intelligence.overflow() / totalIntelligence, intelligence.overflow() / totalIntelligence, intelligence.value(), intelligence.max(), intelligence.overflow());
		} else statusBars.get(StatusBarType.INTELLIGENCE).updateWithResource(intelligence.resource());

		StatusBarTracker.Resource speed = StatusBarTracker.getSpeed();
		statusBars.get(StatusBarType.SPEED).updateWithResource(speed);
		statusBars.get(StatusBarType.EXPERIENCE).updateValues(player.experienceProgress, 0, player.experienceLevel, null, null);
		StatusBarTracker.Resource air = StatusBarTracker.getAir();
		StatusBar airBar = statusBars.get(StatusBarType.AIR);
		airBar.updateWithResource(air);
		if (player.isUnderWater() != airBar.visible) {
			airBar.visible = player.isUnderWater();
			updatePositionsNextFrame = true;
		}
		if (updatePositionsNextFrame) {
			updatePositions(false);
			updatePositionsNextFrame = false;
		}
		return true;
	}
}
