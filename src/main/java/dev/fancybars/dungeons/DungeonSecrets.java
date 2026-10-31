package dev.fancybars.dungeons;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.joml.Matrix3x2fStack;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import dev.fancybars.FancyBarsMod;
import dev.fancybars.config.FancyBarsConfig;
import dev.fancybars.util.SkyblockLocation;
import dev.fancybars.util.gui.Theme;

/**
 * Dungeon secrets display, ported from SkyblockAddons: a chest icon, the word "Secrets", and the room's
 * found/total secrets underneath, coloured red → yellow → green. The chest and the word can each be turned off.
 * The numbers come from the "X/Y Secrets" part of the action bar.
 */
public final class DungeonSecrets {
	public static final Pattern SECRETS = Pattern.compile("(\\d+)/(\\d+) Secrets");
	private static final int WORD_COLOR = 0xFFAAAAAA;
	/** Created on first use: item stacks can't be made while the mod is loading (item components aren't bound yet). */
	private static ItemStack chest;

	private static ItemStack chest() {
		if (chest == null) chest = new ItemStack(Items.CHEST);
		return chest;
	}

	private static int secrets = -1;
	private static int maxSecrets = 0;
	/** When a "X/Y Secrets" part was last seen; a backup in case the location packet says something unexpected. */
	private static long lastSeen = 0;

	private DungeonSecrets() {}

	public static void init() {
		HudElementRegistry.attachElementAfter(VanillaHudElements.HOTBAR, FancyBarsMod.id("dungeon_secrets"), (graphics, tickCounter) -> {
			if (shouldShow()) render(graphics, secrets, maxSecrets);
		});
	}

	public static boolean shouldShow() {
		return FancyBarsConfig.get().secretsEnabled && inDungeon();
	}

	private static boolean inDungeon() {
		return SkyblockLocation.isInDungeons() || SkyblockLocation.isOnSkyblock() && net.minecraft.util.Util.getMillis() - lastSeen < 15_000;
	}

	/** Called by the action bar parser with the (formatting-stripped) part that matched {@link #SECRETS}. */
	public static void update(Matcher matcher) {
		secrets = Integer.parseInt(matcher.group(1));
		maxSecrets = Integer.parseInt(matcher.group(2));
		lastSeen = net.minecraft.util.Util.getMillis();
	}

	/**
	 * Backup path: reads the action bar the HUD is currently holding. If another mod handled the message through a
	 * route our chat listener never saw, the counter still updates as long as the text reached the screen.
	 */
	public static void pollHud() {
		if (!FancyBarsConfig.get().secretsEnabled || !SkyblockLocation.isOnSkyblock()) return;
		net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
		if (mc.gui == null || mc.gui.hud == null) return;
		net.minecraft.network.chat.Component message = ((dev.fancybars.mixin.HudAccessor) mc.gui.hud).getOverlayMessageString();
		if (message == null) return;
		Matcher matcher = SECRETS.matcher(stripped(message.getString()));
		if (matcher.find()) update(matcher);
	}

	/** Called when an action bar message had no secrets part (room without secrets). */
	public static void clear() {
		secrets = -1;
		maxSecrets = 0;
	}

	public static boolean hideFromActionBar() {
		return FancyBarsConfig.get().secretsEnabled && FancyBarsConfig.get().secretsHideActionBar && inDungeon();
	}

	// ---------------------------------------------------------------- Layout / drawing

	private static MutableComponent styled(MutableComponent text) {
		if (!FancyBarsConfig.get().smoothBarText) return text;
		FontDescription font = Theme.barFont();
		return text.withStyle(style -> style.withFont(font));
	}

	private static Component wordText() {
		return styled(Component.translatable("fancybars.secrets.word").withColor(WORD_COLOR & 0xFFFFFF));
	}

	private static Component countText(int found, int max) {
		if (found < 0 || max == 0) return styled(Component.translatable("fancybars.secrets.none").withColor(WORD_COLOR & 0xFFFFFF));
		if (found > max) max = found;
		float percent = Math.clamp(found / (float) max, 0, 1);
		float r;
		float g;
		if (percent <= 0.5f) {
			r = 1;
			g = percent * 2 * 0.66f + 0.33f;
		} else {
			r = (1 - percent) * 0.66f + 0.33f;
			g = 1;
		}
		int color = ((int) (Math.min(1, r) * 255) << 16) | ((int) (g * 255) << 8) | (int) (0.33f * 255);
		return styled(Component.literal(String.valueOf(found)).withColor(color)
				.append(Component.literal("/").withColor(WORD_COLOR & 0xFFFFFF))
				.append(Component.literal(String.valueOf(max)).withColor(color)));
	}

	/** Unscaled size {width, height} of the display with the current settings. */
	public static int[] size(int found, int max) {
		FancyBarsConfig config = FancyBarsConfig.get();
		Font font = Minecraft.getInstance().font;
		int textX = config.secretsShowChest ? 18 : 0;
		int textW = font.width(countText(found, max));
		if (config.secretsShowWord) textW = Math.max(textW, font.width(wordText()));
		int height = config.secretsShowWord ? 20 : 9;
		if (config.secretsShowChest) height = Math.max(height, 16);
		return new int[]{textX + textW, height};
	}

	/** Top-left corner on screen, in GUI pixels. */
	public static float[] position(int screenW, int screenH, int found, int max) {
		FancyBarsConfig config = FancyBarsConfig.get();
		int[] size = size(found, max);
		float s = config.secretsScale;
		if (config.secretsX < 0 || config.secretsY < 0) {
			return new float[]{screenW - size[0] * s - 12, Math.round(screenH * 0.42f)};
		}
		return new float[]{config.secretsX * screenW, config.secretsY * screenH};
	}

	public static void render(GuiGraphicsExtractor graphics, int found, int max) {
		float[] pos = position(graphics.guiWidth(), graphics.guiHeight(), found, max);
		renderAt(graphics, pos[0], pos[1], FancyBarsConfig.get().secretsScale, found, max);
	}

	public static void renderAt(GuiGraphicsExtractor graphics, float x, float y, float scale, int found, int max) {
		FancyBarsConfig config = FancyBarsConfig.get();
		Font font = Minecraft.getInstance().font;
		Matrix3x2fStack pose = graphics.pose();
		pose.pushMatrix();
		pose.translate(x, y);
		pose.scale(scale, scale);

		int textX = config.secretsShowChest ? 18 : 0;
		int[] size = size(found, max);
		if (config.textHudBackgrounds) Theme.hudPanel(graphics, -4, -4, size[0] + 8, size[1] + 7, 4, 0.55f, 255);
		Component count = countText(found, max);
		if (config.secretsShowWord) {
			Component word = wordText();
			int wordW = font.width(word);
			int blockW = Math.max(wordW, font.width(count));
			graphics.text(font, word, textX + (blockW - wordW) / 2, 0, Theme.hudText(255), true);
			graphics.text(font, count, textX + (blockW - font.width(count)) / 2, 11, Theme.hudText(255), true);
		} else {
			int countY = config.secretsShowChest ? 4 : 0;
			graphics.text(font, count, textX, countY, Theme.hudText(255), true);
		}
		if (config.secretsShowChest) graphics.item(chest(), 0, Math.max(0, (size[1] - 16) / 2));
		pose.popMatrix();
	}

	/** Renders the display with sample numbers (for the editor and menu preview). */
	public static void renderSample(GuiGraphicsExtractor graphics) {
		render(graphics, 5, 10);
	}

	public static String stripped(String text) {
		String s = ChatFormatting.stripFormatting(text);
		return s == null ? "" : s;
	}
}
