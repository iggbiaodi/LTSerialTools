package indi.lt.serialtool.view;

import indi.lt.serialtool.controller.SerialReceiveCtrl;
import javafx.fxml.FXMLLoader;
import javafx.scene.layout.StackPane;

import java.io.IOException;

/**
 * @author Nonoas
 * @date 2025/8/22
 * @since 1.0.0
 */
public class SerialReceivePane extends StackPane {

    private SerialReceiveCtrl controller;

    public SerialReceivePane(String serialName, String serialKey) {
        FXMLLoader fxmlLoader = new FXMLLoader(
                getClass().getResource("/fxml/serial-receive-pane.fxml")
        );
        fxmlLoader.setRoot(this);

        try {
            fxmlLoader.load();
            controller = fxmlLoader.getController();
            controller.setSerialName(serialName);
            controller.setKeyLastSerial(serialKey);
            controller.initSerialComboBoxAction();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public SerialReceiveCtrl getController() {
        return controller;
    }

    public void dispose() {
        if (controller != null) {
            controller.dispose();
        }
    }
}

