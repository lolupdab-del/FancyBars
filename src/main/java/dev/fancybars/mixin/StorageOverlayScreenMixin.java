package dev.fancybars.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;

import dev.fancybars.storage.StorageOverlay;

/** Hooks for the Storage Overlay (same places Skysoft hooks its own). */
@Mixin(AbstractContainerScreen.class)
public abstract class StorageOverlayScreenMixin {
	@Inject(method = "extractContents", at = @At("HEAD"))
	private void fancybars$layoutStorage(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a, CallbackInfo ci) {
		StorageOverlay.beforeRender((AbstractContainerScreen<?>) (Object) this);
	}

	@Inject(method = "extractLabels", at = @At("HEAD"), cancellable = true)
	private void fancybars$noStorageLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY, CallbackInfo ci) {
		if (StorageOverlay.active((AbstractContainerScreen<?>) (Object) this)) ci.cancel();
	}

	@Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
	private void fancybars$storageClick(MouseButtonEvent click, boolean doubled, CallbackInfoReturnable<Boolean> cir) {
		AbstractContainerScreen<?> screen = (AbstractContainerScreen<?>) (Object) this;
		if (StorageOverlay.mouseClicked(screen, click) || dev.fancybars.items.list.ItemList.mouseClicked(screen, click)) cir.setReturnValue(true);
	}

	@Inject(method = "mouseScrolled", at = @At("HEAD"), cancellable = true)
	private void fancybars$storageScroll(double mouseX, double mouseY, double horizontal, double vertical, CallbackInfoReturnable<Boolean> cir) {
		AbstractContainerScreen<?> screen = (AbstractContainerScreen<?>) (Object) this;
		if (StorageOverlay.mouseScrolled(screen, mouseX, mouseY, vertical) || dev.fancybars.items.list.ItemList.mouseScrolled(screen, mouseX, mouseY, vertical)) cir.setReturnValue(true);
	}

	@Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
	private void fancybars$storageKeys(KeyEvent key, CallbackInfoReturnable<Boolean> cir) {
		AbstractContainerScreen<?> screen = (AbstractContainerScreen<?>) (Object) this;
		if (StorageOverlay.keyPressed(screen, key) || dev.fancybars.items.list.ItemList.keyPressed(screen, key)) cir.setReturnValue(true);
	}

	@Inject(method = "hasClickedOutside", at = @At("HEAD"), cancellable = true)
	private void fancybars$storageOutside(double mouseX, double mouseY, int left, int top, CallbackInfoReturnable<Boolean> cir) {
		AbstractContainerScreen<?> screen = (AbstractContainerScreen<?>) (Object) this;
		if (StorageOverlay.active(screen)) cir.setReturnValue(StorageOverlay.isOutside(screen, mouseX, mouseY));
	}
}
