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
		Component swapped = Nicknames.apply(text, Nicknames.Where.WORLD);
		NameTagDebug.hit("nameTagRenderer", text, swapped);
		// whoever swapped it and however old this copy is: colours for this exact frame
		swapped = Nicknames.recolor(swapped, Nicknames.Where.WORLD);
		fancybars$widthChange = swapped == text ? 0 : font.width(swapped) - font.width(text);
		return swapped;
	}

	@ModifyExpressionValue(method = "prepareText", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/feature/NameTagFeatureRenderer$Submit;x()F"), require = 0)
	private static float fancybars$recentre(float x) {
		float change = fancybars$widthChange;
		fancybars$widthChange = 0;
		return x - change / 2f;
	}
}
