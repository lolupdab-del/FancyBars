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

	// ---------------------------------------------------------------- Item protection (ported from Skyblocker)

	public boolean itemProtectionEnabled = true;

	public boolean protectionChatNotification = true;

	/** ARGB colour the protection star is tinted with. */
	public int protectionStarColor = 0xFFF0FF00;

	public StarCorner protectionStarCorner = StarCorner.BOTTOM_RIGHT;

	/** SkyBlock item UUIDs of protected items. */
	public java.util.Set<String> protectedItems = new java.util.LinkedHashSet<>();

	/** Display info for protected items (uuid -> name / colour / item id), so they can be listed in the menu. */
	public java.util.Map<String, ProtectedItem> protectedItemInfo = new java.util.LinkedHashMap<>();

	public static class ProtectedItem {
		public String name = "";
		public int color = 0xFFFFFF;
		public String itemId = "minecraft:barrier";
	}

	// ---------------------------------------------------------------- Inventory GIFs (ported from Inventory GIFs by lolupdab)

	public boolean gifsEnabled = true;

	public boolean gifsHideCraftingGrid = true;

	public boolean gifsHideRecipeBook = true;

	// ---------------------------------------------------------------- Dungeon secrets display (from SkyblockAddons)

	public boolean secretsEnabled = true;

	public boolean secretsShowChest = true;

	public boolean secretsShowWord = true;

	/** Remove the "X/Y Secrets" text from the action bar while the display is on. */
	public boolean secretsHideActionBar = true;

	/** Top-left position as a fraction of the screen; negative = default spot. */
	public float secretsX = -1;

	public float secretsY = -1;

	public float secretsScale = 1;

	// ---------------------------------------------------------------- Backpack preview (from Skyblocker)

	public boolean backpackPreviewEnabled = true;

	/** Only show the preview while holding Shift (otherwise hold Shift to see the normal tooltip). */
	public boolean backpackPreviewNeedsShift = false;

	/** Snap things to each other / the screen centre in the editor. */
	public boolean editorSnapping = true;

	/** SHIKIMODE: the pink ShikiClient look and logo instead of SBCLIENT's pink → blue gradient. */
	public boolean shikiMode = false;

	// ---------------------------------------------------------------- Inventory HUD

	public boolean invHudEnabled = true;

	public boolean invHudBackground = true;

	public boolean invHudSlots = true;

	public float invHudX = -1;

	public float invHudY = -1;

	public float invHudScale = 1;

	// ---------------------------------------------------------------- Keystrokes

	public boolean keystrokesEnabled = true;

	/** 0 = mouse only, 1 = WASD + space + mouse, 2 = full keyboard. */
	public int keystrokesMode = 1;

	public boolean keystrokesLeftCps = true;

	public boolean keystrokesRightCps = true;

	public float keystrokesX = -1;

	public float keystrokesY = -1;

	public float keystrokesScale = 1;

	// ---------------------------------------------------------------- Watermark

	public boolean watermarkEnabled = true;

	public int watermarkX = 4;

	public int watermarkY = 4;

	public float watermarkScale = 1;

	// ---------------------------------------------------------------- Rare dungeon drops

	public boolean rareDropsEnabled = true;

	public boolean rareDropsHud = true;

	public boolean rareDropsBackground = true;

	public boolean rareDropsChat = true;

	/** Rare items the player chose not to track. */
	public java.util.Set<String> rareDropsUntracked = new java.util.HashSet<>();

	public float rareDropsX = -1;

	public float rareDropsY = -1;

	public float rareDropsScale = 1;

	// ---------------------------------------------------------------- Action bar

	public boolean actionBarShown = true;

	/** Draw the action bar with Fancy Bars' renderer (font, shadow, background, colour, fade options). */
	public boolean actionBarCustomize = false;

	public int actionBarX = 0;

	public int actionBarY = 0;

	public float actionBarScale = 1;

	public boolean actionBarSmoothFont = true;

	public boolean actionBarShadow = true;

	public boolean actionBarBackground = false;

	/** Background opacity in percent. */
	public int actionBarBackgroundOpacity = 50;

	public boolean actionBarUseCustomColor = false;

	public int actionBarColor = 0xFFFFFFFF;

	public boolean actionBarFade = true;

	public boolean actionBarHideHealth = false;

	public boolean actionBarHideDefense = false;

	public boolean actionBarHideMana = false;

	public boolean actionBarHideSkillXp = false;

	public boolean actionBarHideRiftTime = false;

	public boolean actionBarHideVitality = false;

	// ---------------------------------------------------------------- Held item name (the tooltip shown when switching items)

	public boolean heldItemNameEnabled = true;

	/** Offset from the vanilla position, in GUI pixels. */
	public int heldItemNameX = 0;

	public int heldItemNameY = 0;

	public float heldItemNameScale = 1;

	public static FancyBarsConfig get() {
		return instance;
	}

	public static void load() {
		try (BufferedReader reader = Files.newBufferedReader(FILE)) {
			FancyBarsConfig loaded = FancyBarsMod.GSON.fromJson(reader, FancyBarsConfig.class);
			if (loaded != null) instance = loaded;
			if (instance.intelligenceDisplay == null) instance.intelligenceDisplay = IntelligenceDisplay.ORIGINAL;
			if (instance.barStyle == null) instance.barStyle = BarStyle.CLASSIC;
			if (instance.protectionStarCorner == null) instance.protectionStarCorner = StarCorner.BOTTOM_RIGHT;
			if (instance.protectedItems == null) instance.protectedItems = new java.util.LinkedHashSet<>();
			if (instance.protectedItemInfo == null) instance.protectedItemInfo = new java.util.LinkedHashMap<>();
			if (instance.heldItemNameScale <= 0) instance.heldItemNameScale = 1;
			if (instance.secretsScale <= 0) instance.secretsScale = 1;
			if (instance.actionBarScale <= 0) instance.actionBarScale = 1;
			if (instance.rareDropsScale <= 0) instance.rareDropsScale = 1;
			if (instance.watermarkScale <= 0) instance.watermarkScale = 1;
			if (instance.invHudScale <= 0) instance.invHudScale = 1;
			if (instance.keystrokesScale <= 0) instance.keystrokesScale = 1;
			instance.keystrokesMode = Math.clamp(instance.keystrokesMode, 0, 2);
			if (instance.rareDropsUntracked == null) instance.rareDropsUntracked = new java.util.HashSet<>();
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

	public enum StarCorner {
		TOP_LEFT(false, false),
		TOP_RIGHT(true, false),
		BOTTOM_LEFT(false, true),
		BOTTOM_RIGHT(true, true);

		public final boolean right;
		public final boolean bottom;

		StarCorner(boolean right, boolean bottom) {
			this.right = right;
			this.bottom = bottom;
		}

		public String translationKey() {
			return "fancybars.protection.corner." + name().toLowerCase(java.util.Locale.ROOT);
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
