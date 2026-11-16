package com.limitless.update;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.limitless.LimitlessClient;
import com.limitless.config.LimitlessConfig;
import net.fabricmc.loader.api.FabricLoader;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * 启动时检查 GitHub 仓库的最新 Release。
 * <p>
 * 三条原则：
 * <ul>
 *     <li>检查失败一律当作「没有新版本」，绝不因为它连不上网就影响玩家进游戏；</li>
 *     <li>结果缓存若干小时，避免每次冷启动都打 GitHub 接口；</li>
 *     <li>只提示，不强制。拦人的设计在断网、代理、DNS 污染时会把整个游戏废掉。</li>
 * </ul>
 */
public final class UpdateChecker {
	public enum Status {
		/** 还没查过（异步线程未完成）。 */
		UNKNOWN,
		CHECKING,
		/** 已是最新，或检查失败但按「无更新」处理。 */
		UP_TO_DATE,
		/** 有新版本。 */
		OUTDATED
	}

	private static final String API_URL = "https://api.github.com/repos/jorusid/infinite-limits/releases/latest";
	private static final long CACHE_MILLIS = 6L * 60L * 60L * 1000L;

	private static volatile Status status = Status.UNKNOWN;
	private static volatile String latestVersion = "";
	private static volatile String latestJarUrl = "";
	private static volatile String latestPageUrl = "";
	private static volatile String lastError = "";
	private static volatile long checkedAt = 0L;

	private UpdateChecker() {
	}

	public static Status status() {
		return status;
	}

	public static String latestVersion() {
		return latestVersion;
	}

	public static String latestJarUrl() {
		return latestJarUrl;
	}

	public static String latestPageUrl() {
		return latestPageUrl;
	}

	public static String lastError() {
		return lastError;
	}

	public static long checkedAt() {
		return checkedAt;
	}

	public static String currentVersion() {
		return FabricLoader.getInstance().getModContainer(LimitlessClient.MOD_ID)
				.map(container -> container.getMetadata().getVersion().getFriendlyString())
				.orElse("0.0.0");
	}

	/** 是否该弹更新提示：确实有新版本，且玩家上次没点「暂不下载」这一版。 */
	public static boolean shouldPrompt() {
		if (status != Status.OUTDATED) {
			return false;
		}
		LimitlessConfig cfg = LimitlessConfig.get();
		return latestVersion.isEmpty() || !latestVersion.equals(cfg.skippedVersion);
	}

	/** 后台线程里发起检查，不阻塞游戏启动。 */
	public static void startAsync() {
		Thread thread = new Thread(UpdateChecker::runCheck, "Limitless-Update-Check");
		thread.setDaemon(true);
		thread.start();
	}

	private static void runCheck() {
		status = Status.CHECKING;
		try {
			LimitlessConfig cfg = LimitlessConfig.get();
			long now = System.currentTimeMillis();

			// 缓存命中：直接用上次的结果，不打接口
			if (!cfg.lastKnownLatest.isEmpty() && now - cfg.lastUpdateCheck < CACHE_MILLIS) {
				applyResult(cfg.lastKnownLatest, cfg.lastKnownJarUrl, cfg.lastKnownPageUrl);
				checkedAt = cfg.lastUpdateCheck;
				return;
			}

			HttpClient client = HttpClient.newBuilder()
					.connectTimeout(Duration.ofSeconds(6))
					.followRedirects(HttpClient.Redirect.NORMAL)
					.build();
			HttpRequest request = HttpRequest.newBuilder(URI.create(API_URL))
					.header("User-Agent", "Limitless/" + currentVersion())
					.header("Accept", "application/vnd.github+json")
					.timeout(Duration.ofSeconds(8))
					.GET()
					.build();
			HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
			if (response.statusCode() != 200) {
				// 403 常见于接口限流；404 见过仓库被改名。都按「无更新」处理
				LOGGER_INFO("检查返回 " + response.statusCode() + "，当作无更新处理");
				status = Status.UP_TO_DATE;
				cfg.lastUpdateCheck = now;
				cfg.save();
				return;
			}

			JsonObject root = JsonParser.parseString(response.body()).getAsJsonObject();
			String tag = root.has("tag_name") ? root.get("tag_name").getAsString() : "";
			String page = root.has("html_url") ? root.get("html_url").getAsString() : "";

			String jarUrl = "";
			if (root.has("assets") && root.get("assets").isJsonArray()) {
				JsonArray assets = root.getAsJsonArray("assets");
				for (JsonElement element : assets) {
					if (!element.isJsonObject()) {
						continue;
					}
					JsonObject asset = element.getAsJsonObject();
					String name = asset.has("name") ? asset.get("name").getAsString() : "";
					if (name.startsWith("limitless-") && name.endsWith(".jar")
							&& asset.has("browser_download_url")) {
						jarUrl = asset.get("browser_download_url").getAsString();
						break;
					}
				}
			}

			String version = normalize(tag);
			cfg.lastUpdateCheck = now;
			cfg.lastKnownLatest = version;
			cfg.lastKnownJarUrl = jarUrl;
			cfg.lastKnownPageUrl = page;
			cfg.save();
			checkedAt = now;
			applyResult(version, jarUrl, page);
		} catch (Throwable t) {
			// 断网、超时、DNS 污染、JSON 变样：全部当作无更新，绝不抛给游戏
			lastError = t.toString();
			status = Status.UP_TO_DATE;
			LOGGER_INFO("检查失败（按无更新处理）: " + t);
		}
	}

	private static void applyResult(String version, String jarUrl, String pageUrl) {
		latestVersion = version == null ? "" : version;
		latestJarUrl = jarUrl == null ? "" : jarUrl;
		latestPageUrl = pageUrl == null ? "" : pageUrl;
		if (latestVersion.isEmpty() || compare(latestVersion, currentVersion()) <= 0) {
			status = Status.UP_TO_DATE;
		} else {
			status = Status.OUTDATED;
		}
	}

	private static String normalize(String tag) {
		if (tag == null) {
			return "";
		}
		String trimmed = tag.trim();
		while (trimmed.startsWith("v") || trimmed.startsWith("V")) {
			trimmed = trimmed.substring(1);
		}
		return trimmed;
	}

	/** 逐段比较版本号，缺位补 0；非数字段按字符串比较，避免解析异常。 */
	public static int compare(String a, String b) {
		String[] left = normalize(a).split("\\.");
		String[] right = normalize(b).split("\\.");
		int max = Math.max(left.length, right.length);
		for (int i = 0; i < max; i++) {
			String l = i < left.length ? left[i] : "0";
			String r = i < right.length ? right[i] : "0";
			// 取前导数字参与比较，兼容 0.6.0-beta 这种
			long ln = leadingNumber(l);
			long rn = leadingNumber(r);
			if (ln != rn) {
				return Long.compare(ln, rn);
			}
			int text = l.compareTo(r);
			if (text != 0) {
				return text;
			}
		}
		return 0;
	}

	private static long leadingNumber(String segment) {
		int end = 0;
		while (end < segment.length() && Character.isDigit(segment.charAt(end))) {
			end++;
		}
		if (end == 0) {
			return 0L;
		}
		try {
			return Long.parseLong(segment.substring(0, end));
		} catch (NumberFormatException e) {
			return 0L;
		}
	}

	private static void LOGGER_INFO(String message) {
		LimitlessClient.LOGGER.info("[Limitless] " + message);
	}
}
