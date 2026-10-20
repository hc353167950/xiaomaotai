# 小茅台纪念日 APP

一个简洁优雅的 Android 纪念日应用，帮你记住每一个重要的日子 —— 也帮你记住那些已经走过的日子。

**数据完全保存在本机**，可选通过 WebDAV（坚果云 / InfiniCLOUD / Nextcloud / 群晖等）手动备份与同步，不依赖任何自建账号系统。

> 当前版本：**3.3.0**（versionCode 12）

---

## 功能特色

### 纪念日管理

- 添加、编辑、删除纪念日
- **备注**：记下这个日子背后的故事（最多 200 字），卡片上折叠显示一行摘要
- 支持**公历**：`2025-12-18`
- 支持**农历**（含闰月）：`农历六月十六`、`农历闰六月初一`
- 支持**忽略年份**：`12-18`，每年循环
- 10 套渐变配色随机分配，同屏不撞色

### 两种计数方式

| 模式 | 显示 | 典型场景 |
|------|------|----------|
| **倒数** | `12 天后` / `约1月` | 生日、纪念日、节假日 |
| **正数** | `1825 已经` / `约5年` | 在一起第几年、戒烟第几天、坚持了多久 |

- 天数超过一个月时自动标注「约 N 月」，超过一年标注「约 N 年 N 月」（如 `约1年1月`）
- 天数位数变多时自动缩小字号，不会顶到卡片边缘
- 首页用 **「倒数 / 已过去」两个标签页**分开展示，两种模式各自按符合直觉的方向排序

### 智能提醒系统

**默认档位**（可按事件自定义）：

| 档位 | 提醒时刻 |
|------|----------|
| 提前 7 天 | 上午 08:00 |
| 提前 1 天 | 上午 08:00 |
| 当天 | 凌晨 00:00、上午 08:00、中午 12:00 |

- **按事件独立配置**：可单独关闭提醒、改提前档位、选提醒语气
- **提醒语气**区分场合，避免冒犯：

  | 语气 | 当天文案 |
  |------|----------|
  | 中性（默认） | 今天是「X」 |
  | 祝福 | 今天是「X」，祝一切顺遂 |
  | 纪念 | 今天是「X」，愿一切安好 |
  | 提醒 | 今天：「X」 |

- **通知快速操作**：
  - **稍后提醒** —— 60 分钟后再次提醒
  - **今年不再提醒** —— 跳过该纪念日本年度的所有提醒（明年自动恢复）
- 精确闹钟，开机自动恢复
- **正数模式不发送提醒**（它没有「到期」这件事）

### 常驻通知栏

- 显示最近的纪念日与倒计时天数
- 同一天多个事件会依次显示
- 零点自动更新

### 排序与搜索

- **自动排序**：倒数区按天数升序（最近要到的最前）；正数区按天数降序（累积最久的最前）
- **手动排序**：拖拽自定义，关闭自动排序后生效
- 正序 / 倒序快速排列
- 搜索支持**事件名 + 备注**，实时过滤

### WebDAV 同步（手动触发）

在「设置 → 备份与同步」配置服务器后，可用三个功能：

| 功能 | 行为 |
|------|------|
| **备份到云端** | 本地全部数据覆盖云端（**本地为空时拒绝执行**；覆盖前会把云端旧数据另存一份快照） |
| **从云端恢复** | 云端全部数据覆盖本地（覆盖前自动保存本地快照，保留最近 3 份） |
| **智能合并** | 云端与本地取并集，按「名称 + 日期」去重，两边都不丢；合并前显示预演结果供确认 |

- 云端为单文件快照：`<你的目录>/xiaomaotai/events.json`
- 凭据使用 `EncryptedSharedPreferences`（Android Keystore 托管主密钥）加密保存，不落明文
- 目录不存在时会**自动逐级创建**，无需手动建
- 服务器地址粘贴时带入的换行与多余内容会被自动清理

**典型配置**：

| 服务 | 服务器地址 | 用户名 | 密码 |
|------|-----------|--------|------|
| 坚果云 | `https://dav.jianguoyun.com/dav/` | 登录邮箱 | **应用密码** |
| InfiniCLOUD | `https://soya.infini-cloud.net/dav/` | **数字登录 ID**（不是邮箱） | 登录密码 |

### 界面与隐私

- **两套界面风格**：
  - **流光渐变** —— 高饱和双色渐变 + 柔光 + 白字
  - **清透白卡** —— 白底细描边 + 左侧渐隐色条 + 淡光晕
- 深色模式完整适配
- 支持隐藏最近任务（从任务列表隐藏 APP）
- 双击返回退出
- 无任何埋点、无账号、无网络请求（除你主动触发的 WebDAV 同步）

---

## 技术栈

| 项 | 选择 |
|----|------|
| 开发语言 | Kotlin 1.9.22 |
| UI 框架 | Jetpack Compose + Material Design 3 |
| 构建工具 | AGP 8.3.0 / Gradle 8.13 |
| 本地存储 | SharedPreferences（JSON 格式） |
| JSON 序列化 | Gson 2.10.1（带字段兜底的反序列化器） |
| 云端同步 | WebDAV（Ktor 2.3.7 + OkHttp 引擎） |
| 农历计算 | lunar-java 1.7.7 |
| 后台任务 | WorkManager 2.9.0 |
| 最低支持 | Android 8.0 (API 26) |
| 目标版本 | Android 14 (API 34) |

---

## 项目结构

```
app/src/main/java/com/example/xiaomaotai/
├── MainActivity.kt                  # 主 Activity、导航、首页（含倒数/已过去标签页）
├── XiaoMaoTaiApp.kt                 # Application：单例装配与启动引导
├── AppInfo.kt                       # 版本号与更新说明（关于页数据源）
│
├── Event.kt                         # 事件模型 + EventMode / ReminderTone 枚举
├── EventStore.kt                    # 事件仓库（唯一读写出口）
├── EventJson.kt                     # JSON 编解码（旧数据字段兜底）
│
├── Countdown.kt                     # 倒计时深模块（公历/农历/闰月/正数模式/天数换算）
├── DateParser.kt                    # 日期字符串解析
├── LunarCalendarHelper.kt           # 农历转换与闰月查询（数据来自 lunar-java）
├── ValidationUtils.kt               # 输入校验（含农历日期合法性）
│
├── ReminderSchedule.kt              # 提醒时刻表（唯一来源，支持按事件覆盖）
├── ReminderManager.kt               # AlarmManager 调度 + 已发送台账
├── ReminderNotifier.kt              # 通知构建（含快捷操作按钮）
├── ReminderReceiver.kt              # 闹钟触发接收
├── ReminderActionReceiver.kt        # 通知按钮点击（稍后提醒 / 今年不再提醒）
├── ReminderWorker.kt                # WorkManager 周期补漏
├── PersistentNotificationService.kt # 常驻通知前台服务
├── NotificationAccessibilityService.kt # 无障碍保活
│
├── WebDavClient.kt                  # WebDAV 协议层（PROPFIND / MKCOL / PUT / GET）
├── WebDavSync.kt                    # 三个同步功能：备份 / 恢复 / 智能合并
├── WebDavCredentials.kt             # 凭据加密存储 + 地址清洗
│
├── AppSettings.kt                   # 设置读写
├── PermissionManager.kt             # 权限探测与跳转
├── TimeFormatUtils.kt               # 相对时间文案
│
└── ui/
    ├── theme/                       # 设计令牌
    │   ├── Color.kt                 # 配色与渐变调色板
    │   ├── Theme.kt                 # 三套 ColorScheme
    │   ├── Type.kt                  # 字体
    │   ├── Spacing.kt               # Space / Shape / Sizes 令牌
    │   └── UiStyle.kt               # 界面风格枚举
    │
    └── components/
        ├── SettingsHomeScreen.kt    # 设置根页（分类入口）
        ├── SettingsScaffold.kt      # 二级设置页通用外壳
        ├── SettingsRoute.kt         # 设置路由
        ├── UiStyleScreen.kt         # 界面风格（带缩略预览）
        ├── NotificationScreen.kt    # 通知与权限
        ├── KeepAliveScreen.kt       # 后台保活
        ├── SortingScreen.kt         # 排序规则
        ├── ProfileScreen.kt         # 备份与同步（WebDAV）
        ├── AboutScreen.kt           # 关于
        ├── EventItem.kt             # 事件卡片（渐变卡 / 白卡）
        ├── EventDialog.kt           # 添加/编辑弹窗
        ├── CardStyleManager.kt      # 卡片样式
        ├── CustomDatePicker.kt      # 滚轮日期选择器
        ├── WheelPicker.kt           # 通用滚轮
        ├── SortScreen.kt            # 拖拽排序
        └── GlobalLoadingDialog.kt   # 全屏加载
```

---

## 构建说明

### 环境要求

- Android Studio Hedgehog | 2023.1.1 或更高
- JDK 17
- Android SDK 34
- Gradle 8.13

### 构建命令

```bash
# 构建 Debug 版本
./gradlew assembleDebug

# 运行单元测试（74 个用例）
./gradlew testDebugUnitTest

# 构建 Release 版本（需要签名配置）
./gradlew assembleRelease -PRELEASE_STORE_PASSWORD=xxx -PRELEASE_KEY_PASSWORD=xxx
```

### 签名配置

Release 版本需要签名密钥：

- 密钥文件位置：`app/xiaomaotai-release-key.jks`（已在 `.gitignore` 中忽略）
- 别名：`xiaomaotai`
- 密码通过 Gradle 属性提供：`RELEASE_KEY_PASSWORD` / `RELEASE_STORE_PASSWORD`
- CI 通过 GitHub Secrets 注入：`RELEASE_KEYSTORE_BASE64` / `RELEASE_STORE_PASSWORD` / `RELEASE_KEY_PASSWORD`

### 持续集成

推送到 `main` 分支会自动触发 `.github/workflows/auto-tag.yml`：

1. 从 `app/build.gradle.kts` 读取 `versionName` / `versionCode`
2. 生成 tag：`v{版本}-{日期}-{短SHA}`
3. 构建 Debug 与 Release APK
4. 创建 GitHub Release 并附带产物

---

## 权限说明

| 权限 | 用途 |
|------|------|
| POST_NOTIFICATIONS | 发送提醒通知 |
| SCHEDULE_EXACT_ALARM | 精确时间提醒 |
| USE_EXACT_ALARM | 使用精确闹钟 |
| RECEIVE_BOOT_COMPLETED | 开机恢复提醒 |
| INTERNET | WebDAV 同步 |
| ACCESS_NETWORK_STATE | 同步前检查网络 |
| WAKE_LOCK | 闹钟唤醒 |
| FOREGROUND_SERVICE | 常驻通知服务 |
| FOREGROUND_SERVICE_DATA_SYNC | 常驻通知服务类型 |
| REQUEST_IGNORE_BATTERY_OPTIMIZATIONS | 后台保活 |
| BIND_ACCESSIBILITY_SERVICE | 无障碍保活（可选） |

---

## 农历闰月规则

- 闰月数据全部来自 lunar-java 的 `LunarYear.getLeapMonth`，**不维护任何硬编码年份表**
- **普通月事件在闰月年提醒两次**：以「六月十六」为例，闰六月年先过普通六月十六，
  再过闰六月十六，两次都会倒计时与提醒；两次都过完才轮到下一农历年。非闰月年只有一次。
- **闰月事件在无闰月年份退回普通月**：如「闰六月十六」在 2026 年（无闰六月）
  按普通六月十六提醒 —— 实际生活里没有闰月，普通月照样要过。
- 添加事件时，选择器的月份列表来自所选年份的真实月份，因此无法选出不存在的闰月
- 农历三十遇到只有 29 天的月份时，顺延到下一个该月有 30 天的年份
- **闰月那天会在卡片上显示「闰」角标**，提醒通知会标注「（闰六月十六）」，让用户明白为什么一年出现两次

---

## 日期格式说明

| 格式 | 示例 | 说明 |
|------|------|------|
| 公历完整 | `2025-12-18` | 年-月-日 |
| 忽略年份 | `12-18` | 月-日，每年循环 |
| 农历普通月 | `lunar:2025-06-16` | 农历六月十六 |
| 农历闰月 | `lunar:2025-L06-16` | 农历闰六月十六 |
| 忽略年份农历 | `lunar:06-16` | 每年循环（闰月年两次） |
| 忽略年份农历闰月 | `lunar:L06-16` | 每年循环（无闰月年退回普通月） |

---

## 数据兼容性说明

**升级不会丢数据。** 旧版本存下的 JSON 缺少新增字段（`note` / `reminderEnabled` /
`reminderDaysBefore` / `reminderTone` / `mode`）时，`EventJson` 会逐字段兜底：

- 缺失或为 `null` → 回落到默认值
- 枚举支持三种历史写法：名字面量 `"COUNT_UP"`、整数 `1`、无法识别时回落默认
- JSON 整体损坏 → 返回空列表，不崩溃

这一点很关键：Gson 反序列化 Kotlin data class 时会**绕过构造器**，Kotlin 里写的
参数默认值不会生效。若不显式兜底，旧数据读出来新字段就是 `null`，渲染时一访问就崩溃。

---

## 测试

共 **74 个单元测试**，覆盖核心纯逻辑：

| 测试文件 | 用例数 | 覆盖内容 |
|----------|--------|----------|
| `CountdownTest` | 26 | 公历/农历/闰月顺延/2月29日/跨年循环 |
| `EventFeaturesTest` | 21 | 正数模式、提醒档位覆盖、文案语气、枚举还原 |
| `EventJsonCompatTest` | 10 | 旧数据兼容、字段缺失/null/损坏 JSON |
| `SortPartitionTest` | 9 | 倒数升序、正数降序、分组拼接、null 兜底 |
| `ApproxHumanizeTest` | 8 | 天数换算（含 0~20000 天全量扫描验证） |

```bash
./gradlew testDebugUnitTest
```

---

## 许可证

本项目采用 MIT 许可证 — 详见 [LICENSE](LICENSE) 文件

## 开发者

**胡生** — 项目创建者和主要开发者

## 贡献

欢迎提交 Issue 和 Pull Request！

---

*让每个重要的日子都不被遗忘*
