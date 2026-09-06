package indi.lt.serialtool.view;

import github.nonoas.jfx.flat.ui.stage.AppStage;
import indi.lt.serialtool.global.FontSettingsManager;
import javafx.stage.Stage;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * 程序通用窗口，设置了一系列通用的样式和参数
 *
 * @author Nonoas
 * @datetime 2022/1/22 22:15
 */
public class BaseStage extends AppStage {

    public static final String APP_NAME = "LTSerialTool";
    private static final Properties APP_PROPERTIES = loadAppProperties();
    public static final String APP_VERSION = getAppProperty("app.version", "未知版本");
    public static final String APP_BUILD_TIME = getAppProperty("app.build.time", "未知");

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

    private static Properties loadAppProperties() {
        Properties properties = new Properties();
        try (InputStream inputStream = BaseStage.class.getResourceAsStream("/app.properties")) {
            if (inputStream != null) {
                properties.load(inputStream);
            }
        } catch (IOException ignored) {
        }
        return properties;
    }

    private static String getAppProperty(String key, String defaultValue) {
        String value = APP_PROPERTIES.getProperty(key);
        if (value != null && !value.isBlank()) {
            return value.trim();
        }
        return defaultValue;
    }
}
