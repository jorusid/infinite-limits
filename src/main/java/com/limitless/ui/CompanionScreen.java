package com.limitless.ui;

import com.limitless.i18n.I18n;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 「伴侣模组」页：列出同系列的其他模组，每个一行说明 + 一个按钮直达仓库。
 * <p>
 * 文案走翻译键，跟随客户端语言；链接走原版的 {@link ConfirmLinkScreen} 确认弹窗，
 * 和主界面的 GitHub 按钮保持一致的行为。
 */
public class CompanionScreen extends Screen {
	private static final int ROW_HEIGHT = 34;

	/** 一条伴侣模组：名称键、说明键、链接。 */
	private record Entry(String nameKey, String descKey, String url) {
	}

	private final Screen parent;
	private final List<Entry> entries = new ArrayList<>();

	public CompanionScreen(Screen parent) {
		super(I18n.tr("companion.title"));
		this.parent = parent;
		entries.add(new Entry("companion.world.name", "companion.world.desc",
				"https://github.com/jorusid/custom-world"));
	}

	@Override
	protected void init() {
		int left = this.width / 2 - 160;
		int top = 54;

		for (int i = 0; i < entries.size(); i++) {
			Entry entry = entries.get(i);
			final String url = entry.url();
			this.addRenderableWidget(Button.builder(I18n.tr("companion.open"),
					b -> ConfirmLinkScreen.confirmLinkNow(this, url))
					.bounds(this.width / 2 + 96, top + i * ROW_HEIGHT - 2, 64, 20).build());
		}

		this.addRenderableWidget(Button.builder(I18n.tr("companion.back"), b -> back())
				.bounds(this.width / 2 - 60, this.height - 32, 120, 20).build());
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		graphics.fill(0, 0, this.width, this.height, 0xCC101010);
		graphics.centeredText(this.font, this.title, this.width / 2, 14, 0xFFFFFFFF);
		graphics.centeredText(this.font, I18n.tr("companion.hint"), this.width / 2, 32, 0xFFA0A0A0);

		int left = this.width / 2 - 160;
		int top = 54;
		for (int i = 0; i < entries.size(); i++) {
			Entry entry = entries.get(i);
			int y = top + i * ROW_HEIGHT;
			int nextY = i + 1 < entries.size() ? top + (i + 1) * ROW_HEIGHT : y + ROW_HEIGHT;
			graphics.text(this.font, I18n.tr(entry.nameKey()), left, y, 0xFFFFC24A);
			Component desc = I18n.tr(entry.descKey());
			String text = desc.getString();
			if (text.length() > 46) {
				text = text.substring(0, 45) + "...";
			}
			graphics.text(this.font, text, left, y + 12, 0xFFA0A0A0);
			graphics.fill(left, nextY - 4, this.width / 2 + 160, nextY - 3, 0xFF303030);
		}

		super.extractRenderState(graphics, mouseX, mouseY, partialTick);
	}

	private void back() {
		this.minecraft.gui.setScreen(parent);
	}

	@Override
	public void onClose() {
		back();
	}
}
