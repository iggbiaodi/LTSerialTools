package indi.lt.serialtool.view;

import com.fazecast.jSerialComm.SerialPort;
import github.nonoas.jfx.flat.ui.pane.JustifiedFlowPane;
import indi.lt.serialtool.component.PromptInlineCssTextArea;
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
import javafx.scene.Node;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
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
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
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
    private static final String[] SERIES_COLORS = {
            "#f3622d",
            "#fba71b",
            "#57b757",
            "#41a9c9",
            "#4258c9",
            "#9a42c8",
            "#c84164",
            "#888888"
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

    private final NumberAxis xAxis = new NumberAxis();
    private final NumberAxis yAxis = new NumberAxis();
    private final LineChart<Number, Number> lineChart = new LineChart<>(xAxis, yAxis);
    private final ScrollBar sbTimeline = new ScrollBar();

    private final List<XYChart.Series<Number, Number>> waveformSeries = new ArrayList<>();
    private final List<WaveformLegendItem> legendItems = new ArrayList<>();
    private final List<Double> frameTimes = new ArrayList<>();
    private final ConcurrentLinkedQueue<FrameSnapshot> pendingFrames = new ConcurrentLinkedQueue<>();
    private final AtomicBoolean uiRefreshQueued = new AtomicBoolean(false);
    private final PromptInlineCssTextArea dummyTextArea = new PromptInlineCssTextArea();

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

        xAxis.setLabel("时间 (s)");
        xAxis.setForceZeroInRange(false);
        xAxis.setAutoRanging(false);
        yAxis.setLabel("数据值");
        yAxis.setForceZeroInRange(false);
        yAxis.setAutoRanging(false);
        resetAxisBounds();

        lineChart.setAnimated(false);
        lineChart.setCreateSymbols(false);
        lineChart.setLegendVisible(false);
        lineChart.setHorizontalGridLinesVisible(true);
        lineChart.setVerticalGridLinesVisible(true);
        lineChart.setTitle("实时波形图");
        lineChart.setMinHeight(320);
        lineChart.setFocusTraversable(true);

        legendBox.setPadding(new Insets(8, 0, 0, 0));

        sbTimeline.setOrientation(Orientation.HORIZONTAL);
        sbTimeline.setMin(0);
        sbTimeline.setMax(0);
        sbTimeline.setVisibleAmount(DEFAULT_VISIBLE_POINTS);
        sbTimeline.setDisable(true);

        HBox statusBar = new HBox(16, new Label("接收量:"), lbRecvBytes, createSpacer(), lbFrameInfo);
        statusBar.setAlignment(Pos.CENTER_LEFT);

        VBox root = new VBox(10, topRow, waveformConfigBox, viewControlBox, lineChart, sbTimeline, legendBox, statusBar);
        VBox.setVgrow(lineChart, Priority.ALWAYS);
        setCenter(root);
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
        lineChart.setOnScroll(event -> {
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
        runOnFx(() -> {
            lineChart.getData().clear();
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
        for (int i = 0; i < values.length; i++) {
            XYChart.Series<Number, Number> series = waveformSeries.get(i);
            series.getData().add(new XYChart.Data<>(snapshot.timeSeconds(), values[i]));
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
            XYChart.Series<Number, Number> series = new XYChart.Series<>();
            series.setName("波形" + (channelIndex + 1));
            waveformSeries.add(series);
            lineChart.getData().add(series);
            WaveformLegendItem legendItem = new WaveformLegendItem(channelIndex, series);
            legendItems.add(legendItem);
            legendBox.getChildren().add(legendItem.container());
            bindSeriesVisibility(series, legendItem);
        }
    }

    private void trimSeries(XYChart.Series<Number, Number> series) {
        int overflow = series.getData().size() - maxPoints;
        if (overflow <= 0) {
            return;
        }
        series.getData().remove(0, overflow);
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
        runOnFx(() -> {
            frameTimes.clear();
            lineChart.getData().clear();
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

        double lowerTime = frameTimes.get(viewStartIndex);
        double upperTime = frameTimes.get(endIndex);
        double timeSpan = Math.max(upperTime - lowerTime, 0.001);
        double xPadding = Math.max(timeSpan * 0.02, 0.001);
        xAxis.setLowerBound(lowerTime - xPadding);
        xAxis.setUpperBound(upperTime + xPadding);

        updateYAxis(lowerTime, upperTime);
        updateTimeline(windowPointCount, maxStart);
        lbViewInfo.setText(
                "显示窗口: " + windowPointCount + " 点  X缩放:" + formatZoomText(xZoomFactor)
                        + "  Y缩放:" + formatZoomText(yZoomFactor)
        );
    }

    private void updateYAxis(double lowerTime, double upperTime) {
        double min = Double.POSITIVE_INFINITY;
        double max = Double.NEGATIVE_INFINITY;

        for (XYChart.Series<Number, Number> series : waveformSeries) {
            int seriesIndex = waveformSeries.indexOf(series);
            if (seriesIndex >= 0 && seriesIndex < legendItems.size() && !legendItems.get(seriesIndex).isVisible()) {
                continue;
            }
            List<XYChart.Data<Number, Number>> data = series.getData();
            for (int i = data.size() - 1; i >= 0; i--) {
                XYChart.Data<Number, Number> point = data.get(i);
                double x = point.getXValue().doubleValue();
                if (x > upperTime) {
                    continue;
                }
                if (x < lowerTime) {
                    break;
                }
                double y = point.getYValue().doubleValue();
                if (y < min) {
                    min = y;
                }
                if (y > max) {
                    max = y;
                }
            }
        }

        if (!Double.isFinite(min) || !Double.isFinite(max)) {
            yAxis.setLowerBound(0);
            yAxis.setUpperBound(1);
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
        yAxis.setLowerBound(center - halfSpan);
        yAxis.setUpperBound(center + halfSpan);
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
        xAxis.setLowerBound(0);
        xAxis.setUpperBound(1);
        yAxis.setLowerBound(0);
        yAxis.setUpperBound(1);
    }

    private String formatZoomText(double factor) {
        return String.format("%.2f", 1.0 / factor) + "x";
    }

    private void bindSeriesVisibility(XYChart.Series<Number, Number> series, WaveformLegendItem legendItem) {
        series.nodeProperty().addListener((obs, oldNode, newNode) -> applySeriesVisibility(series, legendItem));
        applySeriesVisibility(series, legendItem);
    }

    private void applySeriesVisibility(XYChart.Series<Number, Number> series, WaveformLegendItem legendItem) {
        boolean visible = legendItem.isVisible();
        Node seriesNode = series.getNode();
        if (seriesNode != null) {
            seriesNode.setVisible(visible);
            seriesNode.setManaged(visible);
        }
        for (XYChart.Data<Number, Number> data : series.getData()) {
            Node dataNode = data.getNode();
            if (dataNode != null) {
                dataNode.setVisible(visible);
                dataNode.setManaged(visible);
            }
        }
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

    private final class WaveformLegendItem {
        private final HBox container;
        private final CheckBox checkBox;
        private final TextField valueTextField;
        private final XYChart.Series<Number, Number> series;

        private WaveformLegendItem(int channelIndex, XYChart.Series<Number, Number> series) {
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
                applySeriesVisibility(this.series, this);
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
