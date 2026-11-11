package dev.fancybars.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import net.minecraft.client.gui.screens.ChatScreen;

import dev.fancybars.players.Nicknames;

/** Shift-clicking a swapped name in chat types the player's real IGN, without the hidden "already swapped" marker. */
@Mixin(ChatScreen.class)
public abstract class ChatInsertNicknameMixin {
	@ModifyVariable(method = "insertText(Ljava/lang/String;Z)V", at = @At("HEAD"), argsOnly = true, ordinal = 0, require = 0)
	private String fancybars$realIgn(String text) {
		return Nicknames.unmarkInsertion(text);
	}
}
