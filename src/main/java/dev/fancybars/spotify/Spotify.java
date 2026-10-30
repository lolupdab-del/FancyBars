package dev.fancybars.spotify;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.jspecify.annotations.Nullable;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.NativeImage;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;

import dev.fancybars.FancyBarsMod;
import dev.fancybars.config.FancyBarsConfig;

/**
 * Spotify through the official Web API: log in once in your browser (PKCE, no password ever touches the mod),
 * then see what's playing and control it. Reading what's playing works on any account; controlling playback
 * (pause, skip, volume, seek, shuffle, repeat) is a Spotify Premium feature.
 */
public final class Spotify {
	public static final int PORT = 43821;
	public static final String REDIRECT = "http://127.0.0.1:" + PORT + "/callback";
	private static final String SCOPES = "user-read-playback-state user-modify-playback-state user-read-currently-playing";
	private static final Path TOKEN_FILE = FancyBarsMod.CONFIG_DIR.resolve("spotify.json");
	private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

	/** What's playing right now. */
	public static final class State {
		public boolean active;
		public boolean playing;
		public String title = "";
		public String artist = "";
		public String album = "";
		public String artUrl = "";
		public long progressMs;
		public long durationMs;
		public int volume = -1;
		public boolean shuffle;
		/** off, context or track. */
		public String repeat = "off";
		public String device = "";
		/** When this state was fetched, to move the progress bar smoothly between polls. */
		long fetchedAt;

		public long progressNow() {
			if (!playing) return progressMs;
			return Math.min(durationMs, progressMs + (Util.getMillis() - fetchedAt));
		}
	}

	private static volatile State state = new State();
	private static @Nullable String accessToken;
	private static long accessExpires;
	private static @Nullable String refreshToken;
	private static volatile String status = "";
	private static volatile boolean busy;
	private static long nextPoll;
	private static final Map<String, Identifier> ART = new HashMap<>();
	private static final Map<String, Boolean> ART_LOADING = new HashMap<>();
	private static int artCounter;

	private Spotify() {}

	public static void init() {
		loadTokens();
		ClientTickEvents.END_CLIENT_TICK.register(mc -> {
			FancyBarsConfig config = FancyBarsConfig.get();
			if (!config.spotifyEnabled || refreshToken == null || busy) return;
			long now = Util.getMillis();
			if (now < nextPoll) return;
			boolean watching = mc.gui.screen() instanceof SpotifyScreen || config.spotifyHud;
			nextPoll = now + (watching ? (state.playing ? 1500 : 4000) : 10000);
			busy = true;
			CompletableFuture.runAsync(Spotify::poll, FancyBarsMod.VIRTUAL_THREAD_EXECUTOR).whenComplete((v, e) -> busy = false);
		});
	}

	public static State state() {
		return state;
	}

	public static boolean connected() {
		return refreshToken != null;
	}

	public static String status() {
		return status;
	}

	// ---------------------------------------------------------------- Login (Authorization Code + PKCE)

	public static void login() {
		String clientId = FancyBarsConfig.get().spotifyClientId.trim();
		if (clientId.isEmpty()) {
			status = "Paste your Client ID first";
			return;
		}
		String verifier = randomString(64);
		String challenge;
		try {
			byte[] hash = MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII));
			challenge = Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
		} catch (Exception e) {
			status = "Couldn't start login";
			return;
		}
		String stateParam = randomString(16);
		String url = "https://accounts.spotify.com/authorize?client_id=" + enc(clientId) + "&response_type=code&redirect_uri=" + enc(REDIRECT)
				+ "&code_challenge_method=S256&code_challenge=" + challenge + "&state=" + stateParam + "&scope=" + enc(SCOPES);
		status = "Waiting for you to log in in your browser…";
		FancyBarsMod.VIRTUAL_THREAD_EXECUTOR.execute(() -> waitForCallback(clientId, verifier, stateParam));
		Util.getPlatform().openUri(url);
	}

	/** A tiny one-shot web server on 127.0.0.1 that catches Spotify's redirect with the login code. */
	private static void waitForCallback(String clientId, String verifier, String expectedState) {
		try (ServerSocket server = new ServerSocket(PORT, 1, InetAddress.getLoopbackAddress())) {
			server.setSoTimeout(180_000);
			while (true) {
				try (Socket socket = server.accept()) {
					BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
					String line = in.readLine();
					if (line == null || !line.startsWith("GET /callback")) {
						respond(socket, 404, "Not found");
						continue;
					}
					String query = line.split(" ")[1];
					Map<String, String> params = new HashMap<>();
					int q = query.indexOf('?');
					if (q >= 0) {
						for (String pair : query.substring(q + 1).split("&")) {
							int eq = pair.indexOf('=');
							if (eq > 0) params.put(pair.substring(0, eq), java.net.URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8));
						}
					}
					if (params.containsKey("error") || !expectedState.equals(params.get("state")) || !params.containsKey("code")) {
						respond(socket, 400, "<h2>Spotify login was cancelled or failed.</h2><p>You can close this tab and try again in Minecraft.</p>");
						status = "Login cancelled";
						return;
					}
					boolean ok = exchange("grant_type=authorization_code&code=" + enc(params.get("code")) + "&redirect_uri=" + enc(REDIRECT)
							+ "&client_id=" + enc(clientId) + "&code_verifier=" + enc(verifier));
					respond(socket, 200, ok ? "<h2>SBCLIENT is connected to Spotify!</h2><p>You can close this tab and go back to Minecraft.</p>"
							: "<h2>Couldn't finish the Spotify login.</h2><p>Check the Client ID and redirect URI, then try again.</p>");
					status = ok ? "Connected" : "Login failed (check the Client ID / redirect URI)";
					nextPoll = 0;
					return;
				}
			}
		} catch (java.net.SocketTimeoutException e) {
			status = "Login timed out";
		} catch (Exception e) {
			status = "Login failed: " + e.getMessage();
			FancyBarsMod.LOGGER.warn("[SBCLIENT] Spotify login failed", e);
		}
	}

	private static void respond(Socket socket, int code, String html) throws Exception {
		byte[] body = ("<html><body style=\"font-family:sans-serif;background:#0b1430;color:#e8eefa;text-align:center;padding-top:60px\">" + html + "</body></html>")
				.getBytes(StandardCharsets.UTF_8);
		OutputStream out = socket.getOutputStream();
		out.write(("HTTP/1.1 " + code + " OK\r\nContent-Type: text/html; charset=utf-8\r\nContent-Length: " + body.length + "\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
		out.write(body);
		out.flush();
	}

	private static synchronized boolean exchange(String form) {
		try {
			HttpRequest request = HttpRequest.newBuilder(URI.create("https://accounts.spotify.com/api/token"))
					.header("Content-Type", "application/x-www-form-urlencoded").timeout(Duration.ofSeconds(15))
					.POST(HttpRequest.BodyPublishers.ofString(form)).build();
			HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
			if (response.statusCode() != 200) {
				FancyBarsMod.LOGGER.warn("[SBCLIENT] Spotify token request failed: {} {}", response.statusCode(), response.body());
				if (response.statusCode() == 400 && form.startsWith("grant_type=refresh_token")) disconnect();
				return false;
			}
			JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
			accessToken = json.get("access_token").getAsString();
			accessExpires = System.currentTimeMillis() + json.get("expires_in").getAsLong() * 1000 - 30_000;
			if (json.has("refresh_token")) refreshToken = json.get("refresh_token").getAsString();
			saveTokens();
			return true;
		} catch (Exception e) {
			FancyBarsMod.LOGGER.warn("[SBCLIENT] Spotify token request failed", e);
			return false;
		}
	}

	private static @Nullable String token() {
		if (refreshToken == null) return null;
		if (accessToken == null || System.currentTimeMillis() > accessExpires) {
			String clientId = FancyBarsConfig.get().spotifyClientId.trim();
			if (!exchange("grant_type=refresh_token&refresh_token=" + enc(refreshToken) + "&client_id=" + enc(clientId))) return null;
		}
		return accessToken;
	}

	public static void disconnect() {
		accessToken = null;
		refreshToken = null;
		state = new State();
		status = "Disconnected";
		try {
			Files.deleteIfExists(TOKEN_FILE);
		} catch (Exception ignored) {
		}
	}

	private static void loadTokens() {
		try {
			if (!Files.exists(TOKEN_FILE)) return;
			JsonObject json = JsonParser.parseString(Files.readString(TOKEN_FILE)).getAsJsonObject();
			if (json.has("refresh_token")) refreshToken = json.get("refresh_token").getAsString();
		} catch (Exception e) {
			FancyBarsMod.LOGGER.warn("[SBCLIENT] Couldn't read Spotify login", e);
		}
	}

	private static void saveTokens() {
		try {
			JsonObject json = new JsonObject();
			if (refreshToken != null) json.addProperty("refresh_token", refreshToken);
			Files.createDirectories(TOKEN_FILE.getParent());
			Files.writeString(TOKEN_FILE, json.toString());
		} catch (Exception e) {
			FancyBarsMod.LOGGER.warn("[SBCLIENT] Couldn't save Spotify login", e);
		}
	}

	// ---------------------------------------------------------------- Playback state

	private static void poll() {
		String token = token();
		if (token == null) return;
		try {
			HttpRequest request = HttpRequest.newBuilder(URI.create("https://api.spotify.com/v1/me/player?additional_types=episode"))
					.header("Authorization", "Bearer " + token).timeout(Duration.ofSeconds(10)).GET().build();
			HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
			if (response.statusCode() == 204 || response.body().isBlank()) {
				state = new State();
				status = "Nothing playing. Start Spotify on any device.";
				return;
			}
			if (response.statusCode() == 401) {
				accessToken = null;
				return;
			}
			if (response.statusCode() == 429) {
				nextPoll = Util.getMillis() + 10_000;
				return;
			}
			if (response.statusCode() != 200) return;
			JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
			State s = new State();
			s.active = true;
			s.fetchedAt = Util.getMillis();
			s.playing = bool(json, "is_playing");
			s.progressMs = json.has("progress_ms") && !json.get("progress_ms").isJsonNull() ? json.get("progress_ms").getAsLong() : 0;
			s.shuffle = bool(json, "shuffle_state");
			s.repeat = json.has("repeat_state") ? json.get("repeat_state").getAsString() : "off";
			if (json.has("device") && json.get("device").isJsonObject()) {
				JsonObject device = json.getAsJsonObject("device");
				s.device = str(device, "name");
				s.volume = device.has("volume_percent") && !device.get("volume_percent").isJsonNull() ? device.get("volume_percent").getAsInt() : -1;
			}
			if (json.has("item") && json.get("item").isJsonObject()) {
				JsonObject item = json.getAsJsonObject("item");
				s.title = str(item, "name");
				s.durationMs = item.has("duration_ms") ? item.get("duration_ms").getAsLong() : 0;
				StringBuilder artists = new StringBuilder();
				if (item.has("artists")) {
					for (JsonElement a : item.getAsJsonArray("artists")) {
						if (!artists.isEmpty()) artists.append(", ");
						artists.append(str(a.getAsJsonObject(), "name"));
					}
				} else if (item.has("show")) {
					artists.append(str(item.getAsJsonObject("show"), "name"));
				}
				s.artist = artists.toString();
				JsonObject album = item.has("album") ? item.getAsJsonObject("album") : item.has("show") ? item.getAsJsonObject("show") : null;
				if (album != null) {
					s.album = str(album, "name");
					if (album.has("images")) {
						JsonArray images = album.getAsJsonArray("images");
						// smallest image that is at least 64px, else the first
						String best = "";
						int bestSize = Integer.MAX_VALUE;
						for (JsonElement img : images) {
							JsonObject o = img.getAsJsonObject();
							int w = o.has("width") && !o.get("width").isJsonNull() ? o.get("width").getAsInt() : 300;
							if (w >= 64 && w < bestSize) {
								bestSize = w;
								best = str(o, "url");
							}
						}
						if (best.isEmpty() && !images.isEmpty()) best = str(images.get(0).getAsJsonObject(), "url");
						s.artUrl = best;
					}
				}
			}
			state = s;
			status = "Connected";
		} catch (Exception e) {
			FancyBarsMod.LOGGER.debug("[SBCLIENT] Spotify poll failed", e);
		}
	}

	private static boolean bool(JsonObject o, String key) {
		return o.has(key) && !o.get(key).isJsonNull() && o.get(key).getAsBoolean();
	}

	private static String str(JsonObject o, String key) {
		return o.has(key) && !o.get(key).isJsonNull() ? o.get(key).getAsString() : "";
	}

	// ---------------------------------------------------------------- Controls (Premium)

	private static void command(String method, String path) {
		FancyBarsMod.VIRTUAL_THREAD_EXECUTOR.execute(() -> {
			String token = token();
			if (token == null) return;
			try {
				HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create("https://api.spotify.com/v1/me/player" + path))
						.header("Authorization", "Bearer " + token).timeout(Duration.ofSeconds(10));
				builder.method(method, HttpRequest.BodyPublishers.noBody());
				HttpResponse<String> response = HTTP.send(builder.build(), HttpResponse.BodyHandlers.ofString());
				if (response.statusCode() == 403) status = "Controls need Spotify Premium";
				else if (response.statusCode() == 404) status = "No active device. Start playing in Spotify first.";
				// refresh soon so the GUI catches up
				nextPoll = Util.getMillis() + 350;
			} catch (Exception e) {
				FancyBarsMod.LOGGER.debug("[SBCLIENT] Spotify command failed", e);
			}
		});
	}

	public static void togglePlay() {
		State s = state;
		s.playing = !s.playing;
		s.progressMs = s.progressNow();
		s.fetchedAt = Util.getMillis();
		command("PUT", s.playing ? "/play" : "/pause");
	}

	public static void next() {
		command("POST", "/next");
	}

	public static void previous() {
		command("POST", "/previous");
	}

	public static void setVolume(int percent) {
		state.volume = Math.clamp(percent, 0, 100);
		command("PUT", "/volume?volume_percent=" + state.volume);
	}

	public static void seek(long ms) {
		State s = state;
		s.progressMs = Math.clamp(ms, 0, s.durationMs);
		s.fetchedAt = Util.getMillis();
		command("PUT", "/seek?position_ms=" + s.progressMs);
	}

	public static void toggleShuffle() {
		state.shuffle = !state.shuffle;
		command("PUT", "/shuffle?state=" + state.shuffle);
	}

	public static void cycleRepeat() {
		String next = switch (state.repeat) {
			case "off" -> "context";
			case "context" -> "track";
			default -> "off";
		};
		state.repeat = next;
		command("PUT", "/repeat?state=" + next);
	}

	// ---------------------------------------------------------------- Album art

	/** Texture for an album cover URL; starts downloading it the first time. */
	public static @Nullable Identifier art(String url) {
		if (url == null || url.isEmpty()) return null;
		Identifier id = ART.get(url);
		if (id != null || ART_LOADING.containsKey(url)) return id;
		ART_LOADING.put(url, true);
		FancyBarsMod.VIRTUAL_THREAD_EXECUTOR.execute(() -> {
			try {
				HttpResponse<byte[]> response = HTTP.send(HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(15)).GET().build(),
						HttpResponse.BodyHandlers.ofByteArray());
				java.awt.image.BufferedImage image = javax.imageio.ImageIO.read(new ByteArrayInputStream(response.body()));
				if (image == null) return;
				int w = image.getWidth();
				int h = image.getHeight();
				int[] pixels = image.getRGB(0, 0, w, h, null, 0, w);
				Minecraft.getInstance().execute(() -> {
					NativeImage tex = new NativeImage(w, h, false);
					for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) tex.setPixel(x, y, pixels[y * w + x] | 0xFF000000);
					Identifier texId = FancyBarsMod.id("spotify/art_" + artCounter++);
					DynamicTexture texture = new DynamicTexture(() -> "SBCLIENT album art", tex);
					Minecraft.getInstance().getTextureManager().register(texId, texture);
					texture.upload();
					ART.put(url, texId);
					if (ART.size() > 20) {
						String oldest = ART.keySet().iterator().next();
						if (!oldest.equals(url)) Minecraft.getInstance().getTextureManager().release(ART.remove(oldest));
					}
				});
			} catch (Exception e) {
				FancyBarsMod.LOGGER.debug("[SBCLIENT] Album art download failed", e);
			}
		});
		return null;
	}

	// ---------------------------------------------------------------- Helpers

	private static String enc(String s) {
		return URLEncoder.encode(s, StandardCharsets.UTF_8);
	}

	private static String randomString(int length) {
		String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
		SecureRandom random = new SecureRandom();
		StringBuilder sb = new StringBuilder(length);
		for (int i = 0; i < length; i++) sb.append(chars.charAt(random.nextInt(chars.length())));
		return sb.toString();
	}

	public static String time(long ms) {
		long s = ms / 1000;
		return s / 60 + ":" + String.format("%02d", s % 60);
	}
}
