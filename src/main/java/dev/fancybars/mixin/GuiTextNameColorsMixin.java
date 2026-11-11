package dev.fancybars.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import net.minecraft.client.renderer.state.gui.GuiTextRenderState;
import net.minecraft.util.FormattedCharSequence;

import dev.fancybars.players.NameColors;

/** Every piece of GUI text is turned into glyphs here, each frame, right before drawing: custom names get their colours. */
@Mixin(GuiTextRenderState.class)
public abstract class GuiTextNameColorsMixin {
	@ModifyExpressionValue(method = "ensurePrepared", at = @At(value = "FIELD", target = "Lnet/minecraft/client/renderer/state/gui/GuiTextRenderState;text:Lnet/minecraft/util/FormattedCharSequence;"), require = 0)
	private FormattedCharSequence fancybars$nameColors(FormattedCharSequence text) {
		return NameColors.wrap(text);
	}
}
