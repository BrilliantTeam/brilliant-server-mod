package net.brilliantw.richpresence.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.brilliantw.richpresence.EmojiPicker;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.util.StringDecomposer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(StringDecomposer.class)
public class StringDecomposerMixin {
	@ModifyVariable(method = {
			"iterate",
			"iterateBackwards",
			"iterateFormatted(Ljava/lang/String;ILnet/minecraft/network/chat/Style;Lnet/minecraft/network/chat/Style;Lnet/minecraft/util/FormattedCharSink;)Z"
	}, at = @At("HEAD"), argsOnly = true)
	private static String brilliant$emojiSequences(String text) {
		return EmojiPicker.display(text);
	}

	@WrapMethod(method = "getPlainText")
	private static String brilliant$plainText(FormattedText text, Operation<String> original) {
		return EmojiPicker.plain(() -> original.call(text));
	}
}
