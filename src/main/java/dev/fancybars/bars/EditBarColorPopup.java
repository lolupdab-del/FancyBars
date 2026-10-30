package dev.fancybars.bars;

import java.awt.Color;
import java.util.function.Consumer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.layouts.FrameLayout;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;

import dev.fancybars.util.gui.ARGBTextInput;
import dev.fancybars.util.gui.AbstractPopupScreen;
import dev.fancybars.util.gui.ColorPickerWidget;
import dev.fancybars.util.gui.ModernButton;
import dev.fancybars.util.gui.Theme;

public class EditBarColorPopup extends AbstractPopupScreen {

	private final Consumer<Color> setColor;
	private int currentColor = -1;

	private LinearLayout layout = LinearLayout.vertical();

	public EditBarColorPopup(Component title, Screen backgroundScreen, Consumer<Color> setColor, int initialColor) {
		super(title, backgroundScreen);
		this.setColor = setColor;
		this.currentColor = initialColor;
	}

	@Override
	protected void init() {
		super.init();
		layout = LinearLayout.vertical();
		layout.spacing(8).defaultCellSetting().alignHorizontallyCenter();
		layout.addChild(new StringWidget(title.copy().withStyle(Style.EMPTY.withColor(Theme.ACCENT & 0xFFFFFF)), Minecraft.getInstance().font));

		LinearLayout colorLayout = layout.addChild(LinearLayout.horizontal().spacing(4));
		ColorPickerWidget colorPicker = new ColorPickerWidget(0, 0, 200, 100);
		ARGBTextInput argb = new ARGBTextInput(0, 0, font, true, false);
		colorPicker.setARGBColor(currentColor);
		argb.setARGBColor(currentColor);

		argb.setOnChange(color -> {
			colorPicker.setARGBColor(color);
			currentColor = color;
		});
		colorPicker.setOnColorChange((color, _) -> {
			argb.setARGBColor(color);
			currentColor = color;
		});

		colorLayout.addChild(colorPicker);
		colorLayout.addChild(argb);

		LinearLayout horizontal = LinearLayout.horizontal();
		horizontal.spacing(4);
		horizontal.addChild(new ModernButton(80, 18, Component.translatable("gui.cancel"), this::onClose));
		horizontal.addChild(new ModernButton(80, 18, Component.translatable("gui.done"), ModernButton.Style.PRIMARY, () -> {
			setColor.accept(new Color(currentColor));
			onClose();
		}));

		layout.addChild(horizontal);
		layout.visitWidgets(this::addRenderableWidget);
		this.layout.arrangeElements();
		FrameLayout.centerInRectangle(layout, this.getRectangle());
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		super.extractBackground(graphics, mouseX, mouseY, a);
		Theme.panel(graphics, layout.getX() - 12, layout.getY() - 10, layout.getWidth() + 24, layout.getHeight() + 20);
	}
}
