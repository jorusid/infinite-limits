package com.limitless;

import com.limitless.config.LimitlessConfig;
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
	}
}
