# 小茅台纪念日 APP

一个简洁优雅的Android纪念日提醒应用，帮助您记住重要的日子。

## 📱 功能特色

- **纪念日管理** - 添加、编辑、删除重要纪念日
- **智能提醒** - 7天前、1天前、当天三重提醒
- **农历支持** - 支持农历日期和节日
- **云端同步** - 登录后数据自动同步到云端
- **本地存储** - 未登录也可正常使用，数据保存在本地
- **现代UI** - Material Design 3设计风格

## 🚀 技术栈

- **开发语言**: Kotlin
- **UI框架**: Jetpack Compose
- **架构**: MVVM + Repository模式
- **数据库**: Supabase (云端) + 本地JSON存储
- **最低支持**: Android 8.0 (API 26)
- **目标版本**: Android 14 (API 34)

## 📦 构建说明

### 环境要求
- Android Studio Hedgehog | 2023.1.1 或更高版本
- JDK 17
- Android SDK 34
- Gradle 8.13

### 构建步骤
```bash
# 克隆项目
git clone https://github.com/hc353167950/xiaomaotai.git

# 进入项目目录
cd xiaomaotai

# 构建Debug版本
./gradlew assembleDebug

# 构建Release版本
./gradlew assembleRelease
```

## 🔑 签名配置

Release版本需要签名密钥，请在项目根目录创建签名文件：
- 密钥文件: `app/xiaomaotai-release-key.jks`
- 在`gradle.properties`中配置签名信息

## 📄 许可证

本项目采用MIT许可证 - 详见 [LICENSE](LICENSE) 文件

## 👨‍💻 开发者

**胡生** - 项目创建者和主要开发者

## 🤝 贡献

欢迎提交Issue和Pull Request！

---

*让每个重要的日子都不被遗忘* ❤️
