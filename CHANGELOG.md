# 更新日志

本模组的版本变更记录，按版本倒序。安装包见 [Releases](../../releases)。

## 0.6.5

- 视频设置入口页（`VideoRedirectScreen`）第一行固定加上「打开原版视频设置（有风险）」
  - 即使一个页面都没接管到，也能从这里进原版视频页
- 新增 `ui/VanillaWarningScreen` 警告页：说明原版页面会对超范围的值重新校验并重置，
  两个按钮「确认打开原版」/「返回 mod 菜单」
  - 确认后构造原版 `VideoSettingsScreen`，走放行通道（`VideoRedirect.openOriginal`），
    不会被自己的拦截图 mixin 再抓回来
  - 原版页的「完成」直接回到模组主界面，不经过入口页
  - 返回按钮回到模组主界面（入口页的父屏），不是回到入口页
- 新增 7 个翻译键 ×3 语言（`zh_cn` / `zh_tw` / `en_us` 各 59 键，键集合已验证一致）

## 0.6.4

- 修复「游戏内从来不弹更新提示」：
  - 根因：Java 只信任 JDK 自带 `cacerts`；Steam++ 之类的加速器会接管 HTTPS 并换上自己的根证书
    （`CN=SteamTools Certificate`），证书装在 Windows 证书库里，浏览器能用、Java 用不了，
    握手抛 `PKIX path building failed`，检查结果被按「无更新」处理，于是永远静默
  - 新增 `update/UpdateHttp`：先按 JDK 默认信任库校验，**不通过时才回退 Windows 系统证书库**
    （`Windows-ROOT`），且只作用于更新检查与下载这一路流量，不改任何全局 SSL 配置
  - 回退发生时打一条 WARN，说明连接可能经过本机中间人
  - 检查失败且错误里含 `PKIX` / `certificate` 时，日志额外提示"可能是加速器接管了 HTTPS"
  - 非 Windows 平台没有系统证书库，保持默认校验

## 0.6.3

- 自动换版按平台分流，补上 macOS / Linux 支持：
  - Windows：仍走脱离进程的 `cmd` 助手（JAR 被加载器独占，进程内改不了名）
  - macOS / Linux：POSIX 允许重命名正被打开的文件，直接在进程内完成换版，不调用 `cmd.exe`
  - 直接换版路径增加字节数核对，复制后大小不符就删掉新文件并把旧 JAR 改回原名，不留死局
  - 版本说明与已知限制补齐平台支持表

## 0.6.2

- 修复「更新下载成功，但重启后版本没变」：
  - 根因是 Windows 给运行中的 JAR 加了独占锁，关闭钩子里的 `Files.move` 必然抛 `AccessDeniedException`，
    异常被吞成一行日志，于是换版静默失效
  - 改为拉起一个独立的 `cmd` 助手：先轮询等游戏 PID 消失（锁释放），再执行
    「旧 JAR 改名 `.disabled` → 新 JAR 复制进 `mods/` → 删暂存」
  - 助手脚本只含相对路径和 ASCII 文件名（工作目录设为 `mods`），绕开批处理编码问题
  - 助手执行结果写入 `mods/.limitless-update/apply.log`（`OK` / `MOVE-FAILED` / `COPY-FAILED`），
    下次启动读出打进日志
  - 换版失败自愈：下次启动发现暂存 JAR 还在，会自动重新挂一次，本次退出时再试
  - 保留进程内直改作为兜底，复制失败会把旧 JAR 改回原名，不留下半截状态

## 0.6.1

- 修复「每更新一次模组配置就重置」：
  - 新增 `config/OverrideApplier`：启动时把 `limitless.json` 里记录的数值回填进 `Options`，
    再立刻写回 `options.txt`。以前这些值只写给 options.txt，一旦它被换启动器/重装/异常退出冲掉就没了
  - 配置写入改为**原子写**（`.tmp` → 替换），并在覆盖前留 `limitless.json.bak`
  - 主配置解析失败时先读 `.bak`；坏文件改名成 `limitless.json.corrupt-<时间戳>` 保留，不再静默回默认
  - 数值项每次键入即落盘，布尔开关也记进账本（以前只有数字项被记录）
  - 检测到模组版本变化时，把当前配置另存到 `设置备份/配置-<旧版本>-<时间戳>.json`
  - 失效项（新版里改名的参数）自动从账本剔除，并写日志

## 0.6.0

- 新增启动时更新检查：后台查 `api.github.com` 的最新 Release，有新版本时主菜单弹出提示
- 三个选项：立即下载 / 静默下载（下次启动自动加载）/ 暂不下载（这一版不再提示）
- 下载走暂存目录 `mods/.limitless-update/`，退出时在关闭钩子里换掉旧 JAR（改名为 `.jar.disabled`），
  下次启动生效；运行中不动正在使用的 JAR
- 检查结果缓存 6 小时；断网、超时、接口限流、JSON 异常一律当作"无更新"，不阻塞进游戏
- `config/limitless.json` 新增 `skippedVersion`、`lastUpdateCheck`、`lastKnown*` 字段，
  旧配置文件缺字段时自动兜成空串

## 0.5.6

- 新增模组图标：方块化的 ∞ 徽标，Fabric 模组列表、Modrinth 与 CurseForge 共用同一张图
- README 首屏加「原版上限 vs 装上之后」对照表，明确写出「允许原样收录进整合包」
- 修正安装说明里的 JAR 文件名（旧文档误写作 `infinite-limits-<版本>.jar`，实际是 `limitless-<版本>.jar`）

## 0.5.5

- 新增多语言系统：`com.limitless.i18n.I18n` 统一走 `Component.translatable`，
  界面文案全部改成翻译键，跟随客户端语言自动切换，切换语言不用重启
- 语言文件三份：`zh_cn`（简体）、`zh_tw`（繁体）、`en_us`（英文，作为缺省回退）
- 模组界面左下角新增 `GitHub` 按钮，走原版 `ConfirmLinkScreen` 确认后打开仓库
- 设置备份里的 `说明.txt` 改为中英双语（该文件在 Minecraft 构造阶段写出，
  那时语言管理器尚未加载，无法用翻译键，故直接写双语文案）

## 0.5.4

- 模组界面右下角新增「视频设置」按钮，点进去是独立的「视频设置入口」页
- 入口页按来源逐条列出被接管过的视频设置页（原版 / Sodium / 第三方模组），点哪条放行哪条，
  不再像 0.5.3 那样只在底部摆一个「打开 xxx」按钮（多来源时摆不下也分不清是谁家的页面）
- 同一来源只保留最新实例，最多记录 8 个来源
- 主界面左下角改为显示"已接管 N 个视频设置页"

## 0.5.3

- 视频设置接管放宽：从"只认原版与 Sodium 两个固定类名"改成"原版 + Sodium 白名单 + 类名特征兜底"，
  任何以 `VideoSettingsScreen` / `VideoSettings` / `GraphicsSettingsScreen` 结尾的页面都会被接管，
  解决了装了其他占视频设置页的模组时接管失效、两边参数混在一张页面里的问题
- 新增 `com.limitless.compat.VideoRedirect`：记住被接管的页面实例与来源名称
- 新增「打开 xxx 的视频设置」按钮：右下角一键放行，用原页面实例打开那个模组自己的设置页
- 新增递归防护：放行时置 bypass 开关，`Gui.setScreen` 直接返回，避免"打开→拦截→再打开"死循环
- 新增橙色提示行，写明本次接管的是原版 / Sodium / 哪个第三方模组的页面
- `fabric.mod.json` 的 license 由 `MIT` 改为 `All Rights Reserved`，与仓库里的 LICENSE 对齐

## 0.5.2

- 底部四个按钮压成两排两列，不再横跨一整行
- 参数列表固定每页四行分页，翻页时页面高度不再跳动

## 0.5.1

- 「分类筛选」与「上页 / 下页」按钮从列表底部移到顶部，翻页不用再滚到底
- 列表区域高度固定，翻页时按钮位置保持不动

## 0.5.0

- 参数按「画面 / 声音 / 操作 / 界面 / 其他」五类分组
- 顶部新增分类筛选条，点分类只显示该类的参数，解决一屏几十项找不着的问题

## 0.4.0

- 修复进「无障碍设置」页直接崩溃（`AccessibilityOptionsScreen.addOptions` 取
  `options[0]` 下标越界）
- 移除原版参数的方式从「返回空参数数组」改为「清空 `OptionsList` 列表」，
  不再触发依赖数组下标的原版代码
- 按键绑定页完全不动，保持原版行为

## 0.3.1

- 被清空的设置页（视频 / 声音 / 聊天 / 鼠标 / 无障碍）加一行说明文字
  与一个「打开无限 Limitless」跳转按钮，不再是一片空白

## 0.3.0

- 「视频设置」按钮重定向到模组界面（`Gui.setScreen` HEAD 拦截）
- 同时覆盖原版视频页与 Sodium 的 `VideoSettingsScreen`，装了钠也能跳转
- 已知取舍：钠自己的渲染参数（区块构建线程、剔除、半透明排序、雾）因此没有界面入口

## 0.2.1

- 修复界面文字全透明、只看得见按钮看不见字
- 根因：26.2 新渲染管线 `GuiGraphicsExtractor` 不再自动补 alpha，
  `0xFFFFFF` 的 alpha 为 0 即完全不可见；全部颜色改为 `0xFFFFFFFF`

## 0.2.0

- 首次启动自动备份：游戏目录下生成
  `设置备份\首次启动-<yyyyMMdd-HHmmss>\`，内含 `options.txt`、`optionsof.txt`（若存在）、
  `参数快照.json`、`说明.txt`
- 界面新增「从备份恢复」，读取最新快照逐项写回，无需重启

## 0.1.0

- 初版
- 拦住 `OptionInstance` 三处取值范围校验（`IntRange` / `ClampingLazyMaxIntRange` /
  `UnitDouble`），超界数值不再被自动夹回
- 清空原版视频 / 声音 / 聊天 / 鼠标 / 无障碍设置页里列出的参数
- 新增独立的参数管理界面，可逐项键入任意数值、布尔项点击切换、枚举项点击循环
- 新增「全部恢复默认」，把每一项写回原版默认值
