package indi.lt.serialtool.controller;

import github.nonoas.jfx.flat.ui.theme.Theme;
import indi.lt.serialtool.component.CommandTableView;
import indi.lt.serialtool.component.FontFamilyComboBox;
import indi.lt.serialtool.component.ThemeComboBox;
import indi.lt.serialtool.data.ZipDataProvider;
import indi.lt.serialtool.global.ConfigManager;
import indi.lt.serialtool.global.FontSettingsManager;
import indi.lt.serialtool.service.AutoSaveService;
import indi.lt.serialtool.utils.ZipUtil;
import indi.lt.serialtool.utils.UIUtil;
import indi.lt.serialtool.view.AsciiStage;
import indi.lt.serialtool.view.BaseStage;
import indi.lt.serialtool.view.SerialReceivePane;
import indi.lt.serialtool.view.SerialSendPane;
import indi.lt.serialtool.view.WaveformPane;
import javafx.application.Platform;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.kordamp.ikonli.javafx.FontIcon;

import java.awt.Desktop;
import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.URL;
import java.util.*;
import java.util.stream.Collectors;

import static indi.lt.serialtool.global.ConfigManager.DEFAULT_RECEIVE_TIMEOUT_MS;
import static indi.lt.serialtool.global.ConfigManager.KEY_RECEIVE_SPLIT_PANE_DIVIDER_POSITIONS;
import static indi.lt.serialtool.global.ConfigManager.KEY_RECEIVE_TIMEOUT_MS;
import static org.kordamp.ikonli.material2.Material2OutlinedAL.ADD_BOX;
import static org.kordamp.ikonli.material2.Material2OutlinedAL.ASSIGNMENT;
import static org.kordamp.ikonli.material2.Material2OutlinedAL.INFO;
import static org.kordamp.ikonli.material2.Material2OutlinedAL.INVERT_COLORS;
import static org.kordamp.ikonli.material2.Material2OutlinedMZ.SETTINGS;
import static org.kordamp.ikonli.material2.Material2OutlinedMZ.TUNE;

public class MainController implements Initializable {
    private static final String KEY_AUTO_SAVE = "main.form.autoSave";
    private final Logger LOG = LogManager.getLogger(MainController.class);

    @FXML
    public ToolBar toolBar;

    public MenuButton mbFile;

    @FXML
    public MenuButton mbSetting;

    @FXML
    public MenuButton mbAppearance;

    public CheckMenuItem autoSaveCheck;

    @FXML
    public MenuItem menuReceiveTimeout;

    // 自动保存相关菜单项
    @FXML
    public MenuItem menuAutoSaveInterval;
    @FXML
    public MenuItem menuAutoSaveFileSize;
    @FXML
    public MenuItem menuBufferCapacity;
    @FXML
    public MenuItem menuSaveAsZip;

    public MenuButton mbTools;
    public MenuButton mbHelp;
    public Button mbNewTab;

    @FXML
    private BorderPane rootPane;

    private final SerialSendPane serialSendPane = SerialSendPane.getInstance();
    private final WaveformPane waveformPane = new WaveformPane();

    private final SplitPane spReceive = new SplitPane();
    private final SplitPane splitTabContainer = new SplitPane();

    private final ToolBar topToolBar = new ToolBar();


    @FXML
    private TabPane tabRootPane;

    private Theme currentTheme;
    private final List<SerialReceivePane> receivePanes = new ArrayList<>();
    private final Map<Tab, Runnable> tabCloseActions = new HashMap<>();
    private final Map<Tab, BaseStage> detachedWindows = new HashMap<>();
    private final Map<BaseStage, Node> detachedContents = new HashMap<>();
    private TabPane leftSplitTabPane;
    private TabPane rightSplitTabPane;
    private int receivePaneIndex = 2;

    private enum SplitSide {
        LEFT,
        RIGHT
    }


    public TabPane getMenuBar() {
        return tabRootPane;
    }

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        initMenuButtons();
        tabRootPane.setTabClosingPolicy(TabPane.TabClosingPolicy.ALL_TABS);
        splitTabContainer.setFocusTraversable(false);

        SerialReceivePane serialReceivePane1 = new SerialReceivePane("串口1:", "serialKey1");
        SerialReceivePane serialReceivePane2 = new SerialReceivePane("串口2:", "serialKey2");
        receivePanes.add(serialReceivePane1);
        receivePanes.add(serialReceivePane2);

        String dividePostions = ConfigManager.get(KEY_RECEIVE_SPLIT_PANE_DIVIDER_POSITIONS, "0.5");
        spReceive.getItems().addAll(serialReceivePane1, serialReceivePane2);
        double[] dividePositionList = Arrays.stream(dividePostions.split(",")).mapToDouble(Double::parseDouble).toArray();
        Platform.runLater(() -> spReceive.setDividerPositions(dividePositionList));

        Tab tabRec = createManagedTab("接收模式", spReceive, () -> {
            disposeReceivePane(serialReceivePane1);
            disposeReceivePane(serialReceivePane2);
        }, false);

        Tab tabSend = createManagedTab("发送模式", serialSendPane, serialSendPane::dispose, false);

        Tab tabWaveform = createManagedTab("波形图模式", waveformPane, waveformPane::dispose, false);
        tabRootPane.getTabs().addAll(tabSend, tabRec, tabWaveform);
        tabRootPane.getSelectionModel().select(tabRec);
    }

    private Tab createManagedTab(String title, Node content, Runnable closeAction, boolean closable) {
        Tab tab = new Tab(title, content);
        tab.setClosable(closable);
        tab.setContextMenu(createTabContextMenu(tab));
        tab.setOnClosed(event -> handleTabClosed(tab));
        if (closeAction != null) {
            tabCloseActions.put(tab, closeAction);
        }
        return tab;
    }

    private ContextMenu createTabContextMenu(Tab tab) {
        MenuItem splitLeftItem = new MenuItem("拆分到左侧面板");
        splitLeftItem.setOnAction(event -> moveTabToSide(tab, SplitSide.LEFT));

        MenuItem splitRightItem = new MenuItem("拆分到右侧面板");
        splitRightItem.setOnAction(event -> moveTabToSide(tab, SplitSide.RIGHT));

        MenuItem cancelSplitItem = new MenuItem("取消拆分");
        cancelSplitItem.setOnAction(event -> moveTabToMain(tab));

        MenuItem openWindowItem = new MenuItem("独立窗口打开");
        openWindowItem.setOnAction(event -> openTabInWindow(tab));

        MenuItem returnToTabItem = new MenuItem("回到标签页");
        returnToTabItem.setOnAction(event -> returnTabToPane(tab));

        ContextMenu contextMenu = new ContextMenu(
                splitLeftItem, splitRightItem, cancelSplitItem,
                new SeparatorMenuItem(),
                openWindowItem, returnToTabItem
        );
        contextMenu.setOnShowing(event -> {
            TabPane owner = tab.getTabPane();
            splitLeftItem.setDisable(owner == null || owner == leftSplitTabPane);
            splitRightItem.setDisable(owner == null || owner == rightSplitTabPane);
            cancelSplitItem.setDisable(owner == null || owner == tabRootPane);
            boolean isDetached = detachedWindows.containsKey(tab);
            openWindowItem.setVisible(!isDetached);
            returnToTabItem.setVisible(isDetached);
        });
        return contextMenu;
    }

    private void handleTabClosed(Tab tab) {
        BaseStage detachedStage = detachedWindows.remove(tab);
        if (detachedStage != null) {
            detachedContents.remove(detachedStage);
            if (detachedStage.getStage().isShowing()) {
                detachedStage.getStage().hide();
            }
        }
        Runnable closeAction = tabCloseActions.remove(tab);
        if (closeAction != null) {
            closeAction.run();
        }
        cleanupEmptySplitTabPanes();
        refreshSplitLayout();
    }

    private void moveTabToSide(Tab tab, SplitSide splitSide) {
        TabPane sourcePane = tab.getTabPane();
        if (sourcePane == null) {
            return;
        }

        TabPane targetPane = ensureSplitTabPane(splitSide);
        if (sourcePane == targetPane) {
            return;
        }

        sourcePane.getTabs().remove(tab);
        targetPane.getTabs().add(tab);
        targetPane.getSelectionModel().select(tab);

        cleanupEmptySplitTabPanes();
        refreshSplitLayout();
    }

    private void moveTabToMain(Tab tab) {
        TabPane sourcePane = tab.getTabPane();
        if (sourcePane == null || sourcePane == tabRootPane) {
            return;
        }

        sourcePane.getTabs().remove(tab);
        tabRootPane.getTabs().add(tab);
        tabRootPane.getSelectionModel().select(tab);

        cleanupEmptySplitTabPanes();
        refreshSplitLayout();
    }

    private void openTabInWindow(Tab tab) {
        if (detachedWindows.containsKey(tab)) {
            return;
        }
        Node content = tab.getContent();
        if (content == null) {
            return;
        }
        tab.setContent(new Label("已独立打开 - 右键标签页可回到标签页"));

        BaseStage baseStage = new BaseStage();
        ToolBar toolBar = new ToolBar();
        toolBar.setMinHeight(40);
        baseStage.registryDragger(toolBar);
        VBox.setVgrow(content, Priority.ALWAYS);

        String originalTitle = tab.getText();
        baseStage.setTitle(originalTitle + " - LTSerialTool");
        baseStage.setContentView(new VBox(toolBar, content));
        baseStage.getStage().setOnHidden(event -> returnTabToPane(tab));

        detachedWindows.put(tab, baseStage);
        detachedContents.put(baseStage, content);

        Platform.runLater(baseStage::show);
    }

    private void returnTabToPane(Tab tab) {
        BaseStage baseStage = detachedWindows.remove(tab);
        if (baseStage == null) {
            return;
        }
        Node content = detachedContents.remove(baseStage);
        if (content != null && tab.getTabPane() != null) {
            tab.setContent(content);
        }
        if (baseStage.getStage().isShowing()) {
            baseStage.getStage().hide();
        }
    }

    private TabPane ensureSplitTabPane(SplitSide splitSide) {
        if (splitSide == SplitSide.LEFT) {
            if (leftSplitTabPane == null) {
                leftSplitTabPane = createSplitTabPane();
            }
            return leftSplitTabPane;
        }
        if (rightSplitTabPane == null) {
            rightSplitTabPane = createSplitTabPane();
        }
        return rightSplitTabPane;
    }

    private TabPane createSplitTabPane() {
        TabPane tabPane = new TabPane();
        tabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.ALL_TABS);
        return tabPane;
    }

    private void cleanupEmptySplitTabPanes() {
        if (leftSplitTabPane != null && leftSplitTabPane.getTabs().isEmpty()) {
            leftSplitTabPane = null;
        }
        if (rightSplitTabPane != null && rightSplitTabPane.getTabs().isEmpty()) {
            rightSplitTabPane = null;
        }
    }

    private void refreshSplitLayout() {
        boolean hasLeftPane = leftSplitTabPane != null && !leftSplitTabPane.getTabs().isEmpty();
        boolean hasRightPane = rightSplitTabPane != null && !rightSplitTabPane.getTabs().isEmpty();

        if (!hasLeftPane && !hasRightPane) {
            splitTabContainer.getItems().clear();
            rootPane.setCenter(tabRootPane);
            return;
        }

        ObservableList<Node> items = splitTabContainer.getItems();
        items.clear();
        if (hasLeftPane) {
            items.add(leftSplitTabPane);
        }
        items.add(tabRootPane);
        if (hasRightPane) {
            items.add(rightSplitTabPane);
        }
        rootPane.setCenter(splitTabContainer);

        Platform.runLater(() -> {
            if (hasLeftPane && hasRightPane) {
                splitTabContainer.setDividerPositions(0.25, 0.75);
            } else if (hasLeftPane) {
                splitTabContainer.setDividerPositions(0.3);
            } else {
                splitTabContainer.setDividerPositions(0.7);
            }
        });
    }

    private void disposeReceivePane(SerialReceivePane receivePane) {
        if (receivePane == null) {
            return;
        }
        receivePane.dispose();
        receivePanes.remove(receivePane);
    }

    private void initMenuButtons() {

        for (Node item : toolBar.getItems()) {
            item.getStyleClass().add("flat-menu-button");
            item.prefWidth(14);
            item.prefHeight(14);
        }
        mbNewTab.setGraphic(new FontIcon(ADD_BOX));
        mbFile.setGraphic(new FontIcon(ASSIGNMENT));
        mbSetting.setGraphic(new FontIcon(SETTINGS));
        mbAppearance.setGraphic(new FontIcon(INVERT_COLORS));
        mbTools.setGraphic(new FontIcon(TUNE));
        mbHelp.setGraphic(new FontIcon(INFO));

        Image logo = new Image(Objects.requireNonNull(getClass().getResourceAsStream("/image/logo.png")));
        ImageView logoView = new ImageView(logo);
        logoView.setFitWidth(38);
        logoView.setFitHeight(38);
        Region region = new Region();
        region.setMinHeight(10);
        toolBar.getItems().add(0, logoView);
        toolBar.getItems().add(1, region);
        currentTheme = ThemeComboBox.resolveDefaultTheme();

        autoSaveCheck.setSelected(AutoSaveService.isAutoSaveEnabled());
        autoSaveCheck.selectedProperty().addListener((obs, oldVal, newVal) -> {
            AutoSaveService.setAutoSaveEnabled(newVal);
            updateAutoSaveForAllPanes(newVal);
        });

        refreshReceiveTimeoutMenuText();
        refreshAutoSaveIntervalMenuText();
        refreshAutoSaveFileSizeMenuText();
        refreshBufferCapacityMenuText();
    }

    /**
     * 更新所有接收面板的自动保存状态
     */
    private void updateAutoSaveForAllPanes(boolean enabled) {
        for (SerialReceivePane pane : receivePanes) {
            pane.getController().updateAutoSaveService(enabled);
        }
    }

    @FXML
    public void setReceiveTimeout(ActionEvent actionEvent) {
        String currentValue = String.valueOf(getReceiveTimeoutMs());
        TextInputDialog dialog = new TextInputDialog(currentValue);
        dialog.setTitle("设置接收超时时间");
        dialog.setHeaderText("单位：ms。超过该时间未收到回车换行，会输出当前缓存数据。\n修改后新开启的接收串口生效。");
        dialog.setContentText("超时时间(ms):");
        FontSettingsManager.configureDialog(dialog);

        dialog.showAndWait().ifPresent(input -> {
            String value = input == null ? "" : input.trim();
            if (value.isEmpty()) {
                return;
            }
            try {
                int timeoutMs = Integer.parseInt(value);
                if (timeoutMs <= 0) {
                    throw new NumberFormatException("timeoutMs <= 0");
                }
                ConfigManager.set(KEY_RECEIVE_TIMEOUT_MS, String.valueOf(timeoutMs));
                refreshReceiveTimeoutMenuText();
            } catch (NumberFormatException ex) {
                Alert alert = new Alert(Alert.AlertType.ERROR);
                alert.setTitle("参数错误");
                alert.setHeaderText("接收超时时间必须是正整数");
                alert.setContentText("示例: 500 (单位 ms)");
                FontSettingsManager.configureDialog(alert);
                alert.showAndWait();
            }
        });
    }

    private int getReceiveTimeoutMs() {
        String timeout = ConfigManager.get(KEY_RECEIVE_TIMEOUT_MS, String.valueOf(DEFAULT_RECEIVE_TIMEOUT_MS));
        try {
            int value = Integer.parseInt(timeout);
            return value > 0 ? value : DEFAULT_RECEIVE_TIMEOUT_MS;
        } catch (NumberFormatException ex) {
            return DEFAULT_RECEIVE_TIMEOUT_MS;
        }
    }

    private void refreshReceiveTimeoutMenuText() {
        if (menuReceiveTimeout == null) {
            return;
        }
        menuReceiveTimeout.setText("设置接收超时时间 (" + getReceiveTimeoutMs() + "ms)");
    }

    // ========== 自动保存间隔时间设置 ==========

    @FXML
    public void setAutoSaveInterval(ActionEvent actionEvent) {
        int currentValue = AutoSaveService.getAutoSaveInterval();
        TextInputDialog dialog = new TextInputDialog(String.valueOf(currentValue));
        dialog.setTitle("设置自动保存间隔时间");
        dialog.setHeaderText("单位：秒。自动保存将按照此间隔触发。");
        dialog.setContentText("间隔时间(秒):");
        FontSettingsManager.configureDialog(dialog);

        dialog.showAndWait().ifPresent(input -> {
            String value = input.trim();
            if (value.isEmpty()) {
                return;
            }
            try {
                int interval = Integer.parseInt(value);
                if (interval < 1) {
                    throw new NumberFormatException("interval < 1");
                }
                AutoSaveService.setAutoSaveInterval(interval);
                refreshAutoSaveIntervalMenuText();
                UIUtil.showToast("自动保存间隔已设置为 " + interval + " 秒");
            } catch (NumberFormatException ex) {
                Alert alert = new Alert(Alert.AlertType.ERROR);
                alert.setTitle("参数错误");
                alert.setHeaderText("间隔时间必须是大于0的整数");
                alert.setContentText("示例: 5 (单位 秒)");
                FontSettingsManager.configureDialog(alert);
                alert.showAndWait();
            }
        });
    }

    private void refreshAutoSaveIntervalMenuText() {
        if (menuAutoSaveInterval == null) {
            return;
        }
        menuAutoSaveInterval.setText("设置自动保存间隔时间 (" + AutoSaveService.getAutoSaveInterval() + "秒)");
    }

    // ========== 自动保存文件大小设置 ==========

    @FXML
    public void setAutoSaveFileSize(ActionEvent actionEvent) {
        int currentSize = AutoSaveService.getAutoSaveFileSize();
        String currentUnit = AutoSaveService.getAutoSaveFileSizeUnit();

        Dialog<String[]> dialog = new Dialog<>();
        dialog.setTitle("设置自动保存文件大小");
        dialog.setHeaderText("设置单个日志文件的最大大小");

        // 设置按钮
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        // 创建输入布局
        TextField sizeField = new TextField(String.valueOf(currentSize));
        sizeField.setPrefWidth(80);

        ComboBox<String> unitBox = new ComboBox<>();
        unitBox.getItems().addAll("KB", "MB");
        unitBox.setValue(currentUnit);
        unitBox.setPrefWidth(80);

        HBox content = new HBox(10);
        content.getChildren().addAll(new Label("文件大小:"), sizeField, unitBox);
        content.setPadding(new javafx.geometry.Insets(10));

        dialog.getDialogPane().setContent(content);
        FontSettingsManager.configureDialog(dialog);

        // 结果转换器
        dialog.setResultConverter(buttonType -> {
            if (buttonType == ButtonType.OK) {
                return new String[]{sizeField.getText(), unitBox.getValue()};
            }
            return null;
        });

        dialog.showAndWait().ifPresent(result -> {
            try {
                int size = Integer.parseInt(result[0].trim());
                if (size < 1) {
                    throw new NumberFormatException("size < 1");
                }
                String unit = result[1];
                AutoSaveService.setAutoSaveFileSize(size, unit);
                refreshAutoSaveFileSizeMenuText();
                UIUtil.showToast("自动保存文件大小已设置为 " + size + unit);
            } catch (NumberFormatException ex) {
                Alert alert = new Alert(Alert.AlertType.ERROR);
                alert.setTitle("参数错误");
                alert.setHeaderText("文件大小必须是大于0的整数");
                alert.setContentText("示例: 10 (单位 MB)");
                FontSettingsManager.configureDialog(alert);
                alert.showAndWait();
            }
        });
    }

    private void refreshAutoSaveFileSizeMenuText() {
        if (menuAutoSaveFileSize == null) {
            return;
        }
        menuAutoSaveFileSize.setText("设置自动保存文件大小 (" + AutoSaveService.getAutoSaveFileSizeWithUnit() + ")");
    }

    // ========== 显示缓冲区容量设置 ==========

    public static final String KEY_BUFFER_CAPACITY = "main.form.bufferCapacity";
    public static final String KEY_BUFFER_CAPACITY_UNIT = "main.form.bufferCapacityUnit";
    public static final int DEFAULT_BUFFER_CAPACITY = 10; // 默认10MB
    public static final String DEFAULT_BUFFER_CAPACITY_UNIT = "MB";

    @FXML
    public void setBufferCapacity(ActionEvent actionEvent) {
        int currentSize = getBufferCapacity();
        String currentUnit = getBufferCapacityUnit();

        Dialog<String[]> dialog = new Dialog<>();
        dialog.setTitle("设置显示缓冲区容量");
        dialog.setHeaderText("设置接收数据在内存中的最大容量\n当数据达到此大小时，将丢弃最早的数据");

        // 设置按钮
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        // 创建输入布局
        TextField sizeField = new TextField(String.valueOf(currentSize));
        sizeField.setPrefWidth(80);

        ComboBox<String> unitBox = new ComboBox<>();
        unitBox.getItems().addAll("KB", "MB");
        unitBox.setValue(currentUnit);
        unitBox.setPrefWidth(80);

        HBox content = new HBox(10);
        content.getChildren().addAll(new Label("缓冲区大小:"), sizeField, unitBox);
        content.setPadding(new javafx.geometry.Insets(10));

        dialog.getDialogPane().setContent(content);
        FontSettingsManager.configureDialog(dialog);

        // 结果转换器
        dialog.setResultConverter(buttonType -> {
            if (buttonType == ButtonType.OK) {
                return new String[]{sizeField.getText(), unitBox.getValue()};
            }
            return null;
        });

        dialog.showAndWait().ifPresent(result -> {
            try {
                int size = Integer.parseInt(result[0].trim());
                if (size < 1) {
                    throw new NumberFormatException("size < 1");
                }
                String unit = result[1];
                setBufferCapacity(size, unit);
                refreshBufferCapacityMenuText();

                // 更新所有接收面板的缓冲区容量
                long capacityBytes = unit.equalsIgnoreCase("KB") ? size * 1024L : size * 1024L * 1024L;
                for (SerialReceivePane pane : receivePanes) {
                    pane.getController().updateBufferCapacity(capacityBytes);
                }

                UIUtil.showToast("显示缓冲区容量已设置为 " + size + unit);
            } catch (NumberFormatException ex) {
                Alert alert = new Alert(Alert.AlertType.ERROR);
                alert.setTitle("参数错误");
                alert.setHeaderText("容量必须是大于0的整数");
                alert.setContentText("示例: 10 (单位 MB)");
                FontSettingsManager.configureDialog(alert);
                alert.showAndWait();
            }
        });
    }

    private void refreshBufferCapacityMenuText() {
        if (menuBufferCapacity == null) {
            return;
        }
        menuBufferCapacity.setText("设置显示缓冲区容量 (" + getBufferCapacityWithUnit() + ")");
    }

    public static int getBufferCapacity() {
        return ConfigManager.get(KEY_BUFFER_CAPACITY, Integer.class, DEFAULT_BUFFER_CAPACITY);
    }

    public static String getBufferCapacityUnit() {
        String unit = ConfigManager.get(KEY_BUFFER_CAPACITY_UNIT, DEFAULT_BUFFER_CAPACITY_UNIT);
        return unit != null && (unit.equalsIgnoreCase("KB") || unit.equalsIgnoreCase("MB"))
                ? unit.toUpperCase()
                : DEFAULT_BUFFER_CAPACITY_UNIT;
    }

    public static String getBufferCapacityWithUnit() {
        return getBufferCapacity() + getBufferCapacityUnit();
    }

    public static void setBufferCapacity(int size, String unit) {
        if (size < 1) size = 1;
        String validUnit = (unit != null && unit.equalsIgnoreCase("KB")) ? "KB" : "MB";
        ConfigManager.set(KEY_BUFFER_CAPACITY, size);
        ConfigManager.set(KEY_BUFFER_CAPACITY_UNIT, validUnit);
    }

    // ========== 保存为压缩包 ==========

    @FXML
    public void saveAsZip(ActionEvent actionEvent) {
        // 收集所有实现了 ZipDataProvider 的控制器数据
        Map<String, String> allEntries = new LinkedHashMap<>();

        // 发送模式
        ZipDataProvider sendProvider = serialSendPane.getController();
        if (sendProvider != null) {
            allEntries.putAll(sendProvider.provideZipEntries());
        }

        // 所有接收面板（包括动态添加的）
        for (SerialReceivePane pane : receivePanes) {
            ZipDataProvider provider = pane.getController();
            if (provider != null) {
                allEntries.putAll(provider.provideZipEntries());
            }
        }

        // 过滤掉空内容
        Map<String, String> entriesToSave = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : allEntries.entrySet()) {
            if (entry.getValue() != null && !entry.getValue().isEmpty()) {
                entriesToSave.put(entry.getKey(), entry.getValue());
            }
        }

        if (entriesToSave.isEmpty()) {
            UIUtil.showToast("没有数据可保存");
            return;
        }

        // 选择保存位置
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("保存为压缩包");
        fileChooser.setInitialFileName("serial-data-" + java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")) + ".zip");
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("ZIP压缩包", "*.zip"));

        File file = fileChooser.showSaveDialog(rootPane.getScene().getWindow());
        if (file == null) {
            return;
        }

        // 选择分卷大小
        ChoiceDialog<String> sizeDialog = new ChoiceDialog<>("10MB", "1MB", "5MB", "10MB", "50MB", "100MB", "不分卷");
        sizeDialog.setTitle("设置分卷大小");
        sizeDialog.setHeaderText("选择每个分卷文件的大小");
        sizeDialog.setContentText("分卷大小:");
        FontSettingsManager.configureDialog(sizeDialog);

        sizeDialog.showAndWait().ifPresent(sizeStr -> {
            try {
                int partSizeMB;
                if ("不分卷".equals(sizeStr)) {
                    partSizeMB = 1024; // 1GB作为不分卷的阈值
                } else {
                    partSizeMB = Integer.parseInt(sizeStr.replace("MB", ""));
                }

                ZipUtil.saveEntriesToZip(entriesToSave, file, partSizeMB);

                UIUtil.showToast("数据已保存到: " + file.getName());
            } catch (IOException e) {
                LOG.error("保存压缩包失败", e);
                UIUtil.showToast("保存失败: " + e.getMessage());
            }
        });
    }

    @FXML
    private void showAppearanceSettingsDialog(ActionEvent actionEvent) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("外观设置");
        dialog.setHeaderText("设置主题、界面字体和文本框字体");
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        ThemeComboBox themeBox = new ThemeComboBox(currentTheme);
        FontFamilyComboBox uiFontBox = new FontFamilyComboBox(FontSettingsManager.getUiFontFamily());
        FontFamilyComboBox textFontBox = new FontFamilyComboBox(FontSettingsManager.getTextFontFamily());
        Label themeLabel = new Label("主题:");
        Label uiLabel = new Label("界面字体:");
        Label textLabel = new Label("文本框字体:");

        GridPane content = new GridPane();
        content.setHgap(12);
        content.setVgap(12);
        content.setPadding(new javafx.geometry.Insets(10));

        ColumnConstraints labelColumn = new ColumnConstraints();
        labelColumn.setMinWidth(Region.USE_PREF_SIZE);
        ColumnConstraints inputColumn = new ColumnConstraints();
        inputColumn.setHgrow(Priority.ALWAYS);
        inputColumn.setFillWidth(true);
        content.getColumnConstraints().addAll(labelColumn, inputColumn);

        content.add(themeLabel, 0, 0);
        content.add(themeBox, 1, 0);
        content.add(uiLabel, 0, 1);
        content.add(uiFontBox, 1, 1);
        content.add(textLabel, 0, 2);
        content.add(textFontBox, 1, 2);
        GridPane.setHgrow(themeBox, Priority.ALWAYS);
        GridPane.setFillWidth(themeBox, true);
        GridPane.setHgrow(uiFontBox, Priority.ALWAYS);
        GridPane.setHgrow(textFontBox, Priority.ALWAYS);
        GridPane.setFillWidth(uiFontBox, true);
        GridPane.setFillWidth(textFontBox, true);

        themeBox.prefWidthProperty().bind(content.widthProperty()
                .subtract(themeLabel.widthProperty())
                .subtract(40));
        uiFontBox.prefWidthProperty().bind(content.widthProperty()
                .subtract(uiLabel.widthProperty())
                .subtract(40));
        textFontBox.prefWidthProperty().bind(content.widthProperty()
                .subtract(textLabel.widthProperty())
                .subtract(40));

        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().setMinWidth(420);
        FontSettingsManager.configureDialog(dialog);

        dialog.showAndWait().ifPresent(buttonType -> {
            if (buttonType != ButtonType.OK) {
                return;
            }
            Theme appliedTheme = themeBox.applySelectedTheme();
            if (appliedTheme != null) {
                currentTheme = appliedTheme;
            }
            FontSettingsManager.saveFontFamilies(uiFontBox.getValue(), textFontBox.getValue());
            FontSettingsManager.applyToOpenWindows();
            UIUtil.showToast("外观设置已保存");
        });
    }

    public String getDividePosition() {
        return Arrays.stream(spReceive.getDividerPositions())
                .mapToObj(e -> new BigDecimal(e).toPlainString())
                .collect(Collectors.joining(","));
    }

    @FXML
    private void changeToReceiveMode() {
        spReceive.setVisible(true);
        serialSendPane.setVisible(false);
    }

    @FXML
    public void goToWebsite(ActionEvent actionEvent) {
        try {
            Desktop.getDesktop().browse(new URI("https://iggbiaodi.github.io/LTSerialToolWebSite/"));
        } catch (Exception ex) {
            LOG.error(ex);
        }
    }

    @FXML
    public void openAsciiTable(ActionEvent actionEvent) {
        AsciiStage.showStage();
    }

    @FXML
    public void addNewTab(ActionEvent actionEvent) {
        ObservableList<Tab> tabs = tabRootPane.getTabs();
        int index = ++receivePaneIndex;
        SerialReceivePane serialReceivePane = new SerialReceivePane("串口接收" + index, "serialKey" + index);
        receivePanes.add(serialReceivePane);
        Tab tab = createManagedTab("串口接收" + index, serialReceivePane, () -> disposeReceivePane(serialReceivePane), true);
        tabs.add(tab);
        tabRootPane.getSelectionModel().select(tab);
    }

    public void persistFormStateToConfig() {
        ConfigManager.put(KEY_AUTO_SAVE, String.valueOf(autoSaveCheck.isSelected()));
        serialSendPane.getController().persistFormStateToConfig();
        waveformPane.persistFormStateToConfig();
        for (SerialReceivePane pane : receivePanes) {
            pane.getController().persistFormStateToConfig();
        }
    }

    public List<CommandTableView.CommandItem> snapshotCommandTable() {
        return serialSendPane.getController().snapshotCommandTable();
    }
}
