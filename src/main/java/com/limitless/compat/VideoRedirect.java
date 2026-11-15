package com.limitless.compat;

import com.limitless.i18n.I18n;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 视频设置页拦截的共享状态。
 * <p>
 * 拦截点在 {@code Gui.setScreen}，被拦下来的页面实例不丢：模组界面里点「视频设置」进入
 * 一个入口列表，里面逐个列出被接管过的页面，点哪一个就把哪一个原样放出去。
 * 这里同时负责三件事：
 * <ol>
 *     <li>按来源类名收集被接管的页面（同一来源只保留最新实例）；</li>
 *     <li>提供一个「放行一次」的开关，避免放行时又被自己的 mixin 拦回来（死循环）；</li>
 *     <li>把类名翻译成用户看得懂的来源描述（原版 / 钠 / 某个第三方模组）。</li>
 * </ol>
 * 来源描述存的是翻译键而不是成品字符串，这样玩家中途切换语言时列表会跟着变。
 * 放在独立的普通类里而不是 mixin 的静态字段，是为了避免 mixin 静态成员在重映射阶段
 * 与其他模组插进来的改动互相影响。
 */
public final class VideoRedirect {
	private static final int MAX_ENTRIES = 8;

	/** 一条被接管的记录：翻译键 + 参数 + 页面实例。 */
	public record Captured(String key, Object[] args, Screen screen) {
		public Component label() {
			return I18n.tr(key, args);
		}
	}

	private static boolean bypass = false;
	private static final List<Captured> captured = new ArrayList<>();

	private VideoRedirect() {
	}

	/** 当前是否处于「主动放行」状态。 */
	public static boolean isBypass() {
		return bypass;
	}

	/** 记录一次拦截：同一来源替换旧实例，不同来源追加。 */
	public static void remember(Screen screen) {
		if (screen == null) {
			return;
		}
		String className = screen.getClass().getName();
		for (int i = 0; i < captured.size(); i++) {
			Captured existing = captured.get(i);
			if (existing.screen().getClass() == screen.getClass()) {
				captured.set(i, build(className, screen));
				return;
			}
		}
		if (captured.size() >= MAX_ENTRIES) {
			captured.remove(0);
		}
		captured.add(build(className, screen));
	}

	private static Captured build(String className, Screen screen) {
		String lower = className.toLowerCase(Locale.ROOT);
		if (lower.startsWith("net.minecraft.")) {
			return new Captured("source.vanilla", new Object[0], screen);
		}
		if (lower.contains("sodium") || lower.contains("caffeinemc")) {
			return new Captured("source.sodium", new Object[0], screen);
		}
		String simple = className.substring(className.lastIndexOf('.') + 1);
		String modId = guessModId(className);
		if (modId.isEmpty()) {
			return new Captured("source.mod", new Object[]{simple}, screen);
		}
		return new Captured("source.modWithId", new Object[]{modId, simple}, screen);
	}

	/** 当前收集到的页面（副本，调用方改动不影响内部状态）。 */
	public static List<Captured> list() {
		return new ArrayList<>(captured);
	}

	public static boolean isEmpty() {
		return captured.isEmpty();
	}

	/**
	 * 打开指定的被接管页面：放行一次，交给原页面自己初始化。
	 * 成功后把它从列表里摘掉，避免拿着一个初始化过的旧实例反复打开。
	 */
	public static void openOriginal(Minecraft minecraft, Screen target) {
		if (target == null || minecraft == null) {
			return;
		}
		bypass = true;
		try {
			minecraft.gui.setScreen(target);
		} finally {
			bypass = false;
			captured.removeIf(entry -> entry.screen() == target);
		}
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
