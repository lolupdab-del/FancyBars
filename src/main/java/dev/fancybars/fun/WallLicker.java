package dev.fancybars.fun;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Stream;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.FloatControl;
import javax.sound.sampled.LineEvent;

import org.joml.Matrix3x2fStack;
import org.jspecify.annotations.Nullable;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import dev.fancybars.FancyBarsMod;
import dev.fancybars.config.FancyBarsConfig;
import dev.fancybars.util.gui.Theme;

/**
 * Wall Licker: counts every time you walk face-first into a wall inside a dungeon. The on-screen "Licks: N" is for
 * the current run (starts over every run); the all-time total is kept separately. Can play a sound
 * of your choice (any .wav file in {@code config/fancybars/wall-licker}).
 */
public final class WallLicker {
	public static final Path SOUND_DIR = FancyBarsMod.CONFIG_DIR.resolve("wall-licker");
	private static final long COOLDOWN_MS = 350;

	private static boolean wasLicking;
	/** Ticks spent pressed against a real wall without moving (a lick needs a few). */
	private static int stuckTicks;
	/** A boss has started talking this run: licks stop counting until you leave. */
	private static boolean inBoss;
	private static long lastLick;

	private WallLicker() {}

	public static void init() {
		try {
			Files.createDirectories(SOUND_DIR);
		} catch (Exception e) {
			FancyBarsMod.LOGGER.warn("[SBCLIENT] Couldn't create {}", SOUND_DIR, e);
		}
		ClientTickEvents.END_CLIENT_TICK.register(WallLicker::tick);
		// the boss fight starts when a boss speaks ("[BOSS] Maxor: ..."); The Watcher also speaks during the clear, so he doesn't count
		net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
			if (overlay) return;
			String text = net.minecraft.ChatFormatting.stripFormatting(message.getString());
			if (text != null && text.startsWith("[BOSS] ") && !text.startsWith("[BOSS] The Watcher")) inBoss = true;
		});
		dev.fancybars.util.SkyblockLocation.onLocationChange(() -> inBoss = false);
		// every dungeon run is a new server: the run counter starts over when you join one and when you leave it
		dev.fancybars.util.SkyblockLocation.onLocationChange(() -> {
			FancyBarsConfig config = FancyBarsConfig.get();
			if (config.wallLicksRun != 0) {
				config.wallLicksRun = 0;
				FancyBarsConfig.save();
			}
		});
		HudElementRegistry.attachElementAfter(VanillaHudElements.HOTBAR, FancyBarsMod.id("wall_licker"), (graphics, tickCounter) -> {
			Minecraft mc = Minecraft.getInstance();
			if (FancyBarsConfig.get().wallLickerEnabled && mc.player != null && dev.fancybars.util.HudVisibility.allowed() && dev.fancybars.util.SkyblockLocation.isInDungeons()) render(graphics);
		});
	}

	private static void tick(Minecraft mc) {
		LocalPlayer player = mc.player;
		if (player == null || mc.level == null || !FancyBarsConfig.get().wallLickerEnabled || !dev.fancybars.util.SkyblockLocation.isInDungeons()) {
			wasLicking = false;
			return;
		}
		FancyBarsConfig cfg = FancyBarsConfig.get();
		boolean blocked = inBoss || cfg.wallLickerIgnoreDungeonbreaker && holdingDungeonbreaker(player);
		// pressed into a real wall (solid at chest AND head height, so stairs and slabs don't count) and not moving
		double moved = Math.hypot(player.getX() - player.xo, player.getZ() - player.zo);
		boolean pressing = !blocked && player.horizontalCollision && mc.options.keyUp.isDown() && player.onGround()
				&& Math.abs(player.getY() - player.yo) < 1.0E-3 && moved < 0.02 && facingWall(player, mc.level);
		stuckTicks = pressing ? stuckTicks + 1 : 0;
		boolean licking = stuckTicks >= 4;
		long now = Util.getMillis();
		if (licking && !wasLicking && now - lastLick > COOLDOWN_MS) {
			lastLick = now;
			FancyBarsConfig config = FancyBarsConfig.get();
			config.wallLicks++;
			config.wallLicksRun++;
			FancyBarsConfig.save();
			if (config.wallLickerSound) playSound();
		}
		wasLicking = licking;
	}

	private static boolean holdingDungeonbreaker(LocalPlayer player) {
		net.minecraft.world.item.ItemStack stack = player.getMainHandItem();
		if (stack.isEmpty()) return false;
		net.minecraft.world.item.component.CustomData data = stack.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
		if (data != null && "DUNGEONBREAKER".equals(((dev.fancybars.mixin.CustomDataAccessor) (Object) data).getTag().getStringOr("id", ""))) return true;
		return stack.getHoverName().getString().contains("Dungeonbreaker");
	}

	/** Is there a solid wall right in front of where you're looking? */
	private static boolean facingWall(LocalPlayer player, Level level) {
		Vec3 look = player.getLookAngle();
		Vec3 flat = new Vec3(look.x, 0, look.z);
		if (flat.lengthSqr() < 1.0E-4) return false;
		flat = flat.normalize();
		double reach = player.getBbWidth() / 2 + 0.25;
		// a wall: solid at chest height and at head height (a step, stair or slab is only solid low down)
		for (double dy : new double[]{1.0, player.getEyeHeight() - 0.1}) {
			BlockPos pos = BlockPos.containing(player.getX() + flat.x * reach, player.getY() + dy, player.getZ() + flat.z * reach);
			if (level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()) return false;
		}
		return true;
	}

	// ---------------------------------------------------------------- Sound

	public static List<Path> sounds() {
		List<Path> list = new ArrayList<>();
		if (!Files.isDirectory(SOUND_DIR)) return list;
		try (Stream<Path> files = Files.list(SOUND_DIR)) {
			files.filter(path -> {
				String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
				return name.endsWith(".wav") || name.endsWith(".aiff") || name.endsWith(".aif") || name.endsWith(".au");
			}).sorted().forEach(list::add);
		} catch (Exception ignored) {
		}
		return list;
	}

	/** Plays one of the sound files (a random one if there are several). */
	public static void playSound() {
		List<Path> sounds = sounds();
		if (sounds.isEmpty()) return;
		Path file = sounds.get(ThreadLocalRandom.current().nextInt(sounds.size()));
		float volume = FancyBarsConfig.get().wallLickerVolume / 100f;
		FancyBarsMod.VIRTUAL_THREAD_EXECUTOR.execute(() -> play(file.toFile(), volume));
	}

	private static void play(File file, float volume) {
		try {
			AudioInputStream in = AudioSystem.getAudioInputStream(file);
			AudioFormat base = in.getFormat();
			if (base.getEncoding() != AudioFormat.Encoding.PCM_SIGNED) {
				AudioFormat pcm = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED, base.getSampleRate(), 16, base.getChannels(), base.getChannels() * 2, base.getSampleRate(), false);
				in = AudioSystem.getAudioInputStream(pcm, in);
			}
			Clip clip = AudioSystem.getClip();
			clip.open(in);
			if (clip.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
				FloatControl gain = (FloatControl) clip.getControl(FloatControl.Type.MASTER_GAIN);
				float db = volume <= 0.001f ? gain.getMinimum() : (float) (20 * Math.log10(volume));
				gain.setValue(Math.clamp(db, gain.getMinimum(), gain.getMaximum()));
			}
			clip.addLineListener(event -> {
				if (event.getType() == LineEvent.Type.STOP) clip.close();
			});
			clip.start();
		} catch (Exception e) {
			FancyBarsMod.LOGGER.warn("[SBCLIENT] Couldn't play wall licker sound {}", file, e);
		}
	}

	public static @Nullable String soundName() {
		List<Path> sounds = sounds();
		if (sounds.isEmpty()) return null;
		return sounds.size() == 1 ? sounds.getFirst().getFileName().toString() : sounds.size() + " sounds (random)";
	}

	public static void openFolder() {
		try {
			Files.createDirectories(SOUND_DIR);
		} catch (Exception ignored) {
		}
		Util.getPlatform().openPath(SOUND_DIR);
	}

	// ---------------------------------------------------------------- HUD

	public static Component text() {
		return Theme.bold(Component.literal("Licks: ").withColor(Theme.night(Theme.ACCENT) & 0xFFFFFF)
				.append(Component.literal(String.valueOf(FancyBarsConfig.get().wallLicksRun)).withColor(Theme.hudText(255) & 0xFFFFFF)));
	}

	public static int[] size() {
		return new int[]{Minecraft.getInstance().font.width(text()) + 12, 14};
	}

	public static float[] position(int screenW, int screenH) {
		FancyBarsConfig config = FancyBarsConfig.get();
		if (config.wallLickerX < 0 || config.wallLickerY < 0) return new float[]{6, 6};
		return new float[]{config.wallLickerX * screenW, config.wallLickerY * screenH};
	}

	public static void render(GuiGraphicsExtractor graphics) {
		float[] pos = position(graphics.guiWidth(), graphics.guiHeight());
		renderAt(graphics, pos[0], pos[1], FancyBarsConfig.get().wallLickerScale, 1);
	}

	public static void renderAt(GuiGraphicsExtractor graphics, float x, float y, float scale, float alpha) {
		Font font = Minecraft.getInstance().font;
		int a = Math.round(alpha * 255);
		int[] size = size();
		Matrix3x2fStack pose = graphics.pose();
		pose.pushMatrix();
		pose.translate(x, y);
		pose.scale(scale, scale);
		Theme.hudPanel(graphics, 0, 0, size[0], size[1], 5, 0.88f, a);
		Theme.boldInBox(graphics, font, text(), 6, 0, size[1], Theme.hudText(a));
		pose.popMatrix();
	}
}
