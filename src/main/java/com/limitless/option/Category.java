package com.limitless.option;

import java.util.Locale;

/** 参数分类：按字段名与翻译键的语义分组。 */
public enum Category {
	VIDEO("画面"),
	SOUND("声音"),
	CONTROL("操作"),
	INTERFACE("界面"),
	OTHER("其他");

	public final String displayName;

	Category(String displayName) {
		this.displayName = displayName;
	}

	public static Category of(String id, String key) {
		String s = (id + " " + key).toLowerCase(Locale.ROOT);
		if (s.contains("volume") || s.contains("sound") || s.contains("subtitle")
				|| s.contains("music") || s.contains("audi") || s.contains("directionalaudio")) {
			return SOUND;
		}
		if (s.contains("render") || s.contains("fov") || s.contains("gamma") || s.contains("particle")
				|| s.contains("graphic") || s.contains("cloud") || s.contains("shadow") || s.contains("mipmap")
				|| s.contains("framerate") || s.contains("maxfps") || s.contains("anisotropy")
				|| s.contains("biomeblend") || s.contains("entitydistance") || s.contains("glint")
				|| s.contains("fullscreen") || s.contains("resolution") || s.contains("preferredgraphicsapi")
				|| s.contains("vulkan") || s.contains("opengl") || s.contains("screen") || s.contains("darkness")
				|| s.contains("brightness") || s.contains("vsync") || s.contains("viewbobbing")) {
			return VIDEO;
		}
		if (s.contains("mouse") || s.contains("touch") || s.contains("rawinput") || s.contains("sensitivity")
				|| s.contains("scroll") || s.contains("sprint") || s.contains("sneak")
				|| s.contains("mainhand") || s.contains("autojump")) {
			return CONTROL;
		}
		if (s.contains("gui") || s.contains("chat") || s.contains("language") || s.contains("debug")
				|| s.contains("font") || s.contains("hud") || s.contains("background") || s.contains("scale")
				|| s.contains("notification") || s.contains("toast") || s.contains("menu")) {
			return INTERFACE;
		}
		return OTHER;
	}
}
