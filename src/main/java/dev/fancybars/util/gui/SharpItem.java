package dev.fancybars.util.gui;

import org.joml.Matrix3x2fStack;
import org.joml.Vector2f;
import org.jspecify.annotations.Nullable;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.vertex.PoseStack;

import net.fabricmc.fabric.api.client.rendering.v1.PictureInPictureRendererRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.pip.PictureInPictureRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.state.gui.pip.PictureInPictureRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/**
 * Draws an item at any size at full screen resolution (like SkyHanni's big item icons): the item model is rendered
 * straight into a texture the size of its box on screen, instead of upscaling Minecraft's 16 px item image.
 */
public final class SharpItem {
	private SharpItem() {}

	public static void init() {
		PictureInPictureRendererRegistry.register(Renderer::new);
	}

	public record State(ItemStack stack, int x0, int y0, int x1, int y1, float scale,
			@Nullable ScreenRectangle scissorArea, @Nullable ScreenRectangle bounds) implements PictureInPictureRenderState {}

	/** Draws {@code stack} filling the square at (x, y) with side {@code size}, in the current pose's units. */
	public static void draw(GuiGraphicsExtractor graphics, ItemStack stack, float x, float y, float size) {
		Matrix3x2fStack pose = graphics.pose();
		Vector2f a = pose.transformPosition(x, y, new Vector2f());
		Vector2f b = pose.transformPosition(x + size, y + size, new Vector2f());
		int x0 = Math.round(Math.min(a.x, b.x));
		int y0 = Math.round(Math.min(a.y, b.y));
		int x1 = Math.round(Math.max(a.x, b.x));
		int y1 = Math.round(Math.max(a.y, b.y));
		if (x1 - x0 < 2 || y1 - y0 < 2) return;
		ScreenRectangle scissor = graphics.scissorStack.peek();
		graphics.guiRenderState.addPicturesInPictureState(new State(stack, x0, y0, x1, y1, Math.min(x1 - x0, y1 - y0), scissor,
				PictureInPictureRenderState.getBounds(x0, y0, x1, y1, scissor)));
	}

	private static final class Renderer extends PictureInPictureRenderer<State> {
		private final ItemStackRenderState renderState = new ItemStackRenderState();

		private Renderer(PictureInPictureRendererRegistry.Context context) {}

		@Override
		public Class<State> getRenderStateClass() {
			return State.class;
		}

		@Override
		protected void renderToTexture(State state, PoseStack matrices, SubmitNodeCollector collector) {
			Minecraft mc = Minecraft.getInstance();
			mc.getItemModelResolver().updateForTopItem(renderState, state.stack(), ItemDisplayContext.GUI, mc.level, mc.player, 0);
			mc.gameRenderer.lighting().setupFor(renderState.usesBlockLight() ? Lighting.Entry.ITEMS_3D : Lighting.Entry.ITEMS_FLAT);
			matrices.pushPose();
			// same orientation as items in the GUI (y up)
			matrices.scale(1, -1, -1);
			renderState.submit(matrices, collector, LightCoordsUtil.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, 0);
			matrices.popPose();
		}

		@Override
		protected float getTranslateY(int height, int guiScale) {
			return height / 2f;
		}

		@Override
		protected String getTextureLabel() {
			return "sgu sharp item";
		}
	}
}
