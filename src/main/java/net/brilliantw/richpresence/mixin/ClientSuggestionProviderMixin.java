package net.brilliantw.richpresence.mixin;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import java.util.concurrent.CompletableFuture;
import net.brilliantw.richpresence.CommandAliases;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.ClientSuggestionProvider;
import net.minecraft.network.protocol.game.ServerboundCommandSuggestionPacket;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ClientSuggestionProvider.class)
public abstract class ClientSuggestionProviderMixin {
	@Shadow
	@Final
	private ClientPacketListener connection;

	@Shadow
	private int pendingSuggestionsId;

	@Shadow
	private CompletableFuture<Suggestions> pendingSuggestionsFuture;

	@Inject(method = "customSuggestion", at = @At("HEAD"), cancellable = true)
	private void brilliant$aliasSuggestion(CommandContext<?> context, CallbackInfoReturnable<CompletableFuture<Suggestions>> cir) {
		int id = pendingSuggestionsId + 1;
		String rewritten = CommandAliases.rewriteSuggestion(id, context.getInput());
		if (rewritten == null) {
			return;
		}
		if (pendingSuggestionsFuture != null) {
			pendingSuggestionsFuture.cancel(false);
		}
		pendingSuggestionsFuture = new CompletableFuture<>();
		pendingSuggestionsId = id;
		connection.send(new ServerboundCommandSuggestionPacket(id, rewritten));
		cir.setReturnValue(pendingSuggestionsFuture);
	}

	@Inject(method = "completeCustomSuggestions", at = @At("HEAD"), cancellable = true)
	private void brilliant$aliasShift(int id, Suggestions suggestions, CallbackInfo ci) {
		Suggestions shifted = CommandAliases.shift(id, suggestions);
		if (shifted != null) {
			ci.cancel();
			((ClientSuggestionProvider) (Object) this).completeCustomSuggestions(id, shifted);
		}
	}
}
