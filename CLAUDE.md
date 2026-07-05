# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 项目概述

## 构建和运行

### 构建项目

## 代码架构

### 核心组件

1. **FXML 文件** (`src/main/resources/fxml/`)
    - `main-view.fxml`: 主界面布局
    - `serial-send-pane.fxml`: 发送模式界面
    - `serial-receive-pane.fxml`: 接收模式界面

2. **控制器** (`src/main/java/indi/lt/serialtool/controller/`)
    - `MainController.java`: 主控制器，管理 Tab 切换
    - `SerialSendCtrl.java`: 发送模式控制器
    - `SerialReceiveCtrl.kt`: 接收模式控制器（Kotlin）

3. **服务层** (`src/main/java/indi/lt/serialtool/service/`)
    - `SerialReadService.java`: 串口读取服务
    - `SerialSenderService.java`: 串口发送服务

4. **组件** (`src/main/java/indi/lt/serialtool/component/`)
    - `SerialPortCombBox.kt`: 串口选择下拉框（Kotlin）
    - `SerialToggleButton.java`: 串口开关按钮
    - `PromptInlineCssTextArea.java`: 带内联 CSS 的文本区域

5. **数据模型** (`src/main/java/indi/lt/serialtool/data/`)
    - `SerialPortSettings.java`: 串口参数设置数据类

### 串口通信流程

1. **初始化**:
    - 控制器初始化串口列表和波特率列表
    - 加载历史配置

2. **串口打开**:
    - 通过 `SerialPortCombBox` 选择串口
    - 调用 `SerialPortCombBox.openSelectedSerial()` 打开串口
    - 设置串口参数（波特率、数据位、停止位、校验位、流控）

3. **数据传输**:
    - 发送模式：通过 `SerialSenderService` 发送数据
    - 接收模式：通过 `SerialReadService` 接收数据

### 串口参数设置

项目支持自定义串口参数设置，包括：
- 波特率
- 数据位（5, 6, 7, 8）
- 停止位（1, 1.5, 2）
- 校验位（None, Odd, Even, Mark, Space）
- 流控（None, RTS/CTS, XON/XOFF）

### UI 组件

- `SerialPortCombBox`: 封装串口选择逻辑，支持自定义参数
- `SerialToggleButton`: 封装串口开关按钮，显示打开/关闭状态
- `PromptInlineCssTextArea`: 支持内联 CSS 的高亮显示文本区域

## 开发注意事项

1. **串口操作**:
    - 串口操作是异步的，使用 `TaskHandler` 进行异步处理
    - 串口打开失败会显示 Toast 提示
    - 串口参数设置通过 `SerialPortSettings` 类管理

2. **UI 布局**:
    - 使用 FXML 进行 UI 布局
    - 控制器通过 `@FXML` 注解注入组件
    - 支持独立窗口模式

3. **数据存储**:
    - 使用 `ConfigManager` 保存配置
    - 命令历史通过 `CommandRepository` 管理

4. **Kotlin/Java 混合开发**:
    - 新组件优先使用 Kotlin
    - 控制器根据需要选择 Kotlin 或 Java
    - 注意 Kotlin 和 Java 之间的互操作性


## 已知问题

- 串口操作可能因权限问题失败
- 跨平台串口名称可能不同
- 高波特率可能导致数据丢失

## 固定规则
- 尽量使用纯java代码，禁止添加新的fxml文件
- 新增功能不要影响现有功能，如果需要影响，请向我确认
- 注意不要在javafx的UI线程中执行耗时让我

## Token消耗问题
- 当需要执行消耗大量Token的任务时，通知我确认

## 编译验证
- 当需要执行`gradle`命令进行编译验证时，将需要验证的命令返回给我，不用擅自执行

## 外部规则引用
@RTK.md
