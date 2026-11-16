package dev.fancybars.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.feature.NameTagFeatureRenderer;
import net.minecraft.network.chat.Component;

import dev.fancybars.players.NameTagDebug;
import dev.fancybars.players.Nicknames;

/**
 * The very last step of every name tag: its stored text is turned into glyphs here, after every mod (SkyHanni's
 * health display and the rest) has finished with it. That's where your own F5 tag finally gets its custom name.
 * The tag was centred on the old text's width, so it's moved by half the difference to stay centred. Runs every
 * frame, so the colours keep flowing. Optional (require = 0).
 */
@Mixin(NameTagFeatureRenderer.class)
public abstract class NameTagFeatureNicknameMixin {
	/** Width the swapped text gained, for re-centring (render thread only). */
	@Unique
	private static float fancybars$widthChange;

	@ModifyExpressionValue(method = "prepareText", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/feature/NameTagFeatureRenderer$Submit;text()Lnet/minecraft/network/chat/Component;"), require = 0)
	private static Component fancybars$swapName(Component text, @Local(argsOnly = true) Font font) {
		long fancybars$perf = dev.fancybars.util.Perf.begin();
		try {
			Component swapped = Nicknames.apply(text, Nicknames.Where.WORLD);
			NameTagDebug.hit("nameTagRenderer", text, swapped);
			// usually an earlier hook already swapped it, so the width is unchanged; recolouring never changes it. Only
			// measure (twice, every frame) when this hook did the swap itself.
			fancybars$widthChange = swapped == text ? 0 : font.width(swapped) - font.width(text);
			// whoever swapped it and however old this copy is: colours for this exact frame
			return Nicknames.recolor(swapped, Nicknames.Where.WORLD);
		} finally {
			if (fancybars$perf != 0) dev.fancybars.util.Perf.end("NameTagFeatureNicknameMixin swapName", fancybars$perf);
		}
	}

	@ModifyExpressionValue(method = "prepareText", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/feature/NameTagFeatureRenderer$Submit;x()F"), require = 0)
	private static float fancybars$recentre(float x) {
		long fancybars$perf = dev.fancybars.util.Perf.begin();
		try {
			float change = fancybars$widthChange;
			fancybars$widthChange = 0;
			return x - change / 2f;
		} finally {
			if (fancybars$perf != 0) dev.fancybars.util.Perf.end("NameTagFeatureNicknameMixin recentre", fancybars$perf);
		}
	}
}
