package dev.fancybars.mixin;

import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.multiplayer.chat.GuiMessage;

/** The chat's visible lines (after the chat tab filter), to find which message a line belongs to. */
@Mixin(ChatComponent.class)
public interface ChatComponentLinesAccessor {
	@Accessor("trimmedMessages")
	List<GuiMessage.Line> fancybars$getTrimmedMessages();
}
