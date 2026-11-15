package com.limitless.mixin;

import com.limitless.config.LimitlessConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;

/** 0~1 区间的双精度项（音量、灵敏度、实体距离倍数等）解除钳制。 */
@Mixin(targets = "net.minecraft.client.OptionInstance$UnitDouble")
public abstract class UnitDoubleMixin {
	@Inject(method = "validateValue(Ljava/lang/Double;)Ljava/util/Optional;", at = @At("HEAD"), cancellable = true)
	private void limitless$unclamp(Double value, CallbackInfoReturnable<Optional<Double>> cir) {
		if (LimitlessConfig.get().extremeMode && value != null) {
			cir.setReturnValue(Optional.of(value));
		}
	}
}
