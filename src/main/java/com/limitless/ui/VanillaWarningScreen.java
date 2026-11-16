package com.limitless.ui;

import com.limitless.compat.VideoRedirect;
import com.limitless.i18n.I18n;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.VideoSettingsScreen;

/**
 * 打开原版视频设置前的警告页。
 * <p>
 * 为什么需要拦一道：原版页面保存时会按原版范围再校验一遍，
 * 超出范围的值会被丢弃或重置（日志里会出现 {@code Error saving option ... outside of range}）。
 * 玩家点进去一圈再回来，模组里辛苦调出来的极限值可能就没了，所以这里先讲清楚再放行。
 * <p>
 * 「返回 mod 菜单」直接回到模组主界面，不在这层里再套一层入口页，避免来回点。
 */
public class VanillaWarningScreen extends Screen {
	private final Screen parent;

	public VanillaWarningScreen(Screen parent) {
		super(I18n.tr("warning.vanilla.title"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		int buttonWidth = 200;
		int centerX = this.width / 2 - buttonWidth / 2;

		this.addRenderableWidget(Button.builder(I18n.tr("warning.vanilla.confirm"), b -> openVanilla())
				.bounds(centerX, this.height / 2 + 24, buttonWidth, 20).build());

		this.addRenderableWidget(Button.builder(I18n.tr("warning.vanilla.back"), b -> backToModMenu())
				.bounds(centerX, this.height / 2 + 50, buttonWidth, 20).build());
	}

	private void openVanilla() {
		Minecraft minecraft = this.minecraft;
		if (minecraft == null) {
			backToModMenu();
			return;
		}
		try {
			// 原版页面初始化后，它自己的「完成」会回到这里传进去的父屏；给模组主界面，
			// 这样玩家从原版页面退出时直接落在 mod 菜单，不用经过入口页
			Screen back = (parent instanceof VideoRedirectScreen redirect) ? redirect.parentScreen() : parent;
			Screen vanilla = new VideoSettingsScreen(back, minecraft, minecraft.options);
			// 走放行通道，否则会被 VideoScreenRedirectMixin 再拦回来
			VideoRedirect.openOriginal(minecraft, vanilla);
		} catch (Throwable t) {
			backToModMenu();
		}
	}

	private void backToModMenu() {
		// 入口页本身是从 mod 菜单进来的，退到它的父屏即回到 mod 菜单
		if (parent instanceof VideoRedirectScreen redirect) {
			this.minecraft.gui.setScreen(redirect.parentScreen());
			return;
		}
		this.minecraft.gui.setScreen(parent);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		graphics.fill(0, 0, this.width, this.height, 0xE6100A0A);
		graphics.centeredText(this.font, this.title, this.width / 2, this.height / 2 - 56, 0xFFFF5555);
		graphics.centeredText(this.font, I18n.tr("warning.vanilla.line1"),
				this.width / 2, this.height / 2 - 30, 0xFFFFD24A);
		graphics.centeredText(this.font, I18n.tr("warning.vanilla.line2"),
				this.width / 2, this.height / 2 - 14, 0xFFCCCCCC);
		graphics.centeredText(this.font, I18n.tr("warning.vanilla.line3"),
				this.width / 2, this.height / 2 + 2, 0xFF999999);
		super.extractRenderState(graphics, mouseX, mouseY, partialTick);
	}

	@Override
	public void onClose() {
		backToModMenu();
	}
}
