package dev.fancybars.util;

import java.util.ArrayList;
import java.util.List;

import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import net.azureaaron.hmapi.events.HypixelPacketEvents;
import net.azureaaron.hmapi.network.HypixelNetworking;
import net.azureaaron.hmapi.network.packet.s2c.HypixelS2CPacket;
import net.azureaaron.hmapi.network.packet.v1.s2c.LocationUpdateS2CPacket;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Util;

/**
 * Tracks whether the player is on SkyBlock (and which island) using the Hypixel Mod API location packets,
 * the same way Skyblocker does.
 */
public class SkyblockLocation {
	private static final List<Runnable> LOCATION_CHANGE_LISTENERS = new ArrayList<>();

	private static boolean onSkyblock = false;
	private static String mode = "";

	public static void init() {
		ClientPlayConnectionEvents.DISCONNECT.register((_, _) -> {
			onSkyblock = false;
			mode = "";
			fireLocationChange();
		});

		HypixelNetworking.registerToEvents(Util.make(new Object2IntOpenHashMap<>(), map -> map.put(LocationUpdateS2CPacket.ID, 1)));
		HypixelPacketEvents.LOCATION_UPDATE.register(SkyblockLocation::onPacket);
	}

	private static void onPacket(HypixelS2CPacket packet) {
		if (packet instanceof LocationUpdateS2CPacket(var _, var serverType, var _, var newMode, var _)) {
			onSkyblock = serverType.orElse("").equals("SKYBLOCK");
			mode = newMode.orElse("");
			fireLocationChange();
		}
	}

	private static void fireLocationChange() {
		for (Runnable listener : LOCATION_CHANGE_LISTENERS) listener.run();
	}

	public static void onLocationChange(Runnable listener) {
		LOCATION_CHANGE_LISTENERS.add(listener);
	}

	public static boolean isOnSkyblock() {
		// Pretend we're always on SkyBlock in singleplayer dev environments so the bars can be tested
		if (FabricLoader.getInstance().isDevelopmentEnvironment() && Minecraft.getInstance().isLocalServer()) return true;
		return onSkyblock;
	}

	public static boolean isInTheRift() {
		return onSkyblock && mode.equals("rift");
	}

	public static boolean isInGarden() {
		return onSkyblock && mode.equals("garden");
	}
}
