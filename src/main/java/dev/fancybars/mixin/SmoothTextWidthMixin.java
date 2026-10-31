package dev.fancybars.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.client.gui.Font;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.util.FormattedCharSequence;

import dev.fancybars.util.gui.SmoothText;

/** Text in SBCLIENT's fonts is measured with the same engine that draws it, so centring is exact. */
@Mixin(Font.class)
public abstract class SmoothTextWidthMixin {
	@Inject(method = "width(Lnet/minecraft/util/FormattedCharSequence;)I", at = @At("HEAD"), cancellable = true, require = 0)
	private void fancybars$widthSequence(FormattedCharSequence text, CallbackInfoReturnable<Integer> cir) {
		float w = SmoothText.width(text);
		if (w >= 0) cir.setReturnValue(Math.round(w));
	}

	@Inject(method = "width(Lnet/minecraft/network/chat/FormattedText;)I", at = @At("HEAD"), cancellable = true, require = 0)
	private void fancybars$widthText(FormattedText text, CallbackInfoReturnable<Integer> cir) {
		float w = SmoothText.width(Language.getInstance().getVisualOrder(text));
		if (w >= 0) cir.setReturnValue(Math.round(w));
	}
}
