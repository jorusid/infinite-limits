package com.limitless.i18n;

import net.minecraft.network.chat.Component;

/**
 * 多语言入口。
 * <p>
 * 所有界面文案都走 {@code Component.translatable}，由 Minecraft 自己按客户端语言解析，
 * 语言文件放在 {@code assets/limitless/lang/*.json}。这样玩家在「选项 → 语言」里切换语言时，
 * 模组界面会跟着变，不需要我们监听任何事件。
 * <p>
 * 缺语言文件时 Minecraft 会回退到 {@code en_us}，所以英文文件必须键齐全。
 */
public final class I18n {
	private static final String PREFIX = "limitless.";

	private I18n() {
	}

	/** 取可翻译文本（用于按钮、标题等需要 Component 的地方）。 */
	public static Component tr(String key, Object... args) {
		return Component.translatable(PREFIX + key, args);
	}

	/** 取已按当前语言解析的字符串（用于写文件、拼日志等需要 String 的地方）。 */
	public static String trs(String key, Object... args) {
		return tr(key, args).getString();
	}

	/** 分类名：category.video / category.sound / ... */
	public static Component category(String id) {
		return tr("category." + id);
	}
}
