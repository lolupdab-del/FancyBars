package dev.fancybars.hud;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;

import dev.fancybars.config.FancyBarsConfig;

/** Settings to hide Minecraft's own hearts, hunger and armor bars (absorption hearts: see AbsorptionHeartsMixin). */
public final class VanillaHudToggles {
	private VanillaHudToggles() {}

	public static void init() {
		HudElementRegistry.replaceElement(VanillaHudElements.HEALTH_BAR, original -> (graphics, tickCounter) -> {
			if (!FancyBarsConfig.get().hideVanillaHearts) original.extractRenderState(graphics, tickCounter);
		});
		HudElementRegistry.replaceElement(VanillaHudElements.FOOD_BAR, original -> (graphics, tickCounter) -> {
			if (!FancyBarsConfig.get().hideVanillaHunger) original.extractRenderState(graphics, tickCounter);
		});
		HudElementRegistry.replaceElement(VanillaHudElements.ARMOR_BAR, original -> (graphics, tickCounter) -> {
			if (!FancyBarsConfig.get().hideVanillaArmor) original.extractRenderState(graphics, tickCounter);
		});
	}
}
