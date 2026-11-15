package com.limitless.mixin;

import com.limitless.backup.BackupManager;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 游戏实例构造完成时做首次启动备份（此时 options 已从 options.txt 读入）。 */
@Mixin(Minecraft.class)
public abstract class MinecraftInitMixin {
	@Inject(method = "<init>", at = @At("RETURN"))
	private void limitless$firstLaunchBackup(CallbackInfo ci) {
		try {
			Minecraft mc = (Minecraft) (Object) this;
			BackupManager.ensureFirstLaunchBackup(mc.options);
		} catch (Throwable ignored) {
			// 备份失败不能影响游戏启动
		}
	}
}
