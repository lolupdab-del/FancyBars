package dev.fancybars.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import dev.fancybars.players.NameColors;
import dev.fancybars.players.Nicknames;

/**
 * Hypixel's name tags (including your own in F5) are floating text display entities, not player name tags. Their
 * text is split into lines once and then drawn from that cache every frame, so: the name is swapped in when the lines
 * are split, and recoloured each frame as they're drawn so it keeps flowing.
 */
@Mixin(targets = "net.minecraft.client.renderer.entity.DisplayRenderer$TextDisplayRenderer")
public abstract class TextDisplayNicknameMixin {
	@ModifyVariable(method = "splitLines", at = @At("HEAD"), argsOnly = true, require = 0)
	private Component fancybars$swapNames(Component text) {
		Component replaced = Nicknames.apply(text, Nicknames.Where.WORLD);
		dev.fancybars.players.NameTagDebug.hit("textDisplay.splitLines", text, replaced);
		return replaced;
	}

	@ModifyExpressionValue(method = "submitInner(Lnet/minecraft/client/renderer/entity/state/TextDisplayEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;IF)V",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Display$TextDisplay$CachedLine;contents()Lnet/minecraft/util/FormattedCharSequence;"), require = 0)
	private FormattedCharSequence fancybars$flowNames(FormattedCharSequence line) {
		return NameColors.wrap(line, false);
	}
}
