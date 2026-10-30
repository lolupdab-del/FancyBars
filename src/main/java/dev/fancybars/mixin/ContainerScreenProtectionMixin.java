package dev.fancybars.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import dev.fancybars.protection.ItemProtection;
import dev.fancybars.util.SkyblockLocation;

/**
 * Item protection inside inventories (same injection points as Skyblocker):
 * protect hovered items with the keybind, block dropping / selling / salvaging / trading / auctioning protected items,
 * and draw the protection star on slots.
 */
@Mixin(AbstractContainerScreen.class)
public abstract class ContainerScreenProtectionMixin<T extends AbstractContainerMenu> extends Screen {
	@Unique
	private static final int OUT_OF_BOUNDS_SLOT = -999;

	@Shadow
	protected @Nullable Slot hoveredSlot;

	@Shadow
	@Final
	protected T menu;

	protected ContainerScreenProtectionMixin(Component title) {
		super(title);
	}

	@Inject(method = "keyPressed", at = @At("HEAD"))
	private void fancybars$protectHovered(KeyEvent input, CallbackInfoReturnable<Boolean> cir) {
		Slot slot = this.hoveredSlot;
		if (this.minecraft.player == null || slot == null || input.isEscape() || !ItemProtection.isOnSkyblockAndEnabled()) return;
		if (ItemProtection.protectKey != null && ItemProtection.protectKey.matches(input)) {
			ItemProtection.protectKey.consumeClick();
			boolean ownItem = ItemProtection.isPlayerInventory(slot, this.minecraft.player) || ItemProtection.isPersonalStorage(this.getTitle().getString());
			if (ownItem) ItemProtection.toggle(slot.getItem());
		}
	}

	@Inject(method = "slotClicked(Lnet/minecraft/world/inventory/Slot;IILnet/minecraft/world/inventory/ContainerInput;)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/MultiPlayerGameMode;handleContainerInput(IIILnet/minecraft/world/inventory/ContainerInput;Lnet/minecraft/world/entity/player/Player;)V"), cancellable = true)
	private void fancybars$blockProtectedClicks(@Nullable Slot slot, int slotId, int button, ContainerInput containerInput, CallbackInfo ci) {
		if (!ItemProtection.isOnSkyblockAndEnabled()) return;

		// Picking the item up and clicking outside the window drops it
		if (slotId == OUT_OF_BOUNDS_SLOT && ItemProtection.isItemProtected(this.menu.getCarried())) {
			ci.cancel();
			return;
		}
		if (slot == null) return;
		ItemStack stack = slot.getItem();
		if (!ItemProtection.isItemProtected(stack)) return;
		String title = this.getTitle().getString();

		// Drop key while hovering
		if (containerInput == ContainerInput.THROW) {
			ci.cancel();
			return;
		}
		// Salvaging
		if (title.equals("Salvage Items")) {
			ci.cancel();
			return;
		}
		// Trading
		if (title.startsWith("You  ")) {
			ci.cancel();
			return;
		}
		// Auctioning
		if (title.endsWith("Auction House") || title.equals("Create Auction") || title.equals("Create BIN Auction")) {
			ci.cancel();
			return;
		}
		// Swapping with a dungeon secret chest item (which drops yours if not taken back out)
		if (SkyblockLocation.isInDungeons() && title.equals("Chest")) {
			ci.cancel();
			return;
		}
		// Selling to NPC shops
		if (this.menu instanceof ChestMenu && !ItemProtection.isNpcSellButton(slot) && ItemProtection.isNpcSellMenu(this.menu)) {
			ci.cancel();
		}
	}

	@Inject(method = "extractSlot", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;item(Lnet/minecraft/world/item/ItemStack;III)V"))
	private void fancybars$drawProtectionStar(CallbackInfo ci, @Local(name = "graphics") GuiGraphicsExtractor graphics, @Local(name = "slot") Slot slot) {
		if (SkyblockLocation.isOnSkyblock() && ItemProtection.isItemProtected(slot.getItem())) {
			ItemProtection.drawStar(graphics, slot.x, slot.y);
		}
	}
}
