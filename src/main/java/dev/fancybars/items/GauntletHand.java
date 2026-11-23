package dev.fancybars.items;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import dev.fancybars.config.FancyBarsConfig;
import dev.fancybars.items.list.ItemRepo;
import dev.fancybars.util.SkyblockLocation;

/**
 * Worn Gemstone Gauntlet: in first person, the Gemstone Gauntlet (a player head) is worn over your fist like a glove,
 * instead of being held out in front of you as a block. Your arm is drawn as if your hand were empty and the head is
 * drawn around the hand.
 */
public final class GauntletHand {
	private GauntletHand() {}

	/** The gauntlet being worn while the first-person hand is drawn (render thread only), else empty. */
	private static ItemStack current = ItemStack.EMPTY;
	private static final ItemStackRenderState STATE = new ItemStackRenderState();

	public static boolean isGauntlet(ItemStack stack) {
		return !stack.isEmpty() && stack.is(Items.PLAYER_HEAD) && "GEMSTONE_GAUNTLET".equals(ItemRepo.idOf(stack));
	}

	/**
	 * Called with the item about to be drawn in a first-person hand: the gauntlet in your main hand is swapped for an
	 * empty hand (so Minecraft draws your arm) and remembered for {@link #drawOnFist}.
	 */
	public static ItemStack swap(ItemStack stack, InteractionHand hand) {
		current = ItemStack.EMPTY;
		if (hand != InteractionHand.MAIN_HAND || !FancyBarsConfig.get().gauntletOnHand || !SkyblockLocation.isOnSkyblock() || !isGauntlet(stack)) return stack;
		current = stack;
		return ItemStack.EMPTY;
	}

	public static void done() {
		current = ItemStack.EMPTY;
	}

	/** Draws the gauntlet around the fist of {@code arm} (the first-person arm model, its pose already set). */
	public static void drawOnFist(PoseStack pose, SubmitNodeCollector collector, int light, ModelPart arm, boolean right) {
		ItemStack stack = current;
		if (stack.isEmpty()) return;
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null || mc.player == null) return;
		pose.pushPose();
		arm.translateAndRotate(pose);
		// the arm is 4 px wide and 12 long; the gauntlet sits over its lower half (the hand), a little wider than it
		pose.translate((right ? -1f : 1f) / 16f, 7.5f / 16f, 0f);
		// drawn with no display transform (NONE): the head is then an 8 px cube from y -0.5 to 0, so it's moved to be centred
		// on the fist and turned in place (lined up in game: opening on top, thumb on the inside of the hand)
		// Hypixel's gauntlet texture has the thumb on the wrong side for a right hand, so it's mirrored side to side
		// (player heads are drawn without back-face culling, so a mirrored head still draws correctly)
		pose.scale(-1f, 1f, 1f);
		pose.mulPose(Axis.YP.rotationDegrees(270));
		pose.mulPose(Axis.ZP.rotationDegrees(180));
		pose.mulPose(Axis.XP.rotationDegrees(90));
		pose.mulPose(Axis.YP.rotationDegrees(180));
		// the head is 8 px across: 0.69 makes it about 5.5 px, just wider than the 4 px arm (smaller and it hid inside it)
		float size = 0.69f * Math.clamp(FancyBarsConfig.get().gauntletSize, 0.5f, 1.5f);
		pose.scale(size, -size, -size);
		pose.translate(0f, 0.25f, 0f);
		mc.getItemModelResolver().updateForTopItem(STATE, stack, ItemDisplayContext.NONE, mc.level, mc.player, 0);
		STATE.submit(pose, collector, light, OverlayTexture.NO_OVERLAY, 0);
		pose.popPose();
	}
}
