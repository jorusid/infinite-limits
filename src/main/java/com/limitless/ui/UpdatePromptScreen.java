package com.limitless.ui;

import com.limitless.config.LimitlessConfig;
import com.limitless.i18n.I18n;
import com.limitless.update.UpdateChecker;
import com.limitless.update.UpdateInstaller;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;

/**
 * 启动时的更新提示。
 * <p>
 * 三个出口都给玩家：
 * <ul>
 *     <li>立即下载：下载完提示重启，重启前这次会话照常玩；</li>
 *     <li>静默下载：下载完只留一句「下次启动自动加载」，不催重启；</li>
 *     <li>暂不下载：这一版不再提示，直接进游戏。</li>
 * </ul>
 * 无论选哪个都不阻塞进游戏——强制更新在断网环境下会把整个游戏废掉。
 */
public class UpdatePromptScreen extends Screen {
	private enum State {
		IDLE,
		DOWNLOADING,
		DONE_NOW,
		DONE_SILENT,
		FAILED
	}

	private static final int BUTTON_WIDTH = 360;
	private static final int BUTTON_HEIGHT = 20;
	private static final int GAP = 6;

	private State state = State.IDLE;
	private String error = "";

	public UpdatePromptScreen() {
		super(I18n.tr("update.title"));
	}

	@Override
	protected void init() {
		int centerX = this.width / 2;
		int top = this.height / 2 - 10;

		if (this.state == State.IDLE) {
			this.addRenderableWidget(Button.builder(I18n.tr("update.button.now"), b -> startDownload(false))
					.bounds(centerX - BUTTON_WIDTH / 2, top, BUTTON_WIDTH, BUTTON_HEIGHT).build());
			this.addRenderableWidget(Button.builder(I18n.tr("update.button.silent"), b -> startDownload(true))
					.bounds(centerX - BUTTON_WIDTH / 2, top + BUTTON_HEIGHT + GAP, BUTTON_WIDTH, BUTTON_HEIGHT).build());
			this.addRenderableWidget(Button.builder(I18n.tr("update.button.later"), b -> skipThisVersion())
					.bounds(centerX - BUTTON_WIDTH / 2, top + (BUTTON_HEIGHT + GAP) * 2, BUTTON_WIDTH, BUTTON_HEIGHT).build());
		} else {
			this.addRenderableWidget(Button.builder(I18n.tr("update.close"), b -> close())
					.bounds(centerX - BUTTON_WIDTH / 2, top + (BUTTON_HEIGHT + GAP) * 2, BUTTON_WIDTH, BUTTON_HEIGHT).build());
		}
	}

	private void startDownload(boolean silent) {
		this.state = State.DOWNLOADING;
		this.rebuildWidgets();

		UpdateInstaller.downloadAsync(
				UpdateChecker.latestJarUrl(),
				UpdateChecker.latestVersion(),
				() -> {
					this.state = silent ? State.DONE_SILENT : State.DONE_NOW;
					this.rebuildWidgets();
				},
				message -> {
					this.state = State.FAILED;
					this.error = message == null ? "" : message;
					this.rebuildWidgets();
				});
	}

	private void skipThisVersion() {
		LimitlessConfig cfg = LimitlessConfig.get();
		cfg.skippedVersion = UpdateChecker.latestVersion();
		cfg.save();
		close();
	}

	private void close() {
		if (this.minecraft != null) {
			this.minecraft.gui.setScreen(new TitleScreen());
		}
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		graphics.fill(0, 0, this.width, this.height, 0xD8101010);

		String latest = UpdateChecker.latestVersion();
		String current = UpdateChecker.currentVersion();

		graphics.centeredText(this.font, I18n.tr("update.title"), this.width / 2, this.height / 2 - 90, 0xFFFFFFFF);
		graphics.centeredText(this.font, I18n.tr("update.versions", current, latest),
				this.width / 2, this.height / 2 - 72, 0xFFFFC24A);

		switch (this.state) {
			case DOWNLOADING -> graphics.centeredText(this.font, I18n.tr("update.downloading"),
					this.width / 2, this.height / 2 + 94, 0xFFA0A0A0);
			case DONE_NOW -> graphics.centeredText(this.font, I18n.tr("update.done.now"),
					this.width / 2, this.height / 2 + 94, 0xFF7CFC7C);
			case DONE_SILENT -> graphics.centeredText(this.font, I18n.tr("update.done.silent"),
					this.width / 2, this.height / 2 + 94, 0xFF7CFC7C);
			case FAILED -> {
				graphics.centeredText(this.font, I18n.tr("update.failed", this.error),
						this.width / 2, this.height / 2 + 94, 0xFFFF6B6B);
				graphics.centeredText(this.font, I18n.tr("update.failed.hint"),
						this.width / 2, this.height / 2 + 108, 0xFFA0A0A0);
			}
			default -> graphics.centeredText(this.font, I18n.tr("update.hint"),
					this.width / 2, this.height / 2 + 94, 0xFFA0A0A0);
		}

		super.extractRenderState(graphics, mouseX, mouseY, partialTick);
	}

	@Override
	public void onClose() {
		skipThisVersion();
	}
}
