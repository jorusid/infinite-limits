package com.limitless.config;

import com.limitless.LimitlessClient;
import com.limitless.option.OptionMirror;
import net.minecraft.client.Options;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 启动时把玩家上次改过的数值写回 Options。
 * <p>
 * 为什么必须有这一步：模组界面里键入的值当场写进 {@code Options}，最终落到 {@code options.txt}。
 * 但 options.txt 是游戏自己的文件——玩家换存档、重装、换启动器、被别的模组重置，
 * 甚至游戏没正常退出，都可能把它冲掉。而 {@code config/limitless.json} 里的
 * {@code overrides} 是模组自己记的账，把它当权威来源回填，玩家的极限值才真正留得住。
 */
public final class OverrideApplier {
	private static boolean applied = false;

	private OverrideApplier() {
	}

	public static void applyOnce(Options options) {
		if (applied) {
			return;
		}
		applied = true;

		LimitlessConfig cfg = LimitlessConfig.get();
		recordVersionChange(cfg);

		if (cfg.overrides.isEmpty()) {
			return;
		}

		Map<String, OptionMirror.Entry> byKey = new HashMap<>();
		for (OptionMirror.Entry entry : OptionMirror.scan(options)) {
			byKey.putIfAbsent(entry.key(), entry);
		}

		int appliedCount = 0;
		List<String> stale = new ArrayList<>();
		for (Map.Entry<String, String> override : cfg.overrides.entrySet()) {
			OptionMirror.Entry entry = byKey.get(override.getKey());
			if (entry == null) {
				// 该项在旧版本里有、新版本里没了（改名或被合并），记一笔再从账本上划掉
				stale.add(override.getKey());
				continue;
			}
			Object parsed = OptionMirror.parse(entry.option(), override.getValue());
			if (parsed == null) {
				stale.add(override.getKey());
				continue;
			}
			try {
				OptionMirror.apply(entry.option(), parsed);
				appliedCount++;
			} catch (Throwable t) {
				stale.add(override.getKey());
			}
		}

		for (String key : stale) {
			cfg.overrides.remove(key);
			LimitlessClient.LOGGER.info("[Limitless] 丢弃失效的自定义项: {}", key);
		}

		if (!stale.isEmpty()) {
			cfg.save();
		}
		if (appliedCount > 0) {
			try {
				// 立刻写回 options.txt，避免这次会话中途崩溃导致值又丢
				options.save();
			} catch (Throwable t) {
				LimitlessClient.LOGGER.warn("[Limitless] 回填后保存 options.txt 失败", t);
			}
			LimitlessClient.LOGGER.info("[Limitless] 启动时回填了 {} 项自定义数值", appliedCount);
		}
	}

	/**
	 * 换过版本时，把当前配置另存一份到「设置备份」目录。
	 * 这样即使将来配置真出问题，也能从备份里把旧账本捞回来。
	 */
	private static void recordVersionChange(LimitlessConfig cfg) {
		String current = currentVersion();
		if (current.isEmpty()) {
			return;
		}
		if (current.equals(cfg.lastSeenVersion)) {
			return;
		}

		Path configFile = LimitlessConfig.file();
		if (Files.exists(configFile)) {
			try {
				Path backupRoot = net.fabricmc.loader.api.FabricLoader.getInstance()
						.getGameDir().resolve("设置备份");
				Files.createDirectories(backupRoot);
				String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
				String from = cfg.lastSeenVersion.isEmpty() ? "首次" : cfg.lastSeenVersion;
				Path target = backupRoot.resolve("配置-" + from + "-" + stamp + ".json");
				Files.copy(configFile, target, StandardCopyOption.REPLACE_EXISTING);
				LimitlessClient.LOGGER.info("[Limitless] 检测到版本变化 {} -> {}，已另存配置快照 {}",
						from, current, target.getFileName());
			} catch (Throwable t) {
				LimitlessClient.LOGGER.warn("[Limitless] 另存配置快照失败", t);
			}
		}

		cfg.lastSeenVersion = current;
		cfg.save();
	}

	private static String currentVersion() {
		return net.fabricmc.loader.api.FabricLoader.getInstance()
				.getModContainer(LimitlessClient.MOD_ID)
				.map(container -> container.getMetadata().getVersion().getFriendlyString())
				.orElse("");
	}
}
