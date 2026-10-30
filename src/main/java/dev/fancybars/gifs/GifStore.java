package dev.fancybars.gifs;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.mojang.blaze3d.platform.NativeImage;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

import dev.fancybars.FancyBarsMod;

/**
 * Loads the .gif files from config/invgifs into animated textures and keeps their layout.
 * Ported from Inventory GIFs by lolupdab (MIT); uses the same folder and layout file so existing setups carry over.
 */
public final class GifStore {
	private static final int MAX_FRAME_SIDE = 256;
	private static final int MAX_ATLAS = 4096;
	private static final int MAX_FRAMES = 400;
	private static final Map<String, Gif> GIFS = new LinkedHashMap<>();
	private static final ExecutorService DECODER = Executors.newSingleThreadExecutor(runnable -> {
		Thread thread = new Thread(runnable, "FancyBars GIF decoder");
		thread.setDaemon(true);
		return thread;
	});
	private static boolean loadedOnce = false;
	private static int texCounter = 0;

	private GifStore() {}

	public static Path folder() {
		return Minecraft.getInstance().gameDirectory.toPath().resolve("config").resolve("invgifs");
	}

	private static Path layoutFile() {
		return folder().resolve("layout.properties");
	}

	public static List<Gif> all() {
		return new ArrayList<>(GIFS.values());
	}

	public static void ensureLoaded() {
		if (!loadedOnce) {
			loadedOnce = true;
			reload();
		}
	}

	public static synchronized void reload() {
		Properties layout = readLayout();
		Set<String> present = new HashSet<>();
		try {
			Files.createDirectories(folder());
			List<Path> files = new ArrayList<>();
			try (DirectoryStream<Path> stream = Files.newDirectoryStream(folder())) {
				for (Path path : stream) {
					if (path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".gif")) files.add(path);
				}
			}
			files.sort((a, b) -> a.getFileName().toString().compareToIgnoreCase(b.getFileName().toString()));
			int newIndex = 0;
			for (Path path : files) {
				String name = path.getFileName().toString();
				present.add(name);
				Gif old = GIFS.remove(name);
				if (old != null) release(old);
				Gif gif = new Gif(name);
				applyLayout(gif, layout, newIndex);
				if (!layout.containsKey(name + ".x")) newIndex++;
				GIFS.put(name, gif);
				startDecode(gif, path);
			}
		} catch (Exception e) {
			FancyBarsMod.LOGGER.error("[FancyBars] Could not read GIF folder", e);
		}
		for (String name : new ArrayList<>(GIFS.keySet())) {
			if (!present.contains(name)) release(GIFS.remove(name));
		}
	}

	private static void applyLayout(Gif gif, Properties layout, int index) {
		gif.x = readInt(layout, gif.name + ".x", 98 + index * 6);
		gif.y = readInt(layout, gif.name + ".y", 17 + index * 6);
		gif.w = readInt(layout, gif.name + ".w", -1);
		gif.h = readInt(layout, gif.name + ".h", -1);
		gif.hidden = Boolean.parseBoolean(layout.getProperty(gif.name + ".hidden", "false"));
		try {
			gif.opacity = Math.clamp(Float.parseFloat(layout.getProperty(gif.name + ".opacity", "1")), 0.1f, 1f);
		} catch (NumberFormatException e) {
			gif.opacity = 1;
		}
	}

	private static void startDecode(Gif gif, Path path) {
		DECODER.submit(() -> {
			try {
				byte[] bytes = Files.readAllBytes(path);
				GifDecoder.Result result = GifDecoder.decode(bytes, MAX_FRAMES, MAX_FRAME_SIDE);
				Minecraft.getInstance().execute(() -> upload(gif, result));
			} catch (Throwable t) {
				gif.error = t.getMessage();
				FancyBarsMod.LOGGER.error("[FancyBars] Could not load GIF {}", gif.name, t);
			}
		});
	}

	private static void upload(Gif gif, GifDecoder.Result result) {
		if (GIFS.get(gif.name) != gif) return;
		int frameW = result.frameW;
		int frameH = result.frameH;
		int maxCols = Math.max(1, MAX_ATLAS / frameW);
		int maxRows = Math.max(1, MAX_ATLAS / frameH);
		int capacity = maxCols * maxRows;
		List<int[]> frames = result.frames;
		List<Integer> delays = result.delaysMs;

		// Too many frames for one atlas: keep every n-th frame and merge delays
		if (frames.size() > capacity) {
			int step = (int) Math.ceil((double) frames.size() / capacity);
			List<int[]> keptFrames = new ArrayList<>();
			List<Integer> keptDelays = new ArrayList<>();
			for (int i = 0; i < frames.size(); i += step) {
				keptFrames.add(frames.get(i));
				int total = 0;
				for (int j = i; j < Math.min(frames.size(), i + step); j++) total += delays.get(j);
				keptDelays.add(total);
			}
			frames = keptFrames;
			delays = keptDelays;
		}

		int count = frames.size();
		int cols = Math.min(maxCols, count);
		int rows = (count + cols - 1) / cols;
		int atlasW = cols * frameW;
		int atlasH = rows * frameH;
		NativeImage image = new NativeImage(atlasW, atlasH, true);
		for (int f = 0; f < count; f++) {
			int[] pixels = frames.get(f);
			int ox = f % cols * frameW;
			int oy = f / cols * frameH;
			for (int py = 0; py < frameH; py++) {
				for (int px = 0; px < frameW; px++) {
					image.setPixel(ox + px, oy + py, pixels[py * frameW + px]);
				}
			}
		}

		Identifier id = FancyBarsMod.id("gif/" + texCounter++);
		DynamicTexture texture = new DynamicTexture(() -> "fancybars gif " + gif.name, image);
		Minecraft.getInstance().getTextureManager().register(id, texture);
		texture.upload();
		gif.texture = id;
		gif.frameW = frameW;
		gif.frameH = frameH;
		gif.cols = cols;
		gif.atlasW = atlasW;
		gif.atlasH = atlasH;
		gif.delays = delays.stream().mapToInt(Integer::intValue).toArray();
		int total = 0;
		for (int d : gif.delays) total += d;
		gif.totalMs = Math.max(1, total);
		gif.srcW = result.width;
		gif.srcH = result.height;
		if (gif.w <= 0 || gif.h <= 0) {
			float s = Math.min(72f / result.width, 54f / result.height);
			gif.w = Math.max(8, Math.round(result.width * s));
			gif.h = Math.max(8, Math.round(result.height * s));
		}
		gif.ready = true;
	}

	private static void release(Gif gif) {
		if (gif != null && gif.texture != null) {
			Identifier id = gif.texture;
			gif.texture = null;
			gif.ready = false;
			Minecraft.getInstance().execute(() -> Minecraft.getInstance().getTextureManager().release(id));
		}
	}

	/** Draws all visible GIFs relative to the inventory's top-left corner. */
	public static void drawAll(GuiGraphicsExtractor graphics, int left, int top) {
		for (Gif gif : GIFS.values()) {
			if (gif.ready && !gif.hidden && gif.texture != null) {
				draw(graphics, gif, left + gif.x, top + gif.y, gif.w, gif.h, gif.opacity);
			}
		}
	}

	/** Draws the current animation frame of a GIF into any rectangle. */
	public static void draw(GuiGraphicsExtractor graphics, Gif gif, int x, int y, int w, int h, float opacity) {
		if (!gif.ready || gif.texture == null) return;
		int frame = frameAt(gif, System.currentTimeMillis());
		int u = frame % gif.cols * gif.frameW;
		int v = frame / gif.cols * gif.frameH;
		int color = (Math.round(Math.clamp(opacity, 0, 1) * 255) << 24) | 0xFFFFFF;
		graphics.blit(RenderPipelines.GUI_TEXTURED, gif.texture, x, y, u, v, w, h, gif.frameW, gif.frameH, gif.atlasW, gif.atlasH, color);
	}

	private static int frameAt(Gif gif, long now) {
		int t = (int) (now % gif.totalMs);
		for (int i = 0; i < gif.delays.length; i++) {
			t -= gif.delays[i];
			if (t < 0) return i;
		}
		return 0;
	}

	public static void save() {
		Properties out = new Properties();
		for (Gif gif : GIFS.values()) {
			out.setProperty(gif.name + ".x", String.valueOf(gif.x));
			out.setProperty(gif.name + ".y", String.valueOf(gif.y));
			if (gif.w > 0) {
				out.setProperty(gif.name + ".w", String.valueOf(gif.w));
				out.setProperty(gif.name + ".h", String.valueOf(gif.h));
			}
			out.setProperty(gif.name + ".hidden", String.valueOf(gif.hidden));
			out.setProperty(gif.name + ".opacity", String.valueOf(gif.opacity));
		}
		// keep settings for GIFs that aren't currently in the folder
		Properties existing = readLayout();
		for (String key : existing.stringPropertyNames()) out.putIfAbsent(key, existing.getProperty(key));
		try {
			Files.createDirectories(folder());
			try (BufferedWriter writer = Files.newBufferedWriter(layoutFile())) {
				out.store(writer, "Inventory GIFs layout (positions are relative to the inventory's top-left corner)");
			}
		} catch (Exception e) {
			FancyBarsMod.LOGGER.error("[FancyBars] Could not save GIF layout", e);
		}
	}

	private static Properties readLayout() {
		Properties props = new Properties();
		try {
			if (Files.exists(layoutFile())) {
				try (BufferedReader reader = Files.newBufferedReader(layoutFile())) {
					props.load(reader);
				}
			}
		} catch (Exception e) {
			FancyBarsMod.LOGGER.error("[FancyBars] Could not read GIF layout", e);
		}
		return props;
	}

	private static int readInt(Properties props, String key, int fallback) {
		try {
			return Integer.parseInt(props.getProperty(key, String.valueOf(fallback)).trim());
		} catch (NumberFormatException e) {
			return fallback;
		}
	}

	public static final class Gif {
		public final String name;
		Identifier texture;
		int frameW;
		int frameH;
		int cols;
		int atlasW;
		int atlasH;
		int[] delays = new int[0];
		int totalMs = 1;
		public int srcW;
		public int srcH;
		public volatile boolean ready = false;
		public volatile String error = null;
		public int x;
		public int y;
		public int w;
		public int h;
		public float opacity = 1;
		public boolean hidden = false;

		Gif(String name) {
			this.name = name;
		}

		public int frameCount() {
			return delays.length;
		}
	}
}
