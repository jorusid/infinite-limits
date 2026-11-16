package com.limitless.mixin;

import com.limitless.ui.UpdatePromptScreen;
import com.limitless.update.UpdateChecker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 在主菜单每秒的 tick 里判断是否该弹更新提示。
 * <p>
 * 之所以挂在 tick 而不是 init：检查是异步的，等它回来时主菜单可能早就初始化完了。
 * tick 每帧都会跑，用静态标记保证一次会话只弹一次。
 */
@Mixin(TitleScreen.class)
public abstract class TitleScreenUpdateMixin {
	private static boolean limitless$prompted = false;

	@Inject(method = "tick", at = @At("HEAD"))
	private void limitless$promptUpdate(CallbackInfo ci) {
		if (limitless$prompted || !UpdateChecker.shouldPrompt()) {
			return;
		}
		limitless$prompted = true;
		try {
			Minecraft client = Minecraft.getInstance();
			client.execute(() -> client.gui.setScreen(new UpdatePromptScreen()));
		} catch (Throwable ignored) {
			// 弹窗失败不能影响主菜单
		}
	}
}
