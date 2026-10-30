package dev.fancybars.items;

import org.jspecify.annotations.Nullable;

import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import dev.fancybars.FancyBarsMod;
import dev.fancybars.config.FancyBarsConfig;
import dev.fancybars.mixin.ContainerScreenAccessor;
import dev.fancybars.protection.ItemProtection;
import dev.fancybars.util.SkyblockLocation;

/**
 * Item customisation from Skyblocker: make any SkyBlock item (anything with a UUID) look like any other item.
 * Purely visual and only for you. Open it with the paintbrush in your inventory or /sbclient items.
 */
public final class ItemChanger {
	public static final Identifier PAINTBRUSH = FancyBarsMod.id("paintbrush");

	private ItemChanger() {}

	public static void init() {
		ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> {
			FancyBarsConfig config = FancyBarsConfig.get();
			if (!config.itemCustomizationEnabled || !config.itemCustomizationButton || !SkyblockLocation.isOnSkyblock()) return;
			if (!(screen instanceof InventoryScreen inventory)) return;
			ContainerScreenAccessor accessor = (ContainerScreenAccessor) inventory;
			PaintbrushButton button = new PaintbrushButton(accessor.fancybars$getLeftPos() + 63, accessor.fancybars$getTopPos() + 10);
			Screens.getWidgets(inventory).add(button);
			inventory.registerRecipeBookToggleCallback(() -> button.setPosition(accessor.fancybars$getLeftPos() + 63, accessor.fancybars$getTopPos() + 10));
		});
	}

	/** The model an item should be drawn with, or null to keep its own. */
	public static @Nullable Identifier modelFor(ItemStack stack) {
		FancyBarsConfig config = FancyBarsConfig.get();
		if (!config.itemCustomizationEnabled || config.customItemModels.isEmpty() || stack.isEmpty()) return null;
		String uuid = ItemProtection.getUuid(stack);
		if (uuid.isEmpty()) return null;
		String model = config.customItemModels.get(uuid);
		return model == null ? null : Identifier.tryParse(model);
	}

	public static @Nullable Identifier defaultModel(Item item) {
		return new ItemStack(item).get(DataComponents.ITEM_MODEL);
	}

	public static void set(String uuid, Item lookLike) {
		Identifier model = defaultModel(lookLike);
		if (model == null) return;
		FancyBarsConfig.get().customItemModels.put(uuid, model.toString());
		FancyBarsConfig.save();
	}

	public static void reset(String uuid) {
		FancyBarsConfig.get().customItemModels.remove(uuid);
		FancyBarsConfig.save();
	}

	/** Skyblocker's little paintbrush button, top of the player preview in the inventory. */
	private static final class PaintbrushButton extends AbstractWidget {
		PaintbrushButton(int x, int y) {
			super(x, y, 10, 10, Component.translatable("fancybars.items.button"));
			setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.translatable("fancybars.items.button")));
		}

		@Override
		protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
			graphics.blitSprite(RenderPipelines.GUI_TEXTURED, PAINTBRUSH, getX(), getY(), getWidth(), getHeight(), isHovered() ? 0xFFFAFA96 : 0x80FFFFFF);
			handleCursor(graphics);
		}

		@Override
		public void onClick(MouseButtonEvent click, boolean doubled) {
			Minecraft mc = Minecraft.getInstance();
			mc.gui.setScreen(new ItemChangerScreen(mc.gui.screen()));
		}

		@Override
		protected void updateWidgetNarration(NarrationElementOutput output) {}
	}
}
