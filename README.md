# 小茅台纪念日 APP

一个简洁优雅的Android纪念日提醒应用，帮助您记住生活中的每一个重要时刻。

## 功能特色

### 纪念日管理
- 添加、编辑、删除纪念日事件
- 支持公历日期（如：2025-12-18）
- 支持农历日期，包括闰月（如：农历六月十六、农历闰六月初一）
- 支持忽略年份格式，每年循环提醒（如：12-18）
- 随机卡片背景样式，视觉效果丰富

### 智能提醒系统
- **提前7天提醒** - 早上8:00
- **提前1天提醒** - 早上8:00
- **当天提醒** - 凌晨0:00、早上8:00、中午12:00
- 支持精确闹钟，确保提醒准时送达
- 开机自动恢复提醒

### 常驻通知栏
- 显示最近的纪念日事件和倒计时天数
- 支持同一天多个事件显示
- 零点自动更新天数
- 点击通知直接进入APP

### 排序功能
- **自动排序**：按距离下次纪念日的天数升序排列
- **手动排序**：拖拽自定义排序，关闭自动排序后生效
- 支持正序、倒序快速排列

### 搜索功能
- 快速搜索纪念日名称
- 实时过滤显示结果

### 云端同步
- 登录后数据自动同步到云端
- 支持多设备数据同步
- 离线模式下数据保存在本地
- 登录后自动合并离线数据

### 隐私保护
- 支持隐藏最近任务（从任务列表中隐藏APP）
- 双击返回退出应用

## 技术栈

- **开发语言**: Kotlin
- **UI框架**: Jetpack Compose + Material Design 3
- **架构模式**: MVVM + Repository
- **云端服务**: Supabase (PostgreSQL + Realtime)
- **本地存储**: SharedPreferences (JSON格式)
- **农历计算**: lunar-java库
- **最低支持**: Android 8.0 (API 26)
- **目标版本**: Android 14 (API 34)

## 项目结构

```
app/src/main/java/com/example/xiaomaotai/
├── MainActivity.kt          # 主Activity，包含导航和首页逻辑
├── DataManager.kt            # 数据管理层，处理本地和云端数据
├── NetworkDataManager.kt     # 网络层，Supabase API调用
├── ReminderManager.kt        # 提醒管理，AlarmManager调度
├── PersistentNotificationService.kt  # 常驻通知前台服务
├── TimeChangedReceiver.kt    # 系统时间变化监听
├── ReminderReceiver.kt       # 提醒广播接收器
├── DateParser.kt             # 统一日期解析工具
├── LunarCalendarHelper.kt    # 农历计算辅助类
├── PermissionManager.kt      # 权限管理
├── Event.kt                  # 事件数据模型
├── User.kt                   # 用户数据模型
└── ui/
    ├── theme/                # 主题配置
    └── components/           # UI组件
        ├── EventItem.kt      # 事件卡片组件
        ├── EventDialog.kt    # 添加/编辑事件弹窗
        ├── SortScreen.kt     # 拖拽排序页面
        ├── ProfileScreen.kt  # 个人中心页面
        └── ...
```

## 构建说明

### 环境要求
- Android Studio Hedgehog | 2023.1.1 或更高版本
- JDK 17
- Android SDK 34
- Gradle 8.13

### Supabase配置

1. 复制配置模板文件：
```bash
cp app/src/main/java/com/example/xiaomaotai/SupabaseConfig.example.kt \
   app/src/main/java/com/example/xiaomaotai/SupabaseConfig.kt
```

2. 填入您的Supabase项目信息：
```kotlin
object SupabaseConfig {
    const val SUPABASE_URL = "your-project-url"
    const val SUPABASE_ANON_KEY = "your-anon-key"
}
```

### 构建命令

```bash
# 克隆项目
git clone https://github.com/hc353167950/xiaomaotai.git
cd xiaomaotai

# 构建Debug版本
./gradlew assembleDebug

# 构建Release版本（需要签名配置）
./gradlew assembleRelease

# 安装到设备
./gradlew installDebug
```

## 签名配置

Release版本需要签名密钥：
- 密钥文件位置: `app/xiaomaotai-release-key.jks`
- 签名信息配置在 `app/build.gradle.kts`

## 权限说明

| 权限 | 用途 |
|------|------|
| POST_NOTIFICATIONS | 发送提醒通知 |
| SCHEDULE_EXACT_ALARM | 精确时间提醒 |
| USE_EXACT_ALARM | 使用精确闹钟 |
| RECEIVE_BOOT_COMPLETED | 开机恢复提醒 |
| INTERNET | 云端数据同步 |
| FOREGROUND_SERVICE | 常驻通知服务 |
| REQUEST_IGNORE_BATTERY_OPTIMIZATIONS | 后台保活 |

## 数据库表结构

### users 表
| 字段 | 类型 | 说明 |
|------|------|------|
| id | uuid | 主键 |
| username | text | 用户名（唯一） |
| nickname | text | 昵称 |
| email | text | 邮箱 |
| password | text | 密码 |
| created_at | timestamp | 创建时间 |

### events 表
| 字段 | 类型 | 说明 |
|------|------|------|
| id | uuid | 主键 |
| user_id | uuid | 用户ID |
| event_name | text | 事件名称 |
| event_date | text | 日期（支持多种格式） |
| sort_order | int | 排序顺序 |
| background_id | int | 背景样式ID（1-10） |
| status | int | 状态（0正常/3已删除） |
| created_at | timestamp | 创建时间 |

## 日期格式说明

| 格式 | 示例 | 说明 |
|------|------|------|
| 公历完整 | 2025-12-18 | 年-月-日 |
| 忽略年份 | 12-18 | 月-日，每年循环 |
| 农历普通月 | lunar:2025-06-16 | 农历六月十六 |
| 农历闰月 | lunar:2025-L06-16 | 农历闰六月十六 |

## 许可证

本项目采用MIT许可证 - 详见 [LICENSE](LICENSE) 文件

## 开发者

**胡生** - 项目创建者和主要开发者

## 贡献

欢迎提交Issue和Pull Request！

---

*让每个重要的日子都不被遗忘*
