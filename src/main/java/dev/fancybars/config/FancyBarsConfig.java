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

	// ---------------------------------------------------------------- Player

	/** Draw yourself at {@link #playerSize}. */
	public boolean playerSizeEnabled = false;

	/** Deprecated single size (migrated to the three axes). */
	public float playerSize = 1;

	public float playerSizeWidth = 1;

	public float playerSizeHeight = 1;

	public float playerSizeLength = 1;

	/** Show other SBCLIENT users at the sizes in the shared list. */
	public boolean playerSizeShowOthers = true;

	public boolean ownNametagInF5 = false;

	// ---------------------------------------------------------------- Screen images

	public static final class ScreenImage {
		public boolean enabled = true;

		/** Position as a fraction of the screen, -1 = default. */
		public float x = -1;

		public float y = -1;

		public float scale = 1;

		public float opacity = 1;
	}

	/** File name → settings. */
	public java.util.Map<String, ScreenImage> screenImages = new java.util.HashMap<>();

	// ---------------------------------------------------------------- Crosshair

	public boolean crosshairEnabled = false;

	/** Grid width/height (odd). */
	public int crosshairSize = 15;

	/** ARGB per grid cell, row by row (0 = empty). */
	public int[] crosshairPixels = null;

	/** GUI pixels per grid cell. */
	public float crosshairScale = 1;

	/** Also show the crosshair in third person (F5). */
	public boolean crosshairInThirdPerson = false;

	// ---------------------------------------------------------------- Viewmodel

	public boolean viewmodelEnabled = false;

	/** Swing at normal speed even with Haste / Mining Fatigue. */
	public boolean viewmodelIgnoreHaste = false;

	/** No first-person swing animation. */
	public boolean viewmodelNoSwing = false;

	/** Swing animation speed multiplier (1 = normal). */
	public float viewmodelSwingSpeed = 1;

	/** The empty hand uses the held item settings. */
	public boolean viewmodelApplyToHand = false;

	public float viewmodelItemX = 0;

	public float viewmodelItemY = 0;

	public float viewmodelItemZ = 0;

	public float viewmodelItemRotX = 0;

	public float viewmodelItemRotY = 0;

	public float viewmodelItemRotZ = 0;

	public float viewmodelItemScale = 1;

	public float viewmodelArmX = 0;

	public float viewmodelArmY = 0;

	public float viewmodelArmZ = 0;

	public float viewmodelArmRotX = 0;

	public float viewmodelArmRotY = 0;

	public float viewmodelArmRotZ = 0;

	public float viewmodelArmScale = 1;

	// ---------------------------------------------------------------- Titles / hotbar

	public boolean titlesShown = true;

	public int titleX = 0;

	public int titleY = 0;

	public float titleScale = 1;

	public int hotbarX = 0;

	public int hotbarY = 0;

	public float hotbarScale = 1;

	// ---------------------------------------------------------------- Chat filters (from Skytils' spam hider)

	/** Skytils chat tabs: All / Party / Guild / DMs / Co-op buttons in the chat screen. */
	public boolean chatTabsEnabled = true;

	/** Switch your Hypixel chat channel (/chat ...) when picking a tab. */
	public boolean chatTabsSwitchChannel = true;

	/** Pre-fill /pc, /gc, /r or /cc when opening chat on that tab. */
	public boolean chatTabsPrefill = false;

	/** Ids of the chat filters that are on. */
	public java.util.Set<String> chatFilters = new java.util.HashSet<>();

	// ---------------------------------------------------------------- Item customisation (from Skyblocker)

	public boolean itemCustomizationEnabled = true;

	public boolean itemCustomizationButton = true;

	/** Item UUID → item model id it should look like. */
	public java.util.Map<String, String> customItemModels = new java.util.HashMap<>();

	/** Item UUID → custom name ("&" colour / format codes, "&#RRGGBB" hex colours). */
	public java.util.Map<String, String> customItemNames = new java.util.HashMap<>();

	/** Item UUID → enchantment glint forced on (true) or off (false). */
	public java.util.Map<String, Boolean> customGlint = new java.util.HashMap<>();

	/** Item UUID → leather dye colour (RGB). */
	public java.util.Map<String, Integer> customDyeColors = new java.util.HashMap<>();

	public static final class AnimatedDye {
		/** Keyframe colours (RGB), spread evenly over the duration. */
		public java.util.List<Integer> colors = new java.util.ArrayList<>();

		public float duration = 2;

		public float delay = 0;

		public boolean cycleBack = true;
	}

	public java.util.Map<String, AnimatedDye> customAnimatedDyes = new java.util.HashMap<>();

	/** Item UUID → {trim material id, trim pattern id}. */
	public java.util.Map<String, String[]> customArmorTrims = new java.util.HashMap<>();

	/** Item UUID → equipment asset id (what armour it looks like when worn). */
	public java.util.Map<String, String> customArmorModel = new java.util.HashMap<>();

	/** Item UUID → head skin texture hash. */
	public java.util.Map<String, String> customHelmetTextures = new java.util.HashMap<>();

	/** Item UUID → animated head id. */
	public java.util.Map<String, String> customAnimatedHelmetTextures = new java.util.HashMap<>();

	// ---------------------------------------------------------------- Capes

	public boolean capeEnabled = false;

	/** Cape id from {@code Capes.ALL}, "custom" or "none". */
	public String cape = "migrator";

	// ---------------------------------------------------------------- Wall Licker

	public boolean wallLickerEnabled = true;

	public boolean wallLickerSound = true;

	/** Sound volume in percent. */
	public int wallLickerVolume = 100;

	/** All-time licks. */
	public int wallLicks = 0;

	/** Licks this dungeon run (reset when a run starts / ends). */
	public int wallLicksRun = 0;

	public float wallLickerX = -1;

	public float wallLickerY = -1;

	public float wallLickerScale = 1;

	// ---------------------------------------------------------------- Commissions

	public boolean commissionsEnabled = true;

	public float commissionsX = -1;

	public float commissionsY = -1;

	public float commissionsScale = 1;

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
			if (instance.commissionsScale <= 0) instance.commissionsScale = 1;
			if (instance.wallLickerScale <= 0) instance.wallLickerScale = 1;
			if (instance.cape == null) instance.cape = "migrator";
			if (instance.screenImages == null) instance.screenImages = new java.util.HashMap<>();
			if (instance.chatFilters == null) instance.chatFilters = new java.util.HashSet<>();
			if (instance.customItemModels == null) instance.customItemModels = new java.util.HashMap<>();
			if (instance.customItemNames == null) instance.customItemNames = new java.util.HashMap<>();
			if (instance.customGlint == null) instance.customGlint = new java.util.HashMap<>();
			if (instance.customDyeColors == null) instance.customDyeColors = new java.util.HashMap<>();
			if (instance.customAnimatedDyes == null) instance.customAnimatedDyes = new java.util.HashMap<>();
			if (instance.customArmorTrims == null) instance.customArmorTrims = new java.util.HashMap<>();
			if (instance.customArmorModel == null) instance.customArmorModel = new java.util.HashMap<>();
			if (instance.customHelmetTextures == null) instance.customHelmetTextures = new java.util.HashMap<>();
			if (instance.customAnimatedHelmetTextures == null) instance.customAnimatedHelmetTextures = new java.util.HashMap<>();
			if (instance.titleScale <= 0) instance.titleScale = 1;
			if (instance.hotbarScale <= 0) instance.hotbarScale = 1;
			if (instance.crosshairSize < 5 || instance.crosshairSize % 2 == 0) instance.crosshairSize = 15;
			if (instance.crosshairScale <= 0) instance.crosshairScale = 1;
			if (instance.viewmodelItemScale <= 0) instance.viewmodelItemScale = 1;
			if (instance.viewmodelArmScale <= 0) instance.viewmodelArmScale = 1;
			if (instance.viewmodelSwingSpeed <= 0) instance.viewmodelSwingSpeed = 1;
			if (instance.playerSize <= 0) instance.playerSize = 1;
			if (instance.playerSize != 1 && instance.playerSizeWidth == 1 && instance.playerSizeHeight == 1 && instance.playerSizeLength == 1) {
				instance.playerSizeWidth = instance.playerSizeHeight = instance.playerSizeLength = instance.playerSize;
				instance.playerSize = 1;
			}
			if (instance.playerSizeWidth <= 0) instance.playerSizeWidth = 1;
			if (instance.playerSizeHeight <= 0) instance.playerSizeHeight = 1;
			if (instance.playerSizeLength <= 0) instance.playerSizeLength = 1;
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
