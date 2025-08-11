@echo off
echo 正在初始化Git仓库...

REM 初始化Git仓库
git init

REM 添加远程仓库
git remote add origin https://github.com/hc353167950/xiaomaotai.git

REM 添加所有文件到暂存区
git add .

REM 提交代码
git commit -m "Initial commit: 小茅台纪念日APP v1.0

- 完整的Android纪念日提醒应用
- 支持农历和公历日期
- 三重提醒系统(7天前/1天前/当天)
- Material Design 3 UI设计
- 云端同步和本地存储
- 无水印蓝色日历图标
- 正式版签名配置"

REM 设置主分支
git branch -M main

REM 推送到远程仓库
git push -u origin main

echo Git仓库初始化完成！
pause
