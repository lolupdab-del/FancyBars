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

	/** Draw the protection star on top of the item instead of behind it. */
	public boolean protectionStarInFront = false;
	/** Item Rarity Background: rarity-coloured backgrounds behind SkyBlock items. */
	public boolean rarityBackground = true;
	public boolean rarityBackgroundHotbar = true;
	/** 0 square, 1 rounded, 2 outline, 3 gradient, 4 circle. */
	public int rarityBackgroundStyle = 1;
	public float rarityBackgroundOpacity = 0.45f;

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

	public boolean gifsHideRecipeBook = true;

	// ---------------------------------------------------------------- Dungeon secrets display (from SkyblockAddons)

	public boolean secretsEnabled = true;

	public boolean secretsShowChest = true;

	public boolean secretsShowWord = true;

	/** Remove the "X/Y Secrets" text from the action bar while the display is on. */
	public boolean secretsHideActionBar = true;

	/** Action Bar section: always hide the "X/Y Secrets" counter, whether or not the secrets display is on. */
	public boolean actionBarHideSecrets = false;

	/** Top-left position as a fraction of the screen; negative = default spot. */
	public float secretsX = -1;

	public float secretsY = -1;

	public float secretsScale = 1;

	// ---------------------------------------------------------------- Backpack preview (from Skyblocker)

	public boolean backpackPreviewEnabled = true;

	/** Only show the preview while holding Shift (otherwise hold Shift to see the normal tooltip). */
	public boolean backpackPreviewNeedsShift = false;

	// ---------------------------------------------------------------- Appearance (whole mod)

	/** Use your own colours for every menu and HUD instead of night blue / SHIKIMODE pink. */
	/** Use Minecraft's own font for every menu and HUD instead of the smooth Geist font. */
	public boolean minecraftFont = false;

	/** Minecraft-style drop shadow on text when using Minecraft's font. */
	public boolean minecraftFontShadow = true;

	public boolean customColors = false;

	public int themeAccent = 0x7C8CFF;

	public int themeBackground = 0x0E0F12;

	public int themeText = 0xECECF1;

	/** See-through-ness of menu panels and cards (1 = normal). */
	public float panelOpacity = 1;

	/** Corner roundness multiplier (0 = square, 1 = normal, 2 = extra round). */
	public float cornerRoundness = 1;

	/** How dark the world gets behind menus. */
	public float backdropDarkness = 0.4f;

	/** Rainbow accent: the whole theme cycles through colours. */
	public boolean chromaAccent = false;

	/** Rainbow border around menus and HUDs. */
	public boolean chromaBorder = false;

	/** Draw the rainbow with SkyHanni's chroma shader (flows across the screen) instead of one colour at a time. */
	public boolean chromaShader = true;
	/** Width of one rainbow cycle in % of the screen width. */
	public float chromaShaderSize = 30;
	public float chromaShaderSpeed = 6;
	public float chromaShaderSaturation = 0.75f;
	/** 0 Forward + Right, 1 Forward + Left, 2 Backward + Right, 3 Backward + Left. */
	public int chromaShaderDirection = 0;
	/** Draw maxed enchants in item tooltips in chroma. */
	public boolean chromaMaxEnchants = true;
	/** Maxed enchants: 0 Chroma, 1 Custom Chroma, 2 Solid Colour, 3 Off. */
	public int enchantMaxStyle = 0;
	public int enchantMaxColor = 0xFFAA00;
	public int enchantMaxCustom1 = 0xFF5555;
	public int enchantMaxCustom2 = 0xFFAA00;
	public int enchantMaxCustom3 = 0xFFFF55;
	/** Ultimate enchants (any level): same choices, solid pink by default. */
	public int enchantUltStyle = 2;
	public int enchantUltColor = 0xFF55FF;
	public int enchantUltCustom1 = 0xFF55FF;
	public int enchantUltCustom2 = 0xAA00AA;
	public int enchantUltCustom3 = 0x55FFFF;

	/** Armor display (Devonian). */
	public boolean armorEnabled = false;
	public float armorX = -1;
	public float armorY = -1;
	public float armorScale = 1;
	public boolean armorHorizontal = false;
	public boolean armorBackground = true;
	public boolean armorBorder = true;
	public boolean armorBarrier = true;

	/** Pet display (SkyHanni). */
	public boolean petEnabled = false;
	public float petX = -1;
	public float petY = -1;
	public float petScale = 1;
	public boolean petBackground = false;
	public boolean petShowIcon = true;
	public boolean petRarityBackground = true;
	public boolean petXpRing = true;
	public boolean petRingRarity = false;
	public int petRingColor = 0x00FFFF;
	public int petRingUnfilled = 0xC0C0C0;
	/** Rarity background size: 1 fills the inside of the XP ring (SkyHanni's default is 0.96). */
	public float petBackgroundSize = 0.96f;
	/** Fill the space between the rarity background and the XP ring (otherwise it's see-through). */
	public boolean petSeparator = true;
	public int petSeparatorColor = 0x808080;
	/** Bumped when the pet display's defaults change to match SkyHanni. */
	public int petStyleVersion = 0;
	public boolean petShowLevel = true;
	/** Show the ✦ after the name when the pet has a skin. */
	public boolean petShowSkin = true;
	public boolean petShowXp = false;
	public boolean petShowHeldItem = false;

	/** Rainbow speed (1 = about one full cycle every 10 seconds). */
	public float chromaSpeed = 1;

	/** Smooth hover/open animations in menus. */
	public boolean menuAnimations = true;

	public float animationSpeed = 1;

	/** Strength of the soft glow on buttons and switches. */
	public float glowStrength = 1;

	/** Strength of drop shadows under panels. */
	public float shadowStrength = 1;

	/** Seconds before a setting's description pops up. */
	public float tooltipDelay = 0.35f;

	/** Border thickness in pixels for menus and HUDs (0 = no border). */
	public float borderThickness = 0.6f;

	/** Bumped when a redesign changes defaults that saved configs should pick up. */
	public int designVersion = 0;

	/** Use {@link #borderColor} for borders instead of the theme's. */
	public boolean customBorder = false;

	public int borderColor = 0x7C8CFF;

	public float borderOpacity = 0.6f;

	/** Draw the border around HUD elements too (not only menus). */
	public boolean hudBorders = true;

	/** Give text-only HUDs (secrets, melody, terminal info) a themed background box. */
	public boolean textHudBackgrounds = false;

	/** Opacity multiplier for every HUD background (1 = normal). */
	public float hudOpacity = 1;

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

	/** Don't draw the fire on yourself in third person. */
	public boolean hideOwnFireF5 = false;

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

	// ---------------------------------------------------------------- Melody Message (Odin)

	public boolean melodyEnabled = true;

	/** Tell the party "Melody 33%" / "Melody 67%". */
	public boolean melodyProgress = false;

	/** Send your coordinates to party chat when opening melody. */
	public boolean melodySendCoords = false;

	/** Melody progress HUD. */
	public boolean melodyGui = true;

	/** 0 none, 1 class, 2 name, 3 class and name. */
	public int melodyShowPlayer = 0;

	public float melodyX = -1;

	public float melodyY = -1;

	public float melodyScale = 1;

	// ---------------------------------------------------------------- Terminals Status (Odin)

	public boolean termStatusEnabled = true;

	/** Term Info HUD. */
	public boolean termHud = false;

	/** 0 all, 1 terms & levers, 2 terminals, 3 devices, 4 levers. */
	public int termShow = 0;

	/** 0 box, text & beacon, 1 box & text, 2 box, 3 text. */
	public int termStyle = 0;

	public int termColor = 0xFFFF55;

	public boolean termThroughWalls = true;

	public boolean termHideDefault = true;

	public float termX = -1;

	public float termY = -1;

	public float termScale = 1;

	// ---------------------------------------------------------------- Commands

	public boolean calculatorEnabled = true;

	/** Comma separated words that run the calculator, e.g. "ca, c" → /ca 5m/5b. */
	public String calculatorTriggers = "ca, c";

	public static final class CommandAlias {
		public String from = "";

		public String to = "";

		public CommandAlias() {}

		public CommandAlias(String from, String to) {
			this.from = from;
			this.to = to;
		}
	}

	/** Shortcuts: typing /from runs /to (anything typed after it is kept). */
	public java.util.List<CommandAlias> commandAliases = new java.util.ArrayList<>();

	public java.util.List<String> shitterList = new java.util.ArrayList<>();

	/** Kick people on the shitter list when they join a party you're in. */
	public boolean shitterAutoKick = true;

	// ---------------------------------------------------------------- Spotify

	/** Show the song from the Spotify app without logging in (no developer app or Premium needed). */
	public boolean spotifyBrokeMode = false;

	public boolean spotifyEnabled = true;

	public boolean spotifyHud = true;
	public boolean spotifyHudBackground = true;
	public boolean spotifyPanelBackground = true;
	/** Storage Overlay (Skysoft): every ender chest page and backpack in one searchable panel. */
	public boolean storageOverlay = true;
	/** Hide Minecraft's own hearts / hunger / armor / absorption hearts. */
	public boolean hideVanillaHearts = false;
	public boolean hideVanillaHunger = false;
	public boolean hideVanillaArmor = false;
	public boolean hideAbsorptionHearts = false;
	/** Item List (Skysoft): every SkyBlock item beside your inventory, with recipes. */
	public boolean itemListEnabled = true;
	/** How many columns / rows of items (0 = as many as fit). */
	public int itemListColumns = 0;
	public int itemListRows = 0;
	public boolean itemListBackground = false;
	public boolean itemListHideInDungeons = true;
	/** 0 Name A-Z, 1 Name Z-A, 2 Value high-low, 3 Value low-high, 4 Rarity high-low, 5 Rarity low-high. */
	public int itemListSort = 0;
	/** Where you put the list in its Edit GUI (-1 = right of the menu). */
	public float itemListX = -1;
	public float itemListY = -1;
	public int storageOverlayColumns = 3;
	/** Shortest time between opening two pages, in ms (Hypixel kicks you for opening menus too fast). */
	public int storageSwitchDelay = 400;
	/** Draw the Storage Overlay like Minecraft's own chests instead of SBCLIENT's dark theme. */
	public boolean storageVanillaStyle = false;
	/** 0 SBCLIENT, 1 your resource pack, 2 Dark Glass (-1 = not chosen yet: taken from the old Vanilla Style toggle). */
	public int storageStyle = -1;
	/** Time left in the song on the Now Playing HUD: 0 off, 1 bottom left, 2 bottom right. */
	public int spotifyHudTime = 0;

	/** Mini player next to your inventory. */
	public boolean spotifyInventory = true;

	/** Where you dragged the inventory player, relative to the inventory's top-left corner. */
	public boolean spotifyPanelMoved = false;
	public int spotifyPanelDX = 0;
	public int spotifyPanelDY = 0;

	/** Item value tooltips (ported from Skyblocker). */
	public boolean itemValueEnabled = true;
	public boolean itemValueEstimated = true;
	public boolean itemValueLowestBin = true;
	public boolean itemValueAvgBin = true;
	public boolean itemValueBazaar = true;
	public boolean itemValueNpc = true;

	/** Your own Spotify app's Client ID (developer.spotify.com). */
	public String spotifyClientId = "";

	public float spotifyX = -1;

	public float spotifyY = -1;

	public float spotifyScale = 1;

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

	/** Swing without the hand moving around (rotation only). */
	public boolean viewmodelSwingInPlace = false;

	/** Swing in Place only when holding an item; the empty hand swings normally. */
	public boolean viewmodelSwingInPlaceItemsOnly = false;

	/** No re-equip animation (no dip when the held item changes). */
	public boolean viewmodelNoReequip = false;

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

	public boolean bossBarShown = true;

	public int bossBarX = 0;

	public int bossBarY = 0;

	public float bossBarScale = 1;

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

	/** Custom cape: 0 = the PNG is a full cape texture, 1 = place the picture on the cape (editor). */
	public int capeCustomMode = 1;

	/** Picture offset on the cape, -1..1 (half a panel each way). */
	public float capeImageX = 0;

	public float capeImageY = 0;

	/** Picture size; 1 = fits the cape exactly. */
	public float capeImageScale = 1;

	/** 0 / 90 / 180 / 270. */
	public int capeImageRotation = 0;

	public boolean capeImageFlip = false;

	public boolean capeImageInside = false;

	public boolean capeImageSmooth = true;

	public int capeBackground = 0x101014;

	// ---------------------------------------------------------------- Wall Licker

	public boolean wallLickerEnabled = true;

	public boolean wallLickerSound = true;

	/** Sound volume in percent. */
	public int wallLickerVolume = 100;

	/** All-time licks. */
	public int wallLicks = 0;

	/** Licks this dungeon run (reset when a run starts / ends). */
	public int wallLicksRun = 0;

	// ---------------------------------------------------------------- FPS / TPS display

	/** Keep SBCLIENT's HUDs on screen while chat or an inventory / chest is open. */
	public boolean hudInScreens = false;

	/** Keep SBCLIENT's HUDs on screen behind every screen (chat, inventories, /sbc, other mods). */
	public boolean hudAlways = true;

	/** Keep screen images visible while chat or an inventory / chest is open. */
	public boolean screenImagesInScreens = false;

	/** Show how many seconds were lost to server lag at the end of every dungeon run. */
	public boolean lagReportEnabled = true;

	/** Also send it to party chat automatically. */
	public boolean lagReportParty = true;

	/** Use your own wording for the lag report ({lost} = seconds lost, {tps} = average TPS). */
	public boolean lagReportCustom = false;

	public String lagReportFormat = "Server shit itself for {lost}s (avg {tps} TPS)";

	public boolean perfEnabled = false;

	public boolean perfShowFps = true;

	public boolean perfShowTps = true;

	/** Colour the TPS green / yellow / red by how laggy the server is. */
	public boolean perfColorTps = true;

	public boolean perfBackground = true;

	public int perfBackgroundColor = 0x0E0F12;

	public float perfBackgroundOpacity = 0.88f;

	public int perfTextColor = 0xECECF1;

	public float perfX = -1;

	public float perfY = -1;

	public float perfScale = 1;

	/** Don't count licks while holding the Dungeonbreaker. */
	public boolean wallLickerIgnoreDungeonbreaker = true;

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
			if (instance.perfScale <= 0) instance.perfScale = 1;
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
			if (instance.bossBarScale <= 0) instance.bossBarScale = 1;
			if (instance.crosshairSize < 5 || instance.crosshairSize % 2 == 0) instance.crosshairSize = 15;
			if (instance.crosshairScale <= 0) instance.crosshairScale = 1;
			if (instance.spotifyScale <= 0) instance.spotifyScale = 1;
			if (instance.calculatorTriggers == null) instance.calculatorTriggers = "ca, c";
			if (instance.melodyScale <= 0) instance.melodyScale = 1;
			if (instance.panelOpacity <= 0) instance.panelOpacity = 1;
			if (instance.storageStyle < 0) instance.storageStyle = instance.storageVanillaStyle ? 1 : 0;
			if (instance.petStyleVersion < 1) {
				// match SkyHanni's pet display out of the box
				instance.petBackground = false;
				instance.petShowXp = false;
				instance.petShowHeldItem = false;
				instance.petRingRarity = false;
				instance.petRingColor = 0x00FFFF;
				instance.petRingUnfilled = 0xC0C0C0;
				instance.petStyleVersion = 1;
			}
			if (instance.designVersion < 2) {
				// Linear redesign: hairline borders
				instance.borderThickness = 0.6f;
				instance.designVersion = 2;
			}
			if (instance.chromaSpeed <= 0) instance.chromaSpeed = 1;
			if (instance.animationSpeed <= 0) instance.animationSpeed = 1;
			if (instance.cornerRoundness < 0) instance.cornerRoundness = 1;
			if (instance.termScale <= 0) instance.termScale = 1;
			if (instance.commandAliases == null) instance.commandAliases = new java.util.ArrayList<>();
			if (instance.shitterList == null) instance.shitterList = new java.util.ArrayList<>();
			if (instance.spotifyClientId == null) instance.spotifyClientId = "";
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
