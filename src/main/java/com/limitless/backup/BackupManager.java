package com.limitless.backup;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.limitless.LimitlessClient;
import com.limitless.config.LimitlessConfig;
import com.limitless.option.OptionMirror;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Options;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 设置备份：首次启动时把当前全部参数与 options.txt 存一份，之后可在界面里一键恢复。
 */
public final class BackupManager {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final String SNAPSHOT = "参数快照.json";

	private BackupManager() {
	}

	/** 备份根目录：<游戏目录>/设置备份 */
	public static Path root() {
		return FabricLoader.getInstance().getGameDir().resolve("设置备份");
	}

	/** 首次启动时创建快照，之后不再重复创建。 */
	public static void ensureFirstLaunchBackup(Options options) {
		LimitlessConfig cfg = LimitlessConfig.get();
		if (cfg.backupDone && Files.exists(root())) {
			return;
		}
		try {
			Files.createDirectories(root());
			String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
			Path folder = root().resolve("首次启动-" + stamp);
			Files.createDirectories(folder);

			Path gameDir = FabricLoader.getInstance().getGameDir();
			copyIfExists(gameDir.resolve("options.txt"), folder.resolve("options.txt"));
			copyIfExists(gameDir.resolve("optionsof.txt"), folder.resolve("optionsof.txt"));

			JsonObject json = new JsonObject();
			json.addProperty("createdAt", stamp);
			json.addProperty("reason", "首次启动自动备份");
			json.addProperty("minecraft", FabricLoader.getInstance().getModContainer("minecraft")
					.map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse("unknown"));
			JsonObject values = new JsonObject();
			for (OptionMirror.Entry entry : OptionMirror.scan(options)) {
				values.addProperty(entry.key(), String.valueOf(entry.option().get()));
			}
			json.add("options", values);

			Files.write(folder.resolve(SNAPSHOT), GSON.toJson(json).getBytes(StandardCharsets.UTF_8));
			Files.write(folder.resolve("说明.txt"), explain().getBytes(StandardCharsets.UTF_8));

			cfg.backupDone = true;
			cfg.backupFolder = folder.getFileName().toString();
			cfg.save();
			LimitlessClient.LOGGER.info("[Limitless] 已创建首次启动备份：{}", folder);
		} catch (Throwable t) {
			LimitlessClient.LOGGER.warn("[Limitless] 创建备份失败", t);
		}
	}

	private static void copyIfExists(Path from, Path to) {
		try {
			if (Files.exists(from)) {
				Files.copy(from, to, StandardCopyOption.REPLACE_EXISTING);
			}
		} catch (Throwable t) {
			LimitlessClient.LOGGER.warn("[Limitless] 复制 {} 失败", from, t);
		}
	}

	/** 全部备份目录，按名称倒序（时间戳命名，字典序即时间序）。 */
	public static List<Path> list() {
		List<Path> out = new ArrayList<>();
		try {
			if (!Files.exists(root())) {
				return out;
			}
			try (var stream = Files.list(root())) {
				stream.filter(Files::isDirectory)
						.sorted(Comparator.comparing((Path p) -> p.getFileName().toString()).reversed())
						.forEach(out::add);
			}
		} catch (Throwable t) {
			LimitlessClient.LOGGER.warn("[Limitless] 列举备份失败", t);
		}
		return out;
	}

	public static Path newest() {
		List<Path> all = list();
		return all.isEmpty() ? null : all.get(0);
	}

	/** 用最近一份备份覆盖当前参数，返回成功写入的项数（-1 表示无备份或读取失败）。 */
	public static int restore(Options options) {
		Path folder = newest();
		if (folder == null) {
			return -1;
		}
		try {
			Path file = folder.resolve(SNAPSHOT);
			if (!Files.exists(file)) {
				return -1;
			}
			String text = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
			JsonObject json = GSON.fromJson(text, JsonObject.class);
			if (json == null || !json.has("options")) {
				return -1;
			}
			JsonObject values = json.getAsJsonObject("options");

			Map<String, OptionMirror.Entry> current = new LinkedHashMap<>();
			for (OptionMirror.Entry entry : OptionMirror.scan(options)) {
				current.putIfAbsent(entry.key(), entry);
			}

			int applied = 0;
			for (Map.Entry<String, com.google.gson.JsonElement> e : values.entrySet()) {
				OptionMirror.Entry entry = current.get(e.getKey());
				if (entry == null) {
					continue;
				}
				Object parsed = OptionMirror.parse(entry.option(), e.getValue().getAsString());
				if (parsed != null) {
					OptionMirror.apply(entry.option(), parsed);
					applied++;
				}
			}
			LimitlessConfig cfg = LimitlessConfig.get();
			cfg.overrides.clear();
			cfg.save();
			LimitlessClient.LOGGER.info("[Limitless] 已从 {} 恢复 {} 项参数", folder.getFileName(), applied);
			return applied;
		} catch (Throwable t) {
			LimitlessClient.LOGGER.warn("[Limitless] 恢复备份失败", t);
			return -1;
		}
	}

	private static String explain() {
		return """
				设置备份说明
				============

				这个文件夹由「无限 Limitless」模组在首次启动时自动创建。

				文件夹里有三样东西：

				1. options.txt —— 备份那一刻的原版设置文件，和游戏目录里的同名文件格式完全一致。
				2. optionsof.txt —— 如果当时装了 OptiFine 之类会生成这个文件，也会一并备份。
				3. 参数快照.json —— 模组自己记录的全参数快照，界面里的「从备份恢复」就是读它。

				两种恢复方式：

				【方式一：游戏内，最省事】
				   进游戏 → 选项 → 无限 Limitless → 点「从备份恢复」。
				   会读取最新一份备份的参数快照，逐项写回，立即生效。

				【方式二：手工替换，最彻底】
				   先把游戏完全关掉（否则退出时游戏会把当前设置覆盖回去），
				   然后把这里的 options.txt 复制到游戏目录，覆盖同名文件，再启动游戏。

				注意：
				- 备份是首次启动时的状态，也就是「改之前」的样子。
				- 想多存几个不同阶段的备份，可以把整个文件夹复制一份改名，模组同样能识别。
				- 渲染距离、Mipmap、图形档次这类项改动后可能要重启游戏才彻底生效。
				""";
	}
}
