package com.limitless.ui;

import com.limitless.compat.VideoRedirect;
import com.limitless.i18n.I18n;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;

import java.util.List;

/**
 * 视频设置入口页：把被本模组接管过的那些视频设置页逐个列出来，点哪一行就放行哪一个。
 * <p>
 * 顶部固定一条「打开原版视频设置」，不管有没有接管到页面都能进原版页——
 * 有些玩家就是想对照一下原版长什么样。原版页会覆盖保存、按原版范围校验，
 * 所以点它之前先过一道警告页（{@link VanillaWarningScreen}）。
 * <p>
 * 单独做一层而不是直接在主界面右下角摆按钮，是因为装了多个占视频页的模组时会有多个来源，
 * 直接摆会把底部挤满，也分不清点的是谁家的页面。
 */
public class VideoRedirectScreen extends Screen {
	private static final int ROW_HEIGHT = 24;
	private static final int BUTTON_WIDTH = 320;

	private final Screen parent;

	public VideoRedirectScreen(Screen parent) {
		super(I18n.tr("redirect.title"));
		this.parent = parent;
	}

	/** 入口页自己的父屏（模组主界面），给原版页面当「返回」目标用。 */
	public Screen parentScreen() {
		return parent;
	}

	@Override
	protected void init() {
		List<VideoRedirect.Captured> list = VideoRedirect.list();
		// 第一行永远是原版视频设置入口，后面才是被接管的各种页面
		int rowCount = list.size() + 1;
		int startY = Math.max(56, this.height / 2 - rowCount * ROW_HEIGHT / 2);

		this.addRenderableWidget(Button.builder(I18n.tr("redirect.vanilla"),
				b -> this.minecraft.gui.setScreen(new VanillaWarningScreen(this)))
				.bounds(this.width / 2 - BUTTON_WIDTH / 2, startY, BUTTON_WIDTH, 20).build());

		for (int i = 0; i < list.size(); i++) {
			VideoRedirect.Captured captured = list.get(i);
			Screen target = captured.screen();
			this.addRenderableWidget(Button.builder(
					I18n.tr("redirect.open", captured.label()),
					b -> VideoRedirect.openOriginal(this.minecraft, target))
					.bounds(this.width / 2 - BUTTON_WIDTH / 2, startY + (i + 1) * ROW_HEIGHT, BUTTON_WIDTH, 20)
					.build());
		}

		this.addRenderableWidget(Button.builder(I18n.tr("redirect.back"), b -> onClose())
				.bounds(this.width / 2 - 80, this.height - 44, 160, 20).build());
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		graphics.fill(0, 0, this.width, this.height, 0xCC101010);
		graphics.centeredText(this.font, this.title, this.width / 2, 14, 0xFFFFFFFF);
		graphics.centeredText(this.font, I18n.tr("redirect.hint"), this.width / 2, 30, 0xFFA0A0A0);

		if (VideoRedirect.isEmpty()) {
			graphics.centeredText(this.font, I18n.tr("redirect.empty"),
					this.width / 2, this.height - 66, 0xFFFFC24A);
		}

		super.extractRenderState(graphics, mouseX, mouseY, partialTick);
	}

	@Override
	public void onClose() {
		this.minecraft.gui.setScreen(this.parent);
	}
}
