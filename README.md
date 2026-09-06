<div align="center">

# 🔌 LTSerialTool

**一款简洁优雅的跨平台串口调试工具**

[![GitHub release](https://img.shields.io/github/v/release/iggbiaodi/LTSerialTools?include_prereleases&color=blue)](https://github.com/iggbiaodi/LTSerialTools/releases)
[![License](https://img.shields.io/github/license/iggbiaodi/LTSerialTools)](LICENSE)
[![Java](https://img.shields.io/badge/Java-17+-orange)](https://www.oracle.com/java/)
[![JavaFX](https://img.shields.io/badge/JavaFX-21-brightgreen)](https://openjfx.io/)
[![Kotlin](https://img.shields.io/badge/Kotlin-1.9+-purple)](https://kotlinlang.org/)

[English](./README_EN.md) | **简体中文**

<img src="src/main/resources/image/wechat-feedback-qr.jpg" alt="微信公众号二维码" width="120"/>

<sub>扫码关注公众号，反馈问题并获取软件更新通知</sub>

</div>

---

## 📋 目录

- [项目简介](#-项目简介)
- [功能特性](#-功能特性)
- [界面预览](#-界面预览)
- [快速开始](#-快速开始)
  - [系统要求](#系统要求)
  - [下载安装](#下载安装)
  - [源码构建](#源码构建)
- [技术栈](#-技术栈)
- [项目结构](#-项目结构)
- [贡献指南](#-贡献指南)
- [许可证](#-许可证)
- [致谢](#-致谢)

---

## 🎯 项目简介

**LTSerialTool** 是一款基于 JavaFX 开发的现代化串口调试工具，专为嵌入式开发和硬件调试场景设计。

项目采用 **Java + Kotlin** 混合开发，界面使用 **AtlantaFX** 主题，提供流畅、美观的用户体验。支持 Windows、Linux、macOS 跨平台运行。

### 核心优势

- 🎨 **现代化 UI** - 采用 AtlantaFX 主题，界面简洁美观
- ⚡ **高性能** - 基于 JavaFX 硬件加速渲染
- 🔧 **功能丰富** - 支持多种波特率、数据位、校验位配置
- 📦 **开箱即用** - 内置 JRE，无需额外安装 Java 环境
- 🌐 **跨平台** - 支持 Windows、Linux、macOS

---

## ✨ 功能特性

| 功能模块 | 描述 | 状态 |
|---------|------|------|
| 🔌 **串口连接** | 自动检测可用串口，支持多种波特率配置 | ✅ |
| 📤 **数据发送** | 支持文本/十六进制发送，可保存常用指令 | ✅ |
| 📥 **数据接收** | 实时接收显示，支持 HEX/ASCII 模式切换 | ✅ |
| 📊 **数据展示** | 支持时间戳、RX/TX 标识，便于调试分析 | ✅ |
| 💾 **日志记录** | 自动保存通信日志到本地文件 | ✅ |
| 🎨 **主题切换** | 支持明亮/暗黑主题切换 | 🚧 |
| ⌨️ **快捷键** | 支持发送快捷键配置 | ✅ |
| 📝 **指令管理** | 支持常用指令的增删改查 | ✅ |

---

## 🖼️ 界面预览

> 截图将在此处展示

<!--
<div align="center">
  <img src="docs/images/screenshot-main.png" alt="主界面" width="800"/>
  <p><i>主界面截图</i></p>
</div>
-->

---

## 🚀 快速开始

### 系统要求

| 项目 | 要求 |
|-----|------|
| **操作系统** | Windows 10+/Linux/macOS 10.14+ |
| **Java 版本** | Java 17 或更高（运行包内置 JRE，无需单独安装） |
| **内存** | 至少 512MB 可用内存 |
| **存储** | 至少 200MB 可用空间 |

### 下载安装

#### 方式一：下载发布版本（推荐）

1. 前往 [Releases](https://github.com/iggbiaodi/LTSerialTools/releases) 页面
2. 下载对应平台的安装包：
   - **Windows**: `LTSerialTool-1.0.0-windows.zip`
   - **Linux**: `LTSerialTool-1.0.0-linux.tar.gz`
   - **macOS**: `LTSerialTool-1.0.0-mac.dmg`
3. 解压/安装后运行即可

#### 方式二：源码构建

```bash
# 克隆仓库
git clone https://github.com/iggbiaodi/LTSerialTools.git
cd LTSerialTool

# 使用 Gradle 构建
./gradlew build

# 运行应用
./gradlew run

# 打包发行版（包含 JRE）
./gradlew packageMyApp
```

构建完成后，发行包位于 `build/LTSerialTool/` 目录下。

---

## 🛠️ 技术栈

### 核心技术

| 技术 | 版本 | 说明 |
|-----|------|------|
| [Java](https://www.oracle.com/java/) | 17+ | 主要开发语言 |
| [Kotlin](https://kotlinlang.org/) | 1.9+ | 辅助开发语言 |
| [JavaFX](https://openjfx.io/) | 21 | GUI 框架 |
| [Gradle](https://gradle.org/) | 8.x | 构建工具 |

### 关键依赖

| 依赖 | 版本 | 用途 |
|-----|------|------|
| [jSerialComm](https://fazecast.github.io/jSerialComm/) | 2.10.4 | 串口通信 |
| [AtlantaFX](https://github.com/mkpaz/atlantafx) | 2.0.1 | UI 主题 |
| [JFX-Flat-UI](https://github.com/nonoas/jfx-flat-ui) | 1.0.3 | 扁平化 UI 组件 |
| [Ikonli](http://kordamp.org/ikonli/) | 12.3.1 | 图标库 |
| [RichTextFX](https://github.com/FXMisc/RichTextFX) | 0.11.1 | 富文本编辑器 |
| [H2 Database](https://www.h2database.com/) | 2.2.220 | 本地数据存储 |
| [Log4j2](https://logging.apache.org/log4j/2.x/) | 2.20.0 | 日志框架 |
| [Gson](https://github.com/google/gson) | 2.11.0 | JSON 处理 |

---

## 📁 项目结构

```
LTSerialTool/
├── src/
│   ├── main/
│   │   ├── java/indi/lt/serialtool/
│   │   │   ├── Main.java                 # 程序入口
│   │   │   ├── SerialApplication.java    # JavaFX 应用启动类
│   │   │   ├── controller/               # 控制器层
│   │   │   ├── service/                  # 业务逻辑层
│   │   │   ├── view/                     # 视图层
│   │   │   ├── data/                     # 数据层
│   │   │   ├── component/                # 自定义组件
│   │   │   ├── utils/                    # 工具类
│   │   │   ├── constant/                 # 常量定义
│   │   │   └── global/                   # 全局配置
│   │   ├── resources/
│   │   │   ├── fxml/                     # FXML 布局文件
│   │   │   ├── css/                      # 样式文件
│   │   │   ├── images/                   # 图片资源
│   │   │   └── log4j2.xml                # 日志配置
│   │   └── kotlin/                       # Kotlin 源码
│   └── test/                             # 测试代码
├── assets/                               # 打包资源
├── build.gradle                          # Gradle 构建脚本
├── settings.gradle                       # Gradle 设置
├── gradle/wrapper/                       # Gradle Wrapper
└── README.md                             # 项目说明
```

---

## 🤝 贡献指南

我们欢迎任何形式的贡献！

### 如何贡献

1. **Fork** 本项目
2. 创建你的特性分支 (`git checkout -b feature/AmazingFeature`)
3. 提交你的修改 (`git commit -m 'Add some AmazingFeature'`)
4. 推送到分支 (`git push origin feature/AmazingFeature`)
5. 打开一个 **Pull Request**

### 贡献规范

- 遵循现有的代码风格
- 添加必要的单元测试
- 更新相关文档
- 确保构建通过 (`./gradlew build`)

## 👥 贡献者

<!-- readme: collaborators,contributors -start -->
<!-- readme: collaborators,contributors -end -->

感谢所有为这个项目做出贡献的开发者！

---

## 📄 许可证

本项目基于 [MIT](LICENSE) 许可证开源。

```
MIT License

Copyright (c) 2025 LTSerialTool Contributors

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.
```

---

## 🙏 致谢

- [jSerialComm](https://fazecast.github.io/jSerialComm/) - 提供优秀的跨平台串口通信库
- [AtlantaFX](https://github.com/mkpaz/atlantafx) - 提供现代化的 JavaFX 主题
- [JavaPackager](https://github.com/fvarrui/JavaPackager) - 提供便捷的 Java 应用打包工具
- [OpenJFX](https://openjfx.io/) - 提供强大的 JavaFX GUI 框架

---

<div align="center">

**[⬆ 返回顶部](#-ltserialtool)**

Made with ❤️ by [LT~](https://github.com/yourusername)

</div>
