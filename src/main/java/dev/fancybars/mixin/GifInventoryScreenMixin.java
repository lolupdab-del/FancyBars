package dev.fancybars.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;

import dev.fancybars.gifs.GifInventory;

/**
 * Draws inventory GIFs behind the player model, and hides the "Crafting" label (Inventory GIFs).
 */
@Mixin(InventoryScreen.class)
public abstract class GifInventoryScreenMixin {
	@Inject(method = "extractBackground", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screens/inventory/InventoryScreen;extractEntityInInventoryFollowsMouse(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIIIIFFFLnet/minecraft/world/entity/LivingEntity;)V"), require = 0)
	private void fancybars$beforePlayer(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta, CallbackInfo ci) {
		GifInventory.drawGifs((InventoryScreen) (Object) this, graphics, false);
	}

	@Inject(method = "extractBackground", at = @At("TAIL"), require = 0)
	private void fancybars$afterBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta, CallbackInfo ci) {
		GifInventory.drawGifs((InventoryScreen) (Object) this, graphics, true);
	}

	@Inject(method = "extractLabels", at = @At("HEAD"), cancellable = true, require = 0)
	private void fancybars$hideCraftingLabel(GuiGraphicsExtractor graphics, int mouseX, int mouseY, CallbackInfo ci) {
		if (GifInventory.hideCraftingGrid()) ci.cancel();
	}
}
