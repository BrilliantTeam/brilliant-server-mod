package net.brilliantw.richpresence.mixin;

import net.brilliantw.richpresence.CommandAliases;
import net.brilliantw.richpresence.DiscordImageLinks;
import net.minecraft.network.protocol.game.ClientboundCommandsPacket;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientPacketListener;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public abstract class ClientPacketListenerMixin {
	@Inject(method = "sendUnattendedCommand", at = @At("HEAD"), cancellable = true)
	private void brilliant$openImageViewer(String command, Screen screen, CallbackInfo ci) {
		if (DiscordImageLinks.intercept(command)) {
			ci.cancel();
		}
	}

	@Inject(method = "sendCommand", at = @At("HEAD"), cancellable = true)
	private void brilliant$expandAlias(String command, CallbackInfo ci) {
		if (CommandAliases.send((ClientPacketListener) (Object) this, command)) {
			ci.cancel();
		}
	}

	@Inject(method = "handleCommands", at = @At("TAIL"))
	private void brilliant$installAliases(ClientboundCommandsPacket packet, CallbackInfo ci) {
		CommandAliases.install();
	}
}
