package dev.fancybars.spotify;

import com.mojang.blaze3d.platform.cursor.CursorTypes;

import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;

import dev.fancybars.config.FancyBarsConfig;
import dev.fancybars.mixin.ContainerScreenAccessor;
import dev.fancybars.util.gui.Theme;

/**
 * A small Spotify player docked next to your inventory: album art, song, seek bar, previous / play-pause / next and a
 * volume slider. Everything is drawn with smooth sprites and pixel-exact album art.
 */
public final class SpotifyInventoryPanel extends AbstractWidget {
	public static final int W = 132;
	public static final int H = 96;
	private final ContainerScreenAccessor screen;
	private final Screen owner;
	private float hoverPlay;
	private long lastFrame = Util.getMillis();
	private int dragging; // 0 none, 1 seek, 2 volume
	private float dragValue;

	private SpotifyInventoryPanel(Screen owner, ContainerScreenAccessor screen) {
		super(0, 0, W, H, Component.translatable("fancybars.spotify.title"));
		this.owner = owner;
		this.screen = screen;
		place();
	}

	public static void init() {
		ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> {
			FancyBarsConfig config = FancyBarsConfig.get();
			if (!config.spotifyEnabled || !config.spotifyInventory || !Spotify.connected()) return;
			if (screen instanceof InventoryScreen inventory) {
				Screens.getWidgets(inventory).add(new SpotifyInventoryPanel(inventory, (ContainerScreenAccessor) inventory));
			}
		});
	}

	/** Right of the inventory if there's room, otherwise left; follows the recipe book. */
	private void place() {
		int left = screen.fancybars$getLeftPos();
		int top = screen.fancybars$getTopPos();
		int right = left + screen.fancybars$getImageWidth();
		int x = right + 6 + W <= owner.width ? right + 6 : Math.max(2, left - 6 - W);
		setPosition(x, top);
	}

	// ---------------------------------------------------------------- Layout (relative to the panel)

	private static final int PAD = 8;
	private static final int ART = 36;

	private int barX() {
		return getX() + PAD;
	}

	private int barW() {
		return W - PAD * 2;
	}

	private int barY() {
		return getY() + PAD + ART + 8;
	}

	private int controlsY() {
		return barY() + 20;
	}

	private int volumeY() {
		return getY() + H - 12;
	}

	private int volumeX() {
		return getX() + PAD + 13;
	}

	private int volumeW() {
		return W - PAD * 2 - 13;
	}

	@Override
	protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		place();
		hoverPlay = Theme.approach(hoverPlay, overPlay(mouseX, mouseY) ? 1 : 0, 10, lastFrame);
		lastFrame = Util.getMillis();
		Spotify.State s = Spotify.state();
		int x = getX();
		int y = getY();

		graphics.nextStratum();
		Theme.shadow(graphics, x, y, W, H, 8, 0, 2);
		Theme.roundedRect(graphics, x, y, W, H, 8, Theme.hudBg(0.94f, 255));
		if (FancyBarsConfig.get().hudBorders) Theme.border(graphics, x, y, W, H, 8, SpotifyUi.green(0x66), 255);

		// art + text
		SpotifyUi.art(graphics, s.artUrl, x + PAD, y + PAD, ART);
		int tx = x + PAD + ART + 7;
		int tw = x + W - PAD - tx;
		if (s.active) {
			SpotifyUi.scrolling(graphics, Component.literal(s.title), tx, y + PAD + 6, tw, Theme.hudText(255), true);
			SpotifyUi.scrolling(graphics, Component.literal(s.artist), tx, y + PAD + 19, tw, Theme.hudTextSoft(255), false);
		} else {
			SpotifyUi.scrolling(graphics, Component.translatable("fancybars.spotify.nothing"), tx, y + PAD + 12, tw, Theme.hudTextSoft(255), false);
		}

		// seek bar
		float fraction = dragging == 1 ? dragValue : s.durationMs > 0 ? s.progressNow() / (float) s.durationMs : 0;
		boolean overBar = inside(mouseX, mouseY, barX() - 2, barY() - 4, barW() + 4, 10);
		SpotifyUi.progress(graphics, barX(), barY(), barW(), fraction, overBar || dragging == 1 ? SpotifyUi.green() : Theme.hudText(255));
		if (overBar || dragging == 1) Theme.circle(graphics, barX() + Math.round(barW() * fraction) - 4, barY() - 2, 8, Theme.hudText(255));
		long shownMs = dragging == 1 ? (long) (dragValue * s.durationMs) : s.progressNow();
		smallText(graphics, Spotify.time(shownMs), barX(), barY() + 5, false);
		smallText(graphics, Spotify.time(s.durationMs), barX() + barW(), barY() + 5, true);

		// controls
		int cx = x + W / 2;
		int cy = controlsY();
		float r = 10 + hoverPlay * 1.2f;
		Theme.circle(graphics, Math.round(cx - r), Math.round(cy - r), Math.round(r * 2), Theme.hudText(255));
		if (s.playing) SpotifyUi.pause(graphics, cx, cy, 9, Theme.hudBg(1, 255));
		else SpotifyUi.play(graphics, cx + 0.8f, cy, 9, Theme.hudBg(1, 255));
		SpotifyUi.skip(graphics, cx - 28, cy, 10, overSkip(mouseX, mouseY, false) ? Theme.hudText(255) : Theme.hudTextSoft(255), false);
		SpotifyUi.skip(graphics, cx + 28, cy, 10, overSkip(mouseX, mouseY, true) ? Theme.hudText(255) : Theme.hudTextSoft(255), true);

		// volume
		float volume = dragging == 2 ? dragValue : Math.max(0, s.volume) / 100f;
		SpotifyUi.icon(graphics, "speaker", x + PAD - 1, volumeY() - 4, 10, Theme.hudTextSoft(255));
		boolean overVol = inside(mouseX, mouseY, volumeX() - 2, volumeY() - 4, volumeW() + 4, 10);
		SpotifyUi.progress(graphics, volumeX(), volumeY(), volumeW(), volume, overVol || dragging == 2 ? SpotifyUi.green() : Theme.hudText(255));
		if (overVol || dragging == 2) Theme.circle(graphics, volumeX() + Math.round(volumeW() * volume) - 4, volumeY() - 2, 8, Theme.hudText(255));

		if (overBar || overVol || overPlay(mouseX, mouseY) || overSkip(mouseX, mouseY, true) || overSkip(mouseX, mouseY, false) || dragging != 0) {
			graphics.requestCursor(CursorTypes.POINTING_HAND);
		}
	}

	private void smallText(GuiGraphicsExtractor graphics, String text, float x, float y, boolean alignRight) {
		org.joml.Matrix3x2fStack pose = graphics.pose();
		Component c = Theme.styled(Component.literal(text));
		float w = Theme.width(net.minecraft.client.Minecraft.getInstance().font, c) * 0.75f;
		pose.pushMatrix();
		pose.translate(alignRight ? x - w : x, y);
		pose.scale(0.75f, 0.75f);
		Theme.drawAtCapTop(graphics, net.minecraft.client.Minecraft.getInstance().font, c, 0, 1, Theme.UI_CAP, Theme.hudTextSoft(255));
		pose.popMatrix();
	}

	private static boolean inside(double mx, double my, int x, int y, int w, int h) {
		return mx >= x && mx < x + w && my >= y && my < y + h;
	}

	private boolean overPlay(double mx, double my) {
		return Math.hypot(mx - (getX() + W / 2.0), my - controlsY()) <= 11;
	}

	private boolean overSkip(double mx, double my, boolean next) {
		double cx = getX() + W / 2.0 + (next ? 28 : -28);
		return Math.abs(mx - cx) <= 8 && Math.abs(my - controlsY()) <= 8;
	}

	@Override
	public void onClick(MouseButtonEvent click, boolean doubled) {
		double mx = click.x();
		double my = click.y();
		if (overPlay(mx, my)) Spotify.togglePlay();
		else if (overSkip(mx, my, true)) Spotify.next();
		else if (overSkip(mx, my, false)) Spotify.previous();
		else if (inside(mx, my, barX() - 2, barY() - 4, barW() + 4, 10) && Spotify.state().durationMs > 0) {
			dragging = 1;
			dragValue = (float) Math.clamp((mx - barX()) / barW(), 0, 1);
		} else if (inside(mx, my, volumeX() - 2, volumeY() - 4, volumeW() + 4, 10)) {
			dragging = 2;
			dragValue = (float) Math.clamp((mx - volumeX()) / volumeW(), 0, 1);
		}
	}

	@Override
	protected void onDrag(MouseButtonEvent click, double dx, double dy) {
		if (dragging == 1) dragValue = (float) Math.clamp((click.x() - barX()) / barW(), 0, 1);
		else if (dragging == 2) dragValue = (float) Math.clamp((click.x() - volumeX()) / volumeW(), 0, 1);
	}

	@Override
	public void onRelease(MouseButtonEvent click) {
		if (dragging == 1) Spotify.seek((long) (dragValue * Spotify.state().durationMs));
		else if (dragging == 2) Spotify.setVolume(Math.round(dragValue * 100));
		dragging = 0;
	}

	@Override
	protected void updateWidgetNarration(NarrationElementOutput output) {}

	/** Never take keyboard focus away from the inventory. */
	@Override
	public net.minecraft.client.gui.@org.jspecify.annotations.Nullable ComponentPath nextFocusPath(net.minecraft.client.gui.navigation.FocusNavigationEvent event) {
		return null;
	}
}
