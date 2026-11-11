package dev.fancybars.mixin;

import java.util.List;
import java.util.Map;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

import com.google.common.collect.MapMaker;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.util.FormattedCharSequence;

/**
 * Switching chat tabs rebuilds the chat from every message you've received; with the chat limit removed (Devonian)
 * that's thousands of messages, each measured and wrapped into lines again, which froze the game for a moment.
 * The wrapped lines are remembered per message and chat width, so a rebuild just reuses them.
 */
@Mixin(ChatComponent.class)
public abstract class ChatSplitCacheMixin {
	/** Message (by identity, dropped when the message is gone) → the width it was wrapped at and its lines. */
	@Unique
	private static final Map<GuiMessage, Object[]> fancybars$lines = new MapMaker().weakKeys().makeMap();

	@WrapOperation(method = "addMessageToDisplayQueue", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/chat/GuiMessage;splitLines(Lnet/minecraft/client/gui/Font;I)Ljava/util/List;"), require = 0)
	@SuppressWarnings("unchecked")
	private List<FormattedCharSequence> fancybars$cachedLines(GuiMessage message, Font font, int width, Operation<List<FormattedCharSequence>> original) {
		Object[] cached = fancybars$lines.get(message);
		if (cached != null && (Integer) cached[0] == width) return (List<FormattedCharSequence>) cached[1];
		List<FormattedCharSequence> lines = original.call(message, font, width);
		fancybars$lines.put(message, new Object[]{width, lines});
		return lines;
	}
}
