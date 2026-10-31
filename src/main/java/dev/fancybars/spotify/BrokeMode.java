package dev.fancybars.spotify;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.minecraft.util.Util;

import dev.fancybars.FancyBarsMod;

/**
 * "Broke Mode": shows what the Spotify desktop app is playing without a Spotify account login, developer app
 * or Premium. It reads the song from the app itself:
 * <ul>
 *     <li>Windows: the Spotify window title ("Artist - Song"); cover art and length come from a public
 *     iTunes search, progress is counted from when the song started; play / pause / skip use media keys.</li>
 *     <li>macOS: AppleScript (full info and controls).</li>
 *     <li>Linux: playerctl (full info and controls).</li>
 * </ul>
 */
public final class BrokeMode {
	private enum Os { WINDOWS, MAC, LINUX }

	private static final Os OS;
	private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();
	private static final Map<String, Lookup> LOOKUPS = new ConcurrentHashMap<>();

	private record Lookup(String art, long durationMs, String album) {}

	// Windows progress estimate
	private static String lastSong = "";
	private static long songStartedAt;
	private static long pausedAt = -1;

	static {
		String name = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
		OS = name.contains("win") ? Os.WINDOWS : name.contains("mac") ? Os.MAC : Os.LINUX;
	}

	private BrokeMode() {}

	/** Volume and seeking only work where the app can be told directly (macOS / Linux). */
	public static boolean canSeek() {
		return OS != Os.WINDOWS;
	}

	static Spotify.State poll(Spotify.State previous) {
		try {
			return switch (OS) {
				case WINDOWS -> pollWindows(previous);
				case MAC -> pollMac();
				case LINUX -> pollLinux();
			};
		} catch (Exception e) {
			FancyBarsMod.LOGGER.debug("[SBCLIENT] Broke Mode poll failed", e);
			return previous;
		}
	}

	// ---------------------------------------------------------------- Windows

	/** Latest Spotify window title from the watcher: null = Spotify isn't running, "" = running with no song shown. */
	private static volatile String windowTitle;
	private static volatile Process watcher;

	/**
	 * One long-running, low-priority PowerShell process that prints Spotify's window title once a second (UTF-8, so
	 * song names in any language come through). Much lighter than starting a program on every poll.
	 */
	private static synchronized void ensureWatcher() {
		if (watcher != null && watcher.isAlive()) return;
		String script = "[Console]::OutputEncoding=[Text.Encoding]::UTF8; while($true){ "
				+ "$all = @(Get-Process Spotify -ErrorAction SilentlyContinue); "
				+ "$w = $all | Where-Object { $_.MainWindowTitle } | Select-Object -First 1; "
				+ "if ($w) { 'T:' + $w.MainWindowTitle } elseif ($all.Count -gt 0) { 'R:' } else { 'N:' }; "
				+ "Start-Sleep -Milliseconds 1000 }";
		try {
			Process p = new ProcessBuilder("powershell", "-NoProfile", "-NonInteractive", "-WindowStyle", "Hidden", "-Command", script).redirectErrorStream(true).start();
			watcher = p;
			Runtime.getRuntime().addShutdownHook(new Thread(p::destroyForcibly));
			Thread.ofVirtual().start(() -> {
				try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
					String line;
					while ((line = r.readLine()) != null) {
						if (line.startsWith("T:")) windowTitle = line.substring(2).trim();
						else if (line.startsWith("R:")) windowTitle = "";
						else if (line.startsWith("N:")) windowTitle = null;
					}
				} catch (Exception ignored) {
				}
			});
		} catch (Exception e) {
			FancyBarsMod.LOGGER.warn("[SBCLIENT] Broke Mode couldn't watch Spotify", e);
		}
	}

	/** Stops the Windows watcher (Broke Mode turned off). */
	static synchronized void stop() {
		if (watcher != null) watcher.destroyForcibly();
		watcher = null;
		windowTitle = null;
	}

	private static Spotify.State pollWindows(Spotify.State previous) {
		ensureWatcher();
		String title = windowTitle;
		boolean running = title != null;
		String song = running && title.contains(" - ") && !title.startsWith("Spotify") ? title : null;
		Spotify.State s = new Spotify.State();
		s.device = "Spotify (Broke Mode)";
		if (!running) return s;
		long now = Util.getMillis();
		if (song == null) {
			// app open but paused (its title is just "Spotify Free" / "Spotify Premium"): keep the last song, paused
			s.active = !lastSong.isEmpty();
			s.playing = false;
			if (pausedAt < 0) pausedAt = now;
			fill(s, lastSong, now);
			return s;
		}
		if (!song.equals(lastSong)) {
			lastSong = song;
			songStartedAt = now;
			pausedAt = -1;
		} else if (pausedAt >= 0) {
			// resumed: don't count the paused time
			songStartedAt += now - pausedAt;
			pausedAt = -1;
		}
		s.active = true;
		s.playing = true;
		fill(s, song, now);
		return s;
	}

	private static void fill(Spotify.State s, String song, long now) {
		if (song.isEmpty()) return;
		int dash = song.indexOf(" - ");
		s.artist = dash > 0 ? song.substring(0, dash) : "";
		s.title = dash > 0 ? song.substring(dash + 3) : song;
		Lookup info = lookup(s.artist, s.title);
		if (info != null) {
			s.artUrl = info.art();
			s.durationMs = info.durationMs();
			s.album = info.album();
		}
		long end = pausedAt >= 0 ? pausedAt : now;
		s.progressMs = Math.max(0, end - songStartedAt);
		if (s.durationMs > 0) s.progressMs = Math.min(s.progressMs, s.durationMs);
		s.fetchedAt = now;
	}

	/** Cover art, length and album from Apple's public search (no account needed); cached per song. */
	private static Lookup lookup(String artist, String title) {
		String key = artist + " - " + title;
		Lookup cached = LOOKUPS.get(key);
		if (cached != null) return cached;
		Lookup result = new Lookup("", 0, "");
		try {
			String term = URLEncoder.encode(artist + " " + title, StandardCharsets.UTF_8);
			HttpRequest request = HttpRequest.newBuilder(URI.create("https://itunes.apple.com/search?entity=song&limit=1&term=" + term))
					.timeout(Duration.ofSeconds(8)).header("User-Agent", "SBCLIENT Minecraft mod").GET().build();
			HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
			JsonArray results = JsonParser.parseString(response.body()).getAsJsonObject().getAsJsonArray("results");
			if (results != null && !results.isEmpty()) {
				JsonObject r = results.get(0).getAsJsonObject();
				String art = r.has("artworkUrl100") ? r.get("artworkUrl100").getAsString().replace("100x100bb", "600x600bb") : "";
				long length = r.has("trackTimeMillis") ? r.get("trackTimeMillis").getAsLong() : 0;
				String album = r.has("collectionName") ? r.get("collectionName").getAsString() : "";
				result = new Lookup(art, length, album);
			}
		} catch (Exception e) {
			FancyBarsMod.LOGGER.debug("[SBCLIENT] Broke Mode song lookup failed", e);
		}
		LOOKUPS.put(key, result);
		return result;
	}

	private static List<String> csv(String line) {
		List<String> cols = new ArrayList<>();
		StringBuilder cur = new StringBuilder();
		boolean quoted = false;
		for (int i = 0; i < line.length(); i++) {
			char c = line.charAt(i);
			if (c == '"') quoted = !quoted;
			else if (c == ',' && !quoted) {
				cols.add(cur.toString());
				cur.setLength(0);
			} else cur.append(c);
		}
		cols.add(cur.toString());
		return cols;
	}

	// ---------------------------------------------------------------- macOS

	private static final String MAC_SCRIPT = String.join("\n",
			"if application \"Spotify\" is running then",
			"tell application \"Spotify\"",
			"set out to (player state as string) & linefeed",
			"set out to out & (name of current track) & linefeed & (artist of current track) & linefeed & (album of current track) & linefeed",
			"set out to out & ((player position * 1000) as integer as string) & linefeed & (duration of current track as string) & linefeed",
			"set out to out & (artwork url of current track) & linefeed & (sound volume as string) & linefeed & (shuffling as string) & linefeed & (repeating as string)",
			"return out",
			"end tell",
			"end if");

	private static Spotify.State pollMac() throws Exception {
		List<String> out = run(5, "osascript", "-e", MAC_SCRIPT);
		Spotify.State s = new Spotify.State();
		s.device = "Spotify (Broke Mode)";
		if (out.size() < 7) return s;
		s.active = true;
		s.playing = out.get(0).trim().equals("playing");
		s.title = out.get(1);
		s.artist = out.get(2);
		s.album = out.get(3);
		s.progressMs = parseLong(out.get(4));
		s.durationMs = parseLong(out.get(5));
		s.artUrl = out.get(6);
		if (out.size() > 7) s.volume = (int) parseLong(out.get(7));
		if (out.size() > 8) s.shuffle = out.get(8).trim().equals("true");
		if (out.size() > 9) s.repeat = out.get(9).trim().equals("true") ? "context" : "off";
		s.fetchedAt = Util.getMillis();
		return s;
	}

	// ---------------------------------------------------------------- Linux

	private static Spotify.State pollLinux() throws Exception {
		List<String> out = run(5, "playerctl", "-p", "spotify", "metadata", "--format",
				"{{status}}\n{{title}}\n{{artist}}\n{{album}}\n{{position}}\n{{mpris:length}}\n{{mpris:artUrl}}\n{{volume}}");
		Spotify.State s = new Spotify.State();
		s.device = "Spotify (Broke Mode)";
		if (out.size() < 7) return s;
		s.active = true;
		s.playing = out.get(0).trim().equalsIgnoreCase("Playing");
		s.title = out.get(1);
		s.artist = out.get(2);
		s.album = out.get(3);
		s.progressMs = parseLong(out.get(4)) / 1000;
		s.durationMs = parseLong(out.get(5)) / 1000;
		s.artUrl = out.get(6).replace("open.spotify.com", "i.scdn.co");
		if (out.size() > 7) {
			try {
				s.volume = Math.round(Float.parseFloat(out.get(7).trim()) * 100);
			} catch (NumberFormatException ignored) {
			}
		}
		s.fetchedAt = Util.getMillis();
		return s;
	}

	// ---------------------------------------------------------------- controls

	static void playPause() {
		switch (OS) {
			case WINDOWS -> mediaKey(179);
			case MAC -> async("osascript", "-e", "tell application \"Spotify\" to playpause");
			case LINUX -> async("playerctl", "-p", "spotify", "play-pause");
		}
		if (OS == Os.WINDOWS) {
			if (pausedAt >= 0) {
				songStartedAt += Util.getMillis() - pausedAt;
				pausedAt = -1;
			} else pausedAt = Util.getMillis();
		}
	}

	static void next() {
		switch (OS) {
			case WINDOWS -> mediaKey(176);
			case MAC -> async("osascript", "-e", "tell application \"Spotify\" to next track");
			case LINUX -> async("playerctl", "-p", "spotify", "next");
		}
	}

	static void previous() {
		switch (OS) {
			case WINDOWS -> mediaKey(177);
			case MAC -> async("osascript", "-e", "tell application \"Spotify\" to previous track");
			case LINUX -> async("playerctl", "-p", "spotify", "previous");
		}
	}

	static void setVolume(int percent) {
		switch (OS) {
			case MAC -> async("osascript", "-e", "tell application \"Spotify\" to set sound volume to " + percent);
			case LINUX -> async("playerctl", "-p", "spotify", "volume", String.format(Locale.ROOT, "%.2f", percent / 100f));
			default -> {}
		}
	}

	static void seek(long ms) {
		switch (OS) {
			case MAC -> async("osascript", "-e", "tell application \"Spotify\" to set player position to " + ms / 1000.0);
			case LINUX -> async("playerctl", "-p", "spotify", "position", String.valueOf(ms / 1000.0));
			default -> {}
		}
	}

	/** Windows media keys (play/pause 179, next 176, previous 177) without any native code. */
	private static void mediaKey(int code) {
		async("powershell", "-NoProfile", "-NonInteractive", "-WindowStyle", "Hidden", "-Command",
				"(New-Object -ComObject WScript.Shell).SendKeys([char]" + code + ")");
	}

	// ---------------------------------------------------------------- helpers

	private static void async(String... command) {
		FancyBarsMod.VIRTUAL_THREAD_EXECUTOR.execute(() -> {
			try {
				run(5, command);
			} catch (Exception e) {
				FancyBarsMod.LOGGER.debug("[SBCLIENT] Broke Mode command failed", e);
			}
		});
	}

	private static List<String> run(int timeoutSeconds, String... command) throws Exception {
		Process p = new ProcessBuilder(command).redirectErrorStream(true).start();
		List<String> lines = new ArrayList<>();
		try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream(), OS == Os.WINDOWS ? java.nio.charset.Charset.forName("UTF-8") : StandardCharsets.UTF_8))) {
			String line;
			while ((line = r.readLine()) != null) lines.add(line);
		}
		if (!p.waitFor(timeoutSeconds, TimeUnit.SECONDS)) p.destroyForcibly();
		return lines;
	}

	private static long parseLong(String s) {
		try {
			return (long) Double.parseDouble(s.trim().replace(',', '.'));
		} catch (NumberFormatException e) {
			return 0;
		}
	}
}
