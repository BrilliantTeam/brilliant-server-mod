package net.brilliantw.richpresence.mixin;

import net.brilliantw.richpresence.ChatHistoryScreen;
import net.brilliantw.richpresence.ChatTabs;
import net.brilliantw.richpresence.DiscordImageLinks;
import net.brilliantw.richpresence.RichPresenceModClient;
import net.brilliantw.richpresence.PrivateMessages;
import net.brilliantw.richpresence.ScriptEngine;
import net.minecraft.client.gui.components.ChatComponent;
//? if >=26.1 {
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.client.multiplayer.chat.GuiMessageSource;
import net.minecraft.client.multiplayer.chat.GuiMessageTag;
//?} else {
/*import net.minecraft.client.GuiMessage;
import net.minecraft.client.GuiMessageTag;
*///?}
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MessageSignature;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChatComponent.class)
public abstract class ChatComponentMixin {
	@Inject(method = "addMessageToDisplayQueue", at = @At("HEAD"), cancellable = true)
	private void brilliant$filterTab(GuiMessage message, CallbackInfo ci) {
		if (!ChatTabs.shows(message.content())) {
			ci.cancel();
		}
	}

	//? if >=26.1 {
	@Inject(method = "addMessage(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/MessageSignature;Lnet/minecraft/client/multiplayer/chat/GuiMessageSource;Lnet/minecraft/client/multiplayer/chat/GuiMessageTag;)V", at = @At("HEAD"), cancellable = true)
	private void brilliant$readChat(Component message, MessageSignature signature, GuiMessageSource source, GuiMessageTag tag, CallbackInfo ci) {
	//?} else {
	/*@Inject(method = "addMessage(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/MessageSignature;Lnet/minecraft/client/GuiMessageTag;)V", at = @At("HEAD"), cancellable = true)
	private void brilliant$readChat(Component message, MessageSignature signature, GuiMessageTag tag, CallbackInfo ci) {
	*///?}
		if (!RichPresenceModClient.featuresActive()) {
			return;
		}
		ChatHistoryScreen.record(message);
		ChatTabs.track(message);
		ScriptEngine.onChat(message.getString());
		DiscordImageLinks.scan(message);
		if (PrivateMessages.onChatMessage(message) && PrivateMessages.hideInChat()) {
			ci.cancel();
		}
	}
}
