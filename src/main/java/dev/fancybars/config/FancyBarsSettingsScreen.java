package dev.fancybars.config;

import java.util.function.Consumer;

import org.jspecify.annotations.Nullable;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import dev.fancybars.bars.BarEditorScreen;
import dev.fancybars.util.EnumUtils;
import dev.fancybars.util.gui.ModernButton;
import dev.fancybars.util.gui.ModernToggle;
import dev.fancybars.util.gui.Theme;

/**
 * Extra settings (the less common options from Skyblocker's "Fancy Bars" config), in the Fancy Bars theme.
 */
public class FancyBarsSettingsScreen extends Screen {
	private static final int PANEL_WIDTH = 280;
	private static final int ROW = 20;

	private final @Nullable Screen parent;
	private int panelX;
	private int panelY;
	private int panelHeight;
	private int sectionY;

	public FancyBarsSettingsScreen(@Nullable Screen parent) {
		super(Component.translatable("fancybars.config.title"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		super.init();
		FancyBarsConfig config = FancyBarsConfig.get();

		int rows = 7;
		panelHeight = 34 + rows * ROW + 16 + 30;
		panelX = (width - PANEL_WIDTH) / 2;
		panelY = Math.max(4, (height - panelHeight) / 2);
		int x = panelX + 12;
		int w = PANEL_WIDTH - 24;
		int y = panelY + 32;

		y = toggle(x, y, w, "riftHealthHP", config.riftHealthHP, value -> config.riftHealthHP = value);
		y = toggle(x, y, w, "showEstimatedTilde", config.showEstimatedTilde, value -> config.showEstimatedTilde = value);
		y = toggle(x, y, w, "hideMana", config.hideMana, value -> config.hideMana = value);

		ModernButton[] intelligence = new ModernButton[1];
		intelligence[0] = new ModernButton(w, 18, intelligenceLabel(config), () -> {
			config.intelligenceDisplay = EnumUtils.cycle(config.intelligenceDisplay);
			FancyBarsConfig.save();
			intelligence[0].setMessage(intelligenceLabel(config));
		});
		intelligence[0].setTooltip(Tooltip.create(Component.translatable("fancybars.config.intelligenceDisplay.@Tooltip")));
		intelligence[0].setPosition(x, y);
		addRenderableWidget(intelligence[0]);
		y += ROW;

		sectionY = y + 4;
		y += 16;
		y = toggle(x, y, w, "enableVanillaStyleManaBar", config.enableVanillaStyleManaBar, value -> config.enableVanillaStyleManaBar = value);
		y = toggle(x, y, w, "enableVanillaStyleManaBarBlinking", config.enableVanillaStyleManaBarBlinking, value -> config.enableVanillaStyleManaBarBlinking = value);
		y = toggle(x, y, w, "useHungerBarSprites", config.useHungerBarSprites, value -> config.useHungerBarSprites = value);

		int footerY = panelY + panelHeight - 26;
		ModernButton edit = new ModernButton((w - 4) / 2, 18, Component.translatable("fancybars.menu.editGui"), () -> minecraft.gui.setScreen(new BarEditorScreen(this)));
		ModernButton done = new ModernButton((w - 4) / 2, 18, Component.translatable("gui.done"), ModernButton.Style.PRIMARY, this::onClose);
		edit.setPosition(x, footerY);
		done.setPosition(x + (w - 4) / 2 + 4, footerY);
		addRenderableWidget(edit);
		addRenderableWidget(done);
	}

	private static Component intelligenceLabel(FancyBarsConfig config) {
		return Component.translatable("fancybars.config.intelligenceDisplay").append(": ").append(Component.translatable(config.intelligenceDisplay.translationKey()));
	}

	private int toggle(int x, int y, int w, String key, boolean initial, Consumer<Boolean> setter) {
		ModernToggle toggle = new ModernToggle(w, 18, Component.translatable("fancybars.config." + key), initial, value -> {
			setter.accept(value);
			FancyBarsConfig.save();
		});
		toggle.setTooltip(Tooltip.create(Component.translatable("fancybars.config." + key + ".@Tooltip")));
		toggle.setPosition(x, y);
		addRenderableWidget(toggle);
		return y + ROW;
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		graphics.fill(0, 0, width, height, Theme.BACKDROP);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		int innerX = panelX + 12;
		int innerRight = panelX + PANEL_WIDTH - 12;
		Theme.panel(graphics, panelX, panelY, PANEL_WIDTH, panelHeight);
		Theme.roundedRect(graphics, innerX, panelY + 10, 3, 12, 1, Theme.ACCENT);
		Theme.text(graphics, font, getTitle(), innerX + 8, panelY + 12, Theme.TEXT);
		graphics.fill(innerX, panelY + 26, innerRight, panelY + 27, Theme.DIVIDER);
		Theme.text(graphics, font, Component.translatable("fancybars.config.section.vanillaMana"), innerX, sectionY + 2, Theme.TEXT_MUTED);
		graphics.fill(innerX, panelY + panelHeight - 32, innerRight, panelY + panelHeight - 31, Theme.DIVIDER);
		super.extractRenderState(graphics, mouseX, mouseY, a);
	}

	@Override
	public void removed() {
		super.removed();
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
}
