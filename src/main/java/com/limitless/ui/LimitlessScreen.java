package com.limitless.ui;

import com.limitless.backup.BackupManager;
import com.limitless.config.LimitlessConfig;
import com.limitless.option.Category;
import com.limitless.option.OptionMirror;
import net.minecraft.client.Options;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * 无限模组主界面：按「画面 / 声音 / 操作 / 界面 / 其他」分类列出全部 OptionInstance，
 * 每项可直接键入任意数值（含负数与超大值）。
 */
public class LimitlessScreen extends Screen {
	private static final int ROW_HEIGHT = 22;
	private static final int TOP = 78;

	/** 一行：要么是分类标题（header 非空），要么是一个参数（entry 非空）。 */
	private record Row(String header, OptionMirror.Entry entry) {
		static Row header(String text) {
			return new Row(text, null);
		}

		static Row item(OptionMirror.Entry entry) {
			return new Row(null, entry);
		}
	}

	private final Screen parent;
	private List<OptionMirror.Entry> entries = new ArrayList<>();
	private List<Row> rows = new ArrayList<>();
	private Category filter;
	private int page;
	private int rowsPerPage = 8;
	private String status = "";

	public LimitlessScreen(Screen parent) {
		super(Component.literal("无限 Limitless"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		Options options = this.minecraft.options;
		this.entries = OptionMirror.scan(options);
		buildRows();
		this.rowsPerPage = Math.max(2, Math.min(4, (this.height - TOP - 100) / ROW_HEIGHT));
		if (page * rowsPerPage >= rows.size()) {
			page = 0;
		}
		buildPage();
	}

	private int pageCount() {
		return Math.max(1, (rows.size() + rowsPerPage - 1) / rowsPerPage);
	}

	private void buildRows() {
		rows.clear();
		for (Category category : Category.values()) {
			if (filter != null && filter != category) {
				continue;
			}
			List<OptionMirror.Entry> inCategory = new ArrayList<>();
			for (OptionMirror.Entry entry : entries) {
				if (Category.of(entry.id(), entry.key()) == category) {
					inCategory.add(entry);
				}
			}
			if (inCategory.isEmpty()) {
				continue;
			}
			if (filter == null) {
				rows.add(Row.header(category.displayName + "  ·  " + inCategory.size() + " 项"));
			}
			for (OptionMirror.Entry entry : inCategory) {
				rows.add(Row.item(entry));
			}
		}
	}

	private void buildPage() {
		int start = page * rowsPerPage;
		int end = Math.min(start + rowsPerPage, rows.size());
		int boxWidth = 96;

		for (int i = start; i < end; i++) {
			Row row = rows.get(i);
			if (row.entry() == null) {
				continue;
			}
			OptionMirror.Entry entry = row.entry();
			int y = TOP + (i - start) * ROW_HEIGHT;
			Object value = entry.option().get();

			if (value instanceof Boolean) {
				Button button = Button.builder(
						Component.literal(Boolean.TRUE.equals(value) ? "开" : "关"),
						b -> {
							OptionMirror.apply(entry.option(), !Boolean.TRUE.equals(entry.option().get()));
							b.setMessage(Component.literal(Boolean.TRUE.equals(entry.option().get()) ? "开" : "关"));
						}).bounds(this.width - 8 - boxWidth, y, boxWidth, 18).build();
				this.addRenderableWidget(button);
			} else if (value instanceof Number) {
				EditBox box = new EditBox(this.font, this.width - 8 - boxWidth, y, boxWidth, 18, Component.literal("值"));
				box.setValue(trim(value));
				box.setResponder(text -> {
					Object parsed = OptionMirror.parse(entry.option(), text);
					if (parsed != null) {
						OptionMirror.apply(entry.option(), parsed);
						LimitlessConfig.get().setOverride(entry.key(), text.trim());
					}
				});
				this.addRenderableWidget(box);
			} else {
				Button button = Button.builder(
						Component.literal(String.valueOf(value)),
						b -> {
							OptionMirror.cycle(entry.option());
							b.setMessage(Component.literal(String.valueOf(entry.option().get())));
						}).bounds(this.width - 8 - boxWidth, y, boxWidth, 18).build();
				this.addRenderableWidget(button);
			}
		}

		buildTopBar();
		buildFilterBar();
		buildFooter();
	}

	/** 顶部：上一页 / 页码 / 下一页。 */
	private void buildTopBar() {
		int y = 52;
		this.addRenderableWidget(Button.builder(Component.literal("< 上一页"), b -> {
			if (page > 0) {
				page--;
				rebuild();
			}
		}).bounds(12, y, 72, 18).build());

		this.addRenderableWidget(Button.builder(Component.literal("下一页 >"), b -> {
			if (page + 1 < pageCount()) {
				page++;
				rebuild();
			}
		}).bounds(this.width - 12 - 72, y, 72, 18).build());
	}

	private void buildFilterBar() {
		String[] names = {"全部", "画面", "声音", "操作", "界面", "其他"};
		Category[] values = {null, Category.VIDEO, Category.SOUND, Category.CONTROL, Category.INTERFACE, Category.OTHER};
		int count = names.length;
		int gap = 4;
		int buttonWidth = Math.min(52, Math.max(26, (this.width - 24 - gap * (count - 1)) / count));
		int totalWidth = buttonWidth * count + gap * (count - 1);
		int x = this.width / 2 - totalWidth / 2;
		int y = 30;

		for (int i = 0; i < count; i++) {
			Category target = values[i];
			boolean active = (filter == null && target == null) || (filter != null && filter == target);
			String label = (active ? "▸" : "") + names[i];
			this.addRenderableWidget(Button.builder(Component.literal(label), b -> {
				filter = target;
				page = 0;
				buildRows();
				rebuild();
			}).bounds(x, y, buttonWidth, 18).build());
			x += buttonWidth + gap;
		}
	}

	private void buildFooter() {
		int rowTop = this.height - 76;
		int rowBottom = this.height - 52;
		int leftX = this.width / 2 - 130;
		int rightX = this.width / 2 + 4;
		int buttonWidth = 126;

		this.addRenderableWidget(Button.builder(
				Component.literal(LimitlessConfig.get().extremeMode ? "极限模式: 开" : "极限模式: 关"),
				b -> {
					LimitlessConfig cfg = LimitlessConfig.get();
					cfg.extremeMode = !cfg.extremeMode;
					cfg.save();
					b.setMessage(Component.literal(cfg.extremeMode ? "极限模式: 开" : "极限模式: 关"));
				}).bounds(leftX, rowTop, buttonWidth, 20).build());

		Path newestBackup = BackupManager.newest();
		this.addRenderableWidget(Button.builder(
				Component.literal("从备份恢复"), b -> {
					int restored = BackupManager.restore(this.minecraft.options);
					status = restored > 0
							? ("已从备份恢复 " + restored + " 项参数")
							: (restored == 0 ? "备份里没有可识别的参数" : "没有找到可用备份");
					rebuild();
				}).bounds(rightX, rowTop, buttonWidth, 20).build());

		this.addRenderableWidget(Button.builder(Component.literal("全部恢复默认"), b -> {
			for (OptionMirror.Entry e : entries) {
				OptionMirror.restoreDefault(e.option());
			}
			LimitlessConfig.get().overrides.clear();
			LimitlessConfig.get().save();
			rebuild();
		}).bounds(leftX, rowBottom, buttonWidth, 20).build());

		this.addRenderableWidget(Button.builder(Component.literal("完成"), b -> onClose())
				.bounds(rightX, rowBottom, buttonWidth, 20).build());

		if (status.isEmpty() && newestBackup != null) {
			status = "最近备份：" + newestBackup.getFileName();
		}
	}

	private void rebuild() {
		this.clearWidgets();
		buildPage();
	}

	private static String trim(Object value) {
		if (value instanceof Double d) {
			return String.valueOf(Math.round(d * 1000.0) / 1000.0);
		}
		return String.valueOf(value);
	}

	/**
	 * 从任意设置子页面反射取出它要返回的上一级界面（原版叫 lastScreen，钠叫 prevScreen）。
	 */
	public static Screen findParent(Screen screen) {
		for (Class<?> type = screen.getClass(); type != null && type != Object.class; type = type.getSuperclass()) {
			for (String field : new String[]{"lastScreen", "prevScreen", "parent"}) {
				try {
					java.lang.reflect.Field f = type.getDeclaredField(field);
					f.setAccessible(true);
					Object value = f.get(screen);
					if (value instanceof Screen s) {
						return s;
					}
				} catch (Throwable ignored) {
					// 换下一个候选字段
				}
			}
		}
		return null;
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		graphics.fill(0, 0, this.width, this.height, 0xCC101010);
		graphics.centeredText(this.font, this.title, this.width / 2, 10, 0xFFFFFFFF);
		graphics.centeredText(this.font,
				Component.literal("第 " + (page + 1) + " / " + pageCount() + " 页 · "
						+ (filter == null ? "全部 " : filter.displayName + " ")
						+ entries.size() + " 项 · "
						+ (LimitlessConfig.get().extremeMode ? "已解除钳制" : "原版范围")),
				this.width / 2, 57, 0xFFA0A0A0);

		int start = page * rowsPerPage;
		int end = Math.min(start + rowsPerPage, rows.size());
		for (int i = start; i < end; i++) {
			Row row = rows.get(i);
			int y = TOP + (i - start) * ROW_HEIGHT;
			if (row.header() != null) {
				graphics.text(this.font, row.header(), 12, y + 5, 0xFFFFC24A);
				continue;
			}
			OptionMirror.Entry entry = row.entry();
			int color = isDangerous(entry, entry.option().get()) ? 0xFFFF5555 : 0xFFFFFFFF;
			String label = entry.label();
			if (label.length() > 34) {
				label = label.substring(0, 33) + "...";
			}
			graphics.text(this.font, label, 22, y + 5, color);
		}

		if (!status.isEmpty()) {
			graphics.text(this.font, status, 12, this.height - 96, 0xFF88FF88);
		}

		super.extractRenderState(graphics, mouseX, mouseY, partialTick);
	}

	private boolean isDangerous(OptionMirror.Entry entry, Object value) {
		if (!(value instanceof Number n)) {
			return false;
		}
		double v = n.doubleValue();
		String key = entry.key();
		if (key.contains("renderDistance") || key.contains("simulationDistance")) {
			return v > 32;
		}
		if (key.contains("framerate") || key.contains("Framerate")) {
			return v > 260;
		}
		if (key.contains("fov") || key.contains("Fov")) {
			return v > 110 || v < 30;
		}
		return Math.abs(v) > 10_000;
	}

	@Override
	public void onClose() {
		LimitlessConfig.get().save();
		this.minecraft.gui.setScreen(this.parent);
	}
}
