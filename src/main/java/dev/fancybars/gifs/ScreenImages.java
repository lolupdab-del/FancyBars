package dev.fancybars.gifs;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.imageio.ImageIO;

import com.mojang.blaze3d.platform.NativeImage;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;

import dev.fancybars.FancyBarsMod;
import dev.fancybars.config.FancyBarsConfig;

/**
 * Pictures that stay on your screen while you play. Any PNG / JPG / GIF (animated) in
 * {@code config/fancybars/screen-images}; each can be switched on/off, moved, scaled and faded.
 */
public final class ScreenImages {
	private static final int MAX_SIDE = 512;
	/** Default on-screen size of the longer side, before scaling. */
	public static final int BASE = 96;
	private static final Map<String, Image> IMAGES = new LinkedHashMap<>();
	private static final ExecutorService DECODER = Executors.newSingleThreadExecutor(runnable -> {
		Thread thread = new Thread(runnable, "SBCLIENT screen image decoder");
		thread.setDaemon(true);
		return thread;
	});
	private static int counter = 0;

	public static final class Image {
		public final String name;
		public volatile boolean ready;
		public volatile String error;
		Identifier texture;
		int frameW;
		int frameH;
		int cols;
		int atlasW;
		int atlasH;
		int[] delays = {1000};
		int totalMs = 1000;
		public int srcW = 1;
		public int srcH = 1;

		Image(String name) {
			this.name = name;
		}

		public FancyBarsConfig.ScreenImage settings() {
			return FancyBarsConfig.get().screenImages.computeIfAbsent(name, n -> new FancyBarsConfig.ScreenImage());
		}

		/** Unscaled size on screen. */
		public int[] baseSize() {
			float s = Math.min((float) BASE / srcW, (float) BASE / srcH);
			return new int[]{Math.max(4, Math.round(srcW * s)), Math.max(4, Math.round(srcH * s))};
		}
	}

	private ScreenImages() {}

	public static Path folder() {
		return FancyBarsMod.CONFIG_DIR.resolve("screen-images");
	}

	public static void init() {
		reload();
		HudElementRegistry.attachElementAfter(VanillaHudElements.HOTBAR, FancyBarsMod.id("screen_images"), (graphics, tickCounter) -> {
			if (Minecraft.getInstance().gui.screen() != null) return;
			for (Image image : IMAGES.values()) {
				FancyBarsConfig.ScreenImage settings = image.settings();
				if (settings.enabled) drawOnScreen(graphics, image, 1);
			}
		});
	}

	public static List<Image> all() {
		return new ArrayList<>(IMAGES.values());
	}

	public static synchronized void reload() {
		for (Image image : IMAGES.values()) release(image);
		IMAGES.clear();
		try {
			Files.createDirectories(folder());
			List<Path> files = new ArrayList<>();
			try (DirectoryStream<Path> stream = Files.newDirectoryStream(folder())) {
				for (Path path : stream) {
					String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
					if (name.endsWith(".png") || name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".gif") || name.endsWith(".bmp")) files.add(path);
				}
			}
			files.sort((a, b) -> a.getFileName().toString().compareToIgnoreCase(b.getFileName().toString()));
			for (Path path : files) {
				Image image = new Image(path.getFileName().toString());
				IMAGES.put(image.name, image);
				decode(image, path);
			}
		} catch (Exception e) {
			FancyBarsMod.LOGGER.error("[SBCLIENT] Couldn't read the screen image folder", e);
		}
	}

	public static void openFolder() {
		try {
			Files.createDirectories(folder());
		} catch (Exception ignored) {
		}
		Util.getPlatform().openPath(folder());
	}

	private static void decode(Image image, Path path) {
		DECODER.submit(() -> {
			try {
				byte[] bytes = Files.readAllBytes(path);
				GifDecoder.Result result;
				if (image.name.toLowerCase(Locale.ROOT).endsWith(".gif")) {
					result = GifDecoder.decode(bytes, 400, MAX_SIDE);
				} else {
					result = still(bytes);
				}
				GifDecoder.Result done = result;
				Minecraft.getInstance().execute(() -> upload(image, done));
			} catch (Throwable t) {
				image.error = String.valueOf(t.getMessage());
				FancyBarsMod.LOGGER.error("[SBCLIENT] Couldn't load screen image {}", image.name, t);
			}
		});
	}

	/** A PNG / JPG / BMP as a one-frame "animation", scaled down if it's huge. */
	private static GifDecoder.Result still(byte[] bytes) throws Exception {
		BufferedImage read = ImageIO.read(new ByteArrayInputStream(bytes));
		if (read == null) throw new IllegalArgumentException("Unsupported image format");
		int w = read.getWidth();
		int h = read.getHeight();
		float s = Math.min(1f, Math.min((float) MAX_SIDE / w, (float) MAX_SIDE / h));
		int fw = Math.max(1, Math.round(w * s));
		int fh = Math.max(1, Math.round(h * s));
		BufferedImage scaled = read;
		if (fw != w || fh != h) {
			scaled = new BufferedImage(fw, fh, BufferedImage.TYPE_INT_ARGB);
			java.awt.Graphics2D g = scaled.createGraphics();
			g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR);
			g.drawImage(read, 0, 0, fw, fh, null);
			g.dispose();
		}
		GifDecoder.Result result = new GifDecoder.Result();
		result.width = w;
		result.height = h;
		result.frameW = fw;
		result.frameH = fh;
		result.frames.add(scaled.getRGB(0, 0, fw, fh, null, 0, fw));
		result.delaysMs.add(1000);
		return result;
	}

	private static void upload(Image image, GifDecoder.Result result) {
		if (IMAGES.get(image.name) != image) return;
		int count = result.frames.size();
		int maxCols = Math.max(1, 4096 / result.frameW);
		int maxRows = Math.max(1, 4096 / result.frameH);
		count = Math.min(count, maxCols * maxRows);
		int cols = Math.min(maxCols, count);
		int rows = (count + cols - 1) / cols;
		NativeImage atlas = new NativeImage(cols * result.frameW, rows * result.frameH, true);
		for (int f = 0; f < count; f++) {
			int[] pixels = result.frames.get(f);
			int ox = f % cols * result.frameW;
			int oy = f / cols * result.frameH;
			for (int py = 0; py < result.frameH; py++) {
				for (int px = 0; px < result.frameW; px++) atlas.setPixel(ox + px, oy + py, pixels[py * result.frameW + px]);
			}
		}
		Identifier id = FancyBarsMod.id("screen_image/" + counter++);
		DynamicTexture texture = new DynamicTexture(() -> "SBCLIENT screen image " + image.name, atlas);
		Minecraft.getInstance().getTextureManager().register(id, texture);
		texture.upload();
		image.texture = id;
		image.frameW = result.frameW;
		image.frameH = result.frameH;
		image.cols = cols;
		image.atlasW = atlas.getWidth();
		image.atlasH = atlas.getHeight();
		image.delays = new int[count];
		int total = 0;
		for (int i = 0; i < count; i++) {
			image.delays[i] = Math.max(10, result.delaysMs.get(i));
			total += image.delays[i];
		}
		image.totalMs = Math.max(1, total);
		image.srcW = result.width;
		image.srcH = result.height;
		image.ready = true;
	}

	private static void release(Image image) {
		if (image.texture != null) {
			Identifier id = image.texture;
			image.texture = null;
			image.ready = false;
			Minecraft.getInstance().execute(() -> Minecraft.getInstance().getTextureManager().release(id));
		}
	}

	/** Top-left corner on screen. */
	public static float[] position(Image image, int screenW, int screenH) {
		FancyBarsConfig.ScreenImage s = image.settings();
		if (s.x < 0 || s.y < 0) return new float[]{8, Math.round(screenH * 0.12f)};
		return new float[]{s.x * screenW, s.y * screenH};
	}

	public static void drawOnScreen(GuiGraphicsExtractor graphics, Image image, float alpha) {
		float[] pos = position(image, graphics.guiWidth(), graphics.guiHeight());
		int[] base = image.baseSize();
		FancyBarsConfig.ScreenImage s = image.settings();
		draw(graphics, image, pos[0], pos[1], base[0] * s.scale, base[1] * s.scale, s.opacity * alpha);
	}

	/** Draws the current frame into a rectangle. */
	public static void draw(GuiGraphicsExtractor graphics, Image image, float x, float y, float w, float h, float opacity) {
		if (!image.ready || image.texture == null) return;
		int t = (int) (System.currentTimeMillis() % image.totalMs);
		int frame = 0;
		for (int i = 0; i < image.delays.length; i++) {
			t -= image.delays[i];
			if (t < 0) {
				frame = i;
				break;
			}
		}
		int u = frame % image.cols * image.frameW;
		int v = frame / image.cols * image.frameH;
		int color = (Math.round(Math.clamp(opacity, 0, 1) * 255) << 24) | 0xFFFFFF;
		org.joml.Matrix3x2fStack pose = graphics.pose();
		pose.pushMatrix();
		pose.translate(x, y);
		pose.scale(w / image.frameW, h / image.frameH);
		graphics.blit(RenderPipelines.GUI_TEXTURED, image.texture, 0, 0, u, v, image.frameW, image.frameH, image.frameW, image.frameH, image.atlasW, image.atlasH, color);
		pose.popMatrix();
	}
}
