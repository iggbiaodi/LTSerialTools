package indi.lt.serialtool.component;

import github.nonoas.jfx.flat.ui.theme.Theme;
import indi.lt.serialtool.SerialApplication;
import indi.lt.serialtool.global.ThemeManager;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ListCell;
import javafx.util.StringConverter;

import java.util.List;

/**
 * 主题选择下拉框
 *
 * @author Nonoas
 * @date 2026/5/17
 * @since 1.0.0
 */
public class ThemeComboBox extends ComboBox<Theme> {

    public static final String DEFAULT_THEME_NAME = "PrimerLight";

    public ThemeComboBox() {
        this(resolveDefaultTheme());
    }

    public ThemeComboBox(Theme selectedTheme) {
        getItems().addAll(ThemeManager.getAll());
        setMaxWidth(Double.MAX_VALUE);
        setVisibleRowCount(8);
        setConverter(new StringConverter<>() {
            @Override
            public String toString(Theme theme) {
                return theme == null ? "" : theme.getName();
            }

            @Override
            public Theme fromString(String string) {
                return null;
            }
        });
        setCellFactory(listView -> new ListCell<>() {
            @Override
            protected void updateItem(Theme item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.getName());
            }
        });
        setButtonCell(new ListCell<>() {
            @Override
            protected void updateItem(Theme item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.getName());
            }
        });
        selectTheme(selectedTheme);
    }

    public void selectTheme(Theme selectedTheme) {
        if (selectedTheme == null) {
            setValue(null);
            return;
        }
        for (Theme theme : getItems()) {
            if (selectedTheme.getName().equals(theme.getName())) {
                setValue(theme);
                return;
            }
        }
        setValue(selectedTheme);
    }

    public Theme applySelectedTheme() {
        Theme theme = getValue();
        if (theme == null) {
            return null;
        }
        SerialApplication.setUserAgentStylesheet(theme.getUserAgentStylesheet());
        return theme;
    }

    public static Theme resolveDefaultTheme() {
        return resolveThemeByName(DEFAULT_THEME_NAME);
    }

    public static Theme resolveThemeByName(String themeName) {
        List<Theme> themes = ThemeManager.getAll();
        for (Theme theme : themes) {
            if (theme.getName().equals(themeName)) {
                return theme;
            }
        }
        return themes.isEmpty() ? null : themes.get(0);
    }
}
