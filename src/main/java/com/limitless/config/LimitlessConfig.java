package com.limitless.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.limitless.LimitlessClient;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 模组的唯一配置来源。原版设置的数值最终由这里驱动。
 * <p>
 * 抗损坏设计（这是「更新完模组配置就重置」那个 bug 的根因所在）：
 * <ol>
 *     <li>写入走 {@code .tmp → 原子替换}，游戏被杀或断电时不会留下半个 JSON；</li>
 *     <li>每次覆盖前把上一版留成 {@code limitless.json.bak}；</li>
 *     <li>主文件解析失败时先读 {@code .bak}，都失败才用默认值，并把坏文件改名成
 *     {@code limitless.json.corrupt-<时间戳>} 保留取证，不再静默覆盖。</li>
 * </ol>
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

	/**
	 * 玩家自己改过的数值（翻译键 -> 文本值）。
	 * 启动时由 {@link OverrideApplier} 写回 Options，所以即使 options.txt 被重置/覆盖，
	 * 这些值也能凭配置恢复。
	 */
	public Map<String, String> overrides = new LinkedHashMap<>();

	/** 是否已经做过首次启动备份。 */
	public boolean backupDone = false;

	/** 最近一次的备份目录名。 */
	public String backupFolder = "";

	/** 玩家点了「暂不下载」的那个版本号，这一版不再提示。 */
	public String skippedVersion = "";

	/** 上次检查更新的时间戳（毫秒），用于缓存，避免每次启动都打 GitHub 接口。 */
	public long lastUpdateCheck = 0L;

	/** 上次查到的版本号 / JAR 地址 / 发布页地址（缓存用）。 */
	public String lastKnownLatest = "";
	public String lastKnownJarUrl = "";
	public String lastKnownPageUrl = "";

	/** 上次启动时记录的模组版本，用来识别「刚换过版本」，换版时把配置另存一份到备份目录。 */
	public String lastSeenVersion = "";

	public static LimitlessConfig get() {
		if (instance == null) {
			instance = load();
		}
		return instance;
	}

	public static Path file() {
		return FabricLoader.getInstance().getConfigDir().resolve("limitless.json");
	}

	private static Path backupFile() {
		return FabricLoader.getInstance().getConfigDir().resolve("limitless.json.bak");
	}

	private static LimitlessConfig load() {
		Path primary = file();
		LimitlessConfig fromPrimary = readFrom(primary, false);
		if (fromPrimary != null) {
			return fromPrimary;
		}

		// 主文件读不出来：先试备份，救回玩家改过的值
		Path backup = backupFile();
		LimitlessConfig fromBackup = readFrom(backup, true);
		if (fromBackup != null && Files.exists(primary)) {
			quarantine(primary);
			LimitlessClient.LOGGER.warn("[Limitless] 主配置损坏，已从 limitless.json.bak 恢复");
			return fromBackup;
		}
		if (fromBackup != null) {
			LimitlessClient.LOGGER.warn("[Limitless] 主配置缺失，已从 limitless.json.bak 恢复");
			return fromBackup;
		}
		if (Files.exists(primary)) {
			quarantine(primary);
		}
		return new LimitlessConfig();
	}

	/** 读一个配置文件；不存在或解析失败返回 null。 */
	private static LimitlessConfig readFrom(Path path, boolean quiet) {
		if (!Files.exists(path)) {
			return null;
		}
		try {
			String text = new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
			LimitlessConfig loaded = GSON.fromJson(text, LimitlessConfig.class);
			if (loaded == null) {
				return null;
			}
			if (loaded.keepKeys == null) loaded.keepKeys = new ArrayList<>();
			if (loaded.overrides == null) loaded.overrides = new LinkedHashMap<>();
			// 旧配置文件里没有新字段，Gson 会留 null，字符串一律兜成空串
			if (loaded.backupFolder == null) loaded.backupFolder = "";
			if (loaded.skippedVersion == null) loaded.skippedVersion = "";
			if (loaded.lastKnownLatest == null) loaded.lastKnownLatest = "";
			if (loaded.lastKnownJarUrl == null) loaded.lastKnownJarUrl = "";
			if (loaded.lastKnownPageUrl == null) loaded.lastKnownPageUrl = "";
			if (loaded.lastSeenVersion == null) loaded.lastSeenVersion = "";
			return loaded;
		} catch (Exception e) {
			if (!quiet) {
				LimitlessClient.LOGGER.warn("[Limitless] 配置 {} 解析失败", path.getFileName(), e);
			}
			return null;
		}
	}

	/** 把坏掉的配置文件改个名留着，不直接覆盖，方便以后排查。 */
	private static void quarantine(Path path) {
		try {
			String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
			Path target = path.resolveSibling("limitless.json.corrupt-" + stamp);
			Files.move(path, target, StandardCopyOption.REPLACE_EXISTING);
			LimitlessClient.LOGGER.warn("[Limitless] 损坏的配置已保留为 {}", target.getFileName());
		} catch (IOException e) {
			LimitlessClient.LOGGER.warn("[Limitless] 隔离损坏配置失败", e);
		}
	}

	/**
	 * 原子写入：先写 .tmp，再替换主文件；替换前把旧内容留成 .bak。
	 */
	public void save() {
		Path path = file();
		Path temp = path.resolveSibling("limitless.json.tmp");
		try {
			Files.createDirectories(path.getParent());
			byte[] bytes = GSON.toJson(this).getBytes(StandardCharsets.UTF_8);
			Files.write(temp, bytes);
			if (Files.exists(path)) {
				try {
					Files.copy(path, backupFile(), StandardCopyOption.REPLACE_EXISTING);
				} catch (IOException ignored) {
					// 备份失败不影响本次写入
				}
			}
			try {
				Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
			} catch (IOException atomicFailed) {
				Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING);
			}
		} catch (IOException e) {
			LimitlessClient.LOGGER.warn("[Limitless] 配置写入失败", e);
			try {
				Files.deleteIfExists(temp);
			} catch (IOException ignored) {
			}
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
