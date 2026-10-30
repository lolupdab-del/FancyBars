package dev.fancybars.bars;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

import com.mojang.blaze3d.platform.cursor.CursorTypes;
import org.joml.Matrix3x2fStack;
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
import dev.fancybars.config.FancyBarsSettingsScreen;
import dev.fancybars.util.gui.ModernButton;
import dev.fancybars.util.gui.ModernToggle;
import dev.fancybars.util.gui.Theme;

/**
 * The main Fancy Bars menu: a centred panel listing every bar with a switch for the bar and its icon,
 * colour swatches, and a button to open the free-form layout editor.
 */
public class BarsMenuScreen extends Screen {
	private static final int PANEL_WIDTH = 310;
	private static final int PADDING = 14;
	private static final int ROW_HEIGHT = 18;
	private static final int SWATCH = 12;

	// Column x offsets (relative to the panel's inner left edge)
	private static final int COL_PREVIEW = 4;
	private static final int COL_NAME = 70;
	private static final int COL_SHOWN = 146;
	private static final int COL_ICON = 180;
	private static final int COL_COLORS = 218;

	private final @Nullable Screen parent;
	private final FancyStatusBars bars = FancyStatusBars.INSTANCE;

	private int panelX;
	private int panelY;
	private int panelHeight;
	private int listTop;
	private final List<StatusBar> rows = new ArrayList<>();
	private long openedAt = -1;

	public BarsMenuScreen(@Nullable Screen parent) {
		super(Component.translatable("fancybars.menu.title"));
		this.parent = parent;
	}

	public @Nullable Screen getParent() {
		return parent;
	}

	@Override
	protected void init() {
		super.init();
		bars.updatePositions(false);

		rows.clear();
		for (StatusBarType type : StatusBarType.values()) {
			StatusBar bar = bars.statusBars.get(type);
			if (bar != null) rows.add(bar);
		}

		if (openedAt < 0) openedAt = Util.getMillis();
		int headerHeight = 36;
		int globalsHeight = 42;
		int columnsHeight = 14;
		int footerHeight = 30;
		panelHeight = headerHeight + globalsHeight + 8 + columnsHeight + rows.size() * ROW_HEIGHT + 6 + footerHeight;
		panelX = (width - PANEL_WIDTH) / 2;
		panelY = Math.max(4, (height - panelHeight) / 2);
		int innerX = panelX + PADDING;
		int innerWidth = PANEL_WIDTH - PADDING * 2;

		// Global switches
		FancyBarsConfig config = FancyBarsConfig.get();
		int globalsY = panelY + headerHeight;
		int globalWidth = (innerWidth - 4) / 2;
		addGlobal(innerX, globalsY, globalWidth, "enableBars", config.enableBars, value -> config.enableBars = value);
		addGlobal(innerX + globalWidth + 4, globalsY, globalWidth, "segmentedBars", config.segmentedBars, value -> config.segmentedBars = value);
		addGlobal(innerX, globalsY + 21, globalWidth, "enableBarsRift", config.enableBarsRift, value -> config.enableBarsRift = value);
		addGlobal(innerX + globalWidth + 4, globalsY + 21, globalWidth, "enableVanillaStyleManaBar", config.enableVanillaStyleManaBar, value -> config.enableVanillaStyleManaBar = value);

		// Bar rows
		listTop = globalsY + globalsHeight + 8 + columnsHeight;
		for (int i = 0; i < rows.size(); i++) {
			StatusBar bar = rows.get(i);
			int y = listTop + i * ROW_HEIGHT;
			int switchY = y + (ROW_HEIGHT - ModernToggle.SWITCH_HEIGHT) / 2;

			ModernToggle shown = new ModernToggle(ModernToggle.SWITCH_WIDTH, ModernToggle.SWITCH_HEIGHT, null, bar.enabled, value -> {
				bars.setBarEnabled(bar, value);
				rebuildNextTick = true;
			});
			shown.setPosition(innerX + COL_SHOWN, switchY);
			shown.setTooltip(Tooltip.create(Component.translatable("fancybars.menu.shown.@Tooltip")));
			addRenderableWidget(shown);

			ModernToggle icon = new ModernToggle(ModernToggle.SWITCH_WIDTH, ModernToggle.SWITCH_HEIGHT, null, bar.getIconPosition() != StatusBar.IconPosition.OFF, value -> {
				bar.setIconPosition(value ? StatusBar.IconPosition.LEFT : StatusBar.IconPosition.OFF);
				bars.saveBarConfig();
			});
			icon.setPosition(innerX + COL_ICON, switchY);
			icon.setTooltip(Tooltip.create(Component.translatable("fancybars.menu.icon.@Tooltip")));
			icon.active = bar.enabled;
			addRenderableWidget(icon);

			int swatchY = y + (ROW_HEIGHT - SWATCH) / 2;
			int sx = innerX + COL_COLORS;
			addRenderableWidget(new ColorSwatch(sx, swatchY, Component.translatable("fancybars.menu.barColor"), () -> bar.getColors()[0], color -> bar.getColors()[0] = color));
			sx += SWATCH + 6;
			if (bar.hasOverflow()) {
				String key = bar.getType() == StatusBarType.HEALTH ? "fancybars.menu.absorptionColor" : bar.getType() == StatusBarType.INTELLIGENCE ? "fancybars.menu.overflowManaColor" : "fancybars.bars.config.overflowColor";
				addRenderableWidget(new ColorSwatch(sx, swatchY, Component.translatable(key), () -> bar.getColors()[1], color -> bar.getColors()[1] = color));
			}
			sx += SWATCH + 6;
			addRenderableWidget(new ColorSwatch(sx, swatchY, Component.translatable("fancybars.bars.config.textColor"), () -> bar.getTextColor() != null ? bar.getTextColor() : bar.getColors()[0], bar::setTextColor));
		}

		// Footer
		int footerY = panelY + panelHeight - footerHeight + 4;
		int buttonWidth = (innerWidth - 12) / 4;
		ModernButton edit = new ModernButton(buttonWidth, 20, Component.translatable("fancybars.menu.editGui"), ModernButton.Style.PRIMARY, () -> minecraft.gui.setScreen(new BarEditorScreen(this)));
		edit.setTooltip(Tooltip.create(Component.translatable("fancybars.menu.editGui.@Tooltip")));
		ModernButton settings = new ModernButton(buttonWidth, 20, Component.translatable("fancybars.menu.moreSettings"), () -> minecraft.gui.setScreen(new FancyBarsSettingsScreen(this)));
		ModernButton reset = new ModernButton(buttonWidth, 20, Component.translatable("fancybars.menu.resetLayout"), () -> minecraft.gui.setScreen(new ConfirmPopup(this,
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
		ModernButton done = new ModernButton(buttonWidth, 20, Component.translatable("gui.done"), this::onClose);
		int bx = innerX;
		for (ModernButton button : new ModernButton[]{edit, settings, reset, done}) {
			button.setPosition(bx, footerY);
			addRenderableWidget(button);
			bx += buttonWidth + 4;
		}
	}

	private void addGlobal(int x, int y, int w, String key, boolean initial, Consumer<Boolean> setter) {
		ModernToggle toggle = new ModernToggle(w, 19, Component.translatable("fancybars.menu.global." + key), initial, value -> {
			setter.accept(value);
			FancyBarsConfig.save();
		});
		toggle.setPosition(x, y);
		toggle.setTooltip(Tooltip.create(Component.translatable("fancybars.config." + key + ".@Tooltip")));
		addRenderableWidget(toggle);
	}

	private boolean rebuildNextTick = false;

	@Override
	public void tick() {
		super.tick();
		if (rebuildNextTick) {
			rebuildNextTick = false;
			rebuildWidgets();
		}
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		Theme.backdrop(graphics, width, height);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		Matrix3x2fStack pose = graphics.pose();
		pose.pushMatrix();
		pose.translate(0, Theme.openOffset(openedAt));

		int innerX = panelX + PADDING;
		int innerRight = panelX + PANEL_WIDTH - PADDING;

		Theme.panel(graphics, panelX, panelY, PANEL_WIDTH, panelHeight);

		// Header: glowing accent pill, big title, subtitle, version badge
		Theme.glow(graphics, innerX, panelY + 12, 4, 20, 2, 1);
		Theme.roundedGradient(graphics, innerX, panelY + 12, 4, 20, 2, Theme.ACCENT_LIGHT, Theme.ACCENT_DEEP);
		Theme.bigText(graphics, font, getTitle(), innerX + 10, panelY + 11, 1.5f, Theme.TEXT);
		Theme.text(graphics, font, Component.translatable("fancybars.menu.subtitle"), innerX + 10, panelY + 26, Theme.TEXT_MUTED);
		Component badge = Component.literal("v" + FancyBarsMod.VERSION);
		int badgeWidth = Theme.width(font, badge) + 10;
		Theme.roundedRect(graphics, innerRight - badgeWidth, panelY + 13, badgeWidth, 13, 6, 0x337CC8FF);
		Theme.roundedOutline(graphics, innerRight - badgeWidth, panelY + 13, badgeWidth, 13, 6, 0x887CC8FF);
		Theme.centeredText(graphics, font, badge, innerRight - badgeWidth / 2, panelY + 16, Theme.ACCENT_LIGHT);

		// Column headers
		int columnsY = listTop - 12;
		graphics.fill(innerX, columnsY - 6, innerRight, columnsY - 5, Theme.DIVIDER);
		Theme.text(graphics, font, Component.translatable("fancybars.menu.header.bar"), innerX + COL_PREVIEW, columnsY, Theme.TEXT_MUTED);
		Theme.centeredText(graphics, font, Component.translatable("fancybars.menu.header.shown"), innerX + COL_SHOWN + ModernToggle.SWITCH_WIDTH / 2, columnsY, Theme.TEXT_MUTED);
		Theme.centeredText(graphics, font, Component.translatable("fancybars.menu.header.icon"), innerX + COL_ICON + ModernToggle.SWITCH_WIDTH / 2, columnsY, Theme.TEXT_MUTED);
		Theme.text(graphics, font, Component.translatable("fancybars.menu.header.colors"), innerX + COL_COLORS, columnsY, Theme.TEXT_MUTED);

		// Rows: cards with hover highlight + accent strip, preview, name
		int adjustedMouseY = mouseY - Math.round(Theme.openOffset(openedAt));
		for (int i = 0; i < rows.size(); i++) {
			StatusBar bar = rows.get(i);
			int y = listTop + i * ROW_HEIGHT;
			boolean hovered = mouseX >= innerX - 4 && mouseX < innerRight + 4 && adjustedMouseY >= y && adjustedMouseY < y + ROW_HEIGHT - 1;
			Theme.roundedRect(graphics, innerX - 4, y, innerRight - innerX + 8, ROW_HEIGHT - 2, 5, hovered ? Theme.CARD_HOVER : Theme.CARD);
			if (hovered) Theme.roundedRect(graphics, innerX - 4, y + 3, 2, ROW_HEIGHT - 8, 1, Theme.ACCENT);

			bar.extractPreview(graphics, innerX + COL_PREVIEW, y + (ROW_HEIGHT - 2 - 9) / 2, 60);
			Theme.text(graphics, font, bar.getName(), innerX + COL_NAME, y + (ROW_HEIGHT - 2 - 8) / 2, bar.enabled ? (hovered ? Theme.ACCENT_LIGHT : Theme.TEXT) : Theme.TEXT_DISABLED);
		}

		// Footer divider
		graphics.fill(innerX, panelY + panelHeight - 32, innerRight, panelY + panelHeight - 31, Theme.DIVIDER);

		super.extractRenderState(graphics, mouseX, adjustedMouseY, a);
		pose.popMatrix();
	}

	@Override
	public void removed() {
		super.removed();
		bars.updatePositions(false);
		bars.saveBarConfig();
	}

	@Override
	public void onClose() {
		minecraft.gui.setScreen(parent);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	/**
	 * A small rounded colour square that opens the colour picker when clicked.
	 */
	private class ColorSwatch extends AbstractWidget {
		private final Supplier<Color> getter;
		private final Consumer<Color> setter;

		ColorSwatch(int x, int y, Component label, Supplier<Color> getter, Consumer<Color> setter) {
			super(x, y, SWATCH, SWATCH, label);
			this.getter = getter;
			this.setter = setter;
			setTooltip(Tooltip.create(label));
		}

		@Override
		protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
			boolean hovered = isHoveredOrFocused();
			if (hovered) Theme.glow(graphics, getX() - 1, getY() - 1, getWidth() + 2, getHeight() + 2, 7, 1);
			Theme.roundedRect(graphics, getX() - 1, getY() - 1, getWidth() + 2, getHeight() + 2, 7, hovered ? Theme.ACCENT_LIGHT : 0x60FFFFFF);
			int color = getter.get().getRGB() | 0xFF000000;
			Theme.roundedGradient(graphics, getX(), getY(), getWidth(), getHeight(), 6, Theme.lerpColor(color, 0xFFFFFFFF, 0.25f), color);
			if (hovered) graphics.requestCursor(CursorTypes.POINTING_HAND);
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

	/**
	 * Themed yes/no confirmation popup.
	 */
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
			pw = 220;
			int lines = font.split(Theme.styled(message), pw - 24).size();
			ph = 50 + lines * 10;
			px = (width - pw) / 2;
			py = (height - ph) / 2;
			ModernButton no = new ModernButton(80, 20, Component.translatable("gui.no"), this::onClose);
			ModernButton yes = new ModernButton(80, 20, Component.translatable("gui.yes"), ModernButton.Style.PRIMARY, () -> {
				onConfirm.run();
				onClose();
			});
			no.setPosition(width / 2 - 84, py + ph - 26);
			yes.setPosition(width / 2 + 4, py + ph - 26);
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
			Theme.text(graphics, font, getTitle(), px + 12, py + 10, Theme.ACCENT_LIGHT);
			graphics.textWithWordWrap(font, Theme.styled(message), px + 12, py + 24, pw - 24, Theme.TEXT, false);
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
