package dev.fancybars.items;

import com.mojang.blaze3d.vertex.PoseStack;

import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import org.jspecify.annotations.Nullable;

import dev.fancybars.config.FancyBarsConfig;
import dev.fancybars.util.SkyblockLocation;

/**
 * Ether Transmission outline (like Odin's Etherwarp feature): while sneaking with an etherwarp item (an Aspect of the
 * Void / End with Etherwarp merged, or an Etherwarp Conduit) it outlines the block you'll teleport to, in one colour
 * if the teleport will work and another if it won't.
 */
public final class EtherwarpOutline {
	private EtherwarpOutline() {}

	public static void init() {
		LevelRenderEvents.COLLECT_SUBMITS.register(context -> {
			FancyBarsConfig c = FancyBarsConfig.get();
			if (!c.etherwarpOutline || !SkyblockLocation.isOnSkyblock()) return;
			Minecraft mc = Minecraft.getInstance();
			LocalPlayer player = mc.player;
			if (player == null || mc.level == null || !player.isShiftKeyDown()) return;
			int range = range(player.getMainHandItem());
			if (range <= 0) return;
			float partial = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
			// your real (modern) sneaking eye height: the 1.8 one (1.54) aims too high and goes over low blocks like stairs
			Vec3 eye = player.getEyePosition(partial);
			Vec3 look = player.getViewVector(partial);
			BlockPos target = trace(mc.level, eye, look, range);
			if (target == null) return;
			boolean valid = valid(mc.level, target);
			if (!valid && !c.etherwarpShowInvalid) return;
			int color = 0xFF000000 | (valid ? c.etherwarpColor : c.etherwarpInvalidColor);
			VoxelShape shape = mc.level.getBlockState(target).getShape(mc.level, target);
			if (shape.isEmpty()) shape = Shapes.block();
			Vec3 cam = context.levelState().cameraRenderState.pos;
			PoseStack pose = context.poseStack();
			pose.pushPose();
			pose.translate(target.getX() - cam.x, target.getY() - cam.y, target.getZ() - cam.z);
			context.submitNodeCollector().submitShapeOutline(pose, shape, RenderTypes.lines(), color, Math.max(0.5f, c.etherwarpLineWidth), false);
			pose.popPose();
		});
	}

	/** Etherwarp range for the held item (57 blocks + Tuned Transmission), 0 if it can't etherwarp. */
	private static int range(ItemStack stack) {
		if (stack.isEmpty()) return 0;
		CompoundTag data = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
		String id = data.getStringOr("id", "");
		boolean ether = id.equals("ETHERWARP_CONDUIT")
				|| (id.equals("ASPECT_OF_THE_VOID") || id.equals("ASPECT_OF_THE_END")) && data.getIntOr("ethermerge", 0) == 1;
		if (!ether) return 0;
		return 57 + data.getIntOr("tuned_transmission", 0);
	}

	/** Blocks etherwarp flies through (no collision: air, grass, flowers...). */
	private static boolean passable(Level level, BlockPos pos) {
		return level.getBlockState(pos).getCollisionShape(level, pos).isEmpty();
	}

	/** You land on top of the block, so the two blocks above it need to be free. */
	private static boolean valid(Level level, BlockPos target) {
		return !passable(level, target) && passable(level, target.above()) && passable(level, target.above(2));
	}

	/** Walks block by block along the look direction (like the server does) to the first solid block in range. */
	private static @Nullable BlockPos trace(Level level, Vec3 start, Vec3 dir, int range) {
		int x = Mth.floor(start.x), y = Mth.floor(start.y), z = Mth.floor(start.z);
		int stepX = (int) Math.signum(dir.x), stepY = (int) Math.signum(dir.y), stepZ = (int) Math.signum(dir.z);
		double tDeltaX = stepX == 0 ? Double.MAX_VALUE : Math.abs(1 / dir.x);
		double tDeltaY = stepY == 0 ? Double.MAX_VALUE : Math.abs(1 / dir.y);
		double tDeltaZ = stepZ == 0 ? Double.MAX_VALUE : Math.abs(1 / dir.z);
		double tMaxX = stepX == 0 ? Double.MAX_VALUE : (stepX > 0 ? x + 1 - start.x : start.x - x) * tDeltaX;
		double tMaxY = stepY == 0 ? Double.MAX_VALUE : (stepY > 0 ? y + 1 - start.y : start.y - y) * tDeltaY;
		double tMaxZ = stepZ == 0 ? Double.MAX_VALUE : (stepZ > 0 ? z + 1 - start.z : start.z - z) * tDeltaZ;
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (int i = 0; i < range * 3 + 3; i++) {
			pos.set(x, y, z);
			if (!level.isLoaded(pos)) return null;
			if (!passable(level, pos)) return pos.immutable();
			if (tMaxX < tMaxY && tMaxX < tMaxZ) {
				if (tMaxX > range) return null;
				x += stepX;
				tMaxX += tDeltaX;
			} else if (tMaxY < tMaxZ) {
				if (tMaxY > range) return null;
				y += stepY;
				tMaxY += tDeltaY;
			} else {
				if (tMaxZ > range) return null;
				z += stepZ;
				tMaxZ += tDeltaZ;
			}
		}
		return null;
	}
}
