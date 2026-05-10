package indi.lt.serialtool;

import github.nonoas.jfx.flat.ui.AppState;
import github.nonoas.jfx.flat.ui.AutoReleaseApplication;
import github.nonoas.jfx.flat.ui.stage.ExceptionAlert;
import github.nonoas.jfx.flat.ui.theme.PrimerLight;
import indi.lt.serialtool.component.CommandTableView;
import indi.lt.serialtool.controller.MainController;
import indi.lt.serialtool.data.CommandRepository;
import indi.lt.serialtool.global.ConfigManager;
import indi.lt.serialtool.global.FontSettingsManager;
import indi.lt.serialtool.view.MainStage;
import indi.lt.serialtool.view.SaveProgressStage;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.Alert;
import javafx.scene.layout.HeaderBar;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;

import static indi.lt.serialtool.global.ConfigManager.KEY_RECEIVE_SPLIT_PANE_DIVIDER_POSITIONS;

public class SerialApplication extends AutoReleaseApplication {

    private final Logger LOG = LogManager.getLogger(SerialApplication.class);

    private MainController controller;
    private volatile boolean shutdownInProgress;
    private volatile boolean shutdownSaveCompleted;

    @Override
    public void start(Stage stage) {
        try {
            setUserAgentStylesheet(new PrimerLight().getUserAgentStylesheet());

            // 注意资源路径，通常加前导斜杠更稳
            FXMLLoader fxmlLoader = new FXMLLoader(SerialApplication.class.getResource("/fxml/main-view.fxml"));
            // 先加载 -> 创建场景图和 Controller 并完成 @FXML 注入
            Parent root = fxmlLoader.load();
            // 再拿 Controller
            controller = fxmlLoader.getController();

            MainStage appStage = new MainStage();
            appStage.setTitle("LTSerialTool");

            StackPane rootPane = new StackPane(root);
            HeaderBar headerBar = appStage.getHeaderBar();
            headerBar.setViewOrder(-1);
            headerBar.setMaxWidth(Region.USE_PREF_SIZE);
            headerBar.setMaxHeight(Region.USE_PREF_SIZE);
            StackPane.setAlignment(headerBar, Pos.TOP_RIGHT);
            rootPane.getChildren().add(headerBar);
            appStage.setContentView(rootPane);
            // 现在 controller 已经不是 null 了，且其 @FXML 成员已注入
            appStage.registryDragger(controller.getMenuBar());

            AppState.setStage(appStage.getStage());
            appStage.getStage().setOnCloseRequest(this::handleCloseRequest);
            appStage.show();
        } catch (Exception e) {
            LOG.error("未知异常", e);
            ExceptionAlert.error(e);
        }

    }

    @Override
    public void stop() throws Exception {
        if (!shutdownSaveCompleted) {
            saveLayoutToMemory();
            ConfigManager.save();
        }
        super.stop();
    }

    private void handleCloseRequest(WindowEvent event) {
        if (shutdownSaveCompleted) {
            return;
        }
        if (shutdownInProgress) {
            event.consume();
            return;
        }
        event.consume();
        shutdownInProgress = true;

        Stage stage = AppState.getStage();
        saveLayoutToMemory();
        List<CommandTableView.CommandItem> commandSnapshot = controller.snapshotCommandTable();

        SaveProgressStage progressStage = new SaveProgressStage(stage);
        Task<Void> saveTask = new Task<>() {
            @Override
            protected Void call() {
                updateMessage("正在整理表单数据...");
                updateProgress(1, 4);
                updateMessage("正在保存指令列表...");
                CommandRepository.INSTANCE.saveAll(commandSnapshot);
                updateProgress(2, 4);
                updateMessage("正在写入配置文件...");
                ConfigManager.save();
                updateProgress(3, 4);
                updateMessage("保存完成，正在关闭窗口...");
                updateProgress(4, 4);
                return null;
            }
        };

        progressStage.bind(saveTask);
        saveTask.setOnSucceeded(e -> {
            shutdownSaveCompleted = true;
            progressStage.close();
            Platform.exit();
        });
        saveTask.setOnFailed(e -> {
            shutdownInProgress = false;
            progressStage.close();
            LOG.error("关闭前保存失败", saveTask.getException());
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("保存失败");
            alert.setHeaderText("关闭前保存配置失败");
            alert.setContentText(saveTask.getException() == null ? "请重试关闭窗口。" : saveTask.getException().getMessage());
            FontSettingsManager.configureDialog(alert);
            alert.showAndWait();
        });

        progressStage.show();
        Thread saveThread = new Thread(saveTask, "app-shutdown-save");
        saveThread.setDaemon(true);
        saveThread.start();
    }

    private void saveLayoutToMemory() {
        if (controller != null) {
            try {
                controller.persistFormStateToConfig();
            } catch (Exception e) {
                LOG.error(e);
                ExceptionAlert.error(e);
            }
        }
        Stage stage = AppState.getStage();
        if (stage == null) {
            return;
        }
        ConfigManager.put("window.x", String.valueOf(stage.getX()));
        ConfigManager.put("window.y", String.valueOf(stage.getY()));
        ConfigManager.put("window.width", String.valueOf(stage.getWidth()));
        ConfigManager.put("window.height", String.valueOf(stage.getHeight()));
        ConfigManager.put("window.isMaximized", String.valueOf(stage.isMaximized()));
        if (controller != null) {
            ConfigManager.put(KEY_RECEIVE_SPLIT_PANE_DIVIDER_POSITIONS, controller.getDividePosition());
        }
    }
}
