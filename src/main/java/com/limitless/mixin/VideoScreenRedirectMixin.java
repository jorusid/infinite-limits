package com.limitless.mixin;

import com.limitless.compat.VideoRedirect;
import com.limitless.ui.LimitlessScreen;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 视频设置页接管。
 * <p>
 * 策略：凡是「看起来是视频设置」的页面一律接管，换成模组自己的界面；同时把被接管的
 * 页面实例记进 {@link VideoRedirect}，用户在模组界面里可以一键放行，打开原页面。
 * <p>
 * 为什么不用类型判断：Fabric 没有 Forge 那种 ConfigScreen API，各模组视频页的类各不相同，
 * 有的还来自不同命名空间的同名类。直接引用会让本模组对这些模组产生编译期/加载期依赖，
 * 缺席时直接 NoClassDefFoundError。所以按类名特征匹配，尽量宽——宁可能拦到一个不该拦的
 * 页面（用户有放行按钮可以回去），也不要漏掉一个把参数塞进视频页的模组。
 * <p>
 * 放行开关是必须的：不设 {@link VideoRedirect#isBypass()} 的话，从模组界面打开原页面时
 * 会再次命中本方法，形成「打开→拦截→再打开」的死循环。
 */
@Mixin(Gui.class)
public abstract class VideoScreenRedirectMixin {
	private static final String VANILLA_VIDEO = "net.minecraft.client.gui.screens.options.VideoSettingsScreen";
	private static final String SODIUM_VIDEO = "net.caffeinemc.mods.sodium.client.gui.VideoSettingsScreen";

	@Inject(method = "setScreen", at = @At("HEAD"), cancellable = true)
	private void limitless$redirectVideoSettings(Screen screen, CallbackInfo ci) {
		if (screen == null || VideoRedirect.isBypass()) {
			return;
		}
		if (screen instanceof LimitlessScreen) {
			// 回到模组自己的界面，说明已经离开原版设置树了
			VideoRedirect.exitVanillaTree();
			return;
		}

		// 已经在原版设置树里（玩家从模组菜单点了「原版设置」），整棵子树都不接管，
		// 否则会出现「原版设置里再点视频设置又被拦回模组界面」这种自相矛盾的行为
		if (VideoRedirect.inVanillaTree()) {
			return;
		}

		String name = screen.getClass().getName();
		if (!looksLikeVideoSettings(name)) {
			return;
		}

		try {
			Gui self = (Gui) (Object) this;
			Screen parent = LimitlessScreen.findParent(screen);
			if (parent == null) {
				parent = self.screen();
			}
			VideoRedirect.remember(screen);
			self.setScreen(new LimitlessScreen(parent));
			ci.cancel();
		} catch (Throwable ignored) {
			// 接管失败就交回原来的页面，不阻塞游戏
		}
	}

	/**
	 * 类名特征判断：原版与钠走白名单，其余按「类名长得像视频设置页」兜底。
	 * 这样即使某个模组改了自己的类名，只要还叫 VideoSettings 系列就能被接管。
	 */
	private static boolean looksLikeVideoSettings(String className) {
		if (VANILLA_VIDEO.equals(className) || SODIUM_VIDEO.equals(className)) {
			return true;
		}
		String simple = className.substring(className.lastIndexOf('.') + 1);
		return simple.endsWith("VideoSettingsScreen")
				|| simple.endsWith("VideoSettings")
				|| simple.endsWith("GraphicsSettingsScreen");
	}
}
