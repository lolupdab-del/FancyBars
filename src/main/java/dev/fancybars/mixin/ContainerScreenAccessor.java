package dev.fancybars.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;

@Mixin(AbstractContainerScreen.class)
public interface ContainerScreenAccessor {
	@Accessor("leftPos")
	int fancybars$getLeftPos();

	@Accessor("topPos")
	int fancybars$getTopPos();

	@Accessor("imageWidth")
	int fancybars$getImageWidth();

	@Accessor("imageHeight")
	int fancybars$getImageHeight();
}
