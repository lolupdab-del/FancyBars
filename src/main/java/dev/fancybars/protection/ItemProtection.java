package dev.fancybars.protection;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.brigadier.Command;
import org.joml.Matrix3x2fStack;
import org.jspecify.annotations.Nullable;

import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

import dev.fancybars.FancyBarsMod;
import dev.fancybars.config.FancyBarsConfig;
import dev.fancybars.mixin.CustomDataAccessor;
import dev.fancybars.util.ItemUtils;
import dev.fancybars.util.SkyblockLocation;

/**
 * Item protection, ported from Skyblocker: protected items can't be dropped, sold to NPCs, salvaged, traded,
 * auctioned or put in item frames. A star is drawn on protected items; its colour and corner are configurable.
 */
public class ItemProtection {
	public static final KeyMapping.Category KEY_CATEGORY = KeyMapping.Category.register(FancyBarsMod.id("main"));
	private static final Identifier STAR = FancyBarsMod.id("protection_star");
	/** Star sprite is 8x9 texels, drawn at half scale (4 x 4.5 GUI px). */
	private static final float STAR_W = 4f;
	private static final float STAR_H = 4.5f;

	public static KeyMapping protectKey;

	public static void init() {
		protectKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.fancybars.itemProtection", InputConstants.KEY_V, KEY_CATEGORY));

		// Don't let protected items go into item frames on your island / garden
		UseEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
			if (!world.isClientSide() || !SkyblockLocation.isOnSkyblock() || !FancyBarsConfig.get().itemProtectionEnabled) return InteractionResult.PASS;
			if (!(SkyblockLocation.isOnPrivateIsland() || SkyblockLocation.isInGarden())) return InteractionResult.PASS;
			if (entity instanceof ItemFrame frame && frame.getItem().isEmpty() && isItemProtected(player.getItemInHand(hand))) {
				return InteractionResult.FAIL;
			}
			return InteractionResult.PASS;
		});
	}

	// ---------------------------------------------------------------- Protection state

	public static String getUuid(@Nullable ItemStack stack) {
		if (stack == null || stack.isEmpty()) return "";
		CustomData data = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
		return ((CustomDataAccessor) (Object) data).getTag().getStringOr("uuid", "");
	}

	public static boolean isItemProtected(@Nullable ItemStack stack) {
		if (!FancyBarsConfig.get().itemProtectionEnabled) return false;
		String uuid = getUuid(stack);
		return !uuid.isEmpty() && FancyBarsConfig.get().protectedItems.contains(uuid);
	}

	public static int protectedCount() {
		return FancyBarsConfig.get().protectedItems.size();
	}

	public static void clearAll() {
		FancyBarsConfig.get().protectedItems.clear();
		FancyBarsConfig.get().protectedItemInfo.clear();
		FancyBarsConfig.save();
	}

	/** Unprotects a single item by UUID (used by the list in the menu). */
	public static void remove(String uuid) {
		FancyBarsConfig.get().protectedItems.remove(uuid);
		FancyBarsConfig.get().protectedItemInfo.remove(uuid);
		FancyBarsConfig.save();
	}

	/** Remembers the name, colour and item type of a protected item so the menu can list it. */
	public static void rememberInfo(ItemStack stack) {
		String uuid = getUuid(stack);
		if (uuid.isEmpty()) return;
		FancyBarsConfig.ProtectedItem info = new FancyBarsConfig.ProtectedItem();
		info.name = stack.getHoverName().getString();
		net.minecraft.network.chat.TextColor color = stack.getHoverName().getStyle().getColor();
		if (color == null && !stack.getHoverName().getSiblings().isEmpty()) color = stack.getHoverName().getSiblings().getFirst().getStyle().getColor();
		info.color = color != null ? color.getValue() : 0xFFFFFF;
		info.itemId = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
		FancyBarsConfig.get().protectedItemInfo.put(uuid, info);
	}

	/** Fills in names for protected items found in the player's inventory (e.g. ones protected before names were saved). */
	public static void refreshFromInventory() {
		LocalPlayer player = Minecraft.getInstance().player;
		if (player == null) return;
		FancyBarsConfig config = FancyBarsConfig.get();
		boolean changed = false;
		for (ItemStack stack : player.getInventory().getNonEquipmentItems()) {
			String uuid = getUuid(stack);
			if (!uuid.isEmpty() && config.protectedItems.contains(uuid)) {
				FancyBarsConfig.ProtectedItem old = config.protectedItemInfo.get(uuid);
				rememberInfo(stack);
				if (old == null || !old.name.equals(config.protectedItemInfo.get(uuid).name)) changed = true;
			}
		}
		if (changed) FancyBarsConfig.save();
	}

	/** Finds the live stack for a protected item in the player's inventory, if they're carrying it. */
	public static @Nullable ItemStack findInInventory(String uuid) {
		LocalPlayer player = Minecraft.getInstance().player;
		if (player == null) return null;
		for (ItemStack stack : player.getInventory().getNonEquipmentItems()) {
			if (uuid.equals(getUuid(stack))) return stack;
		}
		return null;
	}

	/** An icon for a protected item: the real stack if carried, otherwise its saved item type. */
	public static ItemStack iconFor(String uuid) {
		ItemStack live = findInInventory(uuid);
		if (live != null) return live;
		FancyBarsConfig.ProtectedItem info = FancyBarsConfig.get().protectedItemInfo.get(uuid);
		try {
			if (info != null) {
				return new ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(Identifier.parse(info.itemId)));
			}
		} catch (RuntimeException ignored) {
		}
		return new ItemStack(net.minecraft.world.item.Items.PAPER);
	}

	/**
	 * Toggles protection of an item and tells the player.
	 */
	public static void toggle(ItemStack stack) {
		LocalPlayer player = Minecraft.getInstance().player;
		if (player == null) return;
		if (!FancyBarsConfig.get().itemProtectionEnabled) return;
		if (!SkyblockLocation.isOnSkyblock()) {
			player.sendSystemMessage(prefix().append(Component.translatable("fancybars.protection.notOnSkyblock").withStyle(ChatFormatting.RED)));
			return;
		}
		String uuid = getUuid(stack);
		if (uuid.isEmpty()) {
			player.sendSystemMessage(prefix().append(Component.translatable("fancybars.protection.noUuid").withStyle(ChatFormatting.RED)));
			return;
		}

		FancyBarsConfig config = FancyBarsConfig.get();
		boolean nowProtected;
		if (config.protectedItems.contains(uuid)) {
			config.protectedItems.remove(uuid);
			config.protectedItemInfo.remove(uuid);
			nowProtected = false;
		} else {
			config.protectedItems.add(uuid);
			rememberInfo(stack);
			nowProtected = true;
		}
		FancyBarsConfig.save();

		if (config.protectionChatNotification) {
			player.sendSystemMessage(prefix().append(nowProtected
					? Component.translatable("fancybars.protection.added", stack.getHoverName()).withStyle(ChatFormatting.GREEN)
					: Component.translatable("fancybars.protection.removed", stack.getHoverName()).withStyle(ChatFormatting.YELLOW)));
		}
	}

	public static int protectHeldCommand() {
		LocalPlayer player = Minecraft.getInstance().player;
		if (player != null) toggle(player.getMainHandItem());
		return Command.SINGLE_SUCCESS;
	}

	/** Called from the keybind handler when no screen is open. */
	public static void handleHotbarKey(LocalPlayer player) {
		while (protectKey.consumeClick()) {
			toggle(player.getMainHandItem());
		}
	}

	private static MutableComponent prefix() {
		return Component.literal("[").withStyle(ChatFormatting.DARK_GRAY)
				.append(Component.literal("Fancy Bars").withColor(0x7CC8FF))
				.append(Component.literal("] ").withStyle(ChatFormatting.DARK_GRAY));
	}

	// ---------------------------------------------------------------- Star rendering

	/**
	 * Draws the protection star in the configured corner of a 16x16 slot, tinted with the configured colour.
	 */
	public static void drawStar(GuiGraphicsExtractor graphics, int slotX, int slotY) {
		FancyBarsConfig config = FancyBarsConfig.get();
		drawStar(graphics, slotX, slotY, config.protectionStarCorner, config.protectionStarColor);
	}

	public static void drawStar(GuiGraphicsExtractor graphics, int slotX, int slotY, FancyBarsConfig.StarCorner corner, int color) {
		float x = corner.right ? slotX + 16 - STAR_W : slotX;
		float y = corner.bottom ? slotY + 16 - STAR_H : slotY;
		Matrix3x2fStack pose = graphics.pose();
		pose.pushMatrix();
		pose.translate(x, y);
		pose.scale(0.5f, 0.5f);
		graphics.blitSprite(RenderPipelines.GUI_TEXTURED, STAR, 0, 0, 8, 9, color | 0xFF000000);
		pose.popMatrix();
	}

	// ---------------------------------------------------------------- Menu detection (from Skyblocker)

	public static boolean isPersonalStorage(String screenTitle) {
		return screenTitle.equals("Storage") || screenTitle.startsWith("Storage (")
				|| screenTitle.equals("Rift Storage") || screenTitle.startsWith("Rift Storage (")
				|| screenTitle.startsWith("Ender Chest")
				|| screenTitle.startsWith("Chest")
				|| screenTitle.startsWith("Trapped Chest")
				|| (screenTitle.contains("Backpack") && screenTitle.contains("(Slot #"))
				|| screenTitle.startsWith("Chest Storage")
				|| screenTitle.startsWith("Medium Shelves")
				|| screenTitle.startsWith("Wood Chest")
				|| screenTitle.startsWith("Diamond Chest")
				|| screenTitle.startsWith("Emerald Chest")
				|| screenTitle.startsWith("Iron Chest")
				|| screenTitle.startsWith("Gold Chest")
				|| screenTitle.startsWith("Lapis Chest")
				|| screenTitle.startsWith("Redstone Chest")
				|| screenTitle.startsWith("Endstone Chest")
				|| screenTitle.startsWith("Skull Chest")
				|| screenTitle.startsWith("Weapon Rack")
				|| screenTitle.startsWith("Armor Stand")
				|| screenTitle.contains("Stats & Equipment")
				|| screenTitle.contains("Equipment Sets")
				|| screenTitle.contains("Armor Sets")
				|| screenTitle.contains("Loadouts")
				|| screenTitle.contains("Pets");
	}

	private static boolean hasBuybackLore(ItemStack stack) {
		for (String line : ItemUtils.getLoreStrings(stack)) {
			if (line.contains("buyback")) return true;
		}
		return false;
	}

	public static boolean isNpcSellMenu(AbstractContainerMenu menu) {
		for (Slot slot : menu.slots) {
			ItemStack stack = slot.getItem();
			if (stack.isEmpty()) continue;
			String name = stack.getHoverName().getString();
			if (name.equals("Sell Item") || name.equals("Sell Inventory")) return true;
			if (hasBuybackLore(stack)) return true;
		}
		return false;
	}

	public static boolean isNpcSellButton(Slot slot) {
		String name = slot.getItem().getHoverName().getString();
		return name.equals("Sell Item") || name.equals("Sell Inventory") || hasBuybackLore(slot.getItem());
	}

	public static boolean isOnSkyblockAndEnabled() {
		return SkyblockLocation.isOnSkyblock() && FancyBarsConfig.get().itemProtectionEnabled;
	}

	public static boolean isPlayerInventory(Slot slot, Player player) {
		return slot.container == player.getInventory();
	}
}
