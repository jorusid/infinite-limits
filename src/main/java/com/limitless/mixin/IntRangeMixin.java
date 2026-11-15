package com.limitless.mixin;

import com.limitless.config.LimitlessConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;

/** 整数滑块范围（FOV、渲染距离、帧率上限等）解除钳制。 */
@Mixin(targets = "net.minecraft.client.OptionInstance$IntRange")
public abstract class IntRangeMixin {
	@Inject(method = "validateValue(Ljava/lang/Integer;)Ljava/util/Optional;", at = @At("HEAD"), cancellable = true)
	private void limitless$unclamp(Integer value, CallbackInfoReturnable<Optional<Integer>> cir) {
		if (LimitlessConfig.get().extremeMode && value != null) {
			cir.setReturnValue(Optional.of(value));
		}
	}
}
