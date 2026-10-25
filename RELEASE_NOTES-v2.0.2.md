# 垂纶 v2.0.2 — 修复启动闪退

> **如果你装了 v2.0.0 点开就闪退，请装这个版本。**

## 这次修了什么

三处改动，都指向「编译期与运行期的环境错配」。

### 1. Compose 版本错配（首要嫌疑）

| | 之前 | 现在 |
|---|---|---|
| Compose BOM | 2025.08.01 | **2025.07.00** |
| 解析出的 Compose | **1.9.0** | **1.8.3** |
| Wear Compose 1.5.6 声明的依赖 | ui 1.8.2 / runtime 1.8.0 | 同左 ✅ |

Wear Compose 1.5.6 是按 Compose **1.8** 编译的，但 BOM 把它提升到了 **1.9**。编译能过（源码层面 API 存在），运行时二进制不兼容就会抛 `NoSuchMethodError` —— 这是"点图标立刻闪退"最典型的原因。

### 2. targetSdk 高于设备

`targetSdk` 从 **36** 降到 **34**。

Galaxy Watch 7 跑的是 Wear OS 5，对应 **Android 14 / API 34**。之前声明 36（Android 16），系统会按"未来版本"解析部分行为，属于不该有的错配。

### 3. 移除零引用依赖

- `androidx.wear:wear:1.4.0`（legacy 库，代码里零 import）
- `androidx.wear.protolayout:protolayout-material`（零 import）

只贡献体积和 dex 数量，白占启动时的类加载。

---

## 新增：崩溃自记录

即使还有别的问题，这次也不会"默默闪退"了。

`ChuiLunApp` 会捕获未处理异常，把完整堆栈（含设备型号、系统版本、应用版本）写到应用私有目录，**下次打开应用时直接显示在屏幕上**（红字标题「上次启动崩溃」+ 堆栈正文），不用连数据线也能看到原因。

如果新版本仍然闪退，**再打开一次**，把那个红色页面截图发我就行。

---

## 数据

| 项 | v2.0.0 | v2.0.2 |
|---|---|---|
| 包体 | 29.9 MB | **27.4 MB** |
| dex 数量 | 11 | **10** |
| targetSdk | 36 | **34** |

功能与内容完全不变：161 生物 + 104 旧物、16 钓场、钓鱼指数、每日事件、鱼饵金币、Tile、通知。

---

## 安装

```bash
adb install -r chuilun-v2.0.2-debug.apk
```

覆盖安装即可，存档（SharedPreferences）会保留。

如果提示签名冲突：

```bash
adb uninstall com.chuilun.app
adb install chuilun-v2.0.2-debug.apk
```

---

## 如果还是闪退

请提供任意一种：

1. **应用内截图** —— 崩溃后再次打开应用，会显示「上次启动崩溃」页，截图即可
2. **logcat** —— `adb logcat -d -b crash`
