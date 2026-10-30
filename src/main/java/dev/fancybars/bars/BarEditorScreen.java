package dev.fancybars.bars;

import java.util.ArrayList;
import java.util.List;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.cursor.CursorTypes;
import org.joml.Matrix3x2fStack;
import org.jspecify.annotations.Nullable;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import dev.fancybars.config.FancyBarsConfig;
import dev.fancybars.util.EnumUtils;
import dev.fancybars.util.gui.ModernButton;
import dev.fancybars.util.gui.ModernToggle;
import dev.fancybars.util.gui.Theme;

/**
 * Free-form bar editor.
 * <ul>
 *     <li>Drag a bar to move it anywhere (snaps to other bars, the hotbar and the screen centre; hold Shift to disable snapping)</li>
 *     <li>Drag its left or right edge to make it longer or shorter</li>
 *     <li>Scroll over it to scale it up or down</li>
 *     <li>Right-click it for more options</li>
 * </ul>
 */
public class BarEditorScreen extends Screen {
	private static final Identifier HOTBAR_TEXTURE = Identifier.withDefaultNamespace("hud/hotbar");
	private static final int HOTBAR_WIDTH = 182;
	private static final int MIN_WIDTH = 20;
	private static final int EDGE_GRAB = 3;
	private static final int SNAP_DISTANCE = 4;

	private final @Nullable Screen parent;
	private final FancyStatusBars bars = FancyStatusBars.INSTANCE;

	private @Nullable StatusBar dragging;
	private DragMode dragMode = DragMode.NONE;
	private int grabOffsetX;
	private int grabOffsetY;
	private int dragStartRight;
	private boolean pendingDetach = false;

	// Other movable HUD elements (held item name, dungeon secrets)
	private @Nullable MovableElement draggingElement = null;
	private float elementGrabX;
	private float elementGrabY;

	private @Nullable StatusBar optionsBar;
	private int optionsX;
	private int optionsY;
	private int optionsWidth;
	private int optionsHeight;
	private final List<AbstractWidget> optionWidgets = new ArrayList<>();

	private final List<int[]> guides = new ArrayList<>(); // {vertical ? 1 : 0, position}
	/** Done / Reset Layout / Snapping: hidden while something is being dragged. */
	private final List<AbstractWidget> toolbar = new ArrayList<>();
	private boolean rebuildNextTick = false;

	public BarEditorScreen(@Nullable Screen parent) {
		super(Component.translatable("fancybars.editor.title"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		super.init();
		bars.updatePositions(true);

		int toolbarY = 6;
		toolbar.clear();
		ModernButton done = new ModernButton(56, 18, Component.translatable("gui.done"), ModernButton.Style.PRIMARY, this::onClose);
		ModernButton reset = new ModernButton(80, 18, Component.translatable("fancybars.menu.resetLayout"), () -> {
			bars.resetBarPositions();
			bars.statusBars.values().forEach(bar -> {
				bar.enabled = true;
				bar.scale = 1;
			});
			bars.updatePositions(true);
			closeOptions();
		});
		done.setPosition(width / 2 + 2, toolbarY);
		reset.setPosition(width / 2 - 2 - reset.getWidth(), toolbarY);
		// Snapping switch in the top-right corner
		ModernToggle snapping = new ModernToggle(92, 18, Component.translatable("fancybars.editor.snapping"), FancyBarsConfig.get().editorSnapping, value -> {
			FancyBarsConfig.get().editorSnapping = value;
			FancyBarsConfig.save();
		});
		snapping.setPosition(width - snapping.getWidth() - 6, toolbarY);
		toolbar.add(addRenderableWidget(done));
		toolbar.add(addRenderableWidget(reset));
		toolbar.add(addRenderableWidget(snapping));

		if (optionsBar != null) buildOptions(optionsBar);
	}

	// ---------------------------------------------------------------- Rendering

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		// Light dim so the world (and the real HUD position) stays visible
		graphics.fill(0, 0, width, height, 0x40000000);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		// Toolbar buttons disappear while dragging so they never get in the way
		boolean busy = dragging != null || draggingElement != null;
		for (AbstractWidget widget : toolbar) widget.visible = !busy;

		// Hotbar for reference
		graphics.blitSprite(RenderPipelines.GUI_TEXTURED, HOTBAR_TEXTURE, width / 2 - HOTBAR_WIDTH / 2, height - 22, HOTBAR_WIDTH, 22);

		// Bars
		StatusBar hovered = dragging != null ? dragging : barAt(mouseX, mouseY);
		for (StatusBar bar : bars.statusBars.values()) {
			if (!bar.enabled) continue;
			bar.extractBar(graphics);
		}
		for (StatusBar bar : bars.statusBars.values()) {
			if (!bar.enabled) continue;
			bar.extractText(graphics);
		}

		// Other HUD elements (held item name, dungeon secrets)
		extractElements(graphics, mouseX, mouseY, hovered == null && dragging == null);

		// Selection outline + resize handles
		for (StatusBar bar : bars.statusBars.values()) {
			if (!bar.enabled) continue;
			boolean active = bar == hovered || bar == optionsBar;
			if (!active) continue;
			Theme.glow(graphics, bar.getX() - 2, bar.getY() - 2, bar.getWidth() + 4, bar.getHeight() + 4, 4, 0.7f);
			Theme.roundedOutline(graphics, bar.getX() - 3, bar.getY() - 3, bar.getWidth() + 6, bar.getHeight() + 6, 4, Theme.ACCENT);
			int handleHeight = Math.max(7, bar.getHeight() + 2);
			int handleY = bar.getY() + (bar.getHeight() - handleHeight) / 2;
			Theme.roundedGradient(graphics, bar.getX() - 6, handleY, 4, handleHeight, 2, Theme.ACCENT_LIGHT, Theme.ACCENT_DEEP);
			Theme.roundedGradient(graphics, bar.getX() + bar.getWidth() + 2, handleY, 4, handleHeight, 2, Theme.ACCENT_LIGHT, Theme.ACCENT_DEEP);
		}

		// Snap guides
		for (int[] guide : guides) {
			if (guide[0] == 1) graphics.fillGradient(guide[1], 0, guide[1] + 1, height, 0x20FF8FC8, 0xC0FF8FC8);
			else graphics.fill(0, guide[1], width, guide[1] + 1, 0xA0FF8FC8);
		}

		// Size / scale readout while editing
		if (hovered != null && optionsBar == null) {
			Component info = Component.translatable("fancybars.editor.info", hovered.getName(), hovered.getWidth(), Math.round(hovered.effectiveScale() * 100));
			int infoWidth = Theme.width(font, info) + 12;
			int infoX = Math.clamp(hovered.getX() + hovered.getWidth() / 2 - infoWidth / 2, 2, width - infoWidth - 2);
			int infoY = hovered.getY() - 18 < 48 ? hovered.getY() + hovered.getHeight() + 5 : hovered.getY() - 18;
			Theme.shadow(graphics, infoX, infoY, infoWidth, 14, 7, 4, 2);
			Theme.roundedRect(graphics, infoX - 1, infoY - 1, infoWidth + 2, 16, 8, 0x88FF8FC8);
			Theme.roundedGradient(graphics, infoX, infoY, infoWidth, 14, 7, Theme.PANEL_TOP, Theme.PANEL_BOTTOM);
			Theme.textInBox(graphics, font, info, infoX + 6, infoY, 14, Theme.TEXT);
		}

		// Cursor feedback
		if (dragging != null) {
			graphics.requestCursor(dragMode == DragMode.MOVE ? CursorTypes.RESIZE_ALL : CursorTypes.RESIZE_EW);
		} else if (hovered != null && !isOverOptions(mouseX, mouseY)) {
			graphics.requestCursor(edgeAt(hovered, mouseX) != DragMode.MOVE ? CursorTypes.RESIZE_EW : CursorTypes.RESIZE_ALL);
		}

		// Options panel background (widgets render on top via super)
		if (optionsBar != null) {
			Theme.panel(graphics, optionsX, optionsY, optionsWidth, optionsHeight);
			Theme.boldInBox(graphics, font, optionsBar.getName(), optionsX + 8, optionsY + 3, 16, Theme.ACCENT_LIGHT);
			graphics.fill(optionsX + 6, optionsY + 19, optionsX + optionsWidth - 6, optionsY + 20, Theme.DIVIDER);
		}

		super.extractRenderState(graphics, mouseX, mouseY, a);
	}

	private static boolean isOver(float[] r, double mouseX, double mouseY) {
		return mouseX >= r[0] - 3 && mouseX <= r[0] + r[2] + 3 && mouseY >= r[1] - 2 && mouseY <= r[1] + r[3] + 2;
	}

	private @Nullable MovableElement elementAt(double mouseX, double mouseY) {
		for (MovableElement element : MovableElement.ALL) {
			if (isOver(element.rect(width, height), mouseX, mouseY)) return element;
		}
		return null;
	}

	private void extractElements(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean canHover) {
		MovableElement hoveredElement = draggingElement != null ? draggingElement : canHover ? elementAt(mouseX, mouseY) : null;
		for (MovableElement element : MovableElement.ALL) {
			element.drawPreview(graphics, width, height);
		}
		if (hoveredElement == null) return;
		float[] r = hoveredElement.rect(width, height);
		int x = Math.round(r[0]) - 4;
		int y = Math.round(r[1]) - 3;
		int w = Math.round(r[2]) + 8;
		int h = Math.round(r[3]) + 6;
		Theme.glow(graphics, x, y, w, h, 4, 0.6f);
		Theme.roundedOutline(graphics, x, y, w, h, 4, Theme.ACCENT);
		Component label = Component.translatable(hoveredElement.enabled() ? "fancybars.editor.elementLabel" : "fancybars.editor.elementLabelHidden",
				hoveredElement.name(), Math.round(hoveredElement.scale() * 100));
		int lw = Theme.width(font, label) + 12;
		int lx = Math.clamp(x + w / 2 - lw / 2, 2, width - lw - 2);
		int ly = y - 18 < 48 ? y + h + 4 : y - 18;
		Theme.card(graphics, lx, ly, lw, 14, 7, Theme.PANEL, 0x88FF8FC8);
		Theme.textInBox(graphics, font, label, lx + 6, ly, 14, Theme.TEXT);
		graphics.requestCursor(draggingElement != null ? CursorTypes.RESIZE_ALL : CursorTypes.POINTING_HAND);
	}

	private static void outline(GuiGraphicsExtractor graphics, int x, int y, int w, int h, int color) {
		graphics.fill(x + 1, y, x + w - 1, y + 1, color);
		graphics.fill(x + 1, y + h - 1, x + w - 1, y + h, color);
		graphics.fill(x, y + 1, x + 1, y + h - 1, color);
		graphics.fill(x + w - 1, y + 1, x + w, y + h - 1, color);
	}

	// ---------------------------------------------------------------- Hit testing

	private @Nullable StatusBar barAt(double mouseX, double mouseY) {
		StatusBar found = null;
		for (StatusBar bar : bars.statusBars.values()) {
			if (!bar.enabled) continue;
			if (mouseX >= bar.getX() - EDGE_GRAB && mouseX <= bar.getX() + bar.getWidth() + EDGE_GRAB
					&& mouseY >= bar.getY() - 1 && mouseY <= bar.getY() + bar.getHeight() + 1) {
				found = bar; // last one wins = drawn on top
			}
		}
		return found;
	}

	private static DragMode edgeAt(StatusBar bar, double mouseX) {
		if (mouseX <= bar.getX() + EDGE_GRAB - 1) return DragMode.RESIZE_LEFT;
		if (mouseX >= bar.getX() + bar.getWidth() - EDGE_GRAB + 1) return DragMode.RESIZE_RIGHT;
		return DragMode.MOVE;
	}

	private boolean isOverOptions(double mouseX, double mouseY) {
		return optionsBar != null && mouseX >= optionsX && mouseX < optionsX + optionsWidth && mouseY >= optionsY && mouseY < optionsY + optionsHeight;
	}

	// ---------------------------------------------------------------- Input

	@Override
	public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
		if (super.mouseClicked(click, doubled)) return true;
		if (isOverOptions(click.x(), click.y())) return true;

		StatusBar bar = barAt(click.x(), click.y());
		MovableElement element = bar == null ? elementAt(click.x(), click.y()) : null;
		if (element != null) {
			if (click.button() == InputConstants.MOUSE_BUTTON_RIGHT) {
				element.setEnabled(!element.enabled());
				FancyBarsConfig.save();
				return true;
			}
			if (click.button() == InputConstants.MOUSE_BUTTON_MIDDLE) {
				element.reset();
				FancyBarsConfig.save();
				return true;
			}
			if (click.button() == InputConstants.MOUSE_BUTTON_LEFT) {
				closeOptions();
				float[] r = element.rect(width, height);
				draggingElement = element;
				elementGrabX = (float) click.x() - r[0];
				elementGrabY = (float) click.y() - r[1];
				return true;
			}
		}
		if (click.button() == InputConstants.MOUSE_BUTTON_RIGHT) {
			if (bar != null) openOptions(bar, (int) click.x(), (int) click.y());
			else closeOptions();
			return true;
		}
		if (click.button() != InputConstants.MOUSE_BUTTON_LEFT) return false;

		closeOptions();
		if (bar == null) return false;

		DragMode mode = edgeAt(bar, click.x());
		// Bars are only unsnapped from the hotbar once they actually get dragged
		pendingDetach = true;
		dragging = bar;
		dragMode = mode;
		grabOffsetX = (int) click.x() - bar.getX();
		grabOffsetY = (int) click.y() - bar.getY();
		dragStartRight = bar.getX() + bar.getWidth();
		return true;
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent click, double dx, double dy) {
		if (draggingElement != null) {
			float[] r = draggingElement.rect(width, height);
			float x = (float) click.x() - elementGrabX;
			float y = (float) click.y() - elementGrabY;
			// snap to the screen edges / centre, the bars and the other elements (unless snapping is off or Shift is held)
			guides.clear();
			if (snapping()) {
				x = snapX(draggingElement, Math.round(x), Math.round(r[2]));
				y = snapY(draggingElement, Math.round(y), Math.round(r[3]));
			}
			draggingElement.moveTo(x, y, width, height);
			return true;
		}
		if (dragging == null) return super.mouseDragged(click, dx, dy);
		StatusBar bar = dragging;
		if (pendingDetach) {
			pendingDetach = false;
			bars.detach(bar);
		}
		boolean snap = snapping();
		guides.clear();
		int mouseX = (int) click.x();
		int mouseY = (int) click.y();

		switch (dragMode) {
			case MOVE -> {
				int x = mouseX - grabOffsetX;
				int y = mouseY - grabOffsetY;
				if (snap) {
					x = snapX(bar, x, bar.getWidth());
					y = snapY(bar, y, bar.getHeight());
				}
				x = Math.clamp(x, 0, Math.max(0, width - bar.getWidth()));
				y = Math.clamp(y, 0, Math.max(0, height - bar.getHeight()));
				bars.setFreeBounds(bar, x, y, bar.getWidth());
			}
			case RESIZE_RIGHT -> {
				int right = mouseX;
				if (snap) right = snapEdge(bar, right);
				int newWidth = Math.clamp(right - bar.getX(), minWidth(bar), width - bar.getX());
				bars.setFreeBounds(bar, bar.getX(), bar.getY(), newWidth);
			}
			case RESIZE_LEFT -> {
				int left = mouseX;
				if (snap) left = snapEdge(bar, left);
				left = Math.clamp(left, 0, dragStartRight - minWidth(bar));
				bars.setFreeBounds(bar, left, bar.getY(), dragStartRight - left);
			}
			case NONE -> {}
		}
		return true;
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent click) {
		if (draggingElement != null) {
			draggingElement = null;
			guides.clear();
			FancyBarsConfig.save();
			return true;
		}
		if (dragging != null) {
			pendingDetach = false;
			dragging = null;
			dragMode = DragMode.NONE;
			guides.clear();
			bars.updatePositions(true);
			return true;
		}
		return super.mouseReleased(click);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
		if (isOverOptions(mouseX, mouseY)) return super.mouseScrolled(mouseX, mouseY, horizontal, vertical);
		StatusBar bar = barAt(mouseX, mouseY);
		float step = 1f / Theme.guiScale(); // keeps every texture pixel a whole number of screen pixels
		MovableElement element = bar == null && vertical != 0 ? elementAt(mouseX, mouseY) : null;
		if (element != null) {
			// scale around the element's centre
			float[] before = element.rect(width, height);
			float next = StatusBar.snapScale(element.scale() + (vertical > 0 ? step : -step));
			element.setScale(Math.clamp(next, MovableElement.MIN_SCALE, MovableElement.MAX_SCALE));
			float[] after = element.rect(width, height);
			element.moveTo(before[0] + before[2] / 2f - after[2] / 2f, before[1] + before[3] / 2f - after[3] / 2f, width, height);
			FancyBarsConfig.save();
			return true;
		}
		if (bar == null || vertical == 0) return super.mouseScrolled(mouseX, mouseY, horizontal, vertical);

		bars.detach(bar);
		float oldScale = bar.effectiveScale();
		float newScale = Math.clamp(StatusBar.snapScale(oldScale + (vertical > 0 ? step : -step)), StatusBar.MIN_SCALE, StatusBar.MAX_SCALE);
		if (newScale == oldScale) return true;

		// Scale around the mouse so the bar grows "towards" the cursor naturally
		int oldX = bar.getX();
		int oldY = bar.getY();
		float ratio = newScale / oldScale;
		int newWidth = Math.max(minWidth(bar), Math.round(bar.getWidth() * ratio));
		int newX = (int) Math.round(mouseX - (mouseX - oldX) * ratio);
		int newY = (int) Math.round(mouseY - (mouseY - oldY) * ratio);
		bar.scale = newScale;
		int newHeight = bar.getHeight();
		newWidth = Math.min(newWidth, width);
		newX = Math.clamp(newX, 0, width - newWidth);
		newY = Math.clamp(newY, 0, Math.max(0, height - newHeight));
		bars.setFreeBounds(bar, newX, newY, newWidth);
		if (optionsBar == bar) requestRebuild();
		return true;
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (event.key() == InputConstants.KEY_ESCAPE && optionsBar != null) {
			closeOptions();
			return true;
		}
		return super.keyPressed(event);
	}

	private static int minWidth(StatusBar bar) {
		return Math.round(MIN_WIDTH * bar.effectiveScale());
	}

	// ---------------------------------------------------------------- Snapping

	/** Snapping is on when the toolbar switch is on and Shift isn't held. */
	private boolean snapping() {
		return FancyBarsConfig.get().editorSnapping && !minecraft.hasShiftDown();
	}

	/** Edges of the movable HUD elements other than {@code self}: {x0, y0, x1, y1}. */
	private List<int[]> elementEdges(Object self) {
		List<int[]> edges = new ArrayList<>();
		for (MovableElement element : MovableElement.ALL) {
			if (element == self || !element.enabled()) continue;
			float[] r = element.rect(width, height);
			edges.add(new int[]{Math.round(r[0]), Math.round(r[1]), Math.round(r[0] + r[2]), Math.round(r[1] + r[3])});
		}
		return edges;
	}

	private List<int[]> snapTargetsX(Object self) {
		List<int[]> targets = new ArrayList<>();
		targets.add(new int[]{0});
		targets.add(new int[]{width});
		for (int[] e : elementEdges(self)) {
			targets.add(new int[]{e[0]});
			targets.add(new int[]{e[2]});
			targets.add(new int[]{(e[0] + e[2]) / 2});
		}
		targets.add(new int[]{width / 2});
		targets.add(new int[]{width / 2 - HOTBAR_WIDTH / 2});
		targets.add(new int[]{width / 2 + HOTBAR_WIDTH / 2});
		for (StatusBar other : bars.statusBars.values()) {
			if (other == self || !other.enabled) continue;
			targets.add(new int[]{other.getX()});
			targets.add(new int[]{other.getX() + other.getWidth()});
		}
		return targets;
	}

	private List<int[]> snapTargetsY(Object self) {
		List<int[]> targets = new ArrayList<>();
		targets.add(new int[]{0});
		targets.add(new int[]{height});
		targets.add(new int[]{height / 2});
		for (int[] e : elementEdges(self)) {
			targets.add(new int[]{e[1]});
			targets.add(new int[]{e[3]});
		}
		targets.add(new int[]{height - 22});
		for (StatusBar other : bars.statusBars.values()) {
			if (other == self || !other.enabled) continue;
			targets.add(new int[]{other.getY()});
			targets.add(new int[]{other.getY() + other.getHeight()});
			targets.add(new int[]{other.getY() - 1});
			targets.add(new int[]{other.getY() + other.getHeight() + 1});
		}
		return targets;
	}

	private int snapX(Object bar, int x, int w) {
		int best = x;
		int bestDist = SNAP_DISTANCE + 1;
		int guide = -1;
		for (int[] t : snapTargetsX(bar)) {
			int target = t[0];
			// left edge, right edge, centre
			int[][] candidates = {{target, target}, {target - w, target}, {target - w / 2, target}};
			for (int[] c : candidates) {
				int dist = Math.abs(c[0] - x);
				if (dist < bestDist) {
					bestDist = dist;
					best = c[0];
					guide = c[1];
				}
			}
		}
		if (guide >= 0) guides.add(new int[]{1, guide});
		return best;
	}

	private int snapY(Object bar, int y, int h) {
		int best = y;
		int bestDist = SNAP_DISTANCE + 1;
		int guide = -1;
		for (int[] t : snapTargetsY(bar)) {
			int target = t[0];
			int[][] candidates = {{target, target}, {target - h, target}, {target - h / 2, target}};
			for (int[] c : candidates) {
				int dist = Math.abs(c[0] - y);
				if (dist < bestDist) {
					bestDist = dist;
					best = c[0];
					guide = c[1];
				}
			}
		}
		if (guide >= 0) guides.add(new int[]{0, guide});
		return best;
	}

	private int snapEdge(StatusBar bar, int edge) {
		for (int[] t : snapTargetsX(bar)) {
			if (Math.abs(t[0] - edge) <= SNAP_DISTANCE) {
				guides.add(new int[]{1, t[0]});
				return t[0];
			}
		}
		return edge;
	}

	// ---------------------------------------------------------------- Options panel

	private void openOptions(StatusBar bar, int x, int y) {
		optionsBar = bar;
		optionsX = x;
		optionsY = y;
		rebuildWidgets();
	}

	private void closeOptions() {
		if (optionsBar == null) return;
		optionsBar = null;
		rebuildWidgets();
	}

	private void buildOptions(StatusBar bar) {
		optionWidgets.clear();
		int w = 150;
		int rowH = 16;

		optionWidgets.add(cycle(w, rowH, "fancybars.bars.config.icon", () -> bar.getIconPosition().toString(), () -> bar.setIconPosition(EnumUtils.cycle(bar.getIconPosition()))));
		optionWidgets.add(cycle(w, rowH, "fancybars.bars.config.text", () -> bar.getTextPosition().toString(), () -> bar.setTextPosition(EnumUtils.cycle(bar.getTextPosition()))));
		optionWidgets.add(cycle(w, rowH, "fancybars.bars.config.direction", () -> bar.getDirection().toString(), () -> bar.setDirection(EnumUtils.cycle(bar.getDirection()))));
		if (bar.hasMax()) optionWidgets.add(new ModernToggle(w, rowH, Component.translatable("fancybars.bars.config.showMax"), bar.showMax, value -> bar.showMax = value));
		if (bar.hasOverflow()) optionWidgets.add(new ModernToggle(w, rowH, Component.translatable("fancybars.bars.config.showOverflow"), bar.showOverflow, value -> bar.showOverflow = value));
		optionWidgets.add(new ModernButton(w, rowH, Component.translatable("fancybars.editor.resetScale"), () -> {
			if (bar.anchor == null && bar.scale != 1) {
				int newWidth = Math.max(MIN_WIDTH, Math.round(bar.getWidth() / bar.scale));
				bar.scale = 1;
				bars.setFreeBounds(bar, bar.getX(), bar.getY(), newWidth);
			}
		}));
		optionWidgets.add(new ModernButton(w, rowH, Component.translatable("fancybars.editor.snapToHotbar"), () -> {
			bars.snapToDefault(bar);
			closeOptions();
		}));
		optionWidgets.add(new ModernButton(w, rowH, Component.translatable("fancybars.bars.config.hide"), () -> {
			bars.setBarEnabled(bar, false);
			bars.updatePositions(true);
			closeOptions();
		}));

		optionsWidth = w + 12;
		optionsHeight = 26 + optionWidgets.size() * (rowH + 3) + 3;
		optionsX = Math.clamp(optionsX, 2, width - optionsWidth - 2);
		optionsY = Math.clamp(optionsY, 2, height - optionsHeight - 2);
		int y = optionsY + 24;
		for (AbstractWidget widget : optionWidgets) {
			widget.setPosition(optionsX + 6, y);
			y += rowH + 3;
			addRenderableWidget(widget);
		}
	}

	private ModernButton cycle(int w, int h, String key, java.util.function.Supplier<String> value, Runnable cycle) {
		ModernButton[] holder = new ModernButton[1];
		holder[0] = new ModernButton(w, h, label(key, value.get()), () -> {
			cycle.run();
			holder[0].setMessage(label(key, value.get()));
		});
		return holder[0];
	}

	private static Component label(String key, String value) {
		return Component.translatable(key).append(": ").append(Component.literal(value));
	}

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

	// ---------------------------------------------------------------- Lifecycle

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

	private enum DragMode {
		NONE,
		MOVE,
		RESIZE_LEFT,
		RESIZE_RIGHT
	}
}
