package net.brilliantw.richpresence.mixin;

import net.brilliantw.richpresence.CommandAliases;
import net.brilliantw.richpresence.DiscordImageLinks;
import net.minecraft.network.protocol.game.ClientboundCommandsPacket;
import net.minecraft.network.protocol.game.ClientboundSoundEntityPacket;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.brilliantw.richpresence.ScriptEngine;
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

	@Inject(method = "handleSoundEvent", at = @At("TAIL"))
	private void brilliant$heardSound(ClientboundSoundPacket packet, CallbackInfo ci) {
		ScriptEngine.onSound(packet.getSound().value().location().toString(), packet.getX(), packet.getY(), packet.getZ());
	}

	@Inject(method = "handleSoundEntityEvent", at = @At("TAIL"))
	private void brilliant$heardEntitySound(ClientboundSoundEntityPacket packet, CallbackInfo ci) {
		var level = net.minecraft.client.Minecraft.getInstance().level;
		var entity = level == null ? null : level.getEntity(packet.getId());
		if (entity != null) {
			ScriptEngine.onSound(packet.getSound().value().location().toString(), entity.getX(), entity.getY(), entity.getZ());
		}
	}

	@Inject(method = "handleCommands", at = @At("TAIL"))
	private void brilliant$installAliases(ClientboundCommandsPacket packet, CallbackInfo ci) {
		CommandAliases.install();
	}
}
