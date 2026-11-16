package com.limitless.mixin;

import com.limitless.backup.BackupManager;
import com.limitless.config.OverrideApplier;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 游戏实例构造完成时（此时 options 已从 options.txt 读入）：
 * 先把玩家上次的自定义数值回填进去，再做首次启动备份。
 * <p>
 * 顺序很重要：先回填再备份，备份记的才是玩家真正在用的状态；反过来会把默认值当基线存档。
 */
@Mixin(Minecraft.class)
public abstract class MinecraftInitMixin {
	@Inject(method = "<init>", at = @At("RETURN"))
	private void limitless$firstLaunchBackup(CallbackInfo ci) {
		try {
			Minecraft mc = (Minecraft) (Object) this;
			OverrideApplier.applyOnce(mc.options);
			BackupManager.ensureFirstLaunchBackup(mc.options);
		} catch (Throwable ignored) {
			// 回填或备份失败都不能影响游戏启动
		}
	}
}
