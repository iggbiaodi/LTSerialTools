package indi.lt.serialtool.controller

import com.fazecast.jSerialComm.SerialPort
import indi.lt.serialtool.component.InlineCssRegexHighlighter
import indi.lt.serialtool.component.MyStyleClassedTextArea
import indi.lt.serialtool.component.SerialPortCombBox
import indi.lt.serialtool.component.SerialToggleButton
import indi.lt.serialtool.component.StatusIndicator
import indi.lt.serialtool.data.BoundedDisplayBuffer
import indi.lt.serialtool.data.BufferedDisplayLine
import indi.lt.serialtool.data.SerialPortSettings
import indi.lt.serialtool.global.ConfigManager
import indi.lt.serialtool.global.FontSettingsManager
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
import java.util.ResourceBundle
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executors
import java.util.concurrent.ThreadFactory
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * 接收模式逻辑
 */
class SerialReceiveCtrl : Initializable {
    private val logger: Logger = LogManager.getLogger(MainController::class.java)

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
    private lateinit var textAreaOrigin: MyStyleClassedTextArea

    @FXML
    private lateinit var textAreaFilter: MyStyleClassedTextArea

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

    @FXML
    private var statusIndicator: StatusIndicator? = null

    private var serialReadService: SerialReadService? = null
    private var highlighter: InlineCssRegexHighlighter? = null
    private var keyLastSerial: String? = null
    private var serialPortSettings: SerialPortSettings = SerialPortSettings.createDefault()
    private var autoSaveService: AutoSaveService? = null

    private val pendingDisplayLines = ConcurrentLinkedQueue<BufferedDisplayLine>()
    private val uiFlushQueued = AtomicBoolean(false)
    private val filterRebuildRunning = AtomicBoolean(false)
    private val filterRebuildDirty = AtomicBoolean(false)
    private val filterRebuildVersion = AtomicLong(0L)

    private val filterRebuildExecutor = Executors.newSingleThreadExecutor(ThreadFactory { runnable ->
        Thread(runnable, "serial-receive-filter").apply { isDaemon = true }
    })

    private val originBuffer = BoundedDisplayBuffer(1024L)
    private val filterBuffer = BoundedDisplayBuffer(1024L)

    override fun initialize(url: URL?, resourceBundle: ResourceBundle?) {
        highlighter = InlineCssRegexHighlighter(textAreaOrigin)
        textAreaOrigin.maxLines = 0
        textAreaFilter.maxLines = 0
        initBautRateList()
        registerSerialEvent()
        bindFormStatePersistence()
        initDisplayBuffers()
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
        highlighter?.setPatternText(tfKeyWord.text ?: "")
        requestFilterRebuild()
    }

    private fun bindFormStatePersistence() {
        cbHexDisplay.selectedProperty().addListener { _, _, newVal ->
            ConfigManager.put(formKey("hexDisplay"), newVal.toString())
            refreshRenderedAreas()
        }
        cbTimeDisplay.selectedProperty().addListener { _, _, newVal ->
            ConfigManager.put(formKey("timeDisplay"), newVal.toString())
            refreshRenderedAreas()
        }
        cbHighlightKeyword.selectedProperty().addListener { _, _, newVal ->
            ConfigManager.put(formKey("highlightKeyword"), newVal.toString())
        }
        tfKeyWord.textProperty().addListener { _, _, newVal ->
            ConfigManager.put(formKey("keyword"), newVal ?: "")
            highlighter?.setPatternText(newVal ?: "")
            requestFilterRebuild()
        }
    }

    private fun initMoreSettingsButtonAction() {
        btnMoreSettings.setOnAction { showMoreSettings() }
    }

    fun showMoreSettings() {
        try {
            val loader = FXMLLoader(javaClass.getResource("/fxml/serial-settings-dialog.fxml"))
            val dialogPane = loader.load<DialogPane>()
            val dialogCtrl = loader.getController<SerialSettingsDialogCtrl>()
            dialogCtrl.setSettings(serialPortSettings)

            val dialog = Dialog<SerialPortSettings>()
            dialog.dialogPane = dialogPane
            dialog.title = "串口参数设置"
            dialog.headerText = "自定义串口参数"
            FontSettingsManager.configureDialog(dialog)
            dialog.resultConverter = Callback<ButtonType, SerialPortSettings> { buttonType ->
                if (buttonType == ButtonType.OK) {
                    dialogCtrl.updateSettingsFromUI()
                    dialogCtrl.getSettings()
                } else {
                    null
                }
            }

            dialog.showAndWait().ifPresent { settings ->
                serialPortSettings = settings
                ConfigManager.putObject(serialSettingsKey(), settings)
                cbSerialList.setSerialPortSettings(settings)
                cbBautRateList.selectionModel.select(settings.baudRate)
                UIUtil.showToast("串口参数已更新")
            }
        } catch (e: Exception) {
            logger.error("显示串口设置对话框失败", e)
            UIUtil.showToast("打开设置对话框失败")
        }
    }

    @FXML
    private fun restoreScrolling() {
        textAreaOrigin.restoreAutoScrollToEnd()
        textAreaFilter.restoreAutoScrollToEnd()
    }

    @FXML
    private fun clearLogs() {
        pendingDisplayLines.clear()
        filterRebuildVersion.incrementAndGet()
        filterRebuildDirty.set(false)
        originBuffer.clear()
        filterBuffer.clear()
        textAreaOrigin.setText("")
        textAreaFilter.setText("")
        highlighter?.resetTracking()
        serialReadService?.resetRecvBytesCount()
        lbRecvBytes.text = "0 B"
        statusIndicator?.setNormal()
    }

    @FXML
    private fun saveOriginLogs() {
        val content = textAreaOrigin.text
        if (content.isNullOrEmpty()) {
            logger.info("没有日志内容可保存")
            return
        }

        val fileChooser = FileChooser().apply {
            title = "保存日志文件"
            extensionFilters.addAll(
                FileChooser.ExtensionFilter("文本文件", "*.txt"),
                FileChooser.ExtensionFilter("所有文件", "*.*")
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
            prepareReceiveSession()
        }

        cbSerialList.setOnOpenFailed {
            btnOpenSerial.isDisable = false
            btnOpenSerial.isSelected = false
        }
    }

    private fun prepareReceiveSession() {
        pendingDisplayLines.clear()
        filterRebuildVersion.incrementAndGet()
        filterRebuildDirty.set(false)
        originBuffer.clear()
        filterBuffer.clear()
        textAreaOrigin.setText("")
        textAreaFilter.setText("")
        textAreaOrigin.setAutoScroll(true)
        textAreaFilter.setAutoScroll(true)
        lbRecvBytes.text = "0 B"
        statusIndicator?.setNormal()
        highlighter?.setPatternText(tfKeyWord.text ?: "")
        highlighter?.resetTracking()

        serialReadService = SerialReadService(
            cbSerialList.selectedPort,
            textAreaOrigin,
            cbTimeDisplay.selectedProperty(),
            cbHexDisplay.selectedProperty(),
            getReceiveTimeoutMs()
        ) { highlighter?.schedule() }.also { service ->
            service.setInternalAppendEnabled(false)
            service.setOnRecvBytesChanged { bytes ->
                Platform.runLater {
                    lbRecvBytes.text = formatBytes(bytes)
                }
            }
            service.setOnDisplayLineReceived { line ->
                enqueueDisplayLine(line)
                appendAutoSave(line)
            }
            service.start()
        }

        if (AutoSaveService.isAutoSaveEnabled()) {
            autoSaveService?.restart()
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
        serialReadService?.cancel()
        serialReadService = null
        autoSaveService?.flush()
        cbSerialList.closeSelectSerial()
    }

    private fun initStatusIndicator() {
        Platform.runLater {
            val parent = lbRecvBytes.parent
            if (parent is HBox) {
                if (statusIndicator == null) {
                    statusIndicator = StatusIndicator()
                }
                val index = parent.children.indexOf(lbRecvBytes)
                if (index >= 0 && !parent.children.contains(statusIndicator)) {
                    parent.children.add(index, statusIndicator)
                    parent.children.add(index + 1, Label("  "))
                }
            }
        }
    }

    private fun initDisplayBuffers() {
        val capacityBytes = getConfiguredBufferCapacityBytes()
        originBuffer.setCapacity(capacityBytes)
        filterBuffer.setCapacity(capacityBytes)
        originBuffer.setOverflowListener { isOverflow ->
            Platform.runLater {
                statusIndicator?.setOverflow(isOverflow)
            }
        }
    }

    private fun getConfiguredBufferCapacityBytes(): Long {
        val size = MainController.getBufferCapacity()
        val unit = MainController.getBufferCapacityUnit()
        return if (unit.equals("KB", ignoreCase = true)) {
            size * 1024L
        } else {
            size * 1024L * 1024L
        }
    }

    private fun initAutoSaveService() {
        autoSaveService = AutoSaveService()
        if (AutoSaveService.isAutoSaveEnabled()) {
            autoSaveService?.start()
        }
    }

    fun updateAutoSaveService(enabled: Boolean) {
        if (enabled) {
            if (autoSaveService?.state != javafx.concurrent.Worker.State.RUNNING) {
                autoSaveService?.restart()
            }
        } else {
            autoSaveService?.cancel()
        }
    }

    fun updateBufferCapacity(capacityBytes: Long) {
        runOnFx {
            originBuffer.setCapacity(capacityBytes)
            filterBuffer.setCapacity(capacityBytes)
            textAreaOrigin.setLogLines(originBuffer.snapshot(), shouldShowTimestamp(), shouldShowDataType())
            textAreaFilter.setLogLines(filterBuffer.snapshot(), shouldShowTimestamp(), shouldShowDataType())
            highlighter?.resetTracking()
            if (textAreaOrigin.isAutoScroll) {
                textAreaOrigin.restoreAutoScrollToEnd()
            }
            if (textAreaFilter.isAutoScroll) {
                textAreaFilter.restoreAutoScrollToEnd()
            }
            requestFilterRebuild()
        }
    }

    fun dispose() {
        updateAutoSaveService(false)
        closeSelectSerial()
        filterRebuildExecutor.shutdownNow()
    }

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
            } catch (_: NumberFormatException) {
            }
        }
        ConfigManager.putObject(serialSettingsKey(), serialPortSettings)
    }

    private fun enqueueDisplayLine(line: BufferedDisplayLine) {
        pendingDisplayLines.offer(line)
        scheduleUiFlush()
    }

    private fun appendAutoSave(line: BufferedDisplayLine) {
        val logData = line.render(shouldShowTimestamp(), shouldShowDataType()).trimEnd('\n', '\r')
        if (logData.isNotEmpty()) {
            autoSaveService?.appendData(logData)
        }
    }

    private fun scheduleUiFlush() {
        if (!uiFlushQueued.compareAndSet(false, true)) {
            return
        }
        Platform.runLater {
            try {
                flushPendingLines()
            } finally {
                uiFlushQueued.set(false)
                if (pendingDisplayLines.isNotEmpty()) {
                    scheduleUiFlush()
                }
            }
        }
    }

    private fun flushPendingLines() {
        val lines = ArrayList<BufferedDisplayLine>()
        while (true) {
            val line = pendingDisplayLines.poll() ?: break
            lines.add(line)
        }
        if (lines.isEmpty()) {
            return
        }

        val originRemovedLines = ArrayList<BufferedDisplayLine>()
        val originBatch = ArrayList<BufferedDisplayLine>(lines.size)
        val keywords = parseKeywords(tfKeyWord.text)
        val filterActive = keywords.isNotEmpty()
        val rebuildingFilter = filterRebuildRunning.get()
        val filterRemovedLines = ArrayList<BufferedDisplayLine>()
        val filterBatch = ArrayList<BufferedDisplayLine>()

        for (line in lines) {
            originRemovedLines.addAll(originBuffer.append(line).removedLines)
            originBatch.add(line)

            if (!filterActive) {
                continue
            }
            if (rebuildingFilter) {
                filterRebuildDirty.set(true)
                continue
            }
            if (line.matchesAny(keywords)) {
                filterRemovedLines.addAll(filterBuffer.append(line).removedLines)
                filterBatch.add(line)
            }
        }

        applyAppend(textAreaOrigin, originBatch, originRemovedLines, cbHighlightKeyword.isSelected)

        if (!filterActive) {
            if (textAreaFilter.text?.isNotEmpty() == true) {
                filterBuffer.clear()
                textAreaFilter.setText("")
            }
        } else if (rebuildingFilter) {
            filterRebuildDirty.set(true)
        } else {
            applyAppend(textAreaFilter, filterBatch, filterRemovedLines, false)
        }
    }

    private fun applyAppend(
        area: MyStyleClassedTextArea,
        batch: List<BufferedDisplayLine>,
        removedLines: List<BufferedDisplayLine>,
        applyHighlight: Boolean
    ) {
        val removedChars = removedLines.sumOf { it.getRenderedCharLength(shouldShowTimestamp(), shouldShowDataType()) }
        if (removedChars > 0) {
            val currentLength = area.area.length
            area.replaceText(0, removedChars.coerceAtMost(currentLength), "")
        }
        if (batch.isEmpty()) {
            return
        }

        val start = area.area.length
        area.appendLogLines(batch, shouldShowTimestamp(), shouldShowDataType())
        val end = area.area.length
        if (applyHighlight) {
            highlighter?.highlightNewAppend(start, end)
        }
        if (area.isAutoScroll) {
            area.restoreAutoScrollToEnd()
        }
    }

    private fun requestFilterRebuild() {
        runOnFx {
            flushPendingLines()
            val version = filterRebuildVersion.incrementAndGet()
            val keywords = parseKeywords(tfKeyWord.text)
            if (keywords.isEmpty()) {
                filterRebuildDirty.set(false)
                filterBuffer.clear()
                textAreaFilter.setText("")
                return@runOnFx
            }
            startFilterRebuild(version, keywords)
        }
    }

    private fun startFilterRebuild(version: Long, keywords: List<String>) {
        if (!filterRebuildRunning.compareAndSet(false, true)) {
            filterRebuildDirty.set(true)
            return
        }

        val snapshot = originBuffer.snapshot()
        filterRebuildExecutor.execute {
            val matched = ArrayList<BufferedDisplayLine>()
            for (line in snapshot) {
                if (line.matchesAny(keywords)) {
                    matched.add(line)
                }
            }

            Platform.runLater {
                try {
                    val currentKeywords = parseKeywords(tfKeyWord.text)
                    if (version == filterRebuildVersion.get() && currentKeywords == keywords) {
                        filterBuffer.replaceAll(matched)
                        textAreaFilter.setLogLines(matched, shouldShowTimestamp(), shouldShowDataType())
                        if (textAreaFilter.isAutoScroll) {
                            textAreaFilter.restoreAutoScrollToEnd()
                        }
                    } else {
                        filterRebuildDirty.set(true)
                    }
                } finally {
                    filterRebuildRunning.set(false)
                    if (filterRebuildDirty.getAndSet(false) || version != filterRebuildVersion.get()) {
                        requestFilterRebuild()
                    }
                }
            }
        }
    }

    private fun parseKeywords(rawKeywords: String?): List<String> {
        if (rawKeywords.isNullOrBlank()) {
            return emptyList()
        }
        val parts = rawKeywords.split("\\|".toRegex())
        val keywords = ArrayList<String>(parts.size)
        for (part in parts) {
            val keyword = part.trim()
            if (keyword.isNotEmpty()) {
                keywords.add(keyword)
            }
        }
        return keywords
    }

    private fun refreshRenderedAreas() {
        runOnFx {
            flushPendingLines()
            textAreaOrigin.setLogLines(originBuffer.snapshot(), shouldShowTimestamp(), shouldShowDataType())
            if (textAreaOrigin.isAutoScroll) {
                textAreaOrigin.restoreAutoScrollToEnd()
            }
            textAreaFilter.setLogLines(filterBuffer.snapshot(), shouldShowTimestamp(), shouldShowDataType())
            if (textAreaFilter.isAutoScroll) {
                textAreaFilter.restoreAutoScrollToEnd()
            }
            requestFilterRebuild()
        }
    }

    private fun shouldShowTimestamp(): Boolean = cbTimeDisplay.isSelected

    private fun shouldShowDataType(): Boolean = cbHexDisplay.isSelected

    private fun runOnFx(runnable: () -> Unit) {
        if (Platform.isFxApplicationThread()) {
            runnable()
        } else {
            Platform.runLater(runnable)
        }
    }

    private fun formatBytes(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> String.format("%.2f KB", bytes / 1024.0)
            bytes < 1024 * 1024 * 1024 -> String.format("%.2f MB", bytes / (1024.0 * 1024.0))
            else -> String.format("%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0))
        }
    }
}
