package dev.fancybars.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;

import dev.fancybars.storage.StorageOverlay;

/** The Storage Overlay draws instead of the chest background. */
@Mixin(ContainerScreen.class)
public abstract class StorageOverlayBackgroundMixin {
	@Inject(method = "extractBackground", at = @At("HEAD"), cancellable = true)
	private void fancybars$storageBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta, CallbackInfo ci) {
		ContainerScreen screen = (ContainerScreen) (Object) this;
		if (!StorageOverlay.active(screen)) return;
		StorageOverlay.renderBackground(screen, graphics, mouseX, mouseY);
		ci.cancel();
	}
}
