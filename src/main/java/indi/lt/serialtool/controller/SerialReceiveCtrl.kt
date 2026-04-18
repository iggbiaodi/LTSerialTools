package indi.lt.serialtool.controller

import com.fazecast.jSerialComm.SerialPort
import indi.lt.serialtool.component.*
import indi.lt.serialtool.data.CircularByteBuffer
import indi.lt.serialtool.data.SerialPortSettings
import indi.lt.serialtool.global.ConfigManager
import indi.lt.serialtool.service.AutoSaveService
import indi.lt.serialtool.service.SerialReadService
import indi.lt.serialtool.utils.UIUtil
import javafx.application.Platform
import javafx.beans.Observable
import javafx.beans.value.ObservableValue
import javafx.collections.FXCollections
import javafx.fxml.FXML
import javafx.fxml.FXMLLoader
import javafx.fxml.Initializable
import javafx.scene.control.*
import javafx.scene.layout.BorderPane
import javafx.scene.layout.HBox
import javafx.stage.FileChooser
import javafx.util.Callback
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import java.io.FileWriter
import java.io.IOException
import java.net.URL
import java.util.*

/**
 * 接收模式逻辑
 * @author Nonoas
 * @date 2025/8/22
 * @since 1.0.0
 */
class SerialReceiveCtrl : Initializable {
    private val logger: Logger = LogManager.getLogger(MainController::class.java)

    // === FXML 注入的组件 ===
    @FXML
    private lateinit var rootPane: BorderPane

    @FXML
    private lateinit var menuBar: MenuBar

    @FXML
    private lateinit var lbSerialName: Label

    @FXML
    private lateinit var cbSerialList: SerialPortCombBox

    @FXML
    private lateinit var cbBautRateList: ComboBox<Int>

    @FXML
    private lateinit var textAreaOrigin: PromptInlineCssTextArea

    @FXML
    private lateinit var btnOpenSerial: SerialToggleButton

    @FXML
    private lateinit var cbTimeDisplay: CheckBox

    @FXML
    private lateinit var cbHexDisplay: CheckBox

    @FXML
    private lateinit var cbHighlightKeyword: CheckBox

    @FXML
    private lateinit var tfKeyWord: TextField

    @FXML
    private lateinit var lbRecvBytes: Label

    @FXML
    private lateinit var btnMoreSettings: Button

    // 状态指示灯
    @FXML
    private var statusIndicator: StatusIndicator? = null

    // === 内部变量 ===
    private var serialReadService: SerialReadService? = null
    private var highlighter: InlineCssRegexHighlighter? = null
    private var keyLastSerial: String? = null

    // 串口参数设置
    private var serialPortSettings: SerialPortSettings = SerialPortSettings.createDefault()

    // 自动保存服务
    private var autoSaveService: AutoSaveService? = null

    // 环形缓冲区（用于限制内存中的数据大小）
    private val circularBuffer: CircularByteBuffer = CircularByteBuffer()

    // 接收数据缓存（用于自动保存）
    private val receiveBuffer = StringBuilder()

    override fun initialize(url: URL?, resourceBundle: ResourceBundle?) {
        initBautRateList()
        registerSerialEvent()
        bindFormStatePersistence()
        initCircularBuffer()
        initAutoSaveService()
        initStatusIndicator()
    }

    private fun persistenceScope(): String {
        val scope = keyLastSerial?.takeIf { it.isNotBlank() } ?: "default"
        return "receiveMode.$scope"
    }

    private fun formKey(name: String): String {
        return "${persistenceScope()}.form.$name"
    }

    private fun serialSettingsKey(): String {
        return "${persistenceScope()}.serialSettings"
    }

    private fun applyPersistedState() {
        serialPortSettings = ConfigManager.getObject(
            serialSettingsKey(),
            SerialPortSettings::class.java,
            SerialPortSettings.createDefault()
        )
        cbSerialList.setSerialPortSettings(serialPortSettings)
        cbBautRateList.selectionModel.select(serialPortSettings.baudRate)
        cbBautRateList.value = serialPortSettings.baudRate
        restoreFormState()
    }

    private fun registerSerialEvent() {
        initOpenSerialButtonAction()
        initBautRateComboBoxAction()
        initMoreSettingsButtonAction()
    }

    private fun restoreFormState() {
        cbHexDisplay.isSelected = ConfigManager.get(formKey("hexDisplay"), Boolean::class.java, false)
        cbTimeDisplay.isSelected = ConfigManager.get(formKey("timeDisplay"), Boolean::class.java, false)
        cbHighlightKeyword.isSelected = ConfigManager.get(formKey("highlightKeyword"), Boolean::class.java, false)
        tfKeyWord.text = ConfigManager.get(formKey("keyword"), "")
    }

    private fun bindFormStatePersistence() {
        cbHexDisplay.selectedProperty().addListener { _, _, newVal ->
            ConfigManager.put(formKey("hexDisplay"), newVal.toString())
        }
        cbTimeDisplay.selectedProperty().addListener { _, _, newVal ->
            ConfigManager.put(formKey("timeDisplay"), newVal.toString())
        }
        cbHighlightKeyword.selectedProperty().addListener { _, _, newVal ->
            ConfigManager.put(formKey("highlightKeyword"), newVal.toString())
        }
        tfKeyWord.textProperty().addListener { _, _, newVal ->
            ConfigManager.put(formKey("keyword"), newVal ?: "")
        }
    }

    /**
     * 初始化更多设置按钮动作
     */
    private fun initMoreSettingsButtonAction() {
        btnMoreSettings.setOnAction {
            showMoreSettings()
        }
    }

    /**
     * 显示更多设置对话框
     */
    fun showMoreSettings() {
        try {
            // 加载对话框 FXML
            val loader = FXMLLoader(javaClass.getResource("/fxml/serial-settings-dialog.fxml"))
            val dialogPane = loader.load<DialogPane>()

            // 获取控制器
            val dialogCtrl = loader.getController<SerialSettingsDialogCtrl>()

            // 设置当前设置
            dialogCtrl.setSettings(serialPortSettings)

            // 创建对话框
            val dialog = Dialog<SerialPortSettings>()
            dialog.dialogPane = dialogPane
            dialog.title = "串口参数设置"
            dialog.headerText = "自定义串口参数"

            // 注意：FXML 中已经定义了按钮，不需要再次添加

            // 处理 OK 按钮
            dialog.resultConverter = Callback<ButtonType, SerialPortSettings> { buttonType ->
                if (buttonType == ButtonType.OK) {
                    // 从 UI 更新设置
                    dialogCtrl.updateSettingsFromUI()
                    dialogCtrl.getSettings()
                } else {
                    null
                }
            }

            // 显示对话框并等待结果
            dialog.showAndWait().ifPresent { settings ->
                // 保存设置
                serialPortSettings = settings
                ConfigManager.putObject(serialSettingsKey(), settings)

                // 应用设置到串口组件
                cbSerialList.setSerialPortSettings(settings)

                // 同步波特率到主界面下拉框（如果设置中有指定波特率）
                cbBautRateList.selectionModel.select(settings.baudRate)

                // 显示成功提示
                UIUtil.showToast("串口参数已更新")
            }

        } catch (e: Exception) {
            logger.error("显示串口设置对话框失败", e)
            UIUtil.showToast("打开设置对话框失败")
        }
    }

    /**
     * 恢复自动滚动
     */
    @FXML
    private fun restoreScrolling() {
        textAreaOrigin.isAutoScroll = true
    }

    @FXML
    private fun clearLogs() {
        textAreaOrigin.area.clear()
        circularBuffer.clear()
        receiveBuffer.setLength(0)
        serialReadService?.resetRecvBytesCount()
        lbRecvBytes.text = "0 B"
        statusIndicator?.setNormal()
    }

    @FXML
    private fun saveOriginLogs() {
        logger.info("saveOriginLogs")

        val content = textAreaOrigin.text
        if (content.isNullOrEmpty()) {
            logger.info("没有日志内容可保存")
            return
        }

        val fileChooser = FileChooser().apply {
            title = "保存日志文件"
            extensionFilters.addAll(
                FileChooser.ExtensionFilter("文本文件", "*.txt"), FileChooser.ExtensionFilter("所有文件", "*.*")
            )
        }

        val file = fileChooser.showSaveDialog(textAreaOrigin.scene.window) ?: return

        try {
            FileWriter(file, false).use { writer ->
                writer.write(content)
                logger.info("日志已保存到: ${file.absolutePath}")
            }
        } catch (e: IOException) {
            logger.error("保存日志失败", e)
        }
    }

    @FXML
    private fun saveFilterLogs() {
        logger.info("saveFilterLogs")
    }

    fun initSerialComboBoxAction() {
        cbSerialList.init(
            keyLastSerial,
            { UIUtil.getSelectedInt(cbBautRateList, 115200) },
            btnOpenSerial.selectedProperty(),
            SerialPort.TIMEOUT_READ_SEMI_BLOCKING,
            getReceiveTimeoutMs()
        )
    }

    private fun initOpenSerialButtonAction() {
        cbSerialList.disableProperty().bind(btnOpenSerial.disableProperty())
        btnOpenSerial.selectedProperty().addListener { _: ObservableValue<out Boolean>?, _: Boolean?, newVal: Boolean ->
            if (newVal) {
                btnOpenSerial.isDisable = true
                cbSerialList.setTimeoutMillis(getReceiveTimeoutMs())
                cbSerialList.openSelectedSerial()
            } else {
                closeSelectSerial()
            }
        }
        cbSerialList.setOnOpenSucceed {
            btnOpenSerial.isDisable = false
            highlighter = InlineCssRegexHighlighter(textAreaOrigin).apply {
                patternTextProperty().bind(tfKeyWord.textProperty())
            }
            // 清空之前的缓冲区
            circularBuffer.clear()
            textAreaOrigin.area.clear()

            serialReadService = SerialReadService(
                cbSerialList.selectedPort,
                textAreaOrigin,
                cbTimeDisplay.selectedProperty(),
                cbHexDisplay.selectedProperty(),
                getReceiveTimeoutMs()
            ) { highlighter?.schedule() }.also {
                it.setOnRecvBytesChanged { bytes ->
                    Platform.runLater {
                        lbRecvBytes.text = formatBytes(bytes)
                    }
                }
                // 禁用内部文本追加，改为通过回调管理
                it.setInternalAppendEnabled(false)
                // 设置数据接收回调，用于自动保存和环形缓冲区
                it.setOnDataReceived { data ->
                    // 添加到环形缓冲区
                    circularBuffer.append(data)
                    // 更新文本区域
                    Platform.runLater {
                        textAreaOrigin.setText(circularBuffer.content)
                        highlighter?.schedule()
                    }
                    // 添加到自动保存队列（移除换行符用于日志记录）
                    val logData = data.trimEnd('\n', '\r')
                    if (logData.isNotEmpty()) {
                        autoSaveService?.appendData(logData)
                    }
                }
                it.start()
            }
            // 如果自动保存启用，确保服务在运行
            if (AutoSaveService.isAutoSaveEnabled()) {
                autoSaveService?.restart()
            }
        }
        cbSerialList.setOnOpenFailed {
            btnOpenSerial.isDisable = false
            btnOpenSerial.isSelected = false
        }
    }

    private fun initBautRateComboBoxAction() {
        cbBautRateList.selectionModel.selectedItemProperty().addListener { _: Observable?, _: Int?, newVal: Int? ->
            if (newVal != null) {
                serialPortSettings.baudRate = newVal
                cbSerialList.setSerialPortSettings(serialPortSettings)
                ConfigManager.putObject(serialSettingsKey(), serialPortSettings)
            }
            onBaudRateChanged()
        }
    }

    private fun onBaudRateChanged() {
        if (!cbSerialList.isActive) {
            return
        }
        closeSelectSerial()
        cbSerialList.setTimeoutMillis(getReceiveTimeoutMs())
        cbSerialList.openSelectedSerial()
    }

    private fun getReceiveTimeoutMs(): Int {
        val rawTimeout = ConfigManager.get(
            ConfigManager.KEY_RECEIVE_TIMEOUT_MS,
            ConfigManager.DEFAULT_RECEIVE_TIMEOUT_MS.toString()
        )
        return try {
            val timeout = rawTimeout.toInt()
            if (timeout > 0) timeout else ConfigManager.DEFAULT_RECEIVE_TIMEOUT_MS
        } catch (_: NumberFormatException) {
            ConfigManager.DEFAULT_RECEIVE_TIMEOUT_MS
        }
    }

    private fun closeSelectSerial() {
        // 先取消读取服务，避免先关串口导致 read 线程报误错误日志
        serialReadService?.cancel()
        serialReadService = null
        // 停止自动保存服务并刷新数据
        autoSaveService?.flush()
        cbSerialList.closeSelectSerial()
    }

    /**
     * 初始化状态指示灯
     */
    private fun initStatusIndicator() {
        // 查找或创建状态指示灯
        Platform.runLater {
            val parent = lbRecvBytes.parent
            if (parent is HBox) {
                // 如果还没有状态指示灯，创建一个
                if (statusIndicator == null) {
                    statusIndicator = StatusIndicator()
                }
                // 插入到接收量标签之前
                val index = parent.children.indexOf(lbRecvBytes)
                if (index >= 0 && !parent.children.contains(statusIndicator)) {
                    parent.children.add(index, statusIndicator)
                    parent.children.add(index + 1, Label("  ")) // 添加间距
                }
            }
        }
    }

    /**
     * 初始化环形缓冲区
     */
    private fun initCircularBuffer() {
        // 从配置读取缓冲区容量
        val size = MainController.getBufferCapacity()
        val unit = MainController.getBufferCapacityUnit()
        val capacityBytes = if (unit.equals("KB", ignoreCase = true)) size * 1024L else size * 1024L * 1024L

        circularBuffer.setCapacity(capacityBytes)

        // 设置溢出回调
        circularBuffer.setOverflowCallback { isOverflow ->
            Platform.runLater {
                statusIndicator?.setOverflow(isOverflow)
            }
        }
    }

    /**
     * 初始化自动保存服务
     */
    private fun initAutoSaveService() {
        autoSaveService = AutoSaveService()

        // 如果自动保存已启用，启动服务
        if (AutoSaveService.isAutoSaveEnabled()) {
            autoSaveService?.start()
        }
    }

    /**
     * 更新自动保存服务状态
     */
    fun updateAutoSaveService(enabled: Boolean) {
        if (enabled) {
            if (autoSaveService?.state != javafx.concurrent.Worker.State.RUNNING) {
                autoSaveService?.restart()
            }
        } else {
            autoSaveService?.cancel()
        }
    }

    /**
     * 更新缓冲区容量
     */
    fun updateBufferCapacity(capacityBytes: Long) {
        circularBuffer.setCapacity(capacityBytes)
    }

    /**
     * 获取原始数据
     */
    fun getOriginData(): String {
        return textAreaOrigin.text ?: ""
    }

    private fun initBautRateList() {
        val baudRates = FXCollections.observableArrayList(
            1200, 2400, 4800, 9600, 38400, 57600, 115200, 230400, 1500000, 2000000, 3000000
        )

        cbBautRateList.items = baudRates
        val baudRate = serialPortSettings.baudRate
        cbBautRateList.selectionModel.select(baudRate)
        cbBautRateList.value = baudRate
    }

    fun setSerialName(serialName: String?) {
        lbSerialName.text = serialName
    }

    fun setKeyLastSerial(keyLastSerial: String?) {
        this.keyLastSerial = keyLastSerial
        applyPersistedState()
    }

    fun persistFormStateToConfig() {
        ConfigManager.put(formKey("hexDisplay"), cbHexDisplay.isSelected.toString())
        ConfigManager.put(formKey("timeDisplay"), cbTimeDisplay.isSelected.toString())
        ConfigManager.put(formKey("highlightKeyword"), cbHighlightKeyword.isSelected.toString())
        ConfigManager.put(formKey("keyword"), tfKeyWord.text ?: "")

        cbBautRateList.value?.let {
            try {
                serialPortSettings.baudRate = it
            } catch (ignore: NumberFormatException) {

            }
        }
        ConfigManager.putObject(serialSettingsKey(), serialPortSettings)
    }

    /**
     * 将字节数格式化为人类可读的字符串
     */
    private fun formatBytes(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> String.format("%.2f KB", bytes / 1024.0)
            bytes < 1024 * 1024 * 1024 -> String.format("%.2f MB", bytes / (1024.0 * 1024.0))
            else -> String.format("%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0))
        }
    }
}
