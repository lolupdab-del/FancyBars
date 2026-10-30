package dev.fancybars.util.gui;

import java.util.function.Consumer;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.cursor.CursorTypes;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;

/**
 * Minimal smooth search box: type to filter, Backspace to delete, Ctrl+Backspace / Esc-free clear via the ✕.
 */
public class SearchField extends AbstractWidget {
	private static final int MAX_LENGTH = 40;

	private String text;
	private final Consumer<String> onChange;
	private final Component placeholder;
	private float hover = 0;
	private long lastFrame = Util.getMillis();

	public SearchField(int width, int height, String initial, Component placeholder, Consumer<String> onChange) {
		super(0, 0, width, height, placeholder);
		this.text = initial;
		this.placeholder = placeholder;
		this.onChange = onChange;
	}

	public String getText() {
		return text;
	}

	@Override
	protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		boolean focused = isFocused();
		hover = Theme.approach(hover, isHovered() || focused ? 1 : 0, 8, lastFrame);
		lastFrame = Util.getMillis();
		Font font = Theme.font();

		int border = focused ? Theme.ACCENT : Theme.lerpColor(Theme.CARD_BORDER, Theme.SELECTED_BORDER, hover);
		Theme.card(graphics, getX(), getY(), getWidth(), getHeight(), Theme.RADIUS, Theme.lerpColor(0xFF1F2131, 0xFF24273A, hover), border);

		int textX = getX() + 8;
		if (text.isEmpty() && !focused) {
			Theme.textInBox(graphics, font, placeholder, textX, getY(), getHeight(), Theme.TEXT_MUTED);
		} else {
			Component shown = Component.literal(text);
			Theme.textInBox(graphics, font, shown, textX, getY(), getHeight(), Theme.TEXT);
			if (focused && (Util.getMillis() / 500) % 2 == 0) {
				int caretX = textX + Theme.width(font, shown) + 1;
				int caretTop = Math.round(getY() + (getHeight() - Theme.UI_CAP) / 2f) - 2;
				graphics.fill(caretX, caretTop, caretX + 1, caretTop + Math.round(Theme.UI_CAP) + 4, Theme.ACCENT);
			}
		}
		if (!text.isEmpty()) {
			Theme.centeredInBox(graphics, font, Component.literal("✕"), getRight() - 9, getY(), getHeight(), isClearHovered(mouseX, mouseY) ? Theme.TEXT : Theme.TEXT_MUTED);
		}
		if (isHovered()) graphics.requestCursor(isClearHovered(mouseX, mouseY) ? CursorTypes.POINTING_HAND : CursorTypes.IBEAM);
	}

	private boolean isClearHovered(double mouseX, double mouseY) {
		return !text.isEmpty() && mouseX >= getRight() - 16 && mouseX < getRight() && mouseY >= getY() && mouseY < getBottom();
	}

	@Override
	public void onClick(MouseButtonEvent click, boolean doubled) {
		if (isClearHovered(click.x(), click.y())) setText("");
	}

	private void setText(String newText) {
		if (newText.equals(text)) return;
		text = newText;
		onChange.accept(text);
	}

	@Override
	public boolean charTyped(CharacterEvent event) {
		if (!isFocused()) return false;
		String typed = event.codepointAsString();
		if (text.length() < MAX_LENGTH && !typed.isEmpty() && typed.codePointAt(0) >= 32) {
			setText(text + typed);
			return true;
		}
		return false;
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (!isFocused()) return false;
		if (event.key() == InputConstants.KEY_BACKSPACE) {
			if (!text.isEmpty()) {
				boolean word = Minecraft.getInstance().hasControlDown();
				if (word) {
					String trimmed = text.stripTrailing();
					int space = trimmed.lastIndexOf(' ');
					setText(space < 0 ? "" : trimmed.substring(0, space + 1));
				} else {
					setText(text.substring(0, text.length() - 1));
				}
			}
			return true;
		}
		if (event.isPaste()) {
			String clip = Minecraft.getInstance().keyboardHandler.getClipboard().replaceAll("[\\r\\n]", "");
			setText((text + clip).substring(0, Math.min(MAX_LENGTH, text.length() + clip.length())));
			return true;
		}
		return false;
	}

	@Override
	protected void updateWidgetNarration(NarrationElementOutput output) {
		output.add(NarratedElementType.TITLE, Component.literal(text.isEmpty() ? placeholder.getString() : text));
	}
}
