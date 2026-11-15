package com.limitless.ui;

import com.limitless.compat.VideoRedirect;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * 视频设置入口页：把被本模组接管过的那些视频设置页逐个列出来，点哪一行就放行哪一个。
 * 单独做一层而不是直接在主界面右下角摆按钮，是因为装了多个占视频页的模组时会有多个来源，
 * 直接摆会把底部挤满，也分不清点的是谁家的页面。
 */
public class VideoRedirectScreen extends Screen {
	private static final int ROW_HEIGHT = 24;
	private static final int BUTTON_WIDTH = 320;

	private final Screen parent;

	public VideoRedirectScreen(Screen parent) {
		super(Component.literal("视频设置入口"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		List<VideoRedirect.Captured> list = VideoRedirect.list();
		int totalHeight = list.size() * ROW_HEIGHT;
		int startY = Math.max(64, this.height / 2 - totalHeight / 2);

		for (int i = 0; i < list.size(); i++) {
			VideoRedirect.Captured captured = list.get(i);
			String label = "打开 " + captured.label();
			if (label.length() > 34) {
				label = label.substring(0, 33) + "...";
			}
			Screen target = captured.screen();
			this.addRenderableWidget(Button.builder(Component.literal(label),
					b -> VideoRedirect.openOriginal(this.minecraft, target))
					.bounds(this.width / 2 - BUTTON_WIDTH / 2, startY + i * ROW_HEIGHT, BUTTON_WIDTH, 20).build());
		}

		this.addRenderableWidget(Button.builder(Component.literal("返回"), b -> onClose())
				.bounds(this.width / 2 - 80, this.height - 44, 160, 20).build());
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		graphics.fill(0, 0, this.width, this.height, 0xCC101010);
		graphics.centeredText(this.font, this.title, this.width / 2, 14, 0xFFFFFFFF);
		graphics.centeredText(this.font,
				Component.literal("点任意一项，打开那个模组自己的视频设置页（不会再次被本模组拦下）"),
				this.width / 2, 30, 0xFFA0A0A0);

		if (VideoRedirect.isEmpty()) {
			graphics.centeredText(this.font,
					Component.literal("还没有接管到任何页面：先去主菜单点一次「视频设置」"),
					this.width / 2, this.height / 2, 0xFFFFC24A);
		}

		super.extractRenderState(graphics, mouseX, mouseY, partialTick);
	}

	@Override
	public void onClose() {
		this.minecraft.gui.setScreen(this.parent);
	}
}
