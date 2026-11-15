package com.limitless.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 模组的唯一配置来源。原版设置的数值最终由这里驱动。
 */
public final class LimitlessConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static LimitlessConfig instance;

	/** 极限模式：关闭后所有数值回到原版钳制范围。 */
	public boolean extremeMode = true;

	/** 是否从原版设置界面移除全部参数行，交给本模组管理。 */
	public boolean hideVanillaOptions = true;

	/** 允许保留在原版界面显示的条目（按翻译键，例如 options.graphics）。 */
	public List<String> keepKeys = new ArrayList<>();

	/** 需要在上一次关闭游戏后重新写回的数值（翻译键 -> 文本值）。 */
	public Map<String, String> overrides = new LinkedHashMap<>();

	/** 是否已经做过首次启动备份。 */
	public boolean backupDone = false;

	/** 最近一次的备份目录名。 */
	public String backupFolder = "";

	public static LimitlessConfig get() {
		if (instance == null) {
			instance = load();
		}
		return instance;
	}

	private static Path file() {
		return FabricLoader.getInstance().getConfigDir().resolve("limitless.json");
	}

	private static LimitlessConfig load() {
		Path path = file();
		if (Files.exists(path)) {
			try {
				String text = new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
				LimitlessConfig loaded = GSON.fromJson(text, LimitlessConfig.class);
				if (loaded != null) {
					if (loaded.keepKeys == null) loaded.keepKeys = new ArrayList<>();
					if (loaded.overrides == null) loaded.overrides = new LinkedHashMap<>();
					return loaded;
				}
			} catch (Exception e) {
				com.limitless.LimitlessClient.LOGGER.warn("[Limitless] 配置读取失败，使用默认值", e);
			}
		}
		return new LimitlessConfig();
	}

	public void save() {
		try {
			Path path = file();
			Files.createDirectories(path.getParent());
			Files.write(path, GSON.toJson(this).getBytes(StandardCharsets.UTF_8));
		} catch (IOException e) {
			com.limitless.LimitlessClient.LOGGER.warn("[Limitless] 配置写入失败", e);
		}
	}

	public void setOverride(String key, String value) {
		if (value == null) {
			overrides.remove(key);
		} else {
			overrides.put(key, value);
		}
	}
}
