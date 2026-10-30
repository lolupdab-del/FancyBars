package dev.fancybars.mixin;

import java.util.List;
import java.util.Optional;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.inventory.tooltip.TooltipComponent;

import dev.fancybars.storage.BackpackPreview;

/**
 * Replaces the tooltip of ender chest pages / backpacks in the Storage menu with a preview of their contents
 * (same injection point as Skyblocker).
 */
@Mixin(AbstractContainerScreen.class)
public abstract class ContainerScreenBackpackMixin extends Screen {
	@Shadow
	protected @Nullable Slot hoveredSlot;

	protected ContainerScreenBackpackMixin(Component title) {
		super(title);
	}

	@WrapOperation(method = "extractTooltip", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;setTooltipForNextFrame(Lnet/minecraft/client/gui/Font;Ljava/util/List;Ljava/util/Optional;IILnet/minecraft/resources/Identifier;)V"))
	private void fancybars$backpackPreview(GuiGraphicsExtractor graphics, Font font, List<Component> text, Optional<TooltipComponent> data, int x, int y, @Nullable Identifier texture, Operation<Void> original) {
		Slot slot = this.hoveredSlot;
		if (slot != null && this.minecraft.player != null && slot.container != this.minecraft.player.getInventory()
				&& this.getTitle().getString().equals("Storage") && BackpackPreview.wantsPreview()
				&& BackpackPreview.extractPreview(graphics, this, slot.getContainerSlot(), x, y)) {
			return;
		}
		original.call(graphics, font, text, data, x, y, texture);
	}
}
