# 小茅台纪念日 APP

一个简洁优雅的 Android 纪念日提醒应用，帮助您记住生活中的每一个重要时刻。

**数据完全保存在本机**，可选通过 WebDAV（坚果云 / Nextcloud / 群晖等）手动备份与同步，不依赖任何自建账号系统。

## 功能特色

### 纪念日管理
- 添加、编辑、删除纪念日事件
- 支持公历日期（如：2025-12-18）
- 支持农历日期，包括闰月（如：农历六月十六、农历闰六月初一）
- 支持忽略年份格式，每年循环提醒（如：12-18）
- 随机卡片背景样式，视觉效果丰富

### 智能提醒系统
- **提前 7 天提醒** — 早上 8:00
- **提前 1 天提醒** — 早上 8:00
- **当天提醒** — 凌晨 0:00、早上 8:00、中午 12:00
- 支持精确闹钟，确保提醒准时送达
- 开机自动恢复提醒

### 常驻通知栏
- 显示最近的纪念日事件和倒计时天数
- 支持同一天多个事件显示
- 零点自动更新天数
- 点击通知直接进入 APP

### 排序与搜索
- **自动排序**：按距离下次纪念日的天数升序排列
- **手动排序**：拖拽自定义排序，关闭自动排序后生效
- 支持正序、倒序快速排列
- 快速搜索纪念日名称，实时过滤

### WebDAV 同步（手动触发）
在「我的」页面配置 WebDAV 服务器后，可使用三个功能：

| 功能 | 行为 |
|------|------|
| **备份到云端** | 本地全部数据覆盖云端 |
| **从云端恢复** | 云端全部数据覆盖本地（覆盖前自动保存本地快照，保留最近 3 份） |
| **智能合并** | 云端与本地取并集，按「名称 + 日期」去重，两边都不丢；合并前显示预演结果供确认 |

- 云端为单文件快照：`<你的目录>/xiaomaotai/events.json`
- 凭据使用 `EncryptedSharedPreferences`（Android Keystore 托管主密钥）加密保存，不落明文
- 典型配置：坚果云 `https://dav.jianguoyun.com/dav/`（密码请填「应用密码」）

### 隐私保护
- 支持隐藏最近任务（从任务列表中隐藏 APP）
- 双击返回退出应用

## 技术栈

- **开发语言**：Kotlin
- **UI 框架**：Jetpack Compose + Material Design 3
- **本地存储**：SharedPreferences（JSON 格式）
- **云端同步**：WebDAV（Ktor + OkHttp 引擎）
- **农历计算**：lunar-java 1.7.7
- **最低支持**：Android 8.0 (API 26)
- **目标版本**：Android 14 (API 34)

## 项目结构

```
app/src/main/java/com/example/xiaomaotai/
├── MainActivity.kt              # 主 Activity，导航与首页
├── XiaoMaoTaiApp.kt             # Application：单例装配与启动引导
├── EventStore.kt                # 事件仓库（唯一数据出口，纯本地）
├── Event.kt                     # 事件数据模型
├── Countdown.kt                 # 倒计时深模块（公历/农历/闰月顺延唯一来源）
├── DateParser.kt                # 日期字符串解析
├── LunarCalendarHelper.kt       # 农历转换与闰月查询（数据来自 lunar-java）
├── ValidationUtils.kt           # 输入校验（含农历日期合法性）
├── WebDavClient.kt              # WebDAV 协议层（MKCOL / PUT / GET）
├── WebDavSync.kt                # 三个同步功能：备份 / 恢复 / 智能合并
├── WebDavCredentials.kt         # 凭据加密存储
├── ReminderSchedule.kt          # 提醒时刻表（唯一来源）
├── ReminderManager.kt           # AlarmManager 调度
├── ReminderNotifier.kt          # 通知构建
├── ReminderReceiver.kt          # 闹钟广播接收
├── ReminderWorker.kt            # WorkManager 周期补漏
├── PersistentNotificationService.kt  # 常驻通知前台服务
├── AppSettings.kt               # 设置读写
├── PermissionManager.kt         # 权限管理
└── ui/
    ├── theme/                   # 主题与 UI 风格
    └── components/              # UI 组件
        ├── ProfileScreen.kt     # 「我的」：WebDAV 配置与同步
        ├── EventItem.kt         # 事件卡片
        ├── EventDialog.kt       # 添加/编辑弹窗
        ├── CustomDatePicker.kt  # 滚轮日期选择器
        ├── SortScreen.kt        # 拖拽排序
        └── ...
```

## 构建说明

### 环境要求
- Android Studio Hedgehog | 2023.1.1 或更高版本
- JDK 17
- Android SDK 34
- Gradle 8.13

### 构建命令

```bash
# 构建 Debug 版本
./gradlew assembleDebug

# 运行单元测试
./gradlew testDebugUnitTest

# 构建 Release 版本（需要签名配置）
./gradlew assembleRelease
```

### 签名配置

Release 版本需要签名密钥：
- 密钥文件位置：`app/xiaomaotai-release-key.jks`
- 密码通过 `gradle.properties` 的 `RELEASE_KEY_PASSWORD` / `RELEASE_STORE_PASSWORD` 提供
- CI 通过 GitHub Secrets 注入（见 `.github/workflows/auto-tag.yml`）

## 权限说明

| 权限 | 用途 |
|------|------|
| POST_NOTIFICATIONS | 发送提醒通知 |
| SCHEDULE_EXACT_ALARM | 精确时间提醒 |
| USE_EXACT_ALARM | 使用精确闹钟 |
| RECEIVE_BOOT_COMPLETED | 开机恢复提醒 |
| INTERNET | WebDAV 同步 |
| FOREGROUND_SERVICE | 常驻通知服务 |
| REQUEST_IGNORE_BATTERY_OPTIMIZATIONS | 后台保活 |

## 农历闰月规则

- 闰月数据全部来自 lunar-java 的 `LunarYear.getLeapMonth`，**不维护任何硬编码年份表**
- **普通月事件在闰月年提醒两次**：以「六月十六」为例，闰六月年先过普通六月十六，
  再过闰六月十六，两次都会倒计时与提醒；两次都过完才轮到下一农历年。非闰月年只有一次。
- **闰月事件在无闰月年份退回普通月**：如「闰六月十六」在 2026 年（无闰六月）
  按普通六月十六提醒 —— 实际生活里没有闰月，普通月照样要过。
- 添加事件时，选择器的月份列表来自所选年份的真实月份，因此无法选出不存在的闰月
- 农历三十遇到只有 29 天的月份时，顺延到下一个该月有 30 天的年份
- 提醒通知会标注「（闰六月十六）」，让用户知道这是一年中的第二次

## 日期格式说明

| 格式 | 示例 | 说明 |
|------|------|------|
| 公历完整 | 2025-12-18 | 年-月-日 |
| 忽略年份 | 12-18 | 月-日，每年循环 |
| 农历普通月 | lunar:2025-06-16 | 农历六月十六 |
| 农历闰月 | lunar:2025-L06-16 | 农历闰六月十六 |
| 忽略年份农历 | lunar:06-16 | 每年循环（闰月年两次） |
| 忽略年份农历闰月 | lunar:L06-16 | 每年循环（无闰月年退回普通月） |

## 许可证

本项目采用 MIT 许可证 — 详见 [LICENSE](LICENSE) 文件

## 开发者

**胡生** — 项目创建者和主要开发者

## 贡献

欢迎提交 Issue 和 Pull Request！

---

*让每个重要的日子都不被遗忘*
