package dev.fancybars.mixin;

import java.util.concurrent.CompletableFuture;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.suggestion.Suggestions;

import net.minecraft.client.gui.components.CommandSuggestions;
import net.minecraft.client.gui.components.EditBox;

import dev.fancybars.commands.GfsCommand;

/** /gfs suggestions: the chat box asks the mod for /gfs and /getfromsacks instead of the server (whose only offer is player names). */
@Mixin(CommandSuggestions.class)
public abstract class GfsSuggestionsMixin {
	@Shadow
	@Final
	private EditBox input;

	@WrapOperation(method = "updateCommandInfo", at = @At(value = "INVOKE", target = "Lcom/mojang/brigadier/CommandDispatcher;getCompletionSuggestions(Lcom/mojang/brigadier/ParseResults;I)Ljava/util/concurrent/CompletableFuture;"), require = 0)
	private CompletableFuture<Suggestions> fancybars$gfsSuggestions(CommandDispatcher<?> dispatcher, ParseResults<?> parse, int cursor, Operation<CompletableFuture<Suggestions>> original) {
		CompletableFuture<Suggestions> ours = GfsCommand.suggest(input.getValue(), input.getCursorPosition());
		return ours != null ? ours : original.call(dispatcher, parse, cursor);
	}
}
