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

/** Text in SkyblockGuiUtils's fonts is measured with the same engine that draws it, so centring is exact. */
@Mixin(Font.class)
public abstract class SmoothTextWidthMixin {
	@Inject(method = "width(Lnet/minecraft/util/FormattedCharSequence;)I", at = @At("HEAD"), cancellable = true, require = 0)
	private void fancybars$widthSequence(FormattedCharSequence text, CallbackInfoReturnable<Integer> cir) {
		long fancybars$perf = dev.fancybars.util.Perf.begin();
		try {
			float w = SmoothText.width(text);
			if (w >= 0) cir.setReturnValue(Math.round(w));
		} finally {
			if (fancybars$perf != 0) dev.fancybars.util.Perf.end("SmoothTextWidthMixin widthSequence", fancybars$perf);
		}
	}

	@Inject(method = "width(Lnet/minecraft/network/chat/FormattedText;)I", at = @At("HEAD"), cancellable = true, require = 0)
	private void fancybars$widthText(FormattedText text, CallbackInfoReturnable<Integer> cir) {
		long fancybars$perf = dev.fancybars.util.Perf.begin();
		try {
			// most text isn't ours: don't build its visual order (a full pass over it) just to find that out
			if (!SmoothText.mightBeSmooth(text)) return;
			float w = SmoothText.width(text);
			if (w >= 0) cir.setReturnValue(Math.round(w));
		} finally {
			if (fancybars$perf != 0) dev.fancybars.util.Perf.end("SmoothTextWidthMixin widthText", fancybars$perf);
		}
	}
}
