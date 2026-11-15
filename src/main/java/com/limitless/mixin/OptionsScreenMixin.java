package com.limitless.mixin;

import com.limitless.LimitlessClient;
import com.limitless.ui.LimitlessScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Method;

/** 在原版设置主页左上角加入「无限」入口。 */
@Mixin(OptionsScreen.class)
public abstract class OptionsScreenMixin {
	@Inject(method = "init", at = @At("TAIL"))
	private void limitless$entryButton(CallbackInfo ci) {
		Button entry = Button.builder(Component.literal("无限 Limitless"), b -> {
			Minecraft mc = Minecraft.getInstance();
			mc.gui.setScreen(new LimitlessScreen(mc.gui.screen()));
		}).bounds(8, 6, 120, 20).build();

		try {
			// addRenderableWidget(T) 泛型擦除后参数类型是 GuiEventListener
			Method adder = Screen.class.getDeclaredMethod("addRenderableWidget", GuiEventListener.class);
			adder.setAccessible(true);
			adder.invoke(this, entry);
		} catch (Throwable t) {
			LimitlessClient.LOGGER.warn("[Limitless] 入口按钮注册失败", t);
		}
	}
}
