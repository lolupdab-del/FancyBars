package dev.fancybars.items;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

import com.mojang.blaze3d.platform.cursor.CursorTypes;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import dev.fancybars.util.gui.ModernButton;
import dev.fancybars.util.gui.SearchField;
import dev.fancybars.util.gui.Theme;

/** A searchable grid of icons to pick one value from (models, heads, dyes...). */
public class GridPickerScreen<T> extends Screen {
	public record Entry<T>(ItemStack icon, Component name, String search, T value) {}

	private static final int CELL = 20;
	private final Screen parent;
	private final List<Entry<T>> all;
	private final Consumer<T> onPick;
	private final List<Entry<T>> filtered = new ArrayList<>();
	private String query = "";
	private float scroll;
	private int px;
	private int py;
	private int pw;
	private int ph;
	private int gridX;
	private int gridY;
	private int cols;
	private int rows;

	public GridPickerScreen(Component title, Screen parent, List<Entry<T>> entries, Consumer<T> onPick) {
		super(title);
		this.parent = parent;
		this.all = entries;
		this.onPick = onPick;
	}

	@Override
	protected void init() {
		pw = Math.min(width - 16, 380);
		ph = Math.min(height - 16, 260);
		px = (width - pw) / 2;
		py = (height - ph) / 2;
		cols = (pw - 24) / CELL;
		gridX = px + (pw - cols * CELL) / 2;
		gridY = py + 46;
		rows = Math.max(1, (py + ph - 34 - gridY) / CELL);
		SearchField search = new SearchField(pw - 24, 16, query, Component.translatable("fancybars.items.search"), text -> {
			query = text;
			refilter();
		});
		search.setPosition(px + 12, py + 24);
		addRenderableWidget(search);
		setInitialFocus(search);
		ModernButton cancel = new ModernButton(80, 18, Component.translatable("gui.cancel"), this::onClose);
		cancel.setPosition(px + (pw - 80) / 2, py + ph - 26);
		addRenderableWidget(cancel);
		refilter();
	}

	private void refilter() {
		filtered.clear();
		String q = query.trim().toLowerCase(Locale.ROOT);
		for (Entry<T> e : all) if (q.isEmpty() || e.search().contains(q)) filtered.add(e);
		scroll = Math.min(scroll, maxScroll());
	}

	private int maxScroll() {
		return Math.max(0, (filtered.size() + cols - 1) / cols - rows);
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		parent.extractBackground(graphics, mouseX, mouseY, a);
		Theme.backdrop(graphics, width, height);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		Theme.panel(graphics, px, py, pw, ph);
		Theme.titleInBox(graphics, font, title, px + 12, py + 4, 18, Theme.TEXT);
		Component count = Component.literal(String.valueOf(filtered.size()));
		Theme.textInBox(graphics, font, count, px + pw - 12 - Theme.width(font, count), py + 4, 18, Theme.TEXT_MUTED);
		graphics.enableScissor(gridX - 1, gridY - 1, gridX + cols * CELL + 1, gridY + rows * CELL + 1);
		int first = Math.round(scroll) * cols;
		for (int i = first; i < Math.min(filtered.size(), first + rows * cols); i++) {
			int idx = i - first;
			int sx = gridX + idx % cols * CELL;
			int sy = gridY + idx / cols * CELL;
			boolean hovered = mouseX >= sx && mouseX < sx + CELL && mouseY >= sy && mouseY < sy + CELL;
			Theme.roundedRect(graphics, sx + 1, sy + 1, CELL - 2, CELL - 2, 3, hovered ? Theme.SURFACE_HOVER : Theme.SURFACE);
			Entry<T> e = filtered.get(i);
			graphics.item(e.icon(), sx + 2, sy + 2);
			if (hovered) {
				graphics.setTooltipForNextFrame(font, e.name(), mouseX, mouseY);
				graphics.requestCursor(CursorTypes.POINTING_HAND);
			}
		}
		graphics.disableScissor();
		int max = maxScroll();
		if (max > 0) {
			int trackH = rows * CELL;
			int thumbH = Math.max(12, trackH * rows / (rows + max));
			int thumbY = gridY + Math.round((trackH - thumbH) * (scroll / max));
			Theme.roundedRect(graphics, gridX + cols * CELL + 3, thumbY, 3, thumbH, 1, Theme.ACCENT);
		}
		super.extractRenderState(graphics, mouseX, mouseY, a);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
		scroll = Math.clamp(scroll - (float) vertical * 2, 0, maxScroll());
		return true;
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
		if (super.mouseClicked(click, doubled)) return true;
		double mx = click.x();
		double my = click.y();
		if (mx >= gridX && mx < gridX + cols * CELL && my >= gridY && my < gridY + rows * CELL) {
			int idx = (Math.round(scroll) + (int) ((my - gridY) / CELL)) * cols + (int) ((mx - gridX) / CELL);
			if (idx < filtered.size()) {
				T value = filtered.get(idx).value();
				minecraft.gui.setScreen(parent);
				onPick.accept(value);
				return true;
			}
		}
		return false;
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
