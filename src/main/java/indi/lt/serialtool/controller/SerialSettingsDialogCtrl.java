package indi.lt.serialtool.controller;

import com.fazecast.jSerialComm.SerialPort;
import indi.lt.serialtool.data.SerialPortSettings;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;

import java.net.URL;
import java.util.Arrays;
import java.util.List;
import java.util.ResourceBundle;

/**
 * 串口参数设置对话框控制器
 */
public class SerialSettingsDialogCtrl implements Initializable {

    @FXML
    private Spinner<Integer> spBaudRate;

    @FXML
    private ComboBox<Integer> cbDataBits;

    @FXML
    private ComboBox<String> cbStopBits;

    @FXML
    private ComboBox<String> cbParity;

    @FXML
    private ComboBox<String> cbFlowControl;

    // 当前设置
    private SerialPortSettings settings;

    // 常用的波特率列表
    private static final List<Integer> COMMON_BAUD_RATES = Arrays.asList(
            1200, 2400, 4800, 9600, 14400, 19200, 38400, 57600, 115200,
            230400, 460800, 921600, 1500000, 2000000, 3000000
    );

    // 数据位选项
    private static final List<Integer> DATA_BITS_OPTIONS = Arrays.asList(5, 6, 7, 8);

    // 停止位选项（显示文本）
    private static final List<String> STOP_BITS_OPTIONS = Arrays.asList("1", "1.5", "2");

    // 校验位选项（显示文本）
    private static final List<String> PARITY_OPTIONS = Arrays.asList("None", "Odd", "Even", "Mark", "Space");

    // 流控选项（显示文本）
    private static final List<String> FLOW_CONTROL_OPTIONS = Arrays.asList("None", "RTS/CTS", "XON/XOFF");

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        initBaudRateSpinner();
        initDataBitsComboBox();
        initStopBitsComboBox();
        initParityComboBox();
        initFlowControlComboBox();

        // 默认设置
        settings = SerialPortSettings.createDefault();
        applySettingsToUI();
    }

    /**
     * 初始化波特率 Spinner
     */
    private void initBaudRateSpinner() {
        // 使用 IntegerSpinnerValueFactory，范围从 300 到 3000000
        SpinnerValueFactory.IntegerSpinnerValueFactory valueFactory =
                new SpinnerValueFactory.IntegerSpinnerValueFactory(300, 3000000, 1500000, 100);
        spBaudRate.setValueFactory(valueFactory);
        spBaudRate.setEditable(true);

        // 添加常用波特率提示
        // 注意：JavaFX Spinner 不支持像 ComboBox 那样的下拉提示
        // 但可以通过编辑器来输入自定义值
    }

    /**
     * 初始化数据位下拉框
     */
    private void initDataBitsComboBox() {
        cbDataBits.getItems().addAll(DATA_BITS_OPTIONS);
        cbDataBits.getSelectionModel().select(Integer.valueOf(8));
    }

    /**
     * 初始化停止位下拉框
     */
    private void initStopBitsComboBox() {
        cbStopBits.getItems().addAll(STOP_BITS_OPTIONS);
        cbStopBits.getSelectionModel().select("1");
    }

    /**
     * 初始化校验位下拉框
     */
    private void initParityComboBox() {
        cbParity.getItems().addAll(PARITY_OPTIONS);
        cbParity.getSelectionModel().select("None");
    }

    /**
     * 初始化流控下拉框
     */
    private void initFlowControlComboBox() {
        cbFlowControl.getItems().addAll(FLOW_CONTROL_OPTIONS);
        cbFlowControl.getSelectionModel().select("None");
    }

    /**
     * 将设置应用到 UI
     */
    public void applySettingsToUI() {
        if (settings == null) {
            settings = SerialPortSettings.createDefault();
        }

        // 设置波特率
        spBaudRate.getValueFactory().setValue(settings.getBaudRate());

        // 设置数据位
        cbDataBits.getSelectionModel().select(Integer.valueOf(settings.getDataBits()));

        // 设置停止位
        cbStopBits.getSelectionModel().select(SerialPortSettings.getStopBitsText(settings.getStopBits()));

        // 设置校验位
        cbParity.getSelectionModel().select(SerialPortSettings.getParityText(settings.getParity()));

        // 设置流控
        cbFlowControl.getSelectionModel().select(SerialPortSettings.getFlowControlText(settings.getFlowControl()));
    }

    /**
     * 从 UI 获取设置
     */
    public void updateSettingsFromUI() {
        if (settings == null) {
            settings = new SerialPortSettings();
        }

        // 获取波特率
        try {
            Integer baudRate = spBaudRate.getValue();
            if (baudRate != null && baudRate > 0) {
                settings.setBaudRate(baudRate);
            }
        } catch (Exception e) {
            // 如果解析失败，保持默认值
        }

        // 获取数据位
        Integer dataBits = cbDataBits.getSelectionModel().getSelectedItem();
        if (dataBits != null) {
            settings.setDataBits(dataBits);
        }

        // 获取停止位
        String stopBitsText = cbStopBits.getSelectionModel().getSelectedItem();
        if (stopBitsText != null) {
            settings.setStopBits(SerialPortSettings.getStopBitsFromText(stopBitsText));
        }

        // 获取校验位
        String parityText = cbParity.getSelectionModel().getSelectedItem();
        if (parityText != null) {
            settings.setParity(SerialPortSettings.getParityFromText(parityText));
        }

        // 获取流控
        String flowControlText = cbFlowControl.getSelectionModel().getSelectedItem();
        if (flowControlText != null) {
            settings.setFlowControl(SerialPortSettings.getFlowControlFromText(flowControlText));
        }
    }

    /**
     * 获取当前设置
     */
    public SerialPortSettings getSettings() {
        return settings;
    }

    /**
     * 设置当前设置
     */
    public void setSettings(SerialPortSettings settings) {
        this.settings = settings != null ? settings : SerialPortSettings.createDefault();
        applySettingsToUI();
    }
}
