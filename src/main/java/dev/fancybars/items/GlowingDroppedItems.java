package dev.fancybars.items;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;

import dev.fancybars.config.FancyBarsConfig;
import dev.fancybars.util.SkyblockLocation;

/** Glowing Dropped Items (from SkyHanni): items on the ground glow in their rarity's colour. */
public final class GlowingDroppedItems {
	private GlowingDroppedItems() {}

	/** Glow colour for this entity, or -1 if it shouldn't get one from us. */
	public static int color(Entity entity) {
		if (!(entity instanceof ItemEntity item) || !FancyBarsConfig.get().glowingDroppedItems || !SkyblockLocation.isOnSkyblock()) return -1;
		if (item.hasGlowingTag()) return -1; // already glowing from the server: leave its colour alone
		int rgb = RarityBackground.colorOf(item.getItem());
		return rgb == -1 ? 0xFFFFFF : rgb; // no rarity: common (white), like SkyHanni
	}
}
