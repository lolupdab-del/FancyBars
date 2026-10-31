package dev.fancybars.capes;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;

import org.jspecify.annotations.Nullable;

import com.mojang.blaze3d.platform.NativeImage;

import dev.fancybars.config.FancyBarsConfig;

/**
 * Builds a cape texture from any picture: the picture is placed on the back of the cape (and optionally the
 * inside) with your offset, size, rotation and background colour. The texture is 16x the vanilla 64x32 size
 * so the picture stays sharp.
 */
public final class CapeComposer {
	public static final int K = 16;
	/** Back of the cape (what people behind you see), in vanilla 64x32 texture pixels. */
	public static final int PANEL_X = 1;
	public static final int PANEL_Y = 1;
	public static final int PANEL_W = 10;
	public static final int PANEL_H = 16;
	/** Inside of the cape. */
	private static final int INSIDE_X = 12;

	private CapeComposer() {}

	public static @Nullable BufferedImage readSource(@Nullable Path file) {
		if (file == null) return null;
		try (var in = Files.newInputStream(file)) {
			return javax.imageio.ImageIO.read(in);
		} catch (Exception e) {
			return null;
		}
	}

	/** Size multiplier that makes the picture exactly fit inside the panel (scale 1 = this). */
	public static double fitScale(BufferedImage src, int rotation) {
		boolean sideways = rotation % 180 != 0;
		double w = sideways ? src.getHeight() : src.getWidth();
		double h = sideways ? src.getWidth() : src.getHeight();
		return Math.min(PANEL_W * K / w, PANEL_H * K / h);
	}

	/** The scale (relative to "fit") at which the picture covers the whole panel. */
	public static float fillScale(BufferedImage src, int rotation) {
		boolean sideways = rotation % 180 != 0;
		double w = sideways ? src.getHeight() : src.getWidth();
		double h = sideways ? src.getWidth() : src.getHeight();
		double fit = Math.min(PANEL_W * K / w, PANEL_H * K / h);
		double fill = Math.max(PANEL_W * K / w, PANEL_H * K / h);
		return (float) (fill / fit);
	}

	public static BufferedImage compose(BufferedImage src) {
		FancyBarsConfig c = FancyBarsConfig.get();
		BufferedImage out = new BufferedImage(64 * K, 32 * K, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = out.createGraphics();
		g.setColor(new java.awt.Color(c.capeBackground & 0xFFFFFF));
		g.fillRect(0, 0, out.getWidth(), out.getHeight());
		draw(g, src, PANEL_X * K, c, false);
		if (c.capeImageInside) draw(g, src, INSIDE_X * K, c, true);
		g.dispose();
		return out;
	}

	private static void draw(Graphics2D g, BufferedImage src, int panelX, FancyBarsConfig c, boolean mirror) {
		int pw = PANEL_W * K;
		int ph = PANEL_H * K;
		int py = PANEL_Y * K;
		Graphics2D p = (Graphics2D) g.create();
		p.clipRect(panelX, py, pw, ph);
		p.setRenderingHint(RenderingHints.KEY_INTERPOLATION, c.capeImageSmooth ? RenderingHints.VALUE_INTERPOLATION_BICUBIC : RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
		p.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
		p.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		double scale = fitScale(src, c.capeImageRotation) * Math.max(0.01f, c.capeImageScale);
		double cx = panelX + pw / 2.0 + c.capeImageX * pw / 2.0 * (mirror ? -1 : 1);
		double cy = py + ph / 2.0 + c.capeImageY * ph / 2.0;
		AffineTransform t = new AffineTransform();
		t.translate(cx, cy);
		t.rotate(Math.toRadians(c.capeImageRotation));
		t.scale(scale * ((c.capeImageFlip ^ mirror) ? -1 : 1), scale);
		t.translate(-src.getWidth() / 2.0, -src.getHeight() / 2.0);
		p.drawImage(src, t, null);
		p.dispose();
	}

	public static NativeImage toNative(BufferedImage img, int x, int y, int w, int h) {
		NativeImage n = new NativeImage(w, h, false);
		for (int yy = 0; yy < h; yy++) for (int xx = 0; xx < w; xx++) n.setPixel(xx, yy, img.getRGB(x + xx, y + yy));
		return n;
	}

	public static NativeImage toNative(BufferedImage img) {
		return toNative(img, 0, 0, img.getWidth(), img.getHeight());
	}
}
