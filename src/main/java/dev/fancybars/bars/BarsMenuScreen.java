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
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;

import dev.fancybars.FancyBarsMod;
import dev.fancybars.config.FancyBarsConfig;
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
		prev.setTooltip(Tooltip.create(Component.translatable("fancybars.menu.previous")));
		next.setTooltip(Tooltip.create(Component.translatable("fancybars.menu.next")));
		close.setTooltip(Tooltip.create(Component.translatable("gui.done")));
		prev.setPosition(navX, y0 + 6);
		next.setPosition(navX + 22, y0 + 6);
		close.setPosition(navX + 46, y0 + 6);
		addRenderableWidget(prev);
		addRenderableWidget(next);
		addRenderableWidget(close);

		// Sidebar categories
		int sy = y0 + TOP_HEIGHT + GAP + 8;
		for (Category c : Category.values()) {
			SidebarItem item = new SidebarItem(c, SIDEBAR_WIDTH - 16, 20);
			item.setPosition(x0 + 8, sy);
			addRenderableWidget(item);
			sy += 23;
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
				list.add(new HeaderItem(Component.translatable("fancybars.category.bars"), Component.translatable("fancybars.bars.description")));
				for (StatusBarType type : StatusBarType.values()) {
					StatusBar bar = bars.statusBars.get(type);
					if (bar != null) list.add(new BarItem(bar));
				}
			}
			case STYLE -> {
				list.add(new HeaderItem(Component.translatable("fancybars.category.style"), Component.translatable("fancybars.style.description")));
				for (FancyBarsConfig.BarStyle style : FancyBarsConfig.BarStyle.values()) list.add(new StyleItem(style));
				list.add(new ToggleItem("smoothBarText", () -> config.smoothBarText, value -> config.smoothBarText = value));
			}
			case GENERAL -> {
				list.add(new HeaderItem(Component.translatable("fancybars.category.general"), null));
				list.add(new ToggleItem("enableBars", () -> config.enableBars, value -> config.enableBars = value));
				list.add(new ToggleItem("enableBarsRift", () -> config.enableBarsRift, value -> config.enableBarsRift = value));
				list.add(new ToggleItem("riftHealthHP", () -> config.riftHealthHP, value -> config.riftHealthHP = value));
				list.add(new ToggleItem("showEstimatedTilde", () -> config.showEstimatedTilde, value -> config.showEstimatedTilde = value));
				list.add(new ToggleItem("hideMana", () -> config.hideMana, value -> config.hideMana = value));
				list.add(new CycleItem());
			}
			case VANILLA_MANA -> {
				list.add(new HeaderItem(Component.translatable("fancybars.category.vanillaMana"), Component.translatable("fancybars.vanillaMana.description")));
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

		// Title: "Fancy" in light blue, "Bars" in white
		Component fancy = Component.translatable("fancybars.menu.titleAccent");
		Component barsWord = Component.translatable("fancybars.menu.titleRest");
		int fw = Theme.titleWidth(font, fancy);
		int bw = Theme.titleWidth(font, barsWord);
		int tx = x0 + (SIDEBAR_WIDTH - fw - bw) / 2;
		int ty = y0 + (TOP_HEIGHT - 11) / 2;
		Theme.bigText(graphics, font, fancy, tx, ty, 1, Theme.ACCENT);
		Theme.bigText(graphics, font, barsWord, tx + fw, ty, 1, Theme.TEXT);

		// Version at the bottom of the sidebar
		Theme.centeredText(graphics, font, Component.literal("v" + FancyBarsMod.VERSION), x0 + SIDEBAR_WIDTH / 2, y0 + totalHeight - 14, Theme.TEXT_DISABLED);

		// Fixed widgets (search, nav, sidebar)
		super.extractRenderState(graphics, mouseX, my, a);

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

		// Scrollbar
		if (maxScroll() > 0) {
			int trackX = contentX + contentW - 3;
			float visibleFrac = contentH / (float) contentHeight;
			int thumbH = Math.max(16, Math.round(contentH * visibleFrac));
			int thumbY = contentY + Math.round((contentH - thumbH) * (scroll / maxScroll()));
			Theme.roundedRect(graphics, trackX, contentY, 3, contentH, 1, 0x30FFFFFF);
			Theme.roundedRect(graphics, trackX, thumbY, 3, thumbH, 1, 0xB0B4E1FF);
		}

		Theme.popOffset(graphics);
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
			Theme.text(graphics, font, getMessage(), getX() + 8 + Math.round(hover * 2), getY() + (getHeight() - 8) / 2, textColor);
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
			Theme.bigText(graphics, font, title, x + 2, y + 2, 1, Theme.TEXT);
			if (description != null) Theme.text(graphics, font, clip(description, w - 4), x + 2, y + 20, Theme.TEXT_MUTED);
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
			edit.setTooltip(Tooltip.create(Component.translatable("fancybars.menu.editGui.@Tooltip")));
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
			if (accentTitle) {
				Component fancy = Component.translatable("fancybars.menu.titleAccent").append(" ");
				Theme.bigText(graphics, font, fancy, tx, y + 11, 1, Theme.ACCENT);
				tx += Theme.titleWidth(font, fancy);
			}
			Theme.bigText(graphics, font, title, tx, y + 11, 1, Theme.TEXT);
			int ly = y + 32;
			for (Component line : lines) {
				Theme.text(graphics, font, clip(Component.literal("•  ").append(line), w - 28), x + 14, ly, Theme.TEXT_SOFT);
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
			this.height = 34;
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
			Theme.boldText(graphics, font, Component.translatable("fancybars.config." + key), x + 10, y + 7, Theme.TEXT);
			Theme.text(graphics, font, clip(Component.translatable("fancybars.config." + key + ".@Tooltip"), w - 60), x + 10, y + 19, Theme.TEXT_MUTED);
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
			this.height = 34;
			this.searchable = true;
			this.searchText = Component.translatable("fancybars.config.intelligenceDisplay").getString() + " mana overflow";
			ModernButton[] holder = new ModernButton[1];
			holder[0] = new ModernButton(116, 20, Component.translatable(config.intelligenceDisplay.translationKey()), () -> {
				config.intelligenceDisplay = EnumUtils.cycle(config.intelligenceDisplay);
				FancyBarsConfig.save();
				holder[0].setMessage(Component.translatable(config.intelligenceDisplay.translationKey()));
			});
			holder[0].setTooltip(Tooltip.create(Component.translatable("fancybars.config.intelligenceDisplay.@Tooltip")));
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
			Theme.boldText(graphics, font, Component.translatable("fancybars.config.intelligenceDisplay"), x + 10, y + 7, Theme.TEXT);
			Theme.text(graphics, font, clip(Component.translatable("fancybars.config.intelligenceDisplay.short"), w - 140), x + 10, y + 19, Theme.TEXT_MUTED);
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
			shown.setTooltip(Tooltip.create(Component.translatable("fancybars.menu.shown.@Tooltip")));
			icon = new ModernToggle(ModernToggle.SWITCH_WIDTH, ModernToggle.SWITCH_HEIGHT, null, bar.getIconPosition() != StatusBar.IconPosition.OFF, value -> {
				bar.setIconPosition(value ? StatusBar.IconPosition.LEFT : StatusBar.IconPosition.OFF);
				bars.saveBarConfig();
			});
			icon.setTooltip(Tooltip.create(Component.translatable("fancybars.menu.icon.@Tooltip")));
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
			Theme.boldText(graphics, font, bar.getName(), x + 10, y + 7, bar.enabled ? Theme.TEXT : Theme.TEXT_DISABLED);
			Component where = !bar.enabled ? Component.translatable("fancybars.menu.where.hidden")
					: bar.anchor != null ? Component.translatable("fancybars.menu.where.hotbar")
					: Component.translatable("fancybars.menu.where.free", Math.round(bar.scale * 100));
			Theme.text(graphics, font, where, x + 14 + Theme.width(font, Theme.bold(bar.getName())), y + 7, Theme.TEXT_MUTED);
			bar.extractPreview(graphics, x + 10, y + 21, Math.min(110, shown.getX() - x - 24));

			// Column captions above the controls
			int captionY = y + 7;
			Theme.centeredText(graphics, font, Component.translatable("fancybars.menu.header.shown"), shown.getX() + ModernToggle.SWITCH_WIDTH / 2, captionY, Theme.TEXT_MUTED);
			Theme.centeredText(graphics, font, Component.translatable("fancybars.menu.header.icon"), icon.getX() + ModernToggle.SWITCH_WIDTH / 2, captionY, Theme.TEXT_MUTED);
			int colorsLeft = swatches.getFirst().getX();
			int colorsRight = colorsLeft + 3 * SWATCH + 2 * 5;
			Theme.centeredText(graphics, font, Component.translatable("fancybars.menu.header.colors"), (colorsLeft + colorsRight) / 2, captionY, Theme.TEXT_MUTED);
		}
	}

	/** Selectable bar style card with a live preview. */
	private class StyleItem extends Item {
		private final FancyBarsConfig.BarStyle style;

		StyleItem(FancyBarsConfig.BarStyle style) {
			this.style = style;
			this.height = 40;
			this.searchable = true;
			this.searchText = Component.translatable(style.translationKey()).getString() + " style texture bars";
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
			Theme.circle(graphics, rx, ry, 12, selected ? Theme.ACCENT : 0xFF4A4E6B);
			Theme.circle(graphics, rx + 2, ry + 2, 8, selected ? Theme.SELECTED : 0xFF202233);
			if (selected) Theme.circle(graphics, rx + 3, ry + 3, 6, Theme.ACCENT);

			int previewWidth = Math.min(110, w / 3);
			Theme.boldText(graphics, font, Component.translatable(style.translationKey()), x + 30, y + 9, Theme.TEXT);
			Theme.text(graphics, font, clip(Component.translatable(style.translationKey() + ".description"), w - 30 - previewWidth - 20), x + 30, y + 22, Theme.TEXT_MUTED);

			// Live preview of health + mana drawn in this style
			FancyBarsConfig.BarStyle previous = config.barStyle;
			config.barStyle = style;
			StatusBar health = bars.statusBars.get(StatusBarType.HEALTH);
			StatusBar mana = bars.statusBars.get(StatusBarType.INTELLIGENCE);
			int px = x + w - previewWidth - 10;
			if (health != null) health.extractPreview(graphics, px, y + 9, previewWidth);
			if (mana != null) mana.extractPreview(graphics, px, y + 22, previewWidth);
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
			setTooltip(Tooltip.create(label));
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
			Theme.bigText(graphics, font, getTitle(), px + 14, py + 12, 1, Theme.TEXT);
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
