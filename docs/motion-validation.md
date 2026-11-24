# 0.13.6 动效验收记录

日期：2026-11-24。版本号 0.13.6，versionCode 57。状态审查与设计决策见 [统一动效规范](motion.md)。未引入依赖、数据库迁移或金额计算变更。

## 修改范围

主要改动为统一动效策略、导航、共用控件、录入、图表、列表与付款识别浮层；新增触控／隐私语义测试及可重复性能采样。完整文件列表：

- `README.md`
- `app/build.gradle.kts`
- `app/src/androidTest/java/com/wmm/ledger/MotionPerformanceTest.kt`
- `app/src/androidTest/java/com/wmm/ledger/MotionUiTest.kt`
- `app/src/main/java/com/wmm/ledger/MainActivity.kt`
- `app/src/main/java/com/wmm/ledger/capture/PaymentCaptureOverlay.kt`
- `app/src/main/java/com/wmm/ledger/ui/AssetsScreen.kt`
- `app/src/main/java/com/wmm/ledger/ui/BillImportScreen.kt`
- `app/src/main/java/com/wmm/ledger/ui/CashflowCards.kt`
- `app/src/main/java/com/wmm/ledger/ui/Components.kt`
- `app/src/main/java/com/wmm/ledger/ui/EntryComposer.kt`
- `app/src/main/java/com/wmm/ledger/ui/EntryEditor.kt`
- `app/src/main/java/com/wmm/ledger/ui/LabelStyles.kt`
- `app/src/main/java/com/wmm/ledger/ui/LedgerAssistantScreens.kt`
- `app/src/main/java/com/wmm/ledger/ui/LedgerMotion.kt`
- `app/src/main/java/com/wmm/ledger/ui/LedgerNavigation.kt`
- `app/src/main/java/com/wmm/ledger/ui/LedgerScreens.kt`
- `app/src/main/java/com/wmm/ledger/ui/LedgerTheme.kt`
- `app/src/main/java/com/wmm/ledger/ui/LucideIcons.kt`
- `app/src/main/java/com/wmm/ledger/ui/PaymentCaptureScreen.kt`
- `app/src/main/java/com/wmm/ledger/ui/RefundEditor.kt`
- `app/src/main/java/com/wmm/ledger/ui/ScheduledBillsScreen.kt`
- `app/src/main/java/com/wmm/ledger/ui/SettingsScreens.kt`
- `app/src/main/java/com/wmm/ledger/ui/ShareCharts.kt`
- `app/src/main/java/com/wmm/ledger/ui/Surfaces.kt`
- `app/src/main/res/anim/ledger_dialog_enter.xml`
- `app/src/main/res/anim/ledger_dialog_exit.xml`
- `app/src/main/res/values/styles.xml`
- `docs/architecture.md`
- `docs/motion-validation.md`
- `docs/motion.md`
- `docs/ui-refinement/design.md`

## 编译与功能

- Debug、Release 与 AndroidTest 构建通过；Release 必要 Lint 通过。
- 28 项 JVM 单测通过。
- 全量 58 项模拟器测试通过，涵盖录入、转账／退款、账单核对、搜索／问答、图表、拖动排序、系统返回、权限与加密存储。
- 最后针对系统动画关闭补强了立即就位和取消速度形变的处理，重编译与 7 项相关交互复验通过。
- 定向测试确认离场内容的语义和点击立即失效，退出中可反向重新打开；金额隐藏的首个动画帧里旧数值即从未合并语义树移除。
- 完整 `lintDebug` 存在项目原有的 2 项 NewApi 错误：浅色和深色主题直接声明了要求 API 27 的 `windowLightNavigationBar`，而 minSdk 为 26。HEAD 原文件含相同声明；另有 45 项警告及 3 项提示。此次无新增 Lint 错误；没有为动效任务修改这些既有样式兼容问题。

## 窗口、主题与交互

设备为 Pixel 9 Pro Android 16 / API 36 arm64 模拟器。初次启动的自动软件渲染结果弃用，正式复验固定 Apple M5 host GPU / OpenGL ES 3.0（Metal），2GB 模拟器内存。

- 常规竖屏：全量回归覆盖横向底栏拖动、长按排序、垂直分类滚动、数字键边缘点击、快速连续输入、IME 替换／返回、页面返回及草稿重建。
- 320×600dp、1.3 倍字号、深色：7 项定向测试通过，覆盖展开后选末尾类型、收起后保留选择、场景滚动可达、保存、底栏拖动和统计更多分类。数字键采用统一的可容纳两行文字的最小行高，避免保存状态变更改变键盘布局。
- 1200×800dp、浅色：6 项测试通过，验证分栏、展开／收起、账户入口可达、输入、键盘返回、底栏拖动及动态语义。已目视检查合成账本截图。
- animator／window／transition 三项系统动画缩放为 0：7 项交互通过，之后恢复三项为 1，恢复设备分辨率、密度与字号。
- 极短窄屏下，原有「默认场景必须首屏可见」测试不适用：0.13.5 与 0.13.6 在同一 320×600dp／1.3 字号设置均不满足该首屏断言。保留原测试用于常规尺寸，另以实际滚动、选中和保存检查极短窗口，不以截图或放宽原断言替代验证。

## 性能

使用原签名 Release 包，0.13.5 和 0.13.6 各复测两轮。固定 1,200 笔合成流水、12 个类型、4 个场景；先暖机，再滚动 8 次，重复四轮底栏切换、周／年／月筛选、打开记账、展开／收起类型和关闭。每次结束恢复原账本。FrameMetrics 排除 FIRST_DRAW_FRAME，记录总耗时及测量布局耗时，单位毫秒。

| Release / 轮次 | 帧样本 | 总 P50 | 总 P95 | 布局 P50 | 布局 P95 | 超 33.33ms 帧 |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| 0.13.5 / 1 | 75 | 16.901 | 50.758 | 0.022 | 0.130 | 20 |
| 0.13.5 / 2 | 75 | 16.937 | 50.792 | 0.022 | 0.115 | 14 |
| 0.13.6 / 1 | 76 | 16.966 | 50.569 | 0.020 | 0.126 | 18 |
| 0.13.6 / 2 | 75 | 16.997 | 51.687 | 0.020 | 0.087 | 17 |

两版总 P50 相差约 0.06ms，布局 P95 未显示明确回退；总帧长尾仍约 51ms。样本来自模拟器与 Compose 自动化驱动，包含测试、合成和调度开销，不能据此宣称真机稳定 60／120fps。本次无物理手机连接，真机持续满帧标准仍需设备复测。未把软件渲染的数据、静态截图或一次平均值当作性能验收。

## 签名与覆盖升级

- 原签名证书 SHA-256：`f4d0c7c42516ab70f41f4b145f291167a755ed0e307f508ce13940c1870be881`。
- 0.13.5 → 0.13.6 使用 `adb install -r` 成功，安装时间保持 `2026-11-15 20:49:09`。
- 升级前后原有 `Upgrade-Huabei` 信用账户、净资产 −¥123.45、待还 ¥123.45 保留。
- 本地包：`release/WMM-0.13.6-release.apk`，签名验证与 16KB zipalign 检查通过。
- APK SHA-256：`a0ed90774c528b4dcc5d698e31ac0ca24e3dfe10669438de900058e45ff0f42f`。
- 发布目标：[v0.13.6](https://github.com/daybreak430/WMM/releases/tag/v0.13.6)。标签指向构建源码提交，附件名与本地一致；发布流程另核对公开下载 SHA-256。

提交信息：`优化：统一全项目状态动效与交互反馈`。
