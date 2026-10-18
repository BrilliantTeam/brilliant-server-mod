package net.brilliantw.richpresence.mixin;

//? if >=26.2 {
import net.minecraft.client.gui.Hud;
//?} else {
/*import net.minecraft.client.gui.Gui;
*///?}
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

//? if >=26.2 {
@Mixin(Hud.class)
//?} else {
/*@Mixin(Gui.class)
*///?}
public interface HudTextAccessor {
	@Accessor("title")
	Component brilliant$title();

	@Accessor("subtitle")
	Component brilliant$subtitle();

	@Accessor("titleTime")
	int brilliant$titleTime();

	@Accessor("overlayMessageString")
	Component brilliant$overlayMessage();

	@Accessor("overlayMessageTime")
	int brilliant$overlayMessageTime();
}
