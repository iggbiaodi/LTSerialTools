package indi.lt.serialtool.view;

import com.fazecast.jSerialComm.SerialPort;
import github.nonoas.jfx.flat.ui.pane.JustifiedFlowPane;
import indi.lt.serialtool.component.MyStyleClassedTextArea;
import indi.lt.serialtool.component.SerialPortCombBox;
import indi.lt.serialtool.component.SerialToggleButton;
import indi.lt.serialtool.controller.SerialSettingsDialogCtrl;
import indi.lt.serialtool.data.SerialPortSettings;
import indi.lt.serialtool.data.WaveformDataType;
import indi.lt.serialtool.global.ConfigManager;
import indi.lt.serialtool.global.FontSettingsManager;
import indi.lt.serialtool.service.SerialReadService;
import indi.lt.serialtool.service.WaveformProtocolParser;
import indi.lt.serialtool.utils.UIUtil;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.geometry.VPos;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Label;
import javafx.scene.control.OverrunStyle;
import javafx.scene.control.ScrollBar;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Background;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.paint.Paint;
import javafx.scene.shape.StrokeLineCap;
import javafx.scene.text.Font;
import javafx.scene.text.TextAlignment;
import javafx.util.StringConverter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.IntSupplier;

/**
 * 波形图模式。
 */
public class WaveformPane extends BorderPane {
    private static final Logger LOG = LogManager.getLogger(WaveformPane.class);

    private static final String KEY_LAST_SERIAL = "waveformMode.lastSerial";
    private static final String KEY_TYPE = "waveformMode.form.dataType";
    private static final String KEY_MAX_POINTS = "waveformMode.form.maxPoints";
    private static final String KEY_VISIBLE_POINTS = "waveformMode.form.visiblePoints";
    private static final String KEY_SERIAL_SETTINGS = "waveformMode.serialSettings";
    private static final int MIN_POINT_COUNT = 10;
    private static final int MAX_POINT_COUNT = 50000;
    private static final int DEFAULT_MAX_POINTS = 1000;
    private static final int DEFAULT_VISIBLE_POINTS = 200;
    private static final int MAX_WAVE_COUNT = 8;
    private static final double ZOOM_IN_FACTOR = 0.8;
    private static final double ZOOM_OUT_FACTOR = 1.25;
    private static final double MIN_X_ZOOM_FACTOR = 0.1;
    private static final double MAX_X_ZOOM_FACTOR = 20.0;
    private static final double MIN_Y_ZOOM_FACTOR = 0.1;
    private static final double MAX_Y_ZOOM_FACTOR = 20.0;
    private static final double WAVEFORM_STROKE_WIDTH = 0.8;
    private static final double CHART_MIN_HEIGHT = 320.0;
    private static final double CHART_TITLE_HEIGHT = 34.0;
    private static final double CHART_LEFT_MARGIN = 70.0;
    private static final double CHART_RIGHT_MARGIN = 18.0;
    private static final double CHART_BOTTOM_MARGIN = 48.0;
    private static final double CHART_AXIS_LABEL_GAP = 20.0;
    private static final String CHART_TITLE = "实时波形图";
    private static final String X_AXIS_LABEL = "采样点";
    private static final String Y_AXIS_LABEL = "数据值";
    private static final Color DEFAULT_CHART_BACKGROUND_COLOR = Color.web("#f8f8f8");
    private static final Color DEFAULT_CHART_GRID_COLOR = Color.web("#e8e8e8");
    private static final Color DEFAULT_CHART_AXIS_COLOR = Color.web("#666666");
    private static final Color DEFAULT_CHART_TEXT_COLOR = Color.web("#333333");
    private static final double AXIS_TICK_ZERO_EPSILON = 1e-9;
    private static final String[] SERIES_COLORS = {
            "#ff0808",
            "#359e4d",
            "#0e06ea",
            "#ff8000",
            "#ec68e1",
            "#070307",
            "#c84164",
            "#aaaaaa"
    };

    private final Label lbSerialName = new Label("串口:");
    private final SerialPortCombBox cbSerialList = new SerialPortCombBox();
    private final SerialToggleButton btnOpenSerial = new SerialToggleButton();
    private final ComboBox<Integer> cbBaudRateList = new ComboBox<>();
    private final Button btnMoreSettings = new Button("更多设置");
    private final ComboBox<WaveformDataType> cbDataType = new ComboBox<>();
    private final Spinner<Integer> spMaxPoints = new Spinner<>();
    private final Spinner<Integer> spVisiblePoints = new Spinner<>();
    private final Button btnApplyMaxPoints = new Button("应用设置");
    private final Button btnClearWaveform = new Button("清空波形");
    private final Button btnZoomXIn = new Button("X+");
    private final Button btnZoomXOut = new Button("X-");
    private final Button btnZoomYIn = new Button("Y+");
    private final Button btnZoomYOut = new Button("Y-");
    private final Button btnResetView = new Button("重置视图");
    private final Label lbRecvBytes = new Label("0 B");
    private final Label lbFrameInfo = new Label("等待数据");
    private final Label lbViewInfo = new Label("显示窗口: 0 点");
    private final JustifiedFlowPane legendBox = new JustifiedFlowPane(20, 10, 160);

    private final Canvas waveformCanvas = new Canvas();
    private final Pane waveformCanvasPane = new Pane(waveformCanvas);
    private final Region chartBackgroundColorProbe = new Region();
    private final Region chartGridColorProbe = new Region();
    private final Region chartAxisColorProbe = new Region();
    private final Region chartTextColorProbe = new Region();
    private final ScrollBar sbTimeline = new ScrollBar();

    private final List<WaveformSeriesData> waveformSeries = new ArrayList<>();
    private final List<WaveformLegendItem> legendItems = new ArrayList<>();
    private final List<Double> frameTimes = new ArrayList<>();
    private final ConcurrentLinkedQueue<FrameSnapshot> pendingFrames = new ConcurrentLinkedQueue<>();
    private final AtomicBoolean uiRefreshQueued = new AtomicBoolean(false);
    private final MyStyleClassedTextArea dummyTextArea = new MyStyleClassedTextArea();

    private final WaveformProtocolParser protocolParser;

    private SerialReadService serialReadService;
    private SerialPortSettings serialPortSettings = SerialPortSettings.createDefault();
    private long startNanoTime = System.nanoTime();
    private int maxPoints = DEFAULT_MAX_POINTS;
    private int visiblePoints = DEFAULT_VISIBLE_POINTS;
    private int viewStartIndex;
    private boolean followLatest = true;
    private boolean updatingTimeline = false;
    private double xZoomFactor = 1.0;
    private double yZoomFactor = 1.0;
    private double xLowerBound = -1.0;
    private double xUpperBound = 1.0;
    private double yLowerBound = -1.0;
    private double yUpperBound = 1.0;
    private double xTickUnit = 0.5;
    private double yTickUnit = 0.5;
    private int nextDataIndex = 0;

    public WaveformPane() {
        protocolParser = new WaveformProtocolParser(getSelectedDataTypeOrDefault(), this::handleParsedFrame);
        buildUi();
        initBaudRateList();
        initDataTypeList();
        restoreState();
        bindActions();
    }

    private void buildUi() {
        setPadding(new Insets(10));

        btnOpenSerial.setSelectedText("关闭");
        btnOpenSerial.setUnSelectedText("打开");
        btnOpenSerial.setText("打开");
        btnOpenSerial.setPrefWidth(USE_COMPUTED_SIZE);

        cbSerialList.setMinWidth(220);
        cbBaudRateList.setEditable(true);
        cbBaudRateList.setMinWidth(140);
        cbDataType.setMinWidth(120);
        configurePointSpinner(spMaxPoints, DEFAULT_MAX_POINTS, () -> maxPoints);
        configurePointSpinner(spVisiblePoints, DEFAULT_VISIBLE_POINTS, () -> visiblePoints);

        HBox serialBox = new HBox(10, lbSerialName, cbSerialList, btnOpenSerial);
        serialBox.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(cbSerialList, Priority.ALWAYS);

        HBox paramBox = new HBox(10, new Label("波特率:"), cbBaudRateList, btnMoreSettings);
        paramBox.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(cbBaudRateList, Priority.ALWAYS);

        FlowPane topRow = new FlowPane(20, 10, serialBox, paramBox);
        topRow.setPrefWrapLength(900);

        FlowPane waveformConfigBox = new FlowPane(
                10,
                10,
                new Label("数据类型:"),
                cbDataType,
                new Label("保留点数:"),
                spMaxPoints,
                new Label("显示点数:"),
                spVisiblePoints,
                btnApplyMaxPoints,
                btnClearWaveform
        );
        waveformConfigBox.setAlignment(Pos.CENTER_LEFT);

        FlowPane viewControlBox = new FlowPane(
                10,
                10,
                new Label("缩放:"),
                btnZoomXIn,
                btnZoomXOut,
                btnZoomYIn,
                btnZoomYOut,
                btnResetView,
                lbViewInfo
        );
        viewControlBox.setAlignment(Pos.CENTER_LEFT);

        resetAxisBounds();
        waveformCanvasPane.setMinHeight(CHART_MIN_HEIGHT);
        waveformCanvasPane.setPrefHeight(CHART_MIN_HEIGHT);
        waveformCanvasPane.setFocusTraversable(true);
        waveformCanvas.widthProperty().bind(waveformCanvasPane.widthProperty());
        waveformCanvas.heightProperty().bind(waveformCanvasPane.heightProperty());
        waveformCanvas.widthProperty().addListener((obs, oldVal, newVal) -> drawWaveform());
        waveformCanvas.heightProperty().addListener((obs, oldVal, newVal) -> drawWaveform());
        configureThemeColorProbe(chartBackgroundColorProbe, "-color-bg-default");
        configureThemeColorProbe(chartGridColorProbe, "-color-border-default");
        configureThemeColorProbe(chartAxisColorProbe, "-color-fg-subtle");
        configureThemeColorProbe(chartTextColorProbe, "-color-fg-default");
        waveformCanvasPane.getChildren().addAll(
                chartBackgroundColorProbe,
                chartGridColorProbe,
                chartAxisColorProbe,
                chartTextColorProbe
        );

        legendBox.setPadding(new Insets(8, 0, 0, 0));

        sbTimeline.setOrientation(Orientation.HORIZONTAL);
        sbTimeline.setMin(0);
        sbTimeline.setMax(0);
        sbTimeline.setVisibleAmount(DEFAULT_VISIBLE_POINTS);
        sbTimeline.setDisable(true);

        HBox statusBar = new HBox(16, new Label("接收量:"), lbRecvBytes, createSpacer(), lbFrameInfo);
        statusBar.setAlignment(Pos.CENTER_LEFT);

        VBox root = new VBox(10, topRow, waveformConfigBox, viewControlBox, waveformCanvasPane, sbTimeline, legendBox, statusBar);
        VBox.setVgrow(waveformCanvasPane, Priority.ALWAYS);
        setCenter(root);
        Platform.runLater(this::drawWaveform);
    }

    private void configurePointSpinner(Spinner<Integer> spinner, int defaultValue, IntSupplier fallbackSupplier) {
        spinner.setEditable(true);
        spinner.setPrefWidth(120);
        SpinnerValueFactory.IntegerSpinnerValueFactory valueFactory =
                new SpinnerValueFactory.IntegerSpinnerValueFactory(MIN_POINT_COUNT, MAX_POINT_COUNT, defaultValue, 100);
        spinner.setValueFactory(valueFactory);
        spinner.getValueFactory().setConverter(new StringConverter<>() {
            @Override
            public String toString(Integer value) {
                return value == null ? "" : value.toString();
            }

            @Override
            public Integer fromString(String string) {
                if (string == null || string.isBlank()) {
                    return fallbackSupplier.getAsInt();
                }
                try {
                    int value = Integer.parseInt(string.trim());
                    return clampInt(value, MIN_POINT_COUNT, MAX_POINT_COUNT);
                } catch (NumberFormatException ex) {
                    return fallbackSupplier.getAsInt();
                }
            }
        });
    }

    private Region createSpacer() {
        Region region = new Region();
        HBox.setHgrow(region, Priority.ALWAYS);
        return region;
    }

    private void initBaudRateList() {
        cbBaudRateList.setItems(FXCollections.observableArrayList(
                1200, 2400, 4800, 9600, 38400, 57600, 115200, 230400, 1500000, 2000000, 3000000
        ));
    }

    private void initDataTypeList() {
        cbDataType.setItems(FXCollections.observableArrayList(WaveformDataType.values()));
    }

    private void restoreState() {
        serialPortSettings = ConfigManager.getObject(
                KEY_SERIAL_SETTINGS,
                SerialPortSettings.class,
                SerialPortSettings.createDefault()
        );
        cbSerialList.setSerialPortSettings(serialPortSettings);
        cbBaudRateList.getSelectionModel().select(serialPortSettings.getBaudRate());
        cbBaudRateList.setValue(serialPortSettings.getBaudRate());

        WaveformDataType savedType = WaveformDataType.UNSIGNED_16;
        String savedTypeName = ConfigManager.get(KEY_TYPE, savedType.name());
        try {
            savedType = WaveformDataType.valueOf(savedTypeName);
        } catch (IllegalArgumentException ignore) {
            savedType = WaveformDataType.UNSIGNED_16;
        }
        cbDataType.getSelectionModel().select(savedType);
        protocolParser.setDataType(savedType);

        maxPoints = ConfigManager.get(KEY_MAX_POINTS, Integer.class, DEFAULT_MAX_POINTS);
        maxPoints = clampInt(maxPoints, MIN_POINT_COUNT, MAX_POINT_COUNT);
        spMaxPoints.getValueFactory().setValue(maxPoints);

        visiblePoints = ConfigManager.get(KEY_VISIBLE_POINTS, Integer.class, DEFAULT_VISIBLE_POINTS);
        visiblePoints = clampInt(visiblePoints, MIN_POINT_COUNT, maxPoints);
        spVisiblePoints.getValueFactory().setValue(visiblePoints);

        cbSerialList.init(
                KEY_LAST_SERIAL,
                () -> UIUtil.getSelectedInt(cbBaudRateList, serialPortSettings.getBaudRate()),
                btnOpenSerial.selectedProperty(),
                SerialPort.TIMEOUT_READ_SEMI_BLOCKING,
                getReceiveTimeoutMs(),
                serialPortSettings
        );
    }

    private void bindActions() {
        btnMoreSettings.setOnAction(event -> showMoreSettings());
        btnApplyMaxPoints.setOnAction(event -> applyPointSettings());
        btnClearWaveform.setOnAction(event -> clearWaveform());
        btnZoomXIn.setOnAction(event -> zoomXAxis(true));
        btnZoomXOut.setOnAction(event -> zoomXAxis(false));
        btnZoomYIn.setOnAction(event -> zoomYAxis(true));
        btnZoomYOut.setOnAction(event -> zoomYAxis(false));
        btnResetView.setOnAction(event -> resetView());
        waveformCanvasPane.setOnScroll(event -> {
            if (frameTimes.isEmpty()) {
                return;
            }
            if (event.isControlDown()) {
                zoomYAxis(event.getDeltaY() > 0);
            } else {
                zoomXAxis(event.getDeltaY() > 0);
            }
            event.consume();
        });
        sbTimeline.valueProperty().addListener((obs, oldVal, newVal) -> {
            if (updatingTimeline || frameTimes.isEmpty()) {
                return;
            }
            int maxStart = Math.max(0, frameTimes.size() - getCurrentWindowPointCount());
            viewStartIndex = clampInt((int) Math.round(newVal.doubleValue()), 0, maxStart);
            followLatest = viewStartIndex >= maxStart;
            refreshViewport();
        });

        cbDataType.valueProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal == null) {
                return;
            }
            protocolParser.setDataType(newVal);
            ConfigManager.put(KEY_TYPE, newVal.name());
            clearWaveform();
        });

        cbBaudRateList.valueProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal == null) {
                return;
            }
            serialPortSettings.setBaudRate(newVal);
            cbSerialList.setSerialPortSettings(serialPortSettings);
            ConfigManager.putObject(KEY_SERIAL_SETTINGS, serialPortSettings);
            onBaudRateChanged();
        });

        btnOpenSerial.selectedProperty().addListener((obs, oldVal, selected) -> {
            if (selected) {
                btnOpenSerial.setDisable(true);
                cbSerialList.setTimeoutMillis(getReceiveTimeoutMs());
                cbSerialList.openSelectedSerial();
            } else {
                closeSelectedSerial();
            }
        });

        cbSerialList.disableProperty().bind(btnOpenSerial.disableProperty());
        cbSerialList.setOnOpenSucceed(this::onSerialOpenSucceed);
        cbSerialList.setOnOpenFailed(() -> {
            btnOpenSerial.setDisable(false);
            btnOpenSerial.setSelected(false);
            lbFrameInfo.setText("串口打开失败");
        });
    }

    private void onSerialOpenSucceed() {
        btnOpenSerial.setDisable(false);
        resetChartState();

        serialReadService = new SerialReadService(
                Objects.requireNonNull(cbSerialList.getSelectedPort()),
                dummyTextArea,
                new javafx.beans.property.SimpleBooleanProperty(false),
                new javafx.beans.property.SimpleBooleanProperty(false),
                getReceiveTimeoutMs(),
                () -> {
                }
        );
        serialReadService.setInternalAppendEnabled(false);
        serialReadService.setDecodeTextEnabled(false);
        serialReadService.setOnRecvBytesChanged(bytes ->
                Platform.runLater(() -> lbRecvBytes.setText(formatBytes(bytes)))
        );
        serialReadService.setOnRawBytesReceived(protocolParser::accept);
        serialReadService.start();

        lbFrameInfo.setText("已开始采集波形");
    }

    private void resetChartState() {
        startNanoTime = System.nanoTime();
        protocolParser.reset();
        pendingFrames.clear();
        uiRefreshQueued.set(false);
        frameTimes.clear();
        viewStartIndex = 0;
        followLatest = true;
        xZoomFactor = 1.0;
        yZoomFactor = 1.0;
        nextDataIndex = 0;
        runOnFx(() -> {
            legendBox.getChildren().clear();
            waveformSeries.clear();
            legendItems.clear();
            lbRecvBytes.setText("0 B");
            lbFrameInfo.setText("等待数据");
            refreshViewport();
        });
    }

    private void handleParsedFrame(WaveformProtocolParser.FrameData frameData) {
        double timeSeconds = (System.nanoTime() - startNanoTime) / 1_000_000_000.0;
        pendingFrames.offer(new FrameSnapshot(timeSeconds, frameData));
        scheduleUiRefresh();
    }

    private void scheduleUiRefresh() {
        if (!uiRefreshQueued.compareAndSet(false, true)) {
            return;
        }
        Platform.runLater(() -> {
            try {
                FrameSnapshot snapshot;
                while ((snapshot = pendingFrames.poll()) != null) {
                    renderFrame(snapshot);
                }
            } finally {
                uiRefreshQueued.set(false);
                if (!pendingFrames.isEmpty()) {
                    scheduleUiRefresh();
                }
            }
        });
    }

    private void renderFrame(FrameSnapshot snapshot) {
        double[] values = snapshot.frameData().getValues();
        frameTimes.add(snapshot.timeSeconds());
        trimFrameTimes();
        ensureSeriesCount(values.length);
        int currentIndex = nextDataIndex++;
        for (int i = 0; i < values.length; i++) {
            WaveformSeriesData series = waveformSeries.get(i);
            series.points().add(new WaveformPoint(currentIndex, values[i]));
            trimSeries(series);
            legendItems.get(i).setCurrentValue(values[i]);
        }
        refreshViewport();

        StringBuilder info = new StringBuilder("通道数: ")
                .append(snapshot.frameData().getRawValueCount())
                .append("，载荷字节: ")
                .append(snapshot.frameData().getPayloadLength());
        if (snapshot.frameData().isTruncated()) {
            info.append("，仅绘制前").append(MAX_WAVE_COUNT).append("条");
        }
        if (snapshot.frameData().isRemainderIgnored()) {
            info.append("，尾部不足一个完整数据已忽略");
        }
        lbFrameInfo.setText(info.toString());
    }

    private void ensureSeriesCount(int count) {
        int targetCount = Math.min(count, MAX_WAVE_COUNT);
        while (waveformSeries.size() < targetCount) {
            int channelIndex = waveformSeries.size();
            WaveformSeriesData series = new WaveformSeriesData("波形" + (channelIndex + 1));
            waveformSeries.add(series);
            WaveformLegendItem legendItem = new WaveformLegendItem(channelIndex, series);
            legendItems.add(legendItem);
            legendBox.getChildren().add(legendItem.container());
        }
    }

    private void trimSeries(WaveformSeriesData series) {
        int overflow = series.points().size() - maxPoints;
        if (overflow <= 0) {
            return;
        }
        series.points().subList(0, overflow).clear();
    }

    private void trimFrameTimes() {
        int overflow = frameTimes.size() - maxPoints;
        if (overflow <= 0) {
            return;
        }
        frameTimes.subList(0, overflow).clear();
        viewStartIndex = Math.max(0, viewStartIndex - overflow);
    }

    private void applyPointSettings() {
        Integer value = parseMaxPointsFromEditor();
        Integer visibleValue = parseVisiblePointsFromEditor();
        if (value == null || value < MIN_POINT_COUNT) {
            UIUtil.showToast("保留点数必须大于等于10");
            return;
        }
        if (visibleValue == null || visibleValue < MIN_POINT_COUNT) {
            UIUtil.showToast("显示点数必须大于等于10");
            return;
        }
        maxPoints = Math.min(MAX_POINT_COUNT, value);
        visiblePoints = Math.min(maxPoints, visibleValue);
        spMaxPoints.getValueFactory().setValue(maxPoints);
        spVisiblePoints.getValueFactory().setValue(visiblePoints);
        ConfigManager.put(KEY_MAX_POINTS, maxPoints);
        ConfigManager.put(KEY_VISIBLE_POINTS, visiblePoints);
        runOnFx(() -> {
            trimFrameTimes();
            waveformSeries.forEach(this::trimSeries);
            refreshViewport();
        });
        lbFrameInfo.setText("保留点数 " + maxPoints + "，显示点数 " + visiblePoints);
    }

    private void clearWaveform() {
        protocolParser.reset();
        pendingFrames.clear();
        nextDataIndex = 0;
        runOnFx(() -> {
            frameTimes.clear();
            legendBox.getChildren().clear();
            waveformSeries.clear();
            legendItems.clear();
            resetViewState();
            refreshViewport();
            lbFrameInfo.setText("波形已清空");
        });
    }

    private void onBaudRateChanged() {
        if (!cbSerialList.isActive()) {
            return;
        }
        closeSelectedSerial();
        cbSerialList.setTimeoutMillis(getReceiveTimeoutMs());
        cbSerialList.openSelectedSerial();
    }

    private void closeSelectedSerial() {
        if (serialReadService != null) {
            serialReadService.cancel();
            serialReadService = null;
        }
        cbSerialList.closeSelectSerial();
        runOnFx(() -> lbFrameInfo.setText("串口已关闭"));
    }

    public void dispose() {
        pendingFrames.clear();
        closeSelectedSerial();
    }

    private int getReceiveTimeoutMs() {
        String rawTimeout = ConfigManager.get(
                ConfigManager.KEY_RECEIVE_TIMEOUT_MS,
                String.valueOf(ConfigManager.DEFAULT_RECEIVE_TIMEOUT_MS)
        );
        try {
            int timeout = Integer.parseInt(rawTimeout);
            return timeout > 0 ? timeout : ConfigManager.DEFAULT_RECEIVE_TIMEOUT_MS;
        } catch (NumberFormatException ex) {
            return ConfigManager.DEFAULT_RECEIVE_TIMEOUT_MS;
        }
    }

    private void showMoreSettings() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/serial-settings-dialog.fxml"));
            DialogPane dialogPane = loader.load();
            SerialSettingsDialogCtrl dialogCtrl = loader.getController();
            dialogCtrl.setSettings(serialPortSettings);

            Dialog<SerialPortSettings> dialog = new Dialog<>();
            dialog.setDialogPane(dialogPane);
            dialog.setTitle("串口参数设置");
            dialog.setHeaderText("自定义串口参数");
            FontSettingsManager.configureDialog(dialog);
            dialog.setResultConverter(buttonType -> {
                if (buttonType == javafx.scene.control.ButtonType.OK) {
                    dialogCtrl.updateSettingsFromUI();
                    return dialogCtrl.getSettings();
                }
                return null;
            });

            dialog.showAndWait().ifPresent(settings -> {
                serialPortSettings = settings;
                cbSerialList.setSerialPortSettings(settings);
                cbBaudRateList.getSelectionModel().select(settings.getBaudRate());
                cbBaudRateList.setValue(settings.getBaudRate());
                ConfigManager.putObject(KEY_SERIAL_SETTINGS, settings);
                UIUtil.showToast("串口参数已更新");
            });
        } catch (IOException ex) {
            LOG.error("显示串口设置对话框失败", ex);
            UIUtil.showToast("打开设置对话框失败");
        }
    }

    private WaveformDataType getSelectedDataTypeOrDefault() {
        WaveformDataType value = cbDataType.getValue();
        return value != null ? value : WaveformDataType.UNSIGNED_16;
    }

    public void persistFormStateToConfig() {
        ConfigManager.put(KEY_TYPE, getSelectedDataTypeOrDefault().name());
        Integer editorValue = parseMaxPointsFromEditor();
        if (editorValue != null && editorValue >= MIN_POINT_COUNT) {
            maxPoints = Math.min(MAX_POINT_COUNT, editorValue);
        }
        Integer visibleEditorValue = parseVisiblePointsFromEditor();
        if (visibleEditorValue != null && visibleEditorValue >= MIN_POINT_COUNT) {
            visiblePoints = clampInt(visibleEditorValue, MIN_POINT_COUNT, maxPoints);
        }
        ConfigManager.put(KEY_MAX_POINTS, String.valueOf(maxPoints));
        ConfigManager.put(KEY_VISIBLE_POINTS, String.valueOf(visiblePoints));
        ConfigManager.putObject(KEY_SERIAL_SETTINGS, serialPortSettings);
    }

    private Integer parseMaxPointsFromEditor() {
        try {
            return spMaxPoints.getValueFactory().getConverter().fromString(spMaxPoints.getEditor().getText());
        } catch (Exception ex) {
            return spMaxPoints.getValue();
        }
    }

    private Integer parseVisiblePointsFromEditor() {
        try {
            return spVisiblePoints.getValueFactory().getConverter().fromString(spVisiblePoints.getEditor().getText());
        } catch (Exception ex) {
            return spVisiblePoints.getValue();
        }
    }

    private void zoomXAxis(boolean zoomIn) {
        if (frameTimes.isEmpty()) {
            return;
        }
        int oldWindow = getCurrentWindowPointCount();
        double centerIndex = viewStartIndex + Math.max(0, oldWindow - 1) / 2.0;
        double factor = zoomIn ? ZOOM_IN_FACTOR : ZOOM_OUT_FACTOR;
        xZoomFactor = clampDouble(xZoomFactor * factor, MIN_X_ZOOM_FACTOR, MAX_X_ZOOM_FACTOR);
        int newWindow = getCurrentWindowPointCount();
        if (!followLatest) {
            viewStartIndex = Math.max(0, (int) Math.round(centerIndex - Math.max(0, newWindow - 1) / 2.0));
        }
        refreshViewport();
    }

    private void zoomYAxis(boolean zoomIn) {
        if (frameTimes.isEmpty()) {
            return;
        }
        double factor = zoomIn ? ZOOM_IN_FACTOR : ZOOM_OUT_FACTOR;
        yZoomFactor = clampDouble(yZoomFactor * factor, MIN_Y_ZOOM_FACTOR, MAX_Y_ZOOM_FACTOR);
        refreshViewport();
    }

    private void resetView() {
        resetViewState();
        refreshViewport();
    }

    private void resetViewState() {
        viewStartIndex = 0;
        followLatest = true;
        xZoomFactor = 1.0;
        yZoomFactor = 1.0;
    }

    private void refreshViewport() {
        if (frameTimes.isEmpty()) {
            resetAxisBounds();
            updateTimeline(0, 0);
            lbViewInfo.setText("显示窗口: 0 点");
            drawWaveform();
            return;
        }

        int windowPointCount = Math.min(frameTimes.size(), getCurrentWindowPointCount());
        int maxStart = Math.max(0, frameTimes.size() - windowPointCount);
        if (followLatest) {
            viewStartIndex = maxStart;
        } else {
            viewStartIndex = clampInt(viewStartIndex, 0, maxStart);
        }
        int endIndex = Math.min(frameTimes.size() - 1, viewStartIndex + windowPointCount - 1);

        double lowerX;
        double upperX;
        if (waveformSeries.isEmpty() || waveformSeries.get(0).points().isEmpty()) {
            lowerX = viewStartIndex;
            upperX = endIndex;
        } else {
            List<WaveformPoint> data = waveformSeries.get(0).points();
            int dataStart = Math.min(viewStartIndex, data.size() - 1);
            int dataEnd = Math.min(endIndex, data.size() - 1);
            lowerX = data.get(Math.max(0, dataStart)).xIndex();
            upperX = data.get(Math.max(0, dataEnd)).xIndex();
        }
        double xSpan = Math.max(upperX - lowerX, 1);
        double xPadding = Math.max(xSpan * 0.02, 0.5);
        xLowerBound = lowerX - xPadding;
        xUpperBound = upperX + xPadding;

        updateYAxis(lowerX, upperX);
        updateTimeline(windowPointCount, maxStart);
        updateTickUnits();
        drawWaveform();
        lbViewInfo.setText(
                "显示窗口: " + windowPointCount + " 点  X缩放:" + formatZoomText(xZoomFactor)
                        + "  Y缩放:" + formatZoomText(yZoomFactor)
        );
    }

    private void updateYAxis(double lowerX, double upperX) {
        double min = Double.POSITIVE_INFINITY;
        double max = Double.NEGATIVE_INFINITY;

        for (WaveformSeriesData series : waveformSeries) {
            int seriesIndex = waveformSeries.indexOf(series);
            if (seriesIndex >= 0 && seriesIndex < legendItems.size() && !legendItems.get(seriesIndex).isVisible()) {
                continue;
            }
            List<WaveformPoint> data = series.points();
            for (int i = data.size() - 1; i >= 0; i--) {
                WaveformPoint point = data.get(i);
                double x = point.xIndex();
                if (x > upperX) {
                    continue;
                }
                if (x < lowerX) {
                    break;
                }
                double y = point.value();
                if (y < min) {
                    min = y;
                }
                if (y > max) {
                    max = y;
                }
            }
        }

        if (!Double.isFinite(min) || !Double.isFinite(max)) {
            yLowerBound = 0;
            yUpperBound = 1;
            return;
        }

        double center = (min + max) / 2.0;
        double halfSpan;
        if (Math.abs(max - min) < 0.000001) {
            halfSpan = Math.max(Math.abs(center) * 0.1, 1.0) * yZoomFactor;
        } else {
            halfSpan = ((max - min) / 2.0) * 1.1 * yZoomFactor;
        }
        halfSpan = Math.max(halfSpan, 0.5);
        yLowerBound = center - halfSpan;
        yUpperBound = center + halfSpan;
    }

    private void updateTimeline(int windowPointCount, int maxStart) {
        updatingTimeline = true;
        try {
            sbTimeline.setDisable(frameTimes.isEmpty());
            sbTimeline.setMin(0);
            sbTimeline.setMax(maxStart);
            sbTimeline.setVisibleAmount(Math.max(1, windowPointCount));
            sbTimeline.setUnitIncrement(1);
            sbTimeline.setBlockIncrement(Math.max(1, windowPointCount / 2.0));
            sbTimeline.setValue(viewStartIndex);
        } finally {
            updatingTimeline = false;
        }
    }

    private int getCurrentWindowPointCount() {
        int scaled = (int) Math.round(visiblePoints * xZoomFactor);
        return clampInt(scaled, 2, maxPoints);
    }

    private void resetAxisBounds() {
        xLowerBound = -1;
        xUpperBound = 1;
        yLowerBound = -1;
        yUpperBound = 1;
        updateTickUnits();
    }

    /**
     * 根据当前可见范围动态设置坐标轴刻度间距，保持网格线稀疏（约5~8条）。
     */
    private void updateTickUnits() {
        double xRange = xUpperBound - xLowerBound;
        double yRange = yUpperBound - yLowerBound;

        if (xRange > 0) {
            xTickUnit = niceTick(xRange, 6);
        }
        if (yRange > 0) {
            yTickUnit = niceTick(yRange, 6);
        }
    }

    /**
     * 计算一个"好看"的刻度间距，目标约 targetTicks 条网格线。
     */
    private static double niceTick(double range, int targetTicks) {
        double raw = range / targetTicks;
        double exp = Math.pow(10, Math.floor(Math.log10(raw)));
        double mantissa = raw / exp;
        double nice;
        if (mantissa <= 1.5) {
            nice = 1.0;
        } else if (mantissa <= 3.5) {
            nice = 2.0;
        } else if (mantissa <= 7.5) {
            nice = 5.0;
        } else {
            nice = 10.0;
        }
        return nice * exp;
    }

    private void drawWaveform() {
        double width = waveformCanvas.getWidth();
        double height = waveformCanvas.getHeight();
        GraphicsContext gc = waveformCanvas.getGraphicsContext2D();
        gc.clearRect(0, 0, width, height);
        if (width <= 0 || height <= 0) {
            return;
        }

        double plotLeft = Math.min(CHART_LEFT_MARGIN, Math.max(42.0, width * 0.28));
        double plotRight = Math.max(plotLeft + 1.0, width - CHART_RIGHT_MARGIN);
        double plotTop = CHART_TITLE_HEIGHT;
        double plotBottom = Math.max(plotTop + 1.0, height - CHART_BOTTOM_MARGIN);
        double plotWidth = plotRight - plotLeft;
        double plotHeight = plotBottom - plotTop;
        ChartColors colors = resolveChartColors();

        gc.setFont(Font.getDefault());
        gc.setTextBaseline(VPos.CENTER);
        drawChartText(gc, CHART_TITLE, width / 2.0, CHART_TITLE_HEIGHT / 2.0, TextAlignment.CENTER, colors.text());

        gc.setFill(colors.background());
        gc.fillRect(plotLeft, plotTop, plotWidth, plotHeight);

        drawAxesAndGrid(gc, plotLeft, plotTop, plotRight, plotBottom, colors);
        drawSeries(gc, plotLeft, plotTop, plotRight, plotBottom);
    }

    private void drawAxesAndGrid(
            GraphicsContext gc,
            double plotLeft,
            double plotTop,
            double plotRight,
            double plotBottom,
            ChartColors colors
    ) {
        double plotWidth = plotRight - plotLeft;
        double plotHeight = plotBottom - plotTop;
        double yRange = Math.max(yUpperBound - yLowerBound, 0.000001);
        double xRange = Math.max(xUpperBound - xLowerBound, 0.000001);

        gc.setLineWidth(0.5);
        gc.setStroke(colors.grid());
        for (double tick = firstTick(yLowerBound, yTickUnit); tick <= yUpperBound + yTickUnit * 0.5; tick += yTickUnit) {
            double y = plotBottom - ((tick - yLowerBound) / yRange) * plotHeight;
            gc.strokeLine(plotLeft, y, plotRight, y);
            drawChartText(gc, formatAxisTick(tick), plotLeft - 8.0, y, TextAlignment.RIGHT, colors.text());
        }

        gc.setStroke(colors.axis());
        gc.setLineWidth(1.0);
        gc.strokeLine(plotLeft, plotTop, plotLeft, plotBottom);
        gc.strokeLine(plotLeft, plotBottom, plotRight, plotBottom);

        for (double tick = firstTick(xLowerBound, xTickUnit); tick <= xUpperBound + xTickUnit * 0.5; tick += xTickUnit) {
            double x = plotLeft + ((tick - xLowerBound) / xRange) * plotWidth;
            gc.strokeLine(x, plotBottom, x, plotBottom + 4.0);
            drawChartText(gc, formatAxisTick(tick), x, plotBottom + 16.0, TextAlignment.CENTER, colors.text());
        }

        drawChartText(
                gc,
                X_AXIS_LABEL,
                (plotLeft + plotRight) / 2.0,
                plotBottom + CHART_AXIS_LABEL_GAP + 12.0,
                TextAlignment.CENTER,
                colors.text()
        );
        gc.save();
        gc.translate(16.0, (plotTop + plotBottom) / 2.0);
        gc.rotate(-90.0);
        drawChartText(gc, Y_AXIS_LABEL, 0, 0, TextAlignment.CENTER, colors.text());
        gc.restore();
    }

    private void drawSeries(GraphicsContext gc, double plotLeft, double plotTop, double plotRight, double plotBottom) {
        double xRange = xUpperBound - xLowerBound;
        double yRange = yUpperBound - yLowerBound;
        if (xRange <= 0 || yRange <= 0) {
            return;
        }

        gc.save();
        gc.beginPath();
        gc.rect(plotLeft, plotTop, plotRight - plotLeft, plotBottom - plotTop);
        gc.clip();
        gc.setLineWidth(WAVEFORM_STROKE_WIDTH);
        gc.setLineCap(StrokeLineCap.ROUND);

        for (int i = 0; i < waveformSeries.size(); i++) {
            if (i < legendItems.size() && !legendItems.get(i).isVisible()) {
                continue;
            }
            WaveformSeriesData series = waveformSeries.get(i);
            boolean drawing = false;
            gc.setStroke(Color.web(getSeriesColor(i)));
            gc.beginPath();
            for (WaveformPoint point : series.points()) {
                double xValue = point.xIndex();
                if (xValue < xLowerBound) {
                    continue;
                }
                if (xValue > xUpperBound) {
                    break;
                }
                double x = plotLeft + ((xValue - xLowerBound) / xRange) * (plotRight - plotLeft);
                double y = plotBottom - ((point.value() - yLowerBound) / yRange) * (plotBottom - plotTop);
                if (!drawing) {
                    gc.moveTo(x, y);
                    drawing = true;
                } else {
                    gc.lineTo(x, y);
                }
            }
            if (drawing) {
                gc.stroke();
            }
        }
        gc.restore();
    }

    private void drawChartText(GraphicsContext gc, String text, double x, double y, TextAlignment alignment, Color textColor) {
        gc.setFill(textColor);
        gc.setTextAlign(alignment);
        gc.fillText(text, x, y);
    }

    private void configureThemeColorProbe(Region probe, String lookedUpColor) {
        probe.setManaged(false);
        probe.setMouseTransparent(true);
        probe.setOpacity(0);
        probe.setMinSize(0, 0);
        probe.setPrefSize(0, 0);
        probe.setMaxSize(0, 0);
        probe.setStyle("-fx-background-color: " + lookedUpColor + ";");
        probe.backgroundProperty().addListener((obs, oldVal, newVal) -> Platform.runLater(this::drawWaveform));
    }

    private ChartColors resolveChartColors() {
        return new ChartColors(
                resolveThemeColor(chartBackgroundColorProbe, DEFAULT_CHART_BACKGROUND_COLOR),
                resolveThemeColor(chartGridColorProbe, DEFAULT_CHART_GRID_COLOR),
                resolveThemeColor(chartAxisColorProbe, DEFAULT_CHART_AXIS_COLOR),
                resolveThemeColor(chartTextColorProbe, DEFAULT_CHART_TEXT_COLOR)
        );
    }

    private Color resolveThemeColor(Region probe, Color fallback) {
        probe.applyCss();
        Background background = probe.getBackground();
        if (background == null || background.getFills().isEmpty()) {
            return fallback;
        }
        Paint fill = background.getFills().get(0).getFill();
        return fill instanceof Color color ? color : fallback;
    }

    private static double firstTick(double lowerBound, double tickUnit) {
        if (tickUnit <= 0) {
            return lowerBound;
        }
        return Math.ceil(lowerBound / tickUnit) * tickUnit;
    }

    private String formatAxisTick(double value) {
        double normalizedValue = normalizeAxisTick(value);
        if (Math.abs(normalizedValue) >= 1_000_000) {
            return String.format(Locale.ROOT, "%.2e", normalizedValue);
        }
        if (Math.rint(normalizedValue) == normalizedValue) {
            return String.format(Locale.ROOT, "%.0f", normalizedValue);
        }
        String text = String.format(Locale.ROOT, Math.abs(normalizedValue) < 1 ? "%.6f" : "%.3f", normalizedValue);
        while (text.contains(".") && text.endsWith("0")) {
            text = text.substring(0, text.length() - 1);
        }
        return text.endsWith(".") ? text.substring(0, text.length() - 1) : text;
    }

    private static double normalizeAxisTick(double value) {
        return Math.abs(value) < AXIS_TICK_ZERO_EPSILON ? 0.0 : value;
    }

    private String formatZoomText(double factor) {
        return String.format("%.2f", 1.0 / factor) + "x";
    }

    private String formatWaveValue(double value) {
        if (Math.abs(value) >= 1_000_000 || (Math.abs(value) > 0 && Math.abs(value) < 0.001)) {
            return String.format(Locale.ROOT, "%.3e", value);
        }
        if (Math.rint(value) == value) {
            return String.format(Locale.ROOT, "%.0f", value);
        }
        return String.format(Locale.ROOT, "%.3f", value);
    }

    private String getSeriesColor(int channelIndex) {
        return SERIES_COLORS[channelIndex % SERIES_COLORS.length];
    }

    private static int clampInt(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static double clampDouble(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private void runOnFx(Runnable runnable) {
        if (Platform.isFxApplicationThread()) {
            runnable.run();
            return;
        }
        Platform.runLater(runnable);
    }

    private String formatBytes(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        if (bytes < 1024 * 1024) {
            return String.format("%.2f KB", bytes / 1024.0);
        }
        if (bytes < 1024L * 1024L * 1024L) {
            return String.format("%.2f MB", bytes / (1024.0 * 1024.0));
        }
        return String.format("%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0));
    }

    private record FrameSnapshot(double timeSeconds, WaveformProtocolParser.FrameData frameData) {
    }

    private record ChartColors(Color background, Color grid, Color axis, Color text) {
    }

    private record WaveformPoint(int xIndex, double value) {
    }

    private record WaveformSeriesData(String name, List<WaveformPoint> points) {
        private WaveformSeriesData(String name) {
            this(name, new ArrayList<>());
        }
    }

    private final class WaveformLegendItem {
        private final HBox container;
        private final CheckBox checkBox;
        private final TextField valueTextField;
        private final WaveformSeriesData series;

        private WaveformLegendItem(int channelIndex, WaveformSeriesData series) {
            this.series = series;
            Region colorSwatch = new Region();
            colorSwatch.setPrefSize(12, 12);
            colorSwatch.setMinSize(12, 12);
            colorSwatch.setStyle("-fx-background-color: " + getSeriesColor(channelIndex) + "; -fx-background-radius: 999;");

            this.checkBox = new CheckBox("波形" + (channelIndex + 1));
            this.checkBox.setSelected(true);
            this.checkBox.setGraphic(colorSwatch);

            // 防止 CheckBox 文本被压缩
            checkBox.setMinWidth(Region.USE_PREF_SIZE);
            checkBox.setPrefWidth(Region.USE_COMPUTED_SIZE);
            checkBox.setMaxWidth(Region.USE_PREF_SIZE);

            // 可选：避免文字省略成 ...
            checkBox.setTextOverrun(OverrunStyle.CLIP);
            // 或者更常用：
            // checkBox.setTextOverrun(OverrunStyle.ELLIPSIS);

            valueTextField = new TextField();
            valueTextField.setEditable(false);

            // 让 TextField 承担压缩/扩展
            HBox.setHgrow(valueTextField, Priority.ALWAYS);
            valueTextField.setMinWidth(0);

            this.container = new HBox(4, checkBox, valueTextField);
            this.container.setAlignment(Pos.CENTER_LEFT);

            this.checkBox.selectedProperty().addListener((obs, oldVal, selected) -> {
                refreshViewport();
            });
        }

        private HBox container() {
            return container;
        }

        private boolean isVisible() {
            return checkBox.isSelected();
        }

        private void setCurrentValue(double value) {
            valueTextField.setText(formatWaveValue(value));
        }
    }
}
