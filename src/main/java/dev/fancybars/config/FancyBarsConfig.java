package dev.fancybars.config;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;

import dev.fancybars.FancyBarsMod;

/**
 * Simple JSON-backed config. Replaces the "Fancy Bars" section of Skyblocker's YACL config.
 */
public class FancyBarsConfig {
	private static final Path FILE = FancyBarsMod.CONFIG_DIR.resolve("config.json");
	private static FancyBarsConfig instance = new FancyBarsConfig();

	public boolean enableBars = true;

	public boolean enableBarsRift = true;

	public boolean riftHealthHP = false;

	public boolean enableVanillaStyleManaBar = false;

	public boolean enableVanillaStyleManaBarBlinking = true;

	public boolean useHungerBarSprites = false;

	public IntelligenceDisplay intelligenceDisplay = IntelligenceDisplay.ORIGINAL;

	public boolean showEstimatedTilde = true;

	/**
	 * Which textures the bars are drawn with.
	 */
	public BarStyle barStyle = BarStyle.CLASSIC;

	/**
	 * Draws the numbers on the bars with the smooth Inter font instead of the pixel font.
	 */
	public boolean smoothBarText = true;

	/**
	 * @deprecated replaced by {@link #barStyle}; only read to migrate old configs.
	 */
	@Deprecated
	public boolean segmentedBars = false;

	/**
	 * Hides the "-X Mana (Ability)" text from the action bar.
	 */
	public boolean hideMana = false;

	public boolean hasSeenVitalityAtLeastOnce = false;

	public static FancyBarsConfig get() {
		return instance;
	}

	public static void load() {
		try (BufferedReader reader = Files.newBufferedReader(FILE)) {
			FancyBarsConfig loaded = FancyBarsMod.GSON.fromJson(reader, FancyBarsConfig.class);
			if (loaded != null) instance = loaded;
			if (instance.intelligenceDisplay == null) instance.intelligenceDisplay = IntelligenceDisplay.ORIGINAL;
			if (instance.barStyle == null) instance.barStyle = BarStyle.CLASSIC;
			if (instance.segmentedBars) {
				instance.barStyle = BarStyle.SEGMENTED;
				instance.segmentedBars = false;
			}
		} catch (NoSuchFileException _) {
			save();
		} catch (Exception e) {
			FancyBarsMod.LOGGER.error("[FancyBars] Failed to load config, using defaults", e);
		}
	}

	public static void save() {
		try {
			Files.createDirectories(FILE.getParent());
			try (BufferedWriter writer = Files.newBufferedWriter(FILE)) {
				FancyBarsMod.GSON.toJson(instance, writer);
			}
		} catch (Exception e) {
			FancyBarsMod.LOGGER.error("[FancyBars] Failed to save config", e);
		}
	}

	public enum BarStyle {
		CLASSIC,
		SEGMENTED,
		SKYBLOCKADDONS;

		public String translationKey() {
			return "fancybars.style." + name().toLowerCase(java.util.Locale.ROOT);
		}
	}

	public enum IntelligenceDisplay {
		ORIGINAL,
		ACCURATE,
		IN_FRONT;

		public String translationKey() {
			return "fancybars.config.intelligenceDisplay." + name();
		}
	}
}
