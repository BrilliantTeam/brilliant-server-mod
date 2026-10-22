package net.brilliantw.richpresence.mixin;

import net.brilliantw.richpresence.ChannelBar;
import net.brilliantw.richpresence.ChatMenu;
import net.brilliantw.richpresence.ChatTabs;
import net.brilliantw.richpresence.ColorCodes;
import net.brilliantw.richpresence.EmojiPicker;
import net.brilliantw.richpresence.Gfx;
import net.brilliantw.richpresence.PrivateWindow;
import net.brilliantw.richpresence.QuickMessages;
import net.brilliantw.richpresence.RichPresenceModClient;
import net.brilliantw.richpresence.ScriptMonitor;
//? if >=26.1 {
import net.minecraft.client.gui.GuiGraphicsExtractor;
//?} else {
/*import net.minecraft.client.gui.GuiGraphics;
*///?}
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ChatScreen.class)
public abstract class ChatScreenMixin extends Screen {
	@Shadow
	protected EditBox input;

	protected ChatScreenMixin(Component title) {
		super(title);
	}

	@Unique
	private boolean brilliant$typingCommand() {
		boolean typing = input != null && input.getValue().startsWith("/");
		if (typing) {
			EmojiPicker.close();
			ChannelBar.close();
		}
		return typing;
	}

	@Inject(method = "init", at = @At("TAIL"))
	private void brilliant$addPrivateInput(CallbackInfo ci) {
		addRenderableWidget(PrivateWindow.createInput());
		addRenderableWidget(ChatMenu.createSearch());
		ChatMenu.onInit((ChatScreen) (Object) this);
		ChatTabs.open();
	}

	//? if >=26.1 {
	@Inject(method = "extractRenderState", at = @At("HEAD"))
	private void brilliant$renderPrivateWindow(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
	//?} else {
	/*@Inject(method = "render", at = @At("HEAD"))
	private void brilliant$renderPrivateWindow(GuiGraphics graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
	*///?}
		Gfx gfx = new Gfx(graphics);
		PrivateWindow.render(gfx, width, height, mouseX, mouseY, true);
		ScriptMonitor.render(gfx, width, height, mouseX, mouseY, true);
		if (!ChatMenu.isOpen() && getFocused() == ChatMenu.search()) {
			setFocused(input);
		}
		if (!brilliant$typingCommand() && RichPresenceModClient.featuresActive()) {
			ChatMenu.renderBackground((ChatScreen) (Object) this, gfx);
		}
	}

	//? if >=26.1 {
	@Inject(method = "extractRenderState", at = @At("TAIL"))
	private void brilliant$renderEmojiPicker(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
	//?} else {
	/*@Inject(method = "render", at = @At("TAIL"))
	private void brilliant$renderEmojiPicker(GuiGraphics graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
	*///?}
		ChatScreen screen = (ChatScreen) (Object) this;
		Gfx gfx = new Gfx(graphics);
		if (!brilliant$typingCommand()) {
			ChannelBar.render(screen, graphics, mouseX, mouseY);
			EmojiPicker.render(screen, gfx, mouseX, mouseY);
			if (RichPresenceModClient.featuresActive()) {
				QuickMessages.render(screen, gfx, input.getValue(), mouseX, mouseY);
				ChatMenu.render(screen, gfx, mouseX, mouseY);
				ChatTabs.render(screen, gfx, mouseX, mouseY);
			}
		}
		PrivateWindow.renderOverlay(gfx, width, height, mouseX, mouseY);
		if (RichPresenceModClient.isOnBrilliantServer()) {
			EditBox privateInput = PrivateWindow.focusedInput(screen);
			if (privateInput != null) {
				ColorCodes.renderPreview(gfx, font, privateInput.getValue(), privateInput.getX(), privateInput.getY() - 2, width - privateInput.getX() - 4);
			} else {
				ColorCodes.renderPreview(gfx, font, input.getValue(), 2, brilliant$typingCommand() ? height - 16 : ChannelBar.barY(screen) - 2, width - 4);
			}
		}
	}

	@Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
	private void brilliant$clickEmojiPicker(MouseButtonEvent event, boolean doubleClick, CallbackInfoReturnable<Boolean> cir) {
		ChatScreen screen = (ChatScreen) (Object) this;
		if (PrivateWindow.mouseClicked(screen, event, doubleClick) || ScriptMonitor.mouseClicked(screen, event.x(), event.y(), event.button())) {
			cir.setReturnValue(true);
			return;
		}
		if (brilliant$typingCommand()) {
			return;
		}
		if (ChatTabs.mouseClicked(screen, event.x(), event.y(), event.button())
				|| EmojiPicker.mouseClicked(screen, event.x(), event.y(), event.button())
				|| QuickMessages.mouseClicked(screen, input.getValue(), event.x(), event.y(), event.button())
				|| ChannelBar.mouseClicked(screen, event.x(), event.y(), event.button())
				|| ChatMenu.mouseClicked(screen, event.x(), event.y(), event.button())) {
			cir.setReturnValue(true);
		}
	}

	@Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
	private void brilliant$typePrivateMessage(KeyEvent event, CallbackInfoReturnable<Boolean> cir) {
		if (PrivateWindow.keyPressed((ChatScreen) (Object) this, event) || ChatMenu.keyPressed((ChatScreen) (Object) this, event)) {
			cir.setReturnValue(true);
		}
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
		return PrivateWindow.mouseDragged((ChatScreen) (Object) this, event.x(), event.y()) || ScriptMonitor.mouseDragged(event.x(), event.y()) || super.mouseDragged(event, dragX, dragY);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		return PrivateWindow.mouseReleased((ChatScreen) (Object) this, event.x(), event.y()) || ScriptMonitor.mouseReleased() || super.mouseReleased(event);
	}

	@Inject(method = "removed", at = @At("HEAD"))
	private void brilliant$resetTabView(CallbackInfo ci) {
		ChatTabs.close();
	}

	@ModifyVariable(method = "handleChatInput", at = @At("HEAD"), argsOnly = true)
	private String brilliant$applyChannelPrefix(String message) {
		return ChannelBar.applyPrefix(message);
	}

	@Inject(method = "mouseScrolled", at = @At("HEAD"), cancellable = true)
	private void brilliant$scrollEmojiPicker(double mouseX, double mouseY, double scrollX, double scrollY, CallbackInfoReturnable<Boolean> cir) {
		ChatScreen screen = (ChatScreen) (Object) this;
		if (PrivateWindow.mouseScrolled(screen, mouseX, mouseY, scrollY)) {
			cir.setReturnValue(true);
			return;
		}
		if (brilliant$typingCommand()) {
			return;
		}
		if (EmojiPicker.mouseScrolled(screen, mouseX, mouseY, scrollY) || QuickMessages.mouseScrolled(screen, mouseX, mouseY, scrollY)
				|| ChatMenu.mouseScrolled(screen, mouseX, mouseY, scrollY)) {
			cir.setReturnValue(true);
		}
	}
}
