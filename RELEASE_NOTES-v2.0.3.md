# 垂纶 v2.0.3 — 已定位并修复启动闪退

> **真正的崩溃原因找到了，不是推测。**
> v2.0.2 猜的是 Compose 版本错配，猜错了。这次是在 JVM 上把 Activity 真跑起来复现出来的。

## 崩溃原文

在 Robolectric（模拟 Android 14 / API 34 手表环境）里启动 `MainActivity`，稳定复现：

```
java.lang.NullPointerException:
  Cannot invoke "androidx.compose.runtime.MutableState.setValue(Object)"
  because "$this$setValue" is null

  at com.chuilun.app.game.GameEngine.setTheme(GameEngine.kt:464)
  at com.chuilun.app.game.GameEngine.refreshDaily(GameEngine.kt:156)
  at com.chuilun.app.game.GameEngine.<init>(GameEngine.kt:45)
  at com.chuilun.app.MainActivity.onCreate
```

## 根因：属性初始化顺序

`GameEngine` 长这样：

```kotlin
class GameEngine(...) {
    val save = SaveStore(context)
    private val detector = CastDetector(context)

    init {                       // ← 第 43 行，写在最前面
        Feedback.init(context)
        refreshDaily()           // ← 这里给 theme / tide 赋值
    }

    var stage  by mutableStateOf(Stage.SPOT)   // ← 第 51 行
    ...
    var theme  by mutableStateOf(...)          // ← 第 100 行
    var tide   by mutableStateOf(null)         // ← 第 105 行
}
```

**Kotlin 按声明顺序执行初始化**。`init` 块在第 43 行，`theme` 的委托对象在第 100 行才创建。所以 `init → refreshDaily() → theme = ...` 执行时，`theme` 背后的 `MutableState` 还是 `null`，赋值直接 NPE。

这是**必然发生**的崩溃 —— 不是概率问题，不是特定设备问题，任何人点开都会闪。

**修复**：把 `init` 块移到全部 `by mutableStateOf` 属性声明之后。

## 顺带修掉的数据 bug

数据完整性测试发现：**16 个物种引用了不存在的钓场 `riverbend`**（草鱼、翘嘴鲌、鳜鱼、青鱼、大口鲶、鳡鱼、岩原鲤、红腹锯鲑鲤、胭脂鱼、电鳗、欧洲巨鲶、达氏鳇、巨骨舌鱼、白鲟、鼋、胭脂鱼幼鱼）。

这些鱼在游戏里**永远不会出现**，因为它们的钓场列表里有个查不到 ID。已统一改为 `stream`（溪流）。

## 新增：这套测试留在仓库里

以后每次改动都会自动跑，防止同类问题复发。

| 测试 | 覆盖内容 |
|---|---|
| `StartupTest.mainActivity_starts_without_crash` | 真实启动 Activity，走完 Compose 组合 → 布局 → 绘制 |
| `StartupTest.application_class_is_registered` | Application 注册与崩溃记录文件 |
| `LogicTest.dailyEvents_survive_a_full_year` | 遍历 366 天，跑遍每日事件/鱼汛/节气/月相/潮汐 |
| `LogicTest.speciesData_is_consistent` | 265 条物种：稀有度、体长体重区间、活跃时段、季节、钓场引用 |
| `LogicTest.fishingIndex_computes_for_every_spot` | 17 个钓场的分时段指数 |
| `LogicTest.spots_are_unlockable` | 解锁阈值必须单调递增 |
| `EngineFlowTest.every_spot_can_be_opened` | 逐个打开 17 个钓场，检查状态与指数表 |
| `EngineFlowTest.wallet_and_bait_initialise_sanely` | 初始金币鱼饵 |
| `EngineFlowTest.daily_state_is_populated` | 每日主题与活跃物种 |

共 **9 个测试，全部通过**。

```bash
./gradlew testDebugUnitTest    # 自己也能跑
```

## 与 v2.0.2 的关系

v2.0.2 的两处改动（Compose BOM 降到 1.8.3、targetSdk 降到 34）**保留**。它们不是这次闪退的原因，但方向是对的：Wear Compose 1.5.6 声明的确实是 Compose 1.8.2，targetSdk 也确实应该跟设备一致。留着无害，而且包体从 29.9 MB 降到了 27.5 MB。

崩溃自记录（`ChuiLunApp`）也保留 —— 万一还有别的问题，下次打开会直接显示堆栈。

---

## 安装

```bash
adb uninstall com.chuilun.app        # 建议先卸载，避免旧版缓存
adb install chuilun-v2.0.3-debug.apk
```

## 版本信息

| 项 | 值 |
|---|---|
| versionName | 2.0.3 |
| versionCode | 4 |
| minSdk / targetSdk | 33 / 34 |
| 包体 | 27.5 MB |
| MD5 | `91a5008f95b47999aa863312884d09d8` |
