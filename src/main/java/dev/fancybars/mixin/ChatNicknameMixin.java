package dev.fancybars.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.network.chat.Component;

import dev.fancybars.players.Nicknames;

/** Nicknames in chat, swapped in as the line is added to the chat box (after every chat event has seen the real IGN). */
@Mixin(ChatComponent.class)
public abstract class ChatNicknameMixin {
	@ModifyVariable(method = "addMessage(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/MessageSignature;Lnet/minecraft/client/multiplayer/chat/GuiMessageSource;Lnet/minecraft/client/multiplayer/chat/GuiMessageTag;)V",
			at = @At("HEAD"), argsOnly = true, ordinal = 0, require = 0)
	private Component fancybars$nicknames(Component message) {
		return Nicknames.apply(message, Nicknames.Where.CHAT);
	}
}
