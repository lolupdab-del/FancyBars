package dev.fancybars.util.gui;

import java.util.ArrayList;
import java.util.List;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;

import dev.fancybars.FancyBarsMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

/**
 * One big shared texture for everything the mod's UI draws from textures: rounded corners, smooth text, shapes and
 * plain fills (from a white patch).
 * <p>
 * Each of those used to be its own little texture. The GPU draws every change of texture as a separate batch, so a
 * HUD box with text cost several batches, and the whole HUD hundreds, which showed up as frame time spent waiting on
 * the graphics card. With all of it in one texture, consecutive pieces are drawn together in one batch.
 * <p>
 * Space is handed out in rows (shelves). When a page is full another is started (each page is its own texture);
 * when the last page is full everything is thrown away and redrawn as it's needed.
 */
public final class UiAtlas {
	private UiAtlas() {}

	public static final int SIZE = 2048;
	private static final int MAX_PAGES = 4;
	/** Empty pixels around every piece, so scaled drawing never picks up a neighbour's edge. */
	private static final int PAD = 2;

	/** Where a piece is: its texture (a page, or its own for pieces too big for a page) and its pixel rectangle. */
	public record Region(Identifier texture, int u, int v, int w, int h, int texW, int texH, int gen, boolean own) {
		/** False once the atlas was cleared: whatever was here must be drawn again. */
		public boolean valid() {
			return own || gen == generation;
		}
	}

	private static final class Page {
		final Identifier id;
		final DynamicTexture texture;
		int shelfX;
		int shelfY;
		int shelfH;

		Page(int index) {
			id = FancyBarsMod.id("ui/atlas_" + index);
			texture = new DynamicTexture(() -> "SkyblockGuiUtils UI atlas", SIZE, SIZE, true);
			Minecraft.getInstance().getTextureManager().register(id, texture);
			texture.upload();
		}

		int[] alloc(int w, int h) {
			int pw = w + PAD * 2;
			int ph = h + PAD * 2;
			if (shelfX + pw > SIZE) {
				shelfY += shelfH;
				shelfX = 0;
				shelfH = 0;
			}
			if (shelfY + ph > SIZE) return null;
			int[] at = {shelfX + PAD, shelfY + PAD};
			shelfX += pw;
			shelfH = Math.max(shelfH, ph);
			return at;
		}

		void clear() {
			shelfX = 0;
			shelfY = 0;
			shelfH = 0;
			texture.getPixels().fillRect(0, 0, SIZE, SIZE, 0);
			texture.upload();
		}
	}

	private static final List<Page> PAGES = new ArrayList<>();
	private static final List<Runnable> RESET = new ArrayList<>();
	private static volatile int generation;
	private static Region white;
	private static int ownCounter;
	/** Page new pieces go into. */
	private static int current;

	/** Runs when everything was thrown away (caches holding regions should forget them). */
	public static void onReset(Runnable listener) {
		RESET.add(listener);
	}

	/** Copies {@code image} into the atlas and closes it. */
	public static Region add(NativeImage image) {
		int w = image.getWidth();
		int h = image.getHeight();
		if (w + PAD * 2 > SIZE || h + PAD * 2 > SIZE) return own(image);
		if (PAGES.isEmpty()) PAGES.add(new Page(0));
		Page page = PAGES.get(current);
		int[] at = page.alloc(w, h);
		if (at == null) {
			if (current + 1 < PAGES.size()) {
				current++;
			} else if (PAGES.size() < MAX_PAGES) {
				PAGES.add(new Page(PAGES.size()));
				current++;
			} else {
				reset();
			}
			page = PAGES.get(current);
			at = page.alloc(w, h);
		}
		RenderSystem.getDevice().createCommandEncoder().writeToTexture(page.texture.getTexture(), image, 0, 0, at[0], at[1]);
		image.close();
		return new Region(page.id, at[0], at[1], w, h, SIZE, SIZE, generation, false);
	}

	/** A piece too big for a page gets a texture of its own (the old way). */
	private static Region own(NativeImage image) {
		Identifier id = FancyBarsMod.id("ui/own_" + ownCounter++);
		DynamicTexture texture = new DynamicTexture(() -> "SkyblockGuiUtils UI", image);
		Minecraft.getInstance().getTextureManager().register(id, texture);
		texture.upload();
		return new Region(id, 0, 0, image.getWidth(), image.getHeight(), image.getWidth(), image.getHeight(), -1, true);
	}

	/** Frees a region's texture if it has its own (atlas space is reused when the atlas is cleared). */
	public static void release(Region region) {
		if (region != null && region.own()) Minecraft.getInstance().getTextureManager().release(region.texture());
	}

	private static void reset() {
		generation++;
		// the pages are kept (never freed mid-frame) and filled again from the first
		for (Page page : PAGES) page.clear();
		current = 0;
		white = null;
		for (Runnable listener : RESET) listener.run();
	}

	private static Region white() {
		if (white == null || !white.valid()) {
			NativeImage image = new NativeImage(4, 4, false);
			image.fillRect(0, 0, 4, 4, 0xFFFFFFFF);
			white = add(image);
		}
		return white;
	}

	/** Draws part of a region: {@code w × h} pixels starting {@code srcX, srcY} into it, at {@code x, y}. */
	public static void blit(GuiGraphicsExtractor graphics, RenderPipeline pipeline, Region r, int x, int y, int srcX, int srcY, int w, int h, int color) {
		graphics.blit(pipeline, r.texture(), x, y, r.u() + srcX, r.v() + srcY, w, h, r.texW(), r.texH(), color);
	}

	/**
	 * A plain filled rectangle drawn from the atlas's white patch, so it joins the same batch as the corners and text
	 * around it instead of starting a new one.
	 */
	public static void fill(GuiGraphicsExtractor graphics, RenderPipeline pipeline, int x1, int y1, int x2, int y2, int color) {
		if (x2 <= x1 || y2 <= y1) return;
		Region r = white();
		graphics.blit(pipeline, r.texture(), x1, y1, r.u() + 1, r.v() + 1, x2 - x1, y2 - y1, 2, 2, r.texW(), r.texH(), color);
	}
}
