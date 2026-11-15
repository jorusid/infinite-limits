package com.limitless.option;

import com.limitless.config.LimitlessConfig;
import net.minecraft.client.OptionInstance;

import java.util.ArrayList;
import java.util.List;

/**
 * 原版设置界面的过滤器：默认移除非保留项，把参数所有权交给模组。
 */
public final class OptionFilter {
	private OptionFilter() {
	}

	public static OptionInstance<?>[] filter(OptionInstance<?>[] in) {
		if (in == null) {
			return null;
		}
		LimitlessConfig cfg = LimitlessConfig.get();
		if (!cfg.hideVanillaOptions) {
			return in;
		}
		List<OptionInstance<?>> kept = new ArrayList<>(in.length);
		for (OptionInstance<?> option : in) {
			if (option == null) {
				continue;
			}
			String key;
			try {
				key = OptionMirror.keyOf(option);
			} catch (Throwable t) {
				key = "";
			}
			if (cfg.keepKeys.contains(key)) {
				kept.add(option);
			}
		}
		return kept.toArray(new OptionInstance<?>[0]);
	}
}
