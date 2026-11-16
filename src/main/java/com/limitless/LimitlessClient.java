package com.limitless;

import com.limitless.config.LimitlessConfig;
import com.limitless.update.UpdateChecker;
import com.limitless.update.UpdateInstaller;
import net.fabricmc.api.ClientModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LimitlessClient implements ClientModInitializer {
	public static final String MOD_ID = "limitless";
	public static final Logger LOGGER = LoggerFactory.getLogger("Limitless");

	@Override
	public void onInitializeClient() {
		LimitlessConfig cfg = LimitlessConfig.get();
		LOGGER.info("[Limitless] ready (extremeMode={}, hideVanillaOptions={})",
				cfg.extremeMode, cfg.hideVanillaOptions);

		// 收尾上次退出时换版留下的 .disabled 旧 JAR
		UpdateInstaller.cleanupAfterApply();
		// 异步查 GitHub 有没有新版；连不上就当作没更新，绝不阻塞进游戏
		UpdateChecker.startAsync();
	}
}
