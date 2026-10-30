package dev.fancybars.bars;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.Supplier;

import com.mojang.blaze3d.platform.cursor.CursorTypes;
import org.jspecify.annotations.Nullable;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;

import dev.fancybars.FancyBarsMod;
import dev.fancybars.config.FancyBarsConfig;
import dev.fancybars.protection.ItemProtection;
import dev.fancybars.util.EnumUtils;
import dev.fancybars.util.gui.ModernButton;
import dev.fancybars.util.gui.ModernToggle;
import dev.fancybars.util.gui.SearchField;
import dev.fancybars.util.gui.Theme;

/**
 * The Fancy Bars config menu: title box + category sidebar on the left, search bar and navigation on top,
 * and a scrolling list of cards on the right. Everything is drawn with smooth textures and the Inter font.
 */
public class BarsMenuScreen extends Screen {
	private static final int SIDEBAR_WIDTH = 112;
	private static final int GAP = 5;
	private static final int TOP_HEIGHT = 30;
	private static final int CONTENT_PADDING = 8;
	private static final int ITEM_GAP = 6;
	private static final int SWATCH = 12;

	private static Category category = Category.HOME;
	private static String search = "";

	private final @Nullable Screen parent;
	private final FancyStatusBars bars = FancyStatusBars.INSTANCE;
	private final FancyBarsConfig config = FancyBarsConfig.get();

	private int x0;
	private int y0;
	private int totalWidth;
	private int totalHeight;
	private int contentX;
	private int contentY;
	private int contentW;
	private int contentH;

	private final List<Item> items = new ArrayList<>();
	private final List<AbstractWidget> contentWidgets = new ArrayList<>();
	private float scroll = 0;
	private float scrollTarget = 0;
	private int contentHeight = 0;
	private long lastFrame = Util.getMillis();
	private long openedAt = -1;
	private boolean rebuildNextTick = false;
	private boolean focusSearch = false;

	// Themed hover tooltips (descriptions only show when you hover something)
	private final java.util.Map<AbstractWidget, Component> widgetTips = new java.util.IdentityHashMap<>();
	private @Nullable Component pendingTip;
	private @Nullable Object tipOwner;
	private long tipSince;
	private int tipMouseX;
	private int tipMouseY;

	private void tip(Object owner, Component text) {
		if (pendingTip == null) {
			pendingTip = text;
			if (owner != tipOwner) {
				tipOwner = owner;
				tipSince = Util.getMillis();
			}
		}
	}

	private <W extends AbstractWidget> W tipFor(W widget, Component text) {
		widgetTips.put(widget, text);
		return widget;
	}

	public BarsMenuScreen(@Nullable Screen parent) {
		super(Component.translatable("fancybars.menu.title"));
		this.parent = parent;
	}

	public static BarsMenuScreen openAt(@Nullable Screen parent, Category start) {
		category = start;
		search = "";
		return new BarsMenuScreen(parent);
	}

	public @Nullable Screen getParent() {
		return parent;
	}

	// ================================================================ Layout

	@Override
	protected void init() {
		super.init();
		if (openedAt < 0) openedAt = Util.getMillis();
		bars.updatePositions(false);
		widgetTips.clear();
		dev.fancybars.gifs.GifStore.ensureLoaded();
		ItemProtection.refreshFromInventory();

		totalWidth = Math.min(width - 20, 480);
		totalHeight = Math.min(height - 16, 300);
		x0 = (width - totalWidth) / 2;
		y0 = (height - totalHeight) / 2;

		int rightX = x0 + SIDEBAR_WIDTH + GAP;
		int rightW = totalWidth - SIDEBAR_WIDTH - GAP;
		contentX = rightX + CONTENT_PADDING;
		contentY = y0 + TOP_HEIGHT + GAP + CONTENT_PADDING;
		contentW = rightW - CONTENT_PADDING * 2;
		contentH = totalHeight - TOP_HEIGHT - GAP - CONTENT_PADDING * 2;

		// Search
		int searchW = Math.min(180, rightW / 2);
		SearchField searchField = new SearchField(searchW, 18, search, Component.translatable("fancybars.menu.search"), text -> {
			search = text;
			scrollTarget = scroll = 0;
			focusSearch = true;
			rebuildNextTick = true;
		});
		searchField.setPosition(rightX + 6, y0 + 6);
		addRenderableWidget(searchField);
		if (focusSearch) {
			setFocused(searchField);
			focusSearch = false;
		}

		// Navigation: previous / next category, close
		int navX = rightX + rightW - 6 - 66;
		ModernButton prev = new ModernButton(20, 18, Component.literal("‹"), () -> switchCategory(Category.values()[(category.ordinal() + Category.values().length - 1) % Category.values().length]));
		ModernButton next = new ModernButton(20, 18, Component.literal("›"), () -> switchCategory(EnumUtils.cycle(category)));
		ModernButton close = new ModernButton(20, 18, Component.literal("✕"), this::onClose);
		tipFor(prev, Component.translatable("fancybars.menu.previous"));
		tipFor(next, Component.translatable("fancybars.menu.next"));
		tipFor(close, Component.translatable("gui.done"));
		prev.setPosition(navX, y0 + 6);
		next.setPosition(navX + 22, y0 + 6);
		close.setPosition(navX + 46, y0 + 6);
		addRenderableWidget(prev);
		addRenderableWidget(next);
		addRenderableWidget(close);

		// Sidebar categories
		int sy = y0 + TOP_HEIGHT + GAP + 8;
		int available = totalHeight - TOP_HEIGHT - GAP - 8 - 22;
		int step = Math.clamp(available / Category.values().length, 16, 23);
		int itemH = Math.min(20, step - 2);
		for (Category c : Category.values()) {
			SidebarItem item = new SidebarItem(c, SIDEBAR_WIDTH - 16, itemH);
			item.setPosition(x0 + 8, sy);
			addRenderableWidget(item);
			sy += step;
		}

		// Content
		buildItems();
		contentWidgets.clear();
		for (Item item : items) {
			for (AbstractWidget widget : item.widgets) {
				contentWidgets.add(widget);
				addWidget(widget);
			}
		}
		layoutContent();
	}

	private void switchCategory(Category c) {
		category = c;
		search = "";
		scroll = scrollTarget = 0;
		rebuildNextTick = true;
	}

	private void layoutContent() {
		int y = contentY - Math.round(scroll);
		int total = 0;
		for (Item item : items) {
			item.y = y;
			item.layout(contentX, y, contentW - 6);
			y += item.height + ITEM_GAP;
			total += item.height + ITEM_GAP;
		}
		contentHeight = Math.max(0, total - ITEM_GAP);
		for (AbstractWidget widget : contentWidgets) {
			widget.visible = widget.getBottom() > contentY && widget.getY() < contentY + contentH;
		}
	}

	private float maxScroll() {
		return Math.max(0, contentHeight - contentH);
	}

	// ================================================================ Content

	private void buildItems() {
		items.clear();
		String query = search.trim().toLowerCase(Locale.ROOT);
		if (!query.isEmpty()) {
			List<Item> matches = new ArrayList<>();
			for (Category c : Category.values()) {
				for (Item item : itemsFor(c)) {
					if (item.searchable && item.searchText.toLowerCase(Locale.ROOT).contains(query)) matches.add(item);
				}
			}
			items.add(new HeaderItem(Component.translatable("fancybars.menu.results", search), matches.isEmpty() ? Component.translatable("fancybars.menu.noResults") : null));
			items.addAll(matches);
		} else {
			items.addAll(itemsFor(category));
		}
	}

	private List<Item> itemsFor(Category c) {
		List<Item> list = new ArrayList<>();
		switch (c) {
			case HOME -> {
				list.add(new ActionRowItem());
				list.add(new InfoCardItem(Component.translatable("fancybars.home.title"), List.of(
						Component.translatable("fancybars.home.line1"),
						Component.translatable("fancybars.home.line2"),
						Component.translatable("fancybars.home.line3"),
						Component.translatable("fancybars.home.line4")), true));
				list.add(new InfoCardItem(Component.translatable("fancybars.home.changesTitle"), List.of(
						Component.translatable("fancybars.home.change1"),
						Component.translatable("fancybars.home.change2"),
						Component.translatable("fancybars.home.change3")), false));
			}
			case BARS -> {
				list.add(new HeaderItem(Component.translatable("fancybars.category.bars"), null));
				for (StatusBarType type : StatusBarType.values()) {
					StatusBar bar = bars.statusBars.get(type);
					if (bar != null) list.add(new BarItem(bar));
				}
			}
			case STYLE -> {
				list.add(new HeaderItem(Component.translatable("fancybars.category.style"), null));
				for (FancyBarsConfig.BarStyle style : FancyBarsConfig.BarStyle.values()) list.add(new StyleItem(style));
				list.add(new ToggleItem("smoothBarText", () -> config.smoothBarText, value -> config.smoothBarText = value));
			}
			case ITEMS -> {
				list.add(new HeaderItem(Component.translatable("fancybars.category.protection"), null));
				list.add(new ToggleItem("itemProtectionEnabled", () -> config.itemProtectionEnabled, value -> config.itemProtectionEnabled = value));
				list.add(new ToggleItem("protectionChatNotification", () -> config.protectionChatNotification, value -> config.protectionChatNotification = value));
				list.add(new StarItem());
				list.add(new ProtectedItemsItem());
				for (String uuid : new ArrayList<>(config.protectedItems)) list.add(new ProtectedRowItem(uuid));
				list.add(new HeaderItem(Component.translatable("fancybars.category.itemCustomization"), null));
				list.add(new ToggleItem("itemCustomizationEnabled", () -> config.itemCustomizationEnabled, value -> config.itemCustomizationEnabled = value));
				list.add(new ToggleItem("itemCustomizationButton", () -> config.itemCustomizationButton, value -> config.itemCustomizationButton = value));
				list.add(new ButtonRowItem(
						new ModernButton(10, 20, Component.translatable("fancybars.items.open"), ModernButton.Style.PRIMARY, () -> minecraft.gui.setScreen(new dev.fancybars.items.ItemChangerScreen(BarsMenuScreen.this))),
						new ModernButton(10, 20, Component.translatable("fancybars.items.resetAll"), () -> minecraft.gui.setScreen(new ConfirmPopup(BarsMenuScreen.this,
								Component.translatable("fancybars.items.resetAll.title"),
								Component.translatable("fancybars.items.resetAll.message"),
								() -> {
									config.customItemModels.clear();
									config.customItemNames.clear();
									config.customGlint.clear();
									config.customDyeColors.clear();
									config.customAnimatedDyes.clear();
									config.customArmorTrims.clear();
									config.customArmorModel.clear();
									config.customHelmetTextures.clear();
									config.customAnimatedHelmetTextures.clear();
									dev.fancybars.items.Customization.clearCaches();
									FancyBarsConfig.save();
								})))));
				list.add(new HeaderItem(Component.translatable("fancybars.category.backpackPreview"), null));
				list.add(new ToggleItem("backpackPreviewEnabled", () -> config.backpackPreviewEnabled, value -> config.backpackPreviewEnabled = value));
				list.add(new ToggleItem("backpackPreviewNeedsShift", () -> config.backpackPreviewNeedsShift, value -> config.backpackPreviewNeedsShift = value));
				list.add(new HeaderItem(Component.translatable("fancybars.category.heldItem"), null));
				list.add(new ToggleItem("heldItemNameEnabled", () -> config.heldItemNameEnabled, value -> config.heldItemNameEnabled = value));
				list.add(new HeldItemActionsItem());
			}
			case GIFS -> {
				list.add(new HeaderItem(Component.translatable("fancybars.category.gifs"), null));
				list.add(new ToggleItem("gifsEnabled", () -> config.gifsEnabled, value -> config.gifsEnabled = value));
				list.add(new ToggleItem("gifsHideCraftingGrid", () -> config.gifsHideCraftingGrid, value -> config.gifsHideCraftingGrid = value));
				list.add(new ToggleItem("gifsHideRecipeBook", () -> config.gifsHideRecipeBook, value -> config.gifsHideRecipeBook = value));
				list.add(new GifActionsItem());
				List<dev.fancybars.gifs.GifStore.Gif> gifs = dev.fancybars.gifs.GifStore.all();
				if (gifs.isEmpty()) {
					list.add(new InfoCardItem(Component.translatable("fancybars.gifs.emptyTitle"), List.of(
							Component.translatable("fancybars.gifs.empty1"),
							Component.translatable("fancybars.gifs.empty2"),
							Component.translatable("fancybars.gifs.empty3")), false));
				} else {
					for (dev.fancybars.gifs.GifStore.Gif gif : gifs) list.add(new GifItem(gif));
				}
				list.add(new HeaderItem(Component.translatable("fancybars.category.screenImages"), null));
				list.add(new ButtonRowItem(
						new ModernButton(10, 20, Component.translatable("fancybars.heldItem.move"), ModernButton.Style.PRIMARY, () -> minecraft.gui.setScreen(new BarEditorScreen(BarsMenuScreen.this))),
						new ModernButton(10, 20, Component.translatable("fancybars.gifs.openFolder"), dev.fancybars.gifs.ScreenImages::openFolder),
						new ModernButton(10, 20, Component.translatable("fancybars.gifs.reload"), () -> {
							dev.fancybars.gifs.ScreenImages.reload();
							rebuildNextTick = true;
						})));
				List<dev.fancybars.gifs.ScreenImages.Image> images = dev.fancybars.gifs.ScreenImages.all();
				if (images.isEmpty()) {
					list.add(new InfoCardItem(Component.translatable("fancybars.screenImages.emptyTitle"), List.of(
							Component.translatable("fancybars.screenImages.empty1"),
							Component.translatable("fancybars.screenImages.empty2")), false));
				} else {
					for (dev.fancybars.gifs.ScreenImages.Image image : images) list.add(new ScreenImageItem(image));
				}
			}
			case DUNGEONS -> {
				list.add(new HeaderItem(Component.translatable("fancybars.category.secrets"), null));
				list.add(new SecretsPreviewItem());
				list.add(new ToggleItem("secretsEnabled", () -> config.secretsEnabled, value -> config.secretsEnabled = value));
				list.add(new ToggleItem("secretsShowChest", () -> config.secretsShowChest, value -> config.secretsShowChest = value));
				list.add(new ToggleItem("secretsShowWord", () -> config.secretsShowWord, value -> config.secretsShowWord = value));
				list.add(new ToggleItem("secretsHideActionBar", () -> config.secretsHideActionBar, value -> config.secretsHideActionBar = value));
				list.add(new ButtonRowItem(
						new ModernButton(10, 20, Component.translatable("fancybars.heldItem.move"), ModernButton.Style.PRIMARY, () -> minecraft.gui.setScreen(new BarEditorScreen(BarsMenuScreen.this))),
						new ModernButton(10, 20, Component.translatable("fancybars.heldItem.reset"), () -> MovableElement.SECRETS.reset())));
				list.add(new HeaderItem(Component.translatable("fancybars.category.rareDrops"), null));
				list.add(new RareDropsPreviewItem());
				list.add(new ToggleItem("rareDropsEnabled", () -> config.rareDropsEnabled, value -> config.rareDropsEnabled = value));
				list.add(new ToggleItem("rareDropsHud", () -> config.rareDropsHud, value -> config.rareDropsHud = value));
				list.add(new ToggleItem("rareDropsBackground", () -> config.rareDropsBackground, value -> config.rareDropsBackground = value));
				list.add(new ToggleItem("rareDropsChat", () -> config.rareDropsChat, value -> config.rareDropsChat = value));
				list.add(new ButtonRowItem(
						new ModernButton(10, 20, Component.translatable("fancybars.heldItem.move"), ModernButton.Style.PRIMARY, () -> minecraft.gui.setScreen(new BarEditorScreen(BarsMenuScreen.this))),
						new ModernButton(10, 20, Component.translatable("fancybars.heldItem.reset"), () -> MovableElement.RARE_DROPS.reset()),
						new ModernButton(10, 20, Component.translatable("fancybars.rareDrops.resetCounts"), () -> minecraft.gui.setScreen(new ConfirmPopup(BarsMenuScreen.this,
								Component.translatable("fancybars.rareDrops.resetCounts.title"),
								Component.translatable("fancybars.rareDrops.resetCounts.message"),
								() -> {
									dev.fancybars.dungeons.RareDropTracker.reset();
									rebuildNextTick = true;
								})))));
				List<dev.fancybars.dungeons.RareDropTracker.Drop> history = dev.fancybars.dungeons.RareDropTracker.data().history;
				if (!history.isEmpty()) {
					List<Component> recent = new ArrayList<>();
					for (int i = history.size() - 1; i >= Math.max(0, history.size() - 5); i--) {
						dev.fancybars.dungeons.RareDropTracker.Drop drop = history.get(i);
						recent.add(Component.translatable("fancybars.rareDrops.recentLine",
								Component.literal(dev.fancybars.dungeons.RareDropTracker.color(drop.item) + drop.item),
								drop.floor.isEmpty() ? "?" : drop.floor, drop.runs,
								new java.text.SimpleDateFormat("MMM d").format(new java.util.Date(drop.time))));
					}
					list.add(new InfoCardItem(Component.translatable("fancybars.rareDrops.recent"), recent, false));
				}
				list.add(new HeaderItem(Component.translatable("fancybars.rareDrops.tracked"), null));
				for (dev.fancybars.dungeons.RareDropTracker.RareItem item : dev.fancybars.dungeons.RareDropTracker.ITEMS) list.add(new RareDropRowItem(item));
			}
			case ACTION_BAR -> {
				list.add(new HeaderItem(Component.translatable("fancybars.category.actionBar"), null));
				list.add(new ActionBarPreviewItem());
				list.add(new ToggleItem("actionBarShown", () -> config.actionBarShown, value -> config.actionBarShown = value));
				list.add(new ButtonRowItem(
						new ModernButton(10, 20, Component.translatable("fancybars.heldItem.move"), ModernButton.Style.PRIMARY, () -> minecraft.gui.setScreen(new BarEditorScreen(BarsMenuScreen.this))),
						new ModernButton(10, 20, Component.translatable("fancybars.heldItem.reset"), () -> MovableElement.ACTION_BAR.reset())));
				list.add(new HeaderItem(Component.translatable("fancybars.actionBar.parts"), null));
				list.add(new ToggleItem("actionBarHideHealth", () -> config.actionBarHideHealth, value -> config.actionBarHideHealth = value));
				list.add(new ToggleItem("actionBarHideDefense", () -> config.actionBarHideDefense, value -> config.actionBarHideDefense = value));
				list.add(new ToggleItem("actionBarHideMana", () -> config.actionBarHideMana, value -> config.actionBarHideMana = value));
				list.add(new ToggleItem("hideMana", () -> config.hideMana, value -> config.hideMana = value));
				list.add(new ToggleItem("actionBarHideSkillXp", () -> config.actionBarHideSkillXp, value -> config.actionBarHideSkillXp = value));
				list.add(new ToggleItem("secretsHideActionBar", () -> config.secretsHideActionBar, value -> config.secretsHideActionBar = value));
				list.add(new ToggleItem("actionBarHideVitality", () -> config.actionBarHideVitality, value -> config.actionBarHideVitality = value));
				list.add(new ToggleItem("actionBarHideRiftTime", () -> config.actionBarHideRiftTime, value -> config.actionBarHideRiftTime = value));
				list.add(new HeaderItem(Component.translatable("fancybars.actionBar.look"), null));
				list.add(new ToggleItem("actionBarCustomize", () -> config.actionBarCustomize, value -> config.actionBarCustomize = value));
				list.add(new ToggleItem("actionBarSmoothFont", () -> config.actionBarSmoothFont, value -> config.actionBarSmoothFont = value));
				list.add(new ToggleItem("actionBarShadow", () -> config.actionBarShadow, value -> config.actionBarShadow = value));
				list.add(new ToggleItem("actionBarFade", () -> config.actionBarFade, value -> config.actionBarFade = value));
				list.add(new ToggleItem("actionBarBackground", () -> config.actionBarBackground, value -> config.actionBarBackground = value));
				list.add(new ValueCycleItem("actionBarBackgroundOpacity", new int[]{25, 50, 75, 100}, () -> config.actionBarBackgroundOpacity,
						value -> config.actionBarBackgroundOpacity = value, value -> Component.literal(value + "%")));
				list.add(new ToggleItem("actionBarUseCustomColor", () -> config.actionBarUseCustomColor, value -> config.actionBarUseCustomColor = value));
				list.add(new SwatchItem("actionBarColor", () -> new Color(config.actionBarColor, false), color -> config.actionBarColor = color.getRGB() | 0xFF000000));
			}
			case HUD -> {
				list.add(new HeaderItem(Component.translatable("fancybars.category.keystrokes"), null));
				list.add(new ToggleItem("keystrokesEnabled", () -> config.keystrokesEnabled, value -> config.keystrokesEnabled = value));
				list.add(new ValueCycleItem("keystrokesMode", new int[]{0, 1, 2}, () -> config.keystrokesMode,
						value -> config.keystrokesMode = value, dev.fancybars.hud.Keystrokes::modeName));
				list.add(new ToggleItem("keystrokesLeftCps", () -> config.keystrokesLeftCps, value -> config.keystrokesLeftCps = value));
				list.add(new ToggleItem("keystrokesRightCps", () -> config.keystrokesRightCps, value -> config.keystrokesRightCps = value));
				list.add(new ButtonRowItem(
						new ModernButton(10, 20, Component.translatable("fancybars.heldItem.move"), ModernButton.Style.PRIMARY, () -> minecraft.gui.setScreen(new BarEditorScreen(BarsMenuScreen.this))),
						new ModernButton(10, 20, Component.translatable("fancybars.heldItem.reset"), () -> MovableElement.KEYSTROKES.reset())));
				list.add(new HeaderItem(Component.translatable("fancybars.category.crosshair"), null));
				list.add(new ToggleItem("crosshairEnabled", () -> config.crosshairEnabled, value -> config.crosshairEnabled = value));
				list.add(new ToggleItem("crosshairInThirdPerson", () -> config.crosshairInThirdPerson, value -> config.crosshairInThirdPerson = value));
				list.add(new ButtonRowItem(
						new ModernButton(10, 20, Component.translatable("fancybars.crosshair.edit"), ModernButton.Style.PRIMARY, () -> minecraft.gui.setScreen(new dev.fancybars.crosshair.CrosshairEditorScreen(BarsMenuScreen.this)))));
				list.add(new HeaderItem(Component.translatable("fancybars.category.titles"), null));
				list.add(new ToggleItem("titlesShown", () -> config.titlesShown, value -> config.titlesShown = value));
				list.add(new ButtonRowItem(
						new ModernButton(10, 20, Component.translatable("fancybars.heldItem.move"), ModernButton.Style.PRIMARY, () -> minecraft.gui.setScreen(new BarEditorScreen(BarsMenuScreen.this))),
						new ModernButton(10, 20, Component.translatable("fancybars.heldItem.reset"), () -> MovableElement.TITLE.reset())));
				list.add(new HeaderItem(Component.translatable("fancybars.category.hotbar"), null));
				list.add(new ButtonRowItem(
						new ModernButton(10, 20, Component.translatable("fancybars.heldItem.move"), ModernButton.Style.PRIMARY, () -> minecraft.gui.setScreen(new BarEditorScreen(BarsMenuScreen.this))),
						new ModernButton(10, 20, Component.translatable("fancybars.heldItem.reset"), () -> MovableElement.HOTBAR.reset())));
				list.add(new HeaderItem(Component.translatable("fancybars.category.inventoryHud"), null));
				list.add(new ToggleItem("invHudEnabled", () -> config.invHudEnabled, value -> config.invHudEnabled = value));
				list.add(new ToggleItem("invHudBackground", () -> config.invHudBackground, value -> config.invHudBackground = value));
				list.add(new ToggleItem("invHudSlots", () -> config.invHudSlots, value -> config.invHudSlots = value));
				list.add(new ButtonRowItem(
						new ModernButton(10, 20, Component.translatable("fancybars.heldItem.move"), ModernButton.Style.PRIMARY, () -> minecraft.gui.setScreen(new BarEditorScreen(BarsMenuScreen.this))),
						new ModernButton(10, 20, Component.translatable("fancybars.heldItem.reset"), () -> MovableElement.INVENTORY.reset())));
			}
			case SPOTIFY -> {
				list.add(new HeaderItem(Component.translatable("fancybars.category.spotify"), null));
				list.add(new ButtonRowItem(
						new ModernButton(10, 20, Component.translatable("fancybars.spotify.open"), ModernButton.Style.PRIMARY, () -> minecraft.gui.setScreen(new dev.fancybars.spotify.SpotifyScreen(BarsMenuScreen.this))),
						new ModernButton(10, 20, Component.translatable("fancybars.heldItem.move"), () -> minecraft.gui.setScreen(new BarEditorScreen(BarsMenuScreen.this)))));
				list.add(new ToggleItem("spotifyEnabled", () -> config.spotifyEnabled, value -> config.spotifyEnabled = value));
				list.add(new ToggleItem("spotifyHud", () -> config.spotifyHud, value -> config.spotifyHud = value));
				list.add(new ToggleItem("spotifyInventory", () -> config.spotifyInventory, value -> config.spotifyInventory = value));
				list.add(new InfoCardItem(Component.translatable("fancybars.spotify.aboutTitle"), List.of(
						Component.translatable(dev.fancybars.spotify.Spotify.connected() ? "fancybars.spotify.connected" : "fancybars.spotify.notConnected"),
						Component.translatable("fancybars.spotify.premium"),
						Component.translatable("fancybars.spotify.keys")), false));
			}
			case CHAT -> {
				list.add(new HeaderItem(Component.translatable("fancybars.category.chatTabs"), null));
				list.add(new ToggleItem("chatTabsEnabled", () -> config.chatTabsEnabled, value -> {
					config.chatTabsEnabled = value;
					dev.fancybars.chat.ChatTabs.applyFilter();
				}));
				list.add(new ToggleItem("chatTabsSwitchChannel", () -> config.chatTabsSwitchChannel, value -> config.chatTabsSwitchChannel = value));
				list.add(new ToggleItem("chatTabsPrefill", () -> config.chatTabsPrefill, value -> config.chatTabsPrefill = value));
				String group = "";
				for (dev.fancybars.chat.ChatFilters.Filter filter : dev.fancybars.chat.ChatFilters.ALL) {
					if (!filter.group().equals(group)) {
						group = filter.group();
						list.add(new HeaderItem(Component.translatable("fancybars.chat.group." + group), null));
					}
					String id = filter.id();
					list.add(new ToggleItem("chat." + id, () -> config.chatFilters.contains(id), value -> {
						if (value) config.chatFilters.add(id);
						else config.chatFilters.remove(id);
					}));
				}
			}
			case MINING -> {
				list.add(new HeaderItem(Component.translatable("fancybars.category.commissions"), null));
				list.add(new ToggleItem("commissionsEnabled", () -> config.commissionsEnabled, value -> config.commissionsEnabled = value));
				list.add(new ButtonRowItem(
						new ModernButton(10, 20, Component.translatable("fancybars.heldItem.move"), ModernButton.Style.PRIMARY, () -> minecraft.gui.setScreen(new BarEditorScreen(BarsMenuScreen.this))),
						new ModernButton(10, 20, Component.translatable("fancybars.heldItem.reset"), () -> MovableElement.COMMISSIONS.reset())));
			}
			case PLAYER -> {
				list.add(new HeaderItem(Component.translatable("fancybars.category.playerSize"), null));
				list.add(new ToggleItem("playerSizeEnabled", () -> config.playerSizeEnabled, value -> config.playerSizeEnabled = value));
				list.add(new SliderItem("playerSizeWidth", () -> config.playerSizeWidth, value -> config.playerSizeWidth = value));
				list.add(new SliderItem("playerSizeHeight", () -> config.playerSizeHeight, value -> config.playerSizeHeight = value));
				list.add(new SliderItem("playerSizeLength", () -> config.playerSizeLength, value -> config.playerSizeLength = value));
				list.add(new ToggleItem("playerSizeShowOthers", () -> config.playerSizeShowOthers, value -> config.playerSizeShowOthers = value));
				list.add(new HeaderItem(Component.translatable("fancybars.category.viewmodel"), null));
				list.add(new ToggleItem("viewmodelEnabled", () -> config.viewmodelEnabled, value -> config.viewmodelEnabled = value));
				list.add(new ToggleItem("viewmodelIgnoreHaste", () -> config.viewmodelIgnoreHaste, value -> config.viewmodelIgnoreHaste = value));
				list.add(new ToggleItem("viewmodelNoSwing", () -> config.viewmodelNoSwing, value -> config.viewmodelNoSwing = value));
				list.add(new ToggleItem("viewmodelSwingInPlace", () -> config.viewmodelSwingInPlace, value -> config.viewmodelSwingInPlace = value));
				list.add(new ToggleItem("viewmodelSwingInPlaceItemsOnly", () -> config.viewmodelSwingInPlaceItemsOnly, value -> config.viewmodelSwingInPlaceItemsOnly = value));
				list.add(new SliderItem("viewmodelSwingSpeed", 0.25f, 3, 0.05f, true, () -> config.viewmodelSwingSpeed, value -> config.viewmodelSwingSpeed = value));
				list.add(new ToggleItem("viewmodelApplyToHand", () -> config.viewmodelApplyToHand, value -> config.viewmodelApplyToHand = value));
				list.add(new HeaderItem(Component.translatable("fancybars.viewmodel.item"), null));
				list.add(new SliderItem("viewmodelX", -1, 1, 0.02f, false, () -> config.viewmodelItemX, value -> config.viewmodelItemX = value));
				list.add(new SliderItem("viewmodelY", -1, 1, 0.02f, false, () -> config.viewmodelItemY, value -> config.viewmodelItemY = value));
				list.add(new SliderItem("viewmodelZ", -1, 1, 0.02f, false, () -> config.viewmodelItemZ, value -> config.viewmodelItemZ = value));
				list.add(new SliderItem("viewmodelRotX", -180, 180, 5, false, () -> config.viewmodelItemRotX, value -> config.viewmodelItemRotX = value));
				list.add(new SliderItem("viewmodelRotY", -180, 180, 5, false, () -> config.viewmodelItemRotY, value -> config.viewmodelItemRotY = value));
				list.add(new SliderItem("viewmodelRotZ", -180, 180, 5, false, () -> config.viewmodelItemRotZ, value -> config.viewmodelItemRotZ = value));
				list.add(new SliderItem("viewmodelScale", 0.1f, 2, 0.05f, true, () -> config.viewmodelItemScale, value -> config.viewmodelItemScale = value));
				list.add(new HeaderItem(Component.translatable("fancybars.viewmodel.arm"), null));
				list.add(new SliderItem("viewmodelX", -1, 1, 0.02f, false, () -> config.viewmodelArmX, value -> config.viewmodelArmX = value));
				list.add(new SliderItem("viewmodelY", -1, 1, 0.02f, false, () -> config.viewmodelArmY, value -> config.viewmodelArmY = value));
				list.add(new SliderItem("viewmodelZ", -1, 1, 0.02f, false, () -> config.viewmodelArmZ, value -> config.viewmodelArmZ = value));
				list.add(new SliderItem("viewmodelRotX", -180, 180, 5, false, () -> config.viewmodelArmRotX, value -> config.viewmodelArmRotX = value));
				list.add(new SliderItem("viewmodelRotY", -180, 180, 5, false, () -> config.viewmodelArmRotY, value -> config.viewmodelArmRotY = value));
				list.add(new SliderItem("viewmodelRotZ", -180, 180, 5, false, () -> config.viewmodelArmRotZ, value -> config.viewmodelArmRotZ = value));
				list.add(new SliderItem("viewmodelScale", 0.1f, 2, 0.05f, true, () -> config.viewmodelArmScale, value -> config.viewmodelArmScale = value));
				list.add(new ButtonRowItem(new ModernButton(10, 20, Component.translatable("fancybars.viewmodel.reset"), () -> {
					config.viewmodelItemX = config.viewmodelItemY = config.viewmodelItemZ = 0;
					config.viewmodelItemRotX = config.viewmodelItemRotY = config.viewmodelItemRotZ = 0;
					config.viewmodelItemScale = 1;
					config.viewmodelArmX = config.viewmodelArmY = config.viewmodelArmZ = 0;
					config.viewmodelArmRotX = config.viewmodelArmRotY = config.viewmodelArmRotZ = 0;
					config.viewmodelArmScale = 1;
					config.viewmodelSwingSpeed = 1;
					FancyBarsConfig.save();
					rebuildNextTick = true;
				})));
				list.add(new HeaderItem(Component.translatable("fancybars.category.capes"), null));
				list.add(new ToggleItem("capeEnabled", () -> config.capeEnabled, value -> config.capeEnabled = value));
				list.add(new CapeGridItem());
				list.add(new ButtonRowItem(
						new ModernButton(10, 20, Component.translatable("fancybars.capes.openFolder"), dev.fancybars.capes.Capes::openCustomFolder),
						new ModernButton(10, 20, Component.translatable("fancybars.capes.reloadCustom"), () -> {
							dev.fancybars.capes.Capes.reloadCustom();
							rebuildNextTick = true;
						})));
				list.add(new HeaderItem(Component.translatable("fancybars.category.wallLicker"), null));
				list.add(new ToggleItem("wallLickerEnabled", () -> config.wallLickerEnabled, value -> config.wallLickerEnabled = value));
				list.add(new ToggleItem("wallLickerSound", () -> config.wallLickerSound, value -> config.wallLickerSound = value));
				list.add(new ValueCycleItem("wallLickerVolume", new int[]{25, 50, 75, 100}, () -> config.wallLickerVolume,
						value -> config.wallLickerVolume = value, value -> Component.literal(value + "%")));
				list.add(new InfoCardItem(Component.translatable("fancybars.wallLicker.statsTitle"), List.of(
						Component.translatable("fancybars.wallLicker.allTime", config.wallLicks),
						Component.translatable("fancybars.wallLicker.thisRun", config.wallLicksRun)), false));
				String sound = dev.fancybars.fun.WallLicker.soundName();
				list.add(new InfoCardItem(Component.translatable("fancybars.wallLicker.soundTitle"), List.of(
						sound != null ? Component.translatable("fancybars.wallLicker.soundFound", sound) : Component.translatable("fancybars.wallLicker.noSound"),
						Component.translatable("fancybars.wallLicker.howTo")), false));
				list.add(new ButtonRowItem(
						new ModernButton(10, 20, Component.translatable("fancybars.wallLicker.openFolder"), ModernButton.Style.PRIMARY, dev.fancybars.fun.WallLicker::openFolder),
						new ModernButton(10, 20, Component.translatable("fancybars.wallLicker.test"), dev.fancybars.fun.WallLicker::playSound),
						new ModernButton(10, 20, Component.translatable("fancybars.wallLicker.refresh"), () -> rebuildNextTick = true)));
				list.add(new ButtonRowItem(
						new ModernButton(10, 20, Component.translatable("fancybars.heldItem.move"), () -> minecraft.gui.setScreen(new BarEditorScreen(BarsMenuScreen.this))),
						new ModernButton(10, 20, Component.translatable("fancybars.wallLicker.resetCount"), () -> minecraft.gui.setScreen(new ConfirmPopup(BarsMenuScreen.this,
								Component.translatable("fancybars.wallLicker.resetTitle"),
								Component.translatable("fancybars.wallLicker.resetMessage", config.wallLicks),
								() -> {
									config.wallLicks = 0;
									config.wallLicksRun = 0;
									FancyBarsConfig.save();
									rebuildNextTick = true;
								})))));
				list.add(new HeaderItem(Component.translatable("fancybars.category.nametag"), null));
				list.add(new ToggleItem("ownNametagInF5", () -> config.ownNametagInF5, value -> config.ownNametagInF5 = value));
				list.add(new ToggleItem("hideOwnFireF5", () -> config.hideOwnFireF5, value -> config.hideOwnFireF5 = value));
			}
			case GENERAL -> {
				list.add(new HeaderItem(Component.translatable("fancybars.category.general"), null));
				list.add(new ToggleItem("shikiMode", () -> config.shikiMode, value -> config.shikiMode = value));
				list.add(new ToggleItem("enableBars", () -> config.enableBars, value -> config.enableBars = value));
				list.add(new ToggleItem("enableBarsRift", () -> config.enableBarsRift, value -> config.enableBarsRift = value));
				list.add(new ToggleItem("riftHealthHP", () -> config.riftHealthHP, value -> config.riftHealthHP = value));
				list.add(new ToggleItem("showEstimatedTilde", () -> config.showEstimatedTilde, value -> config.showEstimatedTilde = value));
				list.add(new ToggleItem("hideMana", () -> config.hideMana, value -> config.hideMana = value));
				list.add(new CycleItem());
			}
			case VANILLA_MANA -> {
				list.add(new HeaderItem(Component.translatable("fancybars.category.vanillaMana"), null));
				list.add(new ToggleItem("enableVanillaStyleManaBar", () -> config.enableVanillaStyleManaBar, value -> config.enableVanillaStyleManaBar = value));
				list.add(new ToggleItem("enableVanillaStyleManaBarBlinking", () -> config.enableVanillaStyleManaBarBlinking, value -> config.enableVanillaStyleManaBarBlinking = value));
				list.add(new ToggleItem("useHungerBarSprites", () -> config.useHungerBarSprites, value -> config.useHungerBarSprites = value));
			}
		}
		return list;
	}

	// ================================================================ Rendering

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		Theme.backdrop(graphics, width, height);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		// Smooth scrolling
		float dt = Math.min(0.1f, (Util.getMillis() - lastFrame) / 1000f);
		lastFrame = Util.getMillis();
		scrollTarget = Math.clamp(scrollTarget, 0, maxScroll());
		if (Math.abs(scrollTarget - scroll) > 0.1f) {
			scroll += (scrollTarget - scroll) * Math.min(1, dt * 14);
			layoutContent();
		} else if (scroll != scrollTarget) {
			scroll = scrollTarget;
			layoutContent();
		}

		float offset = Theme.openOffset(openedAt);
		Theme.pushOffset(graphics, offset);
		int my = mouseY - Math.round(offset);

		int rightX = x0 + SIDEBAR_WIDTH + GAP;
		int rightW = totalWidth - SIDEBAR_WIDTH - GAP;

		// Panels
		Theme.panel(graphics, x0, y0, SIDEBAR_WIDTH, TOP_HEIGHT);
		Theme.panel(graphics, rightX, y0, rightW, TOP_HEIGHT);
		Theme.panel(graphics, x0, y0 + TOP_HEIGHT + GAP, SIDEBAR_WIDTH, totalHeight - TOP_HEIGHT - GAP);
		Theme.panel(graphics, rightX, y0 + TOP_HEIGHT + GAP, rightW, totalHeight - TOP_HEIGHT - GAP);

		// Title: SBCLIENT (S pink, B blue, CLIENT pink → blue) or ShikiClient in SHIKIMODE, measured as one string and centred exactly in the box
		Component titleText = Theme.title(Theme.brand());
		// Thumbnail + title, centred together; the text shrinks if it would not fit
		int iconSize = 18;
		int iconGap = 5;
		float textW = font.width(titleText);
		float maxText = SIDEBAR_WIDTH - 12 - iconSize - iconGap;
		float textScale = Math.min(1, maxText / textW);
		float groupW = iconSize + iconGap + textW * textScale;
		float gx = x0 + (SIDEBAR_WIDTH - groupW) / 2f;
		dev.fancybars.hud.Watermark.drawIcon(graphics, Theme.brandIcon(), gx, y0 + (TOP_HEIGHT - iconSize) / 2f, iconSize, 255);
		float textX = gx + iconSize + iconGap;
		float capTop = y0 + (TOP_HEIGHT - Theme.TITLE_CAP * textScale) / 2f;
		org.joml.Matrix3x2fStack titlePose = graphics.pose();
		titlePose.pushMatrix();
		titlePose.translate(textX, capTop);
		titlePose.scale(textScale, textScale);
		Theme.drawAtCapTop(graphics, font, titleText, 0, 0, Theme.TITLE_CAP, 0xFFFFFFFF);
		titlePose.popMatrix();

		// Version at the bottom of the sidebar
		Theme.centeredInBox(graphics, font, Component.literal("v" + FancyBarsMod.VERSION), x0 + SIDEBAR_WIDTH / 2f, y0 + totalHeight - 18, 12, Theme.TEXT_DISABLED);

		// Fixed widgets (search, nav, sidebar)
		pendingTip = null;
		super.extractRenderState(graphics, mouseX, my, a);
		for (GuiEventListener child : children()) {
			if (child instanceof AbstractWidget widget && !contentWidgets.contains(widget) && widget.visible && widget.isMouseOver(mouseX, my) && widgetTips.containsKey(widget)) {
				tip(widget, widgetTips.get(widget));
			}
		}

		// Scrolling content
		graphics.enableScissor(contentX - 2, contentY - 2, contentX + contentW + 2, contentY + contentH + 2);
		boolean mouseInContent = mouseX >= contentX && mouseX < contentX + contentW && my >= contentY && my < contentY + contentH;
		int contentMouseY = mouseInContent ? my : -1000;
		for (Item item : items) {
			if (item.y + item.height < contentY || item.y > contentY + contentH) continue;
			item.render(graphics, contentX, item.y, contentW - 6, mouseX, contentMouseY);
		}
		for (AbstractWidget widget : contentWidgets) {
			if (widget.visible) widget.extractRenderState(graphics, mouseX, contentMouseY, a);
		}
		graphics.disableScissor();
		if (mouseInContent) {
			for (AbstractWidget widget : contentWidgets) {
				if (widget.visible && widget.isMouseOver(mouseX, my) && widgetTips.containsKey(widget)) tip(widget, widgetTips.get(widget));
			}
		}
		if (mouseInContent && pendingTip == null) {
			for (Item item : items) {
				if (item.tooltip != null && item.isHovered(contentX, contentW - 6, mouseX, my)) tip(item, item.tooltip);
			}
		}

		// Scrollbar
		if (maxScroll() > 0) {
			int trackX = contentX + contentW - 3;
			float visibleFrac = contentH / (float) contentHeight;
			int thumbH = Math.max(16, Math.round(contentH * visibleFrac));
			int thumbY = contentY + Math.round((contentH - thumbH) * (scroll / maxScroll()));
			Theme.roundedRect(graphics, trackX, contentY, 3, contentH, 1, 0x30FFFFFF);
			Theme.roundedRect(graphics, trackX, thumbY, 3, thumbH, 1, 0xB0FFC4E1);
		}

		Theme.popOffset(graphics);

		// Tooltip (after a short delay, drawn on top of everything)
		if (pendingTip == null) {
			tipOwner = null;
		} else if (Util.getMillis() - tipSince > 350) {
			drawTooltip(graphics, pendingTip, mouseX, mouseY);
		}
	}

	private void drawTooltip(GuiGraphicsExtractor graphics, Component text, int mouseX, int mouseY) {
		graphics.nextStratum();
		List<net.minecraft.util.FormattedCharSequence> lines = font.split(Theme.styled(text), 190);
		int w = 0;
		for (net.minecraft.util.FormattedCharSequence line : lines) w = Math.max(w, font.width(line));
		w += 16;
		int lineH = 11;
		int h = lines.size() * lineH + 9;
		int x = mouseX + 10;
		int y = mouseY + 12;
		if (x + w > width - 4) x = mouseX - w - 6;
		if (y + h > height - 4) y = mouseY - h - 6;
		x = Math.max(4, x);
		y = Math.max(4, y);
		float fade = Math.min(1, (Util.getMillis() - tipSince - 350) / 120f);
		Theme.shadow(graphics, x, y, w, h, 6, 0, 2);
		Theme.card(graphics, x, y, w, h, 6, Theme.withAlpha(0xF536232B, fade), Theme.withAlpha(Theme.SELECTED_BORDER, fade));
		int ly = y + 5;
		for (net.minecraft.util.FormattedCharSequence line : lines) {
			Theme.drawSeqInBox(graphics, font, line, x + 8, ly, lineH, Theme.withAlpha(Theme.TEXT_SOFT, fade));
			ly += lineH;
		}
	}

	// ================================================================ Input

	private boolean inContent(double mouseX, double mouseY) {
		return mouseX >= contentX && mouseX < contentX + contentW && mouseY >= contentY && mouseY < contentY + contentH;
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
		if (inContent(click.x(), click.y())) {
			if (super.mouseClicked(click, doubled)) return true;
			for (Item item : items) {
				if (item.mouseClicked(click.x(), click.y())) return true;
			}
			return false;
		}
		// Don't let scrolled-away content widgets catch clicks outside the content area
		List<AbstractWidget> hidden = new ArrayList<>();
		for (AbstractWidget widget : contentWidgets) {
			if (widget.visible) {
				widget.visible = false;
				hidden.add(widget);
			}
		}
		boolean result = super.mouseClicked(click, doubled);
		for (AbstractWidget widget : hidden) widget.visible = true;
		return result;
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
		if (inContent(mouseX, mouseY) && maxScroll() > 0) {
			scrollTarget = Math.clamp(scrollTarget - (float) vertical * 24, 0, maxScroll());
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, horizontal, vertical);
	}

	@Override
	public void tick() {
		super.tick();
		if (rebuildNextTick) {
			rebuildNextTick = false;
			rebuildWidgets();
		}
	}

	@Override
	public void removed() {
		super.removed();
		bars.updatePositions(false);
		bars.saveBarConfig();
		FancyBarsConfig.save();
	}

	@Override
	public void onClose() {
		minecraft.gui.setScreen(parent);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	// ================================================================ Categories / sidebar

	public enum Category {
		HOME("fancybars.category.home"),
		BARS("fancybars.category.bars"),
		STYLE("fancybars.category.style"),
		ITEMS("fancybars.category.items"),
		GIFS("fancybars.category.gifs"),
		DUNGEONS("fancybars.category.dungeons"),
		ACTION_BAR("fancybars.category.actionBar"),
		HUD("fancybars.category.hud"),
		MINING("fancybars.category.mining"),
		CHAT("fancybars.category.chat"),
		SPOTIFY("fancybars.category.spotify"),
		PLAYER("fancybars.category.player"),
		GENERAL("fancybars.category.general"),
		VANILLA_MANA("fancybars.category.vanillaMana");

		private final String key;

		Category(String key) {
			this.key = key;
		}

		public Component label() {
			return Component.translatable(key);
		}
	}

	private class SidebarItem extends AbstractWidget {
		private final Category target;
		private float hover = 0;
		private long last = Util.getMillis();

		SidebarItem(Category target, int w, int h) {
			super(0, 0, w, h, target.label());
			this.target = target;
		}

		@Override
		protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
			boolean selected = search.isBlank() && category == target;
			hover = Theme.approach(hover, isHovered() ? 1 : 0, 8, last);
			last = Util.getMillis();
			if (selected) {
				Theme.card(graphics, getX(), getY(), getWidth(), getHeight(), Theme.RADIUS, Theme.SELECTED, Theme.SELECTED_BORDER);
			} else if (hover > 0.01f) {
				Theme.roundedRect(graphics, getX(), getY(), getWidth(), getHeight(), Theme.RADIUS, Theme.withAlpha(Theme.SURFACE_HOVER, hover));
			}
			int textColor = selected ? Theme.TEXT : Theme.lerpColor(Theme.TEXT_SOFT, Theme.TEXT, hover);
			Theme.textInBox(graphics, font, getMessage(), getX() + 9 + hover * 2, getY(), getHeight(), textColor);
			if (isHovered()) graphics.requestCursor(CursorTypes.POINTING_HAND);
		}

		@Override
		public void onClick(MouseButtonEvent click, boolean doubled) {
			switchCategory(target);
		}

		@Override
		protected void updateWidgetNarration(NarrationElementOutput output) {
			output.add(NarratedElementType.TITLE, getMessage());
		}
	}

	// ================================================================ Items

	private abstract static class Item {
		int y;
		int height;
		boolean searchable = false;
		String searchText = "";
		@Nullable Component tooltip;
		final List<AbstractWidget> widgets = new ArrayList<>();

		abstract void layout(int x, int y, int w);

		abstract void render(GuiGraphicsExtractor graphics, int x, int y, int w, int mouseX, int mouseY);

		boolean mouseClicked(double mouseX, double mouseY) {
			return false;
		}

		boolean isHovered(int x, int w, int mouseX, int mouseY) {
			return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + height;
		}
	}

	private boolean itemHit(Item item, double mouseX, double mouseY) {
		return mouseX >= contentX && mouseX < contentX + contentW - 6 && mouseY >= item.y && mouseY < item.y + item.height;
	}

	/** Big title with an optional description line. */
	private class HeaderItem extends Item {
		private final Component title;
		private final @Nullable Component description;

		HeaderItem(Component title, @Nullable Component description) {
			this.title = title;
			this.description = description;
			this.height = description != null ? 32 : 20;
		}

		@Override
		void layout(int x, int y, int w) {}

		@Override
		void render(GuiGraphicsExtractor graphics, int x, int y, int w, int mouseX, int mouseY) {
			Theme.titleInBox(graphics, font, title, x + 2, y, 16, Theme.TEXT);
			if (description != null) Theme.textInBox(graphics, font, clip(description, w - 4), x + 2, y + 19, 10, Theme.TEXT_MUTED);
		}
	}

	/** Row of three big buttons at the top of the home page. */
	private class ActionRowItem extends Item {
		private final ModernButton edit;
		private final ModernButton reset;
		private final ModernButton done;

		ActionRowItem() {
			height = 26;
			edit = new ModernButton(10, 26, Component.translatable("fancybars.menu.editGui"), ModernButton.Style.PRIMARY, () -> minecraft.gui.setScreen(new BarEditorScreen(BarsMenuScreen.this)));
			tipFor(edit, Component.translatable("fancybars.menu.editGui.@Tooltip"));
			reset = new ModernButton(10, 26, Component.translatable("fancybars.menu.resetLayout"), () -> minecraft.gui.setScreen(new ConfirmPopup(BarsMenuScreen.this,
					Component.translatable("fancybars.bars.config.resetTitle"),
					Component.translatable("fancybars.bars.config.reset"),
					() -> {
						bars.resetBarPositions();
						bars.statusBars.values().forEach(bar -> {
							bar.enabled = true;
							bar.scale = 1;
						});
						bars.updatePositions(false);
						bars.saveBarConfig();
					})));
			done = new ModernButton(10, 26, Component.translatable("gui.done"), BarsMenuScreen.this::onClose);
			widgets.add(edit);
			widgets.add(reset);
			widgets.add(done);
		}

		@Override
		void layout(int x, int y, int w) {
			int bw = (w - 12) / 3;
			edit.setWidth(bw);
			reset.setWidth(bw);
			done.setWidth(w - 12 - bw * 2);
			edit.setPosition(x, y);
			reset.setPosition(x + bw + 6, y);
			done.setPosition(x + (bw + 6) * 2, y);
		}

		@Override
		void render(GuiGraphicsExtractor graphics, int x, int y, int w, int mouseX, int mouseY) {}
	}

	/** Card with a big title and bullet lines (like an info / changelog box). */
	private class InfoCardItem extends Item {
		private final Component title;
		private final List<Component> lines;
		private final boolean accentTitle;

		InfoCardItem(Component title, List<Component> lines, boolean accentTitle) {
			this.title = title;
			this.lines = lines;
			this.accentTitle = accentTitle;
			this.height = 32 + lines.size() * 12 + 8;
		}

		@Override
		void layout(int x, int y, int w) {}

		@Override
		void render(GuiGraphicsExtractor graphics, int x, int y, int w, int mouseX, int mouseY) {
			Theme.card(graphics, x, y, w, height, Theme.PANEL_RADIUS, Theme.CARD, Theme.CARD_BORDER);
			int tx = x + 12;
			Component heading = accentTitle
					? Component.empty().append(Theme.brand()).append(" ").append(title.copy().withColor(Theme.night(Theme.TEXT) & 0xFFFFFF))
					: title.copy().withColor(Theme.night(Theme.TEXT) & 0xFFFFFF);
			Theme.titleInBox(graphics, font, heading, tx, y + 6, 20, 0xFFFFFFFF);
			int ly = y + 32;
			for (Component line : lines) {
				Theme.textInBox(graphics, font, clip(Component.literal("•  ").append(line), w - 28), x + 14, ly, 10, Theme.TEXT_SOFT);
				ly += 12;
			}
		}
	}

	/** Setting card: name + description on the left, switch on the right. Clicking the card flips it. */
	private class ToggleItem extends Item {
		private final String key;
		private final ModernToggle toggle;

		ToggleItem(String key, Supplier<Boolean> getter, Consumer<Boolean> setter) {
			this.key = key;
			this.height = 24;
			this.tooltip = Component.translatable("fancybars.config." + key + ".@Tooltip");
			this.searchable = true;
			this.searchText = Component.translatable("fancybars.config." + key).getString() + " " + Component.translatable("fancybars.config." + key + ".@Tooltip").getString();
			this.toggle = new ModernToggle(ModernToggle.SWITCH_WIDTH, ModernToggle.SWITCH_HEIGHT, null, getter.get(), value -> {
				setter.accept(value);
				FancyBarsConfig.save();
			});
			widgets.add(toggle);
		}

		@Override
		void layout(int x, int y, int w) {
			toggle.setPosition(x + w - ModernToggle.SWITCH_WIDTH - 10, y + (height - ModernToggle.SWITCH_HEIGHT) / 2);
		}

		@Override
		void render(GuiGraphicsExtractor graphics, int x, int y, int w, int mouseX, int mouseY) {
			boolean hovered = isHovered(x, w, mouseX, mouseY);
			Theme.card(graphics, x, y, w, height, Theme.PANEL_RADIUS, hovered ? Theme.CARD_HOVER : Theme.CARD, hovered ? Theme.SELECTED_BORDER : Theme.CARD_BORDER);
			Theme.boldInBox(graphics, font, clip(Component.translatable("fancybars.config." + key), w - 60), x + 10, y, height, Theme.TEXT);
			if (hovered) graphics.requestCursor(CursorTypes.POINTING_HAND);
		}

		@Override
		boolean mouseClicked(double mouseX, double mouseY) {
			if (itemHit(this, mouseX, mouseY)) {
				toggle.toggle();
				return true;
			}
			return false;
		}
	}

	/** Intelligence display selector. */
	private class CycleItem extends Item {
		private final ModernButton button;

		CycleItem() {
			this.height = 24;
			this.searchable = true;
			this.searchText = Component.translatable("fancybars.config.intelligenceDisplay").getString() + " mana overflow";
			this.tooltip = Component.translatable("fancybars.config.intelligenceDisplay.@Tooltip");
			ModernButton[] holder = new ModernButton[1];
			holder[0] = new ModernButton(116, 18, Component.translatable(config.intelligenceDisplay.translationKey()), () -> {
				config.intelligenceDisplay = EnumUtils.cycle(config.intelligenceDisplay);
				FancyBarsConfig.save();
				holder[0].setMessage(Component.translatable(config.intelligenceDisplay.translationKey()));
			});
			this.button = holder[0];
			widgets.add(button);
		}

		@Override
		void layout(int x, int y, int w) {
			button.setPosition(x + w - button.getWidth() - 8, y + (height - button.getHeight()) / 2);
		}

		@Override
		void render(GuiGraphicsExtractor graphics, int x, int y, int w, int mouseX, int mouseY) {
			boolean hovered = isHovered(x, w, mouseX, mouseY);
			Theme.card(graphics, x, y, w, height, Theme.PANEL_RADIUS, hovered ? Theme.CARD_HOVER : Theme.CARD, hovered ? Theme.SELECTED_BORDER : Theme.CARD_BORDER);
			Theme.boldInBox(graphics, font, clip(Component.translatable("fancybars.config.intelligenceDisplay"), w - 140), x + 10, y, height, Theme.TEXT);
		}
	}

	/** One bar: preview, name, where it is, shown + icon switches and colour swatches. */
	private class BarItem extends Item {
		private final StatusBar bar;
		private final ModernToggle shown;
		private final ModernToggle icon;
		private final List<ColorSwatch> swatches = new ArrayList<>();

		BarItem(StatusBar bar) {
			this.bar = bar;
			this.height = 38;
			this.searchable = true;
			this.searchText = bar.getName().getString() + " bar color colour icon";
			shown = new ModernToggle(ModernToggle.SWITCH_WIDTH, ModernToggle.SWITCH_HEIGHT, null, bar.enabled, value -> {
				bars.setBarEnabled(bar, value);
				bars.saveBarConfig();
				rebuildNextTick = true;
			});
			tipFor(shown, Component.translatable("fancybars.menu.shown.@Tooltip"));
			icon = new ModernToggle(ModernToggle.SWITCH_WIDTH, ModernToggle.SWITCH_HEIGHT, null, bar.getIconPosition() != StatusBar.IconPosition.OFF, value -> {
				bar.setIconPosition(value ? StatusBar.IconPosition.LEFT : StatusBar.IconPosition.OFF);
				bars.saveBarConfig();
			});
			tipFor(icon, Component.translatable("fancybars.menu.icon.@Tooltip"));
			icon.active = bar.enabled;
			widgets.add(shown);
			widgets.add(icon);

			swatches.add(new ColorSwatch(Component.translatable("fancybars.menu.barColor"), () -> bar.getColors()[0], color -> bar.getColors()[0] = color));
			if (bar.hasOverflow()) {
				String key = bar.getType() == StatusBarType.HEALTH ? "fancybars.menu.absorptionColor" : bar.getType() == StatusBarType.INTELLIGENCE ? "fancybars.menu.overflowManaColor" : "fancybars.bars.config.overflowColor";
				swatches.add(new ColorSwatch(Component.translatable(key), () -> bar.getColors()[1], color -> bar.getColors()[1] = color));
			}
			swatches.add(new ColorSwatch(Component.translatable("fancybars.bars.config.textColor"), () -> bar.getTextColor() != null ? bar.getTextColor() : bar.getColors()[0], bar::setTextColor));
			widgets.addAll(swatches);
		}

		@Override
		void layout(int x, int y, int w) {
			// Fixed three-slot colour area so the switches line up between bars
			int colorsLeft = x + w - 10 - 3 * SWATCH - 2 * 5;
			for (int i = 0; i < swatches.size(); i++) {
				swatches.get(i).setPosition(colorsLeft + i * (SWATCH + 5), y + 20);
			}
			icon.setPosition(colorsLeft - 14 - ModernToggle.SWITCH_WIDTH, y + 20);
			shown.setPosition(icon.getX() - 14 - ModernToggle.SWITCH_WIDTH, y + 20);
		}

		@Override
		void render(GuiGraphicsExtractor graphics, int x, int y, int w, int mouseX, int mouseY) {
			boolean hovered = isHovered(x, w, mouseX, mouseY);
			Theme.card(graphics, x, y, w, height, Theme.PANEL_RADIUS, hovered ? Theme.CARD_HOVER : Theme.CARD, hovered ? Theme.SELECTED_BORDER : Theme.CARD_BORDER);

			// Name + location, preview underneath
			Theme.boldInBox(graphics, font, bar.getName(), x + 10, y + 5, 11, bar.enabled ? Theme.TEXT : Theme.TEXT_DISABLED);
			Component where = !bar.enabled ? Component.translatable("fancybars.menu.where.hidden")
					: bar.anchor != null ? Component.translatable("fancybars.menu.where.hotbar")
					: Component.translatable("fancybars.menu.where.free", Math.round(bar.scale * 100));
			Theme.textInBox(graphics, font, where, x + 14 + Theme.boldWidth(font, bar.getName()), y + 5, 11, Theme.TEXT_MUTED);
			bar.extractPreview(graphics, x + 10, y + 21, Math.min(110, shown.getX() - x - 24));

			// Column captions above the controls
			int captionY = y + 5;
			Theme.centeredInBox(graphics, font, Component.translatable("fancybars.menu.header.shown"), shown.getX() + ModernToggle.SWITCH_WIDTH / 2f, captionY, 11, Theme.TEXT_MUTED);
			Theme.centeredInBox(graphics, font, Component.translatable("fancybars.menu.header.icon"), icon.getX() + ModernToggle.SWITCH_WIDTH / 2f, captionY, 11, Theme.TEXT_MUTED);
			int colorsLeft = swatches.getFirst().getX();
			int colorsRight = colorsLeft + 3 * SWATCH + 2 * 5;
			Theme.centeredInBox(graphics, font, Component.translatable("fancybars.menu.header.colors"), (colorsLeft + colorsRight) / 2f, captionY, 11, Theme.TEXT_MUTED);
		}
	}

	/** Selectable bar style card with a live preview. */
	private class StyleItem extends Item {
		private final FancyBarsConfig.BarStyle style;

		StyleItem(FancyBarsConfig.BarStyle style) {
			this.style = style;
			this.height = 30;
			this.searchable = true;
			this.searchText = Component.translatable(style.translationKey()).getString() + " style texture bars";
			this.tooltip = Component.translatable(style.translationKey() + ".description");
		}

		@Override
		void layout(int x, int y, int w) {}

		@Override
		void render(GuiGraphicsExtractor graphics, int x, int y, int w, int mouseX, int mouseY) {
			boolean selected = config.barStyle == style;
			boolean hovered = isHovered(x, w, mouseX, mouseY);
			Theme.card(graphics, x, y, w, height, Theme.PANEL_RADIUS,
					selected ? Theme.SELECTED : hovered ? Theme.CARD_HOVER : Theme.CARD,
					selected ? Theme.ACCENT : hovered ? Theme.SELECTED_BORDER : Theme.CARD_BORDER);

			// Radio button
			int rx = x + 10;
			int ry = y + (height - 12) / 2;
			Theme.circle(graphics, rx, ry, 12, selected ? Theme.ACCENT : 0xFF6B4E5A);
			Theme.circle(graphics, rx + 2, ry + 2, 8, selected ? Theme.SELECTED : 0xFF332229);
			if (selected) Theme.circle(graphics, rx + 3, ry + 3, 6, Theme.ACCENT);

			Theme.boldInBox(graphics, font, Component.translatable(style.translationKey()), x + 30, y, height, Theme.TEXT);

			// Live preview of health + mana drawn in this style
			int previewWidth = Math.min(120, w / 2 - 20);
			FancyBarsConfig.BarStyle previous = config.barStyle;
			config.barStyle = style;
			StatusBar health = bars.statusBars.get(StatusBarType.HEALTH);
			StatusBar mana = bars.statusBars.get(StatusBarType.INTELLIGENCE);
			int px = x + w - previewWidth - 10;
			if (health != null) health.extractPreview(graphics, px, y + 4, previewWidth);
			if (mana != null) mana.extractPreview(graphics, px, y + 16, previewWidth);
			config.barStyle = previous;
			if (hovered) graphics.requestCursor(CursorTypes.POINTING_HAND);
		}

		@Override
		boolean mouseClicked(double mouseX, double mouseY) {
			if (itemHit(this, mouseX, mouseY)) {
				config.barStyle = style;
				FancyBarsConfig.save();
				return true;
			}
			return false;
		}
	}

	/** Star colour + corner picker with live slot previews. */
	private class StarItem extends Item {
		private static final int SLOT = 22;
		private final ColorSwatch swatch;
		private final net.minecraft.world.item.ItemStack sample = new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND_SWORD);
		private int slotsX;

		StarItem() {
			this.height = 30;
			this.searchable = true;
			this.searchText = Component.translatable("fancybars.protection.star").getString() + " star colour color corner protection";
			this.tooltip = Component.translatable("fancybars.protection.star.description");
			swatch = new ColorSwatch(Component.translatable("fancybars.protection.starColor"),
					() -> new Color(config.protectionStarColor, false),
					color -> {
						config.protectionStarColor = color.getRGB() | 0xFF000000;
						FancyBarsConfig.save();
					});
			widgets.add(swatch);
		}

		@Override
		void layout(int x, int y, int w) {
			slotsX = x + w - 8 - 4 * SLOT - 3 * 4;
			swatch.setPosition(slotsX - SWATCH - 12, y + (height - SWATCH) / 2);
		}

		private FancyBarsConfig.@Nullable StarCorner cornerAt(double mouseX, double mouseY) {
			for (FancyBarsConfig.StarCorner corner : FancyBarsConfig.StarCorner.values()) {
				int sx = slotsX + corner.ordinal() * (SLOT + 4);
				int sy = y + (height - SLOT) / 2;
				if (mouseX >= sx && mouseX < sx + SLOT && mouseY >= sy && mouseY < sy + SLOT) return corner;
			}
			return null;
		}

		@Override
		void render(GuiGraphicsExtractor graphics, int x, int y, int w, int mouseX, int mouseY) {
			Theme.card(graphics, x, y, w, height, Theme.PANEL_RADIUS, Theme.CARD, Theme.CARD_BORDER);
			Theme.boldInBox(graphics, font, Component.translatable("fancybars.protection.star"), x + 10, y, height, Theme.TEXT);

			FancyBarsConfig.StarCorner hoveredCorner = cornerAt(mouseX, mouseY);
			for (FancyBarsConfig.StarCorner corner : FancyBarsConfig.StarCorner.values()) {
				int sx = slotsX + corner.ordinal() * (SLOT + 4);
				int sy = y + (height - SLOT) / 2;
				boolean selected = config.protectionStarCorner == corner;
				boolean hovered = hoveredCorner == corner;
				Theme.card(graphics, sx, sy, SLOT, SLOT, 4, selected ? Theme.SELECTED : hovered ? Theme.SURFACE_HOVER : Theme.SURFACE,
						selected ? Theme.ACCENT : hovered ? Theme.SELECTED_BORDER : Theme.CARD_BORDER);
				graphics.item(sample, sx + 3, sy + 3);
				graphics.nextStratum();
				ItemProtection.drawStar(graphics, sx + 3, sy + 3, corner, config.protectionStarColor);
				if (hovered) {
					tip(corner, Component.translatable(corner.translationKey()));
					graphics.requestCursor(CursorTypes.POINTING_HAND);
				}
			}
		}

		@Override
		boolean mouseClicked(double mouseX, double mouseY) {
			FancyBarsConfig.StarCorner corner = cornerAt(mouseX, mouseY);
			if (corner != null) {
				config.protectionStarCorner = corner;
				FancyBarsConfig.save();
				return true;
			}
			return false;
		}
	}

	/** Protected item count + clear all. The items themselves are listed below as {@link ProtectedRowItem}s. */
	private class ProtectedItemsItem extends Item {
		private final ModernButton clear;

		ProtectedItemsItem() {
			this.height = 26;
			this.searchable = true;
			this.searchText = "protected items protect keybind clear";
			Component key = ItemProtection.protectKey != null ? ItemProtection.protectKey.getTranslatedKeyMessage() : Component.literal("V");
			this.tooltip = Component.translatable("fancybars.protection.howTo", key);
			clear = new ModernButton(80, 18, Component.translatable("fancybars.protection.clearAll"), () -> minecraft.gui.setScreen(new ConfirmPopup(BarsMenuScreen.this,
					Component.translatable("fancybars.protection.clearAll.title"),
					Component.translatable("fancybars.protection.clearAll.message", ItemProtection.protectedCount()),
					ItemProtection::clearAll)));
			clear.active = ItemProtection.protectedCount() > 0;
			widgets.add(clear);
		}

		@Override
		void layout(int x, int y, int w) {
			clear.setPosition(x + w - clear.getWidth() - 4, y + (height - clear.getHeight()) / 2);
		}

		@Override
		void render(GuiGraphicsExtractor graphics, int x, int y, int w, int mouseX, int mouseY) {
			int count = ItemProtection.protectedCount();
			Theme.boldInBox(graphics, font, Component.translatable(count == 1 ? "fancybars.protection.countOne" : "fancybars.protection.count", count), x + 2, y, height, Theme.TEXT);
			if (count == 0) {
				Component key = ItemProtection.protectKey != null ? ItemProtection.protectKey.getTranslatedKeyMessage() : Component.literal("V");
				Theme.textInBox(graphics, font, clip(Component.translatable("fancybars.protection.empty", key), w - 110), x + 2 + Theme.boldWidth(font, Component.translatable("fancybars.protection.count", 0)) + 8, y, height, Theme.TEXT_MUTED);
			}
		}
	}

	/** One protected item: icon, name and a remove button. */
	private class ProtectedRowItem extends Item {
		private final String uuid;
		private final ModernButton remove;

		ProtectedRowItem(String uuid) {
			this.uuid = uuid;
			this.height = 24;
			FancyBarsConfig.ProtectedItem info = config.protectedItemInfo.get(uuid);
			this.searchable = true;
			this.searchText = (info != null ? info.name : "") + " protected";
			this.tooltip = Component.translatable("fancybars.protection.uuid", uuid);
			remove = new ModernButton(56, 16, Component.translatable("fancybars.protection.remove"), () -> {
				ItemProtection.remove(uuid);
				rebuildNextTick = true;
			});
			widgets.add(remove);
		}

		@Override
		void layout(int x, int y, int w) {
			remove.setPosition(x + w - remove.getWidth() - 6, y + (height - remove.getHeight()) / 2);
		}

		@Override
		void render(GuiGraphicsExtractor graphics, int x, int y, int w, int mouseX, int mouseY) {
			boolean hovered = isHovered(x, w, mouseX, mouseY);
			Theme.card(graphics, x, y, w, height, Theme.PANEL_RADIUS, hovered ? Theme.CARD_HOVER : Theme.CARD, hovered ? Theme.SELECTED_BORDER : Theme.CARD_BORDER);
			graphics.item(ItemProtection.iconFor(uuid), x + 5, y + (height - 16) / 2);
			FancyBarsConfig.ProtectedItem info = config.protectedItemInfo.get(uuid);
			Component name = info != null && !info.name.isEmpty()
					? Component.literal(info.name).withColor(info.color & 0xFFFFFF)
					: Component.translatable("fancybars.protection.unknownItem").withColor(Theme.night(Theme.TEXT_MUTED) & 0xFFFFFF);
			Theme.boldInBox(graphics, font, clip(name, w - 100), x + 27, y, height, 0xFFFFFFFF);
			if (ItemProtection.findInInventory(uuid) != null) {
				Component carried = Component.translatable("fancybars.protection.carried");
				Theme.textInBox(graphics, font, carried, remove.getX() - Theme.width(font, carried) - 8, y, height, Theme.TEXT_MUTED);
			}
		}
	}

	/** Buttons for the held item name: move it in the editor, or reset it. */
	private class HeldItemActionsItem extends Item {
		private final ModernButton move;
		private final ModernButton reset;

		HeldItemActionsItem() {
			this.height = 20;
			this.searchable = true;
			this.searchText = "held item name tooltip move position reset";
			move = new ModernButton(10, 20, Component.translatable("fancybars.heldItem.move"), ModernButton.Style.PRIMARY, () -> minecraft.gui.setScreen(new BarEditorScreen(BarsMenuScreen.this)));
			reset = new ModernButton(10, 20, Component.translatable("fancybars.heldItem.reset"), HeldItemName::reset);
			widgets.add(move);
			widgets.add(reset);
		}

		@Override
		void layout(int x, int y, int w) {
			int bw = (w - 6) / 2;
			move.setWidth(bw);
			reset.setWidth(w - 6 - bw);
			move.setPosition(x, y);
			reset.setPosition(x + bw + 6, y);
		}

		@Override
		void render(GuiGraphicsExtractor graphics, int x, int y, int w, int mouseX, int mouseY) {}
	}

	/** Open folder / reload / show all for inventory GIFs. */
	private class GifActionsItem extends Item {
		private final ModernButton edit;
		private final ModernButton open;
		private final ModernButton reload;
		private final ModernButton showAll;

		GifActionsItem() {
			this.height = 20;
			edit = tipFor(new ModernButton(10, 20, Component.translatable("fancybars.gifs.editLayout"), ModernButton.Style.PRIMARY, () -> dev.fancybars.gifs.GifInventory.openEditor(BarsMenuScreen.this)),
					Component.translatable("fancybars.gifs.editLayout.@Tooltip"));
			edit.active = config.gifsEnabled && minecraft.player != null;
			open = tipFor(new ModernButton(10, 20, Component.translatable("fancybars.gifs.openFolder"), dev.fancybars.gifs.GifInventory::openFolder),
					Component.translatable("fancybars.gifs.openFolder.@Tooltip"));
			reload = tipFor(new ModernButton(10, 20, Component.translatable("fancybars.gifs.reload"), () -> {
				dev.fancybars.gifs.GifStore.reload();
				rebuildNextTick = true;
			}), Component.translatable("fancybars.gifs.reload.@Tooltip"));
			showAll = new ModernButton(10, 20, Component.translatable("fancybars.gifs.showAll"), () -> {
				dev.fancybars.gifs.GifInventory.showAll();
				rebuildNextTick = true;
			});
			widgets.add(edit);
			widgets.add(open);
			widgets.add(reload);
			widgets.add(showAll);
		}

		@Override
		void layout(int x, int y, int w) {
			int bw = (w - 18) / 4;
			ModernButton[] buttons = {edit, open, reload, showAll};
			for (int i = 0; i < buttons.length; i++) {
				buttons[i].setWidth(i == buttons.length - 1 ? w - 18 - bw * 3 : bw);
				buttons[i].setPosition(x + i * (bw + 6), y);
			}
		}

		@Override
		void render(GuiGraphicsExtractor graphics, int x, int y, int w, int mouseX, int mouseY) {}
	}

	/** One GIF: animated preview, name, size and a shown switch. */
	private class GifItem extends Item {
		private static final int PREVIEW_W = 52;
		private static final int PREVIEW_H = 36;
		private final dev.fancybars.gifs.GifStore.Gif gif;
		private final ModernToggle shown;

		GifItem(dev.fancybars.gifs.GifStore.Gif gif) {
			this.gif = gif;
			this.height = PREVIEW_H + 8;
			this.searchable = true;
			this.searchText = gif.name + " gif";
			shown = tipFor(new ModernToggle(ModernToggle.SWITCH_WIDTH, ModernToggle.SWITCH_HEIGHT, null, !gif.hidden, value -> {
				gif.hidden = !value;
				dev.fancybars.gifs.GifStore.save();
			}), Component.translatable("fancybars.gifs.shown.@Tooltip"));
			widgets.add(shown);
		}

		@Override
		void layout(int x, int y, int w) {
			shown.setPosition(x + w - ModernToggle.SWITCH_WIDTH - 10, y + (height - ModernToggle.SWITCH_HEIGHT) / 2);
		}

		@Override
		void render(GuiGraphicsExtractor graphics, int x, int y, int w, int mouseX, int mouseY) {
			boolean hovered = isHovered(x, w, mouseX, mouseY);
			Theme.card(graphics, x, y, w, height, Theme.PANEL_RADIUS, hovered ? Theme.CARD_HOVER : Theme.CARD, hovered ? Theme.SELECTED_BORDER : Theme.CARD_BORDER);

			// Preview box (letterboxed to keep the GIF's aspect ratio)
			int bx = x + 4;
			int by = y + 4;
			Theme.roundedRect(graphics, bx, by, PREVIEW_W, PREVIEW_H, 4, 0xFF201318);
			if (gif.ready) {
				float s = Math.min(PREVIEW_W / (float) Math.max(1, gif.srcW), PREVIEW_H / (float) Math.max(1, gif.srcH));
				int pw = Math.max(1, Math.round(gif.srcW * s));
				int ph = Math.max(1, Math.round(gif.srcH * s));
				dev.fancybars.gifs.GifStore.draw(graphics, gif, bx + (PREVIEW_W - pw) / 2, by + (PREVIEW_H - ph) / 2, pw, ph, gif.hidden ? 0.35f : 1f);
			} else {
				Theme.centeredInBox(graphics, font, Component.translatable(gif.error != null ? "fancybars.gifs.failed" : "fancybars.gifs.loading"), bx + PREVIEW_W / 2f, by, PREVIEW_H, gif.error != null ? 0xFFFF7070 : Theme.TEXT_MUTED);
			}
			Theme.roundedOutline(graphics, bx, by, PREVIEW_W, PREVIEW_H, 4, Theme.CARD_BORDER);

			Component name = Component.literal(gif.name.replaceAll("(?i)\\.gif$", ""));
			Theme.boldInBox(graphics, font, clip(name, w - PREVIEW_W - 60), bx + PREVIEW_W + 10, y + 7, 12, gif.hidden ? Theme.TEXT_DISABLED : Theme.TEXT);
			Component details = gif.ready
					? Component.translatable("fancybars.gifs.details", gif.w, gif.h, gif.frameCount(), Math.round(gif.opacity * 100))
					: Component.translatable(gif.error != null ? "fancybars.gifs.failed" : "fancybars.gifs.loading");
			Theme.textInBox(graphics, font, clip(details, w - PREVIEW_W - 60), bx + PREVIEW_W + 10, y + 22, 12, Theme.TEXT_MUTED);
		}

		@Override
		boolean mouseClicked(double mouseX, double mouseY) {
			if (itemHit(this, mouseX, mouseY)) {
				shown.toggle();
				return true;
			}
			return false;
		}
	}

	/** Live preview of the dungeon secrets display. */
	private class SecretsPreviewItem extends Item {
		SecretsPreviewItem() {
			this.height = 40;
		}

		@Override
		void layout(int x, int y, int w) {}

		@Override
		void render(GuiGraphicsExtractor graphics, int x, int y, int w, int mouseX, int mouseY) {
			Theme.card(graphics, x, y, w, height, Theme.PANEL_RADIUS, 0xCC22161B, Theme.CARD_BORDER);
			int[] size = dev.fancybars.dungeons.DungeonSecrets.size(5, 10);
			float px = x + (w - size[0]) / 2f;
			float py = y + (height - size[1]) / 2f;
			graphics.nextStratum();
			dev.fancybars.dungeons.DungeonSecrets.renderAt(graphics, px, py, 1, 5, 10);
		}
	}

	/** Live preview of the rare drop tracker with your real numbers. */
	private class RareDropsPreviewItem extends Item {
		RareDropsPreviewItem() {
			this.height = Math.min(dev.fancybars.dungeons.RareDropTracker.size(false)[1], 140) + 12;
		}

		@Override
		void layout(int x, int y, int w) {}

		@Override
		void render(GuiGraphicsExtractor graphics, int x, int y, int w, int mouseX, int mouseY) {
			Theme.card(graphics, x, y, w, height, Theme.PANEL_RADIUS, 0xCC22161B, Theme.CARD_BORDER);
			int[] size = dev.fancybars.dungeons.RareDropTracker.size(false);
			graphics.nextStratum();
			graphics.enableScissor(x + 2, y + 2, x + w - 2, y + height - 2);
			dev.fancybars.dungeons.RareDropTracker.renderAt(graphics, x + (w - size[0]) / 2f, y + 6, 1, false);
			graphics.disableScissor();
		}
	}

	/** One rare item: icon, name, how many you've had, and whether it's tracked. */
	private class RareDropRowItem extends Item {
		private final dev.fancybars.dungeons.RareDropTracker.RareItem item;
		private final ModernToggle toggle;

		RareDropRowItem(dev.fancybars.dungeons.RareDropTracker.RareItem item) {
			this.item = item;
			this.height = 24;
			this.searchable = true;
			this.searchText = item.name() + " rare drop dungeon";
			this.tooltip = Component.translatable("fancybars.rareDrops.track.@Tooltip");
			toggle = new ModernToggle(ModernToggle.SWITCH_WIDTH, ModernToggle.SWITCH_HEIGHT, null, dev.fancybars.dungeons.RareDropTracker.isTracked(item.name()), value -> {
				if (value) config.rareDropsUntracked.remove(item.name());
				else config.rareDropsUntracked.add(item.name());
				FancyBarsConfig.save();
			});
			widgets.add(toggle);
		}

		@Override
		void layout(int x, int y, int w) {
			toggle.setPosition(x + w - ModernToggle.SWITCH_WIDTH - 10, y + (height - ModernToggle.SWITCH_HEIGHT) / 2);
		}

		@Override
		void render(GuiGraphicsExtractor graphics, int x, int y, int w, int mouseX, int mouseY) {
			boolean hovered = isHovered(x, w, mouseX, mouseY);
			Theme.card(graphics, x, y, w, height, Theme.PANEL_RADIUS, hovered ? Theme.CARD_HOVER : Theme.CARD, hovered ? Theme.SELECTED_BORDER : Theme.CARD_BORDER);
			graphics.item(dev.fancybars.dungeons.RareDropTracker.icon(item.name()), x + 5, y + (height - 16) / 2);
			Theme.boldInBox(graphics, font, clip(Component.literal(item.color() + item.name()), w - 110), x + 27, y, height, 0xFFFFFFFF);
			int count = dev.fancybars.dungeons.RareDropTracker.data().counts.getOrDefault(item.name(), 0);
			Component countText = Component.literal("×" + count);
			Theme.textInBox(graphics, font, countText, toggle.getX() - Theme.width(font, countText) - 10, y, height, count > 0 ? Theme.TEXT : Theme.TEXT_MUTED);
			if (hovered) graphics.requestCursor(CursorTypes.POINTING_HAND);
		}

		@Override
		boolean mouseClicked(double mouseX, double mouseY) {
			if (itemHit(this, mouseX, mouseY)) {
				toggle.toggle();
				return true;
			}
			return false;
		}
	}

	/** Live preview of the action bar with the current look settings. */
	private class ActionBarPreviewItem extends Item {
		ActionBarPreviewItem() {
			this.height = 40;
		}

		@Override
		void layout(int x, int y, int w) {}

		@Override
		void render(GuiGraphicsExtractor graphics, int x, int y, int w, int mouseX, int mouseY) {
			Theme.card(graphics, x, y, w, height, Theme.PANEL_RADIUS, 0xCC22161B, Theme.CARD_BORDER);
			Component text = dev.fancybars.actionbar.ActionBar.current();
			if (text == null || text.getString().isBlank()) text = dev.fancybars.actionbar.ActionBar.sample();
			int[] size = dev.fancybars.actionbar.ActionBar.size(text);
			float scale = Math.min(1, (w - 16) / (float) size[0]);
			graphics.nextStratum();
			graphics.enableScissor(x + 2, y + 2, x + w - 2, y + height - 2);
			dev.fancybars.actionbar.ActionBar.draw(graphics, text, x + w / 2f, y + height / 2f, scale, config.actionBarShown ? 1 : 0.35f);
			graphics.disableScissor();
		}
	}

	/** A labelled button cycling through a fixed set of integer values. */
	private class ValueCycleItem extends Item {
		private final String key;
		private final ModernButton button;

		ValueCycleItem(String key, int[] values, Supplier<Integer> getter, Consumer<Integer> setter, java.util.function.IntFunction<Component> label) {
			this.key = key;
			this.height = 24;
			this.searchable = true;
			this.tooltip = Component.translatable("fancybars.config." + key + ".@Tooltip");
			this.searchText = Component.translatable("fancybars.config." + key).getString() + " " + this.tooltip.getString();
			ModernButton[] holder = new ModernButton[1];
			holder[0] = new ModernButton(Math.max(70, Theme.width(font, label.apply(values[values.length - 1])) + 20), 18, label.apply(getter.get()), () -> {
				int current = getter.get();
				int next = values[0];
				for (int i = 0; i < values.length; i++) {
					if (values[i] == current) {
						next = values[(i + 1) % values.length];
						break;
					}
				}
				setter.accept(next);
				FancyBarsConfig.save();
				holder[0].setMessage(label.apply(next));
			});
			this.button = holder[0];
			widgets.add(button);
		}

		@Override
		void layout(int x, int y, int w) {
			button.setPosition(x + w - button.getWidth() - 8, y + (height - button.getHeight()) / 2);
		}

		@Override
		void render(GuiGraphicsExtractor graphics, int x, int y, int w, int mouseX, int mouseY) {
			boolean hovered = isHovered(x, w, mouseX, mouseY);
			Theme.card(graphics, x, y, w, height, Theme.PANEL_RADIUS, hovered ? Theme.CARD_HOVER : Theme.CARD, hovered ? Theme.SELECTED_BORDER : Theme.CARD_BORDER);
			Theme.boldInBox(graphics, font, clip(Component.translatable("fancybars.config." + key), w - 100), x + 10, y, height, Theme.TEXT);
		}
	}

	/** One screen image: preview, name, on/off and opacity. */
	private class ScreenImageItem extends Item {
		private final dev.fancybars.gifs.ScreenImages.Image image;
		private final ModernToggle toggle;
		private final ModernButton opacity;

		ScreenImageItem(dev.fancybars.gifs.ScreenImages.Image image) {
			this.image = image;
			this.height = 40;
			this.searchable = true;
			this.searchText = image.name + " screen image picture";
			toggle = new ModernToggle(ModernToggle.SWITCH_WIDTH, ModernToggle.SWITCH_HEIGHT, null, image.settings().enabled, value -> {
				image.settings().enabled = value;
				FancyBarsConfig.save();
			});
			ModernButton[] holder = new ModernButton[1];
			holder[0] = new ModernButton(50, 16, Component.literal(Math.round(image.settings().opacity * 100) + "%"), () -> {
				float[] steps = {0.25f, 0.5f, 0.75f, 1f};
				float current = image.settings().opacity;
				float next = steps[0];
				for (int i = 0; i < steps.length; i++) if (Math.abs(steps[i] - current) < 0.01f) next = steps[(i + 1) % steps.length];
				image.settings().opacity = next;
				FancyBarsConfig.save();
				holder[0].setMessage(Component.literal(Math.round(next * 100) + "%"));
			});
			opacity = holder[0];
			tipFor(opacity, Component.translatable("fancybars.screenImages.opacity"));
			widgets.add(toggle);
			widgets.add(opacity);
		}

		@Override
		void layout(int x, int y, int w) {
			toggle.setPosition(x + w - ModernToggle.SWITCH_WIDTH - 10, y + (height - ModernToggle.SWITCH_HEIGHT) / 2);
			opacity.setPosition(toggle.getX() - opacity.getWidth() - 8, y + (height - opacity.getHeight()) / 2);
		}

		@Override
		void render(GuiGraphicsExtractor graphics, int x, int y, int w, int mouseX, int mouseY) {
			Theme.card(graphics, x, y, w, height, Theme.PANEL_RADIUS, Theme.CARD, Theme.CARD_BORDER);
			int box = height - 8;
			Theme.roundedRect(graphics, x + 4, y + 4, box, box, 4, 0xFF111320);
			if (image.ready) {
				float s = Math.min((float) box / image.srcW, (float) box / image.srcH);
				float iw = image.srcW * s;
				float ih = image.srcH * s;
				dev.fancybars.gifs.ScreenImages.draw(graphics, image, x + 4 + (box - iw) / 2, y + 4 + (box - ih) / 2, iw, ih, 1);
			}
			Component name = image.error != null ? Component.translatable("fancybars.gifs.failed") : Component.literal(image.name);
			Theme.boldInBox(graphics, font, clip(name, w - box - 150), x + box + 12, y, height, image.error != null ? 0xFFFF7070 : Theme.TEXT);
		}
	}

	/** Grid of every cape (front side preview) plus None and Custom; click one to wear it. */
	private class CapeGridItem extends Item {
		private static final int CELL_W = 30;
		private static final int CELL_H = 42;
		private static final int GAP = 4;
		private final List<String> ids = new ArrayList<>();
		private int gridX;
		private int columns = 1;

		CapeGridItem() {
			this.searchable = true;
			this.searchText = "cape capes custom migrator minecon vanilla";
			ids.add(dev.fancybars.capes.Capes.NONE);
			ids.add(dev.fancybars.capes.Capes.CUSTOM);
			for (dev.fancybars.capes.Capes.Cape cape : dev.fancybars.capes.Capes.ALL) ids.add(cape.id());
			this.height = CELL_H + 12;
		}

		@Override
		void layout(int x, int y, int w) {
			columns = Math.max(1, (w - 12 + GAP) / (CELL_W + GAP));
			int rows = (ids.size() + columns - 1) / columns;
			int gridW = columns * (CELL_W + GAP) - GAP;
			gridX = x + (w - gridW) / 2;
			this.height = rows * (CELL_H + GAP) - GAP + 12;
		}

		private int indexAt(double mouseX, double mouseY) {
			int col = (int) Math.floor((mouseX - gridX) / (CELL_W + GAP));
			int row = (int) Math.floor((mouseY - y - 6) / (CELL_H + GAP));
			if (col < 0 || col >= columns || row < 0) return -1;
			double inX = mouseX - gridX - col * (CELL_W + GAP);
			double inY = mouseY - y - 6 - row * (CELL_H + GAP);
			if (inX >= CELL_W || inY >= CELL_H) return -1;
			int index = row * columns + col;
			return index < ids.size() ? index : -1;
		}

		private Component label(String id) {
			if (id.equals(dev.fancybars.capes.Capes.NONE)) return Component.translatable("fancybars.capes.none");
			if (id.equals(dev.fancybars.capes.Capes.CUSTOM)) return Component.translatable("fancybars.capes.custom");
			dev.fancybars.capes.Capes.Cape cape = dev.fancybars.capes.Capes.byId(id);
			return Component.literal(cape != null ? cape.name() : id);
		}

		@Override
		void render(GuiGraphicsExtractor graphics, int x, int y, int w, int mouseX, int mouseY) {
			Theme.card(graphics, x, y, w, height, Theme.PANEL_RADIUS, 0xCC141622, Theme.CARD_BORDER);
			int hovered = isHovered(x, w, mouseX, mouseY) ? indexAt(mouseX, mouseY) : -1;
			for (int i = 0; i < ids.size(); i++) {
				String id = ids.get(i);
				int cx = gridX + (i % columns) * (CELL_W + GAP);
				int cy = y + 6 + (i / columns) * (CELL_H + GAP);
				// skip cells scrolled out of view (they also wouldn't need downloading yet)
				if (cy + CELL_H < contentY || cy > contentY + contentH) continue;
				boolean selected = config.cape.equals(id);
				Theme.card(graphics, cx, cy, CELL_W, CELL_H, 4, selected ? Theme.SELECTED : i == hovered ? Theme.SURFACE_HOVER : Theme.SURFACE,
						selected ? Theme.ACCENT : i == hovered ? Theme.SELECTED_BORDER : Theme.CARD_BORDER);
				if (id.equals(dev.fancybars.capes.Capes.NONE)) {
					Theme.centeredInBox(graphics, font, Component.literal("✕"), cx + CELL_W / 2f, cy, CELL_H, Theme.TEXT_MUTED);
				} else {
					dev.fancybars.capes.Capes.Loaded tex = dev.fancybars.capes.Capes.get(id);
					if (tex != null) {
						// front of the cape: 10 x 16 at (1, 1) on a 64 x 32 layout, drawn 2x
						float unit = tex.width() / 64f;
						org.joml.Matrix3x2fStack pose = graphics.pose();
						pose.pushMatrix();
						pose.translate(cx + 5, cy + 5);
						pose.scale(2 / unit, 2 / unit);
						graphics.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, tex.texture(), 0, 0, unit, unit, Math.round(10 * unit), Math.round(16 * unit), tex.width(), tex.height());
						pose.popMatrix();
					} else {
						Theme.centeredInBox(graphics, font, Component.literal(id.equals(dev.fancybars.capes.Capes.CUSTOM) ? "+" : "…"), cx + CELL_W / 2f, cy, CELL_H, Theme.TEXT_MUTED);
					}
				}
				if (i == hovered) {
					tip(id, label(id));
					graphics.requestCursor(CursorTypes.POINTING_HAND);
				}
			}
		}

		@Override
		boolean mouseClicked(double mouseX, double mouseY) {
			int index = indexAt(mouseX, mouseY);
			if (index < 0) return false;
			config.cape = ids.get(index);
			config.capeEnabled = !config.cape.equals(dev.fancybars.capes.Capes.NONE);
			FancyBarsConfig.save();
			rebuildNextTick = true;
			return true;
		}
	}

	/** A labelled slider for a player size axis (25% – 300%). */
	private class SliderItem extends Item {
		private final String key;
		private final Supplier<Float> getter;
		private final dev.fancybars.util.gui.ModernSlider slider;

		private boolean percent = true;

		SliderItem(String key, float min, float max, float step, boolean percent, Supplier<Float> getter, Consumer<Float> setter) {
			this(key, getter, setter, min, max, step);
			this.percent = percent;
		}

		SliderItem(String key, Supplier<Float> getter, Consumer<Float> setter) {
			this(key, getter, setter, dev.fancybars.players.PlayerSizes.MIN, dev.fancybars.players.PlayerSizes.MAX, 0.05f);
		}

		SliderItem(String key, Supplier<Float> getter, Consumer<Float> setter, float min, float max, float step) {
			this.key = key;
			this.getter = getter;
			this.height = 24;
			this.searchable = true;
			this.tooltip = Component.translatable("fancybars.config." + key + ".@Tooltip");
			this.searchText = Component.translatable("fancybars.config." + key).getString() + " size scale";
			slider = new dev.fancybars.util.gui.ModernSlider(130, 16, min, max, step, getter.get(), setter, FancyBarsConfig::save);
			widgets.add(slider);
		}

		@Override
		void layout(int x, int y, int w) {
			slider.setPosition(x + w - slider.getWidth() - 8, y + (height - slider.getHeight()) / 2);
		}

		@Override
		void render(GuiGraphicsExtractor graphics, int x, int y, int w, int mouseX, int mouseY) {
			boolean hovered = isHovered(x, w, mouseX, mouseY);
			Theme.card(graphics, x, y, w, height, Theme.PANEL_RADIUS, hovered ? Theme.CARD_HOVER : Theme.CARD, hovered ? Theme.SELECTED_BORDER : Theme.CARD_BORDER);
			Theme.boldInBox(graphics, font, clip(Component.translatable("fancybars.config." + key), w - 200), x + 10, y, height, Theme.TEXT);
			float v = getter.get();
			Component value = Component.literal(percent ? Math.round(v * 100) + "%" : Math.abs(v) >= 5 ? String.valueOf(Math.round(v)) + "°" : String.format("%.2f", v));
			Theme.textInBox(graphics, font, value, slider.getX() - Theme.width(font, value) - 8, y, height, Theme.TEXT_MUTED);
		}
	}

	/** A labelled colour swatch. */
	private class SwatchItem extends Item {
		private final String key;
		private final ColorSwatch swatch;

		SwatchItem(String key, Supplier<Color> getter, Consumer<Color> setter) {
			this.key = key;
			this.height = 24;
			this.searchable = true;
			this.tooltip = Component.translatable("fancybars.config." + key + ".@Tooltip");
			this.searchText = Component.translatable("fancybars.config." + key).getString() + " colour color";
			swatch = new ColorSwatch(Component.translatable("fancybars.config." + key), getter, color -> {
				setter.accept(color);
				FancyBarsConfig.save();
			});
			widgets.add(swatch);
		}

		@Override
		void layout(int x, int y, int w) {
			swatch.setPosition(x + w - SWATCH - 12, y + (height - SWATCH) / 2);
		}

		@Override
		void render(GuiGraphicsExtractor graphics, int x, int y, int w, int mouseX, int mouseY) {
			boolean hovered = isHovered(x, w, mouseX, mouseY);
			Theme.card(graphics, x, y, w, height, Theme.PANEL_RADIUS, hovered ? Theme.CARD_HOVER : Theme.CARD, hovered ? Theme.SELECTED_BORDER : Theme.CARD_BORDER);
			Theme.boldInBox(graphics, font, clip(Component.translatable("fancybars.config." + key), w - 60), x + 10, y, height, Theme.TEXT);
		}
	}

	/** A row of equally wide buttons. */
	private class ButtonRowItem extends Item {
		private final ModernButton[] buttons;

		ButtonRowItem(ModernButton... buttons) {
			this.buttons = buttons;
			this.height = 20;
			for (ModernButton button : buttons) widgets.add(button);
		}

		@Override
		void layout(int x, int y, int w) {
			int gap = 6;
			int bw = (w - gap * (buttons.length - 1)) / buttons.length;
			for (int i = 0; i < buttons.length; i++) {
				buttons[i].setWidth(i == buttons.length - 1 ? w - (bw + gap) * (buttons.length - 1) : bw);
				buttons[i].setPosition(x + i * (bw + gap), y);
			}
		}

		@Override
		void render(GuiGraphicsExtractor graphics, int x, int y, int w, int mouseX, int mouseY) {}
	}

	private Component clip(Component text, int maxWidth) {
		if (Theme.width(font, text) <= maxWidth) return text;
		String s = text.getString();
		while (!s.isEmpty() && Theme.width(font, Component.literal(s + "…")) > maxWidth) s = s.substring(0, s.length() - 1);
		return Component.literal(s.stripTrailing() + "…");
	}

	// ================================================================ Small widgets / popups

	/** Smooth rounded colour square that opens the colour picker. */
	private class ColorSwatch extends AbstractWidget {
		private final Supplier<Color> getter;
		private final Consumer<Color> setter;
		private float hover = 0;
		private long last = Util.getMillis();

		ColorSwatch(Component label, Supplier<Color> getter, Consumer<Color> setter) {
			super(0, 0, SWATCH, SWATCH, label);
			this.getter = getter;
			this.setter = setter;
			widgetTips.put(this, label);
		}

		@Override
		protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
			hover = Theme.approach(hover, isHoveredOrFocused() ? 1 : 0, 8, last);
			last = Util.getMillis();
			if (hover > 0.01f) Theme.glow(graphics, getX(), getY(), getWidth(), getHeight(), 4, hover * 0.6f);
			Theme.roundedRect(graphics, getX(), getY(), getWidth(), getHeight(), 4, getter.get().getRGB() | 0xFF000000);
			Theme.roundedOutline(graphics, getX(), getY(), getWidth(), getHeight(), 4, Theme.lerpColor(0x66FFFFFF, Theme.ACCENT_LIGHT, hover));
			if (isHovered()) graphics.requestCursor(CursorTypes.POINTING_HAND);
		}

		@Override
		public void onClick(MouseButtonEvent click, boolean doubled) {
			minecraft.gui.setScreen(new EditBarColorPopup(getMessage(), BarsMenuScreen.this, color -> {
				setter.accept(color);
				bars.saveBarConfig();
			}, getter.get().getRGB()));
		}

		@Override
		protected void updateWidgetNarration(NarrationElementOutput output) {
			output.add(NarratedElementType.TITLE, getMessage());
		}
	}

	/** Themed yes/no confirmation popup. */
	private static class ConfirmPopup extends Screen {
		private final Screen background;
		private final Component message;
		private final Runnable onConfirm;
		private int px;
		private int py;
		private int pw;
		private int ph;

		ConfirmPopup(Screen background, Component title, Component message, Runnable onConfirm) {
			super(title);
			this.background = background;
			this.message = message;
			this.onConfirm = onConfirm;
		}

		@Override
		protected void init() {
			super.init();
			background.resize(width, height);
			pw = 230;
			int lines = font.split(Theme.styled(message), pw - 28).size();
			ph = 62 + lines * 11;
			px = (width - pw) / 2;
			py = (height - ph) / 2;
			ModernButton no = new ModernButton(90, 22, Component.translatable("gui.no"), this::onClose);
			ModernButton yes = new ModernButton(90, 22, Component.translatable("gui.yes"), ModernButton.Style.PRIMARY, () -> {
				onConfirm.run();
				onClose();
			});
			no.setPosition(width / 2 - 94, py + ph - 32);
			yes.setPosition(width / 2 + 4, py + ph - 32);
			addRenderableWidget(no);
			addRenderableWidget(yes);
		}

		@Override
		public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
			background.extractBackground(graphics, -1, -1, a);
			graphics.nextStratum();
			background.extractRenderState(graphics, -1, -1, a);
			graphics.nextStratum();
			graphics.fill(0, 0, width, height, 0x80000000);
		}

		@Override
		public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
			Theme.panel(graphics, px, py, pw, ph);
			Theme.titleInBox(graphics, font, getTitle(), px + 14, py + 8, 18, Theme.TEXT);
			graphics.textWithWordWrap(font, Theme.styled(message), px + 14, py + 32, pw - 28, Theme.TEXT_SOFT, false);
			super.extractRenderState(graphics, mouseX, mouseY, a);
		}

		@Override
		public void onClose() {
			minecraft.gui.setScreen(background);
		}

		@Override
		public boolean isPauseScreen() {
			return false;
		}
	}
}
