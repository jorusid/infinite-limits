package com.limitless.mixin;

import com.limitless.LimitlessClient;
import com.limitless.i18n.I18n;
import com.limitless.ui.LimitlessScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.OptionsList;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.OptionsSubScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Set;

/**
 * 把被接管的设置页“清空 + 加提示”。
 *
 * 做法是等原版把参数都塞进 OptionsList 之后，再清掉列表内容——不去动参数数组。
 * 之前直接返回空数组会让 AccessibilityOptionsScreen 这类会取 options[0] 的页面下标越界崩溃。
 */
@Mixin(OptionsSubScreen.class)
public abstract class OptionsHintMixin {
	private static final Set<String> MANAGED_PAGES = Set.of(
			"net.minecraft.client.gui.screens.options.VideoSettingsScreen",
			"net.minecraft.client.gui.screens.options.SoundOptionsScreen",
			"net.minecraft.client.gui.screens.options.ChatOptionsScreen",
			"net.minecraft.client.gui.screens.options.MouseSettingsScreen",
			"net.minecraft.client.gui.screens.options.AccessibilityOptionsScreen");

	@Shadow
	protected OptionsList list;

	@Inject(method = "addContents", at = @At("TAIL"))
	private void limitless$replaceWithHint(CallbackInfo ci) {
		try {
			// 玩家从模组菜单点「原版设置」进来的：整棵原版设置树都保持原样，
			// 音频 / 聊天 / 鼠标 / 辅助功能这些页该有的条目一个都不能少
			if (com.limitless.compat.VideoRedirect.inVanillaTree()) {
				return;
			}
			if (this.list == null) {
				return;
			}
			Screen self = (Screen) (Object) this;
			List<?> entries = this.list.children();

			if (MANAGED_PAGES.contains(self.getClass().getName()) && !entries.isEmpty()) {
				clearEntries();
				entries = this.list.children();
				LimitlessClient.LOGGER.info("[Limitless] 已清空设置页：{}", self.getClass().getSimpleName());
			}

			if (!entries.isEmpty()) {
				return;
			}

			this.list.addHeader(I18n.tr("hint.vanillaTitle"));
			this.list.addBig(Button.builder(
					I18n.tr("hint.vanillaButton"),
					b -> Minecraft.getInstance().gui.setScreen(new LimitlessScreen(self)))
					.bounds(0, 0, 310, 20).build());
		} catch (Throwable t) {
			LimitlessClient.LOGGER.warn("[Limitless] 设置页提示插入失败", t);
		}
	}

	private void clearEntries() {
		for (Class<?> type = this.list.getClass(); type != null; type = type.getSuperclass()) {
			try {
				Method method = type.getDeclaredMethod("clearEntries");
				method.setAccessible(true);
				method.invoke(this.list);
				return;
			} catch (NoSuchMethodException ignored) {
				// 继续往父类找
			} catch (Throwable ignored) {
				return;
			}
		}
	}
}
