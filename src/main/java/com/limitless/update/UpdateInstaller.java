package com.limitless.update;

import com.limitless.LimitlessClient;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Consumer;

/**
 * 下载与「下次启动自动换新」。
 * <p>
 * 关键约束（分平台）：<b>Windows</b> 上 JAR 一旦被加载器读进内存就会加独占锁——在游戏进程里
 * 既改不了它的名，也覆盖不了它的内容（0.6.0/0.6.1 就是栽在这里：关闭钩子里
 * {@code Files.move} 必然抛 {@code AccessDeniedException}，异常被吞成一行日志，所以
 * "下载成功、换版静默失效"）。<b>macOS / Linux</b> 是 POSIX 语义，允许重命名甚至删除正被打开的文件，
 * 进程内直接换即可。
 * <p>
 * Windows 走两步：
 * <ol>
 *     <li>新版先下到 {@code mods/.limitless-update/}（Fabric 只扫 mods 根目录，子目录不当模组）；</li>
 *     <li>退出时拉起一个 <b>脱离游戏进程的 cmd 助手</b>，它先轮询等游戏 PID 消失（锁随之释放），
 *     再做「旧 JAR 改名 .disabled → 新 JAR 复制进 mods → 删暂存」。助手是独立进程，
 *     游戏退出不影响它；即使它没跑成，下次启动会检测到暂存 JAR 还在，自动重新挂一次。</li>
 * </ol>
 * 任何一步失败都只是「这次没换成」，不会让游戏起不来。
 */
public final class UpdateInstaller {
	private static final String STAGING = ".limitless-update";
	private static final String LOG_NAME = "apply.log";

	private static boolean armed = false;
	private static boolean helperSpawned = false;

	private UpdateInstaller() {
	}

	public static Path modsDir() {
		return FabricLoader.getInstance().getGameDir().resolve("mods");
	}

	public static Path stagingDir() {
		return modsDir().resolve(STAGING);
	}

	/**
	 * 启动时：先读上次换版助手留下的日志（成功/失败一目了然），再清理 {@code *.jar.disabled}；
	 * 如果暂存目录里还有没换成的 JAR，就重新挂上退出钩子，本次退出再试一次。
	 */
	public static void cleanupAfterApply() {
		Path log = stagingDir().resolve(LOG_NAME);
		if (Files.exists(log)) {
			try {
				String text = new String(Files.readAllBytes(log), StandardCharsets.UTF_8).trim();
				LimitlessClient.LOGGER.info("[Limitless] 上次换版助手返回: {}",
						text.isEmpty() ? "(空)" : text.replace("\r", "").replace("\n", " | "));
			} catch (Throwable ignored) {
				// 日志读不出来不影响任何事
			}
			try {
				Files.deleteIfExists(log);
			} catch (IOException ignored) {
			}
		}

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

		if (hasStagedUpdate()) {
			LimitlessClient.LOGGER.warn("[Limitless] 暂存目录里还有没换成的版本，现在重新挂上换版助手");
			applyNowOrSoon(currentJar(), newestStagedJar());
		}
	}

	/**
	 * 立刻安排换版，<b>不依赖 JVM 关闭钩子</b>。
	 * <p>
	 * 0.6.10 及以前是在关闭钩子里拉起助手，结果撞上 FastQuit 这类「退出时直接终止进程、
	 * 不跑关闭钩子」的优化模组，钩子根本没执行，日志里连「已挂上换版助手」都没有，
	 * 于是出现「下载成功但版本没变」。现在改成下载完就拉助手、启动时发现残留也立刻拉：
	 * 助手自己轮询等游戏 PID 消失，跟游戏怎么退出无关。
	 */
	private static void applyNowOrSoon(Path current, Path staged) {
		if (current == null || staged == null) {
			return;
		}
		// 暂存件必须比当前版本新，否则（比如玩家手动装了更新版、旧暂存件还留着）不能拿它顶掉现版本
		String stagedVersion = versionOf(staged);
		String currentVersion = UpdateChecker.currentVersion();
		if (!stagedVersion.isEmpty() && UpdateChecker.compare(stagedVersion, currentVersion) <= 0) {
			try {
				Files.deleteIfExists(staged);
				LimitlessClient.LOGGER.info("[Limitless] 丢弃过期暂存件 {}（当前已是 {}）",
						staged.getFileName(), currentVersion);
			} catch (IOException ignored) {
			}
			return;
		}
		try {
			if (isWindows()) {
				spawnHelper(current, staged);
				return;
			}
			applyDirect(current, staged);
		} catch (Throwable t) {
			LimitlessClient.LOGGER.warn("[Limitless] 安排换版失败（下次启动再试）", t);
		}
	}

	/** 从 limitless-x.y.z.jar 里取出 x.y.z；取不到返回空串。 */
	private static String versionOf(Path jar) {
		String name = jar.getFileName().toString();
		if (name.startsWith("limitless-") && name.endsWith(".jar")) {
			return name.substring("limitless-".length(), name.length() - ".jar".length());
		}
		return "";
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

				HttpClient client = UpdateHttp.newClient(java.time.Duration.ofSeconds(10));
				HttpRequest request = HttpRequest.newBuilder(URI.create(url))
						.header("User-Agent", "Limitless-Updater")
						.timeout(java.time.Duration.ofSeconds(90))
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
				// 立刻安排换版：助手会自己等游戏退出，不依赖关闭钩子
				applyNowOrSoon(currentJar(), target);
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

	/** 注册退出钩子。多次调用只注册一次。 */
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
			// Windows：JAR 被加载器独占，进程内改不了名，只能交给脱离进程的 cmd 助手
			if (isWindows() && spawnHelper(current, staged)) {
				return;
			}
			// macOS / Linux：POSIX 允许重命名甚至删除「正被打开」的文件，直接换就行
			applyDirect(current, staged);
		} catch (Throwable t) {
			LimitlessClient.LOGGER.warn("[Limitless] 换版准备失败（不影响下次启动）", t);
		}
	}

	private static boolean isWindows() {
		String os = System.getProperty("os.name", "");
		return os.toLowerCase(java.util.Locale.ROOT).contains("win");
	}

	/**
	 * 直接在本进程里换：先改名再复制，并核对新 JAR 的字节数，任何一步不对就回滚，
	 * 保证不会留下「旧版已禁用、新版又没到位」的死局。
	 * <p>
	 * macOS/Linux 上这是主路径（POSIX 允许重命名被打开的文件）；Windows 上只在
	 * cmd 助手拉不起来时才走到这里，通常改名就会失败，失败即放弃并由下次启动重试。
	 */
	private static void applyDirect(Path current, Path staged) {
		Path disabled = current.resolveSibling(current.getFileName().toString() + ".disabled");
		Path destination = modsDir().resolve(staged.getFileName());
		try {
			Files.move(current, disabled, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
		} catch (Throwable t) {
			LimitlessClient.LOGGER.warn("[Limitless] 旧 JAR 改名失败（文件被占用，交给下次启动重试）: {}", t.toString());
			return;
		}
		try {
			long expected = Files.size(staged);
			Files.copy(staged, destination, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
			long actual = Files.size(destination);
			if (expected != actual) {
				throw new IOException("新 JAR 字节数不符（期望 " + expected + "，实际 " + actual + "）");
			}
			Files.deleteIfExists(staged);
			LimitlessClient.LOGGER.info("[Limitless] 已换版，下次启动加载 {}", destination.getFileName());
		} catch (Throwable t) {
			try {
				Files.deleteIfExists(destination);
			} catch (Throwable ignored) {
			}
			try {
				Files.move(disabled, current, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
			} catch (Throwable ignored) {
			}
			LimitlessClient.LOGGER.warn("[Limitless] 换版失败，已回滚，下次启动重试", t);
		}
	}

	/**
	 * 拉起脱离游戏进程的 cmd 助手。
	 * <p>
	 * 助手脚本里 <b>只用相对路径和 ASCII 文件名</b>：工作目录设成 mods，所以脚本正文里
	 * 一个中文或绝对路径都不出现，彻底绕开批处理文件的编码坑。
	 */
	private static synchronized boolean spawnHelper(Path current, Path staged) {
		if (helperSpawned) {
			return true;
		}
		try {
			String oldName = current.getFileName().toString();
			String newName = staged.getFileName().toString();
			String pid = String.valueOf(ProcessHandle.current().pid());

			Path bat = Path.of(System.getProperty("java.io.tmpdir"))
					.resolve("limitless-apply-" + System.currentTimeMillis() + ".bat");

			String log = STAGING + "\\" + LOG_NAME;
			String stagedRel = STAGING + "\\" + newName;

			String script = String.join("\r\n",
					"@echo off",
					"setlocal enabledelayedexpansion",
					"set n=0",
					":wait",
					"tasklist /FI \"PID eq " + pid + "\" 2>nul | find \"" + pid + "\" >nul",
					"if errorlevel 1 goto apply",
					"set /a n+=1",
					"if !n! GEQ 5400 goto timeout",
					"ping -n 2 127.0.0.1 >nul",
					"goto wait",
					":apply",
					"set t=0",
					":retry",
					"move /Y \"" + oldName + "\" \"" + oldName + ".disabled\" > \"" + log + "\" 2>&1",
					"if not errorlevel 1 goto copystep",
					"set /a t+=1",
					"if !t! GEQ 20 goto failmove",
					"ping -n 3 127.0.0.1 >nul",
					"goto retry",
					":copystep",
					"copy /Y \"" + stagedRel + "\" \"" + newName + "\" >> \"" + log + "\" 2>&1",
					"if errorlevel 1 goto failcopy",
					"del /F /Q \"" + stagedRel + "\" >> \"" + log + "\" 2>&1",
					"echo OK >> \"" + log + "\"",
					"goto end",
					":failmove",
					"echo MOVE-FAILED >> \"" + log + "\"",
					"goto end",
					":failcopy",
					"echo COPY-FAILED >> \"" + log + "\"",
					"goto end",
					":timeout",
					"echo TIMEOUT >> \"" + log + "\"",
					":end",
					"del /F /Q \"%~f0\" >nul 2>&1",
					"");

			Files.write(bat, script.getBytes(StandardCharsets.US_ASCII));
			ProcessBuilder pb = new ProcessBuilder("cmd.exe", "/c", bat.toString());
			pb.directory(modsDir().toFile());
			pb.redirectOutput(ProcessBuilder.Redirect.DISCARD);
			pb.redirectErrorStream(true);
			pb.start();
			helperSpawned = true;
			LimitlessClient.LOGGER.info("[Limitless] 已拉起换版助手（PID {}），游戏退出后自动替换 JAR", pid);
			return true;
		} catch (Throwable t) {
			LimitlessClient.LOGGER.warn("[Limitless] 换版助手启动失败", t);
			return false;
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
}
