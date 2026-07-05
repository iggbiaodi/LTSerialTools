package indi.lt.serialtool.controller;

import com.fazecast.jSerialComm.SerialPort;
import github.nonoas.jfx.flat.ui.AppState;
import github.nonoas.jfx.flat.ui.concurrent.TaskHandler;
import github.nonoas.jfx.flat.ui.stage.ToastQueue;
import indi.lt.serialtool.component.*;
import indi.lt.serialtool.constant.CommandType;
import indi.lt.serialtool.constant.MessageDirection;
import indi.lt.serialtool.data.BufferedDisplayLine;
import indi.lt.serialtool.data.CommandRepository;
import indi.lt.serialtool.data.SerialPortSettings;
import indi.lt.serialtool.data.ZipDataProvider;
import indi.lt.serialtool.global.ConfigManager;
import indi.lt.serialtool.global.FontSettingsManager;
import indi.lt.serialtool.service.SerialReadService;
import indi.lt.serialtool.service.SerialSenderService;
import indi.lt.serialtool.utils.StringUtil;
import indi.lt.serialtool.utils.UIUtil;
import indi.lt.serialtool.view.SerialSendPane;
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.util.StringConverter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * 串口发送控制器
 */
public class SerialSendCtrl implements Initializable, ZipDataProvider {

    private final Logger LOG = LogManager.getLogger(SerialSendCtrl.class);

    private static final int DEFAULT_BAUTRATE = 1500000;
    /** 发送写超时（毫秒），超时后 writeBytes 返回实际写入字节数，不阻塞后续操作 */
    private static final int SEND_TIMEOUT_MS = 100;

    /**
     * 自动换行
     */
    @FXML
    public CheckBox lineBreak;
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
    @FXML
    private Label lbRecvBytes;
    @FXML
    private Label lbSendBytes;

    private SerialSendPane rootPane;

    private final CommandTableView table = new CommandTableView();
    private final Set<String> commandPersistenceBoundIds = new HashSet<>();

    private SerialSenderService serialSenderService;
    private SerialReadService serialReadService;

    private InlineCssRegexHighlighter highlighter;

    /** 待发送的单条指令（串口打开成功后自动发送） */
    private CommandTableView.CommandItem pendingSingleSend;

    /** 发送写超时（毫秒），从配置加载 */
    private int sendTimeoutMs = SEND_TIMEOUT_MS;

    // 保存上次串口选择的 key
    private final String keyLastSerial = "sendModeLastSerialPort";

    // 串口参数设置
    private SerialPortSettings serialPortSettings;
    private long recvBytesBase = 0L;
    private long sentBytesCount = 0L;

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
    private static final String KEY_SEND_TIMEOUT = "sendMode.form.sendTimeout";

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
        lbRecvBytes.setText(formatBytes(0L));
        lbSendBytes.setText(formatBytes(0L));

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

        // 表格右键菜单：编辑 / 删除
        ContextMenu tableContextMenu = new ContextMenu();
        MenuItem editItem = new MenuItem("编辑");
        editItem.setOnAction(e -> {
            CommandTableView.CommandItem selected = table.getSelectionModel().getSelectedItem();
            if (selected != null) {
                editCommand(selected);
            }
        });
        MenuItem deleteItem = new MenuItem("删除");
        deleteItem.setOnAction(e -> {
            CommandTableView.CommandItem selected = table.getSelectionModel().getSelectedItem();
            if (selected != null) {
                table.getItems().remove(selected);
                CommandRepository.INSTANCE.remove(selected);
            }
        });
        tableContextMenu.getItems().addAll(editItem, deleteItem);
        table.setContextMenu(tableContextMenu);

        // 注入发送回调，让表格中的"发送"按钮可用
        table.setOnSendCommand(item -> {
            sendSingleCommand(item);
            return kotlin.Unit.INSTANCE;
        });

        // 双击行发送指令
        table.setRowFactory(tv -> {
            TableRow<CommandTableView.CommandItem> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && !row.isEmpty()) {
                    sendSingleCommand(row.getItem());
                }
            });
            return row;
        });
    }

    /**
     * 初始化波特率下拉列表
     */
    private void initBaudRateList() {
        List<Integer> baudRates = Arrays.asList(1200, 2400, 4800, 9600, 38400, 57600, 115200, 230400, DEFAULT_BAUTRATE, 2000000, 3000000);
        cbBautrate.getItems().clear();
        cbBautrate.getItems().addAll(baudRates);
        cbBautrate.setConverter(new StringConverter<Integer>() {
            @Override
            public String toString(Integer value) {
                return value == null ? "" : value.toString();
            }
            @Override
            public Integer fromString(String string) {
                if (string == null || string.trim().isEmpty()) {
                    return serialPortSettings.getBaudRate();
                }
                try {
                    return Integer.parseInt(string.trim());
                } catch (NumberFormatException e) {
                    return serialPortSettings.getBaudRate();
                }
            }
        });
        cbBautrate.setEditable(true);
        int baudRate = serialPortSettings != null ? serialPortSettings.getBaudRate() : DEFAULT_BAUTRATE;
        cbBautrate.setValue(baudRate);
    }

    /**
     * 初始化串口下拉列表
     */
    private void initSerialComboBox() {
        int receiveTimeoutMs = getReceiveTimeoutMs();
        // 串口下拉框初始化
        cbSerialList.init(keyLastSerial,
                () -> UIUtil.getSelectedInt(cbBautrate, DEFAULT_BAUTRATE),
                btnOpenSerial.selectedProperty(),
                SerialPort.TIMEOUT_WRITE_BLOCKING | SerialPort.TIMEOUT_READ_SEMI_BLOCKING,
                receiveTimeoutMs,
                serialPortSettings);  // settings

        addBaudRateSelectionListener();
    }

    @SuppressWarnings("unchecked")
    private void addBaudRateSelectionListener() {
        ObservableValue<Object> selectedItemProperty =
                (ObservableValue<Object>) (ObservableValue<?>) cbBautrate.getSelectionModel().selectedItemProperty();
        ChangeListener<Object> listener = (obs, oldVal, newVal) -> {
            if (parseBaudRateValue(newVal, serialPortSettings.getBaudRate()) == null) {
                return;
            }
            if (cbSerialList.getSelectedPort() != null && cbSerialList.getSelectedPort().isOpen()) {
                cbSerialList.openSelectedSerial();
            }
        };
        selectedItemProperty.addListener(listener);
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
        sendTimeoutMs = ConfigManager.get(KEY_SEND_TIMEOUT, Integer.class, SEND_TIMEOUT_MS);
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
        addBaudRateValueListener();
    }

    @SuppressWarnings("unchecked")
    private void addBaudRateValueListener() {
        ObservableValue<Object> valueProperty = (ObservableValue<Object>) (ObservableValue<?>) cbBautrate.valueProperty();
        ChangeListener<Object> listener = (obs, oldVal, newVal) -> {
            Integer baudRate = parseBaudRateValue(newVal, serialPortSettings.getBaudRate());
            if (baudRate == null) {
                return;
            }
            if (!(newVal instanceof Integer)) {
                Platform.runLater(() -> cbBautrate.setValue(baudRate));
                return;
            }
            serialPortSettings.setBaudRate(baudRate);
            ConfigManager.putObject(KEY_SERIAL_SETTINGS, serialPortSettings);
        };
        valueProperty.addListener(listener);
    }

    private Integer parseBaudRateValue(Object value, int fallback) {
        if (value instanceof Integer baudRate) {
            return baudRate > 0 ? baudRate : fallback;
        }
        if (value instanceof String text) {
            try {
                int baudRate = Integer.parseInt(text.trim());
                return baudRate > 0 ? baudRate : fallback;
            } catch (NumberFormatException ex) {
                return fallback;
            }
        }
        return value == null ? null : fallback;
    }

    private void bindTextPersistence(TextInputControl textInputControl, String key) {
        textInputControl.textProperty().addListener((obs, oldVal, newVal) -> ConfigManager.put(key, newVal == null ? "" : newVal));
    }

    private void bindCheckBoxPersistence(CheckBox checkBox, String key) {
        checkBox.selectedProperty().addListener((obs, oldVal, newVal) -> ConfigManager.set(key, String.valueOf(newVal)));
    }

    private int getReceiveTimeoutMs() {
        String rawTimeout = ConfigManager.get(
                ConfigManager.KEY_RECEIVE_TIMEOUT_MS,
                String.valueOf(ConfigManager.DEFAULT_RECEIVE_TIMEOUT_MS)
        );
        try {
            int timeout = Integer.parseInt(rawTimeout);
            return timeout > 0 ? timeout : ConfigManager.DEFAULT_RECEIVE_TIMEOUT_MS;
        } catch (NumberFormatException e) {
            return ConfigManager.DEFAULT_RECEIVE_TIMEOUT_MS;
        }
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

            // 应用发送超时到串口硬件层
            applySendTimeout();

            serialReadService = new SerialReadService(
                    cbSerialList.getSelectedPort(),
                    taRecvArea,
                    cbTimeStampDisplay.selectedProperty(),
                    cbHexDisplay.selectedProperty(),
                    () -> highlighter.schedule(),
                    getReceiveTimeoutMs(),
                    true
            );
            serialReadService.setOnRecvBytesChanged(bytes ->
                    Platform.runLater(() -> lbRecvBytes.setText(formatBytes(recvBytesBase + bytes)))
            );
            serialReadService.start();
            // 如果定时发送已勾选，自动启动发送
            if (btnScheduleSend.isSelected() && (serialSenderService == null || !serialSenderService.isRunning())) {
                startSendCommand();
            }
            // 如果有待发送的单条指令，发送它
            if (pendingSingleSend != null) {
                CommandTableView.CommandItem item = pendingSingleSend;
                pendingSingleSend = null;
                doSendCommand(item);
            }
        });
        cbSerialList.setOnOpenFailed(() -> {
            btnOpenSerial.setDisable(false);
            btnOpenSerial.setSelected(false);
            btnScheduleSend.setSelected(false);
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

                // 同步波特率到主界面下拉框
                cbBautrate.setValue(settings.getBaudRate());

                // 显示成功提示
                ToastQueue.show(AppState.getStage(), "串口参数已更新", 800);
            });

        } catch (Exception e) {
            LOG.error("显示串口设置对话框失败", e);
            ToastQueue.show(AppState.getStage(), "打开设置对话框失败", 1000);
        }
    }

    /**
     * 将发送超时应用到当前打开的串口硬件层
     */
    private void applySendTimeout() {
        SerialPort port = cbSerialList.getSelectedPort();
        if (port != null && port.isOpen()) {
            int receiveTimeoutMs = getReceiveTimeoutMs();
            cbSerialList.setTimeoutMillis(receiveTimeoutMs);
            port.setComPortTimeouts(
                    SerialPort.TIMEOUT_WRITE_BLOCKING | SerialPort.TIMEOUT_READ_SEMI_BLOCKING,
                    receiveTimeoutMs,
                    sendTimeoutMs     // 写超时
            );
            LOG.info("发送模式超时已设置: 接收={}ms, 发送={}ms", receiveTimeoutMs, sendTimeoutMs);
        }
    }

    /**
     * 关闭串口
     */
    public void closeSerial() {
        // 定时发送跟随串口关闭
        if (btnScheduleSend.isSelected()) {
            btnScheduleSend.setSelected(false);
        }
        recvBytesBase += serialReadService != null ? serialReadService.getRecvBytesCount() : 0L;
        if (cbSerialList.getSelectedPort() != null && cbSerialList.getSelectedPort().isOpen()) {
            cbSerialList.getSelectedPort().closePort();
            LOG.info("串口已关闭");
            ToastQueue.show(AppState.getStage(), "串口已关闭", 800);
        }
        if (serialReadService != null) {
            serialReadService.cancel();
            serialReadService = null;
        }
        lbRecvBytes.setText(formatBytes(recvBytesBase));
    }

    /**
     * 手动发送数据（串口未开时自动打开再发送）
     */
    private void sendData() {
        String text = taSendArea.getText();
        if (text == null || text.trim().isEmpty()) {
            ToastQueue.show(AppState.getStage(), "发送内容不能为空", 800);
            return;
        }

        if (cbSerialList.getSelectedPort() == null || !cbSerialList.getSelectedPort().isOpen()) {
            String type = cbHexSend.isSelected() ? "HEX" : "TXT";
            CommandTableView.CommandItem tempItem = new CommandTableView.CommandItem("__temp__", "", text, type);
            pendingSingleSend = tempItem;
            cbSerialList.openSelectedSerial();
            btnOpenSerial.setSelected(true);
            return;
        }

        doSendText(text, cbHexSend.isSelected(), lineBreak.isSelected());
    }

    /**
     * 从指令表格发送单条指令（双击 / 发送按钮）
     */
    private void sendSingleCommand(CommandTableView.CommandItem item) {
        if (cbSerialList.getSelectedPort() == null || !cbSerialList.getSelectedPort().isOpen()) {
            // 串口未打开 → 自动打开，打开成功后自动发送
            pendingSingleSend = item;
            cbSerialList.openSelectedSerial();
            btnOpenSerial.setSelected(true);
            return;
        }
        doSendCommand(item);
    }

    /**
     * 实际执行单条指令发送（串口已打开时调用）
     */
    private void doSendCommand(CommandTableView.CommandItem item) {
        String text = item.getCommand();
        if (text == null || text.trim().isEmpty()) {
            return;
        }
        boolean isHex = "HEX".equals(item.getCommandType());
        doSendText(text, isHex, false);
    }

    /**
     * 核心发送逻辑：将文本按指定模式发送到串口
     */
    private void doSendText(String text, boolean isHex, boolean addLineBreak) {
        try {
            byte[] data;
            String logText;
            if (isHex) {
                data = StringUtil.hexStringToBytes(text);
                if (addLineBreak) {
                    data = Arrays.copyOf(data, data.length + 1);
                    data[data.length - 1] = (byte) '\n';
                }
                logText = StringUtil.bytesToHexString(data);
            } else {
                String content = addLineBreak ? text + "\n" : text;
                data = content.getBytes(StandardCharsets.UTF_8);
                logText = content;
            }
            int written = cbSerialList.getSelectedPort().writeBytes(data, data.length);
            if (written > 0) {
                sentBytesCount += written;
                lbSendBytes.setText(formatBytes(sentBytesCount));
            }
            if (written != data.length) {
                LOG.warn("发送超时: {}/{} 字节", written, data.length);
                ToastQueue.show(AppState.getStage(),
                        String.format("发送超时: %d/%d 字节", written, data.length), 1000);
            }
            LOG.info("发送成功: {}", logText);
            String ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss.SSS"));
            BufferedDisplayLine line = BufferedDisplayLine.of(0L, ts, BufferedDisplayLine.DataType.TXT, MessageDirection.SEND, logText);
            taRecvArea.appendLogLine(line, true, true, true);
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
            // 串口未打开 → 自动打开，打开成功后会自动启动定时发送
            cbSerialList.openSelectedSerial();
            btnOpenSerial.setSelected(true);
            return;
        }

        serialSenderService = new SerialSenderService(
                table.getItems().filtered(CommandTableView.CommandItem::isScheduled),
                cbSerialList.getSelectedPort(),
                taRecvArea,
                cbHexDisplay,
                cbTimeStampDisplay,
                lineBreak
        );
        serialSenderService.setOnSentBytesChanged(bytes ->
                Platform.runLater(() -> {
                    sentBytesCount += bytes;
                    lbSendBytes.setText(formatBytes(sentBytesCount));
                })
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

    /**
     * 编辑指定指令：弹出对话框修改备注、指令内容、指令类型
     */
    private void editCommand(CommandTableView.CommandItem item) {
        // 创建对话框内容
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(10, 10, 10, 10));

        // 设置列约束：第0列（标签）优先计算宽度，第1列（输入控件）占据剩余空间
        ColumnConstraints col0 = new ColumnConstraints();
        col0.setMinWidth(Region.USE_PREF_SIZE);
        col0.setPrefWidth(Region.USE_COMPUTED_SIZE);
        col0.setMaxWidth(Region.USE_PREF_SIZE);
        col0.setHgrow(Priority.NEVER);

        ColumnConstraints col1 = new ColumnConstraints();
        col1.setMinWidth(200);
        col1.setPrefWidth(400);
        col1.setMaxWidth(Double.MAX_VALUE);
        col1.setHgrow(Priority.ALWAYS);

        grid.getColumnConstraints().addAll(col0, col1);

        Label remarkLabel = new Label("备注:");
        TextField remarkField = new TextField(item.getRemark());
        Label commandLabel = new Label("指令内容:");
        TextArea commandArea = new TextArea(item.getCommand());
        commandArea.setPrefRowCount(3);
        Label typeLabel = new Label("指令类型:");
        ToggleButton hexToggle = new ToggleButton("HEX");
        ToggleButton txtToggle = new ToggleButton("TXT");
        ToggleGroup typeGroup = new ToggleGroup();
        hexToggle.setToggleGroup(typeGroup);
        txtToggle.setToggleGroup(typeGroup);
        HBox typeBox = new HBox(5, hexToggle, txtToggle);
        typeBox.setAlignment(Pos.CENTER_LEFT);

        // 初始化类型选择
        if ("HEX".equals(item.getCommandType())) {
            hexToggle.setSelected(true);
        } else {
            txtToggle.setSelected(true);
        }

        grid.add(remarkLabel, 0, 0);
        grid.add(remarkField, 1, 0);
        grid.add(commandLabel, 0, 1);
        grid.add(commandArea, 1, 1);
        grid.add(typeLabel, 0, 2);
        grid.add(typeBox, 1, 2);

        // 创建对话框
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("编辑指令");
        dialog.setHeaderText("修改指令内容");
        dialog.getDialogPane().setContent(grid);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        dialog.setResizable(true);
        FontSettingsManager.configureDialog(dialog);

        // 自动聚焦到指令内容
        Platform.runLater(() -> commandArea.requestFocus());

        dialog.showAndWait().ifPresent(btn -> {
            if (btn == ButtonType.OK) {
                item.setRemark(remarkField.getText());
                item.setCommand(commandArea.getText());
                item.setCommandType(hexToggle.isSelected() ? "HEX" : "TXT");
                persistCommandTable();
            }
        });
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
        ConfigManager.put(KEY_SEND_TIMEOUT, String.valueOf(sendTimeoutMs));

        serialPortSettings.setBaudRate(UIUtil.getSelectedInt(cbBautrate, serialPortSettings.getBaudRate()));
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
    }

    @Override
    public Map<String, String> provideZipEntries() {
        Map<String, String> entries = new HashMap<>();
        String recvData = taRecvArea.getText();
        if (recvData != null && !recvData.isEmpty()) {
            entries.put("发送模式接收", recvData);
        }
        String sendData = taSendArea.getText();
        if (sendData != null && !sendData.isEmpty()) {
            entries.put("发送模式发送区", sendData);
        }
        return entries;
    }

    /**
     * 恢复自动滚动
     */
    @FXML
    private void restoreScrolling() {
        taRecvArea.restoreAutoScrollToEnd();
    }

    @FXML
    private void clearLogs() {
        taRecvArea.getArea().clear();
        recvBytesBase = 0L;
        sentBytesCount = 0L;
        lbSendBytes.setText(formatBytes(0L));
        if (serialReadService != null) {
            serialReadService.resetRecvBytesCount();
        } else {
            lbRecvBytes.setText(formatBytes(0L));
        }
    }

    private String formatBytes(long bytes) {
        if (bytes < 1024) {
            return bytes + "(0)B";
        }
        if (bytes < 1024 * 1024) {
            return String.format("%d(%.1fK)B", bytes, bytes / 1024.0);
        }
        if (bytes < 1024L * 1024L * 1024L) {
            return String.format("%d(%.1fM)B", bytes, bytes / (1024.0 * 1024.0));
        }
        return String.format("%d(%.1fG)B", bytes, bytes / (1024.0 * 1024.0 * 1024.0));
    }
}
