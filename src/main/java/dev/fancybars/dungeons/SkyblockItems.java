package dev.fancybars.dungeons;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.PropertyMap;
import com.mojang.serialization.JsonOps;

import net.minecraft.core.component.DataComponents;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ResolvableProfile;

/** Builds SkyBlock-looking item stacks: textured player heads and (optionally glinting) vanilla items. */
final class SkyblockItems {
	private SkyblockItems() {}

	static ItemStack head(String texture) {
		ItemStack stack = new ItemStack(Items.PLAYER_HEAD);
		try {
			JsonObject property = new JsonObject();
			property.addProperty("name", "textures");
			property.addProperty("value", texture);
			JsonArray properties = new JsonArray();
			properties.add(property);
			PropertyMap map = ExtraCodecs.PROPERTY_MAP.parse(JsonOps.INSTANCE, properties).getOrThrow();
			UUID id = UUID.nameUUIDFromBytes(texture.getBytes(StandardCharsets.UTF_8));
			stack.set(DataComponents.PROFILE, ResolvableProfile.createResolved(new GameProfile(id, "SkyBlockItem", map)));
		} catch (Exception ignored) {
			// plain head if the texture can't be applied
		}
		return stack;
	}

	static ItemStack item(Item item, boolean glint) {
		ItemStack stack = new ItemStack(item);
		if (glint) stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
		return stack;
	}
}
