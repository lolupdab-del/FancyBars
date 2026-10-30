package dev.fancybars.bars;

import java.awt.Color;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.jspecify.annotations.Nullable;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.PopupScreen;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.layouts.FrameLayout;
import net.minecraft.client.gui.layouts.GridLayout;
import net.minecraft.client.gui.layouts.LayoutSettings;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.layouts.SpacerElement;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.util.CommonColors;

import dev.fancybars.config.FancyBarsConfig;
import dev.fancybars.config.FancyBarsSettingsScreen;
import dev.fancybars.util.GuiHelper;
import dev.fancybars.util.gui.AbstractPopupScreen;

/**
 * The main Fancy Bars menu: a centred panel listing every bar with toggles for the bar and its icon,
 * size controls, colour swatches, and a button to open the drag-and-drop layout editor.
 */
public class BarsMenuScreen extends Screen {
	private static final int ROW_HEIGHT = 16;
	private static final int COLUMNS = 10;

	private final @Nullable Screen parent;
	private final FancyStatusBars bars = FancyStatusBars.INSTANCE;
	private @Nullable GridLayout grid;
	private boolean rebuildNextTick = false;

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

		GridLayout grid = new GridLayout();
		grid.columnSpacing(4).rowSpacing(3);
		grid.defaultCellSetting().alignVerticallyMiddle();
		this.grid = grid;
		int row = 0;

		// Title
		grid.addChild(new StringWidget(getTitle().copy().withStyle(Style.EMPTY.withBold(true)), font), row++, 0, 1, COLUMNS, LayoutSettings.defaults().alignHorizontallyCenter());

		// Global toggles
		FancyBarsConfig config = FancyBarsConfig.get();
		LinearLayout globals = LinearLayout.horizontal().spacing(4);
		globals.addChild(globalToggle("enableBars", config.enableBars, value -> config.enableBars = value));
		globals.addChild(globalToggle("enableBarsRift", config.enableBarsRift, value -> config.enableBarsRift = value));
		globals.addChild(globalToggle("enableVanillaStyleManaBar", config.enableVanillaStyleManaBar, value -> config.enableVanillaStyleManaBar = value));
		grid.addChild(globals, row++, 0, 1, COLUMNS, LayoutSettings.defaults().alignHorizontallyCenter());

		// Column headers
		grid.addChild(header("bar"), row, 0, 1, 2, LayoutSettings.defaults());
		grid.addChild(header("shown"), row, 2, 1, 1, LayoutSettings.defaults().alignHorizontallyCenter());
		grid.addChild(header("icon"), row, 3, 1, 1, LayoutSettings.defaults().alignHorizontallyCenter());
		grid.addChild(header("size"), row, 4, 1, 3, LayoutSettings.defaults().alignHorizontallyCenter());
		grid.addChild(header("colors"), row, 7, 1, 3, LayoutSettings.defaults().alignHorizontallyCenter());
		row++;

		// One row per bar
		for (StatusBarType type : StatusBarType.values()) {
			StatusBar bar = bars.statusBars.get(type);
			if (bar == null) continue;
			addBarRow(grid, row++, bar);
		}

		// Footer
		LinearLayout footer = LinearLayout.horizontal().spacing(4);
		footer.addChild(Button.builder(Component.translatable("fancybars.menu.editGui"), _ -> minecraft.gui.setScreen(new StatusBarsConfigScreen(bars, this)))
				.width(90)
				.tooltip(Tooltip.create(Component.translatable("fancybars.menu.editGui.@Tooltip")))
				.build());
		footer.addChild(Button.builder(Component.translatable("fancybars.menu.moreSettings"), _ -> minecraft.gui.setScreen(new FancyBarsSettingsScreen(this)))
				.width(90)
				.build());
		footer.addChild(Button.builder(Component.translatable("fancybars.menu.resetLayout"), _ -> minecraft.gui.setScreen(new PopupScreen.Builder(this, Component.translatable("fancybars.bars.config.resetTitle"))
						.addButton(CommonComponents.GUI_NO, PopupScreen::onClose)
						.addButton(CommonComponents.GUI_YES, popup -> {
							bars.resetBarPositions();
							bars.statusBars.values().forEach(bar -> bar.enabled = true);
							bars.updatePositions(false);
							popup.onClose();
						})
						.addMessage(Component.translatable("fancybars.bars.config.reset"))
						.build()))
				.width(70)
				.build());
		footer.addChild(Button.builder(CommonComponents.GUI_DONE, _ -> onClose()).width(60).build());
		grid.addChild(SpacerElement.height(2), row++, 0, 1, COLUMNS, LayoutSettings.defaults());
		grid.addChild(footer, row, 0, 1, COLUMNS, LayoutSettings.defaults().alignHorizontallyCenter());

		grid.arrangeElements();
		FrameLayout.centerInRectangle(grid, getRectangle());
		grid.visitWidgets(this::addRenderableWidget);
	}

	private void addBarRow(GridLayout grid, int row, StatusBar bar) {
		boolean enabled = bar.enabled;
		Color nameColor = bar.getTextColor() != null ? bar.getTextColor() : bar.getColors()[0];
		Component name = bar.getName().copy().withStyle(style -> style.withColor(enabled ? nameColor.getRGB() & 0xFFFFFF : 0x808080));

		// Name + live preview
		grid.addChild(new StringWidget(0, 0, 64, ROW_HEIGHT, name, font), row, 0);
		grid.addChild(new BarPreview(bar), row, 1);

		// Shown toggle
		CycleButton<Boolean> shown = onOffButton(enabled, 36, Component.translatable("fancybars.menu.shown"), value -> {
			bars.setBarEnabled(bar, value);
			requestRebuild();
		});
		shown.setTooltip(Tooltip.create(Component.translatable("fancybars.menu.shown.@Tooltip")));
		grid.addChild(shown, row, 2);

		// Icon toggle
		CycleButton<Boolean> icon = onOffButton(bar.getIconPosition() != StatusBar.IconPosition.OFF, 36, Component.translatable("fancybars.menu.icon"), value -> {
			bar.setIconPosition(value ? StatusBar.IconPosition.LEFT : StatusBar.IconPosition.OFF);
			requestRebuild();
		});
		icon.setTooltip(Tooltip.create(Component.translatable("fancybars.menu.icon.@Tooltip")));
		icon.active = enabled;
		grid.addChild(icon, row, 3);

		// Size controls
		Button smaller = Button.builder(Component.literal("-"), _ -> {
			bars.resizeBar(bar, -1);
			requestRebuild();
		}).size(14, ROW_HEIGHT).tooltip(Tooltip.create(Component.translatable("fancybars.menu.smaller"))).build();
		smaller.active = bars.canResize(bar, -1);
		Button bigger = Button.builder(Component.literal("+"), _ -> {
			bars.resizeBar(bar, 1);
			requestRebuild();
		}).size(14, ROW_HEIGHT).tooltip(Tooltip.create(Component.translatable("fancybars.menu.bigger"))).build();
		bigger.active = bars.canResize(bar, 1);
		grid.addChild(smaller, row, 4);
		grid.addChild(new StringWidget(0, 0, 36, ROW_HEIGHT, sizeText(bar), font), row, 5, 1, 1, LayoutSettings.defaults().alignHorizontallyCenter());
		grid.addChild(bigger, row, 6);

		// Colours
		grid.addChild(new ColorSwatch(Component.translatable("fancybars.bars.config.mainColor"), () -> bar.getColors()[0], color -> bar.getColors()[0] = color), row, 7);
		if (bar.hasOverflow()) {
			String key = bar.getType() == StatusBarType.HEALTH ? "fancybars.menu.absorptionColor" : bar.getType() == StatusBarType.INTELLIGENCE ? "fancybars.menu.overflowManaColor" : "fancybars.bars.config.overflowColor";
			grid.addChild(new ColorSwatch(Component.translatable(key), () -> bar.getColors()[1], color -> bar.getColors()[1] = color), row, 8);
		} else {
			grid.addChild(SpacerElement.width(16), row, 8);
		}
		grid.addChild(new ColorSwatch(Component.translatable("fancybars.bars.config.textColor"), () -> bar.getTextColor() != null ? bar.getTextColor() : bar.getColors()[0], bar::setTextColor), row, 9);
	}

	private Component sizeText(StatusBar bar) {
		if (!bar.enabled) return Component.literal("-").withStyle(ChatFormatting.GRAY);
		if (bar.anchor == null) return Component.literal(bar.getWidth() + "px");
		return Component.literal(String.valueOf(bar.size));
	}

	private StringWidget header(String key) {
		return new StringWidget(Component.translatable("fancybars.menu.header." + key).withStyle(ChatFormatting.GRAY, ChatFormatting.UNDERLINE), font);
	}

	private CycleButton<Boolean> globalToggle(String key, boolean initial, Consumer<Boolean> setter) {
		CycleButton<Boolean> button = CycleButton.booleanBuilder(Component.translatable("options.on").withStyle(ChatFormatting.GREEN), Component.translatable("options.off").withStyle(ChatFormatting.RED), initial)
				.create(0, 0, 112, ROW_HEIGHT, Component.translatable("fancybars.menu.global." + key), (_, value) -> {
					setter.accept(value);
					FancyBarsConfig.save();
				});
		button.setTooltip(Tooltip.create(Component.translatable("fancybars.config." + key + ".@Tooltip")));
		return button;
	}

	private static CycleButton<Boolean> onOffButton(boolean initial, int width, Component narration, Consumer<Boolean> setter) {
		return CycleButton.booleanBuilder(Component.translatable("options.on").withStyle(ChatFormatting.GREEN), Component.translatable("options.off").withStyle(ChatFormatting.RED), initial)
				.displayOnlyValue()
				.create(0, 0, width, ROW_HEIGHT, narration, (_, value) -> setter.accept(value));
	}

	/**
	 * Rebuilding the widgets from inside a click callback would modify the widget list while it is being iterated,
	 * so it's done on the next tick instead.
	 */
	private void requestRebuild() {
		rebuildNextTick = true;
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
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		super.extractBackground(graphics, mouseX, mouseY, a);
		if (grid != null) AbstractPopupScreen.extractPopupBackground(graphics, grid.getX(), grid.getY(), grid.getWidth(), grid.getHeight());
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
	 * Draws a sample of the bar with its current colours and icon setting.
	 */
	private static class BarPreview extends AbstractWidget {
		private final StatusBar bar;

		BarPreview(StatusBar bar) {
			super(0, 0, 60, ROW_HEIGHT, bar.getName());
			this.bar = bar;
			this.active = false;
		}

		@Override
		protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
			bar.extractPreview(graphics, getX(), getY() + (getHeight() - 9) / 2, getWidth());
		}

		@Override
		protected void updateWidgetNarration(NarrationElementOutput builder) {}
	}

	/**
	 * A small coloured square that opens a colour picker when clicked.
	 */
	private class ColorSwatch extends AbstractWidget {
		private final Supplier<Color> getter;
		private final Consumer<Color> setter;

		ColorSwatch(Component label, Supplier<Color> getter, Consumer<Color> setter) {
			super(0, 0, 16, ROW_HEIGHT, label);
			this.getter = getter;
			this.setter = setter;
			setTooltip(Tooltip.create(label));
		}

		@Override
		protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
			int border = isHoveredOrFocused() ? CommonColors.WHITE : CommonColors.BLACK;
			graphics.fill(getX(), getY(), getRight(), getBottom(), border);
			GuiHelper.border(graphics, getX() + 1, getY() + 1, getWidth() - 2, getHeight() - 2, 0xFF404040);
			graphics.fill(getX() + 2, getY() + 2, getRight() - 2, getBottom() - 2, getter.get().getRGB() | 0xFF000000);
		}

		@Override
		public void onClick(MouseButtonEvent click, boolean doubled) {
			minecraft.gui.setScreen(new EditBarColorPopup(getMessage(), BarsMenuScreen.this, color -> {
				setter.accept(color);
				bars.saveBarConfig();
			}, getter.get().getRGB()));
		}

		@Override
		protected void updateWidgetNarration(NarrationElementOutput builder) {}
	}
}
