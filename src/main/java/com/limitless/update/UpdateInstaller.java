package com.limitless.update;

import com.limitless.LimitlessClient;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Comparator;
import java.util.function.Consumer;

/**
 * 下载与「下次启动自动换新」。
 * <p>
 * 关键约束：JAR 一旦被加载器读进内存，就不能再热替换——而且这版 JAR 的资源（语言文件、图标）
 * 在游戏运行期间还会被随手读，所以运行中动它的文件名是不安全的。
 * <p>
 * 因此走「暂存 + 退出时换」：新 JAR 先下到 {@code mods/.limitless-update/}（Fabric 只扫 mods 根目录，
 * 子目录不会被当成模组加载，所以放这儿不会造成两个同 ID 模组冲突）；然后在 JVM 关闭钩子里把旧 JAR
 * 改名成 {@code .disabled}、把暂存的 JAR 挪进 mods。下次启动加载的就是新版本。
 * <p>
 * 任何一步失败都只是「这次没换成」，不会让游戏起不来。
 */
public final class UpdateInstaller {
	private static boolean armed = false;

	private UpdateInstaller() {
	}

	public static Path modsDir() {
		return FabricLoader.getInstance().getGameDir().resolve("mods");
	}

	public static Path stagingDir() {
		return modsDir().resolve(".limitless-update");
	}

	/** 启动时清理上次换版留下的 {@code *.jar.disabled}，顺手把暂存目录里没用的东西删掉。 */
	public static void cleanupAfterApply() {
		try (DirectoryStream<Path> stream = Files.newDirectoryStream(modsDir(), "limitless-*.jar.disabled")) {
			for (Path path : stream) {
				try {
					Files.deleteIfExists(path);
					LimitlessClient.LOGGER.info("[Limitless] 已清理旧版 JAR: {}", path.getFileName());
				} catch (IOException ignored) {
					// 删不掉就算了，留着不影响加载（.disabled 不被识别为模组）
				}
			}
		} catch (IOException ignored) {
			// mods 目录读不了：不处理
		}
	}

	/**
	 * 后台下载最新 JAR 到暂存目录，完成后回调（回调已切回客户端线程）。
	 */
	public static void downloadAsync(String url, String version, Runnable onSuccess, Consumer<String> onFailure) {
		Thread thread = new Thread(() -> {
			Path temp = null;
			try {
				if (url == null || url.isEmpty()) {
					throw new IOException("没有可下载的 JAR 地址");
				}
				Files.createDirectories(stagingDir());
				Path target = stagingDir().resolve("limitless-" + version + ".jar");
				temp = stagingDir().resolve("limitless-" + version + ".jar.tmp");

				HttpClient client = HttpClient.newBuilder()
						.connectTimeout(Duration.ofSeconds(10))
						.followRedirects(HttpClient.Redirect.NORMAL)
						.build();
				HttpRequest request = HttpRequest.newBuilder(URI.create(url))
						.header("User-Agent", "Limitless-Updater")
						.timeout(Duration.ofSeconds(90))
						.GET()
						.build();
				HttpResponse<Path> response = client.send(request, HttpResponse.BodyHandlers.ofFile(temp));
				if (response.statusCode() != 200) {
					throw new IOException("HTTP " + response.statusCode());
				}
				long size = Files.size(temp);
				if (size < 4096) {
					throw new IOException("下载内容异常（" + size + " 字节）");
				}
				Files.move(temp, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
				LimitlessClient.LOGGER.info("[Limitless] 新版已暂存: {}（{} 字节）", target.getFileName(), size);
				arm();
				clientExecute(onSuccess);
			} catch (Throwable t) {
				try {
					if (temp != null) {
						Files.deleteIfExists(temp);
					}
				} catch (IOException ignored) {
				}
				LimitlessClient.LOGGER.warn("[Limitless] 更新下载失败", t);
				final String message = t.toString();
				clientExecute(() -> onFailure.accept(message));
			}
		}, "Limitless-Update-Download");
		thread.setDaemon(true);
		thread.start();
	}

	private static void clientExecute(Runnable task) {
		try {
			Minecraft.getInstance().execute(task);
		} catch (Throwable ignored) {
			task.run();
		}
	}

	/** 注册退出钩子：关游戏时把版本换掉。多次调用只注册一次。 */
	public static synchronized void arm() {
		if (armed) {
			return;
		}
		armed = true;
		try {
			Runtime.getRuntime().addShutdownHook(new Thread(UpdateInstaller::applyOnExit, "Limitless-Update-Apply"));
		} catch (Throwable ignored) {
		}
	}

	private static void applyOnExit() {
		try {
			Path current = currentJar();
			Path staged = newestStagedJar();
			if (current == null || staged == null) {
				return;
			}
			Path disabled = current.resolveSibling(current.getFileName().toString() + ".disabled");
			Files.move(current, disabled, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
			Path destination = modsDir().resolve(staged.getFileName());
			Files.copy(staged, destination, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
			Files.deleteIfExists(staged);
			try (DirectoryStream<Path> leftovers = Files.newDirectoryStream(stagingDir(), "*.tmp")) {
				for (Path leftover : leftovers) {
					Files.deleteIfExists(leftover);
				}
			}
			LimitlessClient.LOGGER.info("[Limitless] 已换版，下次启动加载 {}", destination.getFileName());
		} catch (Throwable t) {
			LimitlessClient.LOGGER.warn("[Limitless] 换版失败（不影响下次启动）", t);
		}
	}

	/** 当前加载的 JAR 文件位置；开发环境下是目录，返回 null 即不换版。 */
	private static Path currentJar() {
		return FabricLoader.getInstance().getModContainer(LimitlessClient.MOD_ID)
				.flatMap(container -> container.getRootPaths().stream()
						.filter(path -> path.toString().endsWith(".jar"))
						.findFirst())
				.orElse(null);
	}

	private static Path newestStagedJar() {
		try (DirectoryStream<Path> stream = Files.newDirectoryStream(stagingDir(), "limitless-*.jar")) {
			Path best = null;
			for (Path path : stream) {
				if (best == null) {
					best = path;
					continue;
				}
				String a = path.getFileName().toString();
				String b = best.getFileName().toString();
				if (a.compareTo(b) > 0) {
					best = path;
				}
			}
			return best;
		} catch (IOException e) {
			return null;
		}
	}

	/** 暂存目录里是否已经有下好的新版（调试与提示用）。 */
	public static boolean hasStagedUpdate() {
		Path staged = newestStagedJar();
		return staged != null && Files.exists(staged);
	}

	static {
		// 保证关闭时尽量不残留临时文件；不主动删已换好的 JAR
		Runtime.getRuntime().addShutdownHook(new Thread(() -> {
			try {
				if (!Files.exists(stagingDir())) {
					return;
				}
				try (var stream = Files.list(stagingDir())) {
					stream.sorted(Comparator.reverseOrder()).forEach(path -> {
						try {
							if (path.getFileName().toString().endsWith(".tmp")) {
								Files.deleteIfExists(path);
							}
						} catch (IOException ignored) {
						}
					});
				}
			} catch (Throwable ignored) {
			}
		}, "Limitless-Update-Cleanup"));
	}
}
