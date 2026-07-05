package indi.lt.serialtool.view;

import github.nonoas.jfx.flat.ui.stage.AppStage;
import indi.lt.serialtool.global.FontSettingsManager;
import javafx.stage.Stage;

/**
 * 程序通用窗口，设置了一系列通用的样式和参数
 *
 * @author Nonoas
 * @datetime 2022/1/22 22:15
 */
public class BaseStage extends AppStage {

    public static final String APP_NAME = "LTSerialTool";
    public static final String APP_VERSION = "2.0.0";

    protected final String TITLE = APP_NAME + "-v" + APP_VERSION;

    public BaseStage() {
        setTitle(TITLE);
        Stage stage = getStage(); // 如果没有 getStage()，请改为直接使用父类暴露的 stage 字段
        if (stage != null && stage.getScene() != null) {
            String stylesheet = FontSettingsManager.getAppStylesheetUrl();
            if (stylesheet != null && !stage.getScene().getStylesheets().contains(stylesheet)) {
                stage.getScene().getStylesheets().add(stylesheet);
            }
            stage.getScene().rootProperty().addListener((obs, oldRoot, newRoot) -> {
                if (newRoot != null) {
                    FontSettingsManager.applyTo(newRoot);
                }
            });
            if (stage.getScene().getRoot() != null) {
                FontSettingsManager.applyTo(stage.getScene().getRoot());
            }
        }
        FontSettingsManager.applyAppIcon(getStage());
    }
}
