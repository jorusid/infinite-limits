package com.limitless.option;

import com.limitless.i18n.I18n;
import net.minecraft.network.chat.Component;

import java.util.Locale;

/** 参数分类：按字段名与翻译键的语义分组。 */
public enum Category {
	VIDEO("video"),
	SOUND("sound"),
	CONTROL("control"),
	INTERFACE("interface"),
	OTHER("other");

	private final String id;

	Category(String id) {
		this.id = id;
	}

	public String id() {
		return id;
	}

	/** 当前语言下的分类名（category.video 之类）。 */
	public Component label() {
		return I18n.category(id);
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
