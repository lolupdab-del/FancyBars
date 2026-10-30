package dev.fancybars.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;

import dev.fancybars.gifs.GifInventory;

/**
 * Hides the 2x2 crafting grid slots in the player inventory (Inventory GIFs).
 */
@Mixin(AbstractContainerScreen.class)
public abstract class GifContainerScreenMixin {
	@Inject(method = "extractSlot", at = @At("HEAD"), cancellable = true)
	private void fancybars$hideCraftSlot(GuiGraphicsExtractor graphics, Slot slot, int mouseX, int mouseY, CallbackInfo ci) {
		if (GifInventory.isHiddenCraftSlot(this, slot)) ci.cancel();
	}

	@Inject(method = "getHoveredSlot", at = @At("RETURN"), cancellable = true)
	private void fancybars$noHoverCraftSlot(double mouseX, double mouseY, CallbackInfoReturnable<Slot> cir) {
		Slot slot = cir.getReturnValue();
		if (slot != null && GifInventory.isHiddenCraftSlot(this, slot)) cir.setReturnValue(null);
	}
}
