package dev.fancybars.util.gui;

import java.util.IdentityHashMap;
import java.util.Map;

import org.joml.Matrix3x2f;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.item.TrackingItemStackRenderState;
import net.minecraft.client.renderer.state.gui.GuiItemRenderState;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/**
 * Draws items without rebuilding their model every frame. {@code graphics.item()} works out the item's whole model
 * from scratch each call; for screens full of items that don't change (the Storage Overlay's cached pages, the Item
 * List) that's most of the frame. Here the model is worked out once per item and reused.
 */
public final class CachedItems {
	private static final Map<ItemStack, TrackingItemStackRenderState> STATES = new IdentityHashMap<>();

	private CachedItems() {}

	public static void draw(GuiGraphicsExtractor graphics, ItemStack stack, int x, int y) {
		if (stack.isEmpty()) return;
		Minecraft mc = Minecraft.getInstance();
		TrackingItemStackRenderState state = STATES.get(stack);
		if (state == null) {
			if (STATES.size() > 6000) STATES.clear();
			state = new TrackingItemStackRenderState();
			mc.getItemModelResolver().updateForTopItem(state, stack, ItemDisplayContext.GUI, mc.level, mc.player, 0);
			STATES.put(stack, state);
		}
		if (state.isEmpty()) return;
		graphics.guiRenderState.addItem(new GuiItemRenderState(new Matrix3x2f(graphics.pose()), state, x, y, graphics.scissorStack.peek()));
	}

	/** Forget everything (when the cached items change, e.g. a new resource pack). */
	public static void clear() {
		STATES.clear();
	}
}
