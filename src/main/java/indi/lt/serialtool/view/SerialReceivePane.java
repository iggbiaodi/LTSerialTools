package indi.lt.serialtool.view;

import indi.lt.serialtool.component.PromptInlineCssTextArea;
import indi.lt.serialtool.controller.SerialReceiveCtrl;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.TextField;
import javafx.scene.layout.StackPane;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

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
            initRealtimeFilter(fxmlLoader.getNamespace());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void initRealtimeFilter(Map<String, Object> namespace) {
        Object keywordObj = namespace.get("tfKeyWord");
        Object originObj = namespace.get("textAreaOrigin");
        Object filterObj = namespace.get("textAreaFilter");

        if (!(keywordObj instanceof TextField keywordField)
                || !(originObj instanceof PromptInlineCssTextArea originArea)
                || !(filterObj instanceof PromptInlineCssTextArea filterArea)) {
            return;
        }

        Runnable refreshFilter = () -> applyFilter(originArea, filterArea, keywordField.getText());
        keywordField.textProperty().addListener((obs, oldVal, newVal) -> refreshFilter.run());
        originArea.textProperty().addListener((obs, oldVal, newVal) -> refreshFilter.run());
        refreshFilter.run();
    }

    private void applyFilter(PromptInlineCssTextArea originArea,
                             PromptInlineCssTextArea filterArea,
                             String rawKeywords) {
        String source = originArea.getText();
        if (source == null || source.isEmpty()) {
            filterArea.setText("");
            return;
        }

        List<String> keywords = parseKeywords(rawKeywords);
        if (keywords.isEmpty()) {
            filterArea.setText("");
            return;
        }

        String[] lines = source.split("\\R", -1);
        StringBuilder filteredText = new StringBuilder(source.length());

        for (String line : lines) {
            String lowerLine = line.toLowerCase(Locale.ROOT);
            for (String keyword : keywords) {
                if (lowerLine.contains(keyword)) {
                    filteredText.append(line).append('\n');
                    break;
                }
            }
        }

        filterArea.setText(filteredText.toString());
    }

    private List<String> parseKeywords(String rawKeywords) {
        if (rawKeywords == null || rawKeywords.isBlank()) {
            return new ArrayList<>();
        }

        String[] parts = rawKeywords.split("\\|");
        List<String> keywords = new ArrayList<>(parts.length);
        for (String part : parts) {
            String keyword = part.trim().toLowerCase(Locale.ROOT);
            if (!keyword.isEmpty()) {
                keywords.add(keyword);
            }
        }
        return keywords;
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

