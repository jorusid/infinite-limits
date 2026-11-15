package com.limitless.option;

import com.limitless.LimitlessClient;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.Options;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.sounds.SoundSource;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 通过反射枚举 Options 里的全部 OptionInstance。
 * 26.2 的 Options 字段是 private final，但模组运行在 classpath 上，反射可读写。
 */
public final class OptionMirror {
	private OptionMirror() {
	}

	public record Entry(String id, String key, Component caption, OptionInstance<?> option) {
		public String label() {
			return caption.getString();
		}
	}

	/** 反射读 private 字段（失败返回 null，不抛出）。 */
	public static Object field(Object target, String name) {
		try {
			Field f = target.getClass().getDeclaredField(name);
			f.setAccessible(true);
			return f.get(target);
		} catch (Throwable t) {
			return null;
		}
	}

	private static Object staticField(Class<?> owner, Object target, String name) {
		try {
			Field f = owner.getDeclaredField(name);
			f.setAccessible(true);
			return f.get(target);
		} catch (Throwable t) {
			return null;
		}
	}

	public static String keyOf(OptionInstance<?> option) {
		Component caption = (Component) field(option, "caption");
		if (caption == null) {
			return "unknown";
		}
		if (caption.getContents() instanceof TranslatableContents tc) {
			return tc.getKey();
		}
		return caption.getString();
	}

	/** 扫描 Options 上所有 OptionInstance 字段 + 音量分类。 */
	public static List<Entry> scan(Options options) {
		List<Entry> list = new ArrayList<>();
		for (Field f : Options.class.getDeclaredFields()) {
			if (!OptionInstance.class.isAssignableFrom(f.getType())) {
				continue;
			}
			try {
				f.setAccessible(true);
				Object raw = f.get(options);
				if (raw instanceof OptionInstance<?> option) {
					list.add(new Entry(f.getName(), keyOf(option), captionOf(option), option));
				}
			} catch (Throwable t) {
				LimitlessClient.LOGGER.debug("[Limitless] 读取字段 {} 失败", f.getName(), t);
			}
		}
		Object volumes = field(options, "soundSourceVolumes");
		if (volumes instanceof Map<?, ?> map) {
			for (Map.Entry<?, ?> e : map.entrySet()) {
				if (e.getKey() instanceof SoundSource source && e.getValue() instanceof OptionInstance<?> option) {
					list.add(new Entry("volume." + source.getName(), keyOf(option), captionOf(option), option));
				}
			}
		}
		return list;
	}

	private static Component captionOf(OptionInstance<?> option) {
		Object c = field(option, "caption");
		return c instanceof Component comp ? comp : Component.literal("?");
	}

	@SuppressWarnings("unchecked")
	public static void apply(OptionInstance<?> option, Object value) {
		((OptionInstance<Object>) option).set(value);
	}

	/** 当前值（用于界面显示）。 */
	public static Object current(OptionInstance<?> option) {
		return option.get();
	}

	/** 反序列化文本到目标类型。 */
	public static Object parse(OptionInstance<?> option, String text) {
		Object current = option.get();
		try {
			if (current instanceof Integer) {
				return Integer.parseInt(text.trim());
			}
			if (current instanceof Double) {
				return Double.parseDouble(text.trim());
			}
			if (current instanceof Float) {
				return Float.parseFloat(text.trim());
			}
			if (current instanceof Boolean) {
				return Boolean.parseBoolean(text.trim());
			}
		} catch (NumberFormatException e) {
			return null;
		}
		return null;
	}

	/** 恢复该项到原版默认值（反射读 initialValue）。 */
	public static boolean restoreDefault(OptionInstance<?> option) {
		Object def = field(option, "initialValue");
		if (def == null) {
			return false;
		}
		apply(option, def);
		return true;
	}

	/** 枚举型选项前进一项（next 定义在 SliderableValueSet/CycleableValueSet 上，用反射调用）。 */
	public static boolean cycle(OptionInstance<?> option) {
		Object current = option.get();
		if (current == null) {
			return false;
		}
		try {
			Object values = option.values();
			java.lang.reflect.Method next = values.getClass().getMethod("next", Object.class);
			next.setAccessible(true);
			Object result = next.invoke(values, current);
			if (result instanceof Optional<?> opt && opt.isPresent()) {
				apply(option, opt.get());
				return true;
			}
		} catch (Throwable t) {
			return false;
		}
		return false;
	}

	@SuppressWarnings("unchecked")
	private static <T> T unchecked(Object o) {
		return (T) o;
	}

	public static Collection<Field> allFields(Class<?> type) {
		return List.of(type.getDeclaredFields());
	}
}
