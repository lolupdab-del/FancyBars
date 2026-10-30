package dev.fancybars.players;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

import dev.fancybars.FancyBarsMod;
import dev.fancybars.config.FancyBarsConfig;

/**
 * Custom player sizes. Your own size is set in the menu and applies instantly for you. Sizes for everyone who uses
 * the mod come from a shared list ({@code sizes.json} in the mod's GitHub repo), downloaded every few minutes, so
 * every SBCLIENT user sees the same sizes. Purely visual: hitboxes don't change.
 */
public final class PlayerSizes {
	public static final String LIST_URL = "https://raw.githubusercontent.com/lolupdab-del/FancyBars/main/sizes.json";
	private static final long REFRESH_TICKS = 20 * 60 * 5;
	public static final float MIN = 0.25f;
	public static final float MAX = 3f;

	private static final Map<String, Float> SHARED = new ConcurrentHashMap<>();
	private static long ticks = REFRESH_TICKS - 40;
	private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

	private PlayerSizes() {}

	public static void init() {
		ClientTickEvents.END_CLIENT_TICK.register(mc -> {
			if (mc.player == null) return;
			if (++ticks >= REFRESH_TICKS) {
				ticks = 0;
				refresh();
			}
		});
	}

	public static void refresh() {
		HttpRequest request = HttpRequest.newBuilder(URI.create(LIST_URL)).timeout(Duration.ofSeconds(15)).GET().build();
		HTTP.sendAsync(request, HttpResponse.BodyHandlers.ofString()).thenAccept(response -> {
			if (response.statusCode() != 200) return;
			try {
				JsonObject players = JsonParser.parseString(response.body()).getAsJsonObject().getAsJsonObject("players");
				Map<String, Float> fresh = new java.util.HashMap<>();
				for (Map.Entry<String, JsonElement> entry : players.entrySet()) {
					fresh.put(entry.getKey().toLowerCase(Locale.ROOT), Math.clamp(entry.getValue().getAsFloat(), MIN, MAX));
				}
				SHARED.clear();
				SHARED.putAll(fresh);
			} catch (Exception e) {
				FancyBarsMod.LOGGER.warn("[SBCLIENT] Couldn't read the shared player size list", e);
			}
		}).exceptionally(e -> null);
	}

	/** The size to draw the player with the given entity id at (1 = normal). */
	public static float scaleFor(int entityId) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null) return 1;
		Entity entity = mc.level.getEntity(entityId);
		if (!(entity instanceof Player player)) return 1;
		FancyBarsConfig config = FancyBarsConfig.get();
		if (player == mc.player) {
			if (config.playerSizeEnabled) return Math.clamp(config.playerSize, MIN, MAX);
		}
		if (!config.playerSizeShowOthers && player != mc.player) return 1;
		Float shared = SHARED.get(player.getGameProfile().name().toLowerCase(Locale.ROOT));
		return shared != null ? shared : 1;
	}
}
