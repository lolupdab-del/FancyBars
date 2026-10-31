package dev.fancybars.bars;

import java.awt.Color;
import java.util.function.Consumer;
import java.util.function.Function;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.jspecify.annotations.Nullable;

import org.joml.Matrix3x2fStack;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.layouts.LayoutElement;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.util.CommonColors;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.effect.MobEffects;

import dev.fancybars.FancyBarsMod;
import dev.fancybars.config.FancyBarsConfig;
import dev.fancybars.util.GuiHelper;
import dev.fancybars.util.gui.Theme;
import dev.fancybars.util.SkyblockLocation;

public class StatusBar implements LayoutElement, Renderable, GuiEventListener, NarratableEntry {
	private static final Identifier BAR_FILL = FancyBarsMod.id("bars/bar_fill");
	private static final Identifier BAR_BACK = FancyBarsMod.id("bars/bar_back");
	private static final Identifier SEGMENTED_BAR_FILL = FancyBarsMod.id("bars/segmented/bar_fill");
	private static final Identifier SEGMENTED_BAR_BACK = FancyBarsMod.id("bars/segmented/bar_back");

	/** SkyblockAddons bar sheet: empty bar at v=1, fill at v=7, shine overlay at v=13, progress tick at v=24 (71x5 each). */
	private static final Identifier SBA_BARS = FancyBarsMod.id("textures/sba/bars_v2.png");
	private static final int SBA_WIDTH = 71;

	private static Identifier barFill() {
		return FancyBarsConfig.get().barStyle == FancyBarsConfig.BarStyle.SEGMENTED ? SEGMENTED_BAR_FILL : BAR_FILL;
	}

	private static Identifier barBack() {
		return FancyBarsConfig.get().barStyle == FancyBarsConfig.BarStyle.SEGMENTED ? SEGMENTED_BAR_BACK : BAR_BACK;
	}

	private static boolean sbaStyle() {
		return FancyBarsConfig.get().barStyle == FancyBarsConfig.BarStyle.SKYBLOCKADDONS;
	}

	private static final int SBA_CAP = 2;          // rounded end caps (texture x 1-2 and 70-71)
	private static final int SBA_MIDDLE_U = 1 + SBA_CAP;
	private static final int SBA_MIDDLE = SBA_WIDTH - SBA_CAP * 2; // 67px repeating middle

	/**
	 * Draws part of a SkyblockAddons bar layer. Instead of stretching the 71px texture, the end caps are drawn once
	 * and the middle section repeats, so long bars keep the original pixel pattern.
	 *
	 * @param from start of the drawn part, as a fraction of the bar (0..1)
	 * @param to   end of the drawn part, as a fraction of the bar (0..1)
	 */
	private void sbaLayer(GuiGraphicsExtractor graphics, int barX, int y, int barWidth, int v, float from, float to, int argb) {
		if (to <= from || barWidth <= 0) return;
		int clipStart = barX + Math.round(barWidth * from);
		int clipEnd = barX + Math.round(barWidth * to);
		if (clipEnd <= clipStart) return;

		if (barWidth < SBA_CAP * 2 + 1) {
			sbaPiece(graphics, barX, y, 1, barWidth, v, clipStart, clipEnd, argb);
			return;
		}
		// left cap
		sbaPiece(graphics, barX, y, 1, SBA_CAP, v, clipStart, clipEnd, argb);
		// repeating middle
		int middleStart = barX + SBA_CAP;
		int middleEnd = barX + barWidth - SBA_CAP;
		for (int px = middleStart; px < middleEnd; px += SBA_MIDDLE) {
			int w = Math.min(SBA_MIDDLE, middleEnd - px);
			sbaPiece(graphics, px, y, SBA_MIDDLE_U, w, v, clipStart, clipEnd, argb);
		}
		// right cap
		sbaPiece(graphics, middleEnd, y, 1 + SBA_WIDTH - SBA_CAP, SBA_CAP, v, clipStart, clipEnd, argb);
	}

	/**
	 * Draws one 1:1 piece of the SkyblockAddons sheet, clipped horizontally to [clipStart, clipEnd).
	 */
	private void sbaPiece(GuiGraphicsExtractor graphics, int x, int y, int u, int width, int v, int clipStart, int clipEnd, int argb) {
		int start = Math.max(x, clipStart);
		int end = Math.min(x + width, clipEnd);
		if (end <= start) return;
		graphics.blit(RenderPipelines.GUI_TEXTURED, SBA_BARS, start, y, u + (start - x), v, end - start, 5, 80, 50, argb);
	}

	private void sbaOverlays(GuiGraphicsExtractor graphics, int barX, int barWidth) {
		int y = renderY + 2;
		int white = transparency(0xFFFFFFFF);
		// progress tick at the edge of the main fill (only meaningful for left-to-right fills)
		if (direction == Direction.LEFT_TO_RIGHT && fill > 0 && fill < 1) {
			int edge = barX + Math.round(barWidth * fill);
			int start = Math.max(barX, edge - 2);
			int end = Math.min(barX + barWidth, edge + 2);
			if (end > start) graphics.blit(RenderPipelines.GUI_TEXTURED, SBA_BARS, start, y, 1 + (start - (edge - 2)), 24, end - start, 5, 80, 50, white);
		}
		sbaLayer(graphics, barX, y, barWidth, 13, 0, 1, white);
	}

	public static final int ICON_SIZE = 9;

	private final Identifier icon;
	private final StatusBarType type;
	private Color[] colors;
	private @Nullable Color textColor;

	public Color[] getColors() {
		return colors;
	}

	public boolean hasOverflow() {
		return type.hasOverflow();
	}

	public boolean hasMax() {
		return type.hasMax();
	}

	public @Nullable Color getTextColor() {
		return textColor;
	}

	public void setTextColor(@Nullable Color textColor) {
		this.textColor = textColor;
	}

	public Component getName() {
		return type.getName();
	}

	private @Nullable OnClick onClick = null;
	public int gridX = 0;
	public int gridY = 0;
	public float x = 0;
	public float y = 0;
	public float width = 0;
	public BarPositioner.@Nullable BarAnchor anchor = null;

	public int size = 1;

	/**
	 * Scale of free-floating bars (changed with the scroll wheel in the editor). Bars snapped to an anchor always use 1.
	 */
	public float scale = 1;

	public static final float MIN_SCALE = 0.5f;
	public static final float MAX_SCALE = 4f;

	/**
	 * The scale actually used for drawing. It's snapped so every texture pixel covers a whole number of screen pixels
	 * (scale x GUI scale is an integer); otherwise thin details like segment lines come out uneven.
	 */
	public float effectiveScale() {
		if (anchor != null) return 1;
		return snapScale(scale);
	}

	public static float snapScale(float scale) {
		return Math.max(0.1f, Math.round(scale * 100) / 100f);
	}

	public float fill = 0;
	public float overflowFill = 0;
	public boolean inMouse = false;
	/**
	 * Used to hide the bar dynamically, like the oxygen bar
	 */
	public boolean visible = true;
	public boolean enabled = true;

	private @Nullable Integer value = null;
	private @Nullable Integer max = null;
	private @Nullable Integer overflow = null;
	private final Function<Integer, String> toDisplay;

	private int renderX = 0;
	private int renderY = 0;
	private int renderWidth = 0;

	private IconPosition iconPosition = IconPosition.LEFT;
	private TextPosition textPosition = TextPosition.BAR_CENTER;

	public boolean showMax = false;
	public boolean showOverflow = false;
	private Direction direction = Direction.LEFT_TO_RIGHT;

	public StatusBar(StatusBarType type) {
		this(type, String::valueOf);
	}

	public StatusBar(StatusBarType type, Function<Integer, String> toDisplay) {
		this.icon = FancyBarsMod.id("bars/icons/" + type.getSerializedName());
		this.colors = type.getColors();
		this.textColor = type.getTextColor();
		this.type = type;
		this.toDisplay = toDisplay;
	}

	protected int transparency(int color) {
		if (inMouse) return (color & 0x00FFFFFF) | 0x44_000000;
		return color;
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		extractBar(graphics);
		if (enabled) extractText(graphics);
	}

	protected Identifier getIcon() {
		return icon;
	}

	@SuppressWarnings("incomplete-switch")
	public void extractBar(GuiGraphicsExtractor graphics) {
		float s = effectiveScale();
		if (s != 1) {
			withScale(graphics, s, () -> extractBarUnscaled(graphics));
		} else extractBarUnscaled(graphics);
	}

	/**
	 * Runs the drawing code with the pose scaled around this bar's top left corner,
	 * so the unscaled drawing code can be reused for bigger/smaller bars.
	 */
	private void withScale(GuiGraphicsExtractor graphics, float s, Runnable draw) {
		int tempX = renderX;
		int tempY = renderY;
		int tempWidth = renderWidth;
		Matrix3x2fStack pose = graphics.pose();
		pose.pushMatrix();
		pose.translate(renderX, renderY);
		pose.scale(s, s);
		renderX = 0;
		renderY = 0;
		renderWidth = Math.max(1, Math.round(tempWidth / s));
		try {
			draw.run();
		} finally {
			pose.popMatrix();
			renderX = tempX;
			renderY = tempY;
			renderWidth = tempWidth;
		}
	}

	private void extractBarUnscaled(GuiGraphicsExtractor graphics) {
		if (renderWidth <= 0) return;
		int transparency = transparency(-1);
		switch (iconPosition) {
			case LEFT -> graphics.blitSprite(RenderPipelines.GUI_TEXTURED, getIcon(), renderX, renderY, ICON_SIZE, ICON_SIZE, transparency);
			case RIGHT -> graphics.blitSprite(RenderPipelines.GUI_TEXTURED, getIcon(), renderX + renderWidth - ICON_SIZE, renderY, ICON_SIZE, ICON_SIZE, transparency);
		}

		int barWidth = iconPosition.equals(IconPosition.OFF) ? renderWidth : renderWidth - ICON_SIZE - 1;
		int barX = iconPosition.equals(IconPosition.LEFT) ? renderX + ICON_SIZE + 1 : renderX;
		if (sbaStyle()) {
			// SkyblockAddons draws the empty bar in the bar's own colour (the texture itself is darker)
			int empty = (colors[0].getRGB() & 0x00FFFFFF) | 0xE6000000;
			sbaLayer(graphics, barX, renderY + 2, barWidth, 1, 0, 1, transparency(empty));
			extractBarFill(graphics, barX, barWidth);
			sbaOverlays(graphics, barX, barWidth);
			return;
		}
		graphics.blitSprite(RenderPipelines.GUI_TEXTURED, barBack(), barX, renderY + 1, barWidth, 7, transparency);
		extractBarFill(graphics, barX, barWidth);
		//context.drawText(MinecraftClient.getInstance().textRenderer, gridX + " " + gridY + " s:" + size , x, y-9, Colors.WHITE, true);
	}

	protected void extractBarFill(GuiGraphicsExtractor graphics, int barX, int barWidth) {
		renderBarFill(graphics, barX, barWidth, fill, transparency(colors[0].getRGB()));

		if (hasOverflow() && overflowFill > 0) {
			renderBarFill(graphics, barX, barWidth, Math.min(overflowFill, 1), transparency(colors[1].getRGB()));
		}
	}

	protected void renderBarFill(GuiGraphicsExtractor graphics, int barX, int barWidth, float fill, int argb) {
		if (sbaStyle()) {
			fill = Math.clamp(fill, 0, 1);
			switch (direction) {
				case LEFT_TO_RIGHT -> sbaLayer(graphics, barX, renderY + 2, barWidth, 7, 0, fill, argb);
				case RIGHT_TO_LEFT -> sbaLayer(graphics, barX, renderY + 2, barWidth, 7, 1 - fill, 1, argb);
				case FROM_MIDDLE -> sbaLayer(graphics, barX, renderY + 2, barWidth, 7, 0.5f - fill / 2, 0.5f + fill / 2, argb);
			}
			return;
		}
		int fillWidth = (int) ((barWidth - 2) * fill);
		int x = switch (direction) {
			case LEFT_TO_RIGHT -> barX + 1;
			case RIGHT_TO_LEFT -> barX + barWidth - fillWidth - 1;
			case FROM_MIDDLE -> barX + (barWidth - fillWidth) / 2;
		};

		GuiHelper.nineSliceColored(graphics, barFill(), x, renderY + 2, fillWidth, 5, argb);
	}

	public void updateValues(float fill, float overflowFill, int value, @Nullable Integer max, @Nullable Integer overflow) {
		this.value = value;
		this.fill = Math.clamp(fill, 0, 1);
		this.overflowFill = Math.clamp(overflowFill, 0, 1);
		this.max = max;
		this.overflow = overflow;
	}

	public void updateWithResource(StatusBarTracker.Resource resource) {
		this.updateValues(resource.value() / (float) resource.max(), resource.overflow() / (float) resource.max(), resource.value(), resource.max(), resource.overflow() > 0 ? resource.overflow() : null);
	}

	public void extractText(GuiGraphicsExtractor graphics) {
		if (!showText()) return;
		float s = effectiveScale();
		if (s != 1) {
			withScale(graphics, s, () -> extractTextUnscaled(graphics));
		} else extractTextUnscaled(graphics);
	}

	private void extractTextUnscaled(GuiGraphicsExtractor graphics) {
		Font textRenderer = Minecraft.getInstance().font;
		int barWidth = iconPosition.equals(IconPosition.OFF) ? renderWidth : renderWidth - ICON_SIZE - 1;
		int barX = iconPosition.equals(IconPosition.LEFT) ? renderX + ICON_SIZE + 2 : renderX;
		String stringValue = value == null ? "???" : toDisplay.apply(overflow == null || showOverflow ? value : value + overflow);
		//Color displayColor = overflow != null && !showOverflow ? colors[1] : textColor == null ? colors[0] : textColor;
		Color displayColor = textColor == null ? colors[0] : textColor;
		MutableComponent text = Component.literal(stringValue).withStyle(style -> style.withColor(displayColor.getRGB()));

		if (hasMax() && showMax && max != null) {
			text.append("/").append(max.toString());
		}
		if (hasOverflow() && showOverflow && overflow != null) {
			MutableComponent literal = Component.literal(" + ").withStyle(style -> style.withColor(colors[1].getRGB()));
			literal.append(toDisplay.apply(overflow));
			text.append(literal);
		}

		boolean smooth = FancyBarsConfig.get().smoothBarText;
		if (smooth) {
			FontDescription barFont = Theme.barFont();
			text = text.withStyle(style -> style.withFont(barFont));
		}
		int textWidth = textRenderer.width(text);
		int x;
		switch (textPosition) {
			case RIGHT -> x = barX + barWidth - textWidth;
			case CENTER -> x = this.renderX + (renderWidth - textWidth) / 2;
			case BAR_CENTER -> x = barX + (barWidth - textWidth) / 2;
			default -> x = barX; // Put on the left by default because I said so.
		}
		int y = this.renderY - 3;

		int color = transparency(displayColor.getRGB());
		int outlineColor = transparency(CommonColors.BLACK);

		if (!smooth) {
			GuiHelper.outlinedText(graphics, Component.translationArg(text), x, y, color, outlineColor);
			return;
		}

		// Smooth text: centre the capitals on the same line the pixel font uses (renderY + 0.5),
		// with sub-pixel horizontal centring, snapped to physical pixels.
		float exactX = switch (textPosition) {
			case RIGHT -> barX + barWidth - textWidth;
			case CENTER -> this.renderX + (renderWidth - textWidth) / 2f;
			case BAR_CENTER -> barX + (barWidth - textWidth) / 2f;
			default -> barX;
		};
		float capTop = this.renderY + 0.5f - Theme.BAR_CAP / 2f;
		float originY = capTop + Theme.BAR_CAP - 7f;
		float pixel = Theme.guiScale();
		Matrix3x2fStack pose = graphics.pose();
		pose.pushMatrix();
		pose.translate(Math.round(exactX * pixel) / pixel, Math.round(originY * pixel) / pixel);
		GuiHelper.outlinedText(graphics, Component.translationArg(text), 0, 0, color, outlineColor);
		pose.popMatrix();
	}

	/**
	 * Draws this bar (without text) at the given position and width, using a sample fill. Used by the bars menu preview.
	 */
	public void extractPreview(GuiGraphicsExtractor graphics, int x, int y, int previewWidth) {
		int tempX = renderX;
		int tempY = renderY;
		int tempWidth = renderWidth;
		boolean tempInMouse = inMouse;
		float tempFill = fill;
		float tempOverflow = overflowFill;
		float tempScale = scale;

		scale = 1;
		renderX = x;
		renderY = y;
		renderWidth = previewWidth;
		inMouse = !enabled; // draws semi-transparent when the bar is turned off
		fill = 0.7f;
		overflowFill = hasOverflow() ? 0.3f : 0;

		extractBar(graphics);

		renderX = tempX;
		renderY = tempY;
		renderWidth = tempWidth;
		inMouse = tempInMouse;
		fill = tempFill;
		overflowFill = tempOverflow;
		scale = tempScale;
	}

	public StatusBarType getType() {
		return type;
	}

	public void extractCursor(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		int temp_x = renderX;
		int temp_y = renderY;
		boolean temp_ghost = inMouse;

		renderX = mouseX;
		renderY = mouseY;
		inMouse = false;

		extractRenderState(graphics, mouseX, mouseY, a);

		renderX = temp_x;
		renderY = temp_y;
		inMouse = temp_ghost;
	}

	// GUI shenanigans

	@Override
	public void setX(int x) {
		this.renderX = x;
	}

	@Override
	public void setY(int y) {
		this.renderY = y;
	}

	@Override
	public int getX() {
		return renderX;
	}

	@Override
	public int getY() {
		return renderY;
	}

	@Override
	public int getWidth() {
		return renderWidth;
	}

	public void setWidth(int width) {
		this.renderWidth = width;
	}

	@Override
	public int getHeight() {
		return Math.max(1, Math.round(9 * effectiveScale()));
	}

	@Override
	public ScreenRectangle getRectangle() {
		return LayoutElement.super.getRectangle();
	}

	@Override
	public boolean isMouseOver(double mouseX, double mouseY) {
		return mouseX >= renderX && mouseX <= renderX + getWidth() && mouseY >= renderY && mouseY <= renderY + getHeight();
	}

	@Override
	public void visitWidgets(Consumer<AbstractWidget> consumer) {
	}

	@Override
	public void setFocused(boolean focused) {
	}

	@Override
	public boolean isFocused() {
		return false;
	}

	@Override
	public NarrationPriority narrationPriority() {
		return NarrationPriority.NONE;
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
		if (!isMouseOver(click.x(), click.y())) return false;
		if (onClick != null) {
			onClick.onClick(this, click);
		}
		return true;
	}

	public void setOnClick(@Nullable OnClick onClick) {
		this.onClick = onClick;
	}

	@Override
	public void updateNarration(NarrationElementOutput builder) {
	}

	@Override
	public String toString() {
		return "StatusBar[name=" + getName().getString() + ", gridX=" + gridX + ", gridY=" + gridY + ", size=" + size
				+ ", x=" + renderX + ", y=" + renderY + ", width=" + renderWidth + ", anchor=" + anchor + "]";
	}

	public IconPosition getIconPosition() {
		return iconPosition;
	}

	public void setIconPosition(IconPosition iconPosition) {
		this.iconPosition = iconPosition;
	}

	public boolean showText() {
		return textPosition != TextPosition.OFF;
	}

	public TextPosition getTextPosition() {
		return textPosition;
	}

	public void setTextPosition(TextPosition textPosition) {
		this.textPosition = textPosition;
	}

	public Direction getDirection() {
		return direction;
	}

	public void setDirection(Direction direction) {
		this.direction = direction;
	}

	public enum IconPosition implements StringRepresentable {
		LEFT,
		RIGHT,
		OFF;

		@Override
		public String getSerializedName() {
			return name();
		}

		@Override
		public String toString() {
			return I18n.get("fancybars.bars.config.commonPosition." + name());
		}
	}

	public enum TextPosition implements StringRepresentable {
		LEFT,
		CENTER,
		BAR_CENTER,
		RIGHT,
		OFF;

		@Override
		public String getSerializedName() {
			return name();
		}

		@Override
		public String toString() {
			if (this == CENTER || this == BAR_CENTER) return I18n.get("fancybars.bars.config.textPosition." + name());
			return I18n.get("fancybars.bars.config.commonPosition." + name());
		}
	}

	public enum Direction implements StringRepresentable {
		LEFT_TO_RIGHT,
		RIGHT_TO_LEFT,
		FROM_MIDDLE;

		@Override
		public String getSerializedName() {
			return name();
		}

		@Override
		public String toString() {
			return I18n.get("fancybars.bars.config.direction." + name());
		}
	}

	@FunctionalInterface
	public interface OnClick {
		void onClick(StatusBar statusBar, MouseButtonEvent click);
	}

	public void loadFromJson(JsonObject object) {
		// Make colors optional, so it's easy to reset to default
		if (object.has("colors")) {
			JsonArray colors1 = object.get("colors").getAsJsonArray();
			if (colors1.size() < 2 && hasOverflow()) {
				throw new IllegalStateException("Missing second color of bar that has overflow");
			}
			Color[] newColors = new Color[colors1.size()];
			for (int i = 0; i < colors1.size(); i++) {
				JsonElement jsonElement = colors1.get(i);
				newColors[i] = new Color(Integer.parseInt(jsonElement.getAsString(), 16));
			}
			this.colors = newColors;
		}

		if (object.has("text_color")) this.textColor = new Color(Integer.parseInt(object.get("text_color").getAsString(), 16));

		String maybeAnchor = object.get("anchor").getAsString().trim();
		this.anchor = maybeAnchor.equals("null") ? null : BarPositioner.BarAnchor.valueOf(maybeAnchor);
		if (!object.has("enabled")) {
			enabled = anchor != null;
		} else enabled = object.get("enabled").getAsBoolean();
		if (anchor != null) {
			this.size = object.get("size").getAsInt();
			this.gridX = object.get("x").getAsInt();
			this.gridY = object.get("y").getAsInt();
		} else {
			this.width = object.get("size").getAsFloat();
			this.x = object.get("x").getAsFloat();
			this.y = object.get("y").getAsFloat();
		}
		// these are optional too, why not
		if (object.has("icon_position")) this.iconPosition = IconPosition.valueOf(object.get("icon_position").getAsString().trim());
		// backwards compat teehee
		if (object.has("show_text")) this.textPosition = object.get("show_text").getAsBoolean() ? TextPosition.BAR_CENTER : TextPosition.OFF;
		if (object.has("text_position")) this.textPosition = TextPosition.valueOf(object.get("text_position").getAsString().trim());
		if (object.has("show_max")) this.showMax = object.get("show_max").getAsBoolean();
		if (object.has("show_overflow")) this.showOverflow = object.get("show_overflow").getAsBoolean();
		this.scale = object.has("scale") ? Math.clamp(object.get("scale").getAsFloat(), MIN_SCALE, MAX_SCALE) : 1;
		if (object.has("direction")) this.direction = Direction.valueOf(object.get("direction").getAsString().trim());
	}

	public JsonObject toJson() {
		JsonObject object = new JsonObject();
		JsonArray colors1 = new JsonArray();
		for (Color color : colors) {
			colors1.add(Integer.toHexString(color.getRGB()).substring(2));
		}
		object.add("colors", colors1);
		if (textColor != null) {
			object.addProperty("text_color", Integer.toHexString(textColor.getRGB()).substring(2));
		}
		if (anchor != null) {
			object.addProperty("anchor", anchor.toString());
		} else object.addProperty("anchor", "null");
		if (anchor != null) {
			object.addProperty("x", gridX);
			object.addProperty("y", gridY);
			object.addProperty("size", size);
		} else {
			object.addProperty("size", width);
			object.addProperty("x", x);
			object.addProperty("y", y);
		}
		object.addProperty("icon_position", iconPosition.getSerializedName());
		object.addProperty("text_position", textPosition.getSerializedName());
		object.addProperty("show_max", showMax);
		object.addProperty("show_overflow", showOverflow);
		object.addProperty("direction", direction.getSerializedName());
		object.addProperty("enabled", enabled);
		object.addProperty("scale", scale);
		return object;
	}

	public static class ManaStatusBar extends StatusBar {

		public ManaStatusBar(StatusBarType type) {
			super(type, mana -> StatusBarTracker.getMana().isEstimated() && FancyBarsConfig.get().showEstimatedTilde ? "~" + mana : mana.toString());
		}

		@Override
		protected void extractBarFill(GuiGraphicsExtractor graphics, int barX, int barWidth) {
			if (hasOverflow() && overflowFill > 0) {
				if (overflowFill > fill && FancyBarsConfig.get().intelligenceDisplay == FancyBarsConfig.IntelligenceDisplay.IN_FRONT) {
					renderBarFill(graphics, barX, barWidth, Math.min(overflowFill, 1), transparency(getColors()[1].getRGB()));
					renderBarFill(graphics, barX, barWidth, fill, transparency(getColors()[0].getRGB()));
				} else {
					renderBarFill(graphics, barX, barWidth, fill, transparency(getColors()[0].getRGB()));
					renderBarFill(graphics, barX, barWidth, Math.min(overflowFill, 1), transparency(getColors()[1].getRGB()));
				}
			} else {
				renderBarFill(graphics, barX, barWidth, fill, transparency(getColors()[0].getRGB()));
			}
		}
	}

	public static class ExperienceStatusBar extends StatusBar {
		private static final Identifier CLOCK_ICON = FancyBarsMod.id("bars/icons/rift_time");

		public ExperienceStatusBar(StatusBarType type) {
			super(type, time -> {
				if (SkyblockLocation.isInTheRift()) {
					return time < 60 ? time + "s" : String.format("%dm%02ds", time / 60, time % 60);
				}
				return time.toString();
			});
		}

		@Override
		protected Identifier getIcon() {
			return SkyblockLocation.isInTheRift() ? CLOCK_ICON : super.getIcon();
		}
	}

	public static class HealthStatusBar extends StatusBar {
		private static final Color WITHER_COLOR = new Color(76, 48, 57);
		private static final Color POISON_COLOR = new Color(94, 78, 18);
		private static final Identifier WITHER_ICON = FancyBarsMod.id("bars/icons/health_wither");
		private static final Identifier POISON_ICON = FancyBarsMod.id("bars/icons/health_poison");

		public HealthStatusBar(StatusBarType type) {
			super(type);
		}

		@Override
		protected void extractBarFill(GuiGraphicsExtractor graphics, int barX, int barWidth) {
			Minecraft client = Minecraft.getInstance();
			boolean withering = client.player != null && client.player.hasEffect(MobEffects.WITHER);
			boolean poisoned = client.player != null && client.player.hasEffect(MobEffects.POISON);

			int fillColor;
			if (withering) {
				fillColor = WITHER_COLOR.getRGB();
			} else if (poisoned) {
				fillColor = POISON_COLOR.getRGB();
			} else {
				fillColor = getColors()[0].getRGB();
			}

			renderBarFill(graphics, barX, barWidth, fill, transparency(fillColor));
			if (hasOverflow() && overflowFill > 0) {
				renderBarFill(graphics, barX, barWidth, Math.min(overflowFill, 1), transparency(getColors()[1].getRGB()));
			}
		}

		@Override
		protected Identifier getIcon() {
			Minecraft client = Minecraft.getInstance();
			if (client.player != null) {
				if (client.player.hasEffect(MobEffects.WITHER)) return WITHER_ICON;
				else if (client.player.hasEffect(MobEffects.POISON)) return POISON_ICON;
			}
			return super.getIcon();
		}
	}

	public static class VitalityStatusBar extends StatusBar {
		public VitalityStatusBar(StatusBarType type) {
			super(type, vitality -> StatusBarTracker.getVitality().isEstimated() && FancyBarsConfig.get().showEstimatedTilde ? "~" + vitality : vitality.toString());
		}
	}
}
