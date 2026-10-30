# 垂纶 v2.1.0 — 圆形屏适配 + 流畅度

这一版只做两件事：**把界面真正装进圆屏**，以及**去掉主线程上那些不该有的开销**。

## 一、圆形屏适配

Galaxy Watch 7 是 480×480 的纯圆屏，逻辑直径只有 192dp。上一版所有内容都按方形铺满整屏，
只要文字不在屏幕正中，左右两端就会被圆弧切掉——「UI 没做好」指的就是这个。

本版按几何重新算了安全区：

> 圆半径 96dp，距中心 dy 处的可用半宽 = √(96² − dy²)。
> 取内容区 y ∈ [36dp, 156dp]，该区间最窄处的可用宽度 = 2×√(96²−60²) ≈ 150dp。
> 于是左右各留 20dp、内容宽 152dp，整屏内容就都落在圆内了。

具体改动：

- **统一安全区内边距**：所有页面共用 `Round.Padding`（左右 20dp / 上下 36dp），
  内容宽度恒定 152dp，不再贴边。
- **列表换成 `LazyColumn`**：原来的 `ScalingLazyColumn` 会自动居中并缩放首项，
  导致内容位置不可控、首屏大幅溢出。换成普通列表后，内容严格从顶部内边距开始排，
  滚动行为也更好预测。
- **按钮全部自绘**：Wear Material 的 `Button` 是白色胶囊，在近黑 HUD 上非常刺眼。
  换成 `HudButton` / `HudChip`——方框 + 荧光描边 + 像素字，和整体语言一致。
- **大图缩到安全尺寸**：结算页立绘 190×120 → 132×84dp，遛鱼页 → 100×62dp，
  详情页 180×112 → 128×80dp。
- **信息重新分级**：圆形屏竖向可用只有 120dp，一屏塞不下十来行。
  首页把「事件 + 鱼汛」压成两行、未解锁钓场合并成一行、三个主入口提到首屏；
  统计页由两行式改成单行式（`HudStatRow`），一屏就能看全。
- 遛鱼页重排：张力/收线进度带百分比数字，底部提示改成「按住收线 · 红了松手」。

## 二、像素字体清晰化

Zpix 的字身是 12px。上一版用了 9sp / 13sp / 17sp 这类字号，在 2.5 倍密度下
会落在 22.5px / 32.5px 这种半像素位置，笔画被插值糊掉——看上去就是「字很脏」。

本版把字号统一吸附到 12px 的整数倍（18 / 24 / 30 / 36 / 48px），
每一笔都落在物理像素格上；同时补上 `LineHeightStyle(Alignment.Center)`，
修掉相邻两行贴死的问题。

## 三、流畅度

真机上卡顿的主因不在 Compose，而在「每帧都在重算不该重算的东西」。

| 位置 | 问题 | 修法 | 实测 |
|---|---|---|---|
| `SaveStore.isCaught` | 每次调用都把整条已捕获串 `split → filter → toSet` | 内存 Set 缓存，写入时增量更新 | **13.8µs → 0.2µs（约 70 倍）** |
| `SaveStore.bestLength/bestWeight/catchCount` | 每次 `prefs.getFloat("len_$id")` | HashMap 缓存 | 同上量级 |
| `Env.hour/minute/seasonIndex` | 每次 `Calendar.getInstance()` 新建对象 | `java.time` + 分钟级记忆化 | — |
| `Env.dayKey` | 每次 `SimpleDateFormat.format` | 天级缓存 | **21ns** |
| `Env.weather` | 每帧 `Random(hash)` | 按「钓场+日期」缓存 | — |
| 首页 17 个钓场的鱼口指数 | 每帧每场算一次 | `remember` 整屏算一次 | — |
| `dexEntries()` | 每次重组新建 265 个对象 | `remember` 缓存 | 177µs，一次 |
| 等待咬钩的声呐扫掠 | 30fps 驱动父级重组 | `rememberInfiniteTransition`，扫掠值只在绘制阶段读取 | 只重绘那一层 |
| HUD 扫描线 | 120 条 `drawLine` 每次重绘 | `drawWithCache` 缓存绘制指令 | 尺寸不变就不重画 |
| `HudBackground` / `Round.Padding` | 每次组合新建对象 | 复用实例 | — |

顺带修掉一个一直存在的显示 bug：**钓到新鱼后图鉴计数不刷新**。
`caughtBiology` 之前是普通 getter（读 SharedPreferences 派生值），
不参与 Compose 的订阅，所以钓完鱼回首页数字还是旧的。现在它是 `mutableIntStateOf`。

## 四、验证方式

没有真机也能验证：Robolectric 在 JVM 上跑真实的 Compose 组合 → 布局 → 绘制，
把 192dp 圆屏的每一屏渲成 PNG，再叠一个圆形遮罩，被裁的部分会直接露出底色。

- `ShotTest`：10 个界面的渲染快照
- `BenchTest`：上面那张表的数字来源
- `StartupTest` / `LogicTest` / `EngineFlowTest`：原有回归

全部 12 个用例通过。

## 五、仍待处理

- 71 / 161 张立绘未生成（近海 10 / 远洋 18 / 深海 43），目前回落到程序化绘制
- release 签名未配置，APK 仍是 debug 签名
- 物种数据未逐条联网核实
