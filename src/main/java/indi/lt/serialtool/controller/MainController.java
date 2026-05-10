package indi.lt.serialtool.controller;

import github.nonoas.jfx.flat.ui.theme.Theme;
import indi.lt.serialtool.SerialApplication;
import indi.lt.serialtool.component.CommandTableView;
import indi.lt.serialtool.global.ConfigManager;
import indi.lt.serialtool.global.FontSettingsManager;
import indi.lt.serialtool.global.ThemeManager;
import indi.lt.serialtool.service.AutoSaveService;
import indi.lt.serialtool.utils.ZipUtil;
import indi.lt.serialtool.utils.UIUtil;
import indi.lt.serialtool.view.AsciiStage;
import indi.lt.serialtool.view.SerialReceivePane;
import indi.lt.serialtool.view.SerialSendPane;
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
import javafx.scene.text.Font;
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.ResourceBundle;
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

    public MenuButton mbTheme;

    @FXML
    public ToolBar toolBar;

    public MenuButton mbFile;

    @FXML
    public MenuButton mbSetting;

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

    private final SplitPane spReceive = new SplitPane();

    private final ToolBar topToolBar = new ToolBar();


    @FXML
    private TabPane tabRootPane;

    private ToggleGroup themeGroup;
    private final List<SerialReceivePane> receivePanes = new ArrayList<>();


    public TabPane getMenuBar() {
        return tabRootPane;
    }

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        initMenuButtons();

        SerialReceivePane serialReceivePane1 = new SerialReceivePane("串口1:", "serialKey1");
        SerialReceivePane serialReceivePane2 = new SerialReceivePane("串口2:", "serialKey2");
        receivePanes.add(serialReceivePane1);
        receivePanes.add(serialReceivePane2);

        String dividePostions = ConfigManager.get(KEY_RECEIVE_SPLIT_PANE_DIVIDER_POSITIONS,"0.5");
        spReceive.getItems().addAll(serialReceivePane1, serialReceivePane2);
        double[] dividePositionList = Arrays.stream(dividePostions.split(",")).mapToDouble(Double::parseDouble).toArray();
        Platform.runLater(() -> spReceive.setDividerPositions(dividePositionList));

        Tab tabRec = new Tab("接收模式", spReceive);
        tabRec.setClosable(false);

        Tab tabSend = new Tab("发送模式", serialSendPane);
        tabSend.setClosable(false);
        tabRootPane.getTabs().addAll(tabSend, tabRec);
        tabRootPane.getSelectionModel().select(tabRec);
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
        mbTheme.setGraphic(new FontIcon(INVERT_COLORS));
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

        // 创建 ToggleGroup
        ToggleGroup themeGroup = new ToggleGroup();
        for (Theme theme : ThemeManager.getAll()) {
            RadioMenuItem radioMenuItem = new RadioMenuItem(theme.getName());
            radioMenuItem.setUserData(theme);
            radioMenuItem.setToggleGroup(themeGroup);
            mbTheme.getItems().add(radioMenuItem);
        }
        mbTheme.getItems().add(new SeparatorMenuItem());
        MenuItem fontSettingsItem = new MenuItem("字体设置");
        fontSettingsItem.setOnAction(this::showFontSettingsDialog);
        mbTheme.getItems().add(fontSettingsItem);

        // 监听选项变化
        themeGroup.selectedToggleProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                Theme theme = (Theme) newVal.getUserData();
                SerialApplication.setUserAgentStylesheet(theme.getUserAgentStylesheet());
            }
        });

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
        // 收集所有接收面板的数据
        List<String> allData = new ArrayList<>();
        int paneIndex = 1;
        for (SerialReceivePane pane : receivePanes) {
            String data = pane.getController().getOriginData();
            if (data != null && !data.isEmpty()) {
                allData.add("=== 串口接收" + paneIndex + " ===\n" + data);
            }
            paneIndex++;
        }

        if (allData.isEmpty()) {
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

                String combinedData = String.join("\n\n", allData);
                ZipUtil.saveToZip(combinedData, file, partSizeMB, "serial-data");

                UIUtil.showToast("数据已保存到: " + file.getName());
            } catch (IOException e) {
                LOG.error("保存压缩包失败", e);
                UIUtil.showToast("保存失败: " + e.getMessage());
            }
        });
    }

    private void showFontSettingsDialog(ActionEvent actionEvent) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("字体设置");
        dialog.setHeaderText("设置界面字体和文本框字体");
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        ComboBox<String> uiFontBox = createFontFamilyBox(FontSettingsManager.getUiFontFamily());
        ComboBox<String> textFontBox = createFontFamilyBox(FontSettingsManager.getTextFontFamily());
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

        content.add(uiLabel, 0, 0);
        content.add(uiFontBox, 1, 0);
        content.add(textLabel, 0, 1);
        content.add(textFontBox, 1, 1);
        GridPane.setHgrow(uiFontBox, Priority.ALWAYS);
        GridPane.setHgrow(textFontBox, Priority.ALWAYS);
        GridPane.setFillWidth(uiFontBox, true);
        GridPane.setFillWidth(textFontBox, true);

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
            FontSettingsManager.saveFontFamilies(uiFontBox.getValue(), textFontBox.getValue());
            FontSettingsManager.applyToOpenWindows();
            UIUtil.showToast("字体设置已保存");
        });
    }

    private ComboBox<String> createFontFamilyBox(String selectedFont) {
        ComboBox<String> fontBox = new ComboBox<>();
        fontBox.getItems().addAll(FontSettingsManager.getAvailableFontFamilies());
        fontBox.setValue(selectedFont);
        fontBox.setMaxWidth(Double.MAX_VALUE);
        fontBox.setVisibleRowCount(12);
        fontBox.setCellFactory(listView -> new ListCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setFont(Font.getDefault());
                    return;
                }
                setText(item);
                setFont(Font.font(item, 13));
            }
        });
        fontBox.setButtonCell(new ListCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setFont(Font.getDefault());
                    return;
                }
                setText(item);
                setFont(Font.font(item, 13));
            }
        });
        return fontBox;
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
        SerialReceivePane serialReceivePane = new SerialReceivePane("串口接收" + tabs.size(), "serialKey" + tabs.size());
        receivePanes.add(serialReceivePane);
        Tab tab = new Tab("串口接收" + tabs.size());
        tab.setContent(serialReceivePane);
        tabs.add(tab);
        tabRootPane.getSelectionModel().select(tab);
    }

    public void persistFormStateToConfig() {
        ConfigManager.put(KEY_AUTO_SAVE, String.valueOf(autoSaveCheck.isSelected()));
        serialSendPane.getController().persistFormStateToConfig();
        for (SerialReceivePane pane : receivePanes) {
            pane.getController().persistFormStateToConfig();
        }
    }

    public List<CommandTableView.CommandItem> snapshotCommandTable() {
        return serialSendPane.getController().snapshotCommandTable();
    }
}
