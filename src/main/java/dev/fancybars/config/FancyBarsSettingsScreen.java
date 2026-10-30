package dev.fancybars.config;

import java.util.function.Consumer;

import org.jspecify.annotations.Nullable;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.layouts.GridLayout;
import net.minecraft.client.gui.layouts.HeaderAndFooterLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

import dev.fancybars.bars.FancyStatusBars;
import dev.fancybars.bars.StatusBarsConfigScreen;

/**
 * Vanilla-widget replacement for the "Fancy Bars" category of Skyblocker's YACL config.
 */
public class FancyBarsSettingsScreen extends Screen {
	private static final int WIDE = Button.DEFAULT_WIDTH * 2 + 2;

	private final @Nullable Screen parent;
	private final HeaderAndFooterLayout layout = new HeaderAndFooterLayout(this);

	public FancyBarsSettingsScreen(@Nullable Screen parent) {
		super(Component.translatable("fancybars.config.title"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		super.init();
		FancyBarsConfig config = FancyBarsConfig.get();
		GridLayout grid = new GridLayout().spacing(4);
		layout.addToContents(grid);
		GridLayout.RowHelper adder = grid.createRowHelper(2);

		adder.addChild(wideToggle("enableBars", config.enableBars, value -> config.enableBars = value), 2);
		adder.addChild(toggle("enableBarsRift", config.enableBarsRift, value -> config.enableBarsRift = value));
		adder.addChild(toggle("riftHealthHP", config.riftHealthHP, value -> config.riftHealthHP = value));
		adder.addChild(CycleButton.builder(display -> Component.translatable(display.translationKey()), config.intelligenceDisplay)
				.withValues(FancyBarsConfig.IntelligenceDisplay.values())
				.withTooltip(_ -> Tooltip.create(Component.translatable("fancybars.config.intelligenceDisplay.@Tooltip")))
				.create(Component.translatable("fancybars.config.intelligenceDisplay"), (_, value) -> {
					config.intelligenceDisplay = value;
					FancyBarsConfig.save();
				}));
		adder.addChild(toggle("showEstimatedTilde", config.showEstimatedTilde, value -> config.showEstimatedTilde = value));
		adder.addChild(wideToggle("hideMana", config.hideMana, value -> config.hideMana = value), 2);
		adder.addChild(wideToggle("enableVanillaStyleManaBar", config.enableVanillaStyleManaBar, value -> config.enableVanillaStyleManaBar = value), 2);
		adder.addChild(toggle("enableVanillaStyleManaBarBlinking", config.enableVanillaStyleManaBarBlinking, value -> config.enableVanillaStyleManaBarBlinking = value));
		adder.addChild(toggle("useHungerBarSprites", config.useHungerBarSprites, value -> config.useHungerBarSprites = value));
		adder.addChild(Button.builder(Component.translatable("fancybars.config.openEditor"), _ -> minecraft.gui.setScreen(new StatusBarsConfigScreen(FancyStatusBars.INSTANCE)))
				.width(WIDE)
				.build(), 2);

		layout.addTitleHeader(getTitle(), font);
		layout.addToFooter(Button.builder(CommonComponents.GUI_DONE, _ -> onClose()).build());
		repositionElements();
		layout.visitWidgets(this::addRenderableWidget);
	}

	private static CycleButton<Boolean> toggle(String key, boolean initial, Consumer<Boolean> setter) {
		return toggle(key, initial, setter, Button.DEFAULT_WIDTH);
	}

	private static CycleButton<Boolean> wideToggle(String key, boolean initial, Consumer<Boolean> setter) {
		return toggle(key, initial, setter, WIDE);
	}

	private static CycleButton<Boolean> toggle(String key, boolean initial, Consumer<Boolean> setter, int width) {
		return CycleButton.booleanBuilder(Component.translatable("options.on"), CommonComponents.OPTION_OFF, initial)
				.withTooltip(_ -> Tooltip.create(Component.translatable("fancybars.config." + key + ".@Tooltip")))
				.create(0, 0, width, Button.DEFAULT_HEIGHT, Component.translatable("fancybars.config." + key), (_, value) -> {
					setter.accept(value);
					FancyBarsConfig.save();
				});
	}

	@Override
	protected void repositionElements() {
		layout.arrangeElements();
	}

	@Override
	public void onClose() {
		FancyBarsConfig.save();
		minecraft.gui.setScreen(parent);
	}
}
