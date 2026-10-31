package dev.fancybars.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import dev.fancybars.util.gui.SmoothText;

/** Routes text in SBCLIENT's fonts through {@link SmoothText} for crisp, browser-quality edges. */
@Mixin(GuiGraphicsExtractor.class)
public abstract class SmoothTextMixin {
	@Inject(method = "text(Lnet/minecraft/client/gui/Font;Lnet/minecraft/util/FormattedCharSequence;IIIZ)V", at = @At("HEAD"), cancellable = true, require = 0)
	private void fancybars$smoothSequence(Font font, FormattedCharSequence text, int x, int y, int color, boolean shadow, CallbackInfo ci) {
		if (SmoothText.draw((GuiGraphicsExtractor) (Object) this, text, x, y, color, shadow)) ci.cancel();
	}

	@Inject(method = "text(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;IIIZ)V", at = @At("HEAD"), cancellable = true, require = 0)
	private void fancybars$smoothComponent(Font font, Component text, int x, int y, int color, boolean shadow, CallbackInfo ci) {
		if (SmoothText.draw((GuiGraphicsExtractor) (Object) this, Language.getInstance().getVisualOrder(text), x, y, color, shadow)) ci.cancel();
	}
}
