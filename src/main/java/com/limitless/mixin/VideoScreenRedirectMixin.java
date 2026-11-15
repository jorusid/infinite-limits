package com.limitless.mixin;

import com.limitless.ui.LimitlessScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 视频设置页重定向：不管是原版视频页还是钠（Sodium）的视频页，一律换成模组自己的界面。
 * 用类名字符串判断，避免对钠产生编译期依赖。
 */
@Mixin(Gui.class)
public abstract class VideoScreenRedirectMixin {
	private static final String VANILLA_VIDEO = "net.minecraft.client.gui.screens.options.VideoSettingsScreen";
	private static final String SODIUM_VIDEO = "net.caffeinemc.mods.sodium.client.gui.VideoSettingsScreen";

	@Inject(method = "setScreen", at = @At("HEAD"), cancellable = true)
	private void limitless$redirectVideoSettings(Screen screen, CallbackInfo ci) {
		if (screen == null) {
			return;
		}
		String name = screen.getClass().getName();
		if (!VANILLA_VIDEO.equals(name) && !SODIUM_VIDEO.equals(name)) {
			return;
		}
		try {
			Gui self = (Gui) (Object) this;
			Screen parent = LimitlessScreen.findParent(screen);
			if (parent == null) {
				parent = self.screen();
			}
			self.setScreen(new LimitlessScreen(parent));
			ci.cancel();
		} catch (Throwable ignored) {
			// 重定向失败就交回原来的页面，不阻塞
		}
	}
}
