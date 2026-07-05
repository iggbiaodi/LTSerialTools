package indi.lt.serialtool.component;

import javafx.beans.property.SimpleStringProperty;
import javafx.scene.control.ToggleButton;

/**
 * 串口开关按钮
 *
 * @author Nonoas
 * @date 2025/8/19
 * @since 1.0.0
 */
public class SerialToggleButton extends ToggleButton {

    private final SimpleStringProperty selectedText =new SimpleStringProperty("关闭");
    private final SimpleStringProperty unSelectedText =new SimpleStringProperty("开启");

    public SerialToggleButton() {
        getStyleClass().add("serial-toggle-button");
        selectedProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue) {
                setText(selectedText.getValue());
            } else {
                setText(unSelectedText.getValue());
            }
        });
    }

    public String getSelectedText() {
        return selectedText.get();
    }

    public SimpleStringProperty selectedTextProperty() {
        return selectedText;
    }

    public void setSelectedText(String selectedText) {
        this.selectedText.set(selectedText);
    }

    public String getUnSelectedText() {
        return unSelectedText.get();
    }

    public SimpleStringProperty unSelectedTextProperty() {
        return unSelectedText;
    }

    public void setUnSelectedText(String unSelectedText) {
        this.unSelectedText.set(unSelectedText);
    }
}
