package indi.lt.serialtool.controller;

import com.fazecast.jSerialComm.SerialPort;
import github.nonoas.jfx.flat.ui.AppState;
import github.nonoas.jfx.flat.ui.concurrent.TaskHandler;
import github.nonoas.jfx.flat.ui.stage.ToastQueue;
import indi.lt.serialtool.component.CommandTableView;
import indi.lt.serialtool.component.InlineCssRegexHighlighter;
import indi.lt.serialtool.component.MyStyleClassedTextArea;
import indi.lt.serialtool.component.SerialPortCombBox;
import indi.lt.serialtool.component.SerialToggleButton;
import indi.lt.serialtool.constant.CommandType;
import indi.lt.serialtool.constant.MessageDirection;
import indi.lt.serialtool.data.BufferedDisplayLine;
import indi.lt.serialtool.data.CommandRepository;
import indi.lt.serialtool.data.LogText;
import indi.lt.serialtool.data.SerialPortSettings;
import indi.lt.serialtool.global.ConfigManager;
import indi.lt.serialtool.global.FontSettingsManager;
import indi.lt.serialtool.service.SerialReadService;
import indi.lt.serialtool.service.SerialSenderService;
import indi.lt.serialtool.utils.StringUtil;
import indi.lt.serialtool.utils.UIUtil;
import indi.lt.serialtool.view.BaseStage;
import indi.lt.serialtool.view.SerialSendPane;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.DialogPane;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputControl;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToolBar;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.UUID;

/**
 * 串口发送控制器
 */
public class SerialSendCtrl implements Initializable {

    private final Logger LOG = LogManager.getLogger(SerialSendCtrl.class);

    private static final int DEFAULT_BAUTRATE = 1500000;

    /**
     * 自动换行
     */
    @FXML
    public CheckBox lineBreak;
    public ToggleButton tgWindowMode;
    public SerialToggleButton btnScheduleSend;

    @FXML
    private TextField tfRemark;
    @FXML
    private TextField tfCommand;
    @FXML
    private MyStyleClassedTextArea taRecvArea;
    @FXML
    private TextArea taSendArea;
    @FXML
    private CheckBox cbHexDisplay;
    @FXML
    private CheckBox cbTimeStampDisplay;
    @FXML
    private CheckBox cbHexSend;
    @FXML
    private CheckBox cbIsHex;


    @FXML
    private SerialPortCombBox cbSerialList;
    @FXML
    private ComboBox<Integer> cbBautrate;
    @FXML
    private Button btnSend;
    @FXML
    private SerialToggleButton btnOpenSerial;
    @FXML
    private StackPane spTableContainer;
    @FXML
    private Button btnMoreSettings;

    private SerialSendPane rootPane;

    private final CommandTableView table = new CommandTableView();
    private final Set<String> commandPersistenceBoundIds = new HashSet<>();

    private BaseStage currStage;

    private SerialSenderService serialSenderService;
    private SerialReadService serialReadService;

    private InlineCssRegexHighlighter highlighter;

    // 保存上次串口选择的 key
    private final String keyLastSerial = "sendModeLastSerialPort";

    // 串口参数设置
    private SerialPortSettings serialPortSettings;

    // 串口参数设置对话框 key
    private static final String KEY_SERIAL_SETTINGS = "sendModeSerialSettings";
    private static final String KEY_SEND_TEXT = "sendMode.form.sendText";
    private static final String KEY_HEX_DISPLAY = "sendMode.form.hexDisplay";
    private static final String KEY_HEX_SEND = "sendMode.form.hexSend";
    private static final String KEY_LINE_BREAK = "sendMode.form.lineBreak";
    private static final String KEY_TIMESTAMP_DISPLAY = "sendMode.form.timestampDisplay";
    private static final String KEY_REMARK = "sendMode.form.remark";
    private static final String KEY_COMMAND = "sendMode.form.command";
    private static final String KEY_COMMAND_HEX = "sendMode.form.commandHex";

    /**
     * 初始化串口参数设置
     */
    private void initSerialPortSettings() {
        // 从配置加载串口参数设置
        serialPortSettings = ConfigManager.getObject(KEY_SERIAL_SETTINGS, SerialPortSettings.class, SerialPortSettings.createDefault());

        // 应用设置到串口组件
        cbSerialList.setSerialPortSettings(serialPortSettings);
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        highlighter = new InlineCssRegexHighlighter(taRecvArea);
        spTableContainer.getChildren().add(table);
        initSerialPortSettings();
        initBaudRateList();
        initSerialComboBox();
        loadHistoryCommands();
        setupButtonActions();
        restoreFormState();
        bindFormStatePersistence();

        // 切换独立窗口
        tgWindowMode.selectedProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue) {
                BaseStage baseStage = new BaseStage();
                currStage = baseStage;
                ToolBar toolBar = new ToolBar();
                toolBar.setMinHeight(40);
                VBox.setVgrow(rootPane, Priority.ALWAYS);
                baseStage.registryDragger(toolBar);
                rootPane.setVisible(false);
                baseStage.setContentView(new VBox(toolBar, rootPane));
                // baseStage.setSize(rootPane.getWidth(), rootPane.getHeight() + 40);
                Platform.runLater(() -> {
                    baseStage.show();
                    rootPane.setVisible(true);
                });
            } else {
                // TODO
                currStage.close();
            }
        });


        // 定时发送
        btnScheduleSend.selectedProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue) {
                startSendCommand();
            } else {
                if (serialSenderService != null && serialSenderService.isRunning()) {
                    serialSenderService.cancel();
                }
            }
        });

        // 启动时默认打开第一个串口
        // cbSerialList.openSelectedSerial();
    }

    /**
     * 初始化波特率下拉列表
     */
    private void initBaudRateList() {
        List<Integer> baudRates = Arrays.asList(1200, 2400, 4800, 9600, 38400, 57600, 115200, 230400, DEFAULT_BAUTRATE, 2000000, 3000000);
        cbBautrate.getItems().clear();
        cbBautrate.getItems().addAll(baudRates);
        int baudRate = serialPortSettings != null ? serialPortSettings.getBaudRate() : DEFAULT_BAUTRATE;
        cbBautrate.getSelectionModel().select(Integer.valueOf(baudRate));
    }

    /**
     * 初始化串口下拉列表
     */
    private void initSerialComboBox() {
        // 串口下拉框初始化
        cbSerialList.init(keyLastSerial,
                () -> UIUtil.getSelectedInt(cbBautrate, DEFAULT_BAUTRATE),
                btnOpenSerial.selectedProperty(),
                SerialPort.TIMEOUT_WRITE_BLOCKING | SerialPort.TIMEOUT_READ_SEMI_BLOCKING,
                0,  // timeOutMillionTime
                serialPortSettings);  // settings

        // 选中波特率变化时重新打开串口
        cbBautrate.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (cbSerialList.getSelectedPort() != null && cbSerialList.getSelectedPort().isOpen()) {
                cbSerialList.openSelectedSerial();
            }
        });
    }

    /**
     * 加载历史命令
     */
    private void loadHistoryCommands() {
        new TaskHandler<List<CommandTableView.CommandItem>>()
                .whenCall(CommandRepository.INSTANCE::loadAll)
                .andThen(items -> {
                    table.getItems().addAll(items);
                    items.forEach(this::attachCommandPersistence);
                })
                .handle();
    }

    private void restoreFormState() {
        taSendArea.setText(ConfigManager.get(KEY_SEND_TEXT, ""));
        cbHexDisplay.setSelected(ConfigManager.get(KEY_HEX_DISPLAY, Boolean.class, false));
        cbHexSend.setSelected(ConfigManager.get(KEY_HEX_SEND, Boolean.class, false));
        lineBreak.setSelected(ConfigManager.get(KEY_LINE_BREAK, Boolean.class, false));
        cbTimeStampDisplay.setSelected(ConfigManager.get(KEY_TIMESTAMP_DISPLAY, Boolean.class, false));
        tfRemark.setText(ConfigManager.get(KEY_REMARK, ""));
        tfCommand.setText(ConfigManager.get(KEY_COMMAND, ""));
        cbIsHex.setSelected(ConfigManager.get(KEY_COMMAND_HEX, Boolean.class, false));
    }

    private void bindFormStatePersistence() {
        bindTextPersistence(taSendArea, KEY_SEND_TEXT);
        bindTextPersistence(tfRemark, KEY_REMARK);
        bindTextPersistence(tfCommand, KEY_COMMAND);
        bindCheckBoxPersistence(cbHexDisplay, KEY_HEX_DISPLAY);
        bindCheckBoxPersistence(cbHexSend, KEY_HEX_SEND);
        bindCheckBoxPersistence(lineBreak, KEY_LINE_BREAK);
        bindCheckBoxPersistence(cbTimeStampDisplay, KEY_TIMESTAMP_DISPLAY);
        bindCheckBoxPersistence(cbIsHex, KEY_COMMAND_HEX);
        cbBautrate.valueProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal == null) {
                return;
            }
            serialPortSettings.setBaudRate(newVal);
            ConfigManager.putObject(KEY_SERIAL_SETTINGS, serialPortSettings);
        });
    }

    private void bindTextPersistence(TextInputControl textInputControl, String key) {
        textInputControl.textProperty().addListener((obs, oldVal, newVal) -> ConfigManager.put(key, newVal == null ? "" : newVal));
    }

    private void bindCheckBoxPersistence(CheckBox checkBox, String key) {
        checkBox.selectedProperty().addListener((obs, oldVal, newVal) -> ConfigManager.set(key, String.valueOf(newVal)));
    }

    /**
     * 绑定按钮事件
     */
    private void setupButtonActions() {
        btnSend.setOnAction(e -> sendData());

        btnMoreSettings.setOnAction(e -> showMoreSettings());

        cbSerialList.disableProperty().bind(btnOpenSerial.disabledProperty());
        btnOpenSerial.selectedProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue) {
                btnOpenSerial.setDisable(true);
                cbSerialList.openSelectedSerial();
            } else {
                if (cbSerialList.getSelectedPort() != null && cbSerialList.getSelectedPort().isOpen()) {
                    // 串口已打开 → 关闭
                    closeSerial();
                }
            }
        });

        cbSerialList.setOnOpenSucceed(() -> {
            btnOpenSerial.setDisable(false);
            serialReadService = new SerialReadService(
                    cbSerialList.getSelectedPort(),
                    taRecvArea,
                    cbTimeStampDisplay.selectedProperty(),
                    cbHexDisplay.selectedProperty(),
                    () -> highlighter.schedule(),
                    true
            );
            serialReadService.start();
        });
        cbSerialList.setOnOpenFailed(() -> {
            btnOpenSerial.setDisable(false);
            btnOpenSerial.setSelected(false);
        });
    }

    /**
     * 显示更多设置对话框
     */
    @FXML
    private void showMoreSettings() {
        try {
            // 加载对话框 FXML
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/serial-settings-dialog.fxml"));
            DialogPane dialogPane = loader.load();

            // 获取控制器
            SerialSettingsDialogCtrl dialogCtrl = loader.getController();

            // 设置当前设置
            dialogCtrl.setSettings(serialPortSettings);

            // 创建对话框
            Dialog<SerialPortSettings> dialog = new Dialog<>();
            dialog.setDialogPane(dialogPane);
            dialog.setTitle("串口参数设置");
            dialog.setHeaderText("自定义串口参数");
            FontSettingsManager.configureDialog(dialog);

            // 注意：FXML 中已经定义了按钮，不需要再次添加

            // 处理 OK 按钮
            dialog.setResultConverter(buttonType -> {
                if (buttonType == ButtonType.OK) {
                    // 从 UI 更新设置
                    dialogCtrl.updateSettingsFromUI();
                    return dialogCtrl.getSettings();
                }
                return null;
            });

            // 显示对话框并等待结果
            dialog.showAndWait().ifPresent(settings -> {
                // 保存设置
                serialPortSettings = settings;
                ConfigManager.putObject(KEY_SERIAL_SETTINGS, settings);

                // 应用设置到串口组件
                cbSerialList.setSerialPortSettings(settings);

                // 同步波特率到主界面下拉框（如果设置中有指定波特率）
                cbBautrate.getSelectionModel().select(Integer.valueOf(settings.getBaudRate()));

                // 显示成功提示
                ToastQueue.show(AppState.getStage(), "串口参数已更新", 800);
            });

        } catch (Exception e) {
            LOG.error("显示串口设置对话框失败", e);
            ToastQueue.show(AppState.getStage(), "打开设置对话框失败", 1000);
        }
    }

    /**
     * 关闭串口
     */
    public void closeSerial() {
        if (cbSerialList.getSelectedPort() != null && cbSerialList.getSelectedPort().isOpen()) {
            cbSerialList.getSelectedPort().closePort();
            LOG.info("串口已关闭");
            ToastQueue.show(AppState.getStage(), "串口已关闭", 800);
        }
        if (serialReadService != null) {
            serialReadService.cancel();
        }
    }

    /**
     * 发送数据
     */
    private void sendData() {
        if (cbSerialList.getSelectedPort() == null || !cbSerialList.getSelectedPort().isOpen()) {
            ToastQueue.show(AppState.getStage(), "串口未打开", 800);
            return;
        }

        String text = taSendArea.getText();
        if (text == null || text.trim().isEmpty()) {
            ToastQueue.show(AppState.getStage(), "发送内容不能为空", 800);
            return;
        }

        try {
            boolean isHexSend = cbHexSend.isSelected();
            byte[] data;
            String logText;

            if (isHexSend) {
                data = StringUtil.hexStringToBytes(text);
                if (lineBreak.isSelected()) {
                    data = Arrays.copyOf(data, data.length + 1);
                    data[data.length - 1] = (byte) '\n';
                }
                logText = StringUtil.bytesToHexString(data);
            } else {
                String content = lineBreak.isSelected() ? text + "\n" : text;
                data = content.getBytes(StandardCharsets.UTF_8);
                logText = content;
            }

            cbSerialList.getSelectedPort().writeBytes(data, data.length);
            LOG.info("发送成功: {}", logText);
            // 发送面板追加数据
            BufferedDisplayLine line = BufferedDisplayLine.of(System.currentTimeMillis(), null, BufferedDisplayLine.DataType.TXT, MessageDirection.SEND, logText);
            taRecvArea.appendLogLine(line, true, true);
        } catch (IllegalArgumentException e) {
            LOG.error("HEX 发送失败", e);
            ToastQueue.show(AppState.getStage(), "HEX格式错误: " + e.getMessage(), 1200);
        } catch (Exception e) {
            LOG.error("发送失败", e);
            ToastQueue.show(AppState.getStage(), "发送失败: " + e.getMessage(), 1000);
        }
    }

    private void startSendCommand() {
        if (cbSerialList.getSelectedPort() == null || !cbSerialList.getSelectedPort().isOpen()) {
            ToastQueue.show(AppState.getStage(), "串口未打开", 800);
            btnScheduleSend.setSelected(false);
            return;
        }

        serialSenderService = new SerialSenderService(
                table.getItems().filtered(CommandTableView.CommandItem::isScheduled),
                cbSerialList.getSelectedPort(),
                taRecvArea,
                cbHexDisplay,
                cbTimeStampDisplay
        );
        serialSenderService.start();
    }

    /**
     * 添加自定义命令
     */
    @FXML
    private void addCommand() {
        if (tfCommand.getText().trim().isEmpty()) {
            ToastQueue.show(AppState.getStage(), "指令不能为空", 500);
            return;
        }

        CommandType type = cbIsHex.isSelected() ? CommandType.HEX : CommandType.TXT;
        CommandTableView.CommandItem item = new CommandTableView.CommandItem(
                UUID.randomUUID().toString(),
                tfRemark.getText(),
                tfCommand.getText(),
                type.toString()
        );
        table.getItems().add(item);
        attachCommandPersistence(item);
        CommandRepository.INSTANCE.add(item);
    }

    private void attachCommandPersistence(CommandTableView.CommandItem item) {
        if (item == null || !commandPersistenceBoundIds.add(item.getId())) {
            return;
        }
        item.remarkProperty().addListener((obs, oldVal, newVal) -> persistCommandTable());
        item.commandProperty().addListener((obs, oldVal, newVal) -> persistCommandTable());
        item.commandTypeProperty().addListener((obs, oldVal, newVal) -> persistCommandTable());
        item.intervalProperty().addListener((obs, oldVal, newVal) -> persistCommandTable());
        item.scheduledProperty().addListener((obs, oldVal, newVal) -> persistCommandTable());
    }

    private void persistCommandTable() {
        List<CommandTableView.CommandItem> snapshot = new ArrayList<>();
        for (CommandTableView.CommandItem item : table.getItems()) {
            if (item != null) {
                snapshot.add(item);
            }
        }
        TaskHandler.backRun(() -> CommandRepository.INSTANCE.saveAll(snapshot));
    }

    public void persistFormStateToConfig() {
        ConfigManager.put(KEY_SEND_TEXT, taSendArea.getText() == null ? "" : taSendArea.getText());
        ConfigManager.put(KEY_HEX_DISPLAY, String.valueOf(cbHexDisplay.isSelected()));
        ConfigManager.put(KEY_HEX_SEND, String.valueOf(cbHexSend.isSelected()));
        ConfigManager.put(KEY_LINE_BREAK, String.valueOf(lineBreak.isSelected()));
        ConfigManager.put(KEY_TIMESTAMP_DISPLAY, String.valueOf(cbTimeStampDisplay.isSelected()));
        ConfigManager.put(KEY_REMARK, tfRemark.getText() == null ? "" : tfRemark.getText());
        ConfigManager.put(KEY_COMMAND, tfCommand.getText() == null ? "" : tfCommand.getText());
        ConfigManager.put(KEY_COMMAND_HEX, String.valueOf(cbIsHex.isSelected()));

        Integer baudRate = cbBautrate.getValue();
        if (baudRate != null) {
            serialPortSettings.setBaudRate(baudRate);
        }
        ConfigManager.putObject(KEY_SERIAL_SETTINGS, serialPortSettings);
    }

    public List<CommandTableView.CommandItem> snapshotCommandTable() {
        List<CommandTableView.CommandItem> snapshot = new ArrayList<>();
        for (CommandTableView.CommandItem item : table.getItems()) {
            if (item == null) {
                continue;
            }
            CommandTableView.CommandItem copy = new CommandTableView.CommandItem(
                    item.getId(),
                    item.getRemark(),
                    item.getCommand(),
                    item.getCommandType()
            );
            copy.setInterval(item.getInterval());
            copy.setScheduled(item.isScheduled());
            snapshot.add(copy);
        }
        return snapshot;
    }

    public void setRootPane(SerialSendPane rootPane) {
        this.rootPane = rootPane;
    }

    public void dispose() {
        if (serialSenderService != null && serialSenderService.isRunning()) {
            serialSenderService.cancel();
        }
        closeSerial();
        if (currStage != null) {
            currStage.close();
            currStage = null;
        }
    }

    /**
     * 恢复自动滚动
     */
    @FXML
    private void restoreScrolling() {
        taRecvArea.setAutoScroll(true);
    }

    @FXML
    private void clearLogs() {
        taRecvArea.getArea().clear();
    }
}
