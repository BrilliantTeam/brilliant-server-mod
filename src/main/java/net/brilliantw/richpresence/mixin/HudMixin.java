package net.brilliantw.richpresence.mixin;

import net.brilliantw.richpresence.Gfx;
import net.brilliantw.richpresence.Minimap;
import net.brilliantw.richpresence.PrivateWindow;
import net.brilliantw.richpresence.ScoreboardLayout;
import net.brilliantw.richpresence.ScriptMonitor;
import net.minecraft.client.DeltaTracker;
//? if >=26.2 {
import net.minecraft.client.gui.Hud;
//?} else {
/*import net.minecraft.client.gui.Gui;
*///?}
//? if >=26.1 {
import net.minecraft.client.gui.GuiGraphicsExtractor;
//?} else {
/*import net.minecraft.client.gui.GuiGraphics;
*///?}
import net.minecraft.world.scores.Objective;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

//? if >=26.2 {
@Mixin(Hud.class)
//?} else {
/*@Mixin(Gui.class)
*///?}
public abstract class HudMixin {
	//? if >=26.1 {
	@Inject(method = "displayScoreboardSidebar", at = @At("HEAD"))
	private void brilliant$beginSidebar(GuiGraphicsExtractor graphics, Objective objective, CallbackInfo ci) {
		ScoreboardLayout.begin(graphics.pose());
	}

	@Inject(method = "displayScoreboardSidebar", at = @At("RETURN"))
	private void brilliant$endSidebar(GuiGraphicsExtractor graphics, Objective objective, CallbackInfo ci) {
		ScoreboardLayout.end(graphics.pose());
	}
	//?} else {
	/*@Inject(method = "displayScoreboardSidebar", at = @At("HEAD"))
	private void brilliant$beginSidebar(GuiGraphics graphics, Objective objective, CallbackInfo ci) {
		ScoreboardLayout.begin(graphics.pose());
	}

	@Inject(method = "displayScoreboardSidebar", at = @At("RETURN"))
	private void brilliant$endSidebar(GuiGraphics graphics, Objective objective, CallbackInfo ci) {
		ScoreboardLayout.end(graphics.pose());
	}
	*///?}

	//? if >=26.1 {
	@Inject(method = "extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/DeltaTracker;)V", at = @At("TAIL"))
	private void brilliant$renderPrivateWindow(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker, CallbackInfo ci) {
	//?} else {
	/*@Inject(method = "render(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/client/DeltaTracker;)V", at = @At("TAIL"))
	private void brilliant$renderPrivateWindow(GuiGraphics graphics, DeltaTracker deltaTracker, CallbackInfo ci) {
	*///?}
		//? if >=26.2 {
		if (((Hud) (Object) this).isHidden()) {
			return;
		}
		//?}
		Gfx gfx = new Gfx(graphics);
		Minimap.renderHud(gfx);
		if (ScriptMonitor.onTop) {
			PrivateWindow.renderHud(gfx);
			ScriptMonitor.renderHud(gfx);
		} else {
			ScriptMonitor.renderHud(gfx);
			PrivateWindow.renderHud(gfx);
		}
	}
}
