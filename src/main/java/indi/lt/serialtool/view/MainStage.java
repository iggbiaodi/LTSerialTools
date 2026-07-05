package indi.lt.serialtool.view;

import github.nonoas.jfx.flat.ui.control.UIFactory;
import indi.lt.serialtool.global.ConfigManager;
import javafx.application.Platform;
import javafx.scene.control.Button;
import javafx.scene.control.Tooltip;
import javafx.stage.Stage;

/**
 * @author Nonoas
 * @version 1.0.0
 * @date 2025/8/24
 * @since 1.0.0
 */
public class MainStage extends BaseStage {

    public MainStage() {

        double width = Double.parseDouble(ConfigManager.get("window.width", "1000"));
        double height = Double.parseDouble(ConfigManager.get("window.height", "600"));
        boolean isMaximized = Boolean.parseBoolean(ConfigManager.get("window.isMaximized", "false"));

        getHeaderBar().setPrefHeight(0);
        setWidth(width);
        setHeight(height);
        if (isMaximized) {
            Platform.runLater(() -> setMaximized(true));
        } else {
            setMinWidth(700);
            setMinHeight(600);
        }

        String x = ConfigManager.get("window.x", "NULL");
        String y = ConfigManager.get("window.y", "NULL");
        if (!x.equals("NULL")) {
            setX(Double.parseDouble(x));
        }
        if (!y.equals("NULL")) {
            setY(Double.parseDouble(y));
        }

        Button pinButton = UIFactory.createPinButton(this);
        Tooltip.install(pinButton, new Tooltip("窗口置顶"));
        getSystemButtons().addFirst(pinButton);
    }
}
