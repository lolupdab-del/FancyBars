package dev.fancybars;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mojang.brigadier.Command;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.resources.Identifier;

import dev.fancybars.bars.BarEditorScreen;
import dev.fancybars.bars.BarsMenuScreen;
import dev.fancybars.bars.FancyStatusBars;
import dev.fancybars.bars.HeldItemName;
import dev.fancybars.gifs.GifInventory;
import dev.fancybars.protection.ItemProtection;
import dev.fancybars.bars.StatusBarTracker;
import dev.fancybars.bars.VanillaStyleManaBar;
import dev.fancybars.config.FancyBarsConfig;
import dev.fancybars.util.SkyblockLocation;

/**
 * Standalone port of Skyblocker's Fancy Status Bars.
 * Original code by the Skyblocker team (https://github.com/SkyblockerMod/Skyblocker), licensed under LGPL-3.0-or-later.
 */
public class FancyBarsMod implements ClientModInitializer {
	public static final String NAMESPACE = "fancybars";
	public static final Logger LOGGER = LoggerFactory.getLogger("FancyBars");
	public static final Path CONFIG_DIR = FabricLoader.getInstance().getConfigDir().resolve(NAMESPACE);
	public static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	public static final String VERSION = FabricLoader.getInstance().getModContainer(NAMESPACE)
			.map(container -> container.getMetadata().getVersion().getFriendlyString().split("\\+")[0])
			.orElse("?");
	public static final ExecutorService VIRTUAL_THREAD_EXECUTOR = Executors.newVirtualThreadPerTaskExecutor();

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(NAMESPACE, path);
	}

	@Override
	public void onInitializeClient() {
		try {
			Files.createDirectories(CONFIG_DIR);
		} catch (Exception e) {
			LOGGER.error("[FancyBars] Failed to create config directory", e);
		}

		FancyBarsConfig.load();
		SkyblockLocation.init();
		StatusBarTracker.init();
		FancyStatusBars.initStatic();
		VanillaStyleManaBar.init();
		HeldItemName.init();
		ItemProtection.init();
		GifInventory.init();

		ClientCommandRegistrationCallback.EVENT.register((dispatcher, _) -> dispatcher.register(
				ClientCommands.literal(NAMESPACE)
						.executes(_ -> queueOpenScreen(BarsMenuScreen.openAt(null, BarsMenuScreen.Category.HOME)))
						.then(ClientCommands.literal("edit").executes(_ -> queueOpenScreen(new BarEditorScreen(null))))
						.then(ClientCommands.literal("protect").executes(_ -> ItemProtection.protectHeldCommand()))
						.then(ClientCommands.literal("config").executes(_ -> queueOpenScreen(BarsMenuScreen.openAt(null, BarsMenuScreen.Category.GENERAL))))));
	}

	/**
	 * Opens a screen on the next tick so it isn't immediately closed by the chat screen closing.
	 */
	public static int queueOpenScreen(Screen screen) {
		Minecraft.getInstance().schedule(() -> Minecraft.getInstance().gui.setScreen(screen));
		return Command.SINGLE_SUCCESS;
	}
}
