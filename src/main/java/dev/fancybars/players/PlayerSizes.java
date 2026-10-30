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

	/** {width, height, length}. */
	private static final Map<String, float[]> SHARED = new ConcurrentHashMap<>();
	private static final float[] NORMAL = {1, 1, 1};
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
				Map<String, float[]> fresh = new java.util.HashMap<>();
				for (Map.Entry<String, JsonElement> entry : players.entrySet()) {
					// either one number for all axes, or {"width": .., "height": .., "length": ..}
					JsonElement value = entry.getValue();
					float[] size;
					if (value.isJsonObject()) {
						JsonObject o = value.getAsJsonObject();
						size = new float[]{axis(o, "width"), axis(o, "height"), axis(o, "length")};
					} else {
						float all = Math.clamp(value.getAsFloat(), MIN, MAX);
						size = new float[]{all, all, all};
					}
					fresh.put(entry.getKey().toLowerCase(Locale.ROOT), size);
				}
				SHARED.clear();
				SHARED.putAll(fresh);
			} catch (Exception e) {
				FancyBarsMod.LOGGER.warn("[SBCLIENT] Couldn't read the shared player size list", e);
			}
		}).exceptionally(e -> null);
	}

	private static float axis(JsonObject o, String name) {
		return o.has(name) ? Math.clamp(o.get(name).getAsFloat(), MIN, MAX) : 1;
	}

	/** {width, height, length} to draw the player with the given entity id at (1 = normal). */
	public static float[] scaleFor(int entityId) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null) return NORMAL;
		return scaleFor(mc.level.getEntity(entityId));
	}

	public static float[] scaleFor(Entity entity) {
		Minecraft mc = Minecraft.getInstance();
		if (!(entity instanceof Player player)) return NORMAL;
		FancyBarsConfig config = FancyBarsConfig.get();
		if (player == mc.player && config.playerSizeEnabled) {
			return new float[]{Math.clamp(config.playerSizeWidth, MIN, MAX), Math.clamp(config.playerSizeHeight, MIN, MAX), Math.clamp(config.playerSizeLength, MIN, MAX)};
		}
		if (!config.playerSizeShowOthers && player != mc.player) return NORMAL;
		float[] shared = SHARED.get(player.getGameProfile().name().toLowerCase(Locale.ROOT));
		return shared != null ? shared : NORMAL;
	}
}
