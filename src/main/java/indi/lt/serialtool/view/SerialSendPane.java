package indi.lt.serialtool.view;

import indi.lt.serialtool.controller.SerialSendCtrl;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.SplitPane;
import javafx.scene.layout.Pane;

import java.io.IOException;

public class SerialSendPane extends SplitPane {

    private SerialSendCtrl controller;

    private static final SerialSendPane serialSendPane = new SerialSendPane();

    private SerialSendPane() {
        FXMLLoader fxmlLoader = new FXMLLoader(
                getClass().getResource("/fxml/serial-send-pane.fxml")
        );
        fxmlLoader.setRoot(this);      // 将 this 作为 fxml 根节点

        try {
            fxmlLoader.load(); // 加载 fxml
            controller = fxmlLoader.getController(); // 获取逻辑控制器
            controller.setRootPane(this);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static SerialSendPane getInstance() {
        return serialSendPane;
    }

    public SerialSendCtrl getController() {
        return controller;
    }

    public void dispose() {
        if (controller != null) {
            controller.dispose();
        }
    }
}
