package dev.fancybars.items;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

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
import net.minecraft.world.phys.AABB;
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

	/** This frame's target: {x, y, z, valid}, or null. Worked out once per frame and shared by both hooks. */
	private static int @Nullable [] target;
	private static long targetFrame = -1;

	private static int @Nullable [] target() {
		Minecraft mc = Minecraft.getInstance();
		long now = System.nanoTime();
		if (now - targetFrame < 2_000_000L) return target;
		targetFrame = now;
		target = null;
		FancyBarsConfig c = FancyBarsConfig.get();
		LocalPlayer player = mc.player;
		if (!c.etherwarpOutline || !SkyblockLocation.isOnSkyblock() || player == null || mc.level == null || !player.isShiftKeyDown()) return null;
		int range = range(player.getMainHandItem());
		if (range <= 0) return null;
		float partial = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
		// your real (modern) sneaking eye height: the 1.8 one (1.54) aims too high and goes over low blocks like stairs
		BlockPos pos = trace(mc.level, player.getEyePosition(partial), player.getViewVector(partial), range);
		if (pos == null) return null;
		boolean valid = valid(mc.level, pos);
		if (!valid && !c.etherwarpShowInvalid) return null;
		target = new int[]{pos.getX(), pos.getY(), pos.getZ(), valid ? 1 : 0};
		return target;
	}

	public static void init() {
		// Minecraft's own black block outline would sit on top of ours and muddy the colour
		LevelRenderEvents.BEFORE_BLOCK_OUTLINE.register((context, outline) -> target() == null);
		LevelRenderEvents.COLLECT_SUBMITS.register(context -> {
			int[] t = target();
			if (t == null) return;
			FancyBarsConfig c = FancyBarsConfig.get();
			Minecraft mc = Minecraft.getInstance();
			BlockPos pos = new BlockPos(t[0], t[1], t[2]);
			int rgb = t[3] == 1 ? c.etherwarpColor : c.etherwarpInvalidColor;
			VoxelShape shape = mc.level.getBlockState(pos).getShape(mc.level, pos);
			if (shape.isEmpty()) shape = Shapes.block();
			Vec3 cam = context.levelState().cameraRenderState.pos;
			PoseStack pose = context.poseStack();
			pose.pushPose();
			pose.translate(pos.getX() - cam.x, pos.getY() - cam.y, pos.getZ() - cam.z);
			int style = c.etherwarpStyle; // 0 outline, 1 filled, 2 both
			if (style >= 1) {
				int fill = Math.round(Math.clamp(c.etherwarpFillOpacity, 0f, 1f) * 255) << 24 | rgb;
				AABB box = shape.bounds().inflate(0.003);
				context.submitNodeCollector().submitCustomGeometry(pose, RenderTypes.debugQuads(), (p, buffer) -> box(p, buffer, box, fill));
			}
			if (style != 1) {
				context.submitNodeCollector().submitShapeOutline(pose, shape, RenderTypes.lines(), 0xFF000000 | rgb, Math.max(0.5f, c.etherwarpLineWidth), false);
			}
			pose.popPose();
		});
	}

	private static void quad(PoseStack.Pose p, VertexConsumer b, int color, double... v) {
		for (int i = 0; i < 12; i += 3) b.addVertex(p, (float) v[i], (float) v[i + 1], (float) v[i + 2]).setColor(color);
	}

	private static void box(PoseStack.Pose p, VertexConsumer b, AABB a, int c) {
		double x0 = a.minX, y0 = a.minY, z0 = a.minZ, x1 = a.maxX, y1 = a.maxY, z1 = a.maxZ;
		quad(p, b, c, x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1); // bottom
		quad(p, b, c, x0, y1, z0, x0, y1, z1, x1, y1, z1, x1, y1, z0); // top
		quad(p, b, c, x0, y0, z0, x0, y1, z0, x1, y1, z0, x1, y0, z0); // north
		quad(p, b, c, x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1); // south
		quad(p, b, c, x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0); // west
		quad(p, b, c, x1, y0, z0, x1, y1, z0, x1, y1, z1, x1, y0, z1); // east
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
