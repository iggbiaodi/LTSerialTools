package indi.lt.serialtool.component

import com.fazecast.jSerialComm.SerialPort
import github.nonoas.jfx.flat.ui.AppState
import github.nonoas.jfx.flat.ui.concurrent.TaskHandler
import github.nonoas.jfx.flat.ui.stage.ToastQueue
import indi.lt.serialtool.data.SerialPortSettings
import indi.lt.serialtool.global.ConfigManager
import javafx.beans.property.BooleanProperty
import javafx.beans.property.SimpleBooleanProperty
import javafx.beans.property.SimpleObjectProperty
import javafx.beans.value.ObservableValue
import javafx.event.EventHandler
import javafx.scene.control.ComboBox
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import java.util.*
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.function.Supplier
import kotlin.math.min

/**
 * @author Nonoas
 * @date 2025/9/16
 * @since 1.0.0
 */
class SerialPortCombBox : ComboBox<String?>() {
    private val LOG: Logger = LogManager.getLogger(SerialPortCombBox::class.java)

    var selectedPort: SerialPort? = null
        private set

    private var keyLastSerial: String? = null

    private var onOpenSucceed: Runnable? = null
    private var onOpenFailed: Runnable? = null

    /**
     * 启用状态，启用状态下，切换串口会自动关闭上一个，并打开下一个串口
     */
    private val activeProperty = SimpleBooleanProperty(false)

    /**
     * 波特率获取接口
     */
    private var baudRateSupplier: Supplier<Int>? = null

    /**
     * 串口参数设置
     */
    private val serialPortSettingsProperty = SimpleObjectProperty<SerialPortSettings>(SerialPortSettings())

    private var timeOutMode = 0
    private var timeOutMillionTime = 0

    /**
     * 初始化串口下拉框
     *
     * @param keyLastSerial    最后一次选中的串口配置 KEY
     * @param baudRateSupplier 波特率提供方法
     * @param activeProperty   启用状态绑定
     * @param timeOutMode      超时模式
     * @param settings         串口参数设置（可选，默认为空，使用内部默认值）
     */
    fun init(
        keyLastSerial: String?,
        baudRateSupplier: Supplier<Int>?,
        activeProperty: BooleanProperty,
        timeOutMode: Int,
        timeOutMillionTime: Int = 0,
        settings: SerialPortSettings? = null
    ) {
        this.timeOutMode = timeOutMode
        this.timeOutMillionTime = timeOutMillionTime
        this.keyLastSerial = Objects.requireNonNull(keyLastSerial)
        this.baudRateSupplier = baudRateSupplier
        this.activeProperty.bind(activeProperty)

        // 如果有传入设置，则使用传入的设置
        if (settings != null) {
            this.serialPortSettingsProperty.set(settings)
        }

        // 串口下拉框初始化
        TaskHandler<SerialPortData>()
            .whenCall {
                val lastSerial = ConfigManager.get(keyLastSerial, null)
                val commPorts = SerialPort.getCommPorts()
                SerialPortData(commPorts, lastSerial)
            }
            .andThen { data: SerialPortData ->
                // 清空列表
                items.clear()

                for (port in data.serialPorts) {
                    items.add(port.systemPortName + " - " + port.descriptivePortName)
                }
                // 根據實際項目數量設定可見行數
                val itemCount = items.size
                // 設定一個上限，例如10行
                visibleRowCount = min(itemCount.toDouble(), 5.0).toInt()
                if (data.lastSerial != null && items.contains(data.lastSerial)) {
                    selectionModel.select(data.lastSerial)
                } else if (!items.isEmpty()) {
                    selectionModel.selectFirst()
                }
            }
            .handle()

        // 展开下拉框时刷新列表
        onShowing = EventHandler { refreshSerialList() }

        // 选中串口时自动打开
        selectionModel.selectedItemProperty()
            .addListener { _: ObservableValue<out String?>?, oldVal: String?, newVal: String? ->
                if (!keyLastSerial.isNullOrBlank() && !newVal.isNullOrBlank()) {
                    ConfigManager.put(keyLastSerial, newVal)
                }
                // 如果未激活，则不做处理
                if (!activeProperty.get() || oldVal == newVal) {
                    return@addListener
                }
                // TODO 以下操作为耗时操作，需要异步出处理
                if (oldVal != null) {
                    closeSelectSerial()
                }
                if (newVal != null) {
                    openSelectedSerial()
                }
            }
    }

    private fun refreshSerialList() {
        // 刷新前记录当前选中的串口（用于后续恢复）
        val currentSelected = value

        TaskHandler<List<String>>().whenCall {
            val serialList: MutableList<String> = ArrayList()
            for (serialPort in serialPorts) {
                serialList.add(serialPort.systemPortName + " - " + serialPort.descriptivePortName)
            }
            serialList
        }.andThen { `val`: List<String> ->
            LOG.debug("读取完成{}", `val`)
            items.clear()
            items.addAll(`val`)

            // 刷新后：如果之前有选中项且仍存在，则恢复选中；否则不自动选中
            if (currentSelected != null && `val`.contains(currentSelected)) {
                value = currentSelected // 恢复之前的选中项
            } else {
                // 首次加载或选中项已消失，可选：不自动选中任何项
                selectionModel.clearSelection()
            }
        }.handle()
    }

    /**
     * 打开用户选择的串口
     */
    @Synchronized
    fun openSelectedSerial() {
        TaskHandler<Boolean>().whenCall {
            var future: Future<Boolean>? = null
            val executor = Executors.newSingleThreadExecutor()
            try {
                LOG.info("尝试打开$value")
                if (items.isEmpty()) {
                    LOG.warn("串口列表为空，无法打开串口")
                    ToastQueue.show(AppState.getStage(), "未检测到串口设备", 800)
                    return@whenCall false
                }

                var selectedSerial = value
                if (selectedSerial.isNullOrEmpty()) {
                    selectedSerial = items[0]
                    value = selectedSerial
                }

                val selectedSerialFinal = selectedSerial
                val ports = SerialPort.getCommPorts()
                val index = selectionModel.selectedIndex

                // 1️⃣ 检查索引合法性
                if (index < 0 || index >= ports.size) {
                    LOG.warn("串口索引超出范围")
                    return@whenCall false
                }

                // 2️⃣ 关闭已有串口
                if (selectedPort != null && selectedPort!!.isOpen) {
                    selectedPort!!.closePort()
                    LOG.info("关闭旧串口")
                }
                selectedPort = ports[index]

                // 获取当前设置
                val settings = serialPortSettingsProperty.get() ?: SerialPortSettings()
                val baudRate = baudRateSupplier?.get() ?: settings.baudRate

                selectedPort!!.setComPortParameters(
                    baudRate,
                    settings.dataBits,
                    settings.stopBits,
                    settings.parity
                )

                // 设置流控
                selectedPort!!.setFlowControl(settings.flowControl)

                // 3️⃣ 设置读写超时模式（保持非阻塞或半阻塞都可以）
                selectedPort!!.setComPortTimeouts(timeOutMode, timeOutMillionTime, timeOutMillionTime)

                // 4️⃣ 异步打开串口 + 超时控制
                future = executor.submit<Boolean> { selectedPort!!.openPort() }

                val opened = future[1000, TimeUnit.MILLISECONDS]

                // 5️⃣ 打印结果并保存配置
                if (opened) {
                    LOG.info("串口已打开: " + selectedPort!!.systemPortName + " @ " + baudRate + " bps, 数据位:" + settings.dataBits + ", 停止位:" + SerialPortSettings.getStopBitsText(settings.stopBits) + ", 校验:" + SerialPortSettings.getParityText(settings.parity))
                    if (!keyLastSerial.isNullOrBlank()) {
                        ConfigManager.put(keyLastSerial, selectedSerialFinal)
                    }
                } else {
                    LOG.error("串口打开失败: " + selectedPort!!.systemPortName)
                }
                return@whenCall opened
            } catch (e: TimeoutException) {
                LOG.warn("串口打开超时: " + selectedPort!!.systemPortName)
                future?.cancel(true)
                return@whenCall false
            } catch (e: Exception) {
                LOG.error("串口打开异常", e)
                return@whenCall false
            } finally {
                executor.shutdown()
            }
        }.andThen { opened: Boolean ->
            if (opened) {
                if (onOpenSucceed != null) {
                    onOpenSucceed!!.run()
                }
                ToastQueue.show(AppState.getStage(), "串口已打开: " + selectedPort!!.systemPortName, 800)
            } else {
                if (onOpenFailed != null) {
                    onOpenFailed!!.run()
                }
                ToastQueue.show(AppState.getStage(), "串口打开失败", 800)
            }
        }.handle()
    }

    val isActive: Boolean
        get() = activeProperty.get()

    fun activePropertyProperty(): SimpleBooleanProperty {
        return activeProperty
    }

    fun setActiveProperty(activeProperty: Boolean) {
        this.activeProperty.set(activeProperty)
    }

    fun setTimeoutMillis(timeoutMs: Int) {
        this.timeOutMillionTime = if (timeoutMs > 0) timeoutMs else 0
    }

    fun closeSelectSerial() {
        TaskHandler<Void?>().whenCall {
            if (null != selectedPort && selectedPort!!.isOpen) {
                selectedPort!!.closePort()
            }
            null
        }.andThen {

        }.handle()
    }

    fun setOnOpenSucceed(onOpenSucceed: Runnable?) {
        this.onOpenSucceed = onOpenSucceed
    }

    fun setOnOpenFailed(onOpenFailed: Runnable?) {
        this.onOpenFailed = onOpenFailed
    }

    /**
     * 获取串口参数设置
     */
    fun getSerialPortSettings(): SerialPortSettings {
        return serialPortSettingsProperty.get() ?: SerialPortSettings()
    }

    /**
     * 设置串口参数设置
     */
    fun setSerialPortSettings(settings: SerialPortSettings) {
        serialPortSettingsProperty.set(settings)
    }

    /**
     * 获取串口参数设置属性
     */
    fun serialPortSettingsProperty(): SimpleObjectProperty<SerialPortSettings> {
        return serialPortSettingsProperty
    }

    internal class SerialPortData(internal val serialPorts: Array<SerialPort>, val lastSerial: String?)
    companion object {
        private val serialPorts: List<SerialPort>
            get() = listOf(*SerialPort.getCommPorts())
    }
}
