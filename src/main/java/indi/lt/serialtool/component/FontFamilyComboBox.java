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

    private static final String CELL_LOOKUP_STYLE = String.join(" ",
            "-color-cell-bg: -color-bg-default;",
            "-color-cell-fg: -color-fg-default;",
            "-color-cell-bg-selected: -color-base-1;",
            "-color-cell-fg-selected: -color-fg-default;",
            "-color-cell-bg-selected-focused: -color-base-1;",
            "-color-cell-fg-selected-focused: -color-fg-default;",
            "-color-cell-bg-odd: -color-bg-subtle;",
            "-color-cell-border: -color-border-default;"
    );

    public FontFamilyComboBox(String selectedFont) {
        getItems().addAll(FontSettingsManager.getAvailableFontFamilies());
        setValue(selectedFont);
        setMaxWidth(Double.MAX_VALUE);
        setVisibleRowCount(12);
        setCellFactory(listView -> createFontCell());
        setButtonCell(createFontCell());
    }

    private static ListCell<String> createFontCell() {
        return new ListCell<>() {
            {
                setStyle(CELL_LOOKUP_STYLE);
            }

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
        };
    }
}
