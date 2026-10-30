package dev.fancybars.spotify;

import org.jspecify.annotations.Nullable;

import com.mojang.blaze3d.platform.cursor.CursorTypes;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import dev.fancybars.config.FancyBarsConfig;
import dev.fancybars.util.gui.ModernButton;
import dev.fancybars.util.gui.ModernSlider;
import dev.fancybars.util.gui.SearchField;
import dev.fancybars.util.gui.Theme;

/** The Spotify player: what's playing, seek bar, play / pause / skip, shuffle, repeat and volume. */
public class SpotifyScreen extends Screen {
	private final @Nullable Screen parent;
	private int px;
	private int py;
	private int pw;
	private int ph;
	private int barX;
	private int barY;
	private int barW;
	private int controlsY;
	private boolean seeking;
	private float seekFraction;

	public SpotifyScreen(@Nullable Screen parent) {
		super(Component.translatable("fancybars.spotify.title"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		pw = Math.min(width - 12, 320);
		ph = Math.min(height - 12, Spotify.connected() ? 170 : 200);
		px = (width - pw) / 2;
		py = (height - ph) / 2;
		FancyBarsConfig config = FancyBarsConfig.get();

		if (!Spotify.connected()) {
			SearchField clientId = new SearchField(pw - 24, 16, config.spotifyClientId, Component.translatable("fancybars.spotify.clientId"), text -> {
				config.spotifyClientId = text.trim();
				FancyBarsConfig.save();
			}).maxLength(64);
			clientId.setPosition(px + 12, py + ph - 70);
			addRenderableWidget(clientId);
			ModernButton connect = new ModernButton(pw - 24, 20, Component.translatable("fancybars.spotify.connect"), ModernButton.Style.PRIMARY, Spotify::login);
			connect.setPosition(px + 12, py + ph - 48);
			addRenderableWidget(connect);
		} else {
			int art = 84;
			int infoX = px + 12 + art + 12;
			barX = infoX;
			barW = px + pw - 12 - infoX;
			barY = py + 72;
			controlsY = py + 100;
			int small = 52;
			ModernButton shuffle = new ModernButton(small, 16, Component.translatable("fancybars.spotify.shuffle"), Spotify::toggleShuffle);
			shuffle.setPosition(infoX, controlsY + 30);
			addRenderableWidget(shuffle);
			ModernButton repeat = new ModernButton(small, 16, Component.translatable("fancybars.spotify.repeat"), Spotify::cycleRepeat);
			repeat.setPosition(px + pw - 12 - small, controlsY + 30);
			addRenderableWidget(repeat);
			ModernSlider[] volume = new ModernSlider[1];
			volume[0] = new ModernSlider(barW - 14, 12, 0, 100, 1, Math.max(0, Spotify.state().volume), v -> {}, () -> Spotify.setVolume(Math.round(volume[0].getValue())));
			volume[0].setPosition(infoX + 14, py + ph - 42);
			addRenderableWidget(volume[0]);
			ModernButton disconnect = new ModernButton(80, 16, Component.translatable("fancybars.spotify.disconnect"), () -> {
				Spotify.disconnect();
				rebuild();
			});
			disconnect.setPosition(px + 12, py + ph - 24);
			addRenderableWidget(disconnect);
		}
		ModernButton done = new ModernButton(60, 16, Component.translatable("gui.done"), ModernButton.Style.PRIMARY, this::onClose);
		done.setPosition(px + pw - 12 - 60, py + ph - 24);
		addRenderableWidget(done);
	}

	private void rebuild() {
		minecraft.gui.setScreen(new SpotifyScreen(parent));
	}

	@Override
	public void tick() {
		// the login finishes in the background: switch to the player when it does
		boolean connected = Spotify.connected();
		if (connected != (barW > 0)) rebuild();
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		Theme.backdrop(graphics, width, height);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		Theme.panel(graphics, px, py, pw, ph);
		// header
		Theme.circle(graphics, px + 12, py + 9, 10, SpotifyUi.GREEN);
		Theme.titleInBox(graphics, font, title, px + 28, py + 4, 20, Theme.TEXT);

		if (!Spotify.connected()) {
			int y = py + 30;
			for (int i = 1; i <= 5; i++) {
				Theme.textInBox(graphics, font, Component.translatable("fancybars.spotify.setup" + i, Spotify.REDIRECT), px + 12, y, 13, i == 1 ? Theme.TEXT : Theme.TEXT_SOFT);
				y += 14;
			}
			Theme.textInBox(graphics, font, Component.literal(Spotify.status()), px + 12, py + ph - 24, 16, Theme.TEXT_MUTED);
			super.extractRenderState(graphics, mouseX, mouseY, a);
			return;
		}

		Spotify.State s = Spotify.state();
		int art = 84;
		SpotifyUi.art(graphics, s.artUrl, px + 12, py + 32, art);
		int infoX = px + 12 + art + 12;
		int infoW = px + pw - 12 - infoX;
		if (!s.active) {
			Theme.textInBox(graphics, font, Component.literal(Spotify.status().isEmpty() ? "Loading…" : Spotify.status()), infoX, py + 34, 14, Theme.TEXT_MUTED);
		} else {
			SpotifyUi.scrolling(graphics, Component.literal(s.title), infoX, py + 36, infoW, 0xFFFFFFFF, true);
			SpotifyUi.scrolling(graphics, Component.literal(s.artist), infoX, py + 50, infoW, Theme.TEXT_SOFT, false);
			SpotifyUi.scrolling(graphics, Component.literal(s.device.isEmpty() ? s.album : s.album + "  •  " + s.device), infoX, py + 62, infoW, Theme.TEXT_MUTED, false);
		}

		// seek bar
		float fraction = s.durationMs > 0 ? (seeking ? seekFraction : s.progressNow() / (float) s.durationMs) : 0;
		boolean overBar = mouseX >= barX && mouseX < barX + barW && mouseY >= barY - 4 && mouseY < barY + 7;
		SpotifyUi.progress(graphics, barX, barY, barW, fraction, overBar || seeking ? SpotifyUi.GREEN : 0xFFFFFFFF);
		if (overBar || seeking) {
			Theme.circle(graphics, barX + Math.round(barW * fraction) - 4, barY - 2, 8, 0xFFFFFFFF);
			graphics.requestCursor(CursorTypes.POINTING_HAND);
		}
		long shown = seeking ? (long) (seekFraction * s.durationMs) : s.progressNow();
		Theme.textInBox(graphics, font, Component.literal(Spotify.time(shown)), barX, barY + 5, 12, Theme.TEXT_MUTED);
		Component total = Component.literal(Spotify.time(s.durationMs));
		Theme.textInBox(graphics, font, total, barX + barW - Theme.width(font, total), barY + 5, 12, Theme.TEXT_MUTED);

		// prev / play / next
		int cx = barX + barW / 2;
		int cy = controlsY + 12;
		boolean overPlay = Math.hypot(mouseX - cx, mouseY - cy) <= 12;
		Theme.circle(graphics, cx - 12, cy - 12, 24, overPlay ? 0xFFFFFFFF : 0xFFE8EEFA);
		if (s.playing) SpotifyUi.pause(graphics, cx, cy, 11, 0xFF0B1430);
		else SpotifyUi.play(graphics, cx + 1, cy, 11, 0xFF0B1430);
		boolean overPrev = Math.abs(mouseX - (cx - 34)) <= 9 && Math.abs(mouseY - cy) <= 9;
		boolean overNext = Math.abs(mouseX - (cx + 34)) <= 9 && Math.abs(mouseY - cy) <= 9;
		SpotifyUi.skip(graphics, cx - 34, cy, 12, overPrev ? 0xFFFFFFFF : 0xFFB0B8CC, false);
		SpotifyUi.skip(graphics, cx + 34, cy, 12, overNext ? 0xFFFFFFFF : 0xFFB0B8CC, true);
		if (overPlay || overPrev || overNext) graphics.requestCursor(CursorTypes.POINTING_HAND);

		// shuffle / repeat state
		int stateY = controlsY + 48;
		Theme.textInBox(graphics, font, Component.translatable(s.shuffle ? "fancybars.spotify.on" : "fancybars.spotify.off"), barX + 2, stateY, 10,
				s.shuffle ? SpotifyUi.GREEN : Theme.TEXT_MUTED);
		Component rep = Component.translatable("fancybars.spotify.repeat." + s.repeat);
		Theme.textInBox(graphics, font, rep, barX + barW - Theme.width(font, rep) - 2, stateY, 10, s.repeat.equals("off") ? Theme.TEXT_MUTED : SpotifyUi.GREEN);

		// volume
		SpotifyUi.speaker(graphics, barX, py + ph - 41, Theme.TEXT_SOFT);
		Component status = Component.literal(Spotify.status().equals("Connected") ? "" : Spotify.status());
		Theme.textInBox(graphics, font, status, px + 100, py + ph - 24, 16, Theme.TEXT_MUTED);
		super.extractRenderState(graphics, mouseX, mouseY, a);
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
		if (super.mouseClicked(click, doubled)) return true;
		if (!Spotify.connected()) return false;
		double mx = click.x();
		double my = click.y();
		int cx = barX + barW / 2;
		int cy = controlsY + 12;
		if (Math.hypot(mx - cx, my - cy) <= 12) {
			Spotify.togglePlay();
			return true;
		}
		if (Math.abs(mx - (cx - 34)) <= 9 && Math.abs(my - cy) <= 9) {
			Spotify.previous();
			return true;
		}
		if (Math.abs(mx - (cx + 34)) <= 9 && Math.abs(my - cy) <= 9) {
			Spotify.next();
			return true;
		}
		if (mx >= barX && mx < barX + barW && my >= barY - 4 && my < barY + 7 && Spotify.state().durationMs > 0) {
			seeking = true;
			seekFraction = (float) Math.clamp((mx - barX) / barW, 0, 1);
			return true;
		}
		return false;
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent click, double dx, double dy) {
		if (seeking) {
			seekFraction = (float) Math.clamp((click.x() - barX) / barW, 0, 1);
			return true;
		}
		return super.mouseDragged(click, dx, dy);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent click) {
		if (seeking) {
			seeking = false;
			Spotify.seek((long) (seekFraction * Spotify.state().durationMs));
			return true;
		}
		return super.mouseReleased(click);
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
