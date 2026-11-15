package com.limitless.compat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

import java.util.Locale;

/**
 * 视频设置页拦截的共享状态。
 * <p>
 * 拦截点在 {@code Gui.setScreen}，但被拦下来的页面实例不能丢：用户在模组界面里可以点
 * 「打开原始视频设置」把它原封不动地放出去。这里同时负责三件事：
 * <ol>
 *     <li>记住最近一次被接管的页面实例与来源名称；</li>
 *     <li>提供一个「放行一次」的开关，避免放行时又被自己的 mixin 拦回来（死循环）；</li>
 *     <li>把类名翻译成用户看得懂的来源描述（原版 / 钠 / 某个第三方模组）。</li>
 * </ol>
 * 放在独立的普通类里而不是 mixin 的静态字段，是为了避免 mixin 静态成员在重映射阶段
 * 与其他模组插进来的改动互相影响。
 */
public final class VideoRedirect {
	private static boolean bypass = false;
	private static Screen lastIntercepted = null;
	private static String lastLabel = "";

	private VideoRedirect() {
	}

	/** 当前是否处于「主动放行」状态。 */
	public static boolean isBypass() {
		return bypass;
	}

	/** 记录一次拦截。 */
	public static void remember(Screen screen) {
		lastIntercepted = screen;
		lastLabel = describe(screen.getClass().getName());
	}

	/** 是否有可放行的页面。 */
	public static boolean hasPending() {
		return lastIntercepted != null;
	}

	/** 被接管页面的来源描述。 */
	public static String pendingLabel() {
		return lastLabel;
	}

	/**
	 * 打开被接管的原始页面：放行一次，交给原页面自己初始化。
	 * 成功后清空记录，避免下次拿着一个已经初始化过的旧实例重复打开。
	 */
	public static void openOriginal(Minecraft minecraft) {
		Screen target = lastIntercepted;
		if (target == null || minecraft == null) {
			return;
		}
		bypass = true;
		try {
			minecraft.gui.setScreen(target);
		} finally {
			bypass = false;
			lastIntercepted = null;
			lastLabel = "";
		}
	}

	/** 把类名翻译成人话。 */
	public static String describe(String className) {
		if (className == null || className.isEmpty()) {
			return "未知页面";
		}
		String lower = className.toLowerCase(Locale.ROOT);
		if (lower.startsWith("net.minecraft.")) {
			return "原版视频设置";
		}
		if (lower.contains("sodium") || lower.contains("caffeinemc")) {
			return "Sodium（钠）的视频设置";
		}
		String simple = className.substring(className.lastIndexOf('.') + 1);
		String modId = guessModId(className);
		if (modId.isEmpty()) {
			return "其他模组视频设置（" + simple + "）";
		}
		return "其他模组视频设置（" + modId + " / " + simple + "）";
	}

	/**
	 * 从包名里猜模组 id：常见约定是 {@code xx.xx.mods.<id>.yyy}，
	 * 找不到就退回用包名倒数第二段。
	 */
	private static String guessModId(String className) {
		String[] parts = className.split("\\.");
		for (int i = 0; i < parts.length - 1; i++) {
			if (parts[i].equalsIgnoreCase("mods") && i + 1 < parts.length - 1) {
				return parts[i + 1];
			}
		}
		if (parts.length >= 3) {
			return parts[parts.length - 3];
		}
		return "";
	}
}
