package indi.lt.serialtool.component;

import indi.lt.serialtool.global.FontSettingsManager;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ListCell;
import javafx.scene.text.Font;

/**
 * 字体选择下拉框
 *
 * @author Nonoas
 * @date 2026/5/17
 * @since 1.0.0
 */
public class FontFamilyComboBox extends ComboBox<String> {

    public FontFamilyComboBox(String selectedFont) {
        getItems().addAll(FontSettingsManager.getAvailableFontFamilies());
        setValue(selectedFont);
        setMaxWidth(Double.MAX_VALUE);
        setVisibleRowCount(12);
        setCellFactory(listView -> new ListCell<>() {
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
        setButtonCell(new ListCell<>() {
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
    }
}
